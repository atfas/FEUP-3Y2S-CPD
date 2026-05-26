package common.protocol;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data structure for Ollama API requests.
 * Represents the payload sent to Ollama REST API.
 */
public class OllamaRequest implements Serializable {
    private String model;
    private List<Message> messages;
    private boolean stream;
    private String prompt;
    
    public static class Message implements Serializable {
        private String role;       // "system", "user", "assistant"
        private String content;
        
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
        
        public String getRole() {
            return role;
        }
        
        public String getContent() {
            return content;
        }
    }
    
    /**
     * Create a request with conversation history (for chat API).
     */
    public OllamaRequest(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }
    
    /**
     * Create a simple prompt request (for generate API).
     */
    public OllamaRequest(String model, String prompt, boolean stream) {
        this.model = model;
        this.prompt = prompt;
        this.stream = stream;
        this.messages = null;
    }
    
    public String getModel() {
        return model;
    }
    
    public List<Message> getMessages() {
        return messages;
    }
    
    public boolean isStream() {
        return stream;
    }
    
    public String getPrompt() {
        return prompt;
    }
}
