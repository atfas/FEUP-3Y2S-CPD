package server.common;

import java.io.*;
import java.net.*;
import java.util.List;

import common.models.Message;
import common.models.ClientSession;
import common.models.Room;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;
import database.Database;
import database.Database.DatabaseException;
import server.auth.Registration;

public class ClientHandler implements Runnable {
    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private final Database           database;
    private final RoomManager        roomManager;
    private final SessionManager     sessionManager;

    private User    user;
    private Room    currentRoom;
    private boolean isLoggedIn;

    public ClientHandler(Socket socket, Database database, RoomManager roomManager, SessionManager sessionManager) throws IOException {
        this.socket      = socket;
        this.database    = database;
        this.roomManager = roomManager;
        this.sessionManager = sessionManager;
        this.user        = null;
        this.currentRoom = null;
        this.isLoggedIn  = false;

        socket.setSoTimeout(30 * 60 * 1000); // 30 min timeout for read operations

        // Output stream must be created first to avoid deadlock
        // (both sides waiting for the other's stream header).
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in  = new ObjectInputStream(socket.getInputStream());
    }

    // ------------------------------------------------------------------ //
    //  Runnable
    // ------------------------------------------------------------------ //

    @Override
    public void run() {
        System.out.println("[ClientHandler] New connection from " + socket.getInetAddress());
        try {
            while (true) {
                Request request = Request.getRequest(in);
                System.out.println("[ClientHandler] Received request: " + request.getType());

                switch (request.getType()) {
                    case LOGIN        -> login(request.getPayload());
                    case REGISTER     -> register(request.getPayload());
                    case CHAT_MESSAGE -> {
                        if (!isLoggedIn) invalidRequest("Must be logged in to send messages.");
                        else             sendMessage(request.getPayload());
                    }
                    case ENTER_ROOM   -> {
                        if (!isLoggedIn) invalidRequest("Must be logged in to join a room.");
                        else             enterRoom(request.getPayload());
                    }
                    case LEAVE_ROOM   -> leaveRoom();
                    case LIST_ROOMS   -> listRooms();
                    case LOGOUT       -> logout();
                    case RECONNECT    -> reconnect(request.getPayload());
                    case UNKNOWN      -> invalidRequest(request.getPayload());
                    default           -> notImplementedRequest(request.getType());
                }
            }
        } catch (SocketTimeoutException ex) {
                if (user != null) sessionManager.remove(user.getSessionCookie());
            System.out.println("[ClientHandler] Session timeout. Closing connection for " + user.getUsername());
        } catch (IOException ex) {
            System.out.println("[ClientHandler] Connection closed: " + ex.getMessage());
        } finally {
            roomManager.leaveAllRooms(this);
        }
    }

    // ------------------------------------------------------------------ //
    //  Request handlers
    // ------------------------------------------------------------------ //

    private void login(Object payload) throws IOException {
        if (!(payload instanceof User loginUser)) {
            invalidRequest("LOGIN payload must be a User.");
            return;
        }

        try {
            boolean valid = database.validateUser(loginUser.getUsername(), loginUser.getPassword());
            if (valid) {
                this.user = loginUser;
                this.isLoggedIn = true;
                String token = sessionManager.createSession(loginUser);
                loginUser.setSessionCookie(token);
                new Request(RequestType.SUCCESS, token).sendRequest(out);
                System.out.println("[ClientHandler] Login OK: " + loginUser.getUsername() + " (token=" + token + ")");
            } else {
                new Request(RequestType.ERROR, "Invalid username or password.").sendRequest(out);
            }
        } catch (DatabaseException e) {
            new Request(RequestType.ERROR, "Server error during login.").sendRequest(out);
        }
    }

    private void register(Object payload) throws IOException {
        if (!(payload instanceof User newUser)) {
            invalidRequest("REGISTER payload must be a User.");
            return;
        }

        Registration reg = new Registration(
            newUser.getUsername(),
            newUser.getPassword(),
            database
        );
        reg.run();   // synchronous — we need the result before replying

        switch (reg.getResult()) {
            case SUCCESS -> {
                String token = sessionManager.createSession(newUser);
                newUser.setSessionCookie(token);
                new Request(RequestType.SUCCESS, token).sendRequest(out);
                this.user       = newUser;
                this.isLoggedIn = true;
            }
            case USER_ALREADY_EXISTS ->
                new Request(RequestType.ERROR, "Username already taken.").sendRequest(out);
            case INVALID_INPUT ->
                new Request(RequestType.ERROR, "Invalid username or password.").sendRequest(out);
            case DATABASE_ERROR ->
                new Request(RequestType.ERROR, "Server error during registration.").sendRequest(out);
        }
    }

    private void sendMessage(Object payload) throws IOException {
        if (!(payload instanceof Message message)) {
            invalidRequest("CHAT_MESSAGE payload must be a Message.");
            return;
        }
        if (currentRoom == null) {
            invalidRequest("You must join a room before sending messages.");
            return;
        }

        // Re-stamp the sender so clients can't spoof it.
        Message stamped = new Message(user, currentRoom, message.getContent());

        // Broadcast to everyone in the room.
        for (ClientHandler member : roomManager.getMembers(currentRoom.getName())) {
            if (member != this) {
                new Request(RequestType.CHAT_MESSAGE, stamped).sendRequest(member.out);
            }
        }
    }

    private void enterRoom(Object payload) throws IOException {
        if (!(payload instanceof Room room)) {
            invalidRequest("ENTER_ROOM payload must be a Room.");
            return;
        }

        if (currentRoom != null) {
            broadcastSystem(currentRoom, user.getUsername() + " left the room.");
            roomManager.leaveRoom(currentRoom.getName(), this);
        }

        // Broadcast join to existing members BEFORE this client joins.
        String roomKey = room.getName().trim().toLowerCase();
        broadcastSystemTo(roomKey, user.getUsername() + " joined the room.");

        currentRoom = roomManager.joinRoom(roomKey, this);
        new Request(RequestType.SUCCESS, currentRoom).sendRequest(out);
    }

    private void leaveRoom() throws IOException {
        if (currentRoom != null) {
            roomManager.leaveRoom(currentRoom.getName(), this);
            broadcastSystem(currentRoom, user.getUsername() + " left the room.");
            currentRoom = null;
        }
        new Request(RequestType.SUCCESS, null).sendRequest(out);
    }

    /** Broadcasts a system notification to all members of a room (by Room object). */
    private void broadcastSystem(Room room, String text) {
        Message msg = new Message(null, room, text);
        Request req = new Request(RequestType.CHAT_MESSAGE, msg);
        for (ClientHandler member : roomManager.getMembers(room.getName())) {
            if (member != this) {
                try { req.sendRequest(member.out); } catch (IOException ignored) {}
            }
        }
    }

    /** Broadcasts a system notification to all current members of a room (by name, before joining). */
    private void broadcastSystemTo(String roomName, String text) {
        Room room = new Room(roomName);
        Message msg = new Message(null, room, text);
        Request req = new Request(RequestType.CHAT_MESSAGE, msg);
        for (ClientHandler member : roomManager.getMembers(roomName)) {
            try { req.sendRequest(member.out); } catch (IOException ignored) {}
        }
    }

    private void listRooms() throws IOException {
        List<Room> rooms = roomManager.listRooms();
        new Request(RequestType.ROOMS_LIST, (java.io.Serializable) rooms).sendRequest(out);
    }

    private void reconnect(Object payload) throws IOException {
        // Accept either a session token string OR a ClientSession payload (preferred)
        String token = null;
        ClientSession clientSession = null;

        if (payload instanceof String s) token = s;
        else if (payload instanceof ClientSession cs) {
            clientSession = cs;
            if (cs.getUser() != null) token = cs.getUser().getSessionCookie();
        } else {
            invalidRequest("RECONNECT payload must be a session token or ClientSession.");
            return;
        }

        if (token == null) {
            invalidRequest("No session token provided for RECONNECT.");
            return;
        }

        // Restore session from session manager
        User stored = sessionManager.getUser(token);
        if (stored == null) {
            invalidRequest("Invalid session token. No session found.");
            return;
        }

        // Restore session into this handler
        this.user = new User(stored.getUsername(), stored.getPassword());
        this.user.setSessionCookie(token);
        this.isLoggedIn = true;

        // If client provided a desired room in the ClientSession payload, rejoin it.
        Room desired = clientSession != null ? clientSession.getRoom() : null;
        this.currentRoom = null;
        if (desired != null) {
            this.currentRoom = roomManager.joinRoom(desired.getName(), this);
        }

        new Request(RequestType.SUCCESS, this.currentRoom).sendRequest(out);
        System.out.println("[ClientHandler] Reconnected session restored for token " + token);
    }

    private void logout() {
        if (currentRoom != null)
            roomManager.leaveRoom(currentRoom.getName(), this);
        isLoggedIn  = false;
        if (user != null) {
            String token = user.getSessionCookie();
            if (token != null) {
                sessionManager.remove(token);
            }
        }
        user        = null;
        currentRoom = null;
        System.out.println("[ClientHandler] User logged out.");
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void notImplementedRequest(RequestType type) throws IOException {
        new Request(RequestType.ERROR, "Request type " + type.name() + " not implemented.")
            .sendRequest(out);
    }

    private void invalidRequest(Object reason) throws IOException {
        new Request(RequestType.ERROR, "Invalid request: " + reason).sendRequest(out);
    }

    @Override
    public String toString() {
        return user != null ? "Client[" + user.getUsername() + "]" : "Client[unauthenticated]";
    }
}