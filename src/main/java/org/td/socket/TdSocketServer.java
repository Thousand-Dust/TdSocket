package org.td.socket;


import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * @author Thousand Dust
 */
public class TdSocketServer implements Closeable {

    private int id;

    protected SocketCallback socketCallback;

    private final ServerSocket serverSocket;

    private final int connectionCount;

    private ArrayList<TdSocket> tdSockets = new ArrayList<>();

    public TdSocketServer(ServerSocket serverSocket, int connectionCount, SocketCallback callback) throws IOException {
        this.serverSocket = serverSocket;
        this.socketCallback = callback;
        this.connectionCount = connectionCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    public int getConnectionCount() {
        return connectionCount;
    }

    protected TdSocket accept() throws IOException {
        Socket socket = serverSocket.accept();

        TdSocket tdSocket = new TdSocket(socket, socketCallback);
        tdSocket.setId(id);
        tdSockets.add(tdSocket);

        return tdSocket;
    }

    @Override
    public void close() {
        Utils.close(serverSocket);
    }
}