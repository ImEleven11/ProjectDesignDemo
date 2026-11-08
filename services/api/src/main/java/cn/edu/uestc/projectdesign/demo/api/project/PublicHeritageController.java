package cn.edu.uestc.projectdesign.demo.api.project;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.PublicHeritageResponse;

@RestController
@RequestMapping("/api/public/heritage")
public class PublicHeritageController {
    private final ProjectService service;

    public PublicHeritageController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    List<PublicHeritageResponse> list() {
        return service.listPublicHeritage();
    }

    @GetMapping("/{projectId}")
    PublicHeritageResponse get(@PathVariable("projectId") UUID projectId) {
        return service.getPublicHeritage(projectId);
    }

    @GetMapping("/{projectId}/model")
    ResponseEntity<FileSystemResource> model(@PathVariable("projectId") UUID projectId) {
        ProjectService.ReconstructionArtifact artifact = service.getPublicModel(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(artifact.filename(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(artifact.path()));
    }

    @GetMapping("/{projectId}/cover")
    ResponseEntity<FileSystemResource> cover(@PathVariable("projectId") UUID projectId) {
        ProjectService.ImageContent image = service.getPublicCover(projectId);
        MediaType type;
        try {
            type = image.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(image.contentType());
        } catch (IllegalArgumentException ignored) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(image.path()));
    }
}
