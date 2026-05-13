package client;

import java.io.*;
import java.util.List;

import common.models.Room;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;

/**
 * Handles all console input/output for the chat client.
 *
 * Responsibilities:
 *   - Welcome screen
 *   - Auth menu (login / register)
 *   - Room selection
 *   - Chat loop
 */
public class UIHandler {

    // ANSI colours — fall back gracefully if the terminal doesn't support them.
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";

    private final ConnectionManager connection;
    private final BufferedReader    input;

    private User loggedInUser = null;
    private Room currentRoom  = null;

    public UIHandler(ConnectionManager connection) {
        this.connection = connection;
        this.input      = new BufferedReader(new InputStreamReader(System.in));
    }

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //

    /**
     * Runs the full client session:
     *   1. Welcome screen
     *   2. Auth loop (until logged in)
     *   3. Room selection loop (until a room is joined)
     *   4. Chat loop
     */
    public void start() throws IOException {
        printWelcome();
        authLoop();
        roomLoop();
        chatLoop();
    }

    // ------------------------------------------------------------------ //
    //  Welcome screen
    // ------------------------------------------------------------------ //

    private void printWelcome() {
        System.out.println();
        System.out.println(CYAN + BOLD + "╔══════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + BOLD + "║                                                          ║" + RESET);
        System.out.println(CYAN + BOLD + "║          Welcome to the CPD Chat System                  ║" + RESET);
        System.out.println(CYAN + BOLD + "║                                                          ║" + RESET);
        System.out.println(CYAN + BOLD + "║   Made by:                                               ║" + RESET);
        System.out.println(CYAN + BOLD + "║     ·  Afonso Saraiva                                    ║" + RESET);
        System.out.println(CYAN + BOLD + "║     ·  Raluca                                            ║" + RESET);
        System.out.println(CYAN + BOLD + "║     ·  Daniel Pinto                                      ║" + RESET);
        System.out.println(CYAN + BOLD + "║     ·  Francisco Borralho                                ║" + RESET);
        System.out.println(CYAN + BOLD + "║                                                          ║" + RESET);
        System.out.println(CYAN + BOLD + "╚══════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  Auth loop
    // ------------------------------------------------------------------ //

    private void authLoop() throws IOException {
        while (loggedInUser == null) {
            printAuthMenu();
            String choice = readLine("Your choice").trim();

            switch (choice) {
                case "1" -> doLogin();
                case "2" -> doRegister();
                case "0" -> {
                    System.out.println(YELLOW + "Goodbye!" + RESET);
                    connection.close();
                    System.exit(0);
                }
                default -> printError("Please enter 1, 2, or 0.");
            }
        }
    }

    private void printAuthMenu() {
        System.out.println(BOLD + "┌─ What would you like to do? ─────────┐" + RESET);
        System.out.println(BOLD + "│  1) Login                            │" + RESET);
        System.out.println(BOLD + "│  2) Register                         │" + RESET);
        System.out.println(BOLD + "│  0) Exit                             │" + RESET);
        System.out.println(BOLD + "└──────────────────────────────────────┘" + RESET);
    }

    private void doLogin() throws IOException {
        System.out.println(CYAN + "\n── Login ──────────────────────────────" + RESET);
        String username = readLine("Username");
        String password = readPassword("Password");

        Request response = connection.sendAndReceive(
            new Request(RequestType.LOGIN, new User(username, password))
        );

        if (response.getType() == RequestType.SUCCESS) {
            loggedInUser = new User(username, password);
            System.out.println(GREEN + "\nLogin successful! Welcome back, " + username + "!" + RESET);
        } else {
            printError("Login failed: " + response.getPayload());
        }
    }

    private void doRegister() throws IOException {
        System.out.println(CYAN + "\n── Register ───────────────────────────" + RESET);
        String username = readLine("Choose a username");
        String password = readPassword("Choose a password");
        String confirm  = readPassword("Confirm password");

        if (!password.equals(confirm)) {
            printError("Passwords do not match. Please try again.");
            return;
        }

        Request response = connection.sendAndReceive(
            new Request(RequestType.REGISTER, new User(username, password))
        );

        if (response.getType() == RequestType.SUCCESS) {
            loggedInUser = new User(username, password);
            System.out.println(GREEN + "\nAccount created! Welcome, " + username + "!" + RESET);
        } else {
            printError("Registration failed: " + response.getPayload());
        }
    }

    // ------------------------------------------------------------------ //
    //  Room selection loop
    // ------------------------------------------------------------------ //

    @SuppressWarnings("unchecked")
    private void roomLoop() throws IOException {
        while (currentRoom == null) {
            // Ask the server for the current room list.
            Request response = connection.sendAndReceive(
                new Request(RequestType.LIST_ROOMS, null)
            );

            if (response.getType() != RequestType.ROOMS_LIST) {
                printError("Could not fetch room list: " + response.getPayload());
                continue;
            }

            List<Room> rooms = (List<Room>) response.getPayload();
            printRoomMenu(rooms);

            String choice = readLine("Your choice").trim();

            if (choice.equals("0")) {
                System.out.println(YELLOW + "Goodbye!" + RESET);
                connection.close();
                System.exit(0);
            }

            // Numeric choice — pick from list.
            try {
                int index = Integer.parseInt(choice) - 1;
                if (index >= 0 && index < rooms.size()) {
                    joinRoom(rooms.get(index));
                    continue;
                }
            } catch (NumberFormatException ignored) {}

            // Non-numeric or out-of-range — treat as a custom room name.
            if (!choice.isBlank()) {
                joinRoom(new Room(choice));
            } else {
                printError("Please enter a number from the list or a room name.");
            }
        }
    }

    private void printRoomMenu(List<Room> rooms) {
        System.out.println();
        System.out.println(CYAN + BOLD + "┌─ Available Rooms ─────────────────────┐" + RESET);
        if (rooms.isEmpty()) {
            System.out.println(CYAN + BOLD + "│  (no rooms yet)                       │" + RESET);
        } else {
            for (int i = 0; i < rooms.size(); i++) {
                String line = String.format("│  %2d) %-34s│", i + 1, rooms.get(i).getName());
                System.out.println(CYAN + BOLD + line + RESET);
            }
        }
        System.out.println(CYAN + BOLD + "│   0) Exit                             │" + RESET);
        System.out.println(CYAN + BOLD + "└───────────────────────────────────────┘" + RESET);
        System.out.println(YELLOW + "  Enter a number to join, or type a new room name to create it." + RESET);
        System.out.println();
    }

    private void joinRoom(Room room) throws IOException {
        Request response = connection.sendAndReceive(
            new Request(RequestType.ENTER_ROOM, room)
        );

        if (response.getType() == RequestType.SUCCESS) {
            currentRoom = (Room) response.getPayload();
            System.out.println(GREEN + "\nJoined room: " + currentRoom.getName() + RESET);
        } else {
            printError("Could not join room: " + response.getPayload());
        }
    }

    // ------------------------------------------------------------------ //
    //  Chat loop
    // ------------------------------------------------------------------ //

    private void chatLoop() throws IOException {
        System.out.println();
        System.out.println(YELLOW + "You are in " + BOLD + "#" + currentRoom.getName() + RESET
            + YELLOW + ". Type a message and press Enter." + RESET);
        System.out.println(YELLOW + "Commands: " + BOLD + "/rooms" + RESET + YELLOW
            + " to switch rooms, " + BOLD + "/close" + RESET + YELLOW + " to quit." + RESET);
        System.out.println();

        while (connection.isConnected()) {
            String line = readLine(loggedInUser.getUsername());

            if (line == null || line.equalsIgnoreCase("/close")) {
                System.out.println(YELLOW + "Disconnecting. Goodbye!" + RESET);
                connection.close();
                break;
            }

            if (line.equalsIgnoreCase("/rooms")) {
                currentRoom = null;
                roomLoop();
                System.out.println(YELLOW + "Now in " + BOLD + "#" + currentRoom.getName()
                    + RESET + YELLOW + ". Keep chatting!" + RESET);
                continue;
            }

            if (line.isBlank()) continue;

            // TODO: send CHAT_MESSAGE request with current room
        }
    }

    // ------------------------------------------------------------------ //
    //  Input helpers
    // ------------------------------------------------------------------ //

    private String readLine(String prompt) throws IOException {
        System.out.print(BOLD + prompt + RESET + ": ");
        return input.readLine();
    }

    /**
     * Reads a password. Uses Console for hidden input when available
     * (i.e. when running in a real terminal), otherwise falls back to
     * plain BufferedReader (e.g. when stdin is piped in tests).
     */
    private String readPassword(String prompt) throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword(BOLD + prompt + RESET + ": ");
            return pwd != null ? new String(pwd) : "";
        }
        return readLine(prompt);
    }

    // ------------------------------------------------------------------ //
    //  Output helpers
    // ------------------------------------------------------------------ //

    private void printError(String message) {
        System.out.println(RED + "\n" + message + RESET + "\n");
    }
}