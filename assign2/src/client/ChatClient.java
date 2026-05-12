package client;

import java.net.*;
import java.io.*;


public class ChatClient {
    public static void main(String[] args) {
        if (args.length < 2)
            return;

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(hostname, port)) {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            Auth auth = new Auth();

            while (true) {
                auth.authenticate(in, out);

                if (auth.isLoggedIn()) {
                    RoomSelector roomSelector = new RoomSelector(auth.getCurrentUser());
                    roomSelector.selectRoomPage(in, out);
                } else {
                    break;
                }
            }

            socket.close();

        } catch (UnknownHostException ex) {

            System.out.println("Server not found: " + ex.getMessage());

        } catch (IOException ex) {

            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}