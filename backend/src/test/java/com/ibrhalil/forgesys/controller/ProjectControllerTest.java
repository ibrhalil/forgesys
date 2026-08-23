package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.Task;
import com.ibrhalil.forgesys.entity.TaskPriority;
import com.ibrhalil.forgesys.entity.TaskStatus;
import com.ibrhalil.forgesys.entity.TenantModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
                .andExpect(jsonPath("$.data[?(@.name=='Alpha')].type").value("TASKS"))
                .andExpect(jsonPath("$.data[?(@.name=='Beta')].type").value("NOTES"));
    }

    @Test
    void listWithQFiltersByName() throws Exception {
        seedProject("gamma_probe", ProjectType.TASKS);
        seedProject("delta_probe", ProjectType.NOTES);

        mockMvc.perform(get("/api/v1/projects").param("q", "GAMMA")
                        .cookie(auth("reader@tenant.test", "pm:project:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItem("gamma_probe")))
                .andExpect(jsonPath("$.data[*].name").value(not(hasItem("delta_probe"))));
    }

    @Test
    void listWithSortOutsideWhitelistReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/projects").param("sort", "notAField")
                        .cookie(auth("reader@tenant.test", "pm:project:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
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

    /* ── GET /api/v1/projects/types (K-45 type catalog) ── */

    @Test
    void typesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void typesForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/projects/types").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void typesWithoutTenantContextIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects/types").cookie(auth("reader@tenant.test", "pm:project:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("tenant_not_found"));
    }

    @Test
    void typesListsActiveModuleTypesWithDefaultProject() throws Exception {
        Company company = seedPublicCompany();
        seedModuleRow(company, "pm");
        seedModuleRow(company, "notes");

        Project defaultNotes = new Project();
        defaultNotes.setName("Genel");
        defaultNotes.setType(ProjectType.NOTES);
        defaultNotes.setDefault(true);
        entityManager.persist(defaultNotes);
        entityManager.flush();

        TenantContext.setCurrentTenant("public");
        try {
            mockMvc.perform(get("/api/v1/projects/types")
                            .cookie(authTenant("public", "reader@tenant.test", "pm:project:read")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[?(@.type=='TASKS')].moduleKey").value("pm"))
                    .andExpect(jsonPath("$[?(@.type=='NOTES')].moduleKey").value("notes"))
                    .andExpect(jsonPath("$[?(@.type=='NOTES')].defaultProjectId").value(defaultNotes.getId().toString()));
        } finally {
            TenantContext.clear();
        }
    }

    /* ── Parent (K-45 nesting) ── */

    @Test
    void listByParentProjectIdFilters() throws Exception {
        Project parent = seedProject("Parent", ProjectType.TASKS);
        seedChildProject("Child", parent);
        seedProject("Standalone", ProjectType.TASKS);

        mockMvc.perform(get("/api/v1/projects").param("parentProjectId", parent.getId().toString())
                        .cookie(auth("reader@tenant.test", "pm:project:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(hasItem("Child")))
                .andExpect(jsonPath("$.data[*].name").value(not(hasItem("Standalone"))));
    }

    @Test
    void createWithParentReturns201() throws Exception {
        Project parent = seedProject("Parent", ProjectType.TASKS);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Child","type":"TASKS","parentProjectId":"%s"}""".formatted(parent.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentProjectId").value(parent.getId().toString()))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void createWithUnknownParentReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Orphan","type":"TASKS","parentProjectId":"%s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void updateWithSelfParentReturns409() throws Exception {
        Project project = seedProject("Solo", ProjectType.TASKS);

        mockMvc.perform(put("/api/v1/projects/" + project.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Solo","type":"TASKS","parentProjectId":"%s"}""".formatted(project.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_cycle_forbidden"));
    }

    @Test
    void updateCreatingParentCycleReturns409() throws Exception {
        Project root = seedProject("Root", ProjectType.TASKS);
        Project child = seedChildProject("Child", root);

        mockMvc.perform(put("/api/v1/projects/" + root.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Root","type":"TASKS","parentProjectId":"%s"}""".formatted(child.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_cycle_forbidden"));
    }

    /* ── Type-change / default-container guards (K-45) ── */

    @Test
    void updateTypeChangeWithTasksReturns409() throws Exception {
        Project project = seedProject("Locked", ProjectType.TASKS);
        seedTask(project, "Do something");

        mockMvc.perform(put("/api/v1/projects/" + project.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Locked","type":"NOTES"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_change_forbidden"));
    }

    @Test
    void updateDefaultProjectTypeFrozenReturns409() throws Exception {
        Project def = seedDefaultProject(ProjectType.NOTES);

        mockMvc.perform(put("/api/v1/projects/" + def.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"%s","type":"TASKS"}""".formatted(def.getName())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_default_immutable"));
    }

    @Test
    void updateDefaultProjectParentFrozenReturns409() throws Exception {
        Project def = seedDefaultProject(ProjectType.NOTES);
        Project other = seedProject("Other", ProjectType.TASKS);

        mockMvc.perform(put("/api/v1/projects/" + def.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"%s","type":"NOTES","parentProjectId":"%s"}"""
                                .formatted(def.getName(), other.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_default_immutable"));
    }

    @Test
    void updateDefaultProjectRenameSucceeds() throws Exception {
        Project def = seedDefaultProject(ProjectType.NOTES);

        mockMvc.perform(put("/api/v1/projects/" + def.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"Genel 2","type":"NOTES"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Genel 2"))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    private Project seedProject(String name, ProjectType type) {
        Project project = new Project();
        project.setName(name);
        project.setType(type);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private Project seedChildProject(String name, Project parent) {
        Project project = new Project();
        project.setName(name);
        project.setType(parent.getType());
        project.setParentProjectId(parent.getId());
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private Project seedDefaultProject(ProjectType type) {
        Project project = new Project();
        project.setName("Genel-" + UUID.randomUUID());
        project.setType(type);
        project.setDefault(true);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private void seedTask(Project project, String title) {
        Task task = new Task();
        task.setProjectId(project.getId());
        task.setTitle(title);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(TaskPriority.MEDIUM);
        entityManager.persist(task);
        entityManager.flush();
    }

    private Company seedPublicCompany() {
        Company company = new Company();
        company.setName("Project Test " + UUID.randomUUID());
        company.setSubdomain("proj" + UUID.randomUUID().toString().substring(0, 8));
        company.setSchemaName("public");
        company.setStatus(CompanyStatus.ACTIVE);
        entityManager.persist(company);
        return company;
    }

    private void seedModuleRow(Company company, String moduleKey) {
        TenantModule row = new TenantModule();
        row.setCompany(company);
        row.setModuleKey(moduleKey);
        row.setStatus(ModuleStatus.ACTIVE);
        row.setActivatedAt(OffsetDateTime.now());
        entityManager.persist(row);
    }
}
