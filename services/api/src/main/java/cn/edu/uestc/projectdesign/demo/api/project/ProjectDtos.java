package cn.edu.uestc.projectdesign.demo.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {
    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 80) String heritageType) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            String heritageType,
            OffsetDateTime createdAt) {
    }

    public record ImageResponse(
            UUID id,
            UUID projectId,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String label,
            OffsetDateTime createdAt) {
    }

    public record CreateJobRequest(String algorithm) {
        public CreateJobRequest {
            if (algorithm == null || algorithm.isBlank()) {
                algorithm = "COLMAP_SPARSE";
            }
        }
    }

    public record JobResponse(
            UUID id,
            UUID projectId,
            String algorithm,
            String status,
            int imageCount,
            String message,
            String resultPath,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record WorkerStatusRequest(
            @NotBlank String status,
            @Size(max = 4000) String message,
            @Size(max = 500) String resultPath) {
    }
}
