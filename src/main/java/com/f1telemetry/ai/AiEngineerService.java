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

import org.springframework.beans.factory.annotation.Autowired;
import com.f1telemetry.domain.User;

/**
 * Core AI service — calls the Gemini 2.0/1.5 API to generate
 * engineer-style insights from telemetry data.
 *
 * Uses Java 11+ HttpClient (no extra dependency).
 * API key is injected from environment variable via application.properties.
 */
@Slf4j
@Service
public class AiEngineerService {

    @Value("${gemini.api.model:gemini-3.1-flash-lite}")
    private String modelName;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final com.f1telemetry.service.ActiveUserService activeUserService;
    private final com.f1telemetry.service.AiPricingService pricingService;
    private final com.f1telemetry.repository.AiUsageRecordRepository usageRepository;
    private final com.f1telemetry.repository.UserPreferenceRepository preferenceRepository;

    @Autowired
    public AiEngineerService(com.f1telemetry.service.ActiveUserService activeUserService,
                             com.f1telemetry.service.AiPricingService pricingService,
                             com.f1telemetry.repository.AiUsageRecordRepository usageRepository,
                             com.f1telemetry.repository.UserPreferenceRepository preferenceRepository) {
        this.activeUserService = activeUserService;
        this.pricingService = pricingService;
        this.usageRepository = usageRepository;
        this.preferenceRepository = preferenceRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private String getApiUrl(String selectedModel) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + selectedModel + ":generateContent";
    }

    public String getModelName() {
        User activeUser = activeUserService.getActiveUser();
        if (activeUser != null) {
            try {
                com.f1telemetry.domain.UserPreference prefs = preferenceRepository.findByUser(activeUser).orElse(null);
                if (prefs != null && prefs.getSelectedTextModel() != null) {
                    return prefs.getSelectedTextModel();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return modelName;
    }

    @Value("${gemini.api.key:}")
    private String apiKey;

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
        log.debug("Generating lap alert for lap {} on {}", ctx.getLapNumber(), ctx.getTrackName());
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
        log.info("Generating session debrief for {} {} ({} laps)", trackName, sessionType, laps.size());
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
        log.debug("Chat question for {} {}: '{}'", trackName, sessionType, 
                question.length() > 80 ? question.substring(0, 80) + "..." : question);
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

        String modelToUse = getModelName();
        long startTime = System.currentTimeMillis();

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
                    .uri(URI.create(getApiUrl(modelToUse)))
                    .header("Content-Type", "application/json")
                    .header("X-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            log.debug("[AI] Calling {} for context '{}' (prompt length: {} chars)", 
                    modelToUse, context, prompt.length());

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

            // Extract usageMetadata
            JsonNode usageNode = root.path("usageMetadata");
            int inputTokens = usageNode.path("promptTokenCount").asInt(0);
            int outputTokens = usageNode.path("candidatesTokenCount").asInt(0);

            String result = text.asText().trim();
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("[AI] {} response from {} in {}ms (in={} out={} tokens, {} chars): {}", 
                    context, modelToUse, elapsedMs, inputTokens, outputTokens, result.length(),
                    result.length() > 120 ? result.substring(0, 120) + "..." : result);

            // Record usage in DB if user is present
            User activeUser = activeUserService.getActiveUser();
            if (activeUser != null) {
                double cost = pricingService.calculateTextCost(modelToUse, inputTokens, outputTokens);
                usageRepository.save(new com.f1telemetry.domain.AiUsageRecord(
                    activeUser, System.currentTimeMillis(), modelToUse, context,
                    inputTokens, outputTokens, "TOKENS", cost
                ));
                pricingService.accrueCharges(activeUser, cost);
            }

            return result;

        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.error("[AI] Error calling {} for context '{}' after {}ms: {}", 
                    modelToUse, context, elapsedMs, e.getMessage());
            return null;
        }
    }
}
