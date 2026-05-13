package client;

import java.io.*;
import java.net.*;

public class ChatClient {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ChatClient <hostname> <port>");
            return;
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            ConnectionManager connection = new ConnectionManager(hostname, port);
            UIHandler ui = new UIHandler(connection);
            ui.start();

        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("Connection error: " + ex.getMessage());
        }
    }
}