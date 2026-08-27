package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.service.PlatformAuditQueryService;
import com.ibrhalil.forgesys.service.PlatformMailTestService;
import com.ibrhalil.forgesys.service.mail.InMemoryMailSender;
import com.ibrhalil.forgesys.service.mail.MailTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-51 platform mail testing: gates (platform scope + {@code platform:mail:test}),
 * info surface, no-send preview rendering and the test-send delivery through the
 * test-profile {@link InMemoryMailSender} + platform audit entry.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformMailControllerTest extends AbstractRbacWebTest {

    private static final String BASE = "/api/v1/platform/mail";

    @Autowired
    InMemoryMailSender inMemoryMailSender;

    @Autowired
    PlatformAuditQueryService platformAuditQueryService;

    @AfterEach
    void clearDeliveredMails() {
        inMemoryMailSender.clear();
    }

    /* ── auth / permission gates ── */

    @Test
    void infoRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsForbiddenWithoutMailTest() throws Exception {
        var cookie = authPlatform(UUID.randomUUID(), "reader@platform.test",
                java.util.List.of("platform:company:read"));
        mockMvc.perform(get(BASE + "/info").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/preview").contentType(APPLICATION_JSON)
                        .content("{\"template\":\"TENANT_VERIFY\"}").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/test-send").contentType(APPLICATION_JSON)
                        .content("{\"recipient\":\"dest@example.com\",\"template\":\"TENANT_VERIFY\"}")
                        .cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /** RISK-18 posture: a tenant JWT never reaches the platform surface, legacy authority or not. */
    @Test
    void endpointsForbiddenForTenantJwtEvenWithAuthority() throws Exception {
        var cookie = auth("admin@tenant.test", "platform:mail:test");
        mockMvc.perform(get(BASE + "/info").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── info ── */

    @Test
    void infoReturnsActiveConfigAndTemplateCatalog() throws Exception {
        mockMvc.perform(get(BASE + "/info").cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("IN_MEMORY"))
                .andExpect(jsonPath("$.from").isNotEmpty())
                .andExpect(jsonPath("$.defaultLanguage").value("tr"))
                .andExpect(jsonPath("$.templates.length()").value(MailTemplate.values().length))
                .andExpect(jsonPath("$.templates[0].name").value("TENANT_VERIFY"))
                .andExpect(jsonPath("$.templates[0].key").value("tenant-verify"));
    }

    /* ── preview ── */

    @Test
    void previewRendersRequestedLanguageAndSampleData() throws Exception {
        mockMvc.perform(post(BASE + "/preview").contentType(APPLICATION_JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("""
                                {"template":"TENANT_VERIFY","language":"en",
                                 "firstName":"Ada","organizationName":"Acme Inc.",
                                 "actionUrl":"https://app.example.com/verify?token=x",
                                 "expiresInHours":48}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("ForgeSys — verify your organization"))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.containsString("Ada")))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.containsString("Acme Inc.")))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("{{"))));
    }

    @Test
    void previewDefaultsToConfiguredLanguageAndSampleValues() throws Exception {
        mockMvc.perform(post(BASE + "/preview").contentType(APPLICATION_JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("{\"template\":\"PASSWORD_RESET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("ForgeSys — şifre sıfırlama"))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.containsString("ForgeSys Test")))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.containsString("mail-test.invalid")));
    }

    @Test
    void previewRejectsUnknownTemplate() throws Exception {
        mockMvc.perform(post(BASE + "/preview").contentType(APPLICATION_JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("{\"template\":\"NO_SUCH_TEMPLATE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewRejectsInvalidLanguage() throws Exception {
        mockMvc.perform(post(BASE + "/preview").contentType(APPLICATION_JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("{\"template\":\"TENANT_VERIFY\",\"language\":\"xx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── test-send ── */

    @Test
    void testSendDeliversThroughActiveChannelAndAudits() throws Exception {
        UUID actorId = UUID.randomUUID();
        mockMvc.perform(post(BASE + "/test-send").contentType(APPLICATION_JSON)
                        .cookie(authPlatform(actorId, "root@platform.test"))
                        .content("""
                                {"recipient":"dest@example.com","template":"PASSWORD_RESET",
                                 "language":"tr","firstName":"Ayşe"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("IN_MEMORY"))
                .andExpect(jsonPath("$.recipient").value("dest@example.com"))
                .andExpect(jsonPath("$.template").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$.language").value("tr"));

        assertThat(inMemoryMailSender.getDelivered()).hasSize(1);
        var delivered = inMemoryMailSender.getDelivered().get(0);
        assertThat(delivered.recipient()).isEqualTo("dest@example.com");
        assertThat(delivered.template()).isEqualTo(MailTemplate.PASSWORD_RESET);
        assertThat(delivered.language()).isEqualTo("tr");
        assertThat(delivered.firstName()).isEqualTo("Ayşe");

        // REQUIRES_NEW audit write commits outside the test tx — unique actorId sentinel.
        assertThat(platformAuditQueryService.findAll(
                        PageRequest.of(0, 10), null, null, PlatformMailTestService.ACTION_MAIL_TEST_SENT,
                        actorId, null, null, null).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void testSendRejectsInvalidRecipient() throws Exception {
        mockMvc.perform(post(BASE + "/test-send").contentType(APPLICATION_JSON)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test"))
                        .content("{\"recipient\":\"not-an-email\",\"template\":\"TENANT_VERIFY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }
}
