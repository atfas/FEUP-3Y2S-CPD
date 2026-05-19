package common.models;

import java.io.Serializable;

public class User implements Serializable {
    private String username;
    private String password;
    private String sessionCookie;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.sessionCookie = null; // assigned on login/registration, used for reconnects
    }

    public void setSessionCookie(String sessionCookie) {
        this.sessionCookie = sessionCookie;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getSessionCookie() {
        return sessionCookie;
    }
}
