package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Project CRUD (Faz 3 Stage 1) — the first product feature module. Mirrors the RBAC
 * controller test pattern: 401 unauthenticated, 403 without {@code pm:project:*},
 * happy-path CRUD, duplicate-name 400, unknown-id 404.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void deleteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/projects").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsProjects() throws Exception {
        seedProject("Alpha", ProjectType.TASKS);
        seedProject("Beta", ProjectType.NOTES);

        mockMvc.perform(get("/api/v1/projects").cookie(auth("reader@tenant.test", "pm:project:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name=='Alpha')].type").value("TASKS"))
                .andExpect(jsonPath("$.content[?(@.name=='Beta')].type").value("NOTES"));
    }

    @Test
    void createReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Sprint Board","description":"Team tasks","type":"TASKS"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprint Board"))
                .andExpect(jsonPath("$.description").value("Team tasks"))
                .andExpect(jsonPath("$.type").value("TASKS"));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "pm:project:read"))
                        .content("""
                                {"name":"X","type":"TASKS"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createDuplicateNameReturns400() throws Exception {
        seedProject("Duplicate", ProjectType.TASKS);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Duplicate","type":"TASKS"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("project_name_taken"));
    }

    @Test
    void createWithInvalidTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"X","type":"BOGUS"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void updateChangesFields() throws Exception {
        Project project = seedProject("Old", ProjectType.NOTES);

        mockMvc.perform(put("/api/v1/projects/" + project.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Renamed","description":"now tasks","type":"TASKS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.type").value("TASKS"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "pm:project:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void deleteReturns204() throws Exception {
        Project project = seedProject("Tmp", ProjectType.TASKS);

        mockMvc.perform(delete("/api/v1/projects/" + project.getId())
                        .cookie(auth("deleter@tenant.test", "pm:project:delete")))
                .andExpect(status().isNoContent());
    }

    private Project seedProject(String name, ProjectType type) {
        Project project = new Project();
        project.setName(name);
        project.setType(type);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }
}
