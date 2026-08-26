package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.config.ModuleDefinition;
import com.ibrhalil.forgesys.config.PlanDefinition;
import com.ibrhalil.forgesys.dto.CompanyModulesUpdateRequest;
import com.ibrhalil.forgesys.dto.CompanyReportResponse;
import com.ibrhalil.forgesys.dto.CompanyResponse;
import com.ibrhalil.forgesys.dto.ModuleResponse;
import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.SubscriptionResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.entity.Company_;
import com.ibrhalil.forgesys.entity.Plan;
import com.ibrhalil.forgesys.entity.Subscription;
import com.ibrhalil.forgesys.entity.TenantModule;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
import com.ibrhalil.forgesys.persistence.repository.CompanyRepository;
import com.ibrhalil.forgesys.persistence.repository.NoteRepository;
import com.ibrhalil.forgesys.persistence.repository.PlanRepository;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.persistence.repository.SubscriptionRepository;
import com.ibrhalil.forgesys.persistence.repository.TenantModuleRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.tenant.TenantContextExecutor;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformCompanyService {

    /**
     * Filterable/sortable attributes of the platform company list (K-49); {@code q}
     * matches {@code name}/{@code subdomain}. {@code schemaName} deliberately
     * unregistered (internal detail).
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Company_.NAME, FilterFieldType.STRING, true)
            .field(Company_.SUBDOMAIN, FilterFieldType.STRING, true)
            .enumField(Company_.STATUS, CompanyStatus.class, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final CompanyRepository companyRepository;
    private final PlatformCompanyListQueryExecutor platformCompanyListQueryExecutor;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final PlanLimitService planLimitService;
    private final ModuleActivationService moduleActivationService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AppRepository appRepository;
    private final NoteRepository noteRepository;
    private final PlatformAuditService platformAuditService;
    private final PlatformUserRepository platformUserRepository;
    private final ObjectProvider<PlatformCompanyService> self;

    @Transactional(readOnly = true)
    public Page<CompanyResponse> search(String q, List<String> qFields, Pageable pageable) {
        return doSearch(StringUtils.hasText(q) ? q.trim() : null, qFields, List.of(), pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /platform/companies/search}. */
    @Transactional(readOnly = true)
    public Page<CompanyResponse> search(SearchRequest request, Pageable pageable) {
        return doSearch(request.q(), request.qFields(), request.filters(), pageable);
    }

    private Page<CompanyResponse> doSearch(String q, List<String> qFields,
            List<com.ibrhalil.forgesys.dto.FilterCriteria> filters, Pageable pageable) {
        return TenantContextExecutor.withoutTenantContext(() -> {
            Specification<Company> spec = FilterSpecifications.from(FILTER_FIELDS, q, qFields, filters);
            return platformCompanyListQueryExecutor.search(spec, pageable);
        });
    }

    @Transactional(readOnly = true)
    public CompanyResponse findById(UUID id) {
        return TenantContextExecutor.withoutTenantContext(() ->
                companyRepository.findById(id)
                        .map(this::mapToResponse)
                        .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + id))
        );
    }

    @Transactional
    public CompanyResponse updateStatus(UUID id, CompanyStatus status) {
        String[] previousStatus = new String[1];
        Company saved = TenantContextExecutor.withoutTenantContext(() -> {
            Company company = loadCompany(id);
            if (!company.getStatus().canTransitionTo(status)) {
                // [RISK-32] reject transitions that would leave the tenant broken.
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Illegal company status transition: " + company.getStatus() + " -> " + status);
            }
            previousStatus[0] = company.getStatus().name();
            company.setStatus(status);
            return companyRepository.save(company);
        });
        log.info("Company status updated: id={}, newStatus={}", saved.getId(), saved.getStatus());
        audit(PlatformAuditService.ACTION_COMPANY_STATUS_UPDATED, saved.getId(),
                "%s -> %s".formatted(previousStatus[0], saved.getStatus()));
        return mapToResponse(saved);
    }

    /* ── K-50 F4: subscription ── */

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID id) {
        return TenantContextExecutor.withoutTenantContext(() -> {
            Company company = loadCompany(id);
            Subscription subscription = requireSubscription(company);
            return mapSubscription(company.getId(), subscription);
        });
    }

    /** Platform-driven plan change; the key is validated against the {@link PlanDefinition} registry. */
    @Transactional
    public SubscriptionResponse changePlan(UUID id, String planKey) {
        PlanDefinition definition = PlanDefinition.fromKey(planKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND, "Unknown plan: " + planKey));
        String[] previousPlanKey = new String[1];
        Subscription saved = TenantContextExecutor.withoutTenantContext(() -> {
            Company company = loadCompany(id);
            assertCompanyActive(company);
            Plan plan = planRepository.findByKey(definition.key())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND,
                            "Plan row missing for key: " + planKey));
            Subscription subscription = requireSubscription(company);
            previousPlanKey[0] = subscription.getPlan().getKey();
            subscription.setPlan(plan);
            return subscriptionRepository.save(subscription);
        });
        audit(PlatformAuditService.ACTION_TENANT_PLAN_CHANGED, id,
                "%s -> %s".formatted(previousPlanKey[0], definition.key()));
        return mapSubscription(id, saved);
    }

    /* ── K-50 F4: modules ── */

    @Transactional(readOnly = true)
    public List<ModuleResponse> getModules(UUID id) {
        return TenantContextExecutor.withoutTenantContext(() -> moduleCatalog(loadCompany(id)));
    }

    /**
     * Applies activate/deactivate entries via {@link ModuleActivationService}
     * (activation's tenant-schema writes run in their own windows/transactions).
     */
    @Transactional
    public List<ModuleResponse> updateModules(UUID id, CompanyModulesUpdateRequest request) {
        Company company = TenantContextExecutor.withoutTenantContext(() -> {
            Company loaded = loadCompany(id);
            assertCompanyActive(loaded);
            return loaded;
        });
        for (CompanyModulesUpdateRequest.ModuleActivation activation : request.activations()) {
            ModuleDefinition module = ModuleDefinition.fromKey(activation.key())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_NOT_FOUND,
                            "Unknown module: " + activation.key()));
            if (activation.active()) {
                moduleActivationService.activateForCompany(company, module);
                audit(PlatformAuditService.ACTION_TENANT_MODULE_ACTIVATED, id, "module=" + module.key());
            } else {
                moduleActivationService.deactivateForCompany(company, module);
                audit(PlatformAuditService.ACTION_TENANT_MODULE_DEACTIVATED, id, "module=" + module.key());
            }
        }
        return moduleCatalog(company);
    }

    private List<ModuleResponse> moduleCatalog(Company company) {
        Map<String, TenantModule> activated = tenantModuleRepository.findByCompanyId(company.getId()).stream()
                .collect(Collectors.toMap(TenantModule::getModuleKey, Function.identity()));
        Optional<Integer> planRank = planLimitService.tryActivePlan(company).map(PlanDefinition::rank);
        return Arrays.stream(ModuleDefinition.values())
                .map(module -> new ModuleResponse(
                        module.key(),
                        module.displayName(),
                        module.minPlan().key(),
                        activated.containsKey(module.key()),
                        planRank.isPresent() && planRank.get() >= module.minPlan().rank()))
                .toList();
    }

    /* ── K-50 F4: usage report ── */

    /**
     * Cross-schema usage counters. The counts run in a REQUIRES_NEW read
     * transaction inside the tenant window — a fresh session resolves the tenant
     * schema from the context (ModuleActivationService pattern); count queries
     * only, no entity hydration.
     */
    public CompanyReportResponse getReport(UUID id) {
        Company company = TenantContextExecutor.withoutTenantContext(() -> loadCompany(id));
        return TenantContextExecutor.inTenantContext(company.getSchemaName(),
                () -> self.getObject().countTenantUsage(company));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CompanyReportResponse countTenantUsage(Company company) {
        return new CompanyReportResponse(
                company.getId(),
                userRepository.count(),
                projectRepository.count(),
                appRepository.count(),
                noteRepository.count());
    }

    /* ── helpers ── */

    private Company loadCompany(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + id));
    }

    /** Lifecycle mutations (plan change, module change) only run against ACTIVE companies. */
    private void assertCompanyActive(Company company) {
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COMPANY_NOT_ACTIVE,
                    "Company is not active: " + company.getStatus());
        }
    }

    private Subscription requireSubscription(Company company) {
        return subscriptionRepository.findByCompanyId(company.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "Tenant has no active subscription"));
    }

    private SubscriptionResponse mapSubscription(UUID companyId, Subscription subscription) {
        return new SubscriptionResponse(
                companyId,
                subscription.getPlan().getKey(),
                subscription.getPlan().getName(),
                subscription.getStatus().name(),
                subscription.getStartedAt());
    }

    /**
     * Best-effort platform audit entry (actor = platform principal; type resolved
     * from {@code t_platform_users}, SYSTEM fallback). Never breaks the business op.
     */
    private void audit(String action, UUID companyId, String detail) {
        try {
            UUID actorId = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
                actorId = principal.getUserId();
            }
            String actorType = actorId == null ? PlatformAuditService.ACTOR_SYSTEM
                    : platformUserRepository.findById(actorId)
                            .map(user -> user.getUserType().name())
                            .orElse(PlatformAuditService.ACTOR_SYSTEM);
            platformAuditService.record(actorId, actorType, action, "Company", companyId, detail);
        } catch (RuntimeException ex) {
            log.warn("Failed to record platform audit entry (action={}, companyId={})", action, companyId, ex);
        }
    }

    private CompanyResponse mapToResponse(Company company) {
        // schemaName intentionally omitted — internal detail, not part of the API contract.
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getSubdomain(),
                company.getStatus()
        );
    }
}
