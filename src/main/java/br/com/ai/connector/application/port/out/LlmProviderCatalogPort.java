package br.com.ai.connector.application.port.out;

import br.com.ai.connector.dto.ProviderInfo;
import br.com.ai.connector.provider.LlmProvider;

import java.util.List;

public interface LlmProviderCatalogPort {
    LlmProvider require(String providerName);

    List<ProviderInfo> listProviders();
}
