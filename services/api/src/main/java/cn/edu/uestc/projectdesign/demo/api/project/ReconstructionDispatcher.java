package cn.edu.uestc.projectdesign.demo.api.project;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ReconstructionDispatcher {
    private final RestClient restClient;
    private final String workerToken;
    private final ProjectRepository repository;

    public ReconstructionDispatcher(
            RestClient.Builder builder,
            ProjectRepository repository,
            @Value("${app.worker.base-url}") String workerBaseUrl,
            @Value("${app.worker.token}") String workerToken) {
        this.restClient = builder
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(workerBaseUrl)
                .build();
        this.repository = repository;
        this.workerToken = workerToken;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(JobCreated event) {
        try {
            restClient.post()
                    .uri("/jobs")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + workerToken)
                    .body(new WorkerJobRequest(
                            event.jobId(), event.projectId(), event.algorithm(), event.imageCount()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            repository.updateJob(event.jobId(), "FAILED", "无法提交到重建工作进程: " + exception.getMessage(), null);
        }
    }

    public record JobCreated(UUID jobId, UUID projectId, String algorithm, int imageCount) {
    }

    private record WorkerJobRequest(UUID jobId, UUID projectId, String algorithm, int imageCount) {
    }
}
