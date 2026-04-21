package server.common;

import java.io.*;
import java.net.*;
import java.util.Date;

public class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // For demonstration, just answer the client's message with a timestamp.
            // TODO: Remove this and implement actual Client functionality.
            date(socket);

            // TODO: Implement Login request
            // TODO: Implement Registration request
            // TODO: Implement Chat message handling
            // TODO: Implement Logout request
            // TODO: Implement reconnection handling

        } catch (IOException ex) {
            System.out.println("Client handler exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Simple date method that reads a line from the client and responds with the current date/time.
    private void date(Socket socket) throws IOException {
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            String time = reader.readLine();

            System.out.println("New client connected: " + time);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.println(new Date().toString());
    }
}
