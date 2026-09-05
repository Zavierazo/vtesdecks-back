package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatreonWebhookServiceTest {

    private static final String CAMPAIGN_ID = "41542528";

    @Mock
    private PatreonWebhookConfiguration configuration;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    private PatreonWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PatreonWebhookService(new ObjectMapper(), configuration, userRepository, jdbcTemplate);
        when(configuration.getCampaignId()).thenReturn(CAMPAIGN_ID);
    }

    @Test
    void assignsSupporterToMatchingVerifiedPaidPatron() throws Exception {
        UserEntity user = user(42, true);
        when(userRepository.findByEmailIgnoreCase("patron@example.com")).thenReturn(user);
        whenAssignment().thenReturn(1);

        service.process(payload(500, "patron@example.com").getBytes(StandardCharsets.UTF_8));

        verifyAssignment();
    }

    @Test
    void acceptsEmailFromIncludedPatreonUser() throws Exception {
        UserEntity user = user(42, true);
        when(userRepository.findByEmailIgnoreCase("included@example.com")).thenReturn(user);

        service.process(payloadWithIncludedEmail(500).getBytes(StandardCharsets.UTF_8));

        verifyAssignment();
    }

    @Test
    void repeatedDeliveryUsesIdempotentAssignment() throws Exception {
        UserEntity user = user(42, true);
        when(userRepository.findByEmailIgnoreCase("patron@example.com")).thenReturn(user);
        whenAssignment().thenReturn(1, 0);
        byte[] body = payload(500, "patron@example.com").getBytes(StandardCharsets.UTF_8);

        service.process(body);
        service.process(body);

        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(anyString(), eq(42), eq("supporter"), eq(42), eq("supporter"));
    }

    @Test
    void ignoresMemberWithoutSuccessfulPayment() throws Exception {
        service.process(payload(0, "patron@example.com").getBytes(StandardCharsets.UTF_8));

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verifyNoAssignment();
    }

    @Test
    void ignoresMissingOrUnmatchedEmail() throws Exception {
        service.process(payload(500, null).getBytes(StandardCharsets.UTF_8));
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(null);
        service.process(payload(500, "unknown@example.com").getBytes(StandardCharsets.UTF_8));

        verifyNoAssignment();
    }

    @Test
    void ignoresUnverifiedMatchingUser() throws Exception {
        when(userRepository.findByEmailIgnoreCase("patron@example.com")).thenReturn(user(42, false));

        service.process(payload(500, "patron@example.com").getBytes(StandardCharsets.UTF_8));

        verifyNoAssignment();
    }

    @Test
    void rejectsUnexpectedCampaign() {
        String body = payload(500, "patron@example.com").replace(CAMPAIGN_ID, "another-campaign");

        assertThrows(IllegalArgumentException.class,
                () -> service.process(body.getBytes(StandardCharsets.UTF_8)));
        verifyNoAssignment();
    }

    @Test
    void acceptsLegacyLifetimeSupportFieldName() throws Exception {
        UserEntity user = user(42, true);
        when(userRepository.findByEmailIgnoreCase("patron@example.com")).thenReturn(user);
        String body = payload(500, "patron@example.com")
                .replace("campaign_lifetime_support_cents", "lifetime_support_cents");

        service.process(body.getBytes(StandardCharsets.UTF_8));

        verifyAssignment();
    }

    private UserEntity user(int id, boolean validated) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setValidated(validated);
        return user;
    }

    private org.mockito.stubbing.OngoingStubbing<Integer> whenAssignment() {
        return when(jdbcTemplate.update(anyString(), eq(42), eq("supporter"), eq(42), eq("supporter")));
    }

    private void verifyAssignment() {
        verify(jdbcTemplate).update(anyString(), eq(42), eq("supporter"), eq(42), eq("supporter"));
    }

    private void verifyNoAssignment() {
        verify(jdbcTemplate, never()).update(anyString(), eq(42), eq("supporter"), eq(42), eq("supporter"));
    }

    private String payload(long lifetimeSupportCents, String email) {
        String emailJson = email == null ? "" : ",\"email\":\"" + email + "\"";
        return """
                {"data":{"type":"member","id":"member-1",
                "attributes":{"campaign_lifetime_support_cents":%d%s},
                "relationships":{"campaign":{"data":{"type":"campaign","id":"%s"}},
                "user":{"data":{"type":"user","id":"patreon-user-1"}}}}}
                """.formatted(lifetimeSupportCents, emailJson, CAMPAIGN_ID);
    }

    private String payloadWithIncludedEmail(long lifetimeSupportCents) {
        String base = payload(lifetimeSupportCents, null);
        return base.substring(0, base.lastIndexOf('}'))
                + ",\"included\":[{\"type\":\"user\",\"id\":\"patreon-user-1\","
                + "\"attributes\":{\"email\":\"included@example.com\"}}]}";
    }
}
