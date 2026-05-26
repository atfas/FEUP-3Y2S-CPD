# Chat System with Ollama AI Integration

A distributed Java chat system with client-server architecture supporting both regular chat rooms and AI-powered rooms using Ollama.

## Features

- **User Authentication**: Username/password registration and login
- **Session Management**: Token-based sessions with reconnection support
- **Regular Chat Rooms**: Real-time messaging between users
- **AI Rooms**: Special rooms powered by local LLM via Ollama
  - System prompts to guide AI behavior
  - Conversation history tracking
  - Automatic bot responses
- **Concurrency**: Virtual threads (Java 21+) for scalable connections
- **Thread Safety**: ReadWriteLock-based synchronization
- **Fault Tolerance**: Session recovery on network disconnections

## Prerequisites

- **Java SE 21** or later
- **Docker** (for running Ollama)
- **Build Tools**: make, javac
- **Maven** (for managing dependencies) - OPTIONAL

## Setup

### 1. Start Ollama Server

```bash
# Start Ollama in a Docker container
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama

# Pull a model (llama3 recommended, ~4GB)
sudo docker exec -it ollama ollama pull llama3

# Or try a smaller model:
sudo docker exec -it ollama ollama pull neural-chat
```

### 2. Build the Project

```bash
cd src
make build
# OR
make clean
make build
```

This will compile all Java files and place them in `../bin`.

### 3. Install Dependencies

The project requires **Google's Gson library** for JSON processing:

#### Option A: Using Maven (Recommended)

```bash
# Download gson and add to classpath during build
mvn dependency:copy-dependencies -DoutputDirectory=../lib
```

#### Option B: Manual Download

```bash
# Create lib directory
mkdir -p lib

# Download gson from Maven Central
cd lib
curl -O https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
cd ../
```

Then modify the Makefile to include the lib path:
```makefile
CLASSPATH := $(BIN_DIR):../lib/gson-2.10.1.jar
```

And update the build and run commands to use `-cp $(CLASSPATH)`.

#### Option C: Add Dependencies to src/

Place `gson-2.10.1.jar` in the `src/` directory and update CLASSPATH in Makefile.

## Running

### Start the Server

```bash
cd src
make server PORT=9900
```

### Start a Client

In another terminal:

```bash
cd src
make client HOST=localhost PORT=9900
```

## Client Usage

### Authentication
1. Choose **Register** or **Login**
2. Enter username and password
3. Receive session token for reconnection

### Regular Chat Rooms
- **List Rooms**: View all available rooms
- **Enter Room**: Join a room to chat
- **Create Room**: Create a custom room (just enter a new name)
- **Send Messages**: Type messages in the room
- **Leave Room**: Exit current room

### AI Chat Rooms
- **List AI Rooms**: View AI-powered rooms
- **Create AI Room**: Create a new AI room with:
  - Room name
  - System prompt (guides AI behavior)
  - Model name (e.g., "llama3", "neural-chat")
- **Enter AI Room**: Join the AI room
- **Chat with Bot**: Send messages; the Bot responds automatically

#### Example AI Room Ideas

1. **Meeting Scheduler**
   - Prompt: "Help team members find a common meeting time by analyzing availability"
   - Model: llama3

2. **Code Helper**
   - Prompt: "You are an expert programmer. Help debug and explain code snippets"
   - Model: llama3

3. **Study Buddy**
   - Prompt: "Create summaries and quiz questions about the topic being discussed"
   - Model: neural-chat

## Project Structure

```
src/
├── client/                      # Client code
│   ├── ChatClient.java
│   ├── ConnectionManager.java
│   └── UIHandler.java
├── common/
│   ├── models/
│   │   ├── User.java
│   │   ├── Message.java
│   │   ├── Room.java
│   │   ├── ClientSession.java
│   │   └── AIRoom.java          # AI room model
│   ├── protocol/
│   │   ├── Request.java
│   │   ├── RequestType.java
│   │   ├── OllamaRequest.java   # Ollama API payload
│   │   ├── OllamaResponse.java  # Ollama API response
│   │   └── CreateAIRoomRequest.java
│   └── synchronization/
├── server/
│   ├── ChatServer.java
│   ├── auth/
│   │   └── Registration.java
│   ├── common/
│   │   ├── ClientHandler.java
│   │   ├── RoomManager.java
│   │   └── SessionManager.java
│   └── ai/
│       ├── OllamaClient.java    # HTTP client for Ollama
│       └── AIRoomManager.java   # AI room management
└── database/
    ├── Database.java
    └── users.txt                # User credentials file
```

## Concurrency Design

- **Virtual Threads**: Each client connection runs on a virtual thread
- **ReadWriteLock**: Database and AI room manager use ReadWriteLock for thread-safe access
- **Synchronized Collections**: Session and room managers use synchronized maps
- **No Blocking Queues**: Avoids platform thread overhead

## AI Room Architecture

1. **User sends message** to AI room
2. **ClientHandler intercepts** message, detects AI room
3. **AIRoomManager processes message**:
   - Retrieves conversation history
   - Builds prompt with context
   - Calls Ollama API
4. **Ollama generates response**
5. **Bot message broadcast** to all room members
6. **History updated** for next message

## Troubleshooting

### "Ollama not available at localhost:11434"
- Ensure Docker container is running: `docker ps | grep ollama`
- Check port: `curl http://localhost:11434/api/tags`
- Restart: `docker restart ollama`

### Compilation errors with Gson
- Ensure gson JAR is in classpath
- Add to Makefile: `CLASSPATH := ../lib/gson-2.10.1.jar:$(BIN_DIR)`

### Connection timeouts
- Check firewall allows port 9900
- Ensure server is running before starting client
- Check SSH tunnel if connecting remotely

### Slow AI responses
- LLM models run on CPU by default
- First response slower (model loading)
- Try smaller model: `neural-chat` instead of `llama3`

## Security Notes

- Passwords are SHA-256 hashed in database
- Session tokens used for reconnection (not raw credentials)
- Consider SSL/TLS for production (use SSLSocket)
- Tokens can be configured with expiration times

## Future Enhancements

- [ ] Token expiration times
- [ ] Persistent session storage
- [ ] Model switching per user
- [ ] Conversation export
- [ ] Message reactions/threading
- [ ] File sharing
- [ ] Admin commands
- [ ] Rate limiting
- [ ] Encryption for messages in transit

## Authors

Group 11 - CPD Assignment 2 (2025/2026)

## License

Educational use only
