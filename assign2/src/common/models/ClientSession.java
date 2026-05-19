package common.models;

import java.io.Serializable;

public class ClientSession implements Serializable {

    private final User user;
    private final Room room;

    public ClientSession(User user, Room room) {
        this.user = user;
        this.room = room;
    }

    public User getUser() {
        return user;
    }

    public Room getRoom() {
        return room;
    }
}