package com.ibrhalil.forgesys.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyMaskingTest {

    private final RequestBodyCaptureFilter filter = new RequestBodyCaptureFilter(
            List.of("password", "token", "secret", "credential", "authorization", "apiKey", "accessKey", "clientSecret"),
            List.of("/api/v1/users/**")
    );

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void masksPasswordField() throws Exception {
        String input = "{\"username\":\"test\",\"password\":\"secret123\"}";
        String masked = invokeMasking(input);
        assertThat(masked).isEqualTo("{\"username\":\"test\",\"password\":\"[REDACTED]\"}");
    }

    @Test
    void masksTokenField() throws Exception {
        String input = "{\"access_token\":\"abc123\",\"refresh_token\":\"def456\"}";
        String masked = invokeMasking(input);
        assertThat(masked).contains("\"access_token\":\"[REDACTED]\"");
        assertThat(masked).contains("\"refresh_token\":\"[REDACTED]\"");
    }

    @Test
    void masksSecretField() throws Exception {
        String input = "{\"client_secret\":\"shhh\",\"apiKey\":\"key123\"}";
        String masked = invokeMasking(input);
        assertThat(masked).contains("\"client_secret\":\"[REDACTED]\"");
        assertThat(masked).contains("\"apiKey\":\"[REDACTED]\"");
    }

    @Test
    void masksNestedObjects() throws Exception {
        String input = "{\"user\":{\"password\":\"nested\",\"profile\":{\"secret\":\"deep\"}}}";
        String masked = invokeMasking(input);
        assertThat(masked).contains("\"password\":\"[REDACTED]\"");
        assertThat(masked).contains("\"secret\":\"[REDACTED]\"");
    }

    @Test
    void masksArraysOfObjects() throws Exception {
        String input = "{\"users\":[{\"password\":\"pass1\"},{\"password\":\"pass2\"}]}";
        String masked = invokeMasking(input);
        assertThat(masked).contains("\"password\":\"[REDACTED]\"");
        // Both array elements should be masked
        int count = masked.split("\\[REDACTED\\]").length - 1;
        assertThat(count).isEqualTo(2);
    }

    @Test
    void caseInsensitiveKeyMatch() throws Exception {
        String input = "{\"PASSWORD\":\"upper\",\"Password\":\"mixed\",\"password\":\"lower\"}";
        String masked = invokeMasking(input);
        assertThat(masked).contains("\"PASSWORD\":\"[REDACTED]\"");
        assertThat(masked).contains("\"Password\":\"[REDACTED]\"");
        assertThat(masked).contains("\"password\":\"[REDACTED]\"");
    }

    @Test
    void nonMatchingKeysPreserved() throws Exception {
        String input = "{\"username\":\"john\",\"email\":\"john@example.com\",\"age\":30}";
        String masked = invokeMasking(input);
        assertThat(masked).isEqualTo(input);
    }

    @Test
    void handlesInvalidJsonGracefully() throws Exception {
        String input = "not json";
        String masked = invokeMasking(input);
        assertThat(masked).isEqualTo("[MASKING_FAILED]");
    }

    @Test
    void handlesEmptyBody() throws Exception {
        String input = "";
        String masked = invokeMasking(input);
        assertThat(masked).isEqualTo("[MASKING_FAILED]");
    }

    private String invokeMasking(String jsonBody) throws Exception {
        // Use reflection to call the private maskSensitiveFields method
        var method = RequestBodyCaptureFilter.class.getDeclaredMethod("maskSensitiveFields", String.class);
        method.setAccessible(true);
        return (String) method.invoke(filter, jsonBody);
    }
}