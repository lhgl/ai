package br.com.ai.connector.adapter.out.queue;

import br.com.ai.connector.application.port.out.EventPublisherPort;
import br.com.ai.connector.dto.ConnectorEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.annotations.Channel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RabbitEventPublisherAdapter implements EventPublisherPort {

    private static final Logger LOG = Logger.getLogger(RabbitEventPublisherAdapter.class);

    @Inject
    @Channel("connector-events")
    Emitter<String> eventEmitter;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void publish(ConnectorEvent event) {
        try {
            eventEmitter.send(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            LOG.error("Falha ao serializar evento do conector", e);
        } catch (Exception e) {
            LOG.error("Falha ao publicar evento RabbitMQ", e);
        }
    }
}
