package org.td.socket;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * @author Thousand Dust
 */
public class TdSocketClient extends TdSocket {

    private SocketAddress socketAddress;

    public TdSocketClient(SocketAddress socketAddress, SocketCallback callback) {
        super(callback);
        this.socketAddress = socketAddress;
    }

    protected void connection() throws IOException {
        socket = new Socket();
        socket.connect(socketAddress);
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }
}