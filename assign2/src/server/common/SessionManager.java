package server.common;

import common.models.User;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

/**
 * Lightweight in-memory session registry mapping tokens to Users.
 */
public class SessionManager {

    private final Map<String, User> sessions = new HashMap<>();

    /** Create a session for the given user and return the token. */
    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        User stored = new User(user.getUsername(), user.getPassword());
        stored.setSessionCookie(token);
        synchronized (sessions) {
            // In the unlikely event of a token collision, generate a new token.
            while (sessions.containsKey(token)) {
                token = UUID.randomUUID().toString();
                stored.setSessionCookie(token);
            }
            sessions.put(token, stored);
        }
        return token;
    }

    /** Return the user associated with a token, or null if none. */
    public User getUser(String token) {
        return token == null ? null : sessions.get(token);
    }

    /** Remove a session by token. */
    public void remove(String token) {
        if (token == null) return;
        synchronized (sessions) {
            sessions.remove(token);
        }
    }
}
