package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.CustomAppProperty;
import com.ibrhalil.forgesys.entity.PropertyType;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRecordRepository;
import com.ibrhalil.forgesys.persistence.repository.CustomAppRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Type-matrix unit tests for the cell-value validator (K-15 / Epic 3.0.B): every
 * {@link PropertyType} accepts its JSON shape and rejects the others; reference types
 * (USER / RELATION) enforce tenant-data existence.
 */
@ExtendWith(MockitoExtension.class)
class CustomAppPropertyValueValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TARGET_APP_ID = UUID.randomUUID();
    private static final UUID TARGET_RECORD_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private CustomAppRepository customAppRepository;
    @Mock private CustomAppRecordRepository customAppRecordRepository;

    private CustomAppPropertyValueValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CustomAppPropertyValueValidator(userRepository, customAppRepository, customAppRecordRepository, JSON);
    }

    @Test
    void text_acceptsBoundedStrings() {
        assertThatCode(() -> validator.validate(property(PropertyType.TEXT, null), node("\"hello\"")))
                .doesNotThrowAnyException();
        String longText = "\"" + "a".repeat(CustomAppPropertyValueValidator.MAX_TEXT_LENGTH + 1) + "\"";
        assertThatThrownBy(() -> validator.validate(property(PropertyType.TEXT, null), node(longText)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void text_rejectsNonString() {
        assertThatThrownBy(() -> validator.validate(property(PropertyType.TEXT, null), node("5")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void number_acceptsIntegersAndDecimals() {
        assertThatCode(() -> validator.validate(property(PropertyType.NUMBER, null), node("5")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(property(PropertyType.NUMBER, null), node("3.14")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(property(PropertyType.NUMBER, null), node("\"5\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void select_acceptsOnlyConfiguredOptions() {
        CustomAppProperty property = property(PropertyType.SELECT, "{\"options\":[\"high\",\"low\"]}");
        assertThatCode(() -> validator.validate(property, node("\"high\""))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(property, node("\"medium\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void select_withoutOptions_reportsInvalidConfig() {
        CustomAppProperty property = property(PropertyType.SELECT, null);
        assertThatThrownBy(() -> validator.validate(property, node("\"high\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void date_acceptsIsoDatesOnly() {
        CustomAppProperty property = property(PropertyType.DATE, null);
        assertThatCode(() -> validator.validate(property, node("\"2026-08-22\"")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(property, node("\"22.08.2026\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void user_validatesExistence() {
        CustomAppProperty property = property(PropertyType.USER, null);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        assertThatCode(() -> validator.validate(property, node("\"" + USER_ID + "\"")))
                .doesNotThrowAnyException();

        when(userRepository.existsById(USER_ID)).thenReturn(false);
        assertThatThrownBy(() -> validator.validate(property, node("\"" + USER_ID + "\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);

        assertThatThrownBy(() -> validator.validate(property, node("\"not-a-uuid\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void relation_validatesTargetRecordInTargetApp() {
        CustomAppProperty property = property(PropertyType.RELATION,
                "{\"targetCustomAppId\":\"" + TARGET_APP_ID + "\"}");
        lenient().when(customAppRepository.existsById(TARGET_APP_ID)).thenReturn(true);
        when(customAppRecordRepository.existsByIdAndCustomAppId(TARGET_RECORD_ID, TARGET_APP_ID)).thenReturn(true);
        assertThatCode(() -> validator.validate(property, node("\"" + TARGET_RECORD_ID + "\"")))
                .doesNotThrowAnyException();

        when(customAppRecordRepository.existsByIdAndCustomAppId(TARGET_RECORD_ID, TARGET_APP_ID)).thenReturn(false);
        assertThatThrownBy(() -> validator.validate(property, node("\"" + TARGET_RECORD_ID + "\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    @Test
    void relation_withoutTargetApp_reportsInvalidConfig() {
        CustomAppProperty property = property(PropertyType.RELATION, null);
        assertThatThrownBy(() -> validator.validate(property, node("\"" + TARGET_RECORD_ID + "\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_PROPERTY_CONFIG_INVALID);
    }

    @Test
    void formula_isNeverSettable() {
        assertThatThrownBy(() -> validator.validate(property(PropertyType.FORMULA, null), node("\"x\"")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CUSTOM_APP_RECORD_VALUE_INVALID);
    }

    // --- helpers ---------------------------------------------------------

    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }

    private static CustomAppProperty property(PropertyType type, String config) {
        CustomAppProperty property = new CustomAppProperty();
        property.setId(UUID.randomUUID());
        property.setCustomAppId(UUID.randomUUID());
        property.setName("p-" + type.name().toLowerCase());
        property.setType(type);
        property.setConfig(config);
        return property;
    }
}
