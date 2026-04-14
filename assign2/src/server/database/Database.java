package server.database;

public class Database{
    private String path;

    public Database(String path) {
        this.path = path;
        if (this.path == null || this.path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

    }
}