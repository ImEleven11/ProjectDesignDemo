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
            @NotBlank @Size(max = 80) String heritageType,
            @Size(max = 120) String collectionCode,
            @Size(max = 160) String era,
            @Size(max = 240) String material,
            @Size(max = 240) String dimensions,
            @Size(max = 500) String provenance) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 4000) String description,
            @NotBlank @Size(max = 80) String heritageType,
            @Size(max = 120) String collectionCode,
            @Size(max = 160) String era,
            @Size(max = 240) String material,
            @Size(max = 240) String dimensions,
            @Size(max = 500) String provenance) {
    }

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            String heritageType,
            String collectionCode,
            String era,
            String material,
            String dimensions,
            String provenance,
            String publicationStatus,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
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
            @Size(max = 500) String resultPath,
            Long pointCount,
            Long faceCount,
            Long sizeBytes) {
    }

    public record ArtifactResponse(
            UUID id,
            UUID projectId,
            UUID jobId,
            String name,
            int versionNumber,
            String format,
            long pointCount,
            long faceCount,
            long sizeBytes,
            String resultPath,
            boolean current,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record RenameArtifactRequest(@NotBlank @Size(max = 160) String name) {
    }

    public record PublicHeritageResponse(
            UUID id,
            String name,
            String description,
            String heritageType,
            String collectionCode,
            String era,
            String material,
            String dimensions,
            String provenance,
            UUID artifactId,
            UUID jobId,
            String artifactName,
            int versionNumber,
            String format,
            long pointCount,
            long faceCount,
            long sizeBytes,
            OffsetDateTime publishedAt) {
    }
}
