package common.protocol;

import java.io.Serializable;

/**
 * Payload for creating a new AI room.
 */
public class CreateAIRoomRequest implements Serializable {
    private String name;
    private String systemPrompt;
    private String model;
    
    public CreateAIRoomRequest(String name, String systemPrompt, String model) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.model = model;
    }
    
    public String getName() {
        return name;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public String getModel() {
        return model;
    }
}
