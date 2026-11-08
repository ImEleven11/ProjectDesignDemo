package cn.edu.uestc.projectdesign.demo.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static cn.edu.uestc.projectdesign.demo.api.project.ProjectDtos.ProjectResponse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerTest {

    @Test
    void bindsTheProjectIdPathVariable() throws Exception {
        ProjectService service = mock(ProjectService.class);
        UUID projectId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        when(service.getProject(projectId)).thenReturn(new ProjectResponse(
                projectId, "测试项目", null, "器物", "BW-001", "商代", "青铜",
                "高 20cm", "成都", "DRAFT", null, now, now));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(service)).build();

        mockMvc.perform(get("/api/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId.toString()));
    }
}
