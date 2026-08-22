package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.AppRecordRequest;
import com.ibrhalil.forgesys.entity.App;
import com.ibrhalil.forgesys.entity.AppProperty;
import com.ibrhalil.forgesys.entity.AppRecord;
import com.ibrhalil.forgesys.entity.AppRecordValue;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.AppPropertyRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRecordRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRecordValueRepository;
import com.ibrhalil.forgesys.persistence.repository.AppRepository;
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
class AppRecordServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private AppRepository appRepository;
    @Mock private AppPropertyRepository propertyRepository;
    @Mock private AppRecordRepository recordRepository;
    @Mock private AppRecordValueRepository valueRepository;
    @Mock private AppRecordSearchExecutor searchExecutor;
    @Mock private AppPropertyValueValidator valueValidator;
    @Mock private PlanLimitService planLimitService;
    @Mock private AuditService auditService;

    private AppRecordService service;
    private UUID appId;
    private AppProperty requiredText;
    private AppProperty optionalNumber;

    @BeforeEach
    void setUp() {
        service = new AppRecordService(appRepository, propertyRepository, recordRepository,
                valueRepository, searchExecutor, valueValidator, new AppQueryValidator(),
                planLimitService, auditService, JSON);
        appId = UUID.randomUUID();
        requiredText = property(PropertyType.TEXT, true);
        optionalNumber = property(PropertyType.NUMBER, false);
        lenient().when(appRepository.findById(appId)).thenReturn(Optional.of(new App()));
        lenient().when(propertyRepository.findAllByAppIdOrderByPositionAscNameAsc(appId))
                .thenReturn(List.of(requiredText, optionalNumber));
    }

    @Test
    void create_missingRequiredProperty_rejects() {
        assertThatThrownBy(() -> service.create(appId,
                new AppRecordRequest(Map.of(optionalNumber.getId().toString(), JSON.readTree("5")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_RECORD_VALUE_INVALID);
        verify(recordRepository, never()).save(any(AppRecord.class));
    }

    @Test
    void create_unknownPropertyId_rejects() {
        String unknown = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.create(appId,
                new AppRecordRequest(Map.of(
                        requiredText.getId().toString(), JSON.readTree("\"x\""),
                        unknown, JSON.readTree("\"y\"")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_RECORD_VALUE_INVALID);
    }

    @Test
    void create_satisfyingRequired_savesRecordAndValues() {
        when(planLimitService.maxRecordsPerApp()).thenReturn(100L);
        when(recordRepository.countByAppId(appId)).thenReturn(5L);
        UUID recordId = UUID.randomUUID();
        when(recordRepository.save(any(AppRecord.class))).thenAnswer(inv -> {
            AppRecord record = inv.getArgument(0);
            record.setId(recordId);
            return record;
        });
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of());

        service.create(appId, new AppRecordRequest(Map.of(
                requiredText.getId().toString(), JSON.readTree("\"x\""),
                optionalNumber.getId().toString(), JSON.readTree("7"))));

        verify(planLimitService).assertWithin(5L, 100L, "records in this app");
        verify(recordRepository).save(any(AppRecord.class));
        // Explicit null on an optional property is treated as absent (no row).
        verify(valueRepository).save(org.mockito.ArgumentMatchers.argThat(
                (AppRecordValue v) -> v.getRecordId().equals(recordId)
                        && v.getPropertyId().equals(requiredText.getId())
                        && "\"x\"".equals(v.getValue())));
        verify(valueRepository).save(org.mockito.ArgumentMatchers.argThat(
                (AppRecordValue v) -> v.getPropertyId().equals(optionalNumber.getId())
                        && "7".equals(v.getValue())));
    }

    @Test
    void create_planLimitReached_propagates() {
        when(recordRepository.countByAppId(appId)).thenReturn(1000L);
        when(planLimitService.maxRecordsPerApp()).thenReturn(1000L);
        doThrow(new BusinessException(ErrorCode.APP_LIMIT_REACHED, "limit"))
                .when(planLimitService).assertWithin(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), any(String.class));

        assertThatThrownBy(() -> service.create(appId, new AppRecordRequest(Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_LIMIT_REACHED);
        verify(recordRepository, never()).save(any(AppRecord.class));
    }

    @Test
    void update_overwritesExistingRowAndInsertsNew() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndAppId(recordId, appId))
                .thenReturn(Optional.of(record(recordId)));
        AppRecordValue existing = new AppRecordValue();
        existing.setId(UUID.randomUUID());
        existing.setRecordId(recordId);
        existing.setPropertyId(requiredText.getId());
        existing.setValue("\"old\"");
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of(existing));
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of(existing));

        service.update(appId, recordId, new AppRecordRequest(Map.of(
                requiredText.getId().toString(), JSON.readTree("\"new\""),
                optionalNumber.getId().toString(), JSON.readTree("3"))));

        assertThat(existing.getValue()).isEqualTo("\"new\"");
        verify(valueRepository).save(existing);
        verify(valueRepository).save(org.mockito.ArgumentMatchers.argThat(
                (AppRecordValue v) -> v.getPropertyId().equals(optionalNumber.getId())));
    }

    @Test
    void update_nullClearsOptionalButNotRequired() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndAppId(recordId, appId))
                .thenReturn(Optional.of(record(recordId)));
        AppRecordValue row = new AppRecordValue();
        row.setRecordId(recordId);
        row.setPropertyId(optionalNumber.getId());
        row.setValue("7");
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of(row));
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of());

        service.update(appId, recordId, new AppRecordRequest(Map.of(
                optionalNumber.getId().toString(), JSON.readTree("null"))));
        verify(valueRepository).delete(row);

        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of());
        AppRecordValue requiredRow = new AppRecordValue();
        requiredRow.setRecordId(recordId);
        requiredRow.setPropertyId(requiredText.getId());
        requiredRow.setValue("\"x\"");
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of(requiredRow));
        assertThatThrownBy(() -> service.update(appId, recordId, new AppRecordRequest(Map.of(
                requiredText.getId().toString(), JSON.readTree("null")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APP_RECORD_VALUE_INVALID);
        verify(valueRepository, never()).delete(requiredRow);
    }

    @Test
    void update_absentKeysAreKept() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndAppId(recordId, appId))
                .thenReturn(Optional.of(record(recordId)));
        when(valueRepository.findAllByRecordId(recordId)).thenReturn(List.of());
        when(valueRepository.findAllByRecordIdIn(List.of(recordId))).thenReturn(List.of());

        service.update(appId, recordId, new AppRecordRequest(Map.of(
                optionalNumber.getId().toString(), JSON.readTree("3"))));

        verify(valueRepository, never()).delete(any(AppRecordValue.class));
        verify(valueRepository).save(any(AppRecordValue.class));
    }

    @Test
    void update_recordOfAnotherApp_yields404() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndAppId(recordId, appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(appId, recordId, new AppRecordRequest(Map.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_recordOfAnotherApp_yields404() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndAppId(recordId, appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(appId, recordId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(recordRepository, never()).delete(any(AppRecord.class));
    }

    // --- helpers ---------------------------------------------------------

    private AppProperty property(PropertyType type, boolean required) {
        AppProperty property = new AppProperty();
        property.setId(UUID.randomUUID());
        property.setAppId(appId);
        property.setName("p-" + type.name().toLowerCase());
        property.setType(type);
        property.setRequired(required);
        return property;
    }

    private AppRecord record(UUID id) {
        AppRecord record = new AppRecord();
        record.setId(id);
        record.setAppId(appId);
        return record;
    }

    @SuppressWarnings("unused")
    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }
}
