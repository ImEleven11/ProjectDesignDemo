package cn.edu.uestc.projectdesign.demo.api.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cn.edu.uestc.projectdesign.demo.api.common.NotFoundException;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.CreateJobRequest;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.CreateProjectRequest;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ImageResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.JobResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ProjectResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.UpdateProjectRequest;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ArtifactResponse;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.PublicHeritageResponse;

@Service
public class ProjectService {
    private static final Set<String> ALGORITHMS = Set.of(
            "COLMAP_SPARSE", "COLMAP_MESH", "COLMAP_DENSE", "DEMO_SPARSE", "DEMO_MESH");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".tif", ".tiff", ".bmp", ".webp");

    private final ProjectRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Path uploadRoot;
    private final Path reconstructionRoot;

    public ProjectService(ProjectRepository repository, ApplicationEventPublisher eventPublisher,
            @Value("${app.upload-root}") String uploadRoot,
            @Value("${app.reconstruction-root}") String reconstructionRoot) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
        this.reconstructionRoot = Path.of(reconstructionRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        return repository.insertProject(
                UUID.randomUUID(),
                request.name().trim(),
                trimToNull(request.description()),
                request.heritageType().trim(),
                trimToNull(request.collectionCode()), trimToNull(request.era()),
                trimToNull(request.material()), trimToNull(request.dimensions()),
                trimToNull(request.provenance()));
    }

    public List<ProjectResponse> listProjects() {
        return repository.findProjects();
    }

    public ProjectResponse getProject(UUID projectId) {
        return repository.findProject(projectId)
                .orElseThrow(() -> new NotFoundException("文博项目不存在: " + projectId));
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request) {
        getProject(projectId);
        return repository.updateProject(projectId, request.name().trim(), trimToNull(request.description()),
                request.heritageType().trim(), trimToNull(request.collectionCode()), trimToNull(request.era()),
                trimToNull(request.material()), trimToNull(request.dimensions()), trimToNull(request.provenance()));
    }

    @Transactional
    public void deleteProject(UUID projectId) {
        getProject(projectId);
        if (repository.countActiveJobs(projectId) > 0) {
            throw new IllegalArgumentException("存在运行中的重建任务，暂不能删除项目");
        }
        List<JobResponse> jobs = repository.findJobs(projectId);
        if (repository.deleteProject(projectId) != 1) {
            throw new NotFoundException("文博项目不存在: " + projectId);
        }
        deleteTree(uploadRoot.resolve(projectId.toString()), uploadRoot);
        for (JobResponse job : jobs) {
            deleteTree(reconstructionRoot.resolve("jobs").resolve(job.id().toString()), reconstructionRoot);
        }
    }

    @Transactional
    public ImageResponse uploadImage(UUID projectId, MultipartFile file, String label) {
        getProject(projectId);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、TIFF、BMP 或 WebP 图像");
        }
        String storedFilename = UUID.randomUUID() + extension;
        Path projectDirectory = uploadRoot.resolve(projectId.toString()).normalize();
        Path target = projectDirectory.resolve(storedFilename).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        try {
            Files.createDirectories(projectDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("保存上传文件失败", exception);
        }
        return repository.insertImage(
                UUID.randomUUID(), projectId, originalFilename, storedFilename,
                file.getContentType(), file.getSize(), trimToNull(label));
    }

    public List<ImageResponse> listImages(UUID projectId) {
        getProject(projectId);
        return repository.findImages(projectId);
    }

    public ImageResponse getImage(UUID projectId, UUID imageId) {
        getProject(projectId);
        return repository.findImage(projectId, imageId)
                .orElseThrow(() -> new NotFoundException("项目图像不存在: " + imageId));
    }

    public ImageContent getImageContent(UUID projectId, UUID imageId) {
        getProject(projectId);
        ProjectRepository.StoredImage image = repository.findStoredImage(projectId, imageId)
                .orElseThrow(() -> new NotFoundException("项目图像不存在: " + imageId));
        Path content = resolveUpload(projectId, image.storedFilename());
        if (!Files.isRegularFile(content)) {
            throw new NotFoundException("图像文件不存在: " + imageId);
        }
        String contentType = image.contentType();
        if (contentType == null || MediaTypeNames.OCTET_STREAM.equals(contentType)) {
            try {
                contentType = Files.probeContentType(content);
            } catch (IOException ignored) {
                // Fall through to the extension-based mapping below.
            }
            contentType = contentType == null ? imageContentType(image.originalFilename()) : contentType;
        }
        return new ImageContent(content, image.originalFilename(), contentType);
    }

    @Transactional
    public void deleteImage(UUID projectId, UUID imageId) {
        getProject(projectId);
        if (repository.countActiveJobs(projectId) > 0) {
            throw new IllegalArgumentException("存在运行中的重建任务，暂不能删除图像");
        }
        ProjectRepository.StoredImage image = repository.findStoredImage(projectId, imageId)
                .orElseThrow(() -> new NotFoundException("项目图像不存在: " + imageId));
        if (repository.deleteImage(projectId, imageId) != 1) {
            throw new NotFoundException("项目图像不存在: " + imageId);
        }
        try {
            Files.deleteIfExists(resolveUpload(projectId, image.storedFilename()));
        } catch (IOException exception) {
            throw new UncheckedIOException("删除图像文件失败", exception);
        }
    }

    @Transactional
    public JobResponse createJob(UUID projectId, CreateJobRequest request) {
        getProject(projectId);
        String algorithm = request.algorithm().trim().toUpperCase(Locale.ROOT);
        if (!ALGORITHMS.contains(algorithm)) {
            throw new IllegalArgumentException(
                    "仅支持 COLMAP_SPARSE、COLMAP_MESH、COLMAP_DENSE、DEMO_SPARSE 或 DEMO_MESH 算法");
        }
        int imageCount = repository.countImages(projectId);
        int minimumImages = algorithm.startsWith("COLMAP_") ? 3 : 2;
        if (imageCount < minimumImages) {
            throw new IllegalArgumentException("使用 " + algorithm + " 至少需要 " + minimumImages + " 张图像");
        }
        JobResponse job = repository.insertJob(UUID.randomUUID(), projectId, algorithm, imageCount);
        eventPublisher.publishEvent(new ReconstructionDispatcher.JobCreated(
                job.id(), projectId, algorithm, imageCount));
        return job;
    }

    public List<JobResponse> listJobs(UUID projectId) {
        getProject(projectId);
        return repository.findJobs(projectId);
    }

    public JobResponse getJob(UUID projectId, UUID jobId) {
        getProject(projectId);
        return repository.findJob(projectId, jobId)
                .orElseThrow(() -> new NotFoundException("重建任务不存在: " + jobId));
    }

    @Transactional
    public JobResponse retryJob(UUID projectId, UUID jobId) {
        JobResponse failedJob = getJob(projectId, jobId);
        if (!"FAILED".equals(failedJob.status())) {
            throw new IllegalArgumentException("仅失败任务可以重试");
        }
        return createJob(projectId, new CreateJobRequest(failedJob.algorithm()));
    }

    public ReconstructionArtifact getJobResult(UUID projectId, UUID jobId) {
        JobResponse job = getJob(projectId, jobId);
        if (!"SUCCEEDED".equals(job.status()) || job.resultPath() == null) {
            throw new IllegalArgumentException("任务尚未生成可下载的重建结果");
        }
        Path result = resolveReconstructionArtifact(job.resultPath(), jobId);
        return artifact(result);
    }

    public ReconstructionArtifact getJobPointCloud(UUID projectId, UUID jobId) {
        JobResponse job = getJob(projectId, jobId);
        if (!"SUCCEEDED".equals(job.status())) {
            throw new IllegalArgumentException("任务尚未生成可查看的稀疏点云");
        }
        Path result = resolveReconstructionArtifact(
                Path.of("jobs", jobId.toString(), "sparse.ply").toString(), jobId);
        return artifact(result);
    }

    public String getJobLog(UUID projectId, UUID jobId) {
        JobResponse job = getJob(projectId, jobId);
        if (!job.algorithm().startsWith("COLMAP_")) {
            throw new IllegalArgumentException("该任务没有 COLMAP 日志");
        }
        Path log = reconstructionRoot.resolve("jobs").resolve(jobId.toString()).resolve("colmap.log").normalize();
        if (!log.startsWith(reconstructionRoot) || !Files.isRegularFile(log)) {
            throw new NotFoundException("任务日志尚未生成: " + jobId);
        }
        try {
            String content = Files.readString(log);
            int limit = 30_000;
            return content.length() <= limit ? content : content.substring(content.length() - limit);
        } catch (IOException exception) {
            throw new UncheckedIOException("读取任务日志失败", exception);
        }
    }

    @Transactional
    public List<ArtifactResponse> listArtifacts(UUID projectId) {
        getProject(projectId);
        return repository.findArtifacts(projectId).stream().map(this::hydrateArtifactMetrics).toList();
    }

    @Transactional
    public ProjectResponse publishProject(UUID projectId) {
        getProject(projectId);
        if (repository.findArtifacts(projectId).stream().noneMatch(ArtifactResponse::current)) {
            throw new IllegalArgumentException("发布前必须选择一个当前展示成果");
        }
        return repository.updatePublication(projectId, "PUBLISHED");
    }

    @Transactional
    public ProjectResponse unpublishProject(UUID projectId) {
        ProjectResponse project = getProject(projectId);
        String nextStatus = "DRAFT".equals(project.publicationStatus()) ? "DRAFT" : "UNPUBLISHED";
        return repository.updatePublication(projectId, nextStatus);
    }

    public List<PublicHeritageResponse> listPublicHeritage() {
        return repository.findPublishedProjects();
    }

    public PublicHeritageResponse getPublicHeritage(UUID projectId) {
        return repository.findPublishedProject(projectId)
                .orElseThrow(() -> new NotFoundException("公开文物不存在或已下架: " + projectId));
    }

    public ReconstructionArtifact getPublicModel(UUID projectId) {
        PublicHeritageResponse heritage = getPublicHeritage(projectId);
        return getJobResult(projectId, heritage.jobId());
    }

    public ImageContent getPublicCover(UUID projectId) {
        getPublicHeritage(projectId);
        ProjectRepository.StoredImage image = repository.findFirstStoredImage(projectId)
                .orElseThrow(() -> new NotFoundException("公开文物没有代表图片: " + projectId));
        Path content = resolveUpload(projectId, image.storedFilename());
        if (!Files.isRegularFile(content)) {
            throw new NotFoundException("代表图片文件不存在: " + projectId);
        }
        String contentType = image.contentType();
        if (contentType == null || MediaTypeNames.OCTET_STREAM.equals(contentType)) {
            contentType = imageContentType(image.originalFilename());
        }
        return new ImageContent(content, image.originalFilename(), contentType);
    }

    private ArtifactResponse hydrateArtifactMetrics(ArtifactResponse artifact) {
        if (artifact.sizeBytes() > 0 && (artifact.pointCount() > 0 || artifact.faceCount() > 0)) {
            return artifact;
        }
        try {
            Path result = resolveReconstructionArtifact(artifact.resultPath(), artifact.jobId());
            long pointCount = artifact.pointCount();
            long faceCount = artifact.faceCount();
            Path metadataPath = result.getParent().resolve("result.json");
            if (Files.isRegularFile(metadataPath)) {
                String metadata = Files.readString(metadataPath);
                pointCount = jsonNumber(metadata, "pointCount", pointCount);
                faceCount = jsonNumber(metadata, "faceCount", faceCount);
            }
            return repository.updateArtifactMetrics(artifact.id(), pointCount, faceCount, Files.size(result));
        } catch (IOException | RuntimeException ignored) {
            return artifact;
        }
    }

    private static long jsonNumber(String json, String field, long fallback) {
        var matcher = java.util.regex.Pattern.compile(
                "\\\"" + java.util.regex.Pattern.quote(field) + "\\\"\\s*:\\s*(\\d+)")
                .matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : fallback;
    }

    @Transactional
    public ArtifactResponse renameArtifact(UUID projectId, UUID artifactId, String name) {
        getProject(projectId);
        return repository.renameArtifact(projectId, artifactId, name.trim());
    }

    @Transactional
    public ArtifactResponse selectArtifact(UUID projectId, UUID artifactId) {
        getProject(projectId);
        repository.findArtifact(projectId, artifactId)
                .orElseThrow(() -> new NotFoundException("重建成果不存在: " + artifactId));
        return repository.selectArtifact(projectId, artifactId);
    }

    @Transactional
    public void deleteArtifact(UUID projectId, UUID artifactId) {
        getProject(projectId);
        ArtifactResponse artifact = repository.findArtifact(projectId, artifactId)
                .orElseThrow(() -> new NotFoundException("重建成果不存在: " + artifactId));
        if (repository.deleteArtifact(projectId, artifactId) != 1) {
            throw new NotFoundException("重建成果不存在: " + artifactId);
        }
        repository.deleteCompletedJob(projectId, artifact.jobId());
        repository.selectLatestArtifactIfNone(projectId);
        deleteTree(reconstructionRoot.resolve("jobs").resolve(artifact.jobId().toString()), reconstructionRoot);
    }

    private static void deleteTree(Path target, Path allowedRoot) {
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(allowedRoot) || normalized.equals(allowedRoot) || !Files.exists(normalized)) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("删除项目文件失败", exception);
        }
    }

    private Path resolveUpload(UUID projectId, String storedFilename) {
        Path content = uploadRoot.resolve(projectId.toString()).resolve(storedFilename).normalize();
        if (!content.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return content;
    }

    private Path resolveReconstructionArtifact(String relativePath, UUID jobId) {
        Path result = reconstructionRoot.resolve(relativePath).normalize();
        Path jobDirectory = reconstructionRoot.resolve("jobs").resolve(jobId.toString()).normalize();
        if (!result.startsWith(jobDirectory) || !Files.isRegularFile(result)) {
            throw new NotFoundException("重建结果文件不存在: " + jobId);
        }
        return result;
    }

    private static ReconstructionArtifact artifact(Path path) {
        String filename = path.getFileName().toString();
        String contentType = filename.toLowerCase(Locale.ROOT).endsWith(".glb")
                ? "model/gltf-binary"
                : MediaTypeNames.OCTET_STREAM;
        return new ReconstructionArtifact(path, filename, contentType);
    }

    private static String safeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return Path.of(filename).getFileName().toString();
    }

    private static String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(index).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }

    private static String imageContentType(String filename) {
        return switch (extensionOf(filename)) {
            case ".bmp" -> "image/bmp";
            case ".gif" -> "image/gif";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            default -> MediaTypeNames.OCTET_STREAM;
        };
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ImageContent(Path path, String originalFilename, String contentType) {
    }

    public record ReconstructionArtifact(Path path, String filename, String contentType) {
    }

    private static final class MediaTypeNames {
        private static final String OCTET_STREAM = "application/octet-stream";

        private MediaTypeNames() {
        }
    }
}
