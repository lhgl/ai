package br.com.ai.connector.dto;

public record TrainingQueueItem(
        long id,
        String dataType,
        String dataCharacteristic,
        String content,
        String routeKey,
        String moduleKey,
        String profileId,
        String sourceType
) {
}
