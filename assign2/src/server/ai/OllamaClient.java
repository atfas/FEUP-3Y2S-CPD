package server.ai;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client for communicating with Ollama API.
 * 
 * Ollama runs at http://localhost:11434 by default.
 * Uses the /api/generate endpoint for simple prompts.
 * 
 * NOTE: Uses only Java SE libraries (no Gson). Parses JSON manually.
 */
public class OllamaClient {
    
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String GENERATE_ENDPOINT = "/api/generate";
    private static final Duration TIMEOUT = Duration.ofSeconds(120); // Long timeout for LLM
    private static final int MAX_TOKENS = 64;
    
    private final HttpClient httpClient;
    
    public OllamaClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }
    
    /**
     * Send a prompt to Ollama and get a non-streaming response.
     * 
     * @param model The model to use (e.g., "llama3")
     * @param prompt The prompt text
     * @return The model's response text, or empty string if error
     */
    public String generateResponse(String model, String prompt) {
        try {
            System.out.println("[OllamaClient] Sending request to Ollama: model=" + model);
            System.out.println("[OllamaClient] Prompt length: " + prompt.length() + " chars");
            
            String requestBody = buildJsonRequest(model, prompt);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + GENERATE_ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            System.out.println("[OllamaClient] Sending POST to " + OLLAMA_BASE_URL + GENERATE_ENDPOINT);
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("[OllamaClient] Response status: " + response.statusCode());
            System.out.println("[OllamaClient] Response length: " + response.body().length() + " chars");
            
            if (response.statusCode() == 200) {
                String parsedResponse = parseJsonResponse(response.body());
                System.out.println("[OllamaClient] Parsed response: [" + parsedResponse + "]");
                return parsedResponse;
            } else {
                System.err.println("[OllamaClient] Error: HTTP " + response.statusCode() + " - " + response.body());
                return "";
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[OllamaClient] Failed to generate response: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * Check if Ollama server is reachable.
     */
    public boolean isOllamaAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
    
    /**
     * Build a JSON request string for Ollama.
     */
    private String buildJsonRequest(String model, String prompt) {
        // Escape JSON strings
        String escapedPrompt = escapeJsonString(prompt);
        String escapedModel = escapeJsonString(model);
        
        return "{"
            + "\"model\":\"" + escapedModel + "\","
            + "\"prompt\":\"" + escapedPrompt + "\","
            + "\"stream\":false,"
            + "\"keep_alive\":\"10m\","
            + "\"options\":{"
            + "\"num_predict\":" + MAX_TOKENS + ","
            + "\"temperature\":0.2,"
            + "\"stop\":[\"\\nUser\",\"\\nBot\",\"\\nAssistant\",\"\\nSystem\",\"User:\",\"Bot:\",\"Assistant:\",\"System:\"]"
            + "}"
            + "}";
    }
    
    /**
     * Parse JSON response from Ollama and extract the "response" field.
     * Handles multi-line responses with escaped characters.
     */
    private String parseJsonResponse(String json) {
        try {
            // Find the "response" field value using non-greedy matching
            // This pattern finds "response": and captures the JSON string value (handling escapes)
            Pattern pattern = Pattern.compile("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(json);
            
            if (matcher.find()) {
                String response = matcher.group(1);
                // Unescape common JSON escape sequences
                // NOTE: Order matters - unescape \\\\ first, then \\"
                response = response.replace("\\\\", "\u0000")  // Temp placeholder for \\
                                  .replace("\\\"", "\"")       // Unescape quotes
                                  .replace("\\n", "\n")        // Unescape newlines
                                  .replace("\\t", "\t")        // Unescape tabs
                                  .replace("\\r", "\r")        // Unescape carriage returns
                                  .replace("\u0000", "\\");     // Replace temp placeholder
                return response.trim();
            }
        } catch (Exception e) {
            System.err.println("[OllamaClient] Failed to parse response: " + e.getMessage());
        }
        return "";
    }
    
    /**
     * Escape special characters in JSON string.
     */
    private String escapeJsonString(String str) {
        if (str == null) return "";
        
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
