package client;

import java.io.*;
import java.util.Scanner;

import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;

public class Auth {
    private User currentUser;
    private boolean isLoggedIn;

    public Auth() {
        this.currentUser = null;
        this.isLoggedIn = false;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void authenticate(ObjectInputStream in, ObjectOutputStream out) throws IOException {
        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        boolean exit = false;
        while (!exit) {
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            System.out.println("Choose an option: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    loginPage(in, out);
                    exit = true;
                    break;
                case "2":
                    registerPage(in, out);
                    exit = true;
                    break;
                case "3":
                    System.out.println("Exiting...");
                    exit = true;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void loginPage(ObjectInputStream in, ObjectOutputStream out) throws IOException {
        while (!isLoggedIn) {
            System.out.println("Username(/quit to menu): ");
            String username = System.console().readLine();
            if (username.equals("/quit"))
                return;

            System.out.println("Password: ");
            String password = new String(System.console().readPassword());

            User user = new User(username, password);

            new Request(RequestType.LOGIN, user).sendRequest(out);
            if (Request.getRequest(in).getType() == RequestType.SUCCESS) {
                System.out.println("Login successful!");
                this.currentUser = user;
                this.isLoggedIn = true;
            } else {
                System.out.println("Login failed");
            }
        }
    }

    private void registerPage(ObjectInputStream in, ObjectOutputStream out) throws IOException {
        while (!isLoggedIn) {
            System.out.println("Username(/quit to menu): ");
            String username = System.console().readLine();
            if (username.equals("/quit"))
                return;

            System.out.println("Password: ");
            String password = new String(System.console().readPassword());

            User user = new User(username, password);

            new Request(RequestType.REGISTER, user).sendRequest(out);

            Request response = Request.getRequest(in);
            
            if (response.getType() == RequestType.SUCCESS) {
                System.out.println("Registration successful!");
                this.currentUser = user;
                this.isLoggedIn = true;
            } else {
                System.out.println(response.getPayload());
            }
        }
    }

}
