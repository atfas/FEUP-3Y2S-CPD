package common.protocol;

import java.io.*;
import java.net.*;

public class Request {
    private RequestType type;
    private String payload;

    public Request(RequestType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public static Request getRequest(Socket socket) throws IOException {
        InputStream input = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));

        String line = reader.readLine();

        try {
            RequestType type = RequestType.valueOf(line.split(":")[0]);
            String payload = line.split(":", 2)[1];
            return new Request(type, payload);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // If the request type is unknown or the format is incorrect, return an UNKNOWN request
            return new Request(RequestType.UNKNOWN, line);
        }
    }

    public static void sendRequest(Socket socket, Request request) throws IOException {
        OutputStream output = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);

        // Send the request as a string in the format "TYPE:payload"
        writer.println(request.getType().name() + ":" + request.getPayload());
    }

    public RequestType getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }
}
