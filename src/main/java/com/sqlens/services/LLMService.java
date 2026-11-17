package com.sqlens.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Asks Google Gemini for optimisation advice. Optional: only enabled when
 * GEMINI_API_KEY is set. The key is sent in a header (never in the URL, so it cannot
 * end up in logs) and is never printed.
 */
public class LLMService {

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._\\-]{1,100}");
    private static final int MAX_RESPONSE_CHARS = 20_000;

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String model;
    private final String apiKey;

    LLMService(String baseUrl, String model, String apiKey) {
        if (!MODEL_NAME.matcher(model).matches()) {
            throw new IllegalArgumentException("invalid GEMINI_MODEL name");
        }
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /** @return a service if GEMINI_API_KEY is set, otherwise empty */
    public static Optional<LLMService> fromEnvironment() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String model = System.getenv("GEMINI_MODEL");
        return Optional.of(new LLMService(DEFAULT_BASE_URL,
            model == null || model.isBlank() ? DEFAULT_MODEL : model.strip(), key.strip()));
    }

    public String model() {
        return model;
    }

    public String suggest(String prompt) throws LLMException {
        String body;
        try {
            body = json.writeValueAsString(requestBody(prompt));
        } catch (IOException e) {
            throw new LLMException("could not build request", e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + model + ":generateContent"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LLMException("could not reach the AI service: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("request interrupted");
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new LLMException("AI service rejected the API key (HTTP " + status + ")");
        }
        if (status == 429) {
            throw new LLMException("AI service rate limit reached, try again later (HTTP 429)");
        }
        if (status != 200) {
            throw new LLMException("AI service returned HTTP " + status);
        }
        return sanitize(extractText(response.body()));
    }

    ObjectNode requestBody(String prompt) {
        ObjectNode root = json.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", PromptBuilder.SYSTEM_INSTRUCTION);
        root.putArray("contents").addObject().put("role", "user").putArray("parts").addObject().put("text", prompt);
        root.putObject("generationConfig").put("temperature", 0.2);
        return root;
    }

    String extractText(String responseBody) throws LLMException {
        try {
            JsonNode parts = json.readTree(responseBody).path("candidates").path(0).path("content").path("parts");
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                sb.append(part.path("text").asText(""));
            }
            if (sb.isEmpty()) {
                throw new LLMException("AI service returned no text");
            }
            return sb.toString();
        } catch (IOException e) {
            throw new LLMException("AI service returned malformed JSON");
        }
    }

    /** Strips terminal escape/control characters from model output and caps its length. */
    static String sanitize(String text) {
        String clean = text
            .replaceAll("\u001b\\][^\u0007\u001b]*(\u0007|\u001b\\\\)?", "")   // OSC sequences
            .replaceAll("\u001b\\[[0-9;?]*[ -/]*[@-~]", "")                    // CSI sequences (colours, cursor)
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")                          // any other control char
            .strip();
        return clean.length() > MAX_RESPONSE_CHARS ? clean.substring(0, MAX_RESPONSE_CHARS) + "\n[truncated]" : clean;
    }
}
