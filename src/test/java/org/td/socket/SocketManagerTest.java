package org.td.socket;

import junit.framework.TestCase;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SocketManagerTest extends TestCase {

    @Test
    public void test() {
        SocketManager socketManager = new SocketManager("SocketService");

        try {
//            startService(socketManager);

            ClientCallback callback = new ClientCallback();
            TdSocketClient client = new TdSocketClient(new InetSocketAddress(8989), callback);
            socketManager.addTdSocketClient(client);

            System.out.println("已启动");

            while (!callback.isExit) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startService(SocketManager socketManager) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8989);
        TdSocketServer server = new TdSocketServer(serverSocket, 1, new ServerCallback());
        socketManager.addTdSocketServer(server);
    }


    class ServerCallback implements SocketCallback {
        @Override
        public void onConnectionSucceeded(int id, TdSocket tdSocket) {
            System.out.println("服务端连接成功");

            new Thread(() -> {
                for (int i = 0; i < 30; i++) {
                    try {
                        Thread.sleep(500);
                        tdSocket.sendMessage(("第"+i+"条消息").getBytes(StandardCharsets.UTF_8));
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                }
                tdSocket.close();
            }).start();
        }

        @Override
        public void onConnectionFailed(int id, Exception e) {
            System.out.println("服务端连接失败");
        }

        @Override
        public void onReceiveMessage(int id, TdSocket tdSocket, byte[] message) {
            System.out.println("服务端接收到消息："+new String(message));
        }

        @Override
        public void onDisconnection(int id, TdSocket tdSocket) {
            System.out.println("服务端连接中断");
        }
    }

    class ClientCallback implements SocketCallback {

        public boolean isExit = false;

        @Override
        public void onConnectionSucceeded(int id, TdSocket tdSocket) {
            System.out.println("客户端连接成功");
        }

        @Override
        public void onConnectionFailed(int id, Exception e) {
            System.out.println("客户端连接失败");
            isExit = true;
        }

        @Override
        public void onReceiveMessage(int id, TdSocket tdSocket, byte[] message) {
            System.out.println("客户端接收到消息："+new String(message));
            try {
                tdSocket.sendMessage("客户端回复：test".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnection(int id, TdSocket tdSocket) {
            System.out.println("客户端连接中断");
            isExit = true;
        }
    }

}