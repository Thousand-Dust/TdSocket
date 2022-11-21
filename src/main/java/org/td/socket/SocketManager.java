package org.td.socket;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function1;

/**
 * Socket长连接管理类
 * @author Thousand Dust
 */
public class SocketManager {

    private static String TAG = "SocketManager";

    private final ExecutorService executorService = new ThreadPoolExecutor(10, 10,
            10_000, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * 最大回调任务处理线程
     */
    private final int maxInvokeThread = 1;

    /**
     * 回调任务线程数量
     */
    private int invokeThreadCount = 0;

    /**
     * 回调任务链表
     */
    private final LinkedList<Runnable> invokeTaskList = new LinkedList<>();

    /**
     * socket列表
     */
    private final ArrayList<TdSocket> tdSocketList = new ArrayList<>();

    private Function1<String, Object> logCallback = null;

    public SocketManager(@Nullable String logTag) {
        if (logTag != null) {
            TAG = logTag;
        }
    }

    public void setLogCallback(Function1<String, Object> callback) {
        this.logCallback = callback;
    }
    
    private void invokeLogCallback(String message) {
        if (logCallback == null) {
            return;
        }
        logCallback.invoke(message);
    }

    public void addTdSocketClient(TdSocketClient client) {
        tdSocketList.add(client);

        executorService.execute(() -> {
            try {
                client.connection();
                start(client);
                client.socketCallback.onConnectionSucceeded(client.getId(), client);
            } catch (Exception e) {
                invokeLogCallback("connection error: "+ e);
                //连接失败
                client.socketCallback.onConnectionFailed(client.getId(), e);
            }
        });
    }

    public void addTdSocketServer(TdSocketServer server) {
        executorService.execute(() -> {
            for (int i = 0; i < server.getConnectionCount(); i++) {
                try {
                    TdSocket tdSocket = server.accept();
                    tdSocketList.add(tdSocket);
                    start(tdSocket);
                    tdSocket.socketCallback.onConnectionSucceeded(server.getId(), tdSocket);
                } catch (Exception e) {
                    invokeLogCallback("connection error: "+ e);
                    //连接失败
                    server.socketCallback.onConnectionFailed(server.getId(), e);
                }
            }
        });
    }

    private void start(TdSocket tdSocket) {
        //启动读取线程
        executorService.execute(new SocketReaderThread(tdSocket));
        executorService.execute(new SocketWriterThread(tdSocket));

        if (invokeThreadCount < maxInvokeThread) {
            //启动回调任务线程
            for (; invokeThreadCount < maxInvokeThread; invokeThreadCount++) {
                executorService.execute(new InvokeRunnable());
            }
        }
    }

    /**
     * 删除TdSocket
     */
    public void removeTdSocket(TdSocket tdSocket) {
        tdSocketList.remove(tdSocket);
        if (tdSocket.socket != null && !tdSocket.socket.isClosed()) {
            tdSocket.close();
        }
        if (invokeTaskList.size() < 1) {
            synchronized (invokeTaskList) {
                invokeTaskList.notifyAll();
            }
        }
    }

    /**
     * Socket接收心跳包和数据线程
     */
    private class SocketReaderThread implements Runnable {
        private final TdSocket tdSocket;

        SocketReaderThread(TdSocket tdSocket) {
            this.tdSocket = tdSocket;
        }

        @Override
        public void run() {
            try {
                while (tdSocket.socket != null && !tdSocket.socket.isClosed()) {
                    byte[] content = tdSocket.readMessage();

                    String heartbeatResponse = tdSocket.heartbeatResponse;
                    //判断内容是否是心跳包，是的话处理，不是的话调用接收消息回调
                    if (content.length != heartbeatResponse.getBytes(StandardCharsets.UTF_8).length || !new String(content).equals(heartbeatResponse)) {
                        Runnable invokeTask = () -> tdSocket.socketCallback.onReceiveMessage(tdSocket.getId(), tdSocket, content);
                        synchronized (invokeTaskList) {
                            invokeTaskList.add(invokeTask);
                            invokeTaskList.notify();
                        }
                    }
                }
            } catch (Exception e) {
                invokeLogCallback("run: read thread error: "+e);
            } finally {
                tdSocket.socketCallback.onDisconnection(tdSocket.getId(), tdSocket);
            }
        }
    }

    private class SocketWriterThread implements Runnable {
        private final TdSocket tdSocket;

        SocketWriterThread(TdSocket tdSocket) {
            this.tdSocket = tdSocket;
        }

        @Override
        public void run() {
            try {
                while (tdSocket.socket != null && !tdSocket.socket.isClosed()) {
                    //上次发送到这次的间隔时间
                    long timeConsuming = System.currentTimeMillis() - tdSocket.lastSentTime;

                    Thread.sleep(Math.max(0, tdSocket.getTimeOut() - timeConsuming - 2000));
                    //如果等待期间其他线程发送了数据，则跳过此次心跳验证
                    if ((System.currentTimeMillis() - tdSocket.lastSentTime + 3000) < tdSocket.getTimeOut()) {
                        continue;
                    }

                    //发送心跳包
                    tdSocket.sendMessage(tdSocket.heartbeatRequest.getBytes(StandardCharsets.UTF_8));
                }
            } catch (InterruptedException | IOException e) {
                invokeLogCallback("run: writer thread error: "+e);
            }
        }
    }

    /**
     * 接收消息回调任务处理线程
     */
    private class InvokeRunnable implements Runnable {
        @Override
        public void run() {
            while (!tdSocketList.isEmpty()) {
                Runnable task = null;
                synchronized (invokeTaskList) {
                    if (invokeTaskList.size() < 1) {
                        //没有任务，等待
                        try {
                            invokeTaskList.wait();
                        } catch (InterruptedException e) {
                            invokeLogCallback("run: wait task error: "+e);
                        }
                    } else {
                        //拿到任务
                        task = invokeTaskList.removeFirst();
                    }
                }
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        invokeLogCallback("run: invoke task error: "+e);
                    }
                }
            }
            invokeThreadCount--;
        }
    }

}