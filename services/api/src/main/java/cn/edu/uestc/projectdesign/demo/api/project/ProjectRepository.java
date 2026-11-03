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

@Repository
public class ProjectRepository {
    private final JdbcClient jdbcClient;

    public ProjectRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    ProjectResponse insertProject(UUID id, String name, String description, String heritageType) {
        return jdbcClient.sql("""
                        INSERT INTO heritage_projects (id, name, description, heritage_type)
                        VALUES (:id, :name, :description, :heritageType)
                        RETURNING id, name, description, heritage_type, created_at
                        """)
                .param("id", id)
                .param("name", name)
                .param("description", description, Types.VARCHAR)
                .param("heritageType", heritageType)
                .query(ProjectRepository::mapProject)
                .single();
    }

    List<ProjectResponse> findProjects() {
        return jdbcClient.sql("""
                        SELECT id, name, description, heritage_type, created_at
                        FROM heritage_projects ORDER BY created_at DESC
                        """)
                .query(ProjectRepository::mapProject)
                .list();
    }

    Optional<ProjectResponse> findProject(UUID id) {
        return jdbcClient.sql("""
                        SELECT id, name, description, heritage_type, created_at
                        FROM heritage_projects WHERE id = :id
                        """)
                .param("id", id)
                .query(ProjectRepository::mapProject)
                .optional();
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

    int countImages(UUID projectId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM project_images WHERE project_id = :projectId")
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

    private static ProjectResponse mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("heritage_type"),
                resultSet.getObject("created_at", OffsetDateTime.class));
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
}
