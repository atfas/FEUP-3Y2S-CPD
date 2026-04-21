package server;

import java.io.*;
import java.net.*;

import server.common.ClientHandler;

public class ChatServer {

    public static void main(String[] args) {
        if (args.length < 1) return;
 
        int port = Integer.parseInt(args[0]);
 
        try (ServerSocket serverSocket = new ServerSocket(port)) {
 
            System.out.println("Server is listening on port " + port);
 
            while (true) {
                Socket socket = serverSocket.accept();
                
                ClientHandler handler = new ClientHandler(socket);
                Thread.ofVirtual().start(handler);
            }
 
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
