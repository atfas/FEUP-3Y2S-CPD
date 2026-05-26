package common.models;

import java.io.Serializable;

/**
 * Represents a chat room with AI capabilities.
 * Extends Room to include system prompt and model configuration.
 */
public class AIRoom extends Room implements Serializable {
    private String systemPrompt;
    private String model;  // e.g., "llama3"
    
    public AIRoom(String name, String systemPrompt, String model) {
        super(name);
        if (systemPrompt == null || systemPrompt.isBlank())
            throw new IllegalArgumentException("System prompt cannot be null or blank");
        if (model == null || model.isBlank())
            throw new IllegalArgumentException("Model cannot be null or blank");
            
        this.systemPrompt = systemPrompt.trim();
        this.model = model.trim().toLowerCase();
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public String getModel() {
        return model;
    }
    
    @Override
    public String toString() {
        return "AIRoom{" + getName() + ", model=" + model + "}";
    }
}
