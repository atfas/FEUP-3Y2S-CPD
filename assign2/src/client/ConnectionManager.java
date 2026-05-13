package client;

import java.io.*;
import java.net.*;

import common.protocol.Request;
import common.protocol.RequestType;

public class ConnectionManager {

    private final Socket socket;

    public ConnectionManager(String hostname, int port) throws IOException {
        this.socket = new Socket(hostname, port);
    }

    /** Sends a request to the server. */
    public void send(Request request) throws IOException {
        request.sendRequest(socket);
    }

    /** Blocks until a response arrives from the server. */
    public Request receive() throws IOException {
        return Request.getRequest(socket);
    }

    /** Convenience: send a request and immediately wait for the reply. */
    public Request sendAndReceive(Request request) throws IOException {
        send(request);
        return receive();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void close() throws IOException {
        socket.close();
    }
}
