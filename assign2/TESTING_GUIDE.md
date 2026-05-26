# Complete Testing Guide - Chat System with AI Rooms

## Prerequisites Check

Before testing, verify you have everything:

```bash
# Check Java (need 21+)
java -version

# Check Docker
docker --version

# Check curl (for testing Ollama directly)
curl --version
```

## Step 1: Start Ollama Docker Container

### Option A: Using the provided command

```bash
# Start Ollama container
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama

# Verify it's running
docker ps | grep ollama
```

**Expected output:**
```
ollama14   ollama/ollama   "ollama serve"   ...   Up ...   0.0.0.0:11434->11434/tcp
```

### Option B: Stop existing container first (if one exists)

```bash
# Stop old container if running
docker stop ollama14 2>/dev/null || true
docker rm ollama14 2>/dev/null || true

# Start fresh
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama
```

## Step 2: Pull an AI Model

After starting Ollama, pull a model. This downloads the LLM (~2-6GB depending on model):

### Option A: llama3 (Recommended, ~4GB)

```bash
sudo docker exec -it ollama14 ollama pull llama3
```

**Expected output:**
```
pulling manifest
pulling 6a0746a1ec1a
[████████████████████████] 100%
...
pulling layer ...
Verifying sha256 digest
Writing manifest
Success
```

This takes **5-15 minutes** depending on your internet speed.

### Option B: neural-chat (Smaller, ~2GB, faster)

```bash
sudo docker exec -it ollama14 ollama pull neural-chat
```

### Option C: Multiple models (recommended for testing)

```bash
# Pull both for full testing
sudo docker exec -it ollama14 ollama pull llama3
sudo docker exec -it ollama14 ollama pull neural-chat
```

## Step 3: Test Ollama Directly (Before Starting Chat System)

Test that Ollama is working with simple curl commands:

### Test 1: Simple prompt (streaming response)

```bash
curl http://localhost:11434/api/generate -d '{"model": "llama3", "prompt": "Say hello"}'
```

**Expected output:**
```json
{"model":"llama3","response":"Hello! How can I help you today?","done":false}
{"model":"llama3","response":" ","done":false}
...
{"model":"llama3","response":"","done":true}
```

### Test 2: Non-streaming response (what our app uses)

```bash
curl http://localhost:11434/api/generate -d '{"model": "llama3", "prompt": "How are you today?", "stream": false}'
```

**Expected output:**
```json
{"model":"llama3","response":"I'm doing well, thank you for asking! How can I assist you today?","done":true,...}
```

### Test 3: Check available models

```bash
curl http://localhost:11434/api/tags
```

**Expected output:**
```json
{
  "models": [
    {
      "name": "llama3:latest",
      "modified_at": "...",
      "size": 4109156121,
      "digest": "..."
    },
    {
      "name": "neural-chat:latest",
      "modified_at": "...",
      "size": 2000000000,
      "digest": "..."
    }
  ]
}
```

## Step 4: Build the Chat System

```bash
cd /home/acular/Desktop/erasmus/cpd/g11/assign2/src
make clean
make build
```

**Expected output:**
```
rm -rf ../bin
mkdir -p ../bin
javac -d ../bin ...
```

If there are errors, check that all `.java` files are present in src/.

## Step 5: Start the Chat Server

```bash
cd /home/acular/Desktop/erasmus/cpd/g11/assign2/src
make server PORT=9900
```

**Expected output:**
```
[RoomManager] Initialised with default rooms: [musica, filmes]
[AIRoomManager] Ollama connection established
Server is listening on port 9900
```

**Key signs it's working:**
- ✅ "Server is listening on port 9900"
- ✅ "Ollama connection established"
- ❌ "WARNING: Ollama not available" = Ollama docker not running

Keep this terminal open!

## Step 6: Start a Chat Client (In New Terminal)

```bash
cd /home/acular/Desktop/erasmus/cpd/g11/assign2/src
make client HOST=localhost PORT=9900
```

**Expected output:**
```
Connected to server at localhost:9900
[Menu will appear]
```

## Step 7: Test Regular Chat First

Follow the UI prompts:

1. **Register** a new user (e.g., username: `testuser`, password: `pass123`)
2. **Login** with those credentials
3. **List Rooms** - you should see: `musica` and `filmes`
4. **Join a room** (e.g., `musica`)
5. **Send a message** (e.g., "Hello!")
6. **See your message** appear

This tests basic connectivity.

## Step 8: Test AI Rooms

### Create an AI Room

In the client menu, select **Create AI Room** and provide:

```
Room Name: Test AI
System Prompt: You are a helpful assistant. Keep responses brief and friendly.
Model: llama3
```

### Enter the AI Room

Select **Enter AI Room** and choose the room you just created.

### Send a Test Message

Type: `"Hello, can you help me?"`

**Expected behavior:**
1. Your message appears in the room
2. After 5-30 seconds, Bot responds
3. Bot's response appears as: `Bot: ...response text...`

### Test with Multiple Prompts

Try these:

```
User: What is 2+2?
Bot: 4

User: Tell me a joke
Bot: (AI generates a joke)

User: Explain machine learning in one sentence
Bot: (AI provides brief explanation)
```

## Step 9: Test Multi-User AI Room

### Start a Second Client

In another terminal:
```bash
cd /home/acular/Desktop/erasmus/cpd/g11/assign2/src
make client HOST=localhost PORT=9900
```

- Register as `user2`
- Join the same AI room
- Both users see each other's messages AND Bot responses

## Advanced Testing

### Test 1: Connection Reconnection

While in a chat room:
1. Kill the client connection (Ctrl+C)
2. Restart the client
3. Should reconnect and see previous room

### Test 2: Stress Test

1. Create 3-4 AI rooms with different prompts
2. Join each room
3. Send rapid messages
4. Verify Bot responses come back for each room

### Test 3: Different Models

1. Pull another model: `neural-chat`
2. Create a room with `neural-chat`
3. Send same prompt to both `llama3` and `neural-chat` rooms
4. Compare responses

## Troubleshooting

### Problem: "Ollama not available"

**Cause:** Docker container not running

**Fix:**
```bash
# Check if running
docker ps | grep ollama

# If not, start it
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama

# If container exists but stopped
docker start ollama14
```

### Problem: "Model not found" or Bot doesn't respond

**Cause:** Model not pulled yet

**Fix:**
```bash
# Pull the model
sudo docker exec -it ollama14 ollama pull llama3

# List available models
sudo docker exec -it ollama14 ollama list
```

### Problem: Very slow Bot responses (30+ seconds)

**Cause:** Either:
1. First response (model loading) - normal, takes 10-30s first time
2. Small model loading on CPU - normal
3. System CPU maxed out

**Fix:**
```bash
# Check CPU usage
top

# Try smaller model
neural-chat (2GB) instead of llama3 (4GB)
```

### Problem: Client connects but can't see messages

**Cause:** UIHandler thread issue

**Fix:**
```bash
# Restart client
# Make sure you're logged in before entering room
# Check server logs for errors
```

### Problem: Compilation errors

**Fix:**
```bash
cd src
make clean
make build

# If still errors, check:
ls -la common/models/
ls -la server/ai/
```

### Problem: Port 9900 already in use

**Fix:**
```bash
# Use different port
make server PORT=9901

# In client
make client PORT=9901

# Or kill process using 9900
lsof -i :9900
kill -9 <PID>
```

## Testing Checklist

Use this checklist to verify everything works:

```
[ ] Java 21+ installed
[ ] Docker running
[ ] Ollama container started
[ ] Model pulled (llama3 or neural-chat)
[ ] Ollama responds to curl
[ ] Project builds successfully
[ ] Server starts without errors
[ ] Client connects to server
[ ] User registration works
[ ] User login works
[ ] Can join regular rooms
[ ] Can send messages in regular rooms
[ ] Can create AI room
[ ] Can enter AI room
[ ] Bot responds to messages
[ ] Multiple users can use same AI room
[ ] Reconnection works
[ ] Different models work
```

## Performance Expectations

### First AI Room Message (Cold Start)
- **Time**: 10-30 seconds
- **Reason**: Model needs to load into memory
- **Normal**: Yes, this is expected

### Subsequent Messages
- **Time**: 5-15 seconds per message
- **Depends on**: Model size, prompt length, system load
- **Optimization**: Keep system prompts concise

### Regular Chat Messages
- **Time**: <1 second
- **Reason**: Direct server-to-client, no AI processing

## Next Steps After Testing

Once everything works:

1. **Create more AI rooms** with different prompts
2. **Test with your team** using the network
3. **Document any issues** found
4. **Prepare deployment** instructions
5. **Consider SSL/TLS** for production

## Quick Start Script

Run everything at once (if already tested individually):

```bash
#!/bin/bash

# Terminal 1: Start Ollama
sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama
sudo docker exec -it ollama14 ollama pull llama3

# Terminal 2: Start Server
cd /path/to/assign2/src
make clean && make build
make server PORT=9900

# Terminal 3: Start Client
cd /path/to/assign2/src
make client HOST=localhost PORT=9900
```

## References

- Ollama API Docs: https://github.com/ollama/ollama/blob/main/docs/api.md
- Docker Hub: https://hub.docker.com/r/ollama/ollama
- Java HttpClient: https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html
