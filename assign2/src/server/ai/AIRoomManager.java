package server.ai;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import common.models.AIRoom;
import common.models.Message;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;
import server.common.ClientHandler;

/**
 * Manages AI rooms and handles message processing through Ollama.
 * 
 * Each AI room maintains:
 * - A message history for context
 * - System prompt configuration
 * - Model name
 * - Connection to Ollama
 */
public class AIRoomManager {
    
    private final OllamaClient ollamaClient;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // roomName -> AIRoom metadata
    private final Map<String, AIRoom> aiRooms = new HashMap<>();
    
    // roomName -> list of (role, content) conversation history
    private final Map<String, List<ConversationMessage>> roomHistory = new HashMap<>();
    
    /**
     * Lightweight container for conversation messages (not serializable).
     */
    public static class ConversationMessage {
        public String role;      // "system", "user", "assistant"
        public String content;
        
        public ConversationMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    public AIRoomManager() {
        this.ollamaClient = new OllamaClient();
        
        if (!ollamaClient.isOllamaAvailable()) {
            System.out.println("[AIRoomManager] WARNING: Ollama not available at localhost:11434");
        } else {
            System.out.println("[AIRoomManager] Ollama connection established");
        }
    }
    
    /**
     * Create a new AI room.
     * 
     * @param name Room name
     * @param systemPrompt The system prompt that guides the AI
     * @param model The model to use (e.g., "llama3")
     * @return The created AIRoom
     */
    public AIRoom createAIRoom(String name, String systemPrompt, String model) {
        String normalizedName = normalise(name);
        
        lock.writeLock().lock();
        try {
            if (aiRooms.containsKey(normalizedName)) {
                throw new IllegalArgumentException("AI room already exists: " + normalizedName);
            }
            
            AIRoom room = new AIRoom(normalizedName, systemPrompt, model);
            aiRooms.put(normalizedName, room);
            
            // Initialize history with system message
            List<ConversationMessage> history = new ArrayList<>();
            history.add(new ConversationMessage("system", systemPrompt));
            roomHistory.put(normalizedName, history);
            
            System.out.println("[AIRoomManager] Created AI room: " + normalizedName 
                + " with model: " + model);
            
            return room;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get an AI room by name.
     */
    public AIRoom getAIRoom(String name) {
        String normalizedName = normalise(name);
        lock.readLock().lock();
        try {
            return aiRooms.get(normalizedName);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if a room is an AI room.
     */
    public boolean isAIRoom(String name) {
        return getAIRoom(name) != null;
    }
    
    /**
     * List all AI rooms.
     */
    public List<AIRoom> listAIRooms() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(aiRooms.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Process a user message in an AI room and get the bot's response.
     * Automatically adds both messages to the history.
     */
    public String processMessage(String roomName, String userName, String userMessage) {
        String normalizedName = normalise(roomName);
        
        System.out.println("[AIRoomManager] processMessage called: room=" + normalizedName 
            + ", user=" + userName + ", message=" + userMessage);
        
        lock.writeLock().lock();
        try {
            AIRoom room = aiRooms.get(normalizedName);
            if (room == null) {
                System.out.println("[AIRoomManager] ERROR: AI room not found: " + normalizedName);
                return "[Error: AI room not found]";
            }
            
            List<ConversationMessage> history = roomHistory.get(normalizedName);
            if (history == null) {
                System.out.println("[AIRoomManager] ERROR: Room history not found: " + normalizedName);
                return "[Error: Room history not found]";
            }
            
            // Add user message to history
            history.add(new ConversationMessage("user", userName + ": " + userMessage));
            System.out.println("[AIRoomManager] Added user message to history");
            
            // Build prompt for Ollama
            StringBuilder prompt = new StringBuilder();
            for (ConversationMessage msg : history) {
                prompt.append(msg.role).append(": ").append(msg.content).append("\n");
            }
            prompt.append("assistant: ");
            
            System.out.println("[AIRoomManager] Built prompt, calling OllamaClient with model: " + room.getModel());
            
            // Get response from Ollama
            String response = ollamaClient.generateResponse(room.getModel(), prompt.toString());
            
            System.out.println("[AIRoomManager] OllamaClient returned: [" + response + "]");
            
            if (response.isEmpty()) {
                System.out.println("[AIRoomManager] WARNING: Empty response from Ollama");
                response = "[Bot is currently unavailable]";
            } else {
                // Clean up the response (remove any extra content)
                response = response.trim();
                // Truncate if it starts repeating the prompt
                int promptEnd = response.indexOf("\n" + room.getSystemPrompt());
                if (promptEnd > 0) {
                    response = response.substring(0, promptEnd);
                }
            }
            
            // Add bot response to history
            history.add(new ConversationMessage("assistant", response));
            
            System.out.println("[AIRoomManager] Bot response in " + normalizedName + ": " 
                + response.substring(0, Math.min(50, response.length())) + "...");
            
            return response;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Delete an AI room (called when it becomes empty).
     */
    public void deleteAIRoom(String name) {
        String normalizedName = normalise(name);
        lock.writeLock().lock();
        try {
            aiRooms.remove(normalizedName);
            roomHistory.remove(normalizedName);
            System.out.println("[AIRoomManager] Deleted AI room: " + normalizedName);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all AI rooms and history (for server shutdown/reset).
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            aiRooms.clear();
            roomHistory.clear();
            System.out.println("[AIRoomManager] Cleared all AI rooms");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private String normalise(String name) {
        return name.trim().toLowerCase();
    }
}
