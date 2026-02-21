package br.com.ai.connector.adapter.in.queue;

import br.com.ai.connector.application.usecase.TrainingJobUseCase;
import br.com.ai.connector.dto.TrainingMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TrainingMessageConsumer {

    private static final Logger LOG = Logger.getLogger(TrainingMessageConsumer.class);

    @Inject
    TrainingJobUseCase trainingJobUseCase;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("training-input")
    public void onMessage(String rawMessage) {
        try {
            TrainingMessage message = objectMapper.readValue(rawMessage, TrainingMessage.class);
            validate(message);
            trainingJobUseCase.enqueue(message);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Mensagem de treinamento invalida: %s", rawMessage);
        } catch (Exception e) {
            LOG.errorf(e, "Falha ao consumir mensagem de treinamento: %s", rawMessage);
            throw e;
        }
    }

    private void validate(TrainingMessage message) {
        if (message == null
                || isBlank(message.dataType())
                || isBlank(message.dataCharacteristic())
                || isBlank(message.content())) {
            throw new IllegalArgumentException("Mensagem de treinamento incompleta.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
