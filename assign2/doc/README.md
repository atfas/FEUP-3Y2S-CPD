# CPD Chat System

A distributed client-server chat system built in Java SE 21, developed for the CPD 2025/2026 assignment. It supports real-time messaging across multiple chat rooms, user authentication with session management, fault-tolerant TCP reconnection, and AI-powered rooms backed by a local Ollama LLM.

**Authors:** Afonso Saraiva (2023044) · Daniel Pinto (202304957) · Francisco Borralho (201806242) · Raluca (202512464)

---

## Project Structure

```
src/
├── client/
│   ├── ChatClient.java          # Entry point for the client
│   ├── ConnectionManager.java   # TCP connection, streams, reconnect logic
│   └── UIHandler.java           # Console UI, all user interaction
│
├── common/
│   ├── models/
│   │   ├── User.java            # User with username, password, session cookie
│   │   ├── Room.java            # Chat room (name-based identity)
│   │   ├── AIRoom.java          # AI room extending Room (model + system prompt)
│   │   ├── Message.java         # Chat message (sender, recipient room, content)
│   │   └── ClientSession.java   # Bundles User + Room for reconnect payloads
│   └── protocol/
│       ├── Request.java         # Generic serializable envelope (type + payload)
│       ├── RequestType.java     # Enum of all message types
│       ├── OllamaRequest.java   # DTO for Ollama REST API requests
│       ├── OllamaResponse.java  # DTO for Ollama REST API responses
│       └── CreateAIRoomRequest.java
│
├── server/
│   ├── ChatServer.java          # Entry point for the server
│   ├── common/
│   │   ├── ClientHandler.java   # Per-client request handler (runs on virtual thread)
│   │   ├── RoomManager.java     # Thread-safe room registry and membership
│   │   └── SessionManager.java  # In-memory token → User session registry
│   ├── auth/
│   │   └── Registration.java    # New-user registration logic
│   └── ai/
│       ├── AIRoomManager.java   # AI room lifecycle and Ollama message processing
│       └── OllamaClient.java    # HTTP client for Ollama's /api/generate endpoint
│
└── database/
    ├── Database.java            # File-backed user store with SHA-256 passwords
    └── users.txt                # Persisted user records (username:sha256_hash)
```

---

## Running the System

All commands are run from the `src/` directory.

### Start the Server

```bash
make server
```

### Start a Client

```bash
make client
```

You can run as many clients as you want simultaneously, each in its own terminal.

## Using the Client

### Authentication

On startup you are presented with a menu:

```
┌─ What would you like to do? ─────────┐
│  1) Login                            │
│  2) Register                         │
│  0) Exit                             │
└──────────────────────────────────────┘
```

- **Login** — enter your username and password. The password is hidden when typed in a real terminal (uses `System.console()`).
- **Register** — choose a username and password (confirmed twice). You are logged in automatically on success.

### Room Selection

After authentication, the client fetches and displays available rooms:

```
┌─ Available Rooms ─────────────────────┐
│   1) filmes                           │
│   2) musica                           │
│   0) Exit                             │
└───────────────────────────────────────┘
  Enter a number to join, or type a new room name to create it.
  Commands: /refresh, /ai (create), /ailist (list AI rooms)
```

| Input | Action |
|---|---|
| A number | Join the corresponding room |
| Any text | Create and join a new room with that name |
| `/refresh` | Refresh the room list |
| `/ai` | Create a new AI room (interactive prompt) |
| `/ailist` | List existing AI rooms and optionally enter one |
| `0` | Exit |

Two default rooms always exist: **musica** and **filmes**.

### Chatting

Once in a room, type any message and press Enter to send. Incoming messages from other users (and the Bot in AI rooms) appear in real time, even while you are typing.

| Command | Action |
|---|---|
| `/rooms` | Leave current room and go back to room selection |
| `/close` | Disconnect and exit |

---

### How AI Rooms Work

Every time a user sends a message in an AI room:

1. The message is broadcast to all members immediately.
2. The server sends the conversation history (up to the last 6 exchanges) plus the system prompt to Ollama asynchronously (on a virtual thread, so it does not block other users).
3. Ollama's response is broadcast back to the room under the username **Bot**.

Conversation history is kept in memory per room (max 6 messages for context, plus the system prompt always retained at the top).

### Choosing a Model

| Model | Size | Speed | Quality | Notes |
|---|---|---|---|---|
| `llama3.2:1b` | ~1 GB | Fast | Basic | Good for testing |
| `neural-chat` | ~4–5 GB | Slower | Better | Mistral-based, fits the prompt format well |
| `llama3` | ~4 GB | Medium | Good | Meta's general-purpose model |

The prompt format used internally (`<|system|>`, `<|user|>`, `<|assistant|>`) is Mistral/Zephyr-style, so `neural-chat` follows it most naturally.

---

## Architecture Overview

- The **server** spawns one virtual thread per connected client via `Thread.ofVirtual()`.
- The **client** uses three background virtual threads: one to read from the socket into an inbox queue, one to drain and print incoming chat messages, and one to flush queued messages after reconnection.
- All communication uses Java object serialization over TCP (`ObjectInputStream` / `ObjectOutputStream`). Streams are created once per connection and reused — recreating `ObjectOutputStream` would corrupt the stream header.

---

## Concurrency Design

Concurrency is handled carefully throughout, using `java.util.concurrent.locks` as required (no thread-safe collections from `java.util.concurrent`).

### Server Side

**`RoomManager`** uses two levels of locking:
- The `rooms` map is locked with `synchronized(rooms)` for structural changes (adding/removing rooms).
- Each room's member `Set<ClientHandler>` is locked individually with `synchronized(members)` for join/leave operations, allowing concurrent access to different rooms.

**`Database`** uses a `ReentrantReadWriteLock`:
- Multiple threads can validate/check users concurrently (read lock).
- Adding a new user acquires an exclusive write lock.

**`AIRoomManager`** uses a `ReentrantReadWriteLock`:
- Room lookups and listing acquire the read lock.
- Creating rooms and updating conversation history acquire the write lock.
- Ollama HTTP calls happen **outside** the lock so they do not block other room operations.

**`SessionManager`** uses `synchronized` on the sessions map for all operations.

**`ClientHandler`** uses a `sendLock` object per client so the server can call `send()` from both the handler thread and from broadcast calls on other clients' threads without corrupting the output stream.

### Client Side

A single background `readLoop` thread is the **only** thread that reads from the socket. It places every incoming `Request` into a `LinkedBlockingQueue<Request>` (inbox). All other threads consume from that queue — this eliminates any concurrent read races on the stream.

Writing is done only from the main thread (or the `pendingSender` virtual thread), so no write-side locking is needed on the client.

---

## Fault Tolerance & Reconnection

If the TCP connection drops at any point, both sides handle it gracefully.

### Client-Side Reconnect (`ConnectionManager`)

1. On any `IOException` from a read or write, the client attempts to reconnect.
2. A `reconnectLock` ensures only one reconnect attempt runs at a time — if two threads detect a failure simultaneously, only one does the work; the other waits.
3. After reconnecting the socket, the client sends a `RECONNECT` request with a `ClientSession` payload containing the **session token** (not the password) and the last known room.
4. If the server accepts the token, the session and room are restored transparently.
5. If the token is rejected (e.g. server restarted), the client falls back to re-sending the login credentials.

The user is never asked to log in again after a disconnection — the reconnect is fully automatic.

### Server-Side Reconnect (`ClientHandler`)

When a `RECONNECT` request arrives:

1. The session token is looked up in `SessionManager`.
2. If valid, the user and login state are restored into the new `ClientHandler`.
3. The desired room (from the `ClientSession` payload) is rejoined. If it is an AI room, the full `AIRoom` object (with model and system prompt) is sent back in the success response, so the client restores its AI room context correctly.
4. The server then resumes relaying room messages to the client on the new connection.

**Important:** The client never caches the user's password after the first login. All reconnects use the session token only, which is more secure.

---

## Authentication & Security

- **Passwords** are never stored in plaintext. They are hashed with SHA-256 before being written to `database/users.txt`.
- Each line in `users.txt` has the format: `username:sha256_hex`
- On login, the submitted password is hashed and compared against the stored hash.
- On successful login or registration, the server generates a **UUID session token** and returns it to the client.
- The client stores the token in memory (in the `User` object) and uses it for reconnection. It is never written to disk.

---

## Protocol Reference

All messages are instances of `Request`, serialized over the TCP stream. Each `Request` has a `RequestType` and an `Object` payload.

| Type | Direction | Payload | Description |
|---|---|---|---|
| `LOGIN` | C→S | `User` | Login with username + password |
| `REGISTER` | C→S | `User` | Register new account |
| `RECONNECT` | C→S | `ClientSession` | Restore session via token |
| `LIST_ROOMS` | C→S | `null` | Request list of regular rooms |
| `LIST_AI_ROOMS` | C→S | `null` | Request list of AI rooms |
| `ENTER_ROOM` | C→S | `Room` | Join or create a regular room |
| `ENTER_AI_ROOM` | C→S | `AIRoom` | Join an existing AI room |
| `CREATE_AI_ROOM` | C→S | `AIRoom` | Create a new AI room |
| `LEAVE_ROOM` | C→S | `null` | Leave current room |
| `CHAT_MESSAGE` | C↔S | `Message` | Send or receive a chat message |
| `LOGOUT` | C→S | `null` | Log out and invalidate token |
| `SUCCESS` | S→C | varies | Acknowledgement with result payload |
| `ROOMS_LIST` | S→C | `List<Room>` or `List<AIRoom>` | Room listing response |
| `ERROR` | S→C | `String` | Error description |

C→S = client to server, S→C = server to client, C↔S = both directions.