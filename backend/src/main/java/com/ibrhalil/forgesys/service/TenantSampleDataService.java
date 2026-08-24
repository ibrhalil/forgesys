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
import java.util.Optional;
import java.util.UUID;

/**
 * Sample data seeding for newly provisioned tenants (K-47, the Linear onboarding
 * pattern): instead of empty screens, the admin's first login meets small instructive
 * content in every default module — one TASKS project with four guided tasks, two note
 * categories with two markdown notes, and one sample app with properties/views/records.
 * Content is fixed EN strings: it is tenant data, not UI — deliberately no i18n.
 *
 * <p>Mirrors the {@link ModuleActivationService} transactional pattern: the seed runs
 * in its own {@code REQUIRES_NEW} transaction behind a set-and-restore
 * {@link TenantContext} window, because the caller (provisioning) holds a
 * {@code public}-pinned session (RISK-26). Timing matters just as much — the caller
 * invokes {@link #seedForCompany} from an afterCommit synchronization so this fresh
 * transaction can SEE the just-committed {@code t_tenant_modules} activation records
 * and FREE subscription row that gate {@code ProjectService} and the plan limits;
 * under read-committed a same-transaction call would fail those gates invisibly.
 *
 * <p><strong>Fail-safe:</strong> {@link #seedForCompany} swallows every exception with
 * a warn log — sample data must never break provisioning. Reusing the content services
 * is safe here: authority checks live in the controller layer (system context cannot
 * 403), {@code @AuditLog} falls back to the {@code "system"} actor, and the seed stays
 * well inside the FREE plan limits (1 app, 4 records).
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
    // Self-proxy so the seed body runs through the Spring proxy — required for the
    // REQUIRES_NEW transaction boundary to take effect (ModuleActivationService pattern).
    private final ObjectProvider<TenantSampleDataService> self;

    /**
     * Entry point — called by {@code TenantProvisioningService} after the provisioning
     * transaction commits. Fail-safe: any failure is swallowed with a warn log;
     * provisioning continues regardless.
     */
    public void seedForCompany(Company company, UUID adminUserId) {
        if (!properties.enabled()) {
            log.debug("Sample data seeding disabled (forgesys.provisioning.sample-data.enabled=false)");
            return;
        }
        try {
            inTenantContext(company, () -> {
                self.getObject().seedInNewTx(adminUserId);
                return null;
            });
            log.info("Sample data seeded for tenant {}", company.getSchemaName());
        } catch (Exception e) {
            log.warn("Sample data seeding failed for tenant {} — provisioning continues",
                    company.getSchemaName(), e);
        }
    }

    /**
     * The seed body in its own transaction — the tenant-schema write must resolve the
     * tenant schema on a fresh connection regardless of the caller's (possibly
     * {@code public}-pinned) outer session. Ordered pm → notes → apps; any module's
     * failure aborts only the seed (caught by {@link #seedForCompany}).
     */
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

        // Stage values are spread across the three options; the fourth record carries
        // none — the Board's empty bucket example.
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

    /** Runs the action inside the company's tenant context, restoring the caller's context afterward. */
    private <T> T inTenantContext(Company company, java.util.function.Supplier<T> action) {
        Optional<String> previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(company.getSchemaName());
        try {
            return action.get();
        } finally {
            previous.ifPresentOrElse(TenantContext::setCurrentTenant, TenantContext::clear);
        }
    }
}
