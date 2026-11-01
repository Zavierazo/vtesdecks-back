package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import com.vtesdecks.integration.PatreonMember.Decision;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatreonWebhookServiceTest {
    @Mock private UserRepository users;
    @Mock private PatreonSupporterService supporters;
    private PatreonWebhookService service;

    @BeforeEach
    void setup() {
        var configuration = mock(PatreonWebhookConfiguration.class);
        when(configuration.getCampaignId()).thenReturn("campaign");
        service = new PatreonWebhookService(new ObjectMapper(), users, supporters, configuration);
    }

    @Test
    void prioritizesLinkedIdOverAnyEmail() throws Exception {
        when(users.findUserIdByPatreonId("123")).thenReturn(Optional.of(42));
        process("Paid", 500, "active_patron", "other@example.com");
        verify(supporters).apply(42, "123", Decision.GRANT);
        verify(users, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void linksPaidUserFoundByEmailWithoutOverwritingAnExistingId() throws Exception {
        var user = new UserEntity(); user.setId(42);
        when(users.findByEmailIgnoreCase("patron@example.com")).thenReturn(user);
        when(users.lockPatreonAccount(42)).thenReturn(Optional.of(account(null)));
        process("Paid", 500, "active_patron", "patron@example.com");
        verify(users).updatePatreonId(42, "123");
        verify(supporters).apply(42, "123", Decision.GRANT);
    }

    @Test
    void preservesConflictingIdentityAndRole() throws Exception {
        var user = new UserEntity(); user.setId(42);
        when(users.findByEmailIgnoreCase("patron@example.com")).thenReturn(user);
        when(users.lockPatreonAccount(42)).thenReturn(Optional.of(account("999")));
        process("Paid", 500, "active_patron", "patron@example.com");
        verify(users, never()).updatePatreonId(anyInt(), anyString());
        verifyNoInteractions(supporters);
    }

    @Test
    void cancelledRenewalKeepsThePaidPeriodAndExpiryRemovesOnlyTheRole() throws Exception {
        when(users.findUserIdByPatreonId("123")).thenReturn(Optional.of(42));
        process("Paid", 500, "former_patron", "");
        process("Paid", 0, "former_patron", "");
        verify(supporters).apply(42, "123", Decision.GRANT);
        verify(supporters).apply(42, "123", Decision.REMOVE);
        verify(users, never()).updatePatreonId(anyInt(), any());
    }

    @Test
    void declinedChargeRemovesSupporterEvenWithHistoricalPayments() throws Exception {
        when(users.findUserIdByPatreonId("123")).thenReturn(Optional.of(42));
        process("Declined", 500, "declined_patron", "");
        verify(supporters).apply(42, "123", Decision.REMOVE);
    }

    @Test
    void doesNotLinkOrRemoveAnUnknownSupporterByEmailOnCancellation() throws Exception {
        process("Paid", 0, "former_patron", "patron@example.com");
        verify(users, never()).findByEmailIgnoreCase(anyString());
        verifyNoInteractions(supporters);
    }

    @Test
    void incompletePaymentInformationKeepsTheRole() throws Exception {
        when(users.findUserIdByPatreonId("123")).thenReturn(Optional.of(42));
        service.process("members:update", payload("null", "null", "null", "").getBytes(StandardCharsets.UTF_8));
        verify(supporters).apply(42, "123", Decision.KEEP);
    }

    @Test
    void acceptsEmailFromIncludedUser() throws Exception {
        var user = new UserEntity(); user.setId(42);
        when(users.findByEmailIgnoreCase("included@example.com")).thenReturn(user);
        when(users.lockPatreonAccount(42)).thenReturn(Optional.of(account(null)));
        String body = payload("\"Paid\"", "500", "\"active_patron\"", "");
        body = body.substring(0, body.lastIndexOf('}'))
                + ",\"included\":[{\"type\":\"user\",\"id\":\"123\",\"attributes\":{\"email\":\"included@example.com\"}}]}";
        service.process("members:update", body.getBytes(StandardCharsets.UTF_8));
        verify(users).updatePatreonId(42, "123");
    }

    @Test
    void ignoresOtherCampaignWithoutLookingUpAnyAccount() throws Exception {
        service.process("members:update", payload("null", "0", "null", "").replace("campaign", "other").getBytes(StandardCharsets.UTF_8));
        verifyNoInteractions(users, supporters);
    }

    @Test
    void neverGrantsOnFreeTrialAndProcessesDeletion() throws Exception {
        when(users.findUserIdByPatreonId("123")).thenReturn(Optional.of(42));
        service.process("members:update", payload("\"Paid\"", "500", "null", "")
                .replace("\"campaign_lifetime_support_cents\":500", "\"is_free_trial\":true").getBytes(StandardCharsets.UTF_8));
        verify(supporters).apply(42, "123", Decision.KEEP);
        service.process("members:delete", payload("null", "null", "null", "").getBytes(StandardCharsets.UTF_8));
        verify(supporters).apply(42, "123", Decision.REMOVE);
    }

    private void process(String charge, int amount, String status, String email) throws Exception {
        service.process("members:update", payload("\"" + charge + "\"", Integer.toString(amount), "\"" + status + "\"", email).getBytes(StandardCharsets.UTF_8));
    }

    private String payload(String charge, String amount, String status, String email) {
        return """
                {"data":{"type":"member","id":"member-1","attributes":{
                "email":"%s","last_charge_status":%s,"currently_entitled_amount_cents":%s,
                "patron_status":%s,"campaign_lifetime_support_cents":500},
                "relationships":{"user":{"data":{"id":"123","type":"user"}},
                "campaign":{"data":{"id":"campaign","type":"campaign"}}}}}
                """.formatted(email, charge, amount, status);
    }

    private UserRepository.PatreonAccount account(String patreonId) {
        return new UserRepository.PatreonAccount() {
            public Integer getId() { return 42; }
            public String getPatreonId() { return patreonId; }
        };
    }
}
