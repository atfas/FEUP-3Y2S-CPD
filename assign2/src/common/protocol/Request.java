package common.protocol;

import java.io.*;
import java.net.*;

public class Request {
    private RequestType type;
    private Object payload;

    public Request(RequestType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public void sendRequest(Socket socket) throws IOException {
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.writeObject(this);
    }

    public static Request getRequest(Socket socket) throws IOException{
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        try {
            Object obj = in.readObject();
            if (obj instanceof Request) {
                return (Request) obj;
            } else {
                return new Request(RequestType.UNKNOWN, obj);
            }
        } catch (ClassNotFoundException e) {
            return new Request(RequestType.UNKNOWN, null);
        }
    }

    public RequestType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}
