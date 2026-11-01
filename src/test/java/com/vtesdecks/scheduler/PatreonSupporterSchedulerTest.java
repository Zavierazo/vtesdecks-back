package com.vtesdecks.scheduler;

import com.vtesdecks.api.service.PatreonSupporterService;
import com.vtesdecks.api.service.PatreonMembershipService;
import com.vtesdecks.integration.PatreonMember;
import com.vtesdecks.integration.PatreonMember.Decision;
import com.vtesdecks.jpa.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatreonSupporterSchedulerTest {
    @Test
    void onlyManagesAlreadyLinkedAccountsAndNeverMatchesByEmail() {
        var client = mock(PatreonMembershipService.class);
        var users = mock(UserRepository.class);
        var supporters = mock(PatreonSupporterService.class);
        when(client.available()).thenReturn(true);
        when(users.findPatreonAccounts()).thenReturn(List.of(account(1, "123"), account(2, "456"), account(3, null)));
        when(client.members()).thenReturn(Map.of(
                "123", new PatreonMember("m1", "123", null, Decision.GRANT),
                "999", new PatreonMember("m9", "999", "unknown-supporter@example.com", Decision.REMOVE)));
        new PatreonSupporterScheduler(client, users, supporters).reconcile();
        verify(supporters).apply(1, "123", Decision.GRANT);
        verify(supporters).apply(2, "456", Decision.REMOVE);
        verifyNoMoreInteractions(supporters);
        verify(users, never()).findByEmailIgnoreCase(anyString());
        verify(users, never()).updatePatreonId(anyInt(), any());
    }

    @Test
    void incompleteDownloadNeverChangesAnyRole() {
        var client = mock(PatreonMembershipService.class);
        var users = mock(UserRepository.class);
        var supporters = mock(PatreonSupporterService.class);
        when(client.available()).thenReturn(true);
        when(users.findPatreonAccounts()).thenReturn(List.of(account(1, "123")));
        when(client.members()).thenThrow(new IllegalStateException("Partial page"));
        assertThrows(IllegalStateException.class, () -> new PatreonSupporterScheduler(client, users, supporters).reconcile());
        verifyNoInteractions(supporters);
    }

    @Test
    void missingCredentialsNeverReadAccountsOrChangeRoles() {
        var client = mock(PatreonMembershipService.class);
        var users = mock(UserRepository.class);
        var supporters = mock(PatreonSupporterService.class);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new PatreonSupporterScheduler(client, users, supporters).reconcile());
        verifyNoInteractions(users, supporters);
    }

    private UserRepository.PatreonAccount account(int id, String patreonId) {
        return new UserRepository.PatreonAccount() {
            public Integer getId() { return id; }
            public String getPatreonId() { return patreonId; }
        };
    }
}
