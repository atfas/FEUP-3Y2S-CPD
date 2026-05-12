package server.common;

import java.io.*;
import java.net.*;

import java.util.ArrayList;
import java.util.List;

import common.protocol.Request;
import common.protocol.RequestType;
import common.models.Room;
import common.models.User;
import common.models.Message;

public class ClientHandler implements Runnable {
    static private List<ClientHandler> connections = new ArrayList<>();

    private Socket socket;

    private User user;
    private Room currentRoom;
    private boolean isLoggedIn;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.user = null;
        this.currentRoom = null;
        this.isLoggedIn = false;
    }

    @Override
    public void run() {
        try {
            Request request = Request.getRequest(socket);

            switch (request.getType()) {
                case LOGIN:
                    login(request.getPayload());
                    break;
                case REGISTER:
                    // TODO: Implement Registration request
                    notImplementedRequest(socket, RequestType.REGISTER);
                    break;
                case CHAT_MESSAGE:
                    if (!isLoggedIn) invalidRequest(socket, "User must be logged in to send messages.");
                    else sendMessage(request.getPayload());
                    break;
                case LOGOUT:
                    logout();
                    break;
                case RECONNECT:
                    // TODO: Implement reconnection handling
                    notImplementedRequest(socket, RequestType.RECONNECT);
                    break;
                case UNKNOWN:
                    invalidRequest(socket, request.getPayload());
                    break;
                default:
                    notImplementedRequest(socket, request.getType());
                    break;
            }
        } catch (IOException ex) {
            System.out.println("Client handler exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void notImplementedRequest(Socket socket, RequestType type) throws IOException {
        new Request(RequestType.ERROR, "Request type " + type.name() + " not implemented.").sendRequest(socket);
    }

    private void invalidRequest(Socket socket, Object payload) throws IOException {
        new Request(RequestType.ERROR, "Invalid request").sendRequest(socket);
    }

    private void login(Object payload){
        if (!(payload instanceof User)) return;
        User loginUser = (User) payload;

        // TODO: Validate credentials against the database in a thread? and set isLoggedIn accordingly.
        // For now, we will just accept any login for demonstration purposes.

        this.user = loginUser;
        this.isLoggedIn = true;
    }

    private void register(Object payload) {
        if (!(payload instanceof User)) return;
        User newUser = (User) payload;

        // TODO: Implement registration logic, including validation and database interaction.

        login(newUser);
    }

    private void sendMessage(Object payload) {
        if (!(payload instanceof Message)) return;
        Message message = (Message) payload;

        // Ensure the message sender is the logged-in user
        message = new Message(user, message.getRecipient(), message.getContent());
        currentRoom = message.getRecipient();

        // TODO: Handle sending the message to the current room, by broadcasting it to all clients in the room
        // Use a tread to handle the broadcasting to avoid blocking the client handler thread.

    }

    private void logout() {
        isLoggedIn = false;
        user = null;
        currentRoom = null;
    }
}
