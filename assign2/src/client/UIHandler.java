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
    // Messages typed while disconnected are queued here and flushed after reconnect
    private final BlockingQueue<String> pendingMessages = new LinkedBlockingQueue<>();

    private User loggedInUser = null;
    private Room currentRoom  = null;

    public UIHandler(ConnectionManager connection) {
        this.connection = connection;
        this.input      = new BufferedReader(new InputStreamReader(System.in));
    }

    private void startPendingSender() {
        Thread.ofVirtual().start(() -> {
            try {
                while (!connection.isClosed()) {
                    String msg = pendingMessages.take();

                    // Wait until reconnect finishes before attempting send
                    while (connection.isReconnecting()) {
                        if (connection.isClosed()) return;
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    try {
                        connection.send(new Request(RequestType.CHAT_MESSAGE,
                            new Message(loggedInUser, currentRoom, msg)));
                    } catch (IOException e) {
                        // Sending failed again — requeue and back off a bit
                        try { pendingMessages.put(msg); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Send a request but don't fail the app if the connection is temporarily
     * unavailable — wait for reconnection and retry until success or closed.
     */
    private void safeSend(Request req) throws IOException {
        boolean retried = false;
        while (true) {
            try {
                connection.send(req);
                return;
            } catch (IOException e) {
                if (!retried) {
                    System.out.println(YELLOW + "Connection problem while sending request. Waiting to reconnect..." + RESET);
                    retried = true;
                }
                
                // Wait for any in-flight reconnect attempts to complete.
                while (connection.isReconnecting()) {
                    if (connection.isClosed()) throw new IOException("Connection closed");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting for reconnect", ie);
                    }
                }
                if (connection.isClosed()) throw new IOException("Connection closed");
                // Loop and try again — ConnectionManager.send will attempt reconnects.
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //

    public void start() throws IOException {
        // Start the single background reader immediately.
        // It fills `inbox`; nothing else ever calls connection.receive().
        Thread.ofVirtual().start(this::readLoop);

        // Start background sender that flushes queued messages after reconnect
        startPendingSender();

        printWelcome();
        authLoop();
        roomLoop();
        chatLoop();
    }

    // ------------------------------------------------------------------ //
    //  Background reader — the ONLY thread that calls connection.receive()
    // ------------------------------------------------------------------ //

    private void readLoop() {
        while (!connection.isClosed()) {
            try {
                Request req = connection.receive();
                inbox.put(req);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break; // stop the reader thread on interrupt
            } catch (IOException ioe) {
                // transient — wait a bit and try again so reconnect can succeed
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
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
            loggedInUser.setSessionCookie((String) response.getPayload());
            connection.updateSession(loggedInUser, currentRoom);
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
            loggedInUser.setSessionCookie((String) response.getPayload());
            connection.updateSession(loggedInUser, currentRoom);
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
            try {
                connection.send(new Request(RequestType.LIST_ROOMS, null));
            } catch (IOException e) {
                printError("Could not fetch room list: " + e.getMessage());
                // Allow the user to retry or type a room name even while disconnected.
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to retry", ie);
                }
                continue;
            }

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

            if (choice.equalsIgnoreCase("/refresh")) continue;

            try {
                int index = Integer.parseInt(choice) - 1;
                if (index >= 0 && index < rooms.size()) { joinRoom(rooms.get(index)); continue; }
            } catch (NumberFormatException ignored) {}

            if (!choice.isBlank()) try {
                joinRoom(new Room(choice));
            } catch (IOException e) {
                printError("Could not join room: " + e.getMessage());
            }
            else printError("Please enter a number from the list or a room name.");
        }
    }

    /**
     * Reads from inbox, printing any CHAT_MESSAGE frames that arrive
     * while we're waiting, until we see a frame of the expected type.
     */
    private Request drainUntil(RequestType expected) throws IOException {
        while (true) {
            Request req;
            try {
                // Poll with a timeout so we can detect reconnects and avoid
                // blocking the UI indefinitely when the server is unreachable.
                req = inbox.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for server response", ie);
            }

            if (req == null) {
                // No response yet — if we're reconnecting, return null to let
                // the caller decide how to proceed instead of blocking forever.
                if (connection.isReconnecting()) return null;
                if (connection.isClosed()) throw new IOException("Connection closed");
                continue;
            }

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
        System.out.println(YELLOW + "  Use '/refresh' to update the room list." + RESET);
        System.out.println();
    }

    private void joinRoom(Room room) throws IOException {
        safeSend(new Request(RequestType.ENTER_ROOM, room));
        Request response = drainUntil(RequestType.SUCCESS);
        if (response != null && response.getType() == RequestType.SUCCESS) {
            currentRoom = (Room) response.getPayload();
            connection.updateSession(loggedInUser, currentRoom);
            System.out.println(GREEN + "\nJoined room: #" + currentRoom.getName() + RESET);
        } else {
            printError("Could not join room: " + response.getPayload());
        }
    }

    private void leaveRoom() throws IOException {
        safeSend(new Request(RequestType.LEAVE_ROOM, null));
        Request response = drainUntil(RequestType.SUCCESS);
        if (response != null && response.getType() == RequestType.SUCCESS) {
            System.out.println(GREEN + "\nLeft room: #" + currentRoom.getName() + RESET);
            currentRoom = null;
            connection.updateSession(loggedInUser, currentRoom);
        } else {
            printError("Could not leave room: " + response.getPayload());
        }
    }

    // ------------------------------------------------------------------ //
    //  Chat loop
    // ------------------------------------------------------------------ //

    private void chatLoop() throws IOException {
        printChatHeader();

        // Drain inbox on a background thread so messages print while user types.
        Thread chatDrainThread = Thread.ofVirtual().start(this::chatDrainLoop);

        while (!connection.isClosed()) {
            String line = readLine(loggedInUser.getUsername());

            if (line == null || line.equalsIgnoreCase("/close")) {
                System.out.println(YELLOW + "Disconnecting. Goodbye!" + RESET);
                connection.close();
                break;
            }

            if (line.equalsIgnoreCase("/rooms")) {
                // Stop the chat drain thread while we do room selection, to avoid interleaving chat messages with the room menu. It will be restarted when we re-enter chatLoop().
                chatDrainThread.interrupt();
                try {
                    leaveRoom();
                    roomLoop();
                } catch (IOException e) {
                    printError("Room switch failed: " + e.getMessage());
                }

                printChatHeader();

                chatDrainThread = Thread.ofVirtual().start(this::chatDrainLoop);
                continue;
            }

            if (line.isBlank()) continue;

            try {
                connection.send(new Request(RequestType.CHAT_MESSAGE,
                    new Message(loggedInUser, currentRoom, line)));
            } catch (IOException e) {
                // Queue the message to be sent after reconnect and inform the user.
                try {
                    pendingMessages.put(line);
                    System.out.println(YELLOW + "Message queued until connection is restored." + RESET);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.println(RED + "Failed to queue message." + RESET);
                }
            }
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
            while (!connection.isClosed()) {
                Request req = inbox.take();
                if (req.getType() == RequestType.CHAT_MESSAGE
                        && req.getPayload() instanceof Message msg) {
                    if (currentRoom != null && msg.getRecipient().equals(currentRoom)) {
                        printIncomingMessage(msg);
                    }
                    // Reprint the prompt so the user can keep typing.
                    System.out.print(BOLD + loggedInUser.getUsername() + RESET + ": ");
                    System.out.flush();
                }
                // Non-chat frames (e.g. ERROR) during chat are silently ignored here.
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
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
        // If we're currently reconnecting, wait until it's finished before prompting.
        while (connection.isReconnecting()) {
            System.out.println(YELLOW + "Reconnecting... waiting for connection to be restored." + RESET);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for reconnect", ie);
            }
            if (connection.isClosed()) throw new IOException("Connection closed");
        }

        System.out.print(BOLD + prompt + RESET + ": ");
        return input.readLine();
    }

    private String readPassword(String prompt) throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword(BOLD + prompt + RESET + ": ");
            return pwd != null ? new String(pwd) : "";
        }
        // Ensure we don't prompt while reconnecting
        return readLine(prompt);
    }

    private void printError(String message) {
        System.out.println(RED + "\n" + message + RESET + "\n");
    }
}