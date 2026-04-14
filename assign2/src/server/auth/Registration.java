package server.auth;

import database.Database;
import database.Database.DatabaseException;

/**
 * Handles new-user registration in its own thread.
 *
 * The run() method tries to add the user to the database.
 * The result (success or reason for failure) is stored in
 * `result` and can be read after the thread finishes, or via
 * the callback if one is provided.
 */
public class Registration implements Runnable {

    // ------------------------------------------------------------------ //
    //  Possible outcomes
    // ------------------------------------------------------------------ //

    public enum Result {
        SUCCESS,
        USER_ALREADY_EXISTS,
        DATABASE_ERROR,
        INVALID_INPUT
    }

    // ------------------------------------------------------------------ //
    //  Optional callback so the caller can react without polling
    // ------------------------------------------------------------------ //

    @FunctionalInterface
    public interface RegistrationCallback {
        void onComplete(String username, Result result);
    }

    // ------------------------------------------------------------------ //
    //  Fields
    // ------------------------------------------------------------------ //

    private final String   username;
    private final String   password;
    private final Database database;
    private final RegistrationCallback callback;   // may be null

    private volatile Result result;                // readable after thread ends

    // ------------------------------------------------------------------ //
    //  Constructors
    // ------------------------------------------------------------------ //

    /** Without callback — poll `getResult()` after joining the thread. */
    public Registration(String username, String password, Database database) {
        this(username, password, database, null);
    }

    /** With callback — called on the registration thread when done. */
    public Registration(String username, String password,
                        Database database, RegistrationCallback callback) {
        this.username = username;
        this.password = password;
        this.database = database;
        this.callback = callback;
    }

    // ------------------------------------------------------------------ //
    //  Runnable
    // ------------------------------------------------------------------ //

    @Override
    public void run() {
        System.out.println("[Registration] Attempting to register user: " + username);

        // Basic validation before touching the database.
        if (username == null || username.isBlank() ||
                password == null || password.isEmpty()) {

            result = Result.INVALID_INPUT;
            System.out.println("[Registration] Invalid input for user: " + username);
            notifyCallback();
            return;
        }

        try {
            boolean added = database.addUser(username, password);

            if (added) {
                result = Result.SUCCESS;
                System.out.println("[Registration] Successfully registered: " + username);
            } else {
                result = Result.USER_ALREADY_EXISTS;
                System.out.println("[Registration] User already exists: " + username);
            }

        } catch (DatabaseException e) {
            result = Result.DATABASE_ERROR;
            System.out.println("[Registration] Database error for user "
                    + username + ": " + e.getMessage());
        }

        notifyCallback();
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //

    /** Returns the outcome, or null if the thread has not finished yet. */
    public Result getResult() {
        return result;
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private void notifyCallback() {
        if (callback != null)
            callback.onComplete(username, result);
    }

    // ------------------------------------------------------------------ //
    //  Main — quick smoke-test
    // ------------------------------------------------------------------ //

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Registration smoke-test ===");

        Database database = new Database("database/test_users.txt");

        // --- Test 1: register a new user ---
        Registration r1 = new Registration("alice", "secret123", database,
                (user, res) -> System.out.println("[Callback] " + user + " -> " + res));

        Thread t1 = Thread.ofVirtual().start(r1);
        t1.join();

        // --- Test 2: duplicate registration ---
        Registration r2 = new Registration("alice", "secret123", database,
                (user, res) -> System.out.println("[Callback] " + user + " -> " + res));

        Thread t2 = Thread.ofVirtual().start(r2);
        t2.join();

        // --- Test 3: another new user ---
        Registration r3 = new Registration("bob", "hunter2", database,
                (user, res) -> System.out.println("[Callback] " + user + " -> " + res));

        Thread t3 = Thread.ofVirtual().start(r3);
        t3.join();

        // --- Test 4: invalid input ---
        Registration r4 = new Registration("", "pass", database,
                (user, res) -> System.out.println("[Callback] '" + user + "' -> " + res));

        Thread t4 = Thread.ofVirtual().start(r4);
        t4.join();

        System.out.println("=== Done. Check test_users.txt for persisted data. ===");
    }
}