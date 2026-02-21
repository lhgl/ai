package br.com.ai.connector.application.usecase;

import br.com.ai.connector.application.port.out.EventPublisherPort;
import br.com.ai.connector.application.port.out.KnowledgeContextPort;
import br.com.ai.connector.application.port.out.LlmProviderCatalogPort;
import br.com.ai.connector.application.port.out.RequestLogPort;
import br.com.ai.connector.application.port.out.RouteConfigPort;
import br.com.ai.connector.dto.ConnectorEvent;
import br.com.ai.connector.dto.InferenceRequest;
import br.com.ai.connector.dto.InferenceResponse;
import br.com.ai.connector.dto.ProviderInfo;
import br.com.ai.connector.dto.ProviderPayload;
import br.com.ai.connector.dto.RouteConfig;
import br.com.ai.connector.provider.LlmProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

class InferenceUseCaseTest {

    @Test
    void shouldResolveRouteAndInvokeProvider() {
        InferenceUseCase useCase = new InferenceUseCase();
        AtomicReference<ConnectorEvent> eventRef = new AtomicReference<>();

        useCase.routeConfigPort = new RouteConfigPort() {
            @Override
            public Optional<RouteConfig> resolveRoute(String dataType, String characteristic) {
                return Optional.of(new RouteConfig(1L, "SUPPORT_DEFAULT", "OPENAI", "gpt-5", "system", 0.2, 200));
            }

            @Override
            public List<String> loadContextSnippets(long routeId, int limit) {
                return List.of("regra 1", "regra 2");
            }
        };
        useCase.requestLogPort = (routeKey, provider, model, requestSummary, responseSummary) -> {
        };
        useCase.providerCatalog = new LlmProviderCatalogPort() {
            @Override
            public LlmProvider require(String providerName) {
                return new LlmProvider() {
                    @Override
                    public String name() {
                        return "OPENAI";
                    }

                    @Override
                    public String defaultModel() {
                        return "gpt-5";
                    }

                    @Override
                    public String invoke(ProviderPayload payload) {
                        return "resposta-ok";
                    }
                };
            }

            @Override
            public List<ProviderInfo> listProviders() {
                return List.of(new ProviderInfo("OPENAI", "gpt-5"));
            }
        };
        useCase.eventPublisher = eventRef::set;
        useCase.knowledgeContextPort = new KnowledgeContextPort() {
            @Override
            public List<String> loadModuleContext(long routeId, String moduleKey, int limit) {
                return List.of("modulo-a");
            }

            @Override
            public List<String> loadProfileContext(String profileId, String moduleKey, int limit) {
                return List.of("perfil-a");
            }

            @Override
            public void saveModuleContext(long routeId, String moduleKey, String contextText, String sourceType) {
            }

            @Override
            public void saveProfileContext(String profileId, String moduleKey, String contextText, String sourceType) {
            }
        };

        InferenceResponse response = useCase.ask("SUPPORT", "FAQ", "LEARNING", "STUDENT-1", new InferenceRequest("pergunta", null, null));

        Assertions.assertEquals("OPENAI", response.provider());
        Assertions.assertEquals("gpt-5", response.model());
        Assertions.assertEquals("resposta-ok", response.output());
        Assertions.assertNotNull(eventRef.get());
        Assertions.assertEquals("INFERENCE_COMPLETED", eventRef.get().eventType());
    }
}
