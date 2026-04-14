package server.autentication;

import server.database.Database;

public class Registration implements Runnable {
    private String username;
    private String password;
    private Database database;

    Registration(String username, String password, Database database) {
        this.username = username;
        this.password = password;
        this.database = database;
    }

    public void run() {
        //  TODO: Implement the registration logic to add a new user to the database
    }

    public static void main(String[] args) {
        System.out.println("Registration");
        Database database = new Database("test.txt");
        Registration registration = new Registration("user", "pass", database);
        Thread registrationThread = new Thread(registration);
        registrationThread.start();
    }
}