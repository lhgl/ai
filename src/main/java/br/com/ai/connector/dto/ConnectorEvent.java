package br.com.ai.connector.dto;

import java.time.Instant;

public record ConnectorEvent(
        String eventType,
        String routeKey,
        String provider,
        String model,
        String status,
        String details,
        Instant createdAt
) {
}
