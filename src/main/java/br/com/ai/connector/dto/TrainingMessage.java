package br.com.ai.connector.dto;

public record TrainingMessage(
        String dataType,
        String dataCharacteristic,
        String content,
        String routeKey,
        String moduleKey,
        String profileId,
        String sourceType
) {
}
