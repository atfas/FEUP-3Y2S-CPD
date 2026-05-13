package client;

import java.io.*;
import java.net.*;

import common.protocol.Request;

/**

 * Streams are created once and reused, creating a new ObjectOutputStream
 * on every call corrupts the stream header, so this is important.
 */
public class ConnectionManager {

    private final Socket             socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream  in;

    public ConnectionManager(String hostname, int port) throws IOException {
        this.socket = new Socket(hostname, port);
        // Output stream must be created first to avoid a deadlock
        // (both sides waiting for the other's stream header).
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in  = new ObjectInputStream(socket.getInputStream());
    }

    // High-level helpers (used by UIHandler)

    public void send(Request request) throws IOException {
        request.sendRequest(out);
    }

    public Request receive() throws IOException {
        return Request.getRequest(in);
    }

    public Request sendAndReceive(Request request) throws IOException {
        send(request);
        return receive();
    }

    // Raw stream access (used by Auth, RoomSelector, ChatRoom)

    public ObjectInputStream  getInputStream()  { return in;  }
    public ObjectOutputStream getOutputStream() { return out; }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void close() throws IOException {
        socket.close();
    }
}