package org.td.socket;

import android.icu.util.Output;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Thousand Dust
 */
public class TdSocket implements Closeable {

    private int id;

    protected SocketCallback socketCallback;

    protected Socket socket;

    protected InputStream input;
    protected OutputStream output;

    protected long lastSentTime;

    /**
     * 超时时间
     */
    private int timeOut = 10_000;

    /**
     * 发送的心跳包内容
     */
    public String heartbeatRequest = "still alive";

    /**
     * 接收的心跳包内容
    */
    public String heartbeatResponse = "still alive";

    /**
     * socket线程锁
     */
    protected final Object socketLock = new Object();

    public TdSocket(SocketCallback callback) {
        this.socketCallback = callback;
    }

    public TdSocket(Socket socket, SocketCallback callback) throws IOException {
        this.socket = socket;
        this.socketCallback = callback;

        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * 获取超时时间
     */
    public int getTimeOut() {
        return timeOut;
    }

    /**
     * 设置超时时间
     */
    public void setTimeOut(int timeOut) throws SocketException {
        this.timeOut = timeOut;
        if (socket != null) {
            socket.setSoTimeout(timeOut);
        }
    }

    /**
     * 解析参数
     * @param header 协议头
     * @return 协议头参数键值
     */
    private Map<String, String> getParameters(String header) {
        HashMap<String, String> result = new HashMap<>(16);

        String[] parameters = header.split(";");
        for (String parameter : parameters) {
            String[] strs = parameter.split("=");
            if (strs.length != 2) {
                continue;
            }
            result.put(strs[0], strs[1]);
        }

        return result;
    }

    /**
     * 获取协议头
     * @return
     * @throws IOException
     */
    private String readHeader() throws IOException {
        ByteArrayOutputStream byteout = new ByteArrayOutputStream();
        byte[] buf = new byte[1];
        while (socket != null && !socket.isClosed()) {
            if (input.read(buf) == -1) {
                throw new IOException("read return -1");
            }
            if (buf[0] == '\n') {
                break;
            }
            byteout.write(buf);
        }

        return byteout.size() < 1 ? "" : byteout.toString();
    }

    /**
     * 读取消息
     * @return 读取成功返回消息内容，失败返回null
     * @throws IOException IO异常
     */
    public byte[] readMessage() throws IOException {
        //读取协议头
        String header = readHeader();
        //解析参数
        Map<String, String> map = getParameters(header);

        String lengthStr = map.get("length");
        if (lengthStr == null) {
            throw new IOException("failed to parse the protocol header");
        }
        //正文长度
        int contentLen = Integer.parseInt(lengthStr);
        if (contentLen < 1) {
            throw new IOException("empty content");
        }

        byte[] result = new byte[contentLen];
        int readSize = 0;
        int len;
        while (readSize < contentLen) {
            len = input.read(result, readSize, contentLen - readSize);
            readSize += len;
        }

        return result;
    }

    /**
     * 发送消息
     * @param message 发送的消息
     * @throws IOException IO异常
     */
    public void sendMessage(byte[] message) throws IOException {
        synchronized (socketLock) {
            lastSentTime = System.currentTimeMillis();
            output.write("length=".getBytes());
            output.write(String.valueOf(message.length).getBytes());
            output.write("\n".getBytes());
            output.write(message);
        }
    }

    public boolean isConnect() {
        return (socket != null && socket.isConnected() && !socket.isClosed());
    }

    @Override
    public void close() {
        Utils.close(input);
        Utils.close(output);
        Utils.close(socket);
    }

}