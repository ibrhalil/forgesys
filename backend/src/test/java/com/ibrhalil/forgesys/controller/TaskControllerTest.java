package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.Task;
import com.ibrhalil.forgesys.entity.TaskPriority;
import com.ibrhalil.forgesys.entity.TaskStatus;
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
 * Task CRUD scoped to a project (Faz 3 Stage 2). Covers 401/403, happy-path board fetch,
 * create (defaults + unknown-project/assignee 404), status change, project-scoped
 * isolation (a task of project B is unreachable through project A) and delete.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        Project project = seedProject();
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/tasks").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsTasksForTheProject() throws Exception {
        Project project = seedProject();
        seedTask(project.getId(), "Design API", TaskStatus.TODO, TaskPriority.HIGH);
        seedTask(project.getId(), "Write docs", TaskStatus.DONE, TaskPriority.LOW);

        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/tasks")
                        .cookie(auth("reader@tenant.test", "pm:task:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.title=='Design API')].status").value("TODO"))
                .andExpect(jsonPath("$.data[?(@.title=='Write docs')].status").value("DONE"));
    }

    @Test
    void createReturns201WithDefaults() throws Exception {
        Project project = seedProject();

        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:task:write"))
                        .content("""
                                {"title":"New task"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New task"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.projectId").value(project.getId().toString()));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        Project project = seedProject();
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "pm:task:read"))
                        .content("""
                                {"title":"X"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createInUnknownProjectReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/tasks")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:task:write"))
                        .content("""
                                {"title":"X"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void createWithUnknownAssigneeReturns404() throws Exception {
        Project project = seedProject();
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:task:write"))
                        .content("""
                                {"title":"X","assigneeId":"%s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void updateMovesTaskStatus() throws Exception {
        Project project = seedProject();
        Task task = seedTask(project.getId(), "Draft", TaskStatus.TODO, TaskPriority.MEDIUM);

        mockMvc.perform(put("/api/v1/projects/" + project.getId() + "/tasks/" + task.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:task:write"))
                        .content("""
                                {"title":"Draft","status":"DONE","priority":"HIGH"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void getUnknownTaskInProjectReturns404() throws Exception {
        Project project = seedProject();
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/tasks/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "pm:task:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void taskIsScopedToItsProject() throws Exception {
        Project a = seedProject();
        Project b = seedProject();
        Task taskInB = seedTask(b.getId(), "Belongs to B", TaskStatus.TODO, TaskPriority.LOW);

        // The task exists, but addressing it through project A is a 404 (no cross-project leak).
        mockMvc.perform(get("/api/v1/projects/" + a.getId() + "/tasks/" + taskInB.getId())
                        .cookie(auth("reader@tenant.test", "pm:task:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void deleteReturns204() throws Exception {
        Project project = seedProject();
        Task task = seedTask(project.getId(), "Tmp", TaskStatus.TODO, TaskPriority.LOW);

        mockMvc.perform(delete("/api/v1/projects/" + project.getId() + "/tasks/" + task.getId())
                        .cookie(auth("deleter@tenant.test", "pm:task:delete")))
                .andExpect(status().isNoContent());
    }

    /* ── K-49: engine-wired list — q, structured filters, dueDate (DATE) ── */

    @Test
    void listWithQMatchesTitleAndDescription() throws Exception {
        Project project = seedProject();
        seedTask(project.getId(), "Refactor auth", TaskStatus.TODO, TaskPriority.HIGH);
        Task docs = seedTask(project.getId(), "Write docs", TaskStatus.DONE, TaskPriority.LOW);
        docs.setDescription("explain the refactor steps");
        entityManager.merge(docs);
        seedTask(project.getId(), "Unrelated", TaskStatus.TODO, TaskPriority.LOW);
        entityManager.flush();

        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/tasks")
                        .param("q", "refactor")
                        .cookie(auth("reader@tenant.test", "pm:task:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void searchFiltersByStatusAndDueDate() throws Exception {
        Project project = seedProject();
        Task overdue = seedTask(project.getId(), "Overdue", TaskStatus.TODO, TaskPriority.HIGH);
        overdue.setDueDate(java.time.LocalDate.parse("2026-01-10"));
        Task scheduled = seedTask(project.getId(), "Scheduled", TaskStatus.TODO, TaskPriority.LOW);
        scheduled.setDueDate(java.time.LocalDate.parse("2026-09-01"));
        Task doneEarly = seedTask(project.getId(), "Done early", TaskStatus.DONE, TaskPriority.LOW);
        doneEarly.setDueDate(java.time.LocalDate.parse("2026-01-05"));
        entityManager.flush();

        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "pm:task:read"))
                        .content("""
                                {"filters":[
                                    {"field":"status","operator":"EQ","values":["TODO"]},
                                    {"field":"dueDate","operator":"GTE","values":["2026-08-01"]}
                                 ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Scheduled"));

        // unparseable date -> 400, not 500
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "pm:task:read"))
                        .content("""
                                {"filters":[{"field":"dueDate","operator":"GTE","values":["tomorrow"]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    private Project seedProject() {
        Project project = new Project();
        project.setName("proj-" + UUID.randomUUID());
        project.setType(ProjectType.TASKS);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private Task seedTask(UUID projectId, String title, TaskStatus status, TaskPriority priority) {
        Task task = new Task();
        task.setProjectId(projectId);
        task.setTitle(title);
        task.setStatus(status);
        task.setPriority(priority);
        entityManager.persist(task);
        entityManager.flush();
        return task;
    }
}
