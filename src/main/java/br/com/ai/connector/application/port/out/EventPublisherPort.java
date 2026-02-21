package br.com.ai.connector.application.port.out;

import br.com.ai.connector.dto.ConnectorEvent;

public interface EventPublisherPort {
    void publish(ConnectorEvent event);
}
