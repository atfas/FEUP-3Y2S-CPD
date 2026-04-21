package server.common;

import java.io.*;
import java.net.*;

import common.protocol.Request;
import common.protocol.RequestType;;

public class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            Request request = Request.getRequest(socket);

            switch (request.getType()) {
                case LOGIN:
                    // TODO: Implement Login request
                    notImplementedRequest(socket, RequestType.LOGIN);
                    break;
                case REGISTER:
                    // TODO: Implement Registration request
                    notImplementedRequest(socket, RequestType.REGISTER);
                    break;
                case CHAT_MESSAGE:
                    // TODO: Implement Chat message handling
                    notImplementedRequest(socket, RequestType.CHAT_MESSAGE);
                    break;
                case LOGOUT:
                    // TODO: Implement Logout request
                    notImplementedRequest(socket, RequestType.LOGOUT);
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
        OutputStream output = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);

        writer.println("Request type " + type.name() + " not implemented yet.");
    }

    private void invalidRequest(Socket socket, String payload) throws IOException {
        OutputStream output = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);

        writer.println("Invalid request: " + payload);
    }
}
