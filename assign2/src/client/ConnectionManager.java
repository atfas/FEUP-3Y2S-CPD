package client;

import java.io.*;
import java.net.*;

import common.models.ClientSession;
import common.models.Room;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;

/**
 * 
 * Streams are created once and reused, creating a new ObjectOutputStream
 * on every call corrupts the stream header, so this is important.
 */
public class ConnectionManager {

    private Socket socket;
    private final String hostname;
    private final int port;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final Object reconnectLock = new Object();
    private volatile boolean reconnecting = false;
    private volatile boolean closed = false;
    private volatile User sessionUser = null;
    private volatile Room sessionRoom = null;

    public ConnectionManager(String hostname, int port) throws IOException {
        this.hostname = hostname;
        this.port = port;
        openSocket();
    }

    // High-level helpers (used by UIHandler)

    public void send(Request request) throws IOException {
        boolean retried = false;
        while (true) {
            try {
                request.sendRequest(out);
                return;
            } catch (IOException ex) {
                if (retried) throw ex;
                reconnect();
                retried = true;
            }
        }
    }

    public Request receive() throws IOException {
        boolean retried = false;
        while (true) {
            try {
                return Request.getRequest(in);
            } catch (IOException ex) {
                if (retried) throw ex;
                reconnect();
                retried = true;
            }
        }
    }

    public Request sendAndReceive(Request request) throws IOException {
        boolean retried = false;
        while (true) {
            try {
                request.sendRequest(out);
                return Request.getRequest(in);
            } catch (IOException ex) {
                if (retried) throw ex;
                reconnect();
                retried = true;
            }
        }
    }

    public void updateSession(User user, Room room) {
        synchronized (reconnectLock) {
            this.sessionUser = user;
            this.sessionRoom = room;
        }
    }

    // Raw stream access (used by Auth, RoomSelector, ChatRoom)

    public ObjectInputStream getInputStream() {
        return in;
    }

    public ObjectOutputStream getOutputStream() {
        return out;
    }

    public boolean isConnected() {
        return !closed && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isClosed() {
        return closed;
    }

    public void reconnect() throws IOException {
        synchronized (reconnectLock) {
            while (reconnecting) {
                try {
                    reconnectLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reconnecting", e);
                }
            }

            if (closed) {
                throw new IOException("Connection is closed");
            }

            if (socket == null) {
                throw new IOException("Socket is not initialised");
            }

            reconnecting = true;
        }

        try {
            closeCurrentSocket();
            openSocket();

            User user = sessionUser;
            if (user != null) {
                Request reconnectRequest = new Request(
                    RequestType.RECONNECT,
                    new ClientSession(user, sessionRoom)
                );

                reconnectRequest.sendRequest(out);
                Request response = Request.getRequest(in);
                if (response.getType() != RequestType.SUCCESS) {
                    throw new IOException("Reconnect rejected: " + response.getPayload());
                }

                if (response.getPayload() instanceof Room restoredRoom) {
                    sessionRoom = restoredRoom;
                }
            }
        } finally {
            synchronized (reconnectLock) {
                reconnecting = false;
                reconnectLock.notifyAll();
            }
        }
    }

    private void openSocket() throws IOException {
        this.socket = new Socket(hostname, port);
        // Output stream must be created first to avoid a deadlock
        // (both sides waiting for the other's stream header).
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    private void closeCurrentSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public void close() throws IOException {
        synchronized (reconnectLock) {
            closed = true;
            reconnectLock.notifyAll();
        }
        if (socket != null) {
            socket.close();
        }
    }
}