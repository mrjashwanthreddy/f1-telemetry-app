package com.f1telemetry.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Core AI service — calls the Gemini 2.0 Flash API to generate
 * engineer-style insights from telemetry data.
 *
 * Uses Java 11+ HttpClient (no extra dependency).
 * API key is injected from environment variable via application.properties.
 */
@Slf4j
@Service
public class AiEngineerService {

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String modelName;

    private String getApiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent";
    }

    public String getModelName() {
        return modelName;
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    public AiEngineerService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns true if the Gemini API key is configured.
     * Used by the controller to surface AI availability to the frontend.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Generates a 2-sentence post-lap radio-call verdict.
     * Called async after each lap completion — never blocks the telemetry pipeline.
     *
     * @param ctx all data about the completed lap
     * @return AI-generated engineer verdict, or a fallback message if API is
     *         unavailable
     */
    public String analyzeLapCompletion(LapCompletionContext ctx) {
        String prompt = PromptBuilder.buildLapAlertPrompt(ctx);
        return callGemini(prompt, "lap-alert");
    }

    /**
     * Generates a full post-session debrief report.
     *
     * @param trackName      circuit name (e.g. "Silverstone")
     * @param sessionType    session mode label (e.g. "Time Trial")
     * @param laps           list of lap data: each int[] = {lapNum, s1Ms, s2Ms,
     *                       s3Ms, totalMs}
     * @param cornerAnalysis detailed corner comparison context
     * @return formatted debrief text
     */
    public String generateSessionDebrief(String trackName, String sessionType,
            java.util.List<int[]> laps, String cornerAnalysis) {
        String prompt = PromptBuilder.buildSessionDebriefPrompt(trackName, sessionType, laps, cornerAnalysis);
        return callGemini(prompt, "session-debrief");
    }

    /**
     * Answers a natural language question about a session.
     *
     * @param question          driver's question
     * @param trackName         circuit name
     * @param sessionType       session mode
     * @param laps              lap data list
     * @param additionalContext extra context (e.g. telemetry highlights)
     * @return AI answer
     */
    public String answerChatQuestion(String question, String trackName, String sessionType,
            java.util.List<int[]> laps, String additionalContext) {
        String prompt = PromptBuilder.buildChatPrompt(question, trackName, sessionType,
                laps, additionalContext);
        return callGemini(prompt, "chat");
    }

    // ── Core HTTP call ────────────────────────────────────────────────────────

    /**
     * Sends a prompt to Gemini and returns the generated text response.
     * Returns a safe fallback message on any error — never throws to the caller.
     */
    private String callGemini(String prompt, String context) {
        if (!isConfigured()) {
            log.warn("[AI] Gemini API key not configured. Skipping AI call for: {}", context);
            return null;
        }

        try {
            // Build the Gemini request payload
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", prompt);

            // Optional: configure generation parameters
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", context.equals("session-debrief") ? 600 : 200);
            generationConfig.put("topP", 0.9);

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[AI] Gemini API returned status {} for context '{}': {}",
                        response.statusCode(), context, response.body());
                return null;
            }

            // Parse: candidates[0].content.parts[0].text
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text");

            if (text.isMissingNode()) {
                log.warn("[AI] Unexpected Gemini response shape for '{}': {}", context, response.body());
                return null;
            }

            String result = text.asText().trim();
            log.info("[AI] {} response ({} chars): {}", context, result.length(),
                    result.length() > 120 ? result.substring(0, 120) + "..." : result);
            return result;

        } catch (Exception e) {
            log.error("[AI] Error calling Gemini for context '{}': {}", context, e.getMessage());
            return null;
        }
    }
}
