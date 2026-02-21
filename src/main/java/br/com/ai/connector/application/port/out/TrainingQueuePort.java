package br.com.ai.connector.application.port.out;

import br.com.ai.connector.dto.TrainingQueueItem;

import java.util.List;

public interface TrainingQueuePort {
    List<TrainingQueueItem> fetchPendingTrainingItems(int batchSize);

    void markTrainingDone(long id);

    void markTrainingFailed(long id, String reason);

    void enqueueTrainingItem(
            String dataType,
            String dataCharacteristic,
            String content,
            String routeKey,
            String moduleKey,
            String profileId,
            String sourceType
    );
}
