package database;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-backed user database.
 *
 * Each line in the file has the format:  username:sha256_hex
 *
 * All public methods are thread-safe via a ReentrantReadWriteLock:
 *   - reads (userExists, validateUser) acquire the read lock so many
 *     threads can query concurrently.
 *   - writes (addUser) acquire the write lock for exclusive access.
 */
public class Database {

    private final String path;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //

    public Database(String path) {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("Path cannot be null or empty");

        this.path = path;

        // Create the file if it does not exist yet.
        try {
            Path p = Path.of(path);
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent() == null ? Path.of(".") : p.getParent());
                Files.createFile(p);
                System.out.println("[Database] Created new database file at: " + path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialise database file: " + e.getMessage(), e);
        }
    }

    /**
     * Adds a new user.
     *
     * @return true  if the user was added successfully.
     *         false if a user with that username already exists.
     * @throws DatabaseException if an I/O error occurs.
     */
    public boolean addUser(String username, String password) throws DatabaseException {
        validateInput(username, password);

        lock.writeLock().lock();
        try {
            if (findUser(username) != null)
                return false;                          // duplicate

            String hashed = hash(password);
            String line   = username + ":" + hashed + System.lineSeparator();
            Files.writeString(Path.of(path), line, StandardOpenOption.APPEND);
            System.out.println("[Database] User added: " + username);
            return true;

        } catch (IOException e) {
            throw new DatabaseException("Failed to add user: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return true if a user with the given username exists in the database.
     * @throws DatabaseException if an I/O error occurs.
     */
    public boolean userExists(String username) throws DatabaseException {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be null or blank");

        lock.readLock().lock();
        try {
            return findUser(username) != null;
        } catch (IOException e) {
            throw new DatabaseException("Failed to check user: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validates a login attempt.
     *
     * @return true if the credentials match a stored user.
     * @throws DatabaseException if an I/O error occurs.
     */
    public boolean validateUser(String username, String password) throws DatabaseException {
        validateInput(username, password);

        lock.readLock().lock();
        try {
            String storedHash = findUser(username);
            if (storedHash == null)
                return false;                          // user not found

            return storedHash.equals(hash(password));

        } catch (IOException e) {
            throw new DatabaseException("Failed to validate user: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Scans the file for a line whose username matches.
     * Returns the stored password hash, or null if not found.
     *
     * Must be called while holding at least the read lock.
     */
    private String findUser(String username) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2 && parts[0].equals(username))
                    return parts[1];
            }
        }
        return null;
    }

    /** SHA-256 hash of a string, returned as a lowercase hex string. */
    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec — this never happens.
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static void validateInput(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username cannot be null or blank");
        if (password == null || password.isEmpty())
            throw new IllegalArgumentException("Password cannot be null or empty");
    }

    // ------------------------------------------------------------------ //
    //  Checked exception for callers to handle cleanly
    // ------------------------------------------------------------------ //

    public static class DatabaseException extends Exception {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}