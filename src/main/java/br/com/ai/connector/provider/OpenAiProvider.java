package br.com.ai.connector.provider;

import br.com.ai.connector.dto.ProviderPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OpenAiProvider implements LlmProvider {

    @ConfigProperty(name = "provider.openai.base-url")
    String baseUrl;

    @ConfigProperty(name = "provider.openai.api-key")
    String apiKey;

    @ConfigProperty(name = "provider.openai.default-model")
    String defaultModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper objectMapper;

    public OpenAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "OPENAI";
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }

    @Override
    @Retry(maxRetries = 2, delay = 300)
    @Timeout(130000)
    public String invoke(ProviderPayload payload) {
        ensureApiKey();

        Map<String, Object> body = new HashMap<>();
        body.put("model", payload.model());
        body.put("temperature", payload.temperature() == null ? 0.2 : payload.temperature());
        body.put("max_output_tokens", payload.maxTokens() == null ? 1200 : payload.maxTokens());
        body.put("input", List.of(
                Map.of("role", "system", "content", payload.systemPrompt()),
                Map.of("role", "user", "content", payload.userPrompt())
        ));

        String json = toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/responses"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        String response = send(request);
        return parseOpenAiText(response);
    }

    private String parseOpenAiText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("output_text")) {
                return root.get("output_text").asText();
            }
            JsonNode output = root.path("output");
            if (output.isArray() && !output.isEmpty()) {
                JsonNode content = output.get(0).path("content");
                if (content.isArray() && !content.isEmpty()) {
                    JsonNode text = content.get(0).path("text");
                    if (!text.isMissingNode()) {
                        return text.asText();
                    }
                }
            }
            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao interpretar resposta da OpenAI", e);
        }
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI retornou erro HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Erro ao chamar OpenAI", e);
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar OpenAI", e);
        }
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao serializar payload da OpenAI", e);
        }
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY nao configurada.");
        }
    }
}
