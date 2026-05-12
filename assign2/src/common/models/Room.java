package common.models;

public class Room implements java.io.Serializable {
    private String name;

    public Room(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
