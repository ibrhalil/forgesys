package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppRecordRequest;
import com.ibrhalil.forgesys.dto.AppRecordResponse;
import com.ibrhalil.forgesys.dto.AppRecordSearchRequest;
import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.AppProperty;
import com.ibrhalil.forgesys.entity.AppRecord;
import com.ibrhalil.forgesys.entity.AppRecordValue;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.AppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRecordRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRecordValueRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
import com.ibrhalil.forgesys.service.AppQueryValidator.ValidatedFilter;
import com.ibrhalil.forgesys.service.AppQueryValidator.ValidatedSort;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Record (row) CRUD of custom apps (K-15 / Epic 3.0.B) — the EAV data path on top of
 * {@code t_app_records} + {@code t_app_record_values(value jsonb)}. A record is only
 * addressable through its owning app (nested lookup, 404 on cross-app access — same
 * scoping as {@code TaskService}). Values are validated per {@link PropertyType}
 * ({@link AppPropertyValueValidator}) before persisting; the per-app plan limit is
 * soft-blocked on create. List/get responses bulk-fetch the value rows (one query per
 * page — no N+1); search runs the PostgreSQL JSONB path
 * ({@link AppRecordSearchExecutor}).
 */
@Service
@RequiredArgsConstructor
public class AppRecordService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Sort whitelist for the plain record list — only the record's own audit columns. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final AppRepository appRepository;
    private final AppPropertyRepository propertyRepository;
    private final AppRecordRepository recordRepository;
    private final AppRecordValueRepository valueRepository;
    private final AppRecordSearchExecutor searchExecutor;
    private final AppPropertyValueValidator valueValidator;
    private final AppQueryValidator appQueryValidator;
    private final PlanLimitService planLimitService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ── reads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AppRecordResponse> list(UUID appId, Pageable pageable) {
        getAppOrThrow(appId);
        return toResponsePage(recordRepository.findAllByAppId(appId, pageable));
    }

    @Transactional(readOnly = true)
    public AppRecordResponse findById(UUID appId, UUID recordId) {
        App app = getAppOrThrow(appId);
        AppRecord record = getRecordOrThrow(appId, recordId);
        Map<UUID, Map<String, JsonNode>> values = valuesByRecordId(List.of(recordId));
        return toResponse(record, values.getOrDefault(recordId, Map.of()));
    }

    /**
     * PostgreSQL JSONB search (property-value filters/sorts). Request clauses are
     * validated against the app's property set before the native query is built —
     * see {@link AppRecordSearchExecutor}.
     */
    @Transactional(readOnly = true)
    public Page<AppRecordResponse> search(UUID appId, AppRecordSearchRequest request) {
        App app = getAppOrThrow(appId);
        Map<UUID, AppProperty> properties = AppQueryValidator.byId(properties(appId));
        List<ValidatedFilter> filters = appQueryValidator.validateFilters(
                request.filters(), properties, ErrorCode.VALIDATION_ERROR);
        List<ValidatedSort> sorts = appQueryValidator.validateSorts(
                request.sorts(), properties, ErrorCode.VALIDATION_ERROR);
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();
        Page<UUID> ids = searchExecutor.search(appId, filters, sorts, PageRequest.of(page, size));

        Map<UUID, AppRecord> byId = recordRepository.findAllById(ids.getContent()).stream()
                .filter(record -> record.getAppId().equals(appId))
                .collect(Collectors.toMap(AppRecord::getId, Function.identity()));
        List<AppRecord> ordered = ids.getContent().stream()
                .map(byId::get)
                .collect(Collectors.toCollection(ArrayList::new));
        Map<UUID, Map<String, JsonNode>> values = valuesByRecordId(ids.getContent());
        List<AppRecordResponse> content = ordered.stream()
                .map(record -> toResponse(record, values.getOrDefault(record.getId(), Map.of())))
                .toList();
        return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
    }

    // ── writes ───────────────────────────────────────────────────────────

    @Transactional
    public AppRecordResponse create(UUID appId, AppRecordRequest request) {
        App app = getAppOrThrow(appId);
        planLimitService.assertWithin(recordRepository.countByAppId(appId),
                planLimitService.maxRecordsPerApp(), "records in this app");
        List<AppProperty> properties = properties(appId);
        Map<String, JsonNode> validated = validatedCreateValues(properties, request.values());

        AppRecord record = new AppRecord();
        record.setAppId(appId);
        AppRecord saved = recordRepository.save(record);
        persistValues(saved.getId(), validated);
        auditService.record("app_record_created", "AppRecord", saved.getId(), app.getName());
        return toResponse(saved, valuesByRecordId(List.of(saved.getId()))
                .getOrDefault(saved.getId(), Map.of()));
    }

    /**
     * PATCH semantics: only provided keys are touched — a JSON {@code null} clears the
     * value (rejected for required properties), an absent key keeps it unchanged.
     */
    @Transactional
    public AppRecordResponse update(UUID appId, UUID recordId, AppRecordRequest request) {
        App app = getAppOrThrow(appId);
        AppRecord record = getRecordOrThrow(appId, recordId);
        List<AppProperty> properties = properties(appId);
        Map<UUID, AppProperty> byId = AppQueryValidator.byId(properties);

        Map<UUID, JsonNode> incoming = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : requestValues(request).entrySet()) {
            AppProperty property = resolveProperty(byId, entry.getKey());
            incoming.put(property.getId(), entry.getValue());
        }

        Map<UUID, AppRecordValue> existing = valueRepository.findAllByRecordId(recordId).stream()
                .collect(Collectors.toMap(AppRecordValue::getPropertyId, Function.identity()));
        for (Map.Entry<UUID, JsonNode> entry : incoming.entrySet()) {
            AppProperty property = byId.get(entry.getKey());
            JsonNode value = entry.getValue();
            AppRecordValue row = existing.get(entry.getKey());
            if (value == null || value.isNull()) {
                if (property.isRequired()) {
                    throw new BusinessException(ErrorCode.APP_RECORD_VALUE_INVALID,
                            "Property '" + property.getName() + "' is required and cannot be cleared");
                }
                if (row != null) {
                    valueRepository.delete(row);
                    existing.remove(entry.getKey());
                }
                continue;
            }
            valueValidator.validate(property, value);
            if (row == null) {
                AppRecordValue created = new AppRecordValue();
                created.setRecordId(recordId);
                created.setPropertyId(property.getId());
                created.setValue(value.toString());
                valueRepository.save(created);
            } else {
                row.setValue(value.toString());
                valueRepository.save(row);
            }
        }
        recordRepository.save(record);
        auditService.record("app_record_updated", "AppRecord", recordId, app.getName());
        return toResponse(record, valuesByRecordId(List.of(recordId))
                .getOrDefault(recordId, Map.of()));
    }

    @Transactional
    public void delete(UUID appId, UUID recordId) {
        App app = getAppOrThrow(appId);
        AppRecord record = getRecordOrThrow(appId, recordId);
        recordRepository.delete(record);
        auditService.record("app_record_deleted", "AppRecord", recordId, app.getName());
    }

    // ── value validation & persistence ──────────────────────────────────

    /** Create-time validation: keys resolve, types match, every required property covered. */
    private Map<String, JsonNode> validatedCreateValues(List<AppProperty> properties,
                                                        Map<String, JsonNode> values) {
        Map<UUID, AppProperty> byId = AppQueryValidator.byId(properties);
        Map<String, JsonNode> validated = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            AppProperty property = resolveProperty(byId, entry.getKey());
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                if (property.isRequired()) {
                    throw new BusinessException(ErrorCode.APP_RECORD_VALUE_INVALID,
                            "Property '" + property.getName() + "' is required and cannot be empty");
                }
                continue;
            }
            valueValidator.validate(property, value);
            validated.put(entry.getKey(), value);
        }
        for (AppProperty property : properties) {
            if (property.getType() != PropertyType.FORMULA && property.isRequired()
                    && !validated.containsKey(property.getId().toString())) {
                throw new BusinessException(ErrorCode.APP_RECORD_VALUE_INVALID,
                        "Required property '" + property.getName() + "' is missing a value");
            }
        }
        return validated;
    }

    private void persistValues(UUID recordId, Map<String, JsonNode> validated) {
        for (Map.Entry<String, JsonNode> entry : validated.entrySet()) {
            AppRecordValue row = new AppRecordValue();
            row.setRecordId(recordId);
            row.setPropertyId(UUID.fromString(entry.getKey()));
            row.setValue(entry.getValue().toString());
            valueRepository.save(row);
        }
    }

    private AppProperty resolveProperty(Map<UUID, AppProperty> byId, String propertyId) {
        UUID id;
        try {
            id = UUID.fromString(propertyId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.APP_RECORD_VALUE_INVALID,
                    "Unknown property id: '" + propertyId + "'");
        }
        AppProperty property = byId.get(id);
        if (property == null) {
            throw new BusinessException(ErrorCode.APP_RECORD_VALUE_INVALID,
                    "Property '" + propertyId + "' does not exist in this app");
        }
        if (property.getType() == PropertyType.FORMULA) {
            throw new BusinessException(ErrorCode.APP_RECORD_VALUE_INVALID,
                    "Property '" + property.getName() + "' is a deferred FORMULA and cannot be set");
        }
        return property;
    }

    // ── lookups & mappers ───────────────────────────────────────────────

    private App getAppOrThrow(UUID id) {
        return appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found: " + id));
    }

    private AppRecord getRecordOrThrow(UUID appId, UUID recordId) {
        return recordRepository.findByIdAndAppId(recordId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found: " + recordId));
    }

    private List<AppProperty> properties(UUID appId) {
        return propertyRepository.findAllByAppIdOrderByPositionAscNameAsc(appId);
    }

    private static Map<String, JsonNode> requestValues(AppRecordRequest request) {
        return request.values() == null ? Map.of() : request.values();
    }

    /** Bulk value fetch for a page of records — one query, grouped by record id (no N+1). */
    private Map<UUID, Map<String, JsonNode>> valuesByRecordId(List<UUID> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, JsonNode>> grouped = new HashMap<>();
        for (AppRecordValue row : valueRepository.findAllByRecordIdIn(recordIds)) {
            grouped.computeIfAbsent(row.getRecordId(), id -> new LinkedHashMap<>())
                    .put(row.getPropertyId().toString(), objectMapper.readTree(row.getValue()));
        }
        return grouped;
    }

    private Page<AppRecordResponse> toResponsePage(Page<AppRecord> page) {
        Map<UUID, Map<String, JsonNode>> values = valuesByRecordId(
                page.getContent().stream().map(AppRecord::getId).toList());
        List<AppRecordResponse> content = page.getContent().stream()
                .map(record -> toResponse(record, values.getOrDefault(record.getId(), Map.of())))
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private AppRecordResponse toResponse(AppRecord record, Map<String, JsonNode> values) {
        return new AppRecordResponse(record.getId(), record.getAppId(), values,
                record.getCreatedDate(), record.getUpdatedAt(), record.getCreatedBy());
    }
}
