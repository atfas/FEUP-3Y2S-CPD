package client;

import java.net.*;
import java.io.*;

import common.protocol.Request;
import common.protocol.RequestType;
import common.models.User;

public class ChatClient {
    static boolean isLoggedIn = false;

    public static void main(String[] args) {
        if (args.length < 2)
            return;

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(hostname, port)) {
            do {
                login(socket);
            } while (!isLoggedIn);

            boolean isRunning = true;
            while (isRunning) {
                String message = new BufferedReader(new InputStreamReader(System.in)).readLine();
                if (message.equalsIgnoreCase("/close")) {
                    isRunning = false;
                }
            }

            socket.close();

        } catch (UnknownHostException ex) {

            System.out.println("Server not found: " + ex.getMessage());

        } catch (IOException ex) {

            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    private static void login(Socket socket) throws IOException {
        System.out.println("Username: ");
        String username = new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.println("Password: ");
        String password = new BufferedReader(new InputStreamReader(System.in)).readLine();

        User user = new User(username, password);
        new Request(RequestType.LOGIN, user).sendRequest(socket);
        Request response = Request.getRequest(socket);
        isLoggedIn = response.getType() == RequestType.SUCCESS;
        if (isLoggedIn) {
            System.out.println("Login successful!");
        } else {
            System.out.println("Login failed");
        }
    }
}