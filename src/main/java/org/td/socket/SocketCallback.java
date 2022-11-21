package org.td.socket;

/**
 * @author Thousand Dust
 */
public interface SocketCallback {

    /**
     * 连接成功
     */
    void onConnectionSucceeded(int id, TdSocket tdSocket);

    /**
     * 连接失败
     */
    void onConnectionFailed(int id, Exception e);

    /**
     * 接收到远程消息
     * @param message 消息内容
     */
    void onReceiveMessage(int id, TdSocket tdSocket, byte[] message);


    /**
     * socket断开连接
     */
    void onDisconnection(int id, TdSocket tdSocket);

}