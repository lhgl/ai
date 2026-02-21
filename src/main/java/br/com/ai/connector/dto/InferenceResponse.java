package br.com.ai.connector.dto;

public record InferenceResponse(
        String provider,
        String model,
        String output,
        String routeKey
) {
}
