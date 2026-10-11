package com.tripmind.tripmind.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HybridAiProviderService implements AiProviderService {

    private final RestClient restClient;
    private final String provider;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String xAiApiKey;
    private final String xAiModel;
    private final String openRouterApiKey;
    private final String openRouterModel;
    private final String groqApiKey;
    private final String groqModel;

    public HybridAiProviderService(
            RestClient.Builder restClientBuilder,
            @Value("${tripmind.ai.provider:rule_based}") String provider,
            @Value("${tripmind.ai.openai.api-key:${OPENAI_API_KEY:}}") String openAiApiKey,
            @Value("${tripmind.ai.openai.model:gpt-5.1}") String openAiModel,
            @Value("${tripmind.ai.xai.api-key:${XAI_API_KEY:}}") String xAiApiKey,
            @Value("${tripmind.ai.xai.model:grok-4.20-reasoning}") String xAiModel,
            @Value("${tripmind.ai.openrouter.api-key:${OPENROUTER_API_KEY:}}") String openRouterApiKey,
            @Value("${tripmind.ai.openrouter.model:openrouter/free}") String openRouterModel,
            @Value("${tripmind.ai.groq.api-key:${GROQ_API_KEY:}}") String groqApiKey,
            @Value("${tripmind.ai.groq.model:qwen/qwen3.8-27b}") String groqModel
    ) {
        this.restClient = restClientBuilder.build();
        this.provider = provider;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.xAiApiKey = xAiApiKey;
        this.xAiModel = xAiModel;
        this.openRouterApiKey = openRouterApiKey;
        this.openRouterModel = openRouterModel;
        this.groqApiKey = groqApiKey;
        this.groqModel = groqModel;
    }

    @Override
    public String polishChatReply(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        if ("openai".equalsIgnoreCase(provider) && hasKey(openAiApiKey)) {
            return callOpenAi(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan);
        }
        if (isXaiProvider() && hasKey(xAiApiKey)) {
            return callXai(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan);
        }
        if ("openrouter".equalsIgnoreCase(provider) && hasKey(openRouterApiKey)) {
            return callOpenRouter(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan);
        }
        if ("groq".equalsIgnoreCase(provider) && hasKey(groqApiKey)) {
            return callGroq(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan);
        }
        return draftReply;
    }

    @Override
    public String enrichAgentOutput(String agentName, String prompt, String fallback) {
        String system = """
                You are an AI enrichment layer inside a travel-planning agent. Be practical, concise, and honest.
                Do not claim live inventory, confirmed booking, guaranteed prices, or real-time supplier availability.
                Return plain text only.
                """;
        String userPrompt = "Agent: " + agentName + "\n" + prompt;
        if ("openai".equalsIgnoreCase(provider) && hasKey(openAiApiKey)) {
            return callOpenAiGeneric(system, userPrompt, fallback, 260);
        }
        if (isXaiProvider() && hasKey(xAiApiKey)) {
            return callChatCompletion("https://api.x.ai/v1/chat/completions", xAiApiKey, xAiModel, system, userPrompt, fallback, 260);
        }
        if ("openrouter".equalsIgnoreCase(provider) && hasKey(openRouterApiKey)) {
            return callOpenRouterGeneric(system, userPrompt, fallback, 260);
        }
        if ("groq".equalsIgnoreCase(provider) && hasKey(groqApiKey)) {
            return callChatCompletion("https://api.groq.com/openai/v1/chat/completions", groqApiKey, groqModel, system, userPrompt, fallback, 260);
        }
        return fallback;
    }

    private String callOpenAi(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://api.openai.com/v1/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", openAiModel,
                            "instructions", systemInstruction(),
                            "input", prompt(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan),
                            "max_output_tokens", 180
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractOpenAiText(response);
            return usable(output) ? output : draftReply;
        } catch (RuntimeException ignored) {
            return draftReply;
        }
    }

    private String callOpenAiGeneric(String system, String prompt, String fallback, int maxTokens) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://api.openai.com/v1/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", openAiModel,
                            "instructions", system,
                            "input", prompt,
                            "max_output_tokens", maxTokens
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractOpenAiText(response);
            return usable(output) ? output : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String callXai(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://api.x.ai/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + xAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", xAiModel,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemInstruction()),
                                    Map.of("role", "user", "content", prompt(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan))
                            ),
                            "stream", false
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractChatCompletionText(response);
            return usable(output) ? output : draftReply;
        } catch (RuntimeException ignored) {
            return draftReply;
        }
    }

    private String callOpenRouter(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://openrouter.ai/api/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterApiKey)
                    .header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "TripMind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", openRouterModel,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemInstruction()),
                                    Map.of("role", "user", "content", prompt(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan))
                            )
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractChatCompletionText(response);
            return usable(output) ? output : draftReply;
        } catch (RuntimeException ignored) {
            return draftReply;
        }
    }

    private String callGroq(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", groqModel,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemInstruction()),
                                    Map.of("role", "user", "content", prompt(userMessage, draftReply, tripSnapshot, missingFields, readyToPlan))
                            ),
                            "temperature", 0.2,
                            "max_tokens", 180
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractChatCompletionText(response);
            return usable(output) ? output : draftReply;
        } catch (RuntimeException ignored) {
            return draftReply;
        }
    }

    private String callChatCompletion(
            String uri,
            String apiKey,
            String model,
            String system,
            String prompt,
            String fallback,
            int maxTokens
    ) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "messages", List.of(
                                    Map.of("role", "system", "content", system),
                                    Map.of("role", "user", "content", prompt)
                            ),
                            "temperature", 0.2,
                            "max_tokens", maxTokens
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractChatCompletionText(response);
            return usable(output) ? output : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String callOpenRouterGeneric(String system, String prompt, String fallback, int maxTokens) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://openrouter.ai/api/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterApiKey)
                    .header("HTTP-Referer", "http://localhost:8080")
                    .header("X-Title", "TripMind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", openRouterModel,
                            "messages", List.of(
                                    Map.of("role", "system", "content", system),
                                    Map.of("role", "user", "content", prompt)
                            ),
                            "max_tokens", maxTokens
                    ))
                    .retrieve()
                    .body(Map.class);
            String output = extractChatCompletionText(response);
            return usable(output) ? output : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String systemInstruction() {
        return """
                You are TripMind's travel-planning chat layer. Rewrite backend draft replies so they feel natural,
                confident, and concise. Do not invent missing facts, prices, APIs, hotels, flights, or bookings.
                Use the trip snapshot and missing-fields list as the source of truth. Never claim a field is captured
                unless it exists in the trip snapshot. Ask only for fields listed in missing fields. Never ask whether
                the budget is total or per-person unless the backend draft explicitly asks that. Avoid phrasing like
                "1 other person"; use the exact traveler count instead. If the backend says the plan is ready or
                generated, confirm that clearly. Keep replies under 90 words.
                """;
    }

    private String prompt(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        return "User message: " + userMessage
                + "\nBackend draft reply: " + draftReply
                + "\nTrip snapshot: " + tripSnapshot
                + "\nMissing fields: " + missingFields
                + "\nReady to plan: " + readyToPlan
                + "\nRewrite only the assistant reply.";
    }

    private String extractOpenAiText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object directText = response.get("output_text");
        if (directText instanceof String text) {
            return text;
        }
        Object output = response.get("output");
        if (!(output instanceof List<?> items)) {
            return null;
        }
        for (Object item : items) {
            if (item instanceof Map<?, ?> itemMap && itemMap.get("content") instanceof List<?> contentItems) {
                for (Object contentItem : contentItems) {
                    if (contentItem instanceof Map<?, ?> contentMap && contentMap.get("text") instanceof String text) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private String extractChatCompletionText(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object first = choices.getFirst();
        if (first instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            Object content = message.get("content");
            return content instanceof String text ? text : null;
        }
        return null;
    }

    private boolean isXaiProvider() {
        String normalized = provider.toLowerCase(Locale.ENGLISH);
        return normalized.equals("xai") || normalized.equals("grok");
    }

    private boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private boolean usable(String text) {
        return text != null && !text.isBlank();
    }
}
