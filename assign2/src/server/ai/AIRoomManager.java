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
    
    private static final int MAX_CONTEXT_MESSAGES = 6;

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
        
        AIRoom room;
        List<ConversationMessage> promptMessages;

        lock.writeLock().lock();
        try {
            room = aiRooms.get(normalizedName);
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
            history.add(new ConversationMessage("user", userMessage));
            System.out.println("[AIRoomManager] Added user message to history");
            promptMessages = recentHistory(history);
        } finally {
            lock.writeLock().unlock();
        }

        String prompt = buildPrompt(promptMessages);
        System.out.println("[AIRoomManager] Built prompt, calling OllamaClient with model: " + room.getModel());

        String response = ollamaClient.generateResponse(room.getModel(), prompt);
        System.out.println("[AIRoomManager] OllamaClient returned: [" + response + "]");

        if (response.isEmpty()) {
            System.out.println("[AIRoomManager] WARNING: Empty response from Ollama");
            response = "[Bot is currently unavailable]";
        } else {
            response = cleanBotResponse(response);
            int promptEnd = response.indexOf("\n" + room.getSystemPrompt());
            if (promptEnd > 0) {
                response = response.substring(0, promptEnd);
            }
        }

        lock.writeLock().lock();
        try {
            List<ConversationMessage> history = roomHistory.get(normalizedName);
            if (history != null) {
                history.add(new ConversationMessage("assistant", response));
                trimHistory(history);
            }
        } finally {
            lock.writeLock().unlock();
        }

        System.out.println("[AIRoomManager] Bot response in " + normalizedName + ": " 
            + response.substring(0, Math.min(50, response.length())) + "...");

        return response;
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

    private List<ConversationMessage> recentHistory(List<ConversationMessage> history) {
        List<ConversationMessage> recent = new ArrayList<>();
        if (!history.isEmpty()) {
            recent.add(history.get(0)); // keep the system prompt
        }

        int start = Math.max(1, history.size() - MAX_CONTEXT_MESSAGES);
        recent.addAll(history.subList(start, history.size()));
        return recent;
    }

    private String buildPrompt(List<ConversationMessage> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<|system|>\n");
        prompt.append("You are a concise chat assistant. Answer only the user's last message. ");
        prompt.append("No greetings, no preamble, no speaker names.\n");
        for (ConversationMessage msg : history) {
            if ("system".equals(msg.role)) {
                prompt.append(msg.content).append("\n");
            }
        }

        for (ConversationMessage msg : history) {
            if ("user".equals(msg.role)) {
                prompt.append("<|user|>\n").append(msg.content).append("\n");
            } else if ("assistant".equals(msg.role)) {
                prompt.append("<|assistant|>\n").append(msg.content).append("\n");
            }
        }

        prompt.append("<|assistant|>\n");
        return prompt.toString();
    }

    private String cleanBotResponse(String response) {
        String cleaned = response.trim()
                .replaceFirst("(?i)^sure,?\\s+here('?s| is)\\s+(a\\s+)?brief\\s+response\\s+to\\s+the\\s+latest\\s+user\\s+message\\s*:\\s*", "")
                .replaceFirst("(?i)^sure,?\\s+here('?s| is)\\s+(a\\s+)?brief\\s+answer\\s*:\\s*", "")
                .replaceFirst("(?i)^brief\\s+answer\\s*:\\s*", "")
                .replaceFirst("(?i)^assistant\\s*:\\s*", "")
                .replaceFirst("(?i)^bot\\s*:\\s*", "")
                .replaceFirst("(?i)^conversation\\s*:\\s*", "")
                .replaceFirst("^<\\|assistant\\|>\\s*", "")
                .replaceFirst("^[A-Za-z0-9_ -]{1,32}\\s*:\\s*", "");

        cleaned = cutAtTranscriptMarker(cleaned).trim();

        String[] lines = cleaned.split("\\R");
        StringBuilder kept = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.matches("(?i)^(user|assistant|system|bot)\\s*:.*")) continue;
            if (trimmed.matches("^[A-Za-z0-9_ -]{1,32}\\s*:.*")) continue;
            if (kept.length() > 0) kept.append('\n');
            kept.append(trimmed);
        }

        cleaned = kept.length() == 0 ? cleaned.replaceFirst("^[A-Za-z0-9_ -]{1,32}\\s*:\\s*", "") : kept.toString();
        if (cleaned.isBlank()) {
            return "I am not sure how to answer that briefly.";
        }
        return shorten(cleaned);
    }

    private String cutAtTranscriptMarker(String text) {
        String[] markers = {
            " User:", " Bot:", " Assistant:", " System:",
            "\nUser:", "\nBot:", "\nAssistant:", "\nSystem:",
            "<|user|>", "<|assistant|>", "<|system|>"
        };
        int end = text.length();
        for (String marker : markers) {
            int pos = text.indexOf(marker);
            if (pos >= 0 && pos < end) {
                end = pos;
            }
        }
        return text.substring(0, end);
    }

    private String shorten(String text) {
        if (text.length() <= 240) return text;
        int sentenceEnd = text.indexOf('.', 80);
        if (sentenceEnd > 0 && sentenceEnd < 240) {
            return text.substring(0, sentenceEnd + 1);
        }
        return text.substring(0, 240).trim() + "...";
    }

    private void trimHistory(List<ConversationMessage> history) {
        while (history.size() > MAX_CONTEXT_MESSAGES + 1) {
            history.remove(1); // preserve the system prompt at index 0
        }
    }
}
