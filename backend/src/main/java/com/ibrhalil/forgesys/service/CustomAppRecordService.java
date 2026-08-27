package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CustomAppRecordRequest;
import com.ibrhalil.forgesys.dto.CustomAppRecordResponse;
import com.ibrhalil.forgesys.dto.CustomAppRecordSearchRequest;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.CustomAppRecord;
import com.ibrhalil.forgesys.entity.CustomAppRecordValue;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CustomAppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRecordRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRecordValueRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import com.ibrhalil.forgesys.service.CustomAppQueryValidator.ValidatedFilter;
import com.ibrhalil.forgesys.service.CustomAppQueryValidator.ValidatedSort;
import com.ibrhalil.forgesys.audit.AuditLog;
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
 * Record (row) CRUD of custom apps (K-15) on {@code t_custom_app_records} +
 * {@code t_custom_app_record_values(value jsonb)}. A record is addressable only through its
 * owning customApp (404 on cross-customApp access); values validated per {@link PropertyType};
 * per-customApp plan limit soft-blocked on create; value rows bulk-fetched per page (no N+1).
 * Rationale: docs/CODE_NOTES.md (backend/service → CustomAppRecordService).
 */
@Service
@RequiredArgsConstructor
public class CustomAppRecordService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Sort whitelist for the plain record list — only the record's own audit columns. */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final CustomAppRepository customAppRepository;
    private final CustomAppPropertyRepository propertyRepository;
    private final CustomAppRecordRepository recordRepository;
    private final CustomAppRecordValueRepository valueRepository;
    private final CustomAppRecordSearchExecutor searchExecutor;
    private final CustomAppPropertyValueValidator valueValidator;
    private final CustomAppQueryValidator customAppQueryValidator;
    private final PlanLimitService planLimitService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ── reads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CustomAppRecordResponse> list(UUID customAppId, Pageable pageable) {
        getCustomAppOrThrow(customAppId);
        return toResponsePage(recordRepository.findAllByCustomAppId(customAppId, pageable));
    }

    @Transactional(readOnly = true)
    public CustomAppRecordResponse findById(UUID customAppId, UUID recordId) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppRecord record = getRecordOrThrow(customAppId, recordId);
        Map<UUID, Map<String, JsonNode>> values = valuesByRecordId(List.of(recordId));
        return toResponse(record, values.getOrDefault(recordId, Map.of()));
    }

    /** PostgreSQL JSONB search; clauses validated before the native query is built. */
    @Transactional(readOnly = true)
    public Page<CustomAppRecordResponse> search(UUID customAppId, CustomAppRecordSearchRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        Map<UUID, CustomAppProperty> properties = CustomAppQueryValidator.byId(properties(customAppId));
        List<ValidatedFilter> filters = customAppQueryValidator.validateFilters(
                request.filters(), properties, ErrorCode.VALIDATION_ERROR);
        List<ValidatedSort> sorts = customAppQueryValidator.validateSorts(
                request.sorts(), properties, ErrorCode.VALIDATION_ERROR);
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();
        Page<UUID> ids = searchExecutor.search(customAppId, filters, sorts, PageRequest.of(page, size));

        Map<UUID, CustomAppRecord> byId = recordRepository.findAllById(ids.getContent()).stream()
                .filter(record -> record.getCustomAppId().equals(customAppId))
                .collect(Collectors.toMap(CustomAppRecord::getId, Function.identity()));
        List<CustomAppRecord> ordered = ids.getContent().stream()
                .map(byId::get)
                .collect(Collectors.toCollection(ArrayList::new));
        Map<UUID, Map<String, JsonNode>> values = valuesByRecordId(ids.getContent());
        List<CustomAppRecordResponse> content = ordered.stream()
                .map(record -> toResponse(record, values.getOrDefault(record.getId(), Map.of())))
                .toList();
        return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
    }

    // ── writes ───────────────────────────────────────────────────────────

    @Transactional
    @AuditLog(action = "custom_app_record_created", entityType = "CustomAppRecord", entityId = "#result.id", entityName = "#customApp.name")
    public CustomAppRecordResponse create(UUID customAppId, CustomAppRecordRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        planLimitService.assertWithin(recordRepository.countByCustomAppId(customAppId),
                planLimitService.maxRecordsPerCustomApp(), "records in this custom app");
        List<CustomAppProperty> properties = properties(customAppId);
        Map<String, JsonNode> validated = validatedCreateValues(properties, request.values());

        CustomAppRecord record = new CustomAppRecord();
        record.setCustomAppId(customAppId);
        CustomAppRecord saved = recordRepository.save(record);
        persistValues(saved.getId(), validated);
        return toResponse(saved, valuesByRecordId(List.of(saved.getId()))
                .getOrDefault(saved.getId(), Map.of()));
    }

    /** PATCH semantics: JSON {@code null} clears (rejected for required), absent keys keep. */
    @Transactional
    @AuditLog(action = "custom_app_record_updated", entityType = "CustomAppRecord", entityId = "#recordId", entityName = "#customApp.name")
    public CustomAppRecordResponse update(UUID customAppId, UUID recordId, CustomAppRecordRequest request) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppRecord record = getRecordOrThrow(customAppId, recordId);
        List<CustomAppProperty> properties = properties(customAppId);
        Map<UUID, CustomAppProperty> byId = CustomAppQueryValidator.byId(properties);

        Map<UUID, JsonNode> incoming = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : requestValues(request).entrySet()) {
            CustomAppProperty property = resolveProperty(byId, entry.getKey());
            incoming.put(property.getId(), entry.getValue());
        }

        Map<UUID, CustomAppRecordValue> existing = valueRepository.findAllByRecordId(recordId).stream()
                .collect(Collectors.toMap(CustomAppRecordValue::getPropertyId, Function.identity()));
        for (Map.Entry<UUID, JsonNode> entry : incoming.entrySet()) {
            CustomAppProperty property = byId.get(entry.getKey());
            JsonNode value = entry.getValue();
            CustomAppRecordValue row = existing.get(entry.getKey());
            if (value == null || value.isNull()) {
                if (property.isRequired()) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
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
                CustomAppRecordValue created = new CustomAppRecordValue();
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
        return toResponse(record, valuesByRecordId(List.of(recordId))
                .getOrDefault(recordId, Map.of()));
    }

    @Transactional
    @AuditLog(action = "custom_app_record_deleted", entityType = "CustomAppRecord", entityId = "#recordId", entityName = "")
    public void delete(UUID customAppId, UUID recordId) {
        CustomApp customApp = getCustomAppOrThrow(customAppId);
        CustomAppRecord record = getRecordOrThrow(customAppId, recordId);
        recordRepository.delete(record);
    }

    // ── value validation & persistence ──────────────────────────────────

    /** Create-time validation: keys resolve, types match, every required property covered. */
    private Map<String, JsonNode> validatedCreateValues(List<CustomAppProperty> properties,
                                                        Map<String, JsonNode> values) {
        Map<UUID, CustomAppProperty> byId = CustomAppQueryValidator.byId(properties);
        Map<String, JsonNode> validated = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            CustomAppProperty property = resolveProperty(byId, entry.getKey());
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                if (property.isRequired()) {
                    throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
                            "Property '" + property.getName() + "' is required and cannot be empty");
                }
                continue;
            }
            valueValidator.validate(property, value);
            validated.put(entry.getKey(), value);
        }
        for (CustomAppProperty property : properties) {
            if (property.getType() != PropertyType.FORMULA && property.isRequired()
                    && !validated.containsKey(property.getId().toString())) {
                throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
                        "Required property '" + property.getName() + "' is missing a value");
            }
        }
        return validated;
    }

    private void persistValues(UUID recordId, Map<String, JsonNode> validated) {
        for (Map.Entry<String, JsonNode> entry : validated.entrySet()) {
            CustomAppRecordValue row = new CustomAppRecordValue();
            row.setRecordId(recordId);
            row.setPropertyId(UUID.fromString(entry.getKey()));
            row.setValue(entry.getValue().toString());
            valueRepository.save(row);
        }
    }

    private CustomAppProperty resolveProperty(Map<UUID, CustomAppProperty> byId, String propertyId) {
        UUID id;
        try {
            id = UUID.fromString(propertyId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
                    "Unknown property id: '" + propertyId + "'");
        }
        CustomAppProperty property = byId.get(id);
        if (property == null) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
                    "Property '" + propertyId + "' does not exist in this custom app");
        }
        if (property.getType() == PropertyType.FORMULA) {
            throw new BusinessException(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID,
                    "Property '" + property.getName() + "' is a deferred FORMULA and cannot be set");
        }
        return property;
    }

    // ── lookups & mappers ───────────────────────────────────────────────

    private CustomApp getCustomAppOrThrow(UUID id) {
        return customAppRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomApp not found: " + id));
    }

    private CustomAppRecord getRecordOrThrow(UUID customAppId, UUID recordId) {
        return recordRepository.findByIdAndCustomAppId(recordId, customAppId)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found: " + recordId));
    }

    private List<CustomAppProperty> properties(UUID customAppId) {
        return propertyRepository.findAllByCustomAppIdOrderByPositionAscNameAsc(customAppId);
    }

    private static Map<String, JsonNode> requestValues(CustomAppRecordRequest request) {
        return request.values() == null ? Map.of() : request.values();
    }

    /** Bulk value fetch for a page of records — one query, grouped by record id (no N+1). */
    private Map<UUID, Map<String, JsonNode>> valuesByRecordId(List<UUID> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, JsonNode>> grouped = new HashMap<>();
        for (CustomAppRecordValue row : valueRepository.findAllByRecordIdIn(recordIds)) {
            grouped.computeIfAbsent(row.getRecordId(), id -> new LinkedHashMap<>())
                    .put(row.getPropertyId().toString(), objectMapper.readTree(row.getValue()));
        }
        return grouped;
    }

    private Page<CustomAppRecordResponse> toResponsePage(Page<CustomAppRecord> page) {
        Map<UUID, Map<String, JsonNode>> values = valuesByRecordId(
                page.getContent().stream().map(CustomAppRecord::getId).toList());
        List<CustomAppRecordResponse> content = page.getContent().stream()
                .map(record -> toResponse(record, values.getOrDefault(record.getId(), Map.of())))
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private CustomAppRecordResponse toResponse(CustomAppRecord record, Map<String, JsonNode> values) {
        return new CustomAppRecordResponse(record.getId(), record.getCustomAppId(), values,
                record.getCreatedDate(), record.getUpdatedAt(), record.getCreatedBy());
    }
}
