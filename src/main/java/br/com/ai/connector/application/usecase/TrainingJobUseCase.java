package br.com.ai.connector.application.usecase;

import br.com.ai.connector.application.port.out.TrainingQueuePort;
import br.com.ai.connector.dto.TrainingMessage;
import br.com.ai.connector.dto.TrainingQueueItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class TrainingJobUseCase {

    private static final Logger LOG = Logger.getLogger(TrainingJobUseCase.class);

    @Inject
    TrainingQueuePort trainingQueuePort;

    @Inject
    InferenceUseCase inferenceUseCase;

    public void runBatch(int batchSize) {
        List<TrainingQueueItem> items = trainingQueuePort.fetchPendingTrainingItems(batchSize);
        for (TrainingQueueItem item : items) {
            try {
                inferenceUseCase.processTrainingItem(
                        item.dataType(),
                        item.dataCharacteristic(),
                        item.moduleKey(),
                        item.profileId(),
                        item.content(),
                        item.sourceType()
                );
                trainingQueuePort.markTrainingDone(item.id());
            } catch (Exception e) {
                LOG.errorf(e, "Falha no item de treinamento id=%d", item.id());
                trainingQueuePort.markTrainingFailed(item.id(), e.getMessage());
            }
        }
    }

    public void enqueue(TrainingMessage message) {
        if (message == null
                || isBlank(message.dataType())
                || isBlank(message.dataCharacteristic())
                || isBlank(message.content())) {
            throw new IllegalArgumentException("Mensagem de treinamento incompleta.");
        }
        trainingQueuePort.enqueueTrainingItem(
                message.dataType(),
                message.dataCharacteristic(),
                message.content(),
                message.routeKey(),
                message.moduleKey(),
                message.profileId(),
                message.sourceType()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
