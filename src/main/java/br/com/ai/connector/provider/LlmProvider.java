package br.com.ai.connector.provider;

import br.com.ai.connector.dto.ProviderPayload;
import io.smallrye.mutiny.Multi;

public interface LlmProvider {
    String name();

    String defaultModel();

    String invoke(ProviderPayload payload);

    default Multi<String> stream(ProviderPayload payload) {
        String output = invoke(payload);
        return Multi.createFrom().iterable(output.lines().toList());
    }
}
