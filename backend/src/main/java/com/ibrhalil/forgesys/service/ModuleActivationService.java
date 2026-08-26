package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.ModuleProperties;
import com.ibrhalil.forgesys.config.PermissionCatalog;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.dto.ModuleResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Project;
import com.ibrhalil.forgesys.entity.ProjectType;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.tenant.TenantContextExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module activation (K-16): plan check → module tenant migration → permission seed →
 * activation record (LAST — every earlier step is idempotent, so partial failure is
 * recovered by retrying). Public-schema writes JOIN the caller's tx (an activation
 * under provisioning would FK-block on the uncommitted Company); only the tenant-schema
 * writes run REQUIRES_NEW (the outer session is public-pinned, RISK-26).
 * Rationale: docs/CODE_NOTES.md (backend/service → ModuleActivationService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleActivationService {

    /** Per-type default container name (K-45); mirrored in the module V2 migrations. */
    static final String DEFAULT_PROJECT_NAME = "Genel";

    private final CompanyRepository companyRepository;
    private final PlanLimitService planLimitService;
    private final TenantModuleRepository tenantModuleRepository;
    private final PermissionRepository permissionRepository;
    private final ProjectRepository projectRepository;
    private final TenantMigrationSupport tenantMigrationSupport;
    private final AuditService auditService;
    private final ObjectProvider<ModuleActivationService> self;
    // Optional: ModuleProperties is registered by ModuleSyncRunner (@Profile("!test")) —
    // absent in tests, which fall back to the built-in default keys.
    private final ObjectProvider<ModuleProperties> modulePropertiesProvider;

    /**
     * Catalog listing with activation state + plan eligibility; a no-subscription
     * tenant still gets the catalog with {@code allowedByPlan=false}.
     */
    @Transactional(readOnly = true)
    public List<ModuleResponse> listModules() {
        Company company = currentCompany();
        Map<String, TenantModule> activated = tenantModuleRepository.findByCompanyId(company.getId()).stream()
                .collect(Collectors.toMap(TenantModule::getModuleKey, Function.identity()));
        Optional<Integer> planRank = activePlanRank(company);
        return Arrays.stream(ModuleDefinition.values())
                .map(module -> new ModuleResponse(
                        module.key(),
                        module.displayName(),
                        module.minPlan().key(),
                        activated.containsKey(module.key()),
                        planRank.isPresent() && planRank.get() >= module.minPlan().rank()))
                .toList();
    }

    /** Activates by key (HTTP path); idempotent — an already-ACTIVE module is a no-op success. */
    @Transactional
    public ModuleResponse activate(String moduleKey) {
        ModuleDefinition module = ModuleDefinition.fromKey(moduleKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_NOT_FOUND, "Unknown module: " + moduleKey));
        Company company = currentCompany();
        TenantModule result = activateForCompany(company, module);
        return new ModuleResponse(module.key(), module.displayName(), module.minPlan().key(),
                result.getStatus() == ModuleStatus.ACTIVE, true);
    }

    /**
     * Activates the configured default modules ({@code forgesys.modules.default-keys})
     * for a company — the single default-set entry point (provisioning + sync runner).
     * Unknown keys are logged + skipped; participates in the caller's transaction.
     */
    public void activateDefaultModules(Company company) {
        ModuleProperties properties = modulePropertiesProvider.getIfAvailable(() -> new ModuleProperties(null));
        for (String key : properties.effectiveDefaultKeys()) {
            ModuleDefinition.fromKey(key).ifPresentOrElse(
                    module -> activateForCompany(company, module),
                    () -> log.warn("Unknown default module key in forgesys.modules.default-keys: {}", key));
        }
    }

    /**
     * Activates for a known company inside a set-and-restore {@link TenantContext}
     * window; idempotent (returns the existing row when already ACTIVE).
     */
    public TenantModule activateForCompany(Company company, ModuleDefinition module) {
        return TenantContextExecutor.inTenantContext(company.getSchemaName(), () -> doActivateForCompany(company, module));
    }

    /**
     * K-50 F4: platform-driven deactivation — soft-deletes the activation row
     * (public schema, no tenant window needed). Idempotent; module data,
     * permissions and migrations are deliberately KEPT (cleanup semantics arrive
     * with billing-driven downgrades, Faz 6).
     */
    public void deactivateForCompany(Company company, ModuleDefinition module) {
        tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), module.key())
                .ifPresent(row -> {
                    tenantModuleRepository.delete(row);
                    log.info("Module '{}' deactivated for company {}", module.key(), company.getId());
                });
    }

    /** Re-applies migrations + permission seed to an ALREADY-activated tenant (ModuleSyncRunner). */
    public void resyncForCompany(Company company, ModuleDefinition module) {
        TenantContextExecutor.inTenantContext(company.getSchemaName(), () -> {
            tenantMigrationSupport.migrateModule(company.getSchemaName(), module);
            self.getObject().seedModulePermissionsInNewTx(module);
        });
    }

    /**
     * The activation flow. NOT transactional on its own — the caller's tx scopes the
     * public-schema writes. Order: gates first, idempotent DDL + seeds next, record LAST.
     */
    @AuditLog(action = "module_activated", entityType = "Module", entityId = "#result.id", entityName = "#module.displayName()")
    public TenantModule doActivateForCompany(Company company, ModuleDefinition module) {
        Optional<TenantModule> existing = tenantModuleRepository.findByCompanyIdAndModuleKey(company.getId(), module.key());
        if (existing.isPresent() && existing.get().getStatus() == ModuleStatus.ACTIVE) {
            log.debug("Module '{}' already active for company {}", module.key(), company.getId());
            return existing.get();
        }
        int planRank = activePlanRank(company).orElseThrow(() ->
                new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "Tenant has no active subscription"));
        if (planRank < module.minPlan().rank()) {
            throw new BusinessException(ErrorCode.MODULE_PLAN_REQUIRED,
                    "Module '%s' requires plan '%s' or higher".formatted(module.key(), module.minPlan().key()));
        }
        tenantMigrationSupport.migrateModule(company.getSchemaName(), module);
        self.getObject().seedModulePermissionsInNewTx(module);
        self.getObject().ensureDefaultProjectInNewTx(module);

        TenantModule row = existing.orElseGet(TenantModule::new);
        row.setCompany(company);
        row.setModuleKey(module.key());
        row.setStatus(ModuleStatus.ACTIVE);
        row.setActivatedAt(OffsetDateTime.now());
        TenantModule saved = tenantModuleRepository.save(row);

        log.info("Module '{}' activated for company {}", module.key(), company.getId());
        return saved;
    }

    /**
     * REQUIRES_NEW — the ONLY tenant-schema write, isolated so it resolves the tenant
     * schema even under a {@code public}-pinned caller session.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedModulePermissionsInNewTx(ModuleDefinition module) {
        for (PermissionCatalog.PermissionDefinition definition : module.permissions()) {
            permissionRepository.findByName(definition.name()).orElseGet(() -> {
                Permission permission = new Permission();
                permission.setName(definition.name());
                permission.setDescription(definition.description());
                log.info("Seeding module permission: {}", definition.name());
                return permissionRepository.save(permission);
            });
        }
    }

    /**
     * Ensures the module's per-type default "Genel" container (K-45) — covers
     * re-activation after a soft-delete and migration no-op schemas. Content-collection
     * types only (a TASKS default would be meaningless noise). Same REQUIRES_NEW
     * isolation as the permission seed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefaultProjectInNewTx(ModuleDefinition module) {
        ProjectType type = module.projectType();
        if (type == null || type == ProjectType.TASKS) {
            return;
        }
        if (!projectRepository.findDefaultIdsByType(type).isEmpty()) {
            return;
        }
        Project project = projectRepository.findFirstByNameAndTypeOrderByName(DEFAULT_PROJECT_NAME, type)
                .orElseGet(Project::new);
        if (project.getId() == null) {
            project.setName(DEFAULT_PROJECT_NAME);
            project.setType(type);
        }
        project.setDefault(true);
        projectRepository.save(project);
        log.info("Ensured default '{}' container for module '{}' (type {})", DEFAULT_PROJECT_NAME, module.key(), type);
    }

    /**
     * Rank of the ACTIVE subscription plan; empty in degraded states (list shows it,
     * activation rejects). Single K-40 resolution chain via {@link PlanLimitService}.
     */
    private Optional<Integer> activePlanRank(Company company) {
        return planLimitService.tryActivePlan(company).map(PlanDefinition::rank);
    }

    private Company currentCompany() {
        String schemaName = TenantContext.getCurrentTenant()
                .orElseThrow(() -> new TenantNotFoundException("Tenant context is not set"));
        return companyRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new TenantNotFoundException("Unknown tenant schema: " + schemaName));
    }
}
