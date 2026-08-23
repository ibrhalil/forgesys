package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Note;
import com.ibrhalil.forgesys.entity.NoteCategory;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notes nested under their project container (K-45 step 4) — the TaskController
 * pattern: 404 unknown container, 409 {@code project_type_mismatch} for a non-NOTES
 * container, category/note project consistency, and the type-change lock now that a
 * NOTES project can hold notes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectNoteControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private Project notesProject;

    @BeforeEach
    void seedContainer() {
        notesProject = new Project();
        notesProject.setName("Notes Home " + UUID.randomUUID());
        notesProject.setType(ProjectType.NOTES);
        entityManager.persist(notesProject);
        entityManager.flush();
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + notesProject.getId() + "/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + notesProject.getId() + "/notes")
                        .cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listInUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/notes")
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void listInNonNotesProjectReturns409() throws Exception {
        Project tasks = seedProject(ProjectType.TASKS, "Board");

        mockMvc.perform(get("/api/v1/projects/" + tasks.getId() + "/notes")
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_mismatch"));
    }

    @Test
    void listScopesToItsContainer() throws Exception {
        Project other = seedProject(ProjectType.NOTES, "Other Notes");
        seedNote(notesProject.getId(), "Mine", null);
        seedNote(other.getId(), "Theirs", null);

        mockMvc.perform(get("/api/v1/projects/" + notesProject.getId() + "/notes")
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Mine"))
                .andExpect(jsonPath("$.data[0].projectName").value(notesProject.getName()));
    }

    @Test
    void createReturns201AndAnchorsToContainer() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + notesProject.getId() + "/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"Nested note","content":"body"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Nested note"))
                .andExpect(jsonPath("$.projectId").value(notesProject.getId().toString()));
    }

    @Test
    void createInNonNotesProjectReturns409() throws Exception {
        Project tasks = seedProject(ProjectType.TASKS, "Board");

        mockMvc.perform(post("/api/v1/projects/" + tasks.getId() + "/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"X"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_mismatch"));
    }

    @Test
    void createWithForeignCategoryReturns409() throws Exception {
        Project other = seedProject(ProjectType.NOTES, "Other Notes");
        NoteCategory foreign = seedCategory(other.getId(), "Foreign");

        mockMvc.perform(post("/api/v1/projects/" + notesProject.getId() + "/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"X","categoryId":"%s"}""".formatted(foreign.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("note_category_project_mismatch"));
    }

    @Test
    void createCategoryInProjectReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + notesProject.getId() + "/note-categories")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:category:write"))
                        .content("""
                                {"name":"Plans","color":"#22c55e"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Plans"))
                .andExpect(jsonPath("$.projectId").value(notesProject.getId().toString()));
    }

    @Test
    void flatCreateWithoutProjectDefaultsToDefaultContainer() throws Exception {
        Project def = new Project();
        def.setName("Genel");
        def.setType(ProjectType.NOTES);
        def.setDefault(true);
        entityManager.persist(def);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"Top nav note"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(def.getId().toString()))
                .andExpect(jsonPath("$.projectName").value("Genel"));
    }

    @Test
    void typeChangeWithNotesReturns409() throws Exception {
        seedNote(notesProject.getId(), "Kept note", null);

        mockMvc.perform(put("/api/v1/projects/" + notesProject.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "pm:project:write"))
                        .content("""
                                {"name":"%s","type":"TASKS"}""".formatted(notesProject.getName())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("project_type_change_forbidden"));
    }

    private Project seedProject(ProjectType type, String name) {
        Project project = new Project();
        project.setName(name + " " + UUID.randomUUID());
        project.setType(type);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private NoteCategory seedCategory(UUID projectId, String name) {
        NoteCategory category = new NoteCategory();
        category.setName(name);
        category.setProjectId(projectId);
        entityManager.persist(category);
        entityManager.flush();
        return category;
    }

    private Note seedNote(UUID projectId, String title, UUID categoryId) {
        Note note = new Note();
        note.setTitle(title);
        note.setContent("");
        note.setCategoryId(categoryId);
        note.setProjectId(projectId);
        note.setPinned(false);
        entityManager.persist(note);
        entityManager.flush();
        return note;
    }
}
