package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.entity.LoginHistory;
import com.ibrhalil.forgesys.persistence.repository.LoginHistoryRepository;
import com.ibrhalil.forgesys.web.RequestContext;
import com.ibrhalil.forgesys.web.RequestMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginHistoryServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @InjectMocks
    private LoginHistoryService service;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void recordPopulatesEntryFromRequestContextAndSaves() {
        UUID userId = UUID.randomUUID();
        RequestContext.set(new RequestMeta("trace-1", "203.0.113.9", "Mozilla/5.0"));

        service.record(userId, "user@example.com", true, null);

        ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(captor.capture());
        LoginHistory saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals("user@example.com", saved.getUsername());
        assertTrue(saved.isSuccess());
        assertNull(saved.getReason());
        assertEquals("203.0.113.9", saved.getIpAddress());
        assertEquals("Mozilla/5.0", saved.getUserAgent());
    }

    @Test
    void recordLeavesIpAndUserAgentNullWhenNoRequestContext() {
        service.record(null, "ghost@example.com", false, "auth_bad_credentials");

        ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(captor.capture());
        LoginHistory saved = captor.getValue();
        assertNull(saved.getUserId());
        assertEquals("ghost@example.com", saved.getUsername());
        assertFalse(saved.isSuccess());
        assertEquals("auth_bad_credentials", saved.getReason());
        assertNull(saved.getIpAddress());
        assertNull(saved.getUserAgent());
    }

    @Test
    void recordSwallowsRepositoryFailureAndNeverRethrows() {
        when(loginHistoryRepository.save(any(LoginHistory.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record(UUID.randomUUID(), "x@example.com", false, "auth_bad_credentials"));

        verify(loginHistoryRepository).save(any(LoginHistory.class));
    }
}
