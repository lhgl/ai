package br.com.ai.connector.application.usecase;

import br.com.ai.connector.application.port.out.EventPublisherPort;
import br.com.ai.connector.application.port.out.KnowledgeContextPort;
import br.com.ai.connector.application.port.out.LlmProviderCatalogPort;
import br.com.ai.connector.application.port.out.RequestLogPort;
import br.com.ai.connector.application.port.out.RouteConfigPort;
import br.com.ai.connector.dto.ConnectorEvent;
import br.com.ai.connector.dto.InferenceRequest;
import br.com.ai.connector.dto.InferenceResponse;
import br.com.ai.connector.dto.ProviderPayload;
import br.com.ai.connector.dto.RouteConfig;
import br.com.ai.connector.provider.LlmProvider;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class InferenceUseCase {

    @Inject
    RouteConfigPort routeConfigPort;

    @Inject
    RequestLogPort requestLogPort;

    @Inject
    LlmProviderCatalogPort providerCatalog;

    @Inject
    EventPublisherPort eventPublisher;

    @Inject
    KnowledgeContextPort knowledgeContextPort;

    public InferenceResponse ask(
            String dataType,
            String dataCharacteristic,
            String moduleKey,
            String profileId,
            InferenceRequest request
    ) {
        RouteConfig route = resolveRoute(dataType, dataCharacteristic);
        LlmProvider provider = providerCatalog.require(route.provider());
        String model = route.effectiveModel(provider.defaultModel());
        String systemPrompt = Objects.requireNonNullElse(route.systemPrompt(), "Voce e um assistente corporativo.");
        String compiledPrompt = compilePrompt(
                request.prompt(),
                routeConfigPort.loadContextSnippets(route.routeId(), 8),
                knowledgeContextPort.loadModuleContext(route.routeId(), moduleKey, 8),
                knowledgeContextPort.loadProfileContext(profileId, moduleKey, 8)
        );

        ProviderPayload payload = new ProviderPayload(
                provider.name(),
                model,
                systemPrompt,
                compiledPrompt,
                route.temperature(),
                route.maxTokens()
        );

        String output = provider.invoke(payload);
        requestLogPort.saveRequestLog(route.routeKey(), provider.name(), model, shrink(request.prompt()), shrink(output));
        eventPublisher.publish(new ConnectorEvent(
                "INFERENCE_COMPLETED",
                route.routeKey(),
                provider.name(),
                model,
                "SUCCESS",
                "Inference finalizada.",
                Instant.now()
        ));
        return new InferenceResponse(provider.name(), model, output, route.routeKey());
    }

    public Multi<String> askStream(
            String dataType,
            String dataCharacteristic,
            String moduleKey,
            String profileId,
            InferenceRequest request
    ) {
        RouteConfig route = resolveRoute(dataType, dataCharacteristic);
        LlmProvider provider = providerCatalog.require(route.provider());
        String model = route.effectiveModel(provider.defaultModel());
        String systemPrompt = Objects.requireNonNullElse(route.systemPrompt(), "Voce e um assistente corporativo.");
        String compiledPrompt = compilePrompt(
                request.prompt(),
                routeConfigPort.loadContextSnippets(route.routeId(), 8),
                knowledgeContextPort.loadModuleContext(route.routeId(), moduleKey, 8),
                knowledgeContextPort.loadProfileContext(profileId, moduleKey, 8)
        );

        ProviderPayload payload = new ProviderPayload(
                provider.name(),
                model,
                systemPrompt,
                compiledPrompt,
                route.temperature(),
                route.maxTokens()
        );
        return provider.stream(payload);
    }

    public void processTrainingItem(
            String dataType,
            String characteristic,
            String moduleKey,
            String profileId,
            String content,
            String sourceType
    ) {
        RouteConfig route = resolveRoute(dataType, characteristic);
        LlmProvider provider = providerCatalog.require(route.provider());
        String model = route.effectiveModel(provider.defaultModel());
        String system = """
                Voce esta recebendo atualizacao de conhecimento de dominio.
                Extraia regras, conceitos e fatos uteis para respostas futuras.
                """;
        String userPrompt = "Conteudo de atualizacao:\n" + content;
        ProviderPayload payload = new ProviderPayload(provider.name(), model, system, userPrompt, 0.1, 800);
        String output = provider.invoke(payload);
        String effectiveSource = isBlank(sourceType) ? "JOB" : sourceType;
        String effectiveModule = isBlank(moduleKey) ? "GENERAL" : moduleKey;

        knowledgeContextPort.saveModuleContext(route.routeId(), effectiveModule, output, effectiveSource);
        if (!isBlank(profileId)) {
            knowledgeContextPort.saveProfileContext(profileId, effectiveModule, output, effectiveSource);
        }

        requestLogPort.saveRequestLog(route.routeKey(), provider.name(), model, shrink(userPrompt), shrink(output));
        eventPublisher.publish(new ConnectorEvent(
                "TRAINING_ITEM_PROCESSED",
                route.routeKey(),
                provider.name(),
                model,
                "SUCCESS",
                "Item de treinamento processado.",
                Instant.now()
        ));
    }

    public List<String> listProviderNames() {
        return providerCatalog.listProviders().stream().map(p -> p.name()).toList();
    }

    private RouteConfig resolveRoute(String dataType, String dataCharacteristic) {
        return routeConfigPort.resolveRoute(dataType, dataCharacteristic)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhuma rota ativa encontrada para dataType=" + dataType + " e characteristic=" + dataCharacteristic
                ));
    }

    private String compilePrompt(
            String prompt,
            List<String> routeSnippets,
            List<String> moduleSnippets,
            List<String> profileSnippets
    ) {
        if (routeSnippets.isEmpty() && moduleSnippets.isEmpty() && profileSnippets.isEmpty()) {
            return prompt;
        }
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "Contexto de rota", routeSnippets);
        appendSection(sb, "Contexto de modulo", moduleSnippets);
        appendSection(sb, "Contexto de perfil", profileSnippets);
        sb.append("\nPergunta do usuario:\n").append(prompt);
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, List<String> snippets) {
        if (snippets.isEmpty()) {
            return;
        }
        sb.append(title).append(":\n");
        for (String snippet : snippets) {
            sb.append("- ").append(snippet).append('\n');
        }
        sb.append('\n');
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String shrink(String input) {
        if (input == null) {
            return null;
        }
        int max = 1000;
        return input.length() > max ? input.substring(0, max) : input;
    }
}
