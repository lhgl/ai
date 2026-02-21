package br.com.ai.connector.dto;

public record RouteConfig(
        long routeId,
        String routeKey,
        String provider,
        String model,
        String systemPrompt,
        Double temperature,
        Integer maxTokens
) {
    public String effectiveModel(String fallbackModel) {
        return (model == null || model.isBlank()) ? fallbackModel : model;
    }
}
