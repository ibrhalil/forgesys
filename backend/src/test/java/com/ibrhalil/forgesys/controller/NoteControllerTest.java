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
 * Flat note surface (K-44, re-scoped by K-45). Covers 401/403, happy-path list with
 * {@code q} / {@code categoryId} / {@code pinned} / {@code projectId} filters, create
 * (markdown content + defaults — lands in the default container when no
 * {@code projectId} is given), unknown-category 404, update (partial semantics: null
 * pinned leaves unchanged), delete, and duplicate-title legitimacy.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteControllerTest extends AbstractRbacWebTest {

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
        mockMvc.perform(get("/api/v1/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void listForbiddenWithoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/notes").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void listReturnsNotesWithCategoryNames() throws Exception {
        NoteCategory category = seedCategory("Work", "#ff0000");
        seedNote("API design", "GET /api spec", category.getId(), false);
        seedNote("Groceries", "milk, eggs", null, true);

        mockMvc.perform(get("/api/v1/notes")
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.title=='API design')].categoryName").value("Work"))
                .andExpect(jsonPath("$.data[?(@.title=='Groceries')].pinned").value(true));
    }

    @Test
    void listSearchesByQAndFiltersByCategory() throws Exception {
        NoteCategory work = seedCategory("Work", null);
        seedNote("API design", "REST conventions", work.getId(), false);
        seedNote("Groceries", "REST? no, milk", null, false);

        mockMvc.perform(get("/api/v1/notes")
                        .param("q", "API")
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("API design"));

        mockMvc.perform(get("/api/v1/notes")
                        .param("categoryId", work.getId().toString())
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("API design"));
    }

    @Test
    void listFiltersByPinned() throws Exception {
        seedNote("Pinned one", "", null, true);
        seedNote("Regular", "", null, false);

        mockMvc.perform(get("/api/v1/notes")
                        .param("pinned", "true")
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Pinned one"));
    }

    @Test
    void listFiltersByProjectId() throws Exception {
        Project otherContainer = seedNotesProject("Second");
        seedNote("In Genel", "", null, false);
        seedNoteIn(otherContainer.getId(), "In Second", "", null, false);

        mockMvc.perform(get("/api/v1/notes")
                        .param("projectId", otherContainer.getId().toString())
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("In Second"))
                .andExpect(jsonPath("$.data[0].projectName").value("Second"));
    }

    @Test
    void createReturns201WithDefaults() throws Exception {
        NoteCategory category = seedCategory("Work", null);

        mockMvc.perform(post("/api/v1/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"New note","content":"# Heading","categoryId":"%s"}"""
                                .formatted(category.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New note"))
                .andExpect(jsonPath("$.content").value("# Heading"))
                .andExpect(jsonPath("$.categoryName").value("Work"))
                .andExpect(jsonPath("$.projectId").value(defaultNotesProject.getId().toString()))
                .andExpect(jsonPath("$.projectName").value("Genel"))
                .andExpect(jsonPath("$.pinned").value(false));
    }

    @Test
    void createForbiddenWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/notes")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "notes:note:read"))
                        .content("""
                                {"title":"X"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void createWithUnknownCategoryReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"X","categoryId":"%s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void createAllowsDuplicateTitles() throws Exception {
        seedNote("Same title", "", null, false);

        mockMvc.perform(post("/api/v1/notes")
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"Same title"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void updatePinsAndMovesCategory() throws Exception {
        NoteCategory category = seedCategory("Work", null);
        Note note = seedNote("Draft", "old", null, false);

        mockMvc.perform(put("/api/v1/notes/" + note.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"Draft","content":"new","categoryId":"%s","pinned":true}"""
                                .formatted(category.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("new"))
                .andExpect(jsonPath("$.categoryName").value("Work"))
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void updateWithNullPinnedLeavesItUnchanged() throws Exception {
        Note note = seedNote("Pinned", "", null, true);

        mockMvc.perform(put("/api/v1/notes/" + note.getId())
                        .contentType(JSON)
                        .cookie(auth("writer@tenant.test", "notes:note:write"))
                        .content("""
                                {"title":"Pinned"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void getUnknownNoteReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/notes/" + UUID.randomUUID())
                        .cookie(auth("reader@tenant.test", "notes:note:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource_not_found"));
    }

    @Test
    void deleteReturns204() throws Exception {
        Note note = seedNote("Tmp", "", null, false);

        mockMvc.perform(delete("/api/v1/notes/" + note.getId())
                        .cookie(auth("deleter@tenant.test", "notes:note:delete")))
                .andExpect(status().isNoContent());
    }

    private Project seedNotesProject(String name) {
        Project project = new Project();
        project.setName(name);
        project.setType(ProjectType.NOTES);
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private NoteCategory seedCategory(String name, String color) {
        NoteCategory category = new NoteCategory();
        category.setName(name);
        category.setColor(color);
        category.setProjectId(defaultNotesProject.getId());
        entityManager.persist(category);
        entityManager.flush();
        return category;
    }

    private Note seedNote(String title, String content, UUID categoryId, boolean pinned) {
        return seedNoteIn(defaultNotesProject.getId(), title, content, categoryId, pinned);
    }

    private Note seedNoteIn(UUID projectId, String title, String content, UUID categoryId, boolean pinned) {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setCategoryId(categoryId);
        note.setProjectId(projectId);
        note.setPinned(pinned);
        entityManager.persist(note);
        entityManager.flush();
        return note;
    }
}
