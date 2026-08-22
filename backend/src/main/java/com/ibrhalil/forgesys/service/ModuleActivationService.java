package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.common.exception.TenantNotFoundException;
import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.ModuleProperties;
import com.ibrhalil.forgesys.config.PermissionCatalog;
import com.ibrhalil.forgesys.dto.ModuleResponse;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.ModuleStatus;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.SubscriptionStatus;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.PermissionRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
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
 * Module activation (K-16 / Epic 3.0.A): plan check -&gt; module tenant migration -&gt;
 * permission seed -&gt; activation record. The activation record is written LAST — every
 * step before it is idempotent (Flyway history, ensure-permission), so a partial failure
 * is recovered by simply retrying (the DEBT-10 model).
 *
 * <p><strong>Transaction/session split (RISK-26, FK-deadlock avoidance):</strong> the
 * public-schema writes (checks, activation record) <em>join the caller's transaction</em>
 * — an activation triggered from tenant provisioning must insert its {@code t_tenant_modules}
 * row into the same transaction that holds the (not yet committed) {@code Company}, or the
 * FK would block on the uncommitted parent. Only the <em>permission seed</em> runs
 * {@code REQUIRES_NEW} on PostgreSQL, because it writes the <em>tenant</em> schema and the
 * provisioning outer session is pinned to {@code public} (the session resolves its schema
 * at open — RISK-26). {@link #activateForCompany}/{@link #resyncForCompany} wrap everything
 * in a set-and-restore {@link TenantContext} window so the REQUIRES_NEW seed and the audit
 * write resolve the tenant schema regardless of the caller's own context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleActivationService {

    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final PermissionRepository permissionRepository;
    private final TenantMigrationSupport tenantMigrationSupport;
    private final AuditService auditService;
    private final ObjectProvider<ModuleActivationService> self;
    // Optional: ModuleProperties is registered by ModuleSyncRunner (@Profile("!test")) —
    // absent in tests, which fall back to the built-in default keys.
    private final ObjectProvider<ModuleProperties> modulePropertiesProvider;

    /**
     * Catalog listing for the current tenant (resolved from {@link TenantContext}):
     * every {@link ModuleDefinition} with its activation state and plan eligibility.
     * A tenant without an active subscription still gets the catalog with
     * {@code allowedByPlan=false} (activation itself rejects with
     * {@link ErrorCode#SUBSCRIPTION_NOT_FOUND}).
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

    /**
     * Activates a module for the current tenant (HTTP path — module key from the
     * request). Transactional so the activation record lands atomically; the session
     * opens against the filter-set tenant context. Idempotent: an already-ACTIVE module
     * returns success without redoing the work.
     */
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
     * for a company — the single default-set entry point used by tenant provisioning
     * (K-21 verify) and {@code ModuleSyncRunner} backfill. Unknown keys are logged and
     * skipped; each activation is idempotent. Participates in the caller's transaction.
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
     * Activates a module for a known company (HTTP path, tenant provisioning,
     * {@code ModuleSyncRunner}). Runs inside a set-and-restore {@link TenantContext}
     * window (preserving the caller's context); public-schema writes join the caller's
     * transaction, the permission seed opens its own (see class javadoc). Idempotent —
     * returns the existing row for an already-ACTIVE module.
     */
    public TenantModule activateForCompany(Company company, ModuleDefinition module) {
        return inTenantContext(company, () -> doActivateForCompany(company, module));
    }

    /**
     * Re-applies a module's migrations and permission seed to an ALREADY-activated
     * tenant (used by {@code ModuleSyncRunner} so newly shipped module migrations /
     * permissions propagate to existing tenants). Does not touch the activation record.
     */
    public void resyncForCompany(Company company, ModuleDefinition module) {
        inTenantContext(company, () -> {
            tenantMigrationSupport.migrateModule(company.getSchemaName(), module);
            self.getObject().seedModulePermissionsInNewTx(module);
            return null;
        });
    }

    /**
     * The activation flow. NOT transactional on its own — the caller's transaction
     * scopes the public-schema writes (see class javadoc for why the record must join
     * the provisioning transaction). Order: gate checks first, idempotent DDL +
     * permission seed next, activation record LAST (partial failure is retriable).
     */
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

        TenantModule row = existing.orElseGet(TenantModule::new);
        row.setCompany(company);
        row.setModuleKey(module.key());
        row.setStatus(ModuleStatus.ACTIVE);
        row.setActivatedAt(OffsetDateTime.now());
        TenantModule saved = tenantModuleRepository.save(row);

        auditService.record("module_activated", "Module", saved.getId(), module.displayName());
        log.info("Module '{}' activated for company {}", module.key(), company.getId());
        return saved;
    }

    /**
     * Permission seed in its own transaction — the ONLY tenant-schema write, isolated
     * so it resolves the tenant schema regardless of the caller's (possibly
     * {@code public}-pinned) outer session. Called through the self proxy.
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
     * Rank of the tenant's ACTIVE subscription plan; empty when no subscription row
     * exists or it is not ACTIVE (degraded state — list endpoints show it, activation
     * rejects).
     */
    private Optional<Integer> activePlanRank(Company company) {
        return subscriptionRepository.findByCompanyId(company.getId())
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .map(Subscription::getPlan)
                .map(Plan -> Plan.getRank());
    }

    private Company currentCompany() {
        String schemaName = TenantContext.getCurrentTenant()
                .orElseThrow(() -> new TenantNotFoundException("Tenant context is not set"));
        return companyRepository.findBySchemaName(schemaName)
                .orElseThrow(() -> new TenantNotFoundException("Unknown tenant schema: " + schemaName));
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
