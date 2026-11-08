package cn.edu.uestc.projectdesign.demo.api.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ImageResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.JobResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ProjectResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ArtifactResponse;

@Repository
public class ProjectRepository {
    private final JdbcClient jdbcClient;

    public ProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    ProjectResponse insertProject(UUID id, String name, String description, String heritageType,
            String collectionCode, String era, String material, String dimensions, String provenance) {
        return jdbcClient.sql("""
                        INSERT INTO heritage_projects
                            (id, name, description, heritage_type, collection_code, era, material, dimensions, provenance)
                        VALUES (:id, :name, :description, :heritageType, :collectionCode, :era, :material, :dimensions, :provenance)
                        RETURNING id, name, description, heritage_type, collection_code, era, material,
                                  dimensions, provenance, created_at, updated_at
                        """)
                .param("id", id)
                .param("name", name)
                .param("description", description, Types.VARCHAR)
                .param("heritageType", heritageType)
                .param("collectionCode", collectionCode, Types.VARCHAR)
                .param("era", era, Types.VARCHAR)
                .param("material", material, Types.VARCHAR)
                .param("dimensions", dimensions, Types.VARCHAR)
                .param("provenance", provenance, Types.VARCHAR)
                .query(ProjectRepository::mapProject)
                .single();
    }

    List<ProjectResponse> findProjects() {
        return jdbcClient.sql("""
                        SELECT id, name, description, heritage_type, collection_code, era, material,
                               dimensions, provenance, created_at, updated_at
                        FROM heritage_projects ORDER BY created_at DESC
                        """)
                .query(ProjectRepository::mapProject)
                .list();
    }

    Optional<ProjectResponse> findProject(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, name, description, heritage_type, collection_code, era, material,
                               dimensions, provenance, created_at, updated_at
                        FROM heritage_projects WHERE id = :id
                        """)
                .param("id", id)
                .query(ProjectRepository::mapProject)
                .optional();
    }

    ProjectResponse updateProject(UUID id, String name, String description, String heritageType,
            String collectionCode, String era, String material, String dimensions, String provenance) {
        return jdbcClient.sql("""
                        UPDATE heritage_projects SET name = :name, description = :description,
                            heritage_type = :heritageType, collection_code = :collectionCode,
                            era = :era, material = :material, dimensions = :dimensions,
                            provenance = :provenance, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        RETURNING id, name, description, heritage_type, collection_code, era, material,
                                  dimensions, provenance, created_at, updated_at
                        """)
                .param("id", id).param("name", name)
                .param("description", description, Types.VARCHAR).param("heritageType", heritageType)
                .param("collectionCode", collectionCode, Types.VARCHAR).param("era", era, Types.VARCHAR)
                .param("material", material, Types.VARCHAR).param("dimensions", dimensions, Types.VARCHAR)
                .param("provenance", provenance, Types.VARCHAR)
                .query(ProjectRepository::mapProject).single();
    }

    int deleteProject(UUID id) {
        return jdbcClient.sql("DELETE FROM heritage_projects WHERE id = :id")
                .param("id", id).update();
    }

    ImageResponse insertImage(UUID id, UUID projectId, String originalFilename, String storedFilename,
            String contentType, long sizeBytes, String label) {
        return jdbcClient.sql("""
                        INSERT INTO project_images
                            (id, project_id, original_filename, stored_filename, content_type, size_bytes, label)
                        VALUES (:id, :projectId, :originalFilename, :storedFilename, :contentType, :sizeBytes, :label)
                        RETURNING id, project_id, original_filename, content_type, size_bytes, label, created_at
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("originalFilename", originalFilename)
                .param("storedFilename", storedFilename)
                .param("contentType", contentType, Types.VARCHAR)
                .param("sizeBytes", sizeBytes)
                .param("label", label, Types.VARCHAR)
                .query(ProjectRepository::mapImage)
                .single();
    }

    List<ImageResponse> findImages(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, original_filename, content_type, size_bytes, label, created_at
                        FROM project_images WHERE project_id = :projectId ORDER BY created_at
                        """)
                .param("projectId", projectId)
                .query(ProjectRepository::mapImage)
                .list();
    }

    Optional<ImageResponse> findImage(UUID projectId, UUID imageId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, original_filename, content_type, size_bytes, label, created_at
                        FROM project_images WHERE project_id = :projectId AND id = :imageId
                        """)
                .param("projectId", projectId)
                .param("imageId", imageId)
                .query(ProjectRepository::mapImage)
                .optional();
    }

    Optional<StoredImage> findStoredImage(UUID projectId, UUID imageId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, original_filename, stored_filename, content_type
                        FROM project_images WHERE project_id = :projectId AND id = :imageId
                        """)
                .param("projectId", projectId)
                .param("imageId", imageId)
                .query((resultSet, rowNumber) -> new StoredImage(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("project_id", UUID.class),
                        resultSet.getString("original_filename"),
                        resultSet.getString("stored_filename"),
                        resultSet.getString("content_type")))
                .optional();
    }

    int deleteImage(UUID projectId, UUID imageId) {
        return jdbcClient.sql("DELETE FROM project_images WHERE project_id = :projectId AND id = :imageId")
                .param("projectId", projectId)
                .param("imageId", imageId)
                .update();
    }

    int countImages(UUID projectId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM project_images WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(Integer.class)
                .single();
    }

    int countActiveJobs(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM reconstruction_jobs
                        WHERE project_id = :projectId AND status IN ('QUEUED', 'RUNNING')
                        """)
                .param("projectId", projectId)
                .query(Integer.class)
                .single();
    }

    JobResponse insertJob(UUID id, UUID projectId, String algorithm, int imageCount) {
        return jdbcClient.sql("""
                        INSERT INTO reconstruction_jobs (id, project_id, algorithm, status, image_count, message)
                        VALUES (:id, :projectId, :algorithm, 'QUEUED', :imageCount, '等待重建工作进程接收')
                        RETURNING id, project_id, algorithm, status, image_count, message, result_path, created_at, updated_at
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("algorithm", algorithm)
                .param("imageCount", imageCount)
                .query(ProjectRepository::mapJob)
                .single();
    }

    List<JobResponse> findJobs(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, algorithm, status, image_count, message, result_path, created_at, updated_at
                        FROM reconstruction_jobs WHERE project_id = :projectId ORDER BY created_at DESC
                        """)
                .param("projectId", projectId)
                .query(ProjectRepository::mapJob)
                .list();
    }

    Optional<JobResponse> findJob(UUID projectId, UUID jobId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, algorithm, status, image_count, message, result_path, created_at, updated_at
                        FROM reconstruction_jobs WHERE project_id = :projectId AND id = :jobId
                        """)
                .param("projectId", projectId)
                .param("jobId", jobId)
                .query(ProjectRepository::mapJob)
                .optional();
    }

    Optional<JobResponse> findJob(UUID jobId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, algorithm, status, image_count, message, result_path, created_at, updated_at
                        FROM reconstruction_jobs WHERE id = :jobId
                        """)
                .param("jobId", jobId)
                .query(ProjectRepository::mapJob)
                .optional();
    }

    JobResponse updateJob(UUID jobId, String status, String message, String resultPath) {
        return jdbcClient.sql("""
                        UPDATE reconstruction_jobs
                        SET status = :status,
                            message = :message,
                            result_path = COALESCE(:resultPath, result_path),
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :jobId
                        RETURNING id, project_id, algorithm, status, image_count, message, result_path, created_at, updated_at
                        """)
                .param("jobId", jobId)
                .param("status", status)
                .param("message", message, Types.VARCHAR)
                .param("resultPath", resultPath, Types.VARCHAR)
                .query(ProjectRepository::mapJob)
                .optional()
                .orElseThrow(() -> new cn.edu.uestc.projectdesign.demo.api.common.NotFoundException(
                        "重建任务不存在: " + jobId));
    }

    ArtifactResponse insertArtifactForJob(UUID artifactId, UUID jobId, String name, String format,
            long pointCount, long faceCount, long sizeBytes, String resultPath) {
        return jdbcClient.sql("""
                        WITH selected_job AS (
                            SELECT project_id FROM reconstruction_jobs WHERE id = :jobId
                        ), next_version AS (
                            SELECT COALESCE(MAX(version_number), 0) + 1 AS value
                            FROM reconstruction_artifacts
                            WHERE project_id = (SELECT project_id FROM selected_job)
                        )
                        INSERT INTO reconstruction_artifacts
                            (id, project_id, job_id, name, version_number, format, point_count,
                             face_count, size_bytes, result_path, is_current)
                        SELECT :artifactId, project_id, :jobId, :name, value, :format, :pointCount,
                               :faceCount, :sizeBytes, :resultPath, TRUE
                        FROM selected_job CROSS JOIN next_version
                        ON CONFLICT (job_id) DO UPDATE SET
                            point_count = EXCLUDED.point_count, face_count = EXCLUDED.face_count,
                            size_bytes = EXCLUDED.size_bytes, result_path = EXCLUDED.result_path,
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id, project_id, job_id, name, version_number, format, point_count,
                                  face_count, size_bytes, result_path, is_current, created_at, updated_at
                        """)
                .param("artifactId", artifactId).param("jobId", jobId).param("name", name)
                .param("format", format).param("pointCount", pointCount).param("faceCount", faceCount)
                .param("sizeBytes", sizeBytes).param("resultPath", resultPath)
                .query(ProjectRepository::mapArtifact).single();
    }

    void clearCurrentArtifacts(UUID projectId) {
        jdbcClient.sql("""
                        UPDATE reconstruction_artifacts SET is_current = FALSE, updated_at = CURRENT_TIMESTAMP
                        WHERE project_id = :projectId AND is_current
                        """)
                .param("projectId", projectId).update();
    }

    List<ArtifactResponse> findArtifacts(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, job_id, name, version_number, format, point_count,
                               face_count, size_bytes, result_path, is_current, created_at, updated_at
                        FROM reconstruction_artifacts WHERE project_id = :projectId
                        ORDER BY version_number DESC
                        """)
                .param("projectId", projectId).query(ProjectRepository::mapArtifact).list();
    }

    Optional<ArtifactResponse> findArtifact(UUID projectId, UUID artifactId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, job_id, name, version_number, format, point_count,
                               face_count, size_bytes, result_path, is_current, created_at, updated_at
                        FROM reconstruction_artifacts WHERE project_id = :projectId AND id = :artifactId
                        """)
                .param("projectId", projectId).param("artifactId", artifactId)
                .query(ProjectRepository::mapArtifact).optional();
    }

    ArtifactResponse renameArtifact(UUID projectId, UUID artifactId, String name) {
        return jdbcClient.sql("""
                        UPDATE reconstruction_artifacts SET name = :name, updated_at = CURRENT_TIMESTAMP
                        WHERE project_id = :projectId AND id = :artifactId
                        RETURNING id, project_id, job_id, name, version_number, format, point_count,
                                  face_count, size_bytes, result_path, is_current, created_at, updated_at
                        """)
                .param("projectId", projectId).param("artifactId", artifactId).param("name", name)
                .query(ProjectRepository::mapArtifact).optional()
                .orElseThrow(() -> new cn.edu.uestc.projectdesign.demo.api.common.NotFoundException(
                        "重建成果不存在: " + artifactId));
    }

    ArtifactResponse selectArtifact(UUID projectId, UUID artifactId) {
        jdbcClient.sql("UPDATE reconstruction_artifacts SET is_current = FALSE WHERE project_id = :projectId")
                .param("projectId", projectId).update();
        return jdbcClient.sql("""
                        UPDATE reconstruction_artifacts SET is_current = TRUE, updated_at = CURRENT_TIMESTAMP
                        WHERE project_id = :projectId AND id = :artifactId
                        RETURNING id, project_id, job_id, name, version_number, format, point_count,
                                  face_count, size_bytes, result_path, is_current, created_at, updated_at
                        """)
                .param("projectId", projectId).param("artifactId", artifactId)
                .query(ProjectRepository::mapArtifact).optional()
                .orElseThrow(() -> new cn.edu.uestc.projectdesign.demo.api.common.NotFoundException(
                        "重建成果不存在: " + artifactId));
    }

    int deleteArtifact(UUID projectId, UUID artifactId) {
        return jdbcClient.sql("DELETE FROM reconstruction_artifacts WHERE project_id = :projectId AND id = :artifactId")
                .param("projectId", projectId).param("artifactId", artifactId).update();
    }

    int deleteCompletedJob(UUID projectId, UUID jobId) {
        return jdbcClient.sql("""
                        DELETE FROM reconstruction_jobs
                        WHERE project_id = :projectId AND id = :jobId AND status = 'SUCCEEDED'
                        """)
                .param("projectId", projectId).param("jobId", jobId).update();
    }

    void selectLatestArtifactIfNone(UUID projectId) {
        jdbcClient.sql("""
                        UPDATE reconstruction_artifacts SET is_current = TRUE, updated_at = CURRENT_TIMESTAMP
                        WHERE id = (
                            SELECT id FROM reconstruction_artifacts
                            WHERE project_id = :projectId
                            ORDER BY version_number DESC LIMIT 1
                        ) AND NOT EXISTS (
                            SELECT 1 FROM reconstruction_artifacts
                            WHERE project_id = :projectId AND is_current
                        )
                        """)
                .param("projectId", projectId).update();
    }

    ArtifactResponse updateArtifactMetrics(UUID artifactId, long pointCount, long faceCount, long sizeBytes) {
        return jdbcClient.sql("""
                        UPDATE reconstruction_artifacts SET point_count = :pointCount,
                            face_count = :faceCount, size_bytes = :sizeBytes,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :artifactId
                        RETURNING id, project_id, job_id, name, version_number, format, point_count,
                                  face_count, size_bytes, result_path, is_current, created_at, updated_at
                        """)
                .param("artifactId", artifactId).param("pointCount", pointCount)
                .param("faceCount", faceCount).param("sizeBytes", sizeBytes)
                .query(ProjectRepository::mapArtifact).single();
    }

    private static ProjectResponse mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("heritage_type"),
                resultSet.getString("collection_code"),
                resultSet.getString("era"),
                resultSet.getString("material"),
                resultSet.getString("dimensions"),
                resultSet.getString("provenance"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private static ImageResponse mapImage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ImageResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("original_filename"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("label"),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private static JobResponse mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new JobResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getString("algorithm"),
                resultSet.getString("status"),
                resultSet.getInt("image_count"),
                resultSet.getString("message"),
                resultSet.getString("result_path"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private static ArtifactResponse mapArtifact(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ArtifactResponse(
                resultSet.getObject("id", UUID.class), resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("job_id", UUID.class), resultSet.getString("name"),
                resultSet.getInt("version_number"), resultSet.getString("format"),
                resultSet.getLong("point_count"), resultSet.getLong("face_count"),
                resultSet.getLong("size_bytes"), resultSet.getString("result_path"),
                resultSet.getBoolean("is_current"), resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    record StoredImage(
            UUID id,
            UUID projectId,
            String originalFilename,
            String storedFilename,
            String contentType) {
    }
}
