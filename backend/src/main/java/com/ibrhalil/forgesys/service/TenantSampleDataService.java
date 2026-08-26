package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.SampleDataProperties;
import com.ibrhalil.forgesys.dto.AppPropertyConfigDto;
import com.ibrhalil.forgesys.dto.AppPropertyRequest;
import com.ibrhalil.forgesys.dto.AppRecordRequest;
import com.ibrhalil.forgesys.dto.AppRequest;
import com.ibrhalil.forgesys.dto.AppViewConfigDto;
import com.ibrhalil.forgesys.dto.AppViewRequest;
import com.ibrhalil.forgesys.dto.NoteCategoryRequest;
import com.ibrhalil.forgesys.dto.NoteRequest;
import com.ibrhalil.forgesys.dto.ProjectRequest;
import com.ibrhalil.forgesys.dto.TaskRequest;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.entity.TaskPriority;
import com.ibrhalil.forgesys.entity.TaskStatus;
import com.ibrhalil.forgesys.entity.ViewType;
import com.ibrhalil.forgesys.tenant.TenantContextExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Onboarding sample data for newly provisioned tenants (K-47, the Linear pattern):
 * one TASKS project + 4 guided tasks, 2 note categories + 2 markdown notes, 1 app +
 * properties/views/records (inside FREE plan limits). Fixed EN strings — tenant data,
 * not UI (no i18n). The seed runs REQUIRES_NEW behind a set-and-restore
 * {@link TenantContext} window (RISK-26) from provisioning's afterCommit — it must
 * SEE the committed activation + subscription rows. Fail-safe: seeding never breaks
 * provisioning. Rationale: docs/CODE_NOTES.md (backend/service → TenantSampleDataService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSampleDataService {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final NoteCategoryService noteCategoryService;
    private final NoteService noteService;
    private final AppBuilderService appBuilderService;
    private final AppRecordService appRecordService;
    private final SampleDataProperties properties;
    // Self-proxy: the REQUIRES_NEW boundary only takes effect through the Spring proxy.
    private final ObjectProvider<TenantSampleDataService> self;

    /** Entry point (called after the provisioning tx commits); fail-safe by contract. */
    public void seedForCompany(Company company, UUID adminUserId) {
        if (!properties.enabled()) {
            log.debug("Sample data seeding disabled (forgesys.provisioning.sample-data.enabled=false)");
            return;
        }
        try {
            TenantContextExecutor.inTenantContext(company.getSchemaName(), () -> self.getObject().seedInNewTx(adminUserId));
            log.info("Sample data seeded for tenant {}", company.getSchemaName());
        } catch (Exception e) {
            log.warn("Sample data seeding failed for tenant {} — provisioning continues",
                    company.getSchemaName(), e);
        }
    }

    /** The seed body in its own tx; ordered pm → notes → apps (failure aborts only the seed). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedInNewTx(UUID adminUserId) {
        seedPm(adminUserId);
        seedNotes();
        seedApps();
    }

    // ── pm ───────────────────────────────────────────────────────────────

    private void seedPm(UUID adminUserId) {
        UUID projectId = projectService.create(new ProjectRequest(
                "Getting Started",
                "A short tour of ForgeSys — delete or edit anything here.",
                ProjectType.TASKS,
                null)).id();

        LocalDate today = LocalDate.now();
        taskService.create(projectId, new TaskRequest(
                "Explore the board",
                "Open each column and try the filters.",
                TaskStatus.TODO, TaskPriority.LOW, adminUserId, today.plusDays(2)));
        taskService.create(projectId, new TaskRequest(
                "Drag a card to Done",
                "Grab this card and drop it into Done — or use the status select.",
                TaskStatus.TODO, TaskPriority.MEDIUM, adminUserId, today.plusDays(3)));
        taskService.create(projectId, new TaskRequest(
                "Create your first project",
                "Use the New Project button on the Projects page.",
                TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, adminUserId, today.plusDays(4)));
        taskService.create(projectId, new TaskRequest(
                "Invite your team",
                "Add teammates from Identity & Access > Users.",
                TaskStatus.TODO, TaskPriority.HIGH, adminUserId, today.plusDays(5)));
    }

    // ── notes ────────────────────────────────────────────────────────────

    private void seedNotes() {
        // Flat creates with a null projectId land in the default "Genel" NOTES container.
        UUID guides = noteCategoryService.create(new NoteCategoryRequest("Guides", null, null)).id();
        noteCategoryService.create(new NoteCategoryRequest("Ideas", null, null));

        noteService.create(new NoteRequest(
                "Welcome to Notes",
                """
                # Welcome to Notes

                - Notes live in **containers** — this one is the default.
                - Pin what matters: this note is pinned.
                - Markdown is supported — **bold**, *italic*, `code`.

                Delete or edit anything here. It is yours.
                """,
                guides, true, null));
        noteService.create(new NoteRequest(
                "Markdown cheatsheet",
                """
                # Markdown cheatsheet

                ## Links
                [ForgeSys](https://forge.sys)

                ## Code blocks
                ```
                SELECT * FROM t_projects;
                ```

                ## Emphasis
                **bold**, *italic*, ~~strikethrough~~.
                """,
                guides, null, null));
    }

    // ── apps ─────────────────────────────────────────────────────────────

    private void seedApps() {
        UUID appId = appBuilderService.create(new AppRequest(
                "Team Tracker", "A sample app — shape it into anything you like.", null, null)).id();

        UUID name = appBuilderService.addProperty(appId, new AppPropertyRequest(
                "Name", PropertyType.TEXT, null, true, 0)).id();
        UUID stage = appBuilderService.addProperty(appId, new AppPropertyRequest(
                "Stage", PropertyType.SELECT,
                new AppPropertyConfigDto(List.of("Discovery", "In Progress", "Launched"), null),
                false, 1)).id();
        UUID launchDate = appBuilderService.addProperty(appId, new AppPropertyRequest(
                "Launch date", PropertyType.DATE, null, false, 2)).id();

        appBuilderService.addView(appId, new AppViewRequest("Table", ViewType.TABLE, null, 0));
        appBuilderService.addView(appId, new AppViewRequest("Board", ViewType.BOARD,
                new AppViewConfigDto(null, null, stage.toString(), null), 1));

        // Stage values spread across the options; the fourth record carries none
        // (the Board's empty-bucket example).
        appRecordService.create(appId, sampleRecord(name, "Aurora", stage, "Discovery",
                launchDate, LocalDate.of(2026, 9, 30)));
        appRecordService.create(appId, sampleRecord(name, "Beacon", stage, "In Progress",
                launchDate, LocalDate.of(2026, 10, 15)));
        appRecordService.create(appId, sampleRecord(name, "Cobalt", stage, "Launched",
                launchDate, LocalDate.of(2026, 6, 1)));
        appRecordService.create(appId, sampleRecord(name, "Dune", null, null, null, null));
    }

    private AppRecordRequest sampleRecord(UUID namePropertyId, String name,
                                          UUID stagePropertyId, String stage,
                                          UUID datePropertyId, LocalDate launchDate) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put(namePropertyId.toString(), StringNode.valueOf(name));
        if (stage != null) {
            values.put(stagePropertyId.toString(), StringNode.valueOf(stage));
        }
        if (launchDate != null) {
            values.put(datePropertyId.toString(), StringNode.valueOf(launchDate.toString()));
        }
        return new AppRecordRequest(values);
    }
}
