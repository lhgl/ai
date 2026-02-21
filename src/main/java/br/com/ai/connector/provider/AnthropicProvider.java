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
public class AnthropicProvider implements LlmProvider {

    @ConfigProperty(name = "provider.anthropic.base-url")
    String baseUrl;

    @ConfigProperty(name = "provider.anthropic.api-key")
    String apiKey;

    @ConfigProperty(name = "provider.anthropic.default-model")
    String defaultModel;

    @ConfigProperty(name = "provider.anthropic.version")
    String version;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final ObjectMapper objectMapper;

    public AnthropicProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "ANTHROPIC";
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
        body.put("system", payload.systemPrompt());
        body.put("temperature", payload.temperature() == null ? 0.2 : payload.temperature());
        body.put("max_tokens", payload.maxTokens() == null ? 1200 : payload.maxTokens());
        body.put("messages", List.of(Map.of("role", "user", "content", payload.userPrompt())));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", version)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        String response = send(request);
        return parseAnthropicText(response);
    }

    private String parseAnthropicText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                JsonNode text = content.get(0).path("text");
                if (!text.isMissingNode()) {
                    return text.asText();
                }
            }
            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao interpretar resposta da Anthropic", e);
        }
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Anthropic retornou erro HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Erro ao chamar Anthropic", e);
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar Anthropic", e);
        }
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao serializar payload da Anthropic", e);
        }
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY nao configurada.");
        }
    }
}
