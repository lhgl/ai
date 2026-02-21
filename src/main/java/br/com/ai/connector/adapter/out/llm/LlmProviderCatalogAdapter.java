package br.com.ai.connector.adapter.out.llm;

import br.com.ai.connector.application.port.out.LlmProviderCatalogPort;
import br.com.ai.connector.dto.ProviderInfo;
import br.com.ai.connector.provider.LlmProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class LlmProviderCatalogAdapter implements LlmProviderCatalogPort {

    private final Map<String, LlmProvider> providers = new HashMap<>();

    @Inject
    public LlmProviderCatalogAdapter(List<LlmProvider> discoveredProviders) {
        for (LlmProvider provider : discoveredProviders) {
            providers.put(provider.name().toUpperCase(Locale.ROOT), provider);
        }
    }

    @Override
    public LlmProvider require(String providerName) {
        LlmProvider provider = providers.get(providerName.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalArgumentException("Provider nao suportado: " + providerName);
        }
        return provider;
    }

    @Override
    public List<ProviderInfo> listProviders() {
        return providers.values().stream()
                .map(p -> new ProviderInfo(p.name(), p.defaultModel()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }
}
