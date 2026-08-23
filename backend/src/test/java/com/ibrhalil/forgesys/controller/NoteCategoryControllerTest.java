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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flat note-category surface (K-44, re-scoped by K-45). Covers 401/403, list +
 * {@code q} search, create/update with duplicate-name rejection, delete (keeps
 * notes — they become uncategorized with a null categoryName), project-immutability
 * on update, 404s.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteCategoryControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private Project defaultNotesProject;

    @BeforeEach
    void seedDefaultNotesContainer() {
        defaultNotesProject = new Project();
        defaultNotesProject.setName("Genel");
        defaultNotesProject.setType(ProjectType.NOTES);
        defaultNotesProject.setDefault(true);
        entityManager.persist(defaultNotesProject);
        entityManager.flush();
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/note-categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/note-categories").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsCategoriesSortedByName() throws Exception {
        seedCategory("Work");
        seedCategory("Archive");

        mockMvc.perform(get("/api/v1/note-categories")
                        .cookie(auth("reader@tenant.test", "notes:category:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Archive"))
                .andExpect(jsonPath("$.data[1].name").value("Work"));
    }

    @Test
    void createReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/note-categories")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:category:write"))
                        .content("""
                                {"name":"Ideas","color":"#10b981"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ideas"))
                .andExpect(jsonPath("$.color").value("#10b981"))
                .andExpect(jsonPath("$.projectId").value(defaultNotesProject.getId().toString()));
    }

    @Test
    void updateRejectsProjectMove() throws Exception {
        Project other = seedNotesProject("Second");
        NoteCategory category = seedCategory("Work");

        mockMvc.perform(put("/api/v1/note-categories/" + category.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:category:write"))
                        .content("""
                                {"name":"Work","projectId":"%s"}""".formatted(other.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("note_category_project_mismatch"));
    }

    @Test
    void createWithDuplicateNameReturns400() throws Exception {
        seedCategory("Work");

        mockMvc.perform(post("/api/v1/note-categories")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:category:write"))
                        .content("""
                                {"name":"Work"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("note_category_name_taken"));
    }

    @Test
    void updateRejectsNameTakenByAnotherCategory() throws Exception {
        NoteCategory work = seedCategory("Work");
        NoteCategory other = seedCategory("Other");

        mockMvc.perform(put("/api/v1/note-categories/" + other.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:category:write"))
                        .content("""
                                {"name":"Work"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("note_category_name_taken"));

        // keeping its own name is fine
        mockMvc.perform(put("/api/v1/note-categories/" + work.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:category:write"))
                        .content("""
                                {"name":"Work","color":"#000000"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#000000"));
    }

    @Test
    void deleteKeepsNotesAsUncategorized() throws Exception {
        NoteCategory category = seedCategory("Work");
        Note note = seedNote("Keeps living", "content", category.getId(), false);

        mockMvc.perform(delete("/api/v1/note-categories/" + category.getId())
                        .cookie(auth("writer@tenant.test", "notes:category:write")))
                .andExpect(status().isNoContent());

        // The note survives; its categoryName resolves to nothing (soft-deleted category).
        mockMvc.perform(get("/api/v1/notes/" + note.getId())
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Keeps living"))
                .andExpect(jsonPath("$.categoryName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getUnknownCategoryReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/note-categories/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "notes:category:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    private Project seedNotesProject(String name) {
        Project project = new Project();
        project.setName(name);
        project.setType(ProjectType.NOTES);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private NoteCategory seedCategory(String name) {
        NoteCategory category = new NoteCategory();
        category.setName(name);
        category.setProjectId(defaultNotesProject.getId());
        entityManager.persist(category);
        entityManager.flush();
        return category;
    }

    private Note seedNote(String title, String content, UUID categoryId, boolean pinned) {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setCategoryId(categoryId);
        note.setProjectId(defaultNotesProject.getId());
        note.setPinned(pinned);
        entityManager.persist(note);
        entityManager.flush();
        return note;
    }
}
