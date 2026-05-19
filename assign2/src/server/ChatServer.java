package server;

import java.io.*;
import java.net.*;

import database.Database;
import server.common.ClientHandler;
import server.common.RoomManager;
import server.common.SessionManager;

public class ChatServer {

    public static void main(String[] args) {
        if (args.length < 1) return;

        int port = Integer.parseInt(args[0]);

        // Shared state — one instance passed to every ClientHandler.
        Database    database    = new Database("database/users.txt");
        RoomManager roomManager = new RoomManager();
        SessionManager sessionManager = new SessionManager();

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(socket, database, roomManager, sessionManager);
                Thread.ofVirtual().start(handler);
            }

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}