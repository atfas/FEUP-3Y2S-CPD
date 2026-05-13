package common.models;

import java.io.Serializable;

public class Room implements Serializable {
    private String name;

    public Room(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Room name cannot be null or blank");
        this.name = name.trim().toLowerCase();
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room)) return false;
        return name.equals(((Room) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Room{" + name + "}";
    }
}