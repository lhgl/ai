package br.com.ai.connector.application.usecase;

import br.com.ai.connector.application.port.out.TrainingQueuePort;
import br.com.ai.connector.dto.TrainingMessage;
import br.com.ai.connector.dto.TrainingQueueItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TrainingJobUseCaseTest {

    @Test
    void shouldRejectInvalidTrainingMessage() {
        TrainingJobUseCase useCase = new TrainingJobUseCase();
        useCase.trainingQueuePort = new NoopTrainingQueuePort();
        useCase.inferenceUseCase = new InferenceUseCase();

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> useCase.enqueue(new TrainingMessage("", "FAQ", "", null, null, null, null))
        );
        Assertions.assertTrue(ex.getMessage().contains("incompleta"));
    }

    @Test
    void shouldEnqueueValidMessage() {
        TrainingJobUseCase useCase = new TrainingJobUseCase();
        CapturingTrainingQueuePort queuePort = new CapturingTrainingQueuePort();
        useCase.trainingQueuePort = queuePort;
        useCase.inferenceUseCase = new InferenceUseCase();

        useCase.enqueue(new TrainingMessage("SUPPORT", "FAQ", "conteudo", "SUPPORT_DEFAULT", "LEARNING", "STUDENT-1", "RABBIT"));

        Assertions.assertEquals(1, queuePort.enqueued.size());
        Assertions.assertEquals("SUPPORT|FAQ|SUPPORT_DEFAULT|LEARNING|STUDENT-1", queuePort.enqueued.get(0));
    }

    private static class NoopTrainingQueuePort implements TrainingQueuePort {
        @Override
        public List<TrainingQueueItem> fetchPendingTrainingItems(int batchSize) {
            return List.of();
        }

        @Override
        public void markTrainingDone(long id) {
        }

        @Override
        public void markTrainingFailed(long id, String reason) {
        }

        @Override
        public void enqueueTrainingItem(
                String dataType,
                String dataCharacteristic,
                String content,
                String routeKey,
                String moduleKey,
                String profileId,
                String sourceType
        ) {
        }
    }

    private static class CapturingTrainingQueuePort extends NoopTrainingQueuePort {
        final List<String> enqueued = new ArrayList<>();

        @Override
        public void enqueueTrainingItem(
                String dataType,
                String dataCharacteristic,
                String content,
                String routeKey,
                String moduleKey,
                String profileId,
                String sourceType
        ) {
            enqueued.add(dataType + "|" + dataCharacteristic + "|" + routeKey + "|" + moduleKey + "|" + profileId);
        }
    }
}
