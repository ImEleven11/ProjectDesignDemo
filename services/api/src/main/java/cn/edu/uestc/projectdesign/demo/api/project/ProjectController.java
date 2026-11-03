package cn.edu.uestc.projectdesign.demo.api.project;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.CreateJobRequest;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.CreateProjectRequest;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ImageResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.JobResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ProjectResponse;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "http://localhost:5173")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = service.createProject(request);
        return ResponseEntity.created(URI.create("/api/projects/" + response.id())).body(response);
    }

    @GetMapping
    List<ProjectResponse> listProjects() {
        return service.listProjects();
    }

    @GetMapping("/{projectId}")
    ProjectResponse getProject(@PathVariable("projectId") UUID projectId) {
        return service.getProject(projectId);
    }

    @PostMapping(path = "/{projectId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImageResponse> uploadImage(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "label", required = false) String label) {
        ImageResponse response = service.uploadImage(projectId, file, label);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/images/" + response.id()))
                .body(response);
    }

    @GetMapping("/{projectId}/images")
    List<ImageResponse> listImages(@PathVariable("projectId") UUID projectId) {
        return service.listImages(projectId);
    }

    @GetMapping("/{projectId}/images/{imageId}")
    ImageResponse getImage(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("imageId") UUID imageId) {
        return service.getImage(projectId, imageId);
    }

    @GetMapping("/{projectId}/images/{imageId}/content")
    ResponseEntity<FileSystemResource> getImageContent(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("imageId") UUID imageId) {
        ProjectService.ImageContent content = service.getImageContent(projectId, imageId);
        MediaType contentType;
        try {
            contentType = content.contentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(content.contentType());
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String disposition = ContentDisposition.inline()
                .filename(content.originalFilename(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new FileSystemResource(content.path()));
    }

    @DeleteMapping("/{projectId}/images/{imageId}")
    ResponseEntity<Void> deleteImage(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("imageId") UUID imageId) {
        service.deleteImage(projectId, imageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/jobs")
    ResponseEntity<JobResponse> createJob(
            @PathVariable("projectId") UUID projectId,
            @RequestBody(required = false) CreateJobRequest request) {
        CreateJobRequest normalizedRequest = request == null ? new CreateJobRequest(null) : request;
        JobResponse response = service.createJob(projectId, normalizedRequest);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/jobs/" + response.id()))
                .body(response);
    }

    @GetMapping("/{projectId}/jobs")
    List<JobResponse> listJobs(@PathVariable("projectId") UUID projectId) {
        return service.listJobs(projectId);
    }

    @GetMapping("/{projectId}/jobs/{jobId}")
    JobResponse getJob(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("jobId") UUID jobId) {
        return service.getJob(projectId, jobId);
    }

    @PostMapping("/{projectId}/jobs/{jobId}/retry")
    ResponseEntity<JobResponse> retryJob(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("jobId") UUID jobId) {
        JobResponse response = service.retryJob(projectId, jobId);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/jobs/" + response.id()))
                .body(response);
    }

    @GetMapping(path = "/{projectId}/jobs/{jobId}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> getJobLog(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("jobId") UUID jobId) {
        return ResponseEntity.ok(service.getJobLog(projectId, jobId));
    }

    @GetMapping("/{projectId}/jobs/{jobId}/result")
    ResponseEntity<FileSystemResource> downloadJobResult(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("jobId") UUID jobId) {
        Path result = service.getJobResult(projectId, jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sparse.ply\"")
                .body(new FileSystemResource(result));
    }
}
