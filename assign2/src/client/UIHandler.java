package client;

import java.io.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import common.models.Message;
import common.models.Room;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;

/**
 * Handles all console input/output for the chat client.
 *
 * Stream ownership rule — only ONE thread ever calls connection.receive().
 * That is the background reader thread, which runs for the whole session.
 * It puts every incoming Request into `inbox`.
 *
 * chatLoop()  polls inbox and prints chat messages.
 * roomLoop()  polls inbox and waits for ROOMS_LIST / SUCCESS responses.
 *
 * Writing (send) is only ever done from the main thread, so no lock needed there.
 */
public class UIHandler {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";

    private final ConnectionManager      connection;
    private final BufferedReader         input;
    private final BlockingQueue<Request> inbox = new LinkedBlockingQueue<>();

    private User loggedInUser = null;
    private Room currentRoom  = null;

    public UIHandler(ConnectionManager connection) {
        this.connection = connection;
        this.input      = new BufferedReader(new InputStreamReader(System.in));
    }

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //

    public void start() throws IOException {
        // Start the single background reader immediately.
        // It fills `inbox`; nothing else ever calls connection.receive().
        Thread.ofVirtual().start(this::readLoop);

        printWelcome();
        authLoop();
        roomLoop();
        chatLoop();
    }

    // ------------------------------------------------------------------ //
    //  Background reader — the ONLY thread that calls connection.receive()
    // ------------------------------------------------------------------ //

    private void readLoop() {
        try {
            while (connection.isConnected()) {
                Request req = connection.receive();
                inbox.put(req);
            }
        } catch (IOException | InterruptedException ignored) {}
    }

    /** Blocks until the next Request arrives in the inbox. */
    private Request nextResponse() throws IOException {
        try {
            return inbox.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for server response", e);
        }
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
                case "0" -> { System.out.println(YELLOW + "Goodbye!" + RESET); connection.close(); System.exit(0); }
                default  -> printError("Please enter 1, 2, or 0.");
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

        connection.send(new Request(RequestType.LOGIN, new User(username, password)));
        Request response = nextResponse();

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

        if (!password.equals(confirm)) { printError("Passwords do not match."); return; }

        connection.send(new Request(RequestType.REGISTER, new User(username, password)));
        Request response = nextResponse();

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
            connection.send(new Request(RequestType.LIST_ROOMS, null));

            // Drain any stale chat messages that arrived before our response.
            Request response = drainUntil(RequestType.ROOMS_LIST);

            if (response == null || response.getType() != RequestType.ROOMS_LIST) {
                printError("Could not fetch room list.");
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

            try {
                int index = Integer.parseInt(choice) - 1;
                if (index >= 0 && index < rooms.size()) { joinRoom(rooms.get(index)); continue; }
            } catch (NumberFormatException ignored) {}

            if (!choice.isBlank()) joinRoom(new Room(choice));
            else printError("Please enter a number from the list or a room name.");
        }
    }

    /**
     * Reads from inbox, printing any CHAT_MESSAGE frames that arrive
     * while we're waiting, until we see a frame of the expected type.
     */
    private Request drainUntil(RequestType expected) throws IOException {
        while (true) {
            Request req = nextResponse();
            if (req.getType() == expected) return req;

            // A chat message snuck in — print it so it isn't lost.
            if (req.getType() == RequestType.CHAT_MESSAGE
                    && req.getPayload() instanceof Message msg) {
                printIncomingMessage(msg);
            }
            // Any other unexpected frame: discard and keep waiting.
        }
    }

    private void printRoomMenu(List<Room> rooms) {
        System.out.println();
        System.out.println(CYAN + BOLD + "┌─ Available Rooms ─────────────────────┐" + RESET);
        if (rooms.isEmpty()) {
            System.out.println(CYAN + BOLD + "│  (no rooms yet)                       │" + RESET);
        } else {
            for (int i = 0; i < rooms.size(); i++) {
                String entry = String.format("│  %2d) %-34s│", i + 1, rooms.get(i).getName());
                System.out.println(CYAN + BOLD + entry + RESET);
            }
        }
        System.out.println(CYAN + BOLD + "│   0) Exit                             │" + RESET);
        System.out.println(CYAN + BOLD + "└───────────────────────────────────────┘" + RESET);
        System.out.println(YELLOW + "  Enter a number to join, or type a new room name to create it." + RESET);
        System.out.println();
    }

    private void joinRoom(Room room) throws IOException {
        connection.send(new Request(RequestType.ENTER_ROOM, room));
        Request response = drainUntil(RequestType.SUCCESS);
        if (response.getType() == RequestType.SUCCESS) {
            currentRoom = (Room) response.getPayload();
            System.out.println(GREEN + "\nJoined room: #" + currentRoom.getName() + RESET);
        } else {
            printError("Could not join room: " + response.getPayload());
        }
    }

    // ------------------------------------------------------------------ //
    //  Chat loop
    // ------------------------------------------------------------------ //

    private void chatLoop() throws IOException {
        printChatHeader();

        // Drain inbox on a background thread so messages print while user types.
        Thread.ofVirtual().start(this::chatDrainLoop);

        while (connection.isConnected()) {
            String line = readLine(loggedInUser.getUsername());

            if (line == null || line.equalsIgnoreCase("/close")) {
                System.out.println(YELLOW + "Disconnecting. Goodbye!" + RESET);
                connection.close();
                break;
            }

            if (line.equalsIgnoreCase("/rooms")) {
                currentRoom = null;
                // chatDrainLoop is still running but roomLoop() uses drainUntil()
                // which correctly handles stray CHAT_MESSAGE frames in the queue.
                roomLoop();
                printChatHeader();
                // A new chatDrainLoop is NOT needed — the existing one keeps running.
                continue;
            }

            if (line.isBlank()) continue;

            connection.send(new Request(RequestType.CHAT_MESSAGE,
                new Message(loggedInUser, currentRoom, line)));
        }
    }

    /**
     * Runs on a background thread during chat.
     * Pulls from inbox and prints CHAT_MESSAGE frames.
     * When roomLoop() is active it also pulls from inbox via drainUntil(),
     * so this thread may be starved temporarily — that's fine, it just blocks
     * on inbox.take() until something arrives.
     */
    private void chatDrainLoop() {
        try {
            while (connection.isConnected()) {
                Request req = inbox.take();
                if (req.getType() == RequestType.CHAT_MESSAGE
                        && req.getPayload() instanceof Message msg) {
                    printIncomingMessage(msg);
                    // Reprint the prompt so the user can keep typing.
                    System.out.print(BOLD + loggedInUser.getUsername() + RESET + ": ");
                    System.out.flush();
                }
                // Non-chat frames (e.g. ERROR) during chat are silently ignored here.
            }
        } catch (InterruptedException ignored) {}
    }

    // ------------------------------------------------------------------ //
    //  Shared print helper
    // ------------------------------------------------------------------ //

    private void printIncomingMessage(Message msg) {
        // \r moves to start of line; \033[2K erases it entirely —
        // this wipes whatever the user was typing mid-line so the
        // incoming message prints cleanly without leftover prompt text.
        System.out.print("\r\033[2K");
        if (msg.getSender() == null) {
            System.out.println(YELLOW + "* " + msg.getContent() + RESET);
        } else {
            System.out.println(CYAN + BOLD + msg.getSender().getUsername()
                + RESET + ": " + msg.getContent());
        }
    }

    private void printChatHeader() {
        System.out.println();
        System.out.println(YELLOW + "You are in " + BOLD + "#" + currentRoom.getName() + RESET
            + YELLOW + ". Type a message and press Enter." + RESET);
        System.out.println(YELLOW + "Commands: " + BOLD + "/rooms" + RESET + YELLOW
            + " to switch rooms, " + BOLD + "/close" + RESET + YELLOW + " to quit." + RESET);
        System.out.println();
    }

    // ------------------------------------------------------------------ //
    //  Input helpers
    // ------------------------------------------------------------------ //

    private String readLine(String prompt) throws IOException {
        System.out.print(BOLD + prompt + RESET + ": ");
        return input.readLine();
    }

    private String readPassword(String prompt) throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword(BOLD + prompt + RESET + ": ");
            return pwd != null ? new String(pwd) : "";
        }
        return readLine(prompt);
    }

    private void printError(String message) {
        System.out.println(RED + "\n" + message + RESET + "\n");
    }
}