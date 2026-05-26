package common.protocol;

import java.io.Serializable;

/**
 * Data structure for Ollama API responses.
 */
public class OllamaResponse implements Serializable {
    private String model;
    private String response;
    private boolean done;
    
    public OllamaResponse(String model, String response, boolean done) {
        this.model = model;
        this.response = response;
        this.done = done;
    }
    
    public String getModel() {
        return model;
    }
    
    public String getResponse() {
        return response;
    }
    
    public boolean isDone() {
        return done;
    }
}
