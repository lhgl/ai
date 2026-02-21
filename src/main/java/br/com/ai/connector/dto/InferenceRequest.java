package br.com.ai.connector.dto;

import java.util.Map;

public record InferenceRequest(
        String prompt,
        String conversationId,
        Map<String, Object> metadata
) {
}
