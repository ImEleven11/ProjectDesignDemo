package cn.edu.uestc.projectdesign.demo.api.project;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.JobResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.WorkerStatusRequest;

@RestController
@RequestMapping("/internal/jobs")
public class WorkerCallbackController {
    private static final Set<String> WORKER_STATUSES = Set.of("RUNNING", "SUCCEEDED", "FAILED");

    private final ProjectRepository repository;
    private final String workerToken;

    public WorkerCallbackController(ProjectRepository repository, @Value("${app.worker.token}") String workerToken) {
        this.repository = repository;
        this.workerToken = workerToken;
    }

    @PatchMapping("/{jobId}")
    JobResponse update(
            @PathVariable("jobId") UUID jobId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody WorkerStatusRequest request) {
        requireWorkerToken(authorization);
        String status = request.status().trim().toUpperCase(java.util.Locale.ROOT);
        if (!WORKER_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Worker 状态必须是 RUNNING、SUCCEEDED 或 FAILED");
        }
        if ("SUCCEEDED".equals(status) && (request.resultPath() == null || request.resultPath().isBlank())) {
            throw new IllegalArgumentException("成功任务必须提供 resultPath");
        }
        return repository.updateJob(jobId, status, request.message(), request.resultPath());
    }

    private void requireWorkerToken(String authorization) {
        String actual = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : "";
        if (!java.security.MessageDigest.isEqual(
                workerToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的 Worker 令牌");
        }
    }
}
