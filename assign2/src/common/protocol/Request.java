package common.protocol;

import java.io.*;
import java.net.*;

public class Request implements Serializable {
    private RequestType type;
    private Object payload;

    public Request(RequestType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public void sendRequest(ObjectOutputStream out) throws IOException {
        out.writeObject(this);
    }

    public static Request getRequest(ObjectInputStream in) throws IOException{
        try {
            Object obj = in.readObject();
            if (obj instanceof Request) {
                return (Request) obj;
            } else {
                return new Request(RequestType.UNKNOWN, obj);
            }
        } catch (ClassNotFoundException | StreamCorruptedException e) {
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
