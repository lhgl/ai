package br.com.ai.connector.dto;

public record ProviderPayload(
        String provider,
        String model,
        String systemPrompt,
        String userPrompt,
        Double temperature,
        Integer maxTokens
) {
}
