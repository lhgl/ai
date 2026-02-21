package br.com.ai.connector.adapter.in.scheduler;

import br.com.ai.connector.application.usecase.TrainingJobUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TrainingJobScheduler {

    @ConfigProperty(name = "job.training.enabled")
    boolean enabled;

    @ConfigProperty(name = "job.training.batch-size")
    int batchSize;

    @Inject
    TrainingJobUseCase trainingJobUseCase;

    @Scheduled(every = "{job.training.every}")
    void runTrainingBatch() {
        if (enabled) {
            trainingJobUseCase.runBatch(batchSize);
        }
    }
}
