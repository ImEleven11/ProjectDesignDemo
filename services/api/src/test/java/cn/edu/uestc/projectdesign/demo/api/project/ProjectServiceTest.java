package cn.edu.uestc.projectdesign.demo.api.project;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationEventPublisher;

import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.CreateJobRequest;
import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ProjectResponse;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceTest {
    @TempDir
    Path uploadRoot;

    @Test
    void rejectsAJobWhenTheProjectHasFewerThanTwoImages() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectService service = service(repository);
        UUID projectId = UUID.randomUUID();
        when(repository.findProject(projectId)).thenReturn(java.util.Optional.of(project(projectId)));
        when(repository.countImages(projectId)).thenReturn(1);

        assertThatThrownBy(() -> service.createJob(projectId, new CreateJobRequest("COLMAP_SPARSE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少上传 2 张图像");
    }

    @Test
    void rejectsAnEmptyUpload() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectService service = service(repository);
        UUID projectId = UUID.randomUUID();
        when(repository.findProject(projectId)).thenReturn(java.util.Optional.of(project(projectId)));
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.uploadImage(projectId, file, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上传文件不能为空");
    }

    private static ProjectResponse project(UUID id) {
        return new ProjectResponse(id, "测试文物", null, "器物", OffsetDateTime.now());
    }

    private ProjectService service(ProjectRepository repository) {
        return new ProjectService(
                repository,
                mock(ApplicationEventPublisher.class),
                uploadRoot.toString(),
                uploadRoot.resolve("results").toString());
    }
}
