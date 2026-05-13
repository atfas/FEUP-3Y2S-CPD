package server.common;

import java.io.*;
import java.net.*;
import java.util.List;

import common.models.Message;
import common.models.Room;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;
import database.Database;
import database.Database.DatabaseException;
import server.auth.Registration;

public class ClientHandler implements Runnable {

    private final Socket      socket;
    private final Database    database;
    private final RoomManager roomManager;

    private User    user;
    private Room    currentRoom;
    private boolean isLoggedIn;

    public ClientHandler(Socket socket, Database database, RoomManager roomManager) {
        this.socket      = socket;
        this.database    = database;
        this.roomManager = roomManager;
        this.user        = null;
        this.currentRoom = null;
        this.isLoggedIn  = false;
    }

    // ------------------------------------------------------------------ //
    //  Runnable
    // ------------------------------------------------------------------ //

    @Override
    public void run() {
        System.out.println("[ClientHandler] New connection from " + socket.getInetAddress());
        try {
            while (socket.isConnected()) {
                Request request = Request.getRequest(socket);

                switch (request.getType()) {
                    case LOGIN        -> login(request.getPayload());
                    case REGISTER     -> register(request.getPayload());
                    case CHAT_MESSAGE -> {
                        if (!isLoggedIn) invalidRequest(socket, "Must be logged in to send messages.");
                        else             sendMessage(request.getPayload());
                    }
                    case ENTER_ROOM   -> {
                        if (!isLoggedIn) invalidRequest(socket, "Must be logged in to join a room.");
                        else             enterRoom(request.getPayload());
                    }
                    case LEAVE_ROOM   -> leaveRoom();
                    case LIST_ROOMS   -> listRooms();
                    case LOGOUT       -> logout();
                    case RECONNECT    -> notImplementedRequest(socket, RequestType.RECONNECT);
                    case UNKNOWN      -> invalidRequest(socket, request.getPayload());
                    default           -> notImplementedRequest(socket, request.getType());
                }
            }
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
            invalidRequest(socket, "LOGIN payload must be a User.");
            return;
        }

        try {
            boolean valid = database.validateUser(loginUser.getUsername(), loginUser.getPassword());
            if (valid) {
                this.user = loginUser;
                this.isLoggedIn = true;
                new Request(RequestType.SUCCESS, null).sendRequest(socket);
                System.out.println("[ClientHandler] Login OK: " + loginUser.getUsername());
            } else {
                new Request(RequestType.ERROR, "Invalid username or password.").sendRequest(socket);
            }
        } catch (DatabaseException e) {
            new Request(RequestType.ERROR, "Server error during login.").sendRequest(socket);
        }
    }

    private void register(Object payload) throws IOException {
        if (!(payload instanceof User newUser)) {
            invalidRequest(socket, "REGISTER payload must be a User.");
            return;
        }

        // Run registration on the current virtual thread (already off the accept loop).
        Registration reg = new Registration(
            newUser.getUsername(),
            newUser.getPassword(),
            database
        );
        reg.run();   // synchronous — we need the result before replying

        switch (reg.getResult()) {
            case SUCCESS           -> {
                new Request(RequestType.SUCCESS, null).sendRequest(socket);
                // Automatically log in after registration.
                this.user       = newUser;
                this.isLoggedIn = true;
            }
            case USER_ALREADY_EXISTS ->
                new Request(RequestType.ERROR, "Username already taken.").sendRequest(socket);
            case INVALID_INPUT ->
                new Request(RequestType.ERROR, "Invalid username or password.").sendRequest(socket);
            case DATABASE_ERROR ->
                new Request(RequestType.ERROR, "Server error during registration.").sendRequest(socket);
        }
    }

    private void sendMessage(Object payload) throws IOException {
        if (!(payload instanceof Message message)) {
            invalidRequest(socket, "CHAT_MESSAGE payload must be a Message.");
            return;
        }
        if (currentRoom == null) {
            invalidRequest(socket, "You must join a room before sending messages.");
            return;
        }

        // Re-stamp the sender so clients can't spoof it.
        Message stamped = new Message(user, currentRoom, message.getContent());

        // Broadcast to everyone in the room.
        for (ClientHandler member : roomManager.getMembers(currentRoom.getName())) {
            if (member != this) {
                new Request(RequestType.CHAT_MESSAGE, stamped).sendRequest(member.socket);
            }
        }
    }

    private void enterRoom(Object payload) throws IOException {
        if (!(payload instanceof Room room)) {
            invalidRequest(socket, "ENTER_ROOM payload must be a Room.");
            return;
        }

        // Leave previous room if any.
        if (currentRoom != null)
            roomManager.leaveRoom(currentRoom.getName(), this);

        currentRoom = roomManager.joinRoom(room.getName(), this);
        new Request(RequestType.SUCCESS, currentRoom).sendRequest(socket);
    }

    private void leaveRoom() throws IOException {
        if (currentRoom != null) {
            roomManager.leaveRoom(currentRoom.getName(), this);
            currentRoom = null;
        }
        new Request(RequestType.SUCCESS, null).sendRequest(socket);
    }

    private void listRooms() throws IOException {
        List<Room> rooms = roomManager.listRooms();
        new Request(RequestType.SUCCESS, (java.io.Serializable) rooms).sendRequest(socket);
    }

    private void logout() {
        if (currentRoom != null)
            roomManager.leaveRoom(currentRoom.getName(), this);
        isLoggedIn  = false;
        user        = null;
        currentRoom = null;
        System.out.println("[ClientHandler] User logged out.");
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void notImplementedRequest(Socket socket, RequestType type) throws IOException {
        new Request(RequestType.ERROR, "Request type " + type.name() + " not implemented.")
            .sendRequest(socket);
    }

    private void invalidRequest(Socket socket, Object reason) throws IOException {
        new Request(RequestType.ERROR, "Invalid request: " + reason).sendRequest(socket);
    }

    @Override
    public String toString() {
        return user != null ? "Client[" + user.getUsername() + "]" : "Client[unauthenticated]";
    }
}