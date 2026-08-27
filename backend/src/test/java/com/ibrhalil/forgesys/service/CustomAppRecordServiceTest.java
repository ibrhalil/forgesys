package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.CustomAppRecordRequest;
import com.ibrhalil.forgesys.entity.CustomApp;
import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.CustomAppRecord;
import com.ibrhalil.forgesys.entity.CustomAppRecordValue;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.CustomAppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRecordRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRecordValueRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the record service (K-15 / Epic 3.0.B): required coverage on create,
 * unknown-property rejection, PATCH merge semantics (overwrite / clear / keep), plan
 * limit delegation and nested cross-app 404 scoping. The value validator and the
 * search executor are mocked (their logic has dedicated tests).
 */
@ExtendWith(MockitoExtension.class)
class CustomAppRecordServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private CustomAppRepository customAppRepository;
    @Mock private CustomAppPropertyRepository propertyRepository;
    @Mock private CustomAppRecordRepository recordRepository;
    @Mock private CustomAppRecordValueRepository valueRepository;
    @Mock private CustomAppRecordSearchExecutor searchExecutor;
    @Mock private CustomAppPropertyValueValidator valueValidator;
    @Mock private PlanLimitService planLimitService;
    @Mock private AuditService auditService;

    private CustomAppRecordService service;
    private UUID customAppId;
    private CustomAppProperty requiredText;
    private CustomAppProperty optionalNumber;

    @BeforeEach
    void setUp() {
        service = new CustomAppRecordService(customAppRepository, propertyRepository, recordRepository,
                valueRepository, searchExecutor, valueValidator, new CustomAppQueryValidator(),
                planLimitService, auditService, JSON);
        customAppId = UUID.randomUUID();
        requiredText = property(PropertyType.TEXT, true);
        optionalNumber = property(PropertyType.NUMBER, false);
        lenient().when(customAppRepository.findById(customAppId)).thenReturn(Optional.of(new CustomApp()));
        lenient().when(propertyRepository.findAllByCustomAppIdOrderByPositionAscNameAsc(customAppId))
                .thenReturn(List.of(requiredText, optionalNumber));
    }

    @Test
    void create_missingRequiredProperty_rejects() {
        assertThatThrownBy(() -> service.create(customAppId,
                new CustomAppRecordRequest(Map.of(optionalNumber.getId().toString(), JSON.readTree("5")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
        verify(recordRepository, never()).save(any(CustomAppRecord.class));
    }

    @Test
    void create_unknownPropertyId_rejects() {
        String unknown = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.create(customAppId,
                new CustomAppRecordRequest(Map.of(
                        requiredText.getId().toString(), JSON.readTree("\"x\""),
                        unknown, JSON.readTree("\"y\"")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void create_satisfyingRequired_savesRecordAndValues() {
        when(planLimitService.maxRecordsPerCustomApp()).thenReturn(100L);
        when(recordRepository.countByCustomAppId(customAppId)).thenReturn(5L);
        UUID recordId = UUID.randomUUID();
        when(recordRepository.save(any(CustomAppRecord.class))).thenAnswer(inv -> {
            CustomAppRecord record = inv.getArgument(0);
            record.setId(recordId);
            return record;
        });
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of());

        service.create(customAppId, new CustomAppRecordRequest(Map.of(
                requiredText.getId().toString(), JSON.readTree("\"x\""),
                optionalNumber.getId().toString(), JSON.readTree("7"))));

        verify(planLimitService).assertWithin(5L, 100L, "records in this custom app");
        verify(recordRepository).save(any(CustomAppRecord.class));
        // Explicit null on an optional property is treated as absent (no row).
        verify(valueRepository).save(org.mockito.ArgumentMatchers.argThat(
                (CustomAppRecordValue v) -> v.getRecordId().equals(recordId)
                        && v.getPropertyId().equals(requiredText.getId())
                        && "\"x\"".equals(v.getValue())));
        verify(valueRepository).save(org.mockito.ArgumentMatchers.argThat(
                (CustomAppRecordValue v) -> v.getPropertyId().equals(optionalNumber.getId())
                        && "7".equals(v.getValue())));
    }

    @Test
    void create_planLimitReached_propagates() {
        when(recordRepository.countByCustomAppId(customAppId)).thenReturn(1000L);
        when(planLimitService.maxRecordsPerCustomApp()).thenReturn(1000L);
        doThrow(new BusinessException(ErrorCode.CUSTOM_APP_LIMIT_REACHED, "limit"))
                .when(planLimitService).assertWithin(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), any(String.class));

        assertThatThrownBy(() -> service.create(customAppId, new CustomAppRecordRequest(Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_LIMIT_REACHED);
        verify(recordRepository, never()).save(any(CustomAppRecord.class));
    }

    @Test
    void update_overwritesExistingRowAndInsertsNew() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndCustomAppId(recordId, customAppId))
                .thenReturn(Optional.of(record(recordId)));
        CustomAppRecordValue existing = new CustomAppRecordValue();
        existing.setId(UUID.randomUUID());
        existing.setRecordId(recordId);
        existing.setPropertyId(requiredText.getId());
        existing.setValue("\"old\"");
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of(existing));
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of(existing));

        service.update(customAppId, recordId, new CustomAppRecordRequest(Map.of(
                requiredText.getId().toString(), JSON.readTree("\"new\""),
                optionalNumber.getId().toString(), JSON.readTree("3"))));

        assertThat(existing.getValue()).isEqualTo("\"new\"");
        verify(valueRepository).save(existing);
        verify(valueRepository).save(org.mockito.ArgumentMatchers.argThat(
                (CustomAppRecordValue v) -> v.getPropertyId().equals(optionalNumber.getId())));
    }

    @Test
    void update_nullClearsOptionalButNotRequired() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndCustomAppId(recordId, customAppId))
                .thenReturn(Optional.of(record(recordId)));
        CustomAppRecordValue row = new CustomAppRecordValue();
        row.setRecordId(recordId);
        row.setPropertyId(optionalNumber.getId());
        row.setValue("7");
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of(row));
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of());

        service.update(customAppId, recordId, new CustomAppRecordRequest(Map.of(
                optionalNumber.getId().toString(), JSON.readTree("null"))));
        verify(valueRepository).delete(row);

        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of());
        CustomAppRecordValue requiredRow = new CustomAppRecordValue();
        requiredRow.setRecordId(recordId);
        requiredRow.setPropertyId(requiredText.getId());
        requiredRow.setValue("\"x\"");
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of(requiredRow));
        assertThatThrownBy(() -> service.update(customAppId, recordId, new CustomAppRecordRequest(Map.of(
                requiredText.getId().toString(), JSON.readTree("null")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
        verify(valueRepository, never()).delete(requiredRow);
    }

    @Test
    void update_absentKeysAreKept() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndCustomAppId(recordId, customAppId))
                .thenReturn(Optional.of(record(recordId)));
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of());
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of());

        service.update(customAppId, recordId, new CustomAppRecordRequest(Map.of(
                optionalNumber.getId().toString(), JSON.readTree("3"))));

        verify(valueRepository, never()).delete(any(CustomAppRecordValue.class));
        verify(valueRepository).save(any(CustomAppRecordValue.class));
    }

    @Test
    void update_recordOfAnotherApp_yields404() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndCustomAppId(recordId, customAppId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(customAppId, recordId, new CustomAppRecordRequest(Map.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_recordOfAnotherApp_yields404() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndCustomAppId(recordId, customAppId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(customAppId, recordId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(recordRepository, never()).delete(any(CustomAppRecord.class));
    }

    // --- helpers ---------------------------------------------------------

    private CustomAppProperty property(PropertyType type, boolean required) {
        CustomAppProperty property = new CustomAppProperty();
        property.setId(UUID.randomUUID());
        property.setCustomAppId(customAppId);
        property.setName("p-" + type.name().toLowerCase());
        property.setType(type);
        property.setRequired(required);
        return property;
    }

    private CustomAppRecord record(UUID id) {
        CustomAppRecord record = new CustomAppRecord();
        record.setId(id);
        record.setCustomAppId(customAppId);
        return record;
    }

    @SuppressWarnings("unused")
    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }
}
