package com.vtesdecks.scheduler;

import com.vtesdecks.integration.ArchonClient;
import com.vtesdecks.integration.ArchonWebsiteClient;
import com.vtesdecks.jpa.entity.ArchonUserEntity;
import com.vtesdecks.jpa.repositories.ArchonUserRepository;
import com.vtesdecks.model.archon.ArchonAccessToken;
import com.vtesdecks.model.archon.ArchonUser;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchonUserSchedulerTest {
    private static final String EXPORT = "{\"type\":\"user\",\"data\":{\"uid\":\"member-uid\",\"vekn_id\":\"0012345\"}}\n";
    private static final String SNAPSHOT = "{\"type\":\"header\",\"version\":2}\n"
            + "{\"type\":\"tournament\",\"data\":{\"uid\":\"ignored\"}}\n"
            + "{\"type\":\"user\",\"data\":{\"uid\":\"member-uid\",\"name\":\"Hernando Sagardia\","
            + "\"nickname\":\"Nano\",\"city\":\"Barcelona\",\"country\":\"ES\",\"roles\":[\"Prince\",\"Judge\"]}}\n"
            + "{\"type\":\"eof\",\"count\":2}\n";
    @Mock private ArchonClient archonClient;
    @Mock private ArchonWebsiteClient websiteClient;
    @Mock private ArchonUserRepository userRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private ArchonUserScheduler scheduler;

    @BeforeEach
    void configureCredentials() {
        ReflectionTestUtils.setField(scheduler, "clientId", "test-client");
        ReflectionTestUtils.setField(scheduler, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(scheduler, "websiteEmail", "member@example.test");
        ReflectionTestUtils.setField(scheduler, "websitePassword", "website-password");
    }

    @Test
    void joinsGzipFeedsByUidAndUpsertsWithoutDeletingExistingMembers() throws Exception {
        when(websiteClient.login(any())).thenReturn(new ArchonWebsiteClient.WebsiteSession("website-session-token"));
        ArchonAccessToken token = new ArchonAccessToken();
        token.setAccessToken("test-token");
        when(archonClient.token(any())).thenReturn(token);
        when(archonClient.export("Bearer test-token")).thenReturn(response(EXPORT, true));
        when(websiteClient.snapshot("website-session-token")).thenReturn(response(SNAPSHOT, true));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ArchonUserEntity existing = new ArchonUserEntity();
        existing.setVeknId("0012345");
        existing.setCreationDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(userRepository.findAllById(any())).thenReturn(List.of(existing));

        scheduler.scrappingUsers();

        ArgumentCaptor<List<ArchonUserEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAllAndFlush(captor.capture());
        ArchonUserEntity stored = captor.getValue().getFirst();
        assertEquals("0012345", stored.getVeknId());
        assertEquals("member-uid", stored.getArchonUserUid());
        assertEquals("Hernando Sagardia", stored.getName());
        assertEquals("Nano", stored.getAlias());
        assertEquals("ES", stored.getCountry());
        assertEquals("Barcelona", stored.getCity());
        assertEquals("[\"Prince\",\"Judge\"]", stored.getRoles().toString());
        assertEquals(existing.getCreationDate(), stored.getCreationDate());
        verify(userRepository, never()).deleteAll();
    }

    @Test
    void handlesAlreadyDecompressedFeedAndNullableFields() throws Exception {
        List<ArchonUser> identities = scheduler.readUsers(response(EXPORT, false), false);
        String minimal = "{\"type\":\"header\",\"version\":2}\n"
                + "{\"type\":\"user\",\"data\":{\"uid\":\"member-uid\",\"name\":\" José \"}}\n"
                + "{\"type\":\"eof\",\"count\":1}";
        ArchonUserEntity member = scheduler.mapMembers(identities,
                scheduler.readUsers(response(minimal, false), true)).getFirst();
        assertEquals("José", member.getName());
        assertNull(member.getAlias());
        assertNull(member.getCity());
        assertNull(member.getCountry());
        assertEquals("[]", member.getRoles().toString());
    }

    @Test
    void rejectsTruncatedWrongCountAndUnsupportedSnapshots() throws Exception {
        assertThrows(IOException.class, () -> scheduler.readUsers(response(EXPORT, false), true));
        assertThrows(IOException.class, () -> scheduler.readUsers(response(SNAPSHOT.replace("\"count\":2", "\"count\":3"), true), true));
        assertThrows(IOException.class, () -> scheduler.readUsers(response(SNAPSHOT.replace("\"version\":2", "\"version\":3"), false), true));
    }

    @Test
    void skipsDeletedMissingVeknAndMissingNameProfiles() {
        ArchonUser identity = new ArchonUser();
        identity.setUid("matched");
        identity.setVeknId("123");
        ArchonUser deleted = new ArchonUser();
        deleted.setUid("matched");
        deleted.setName("Deleted");
        deleted.setDeletedAt("2026-10-01");
        ArchonUser unmatched = new ArchonUser();
        unmatched.setUid("unmatched");
        unmatched.setName("No VEKN ID");
        ArchonUser unnamed = new ArchonUser();
        unnamed.setUid("matched");
        assertTrue(scheduler.mapMembers(List.of(identity), List.of(deleted, unmatched, unnamed)).isEmpty());
    }

    @Test
    void doesNotWriteIfSnapshotIsTruncated() throws Exception {
        ArchonAccessToken token = new ArchonAccessToken();
        token.setAccessToken("test-token");
        when(archonClient.token(any())).thenReturn(token);
        when(archonClient.export(any())).thenReturn(response(EXPORT, true));
        when(websiteClient.login(any())).thenReturn(new ArchonWebsiteClient.WebsiteSession("website-session-token"));
        when(websiteClient.snapshot("website-session-token")).thenReturn(response(EXPORT, false));
        scheduler.scrappingUsers();
        verifyNoInteractions(userRepository, transactionManager);
    }

    @Test
    void neverDownloadsAnonymousSnapshotWithoutWebsiteCredentials() {
        ReflectionTestUtils.setField(scheduler, "websitePassword", "");
        scheduler.scrappingUsers();
        verifyNoInteractions(archonClient, websiteClient, userRepository, transactionManager);
    }

    @Test
    void rejectsRestrictedSnapshotBeforeAnyDatabaseWrites() throws Exception {
        ArchonAccessToken token = new ArchonAccessToken();
        token.setAccessToken("api-token");
        when(archonClient.token(any())).thenReturn(token);
        when(archonClient.export(any())).thenReturn(response(EXPORT, false));
        when(websiteClient.login(any())).thenReturn(new ArchonWebsiteClient.WebsiteSession("website-token"));
        when(websiteClient.snapshot("website-token")).thenReturn(response(
                SNAPSHOT.replace("member-uid", "public-official-only"), true));
        scheduler.scrappingUsers();
        verifyNoInteractions(userRepository, transactionManager);
    }

    @Test
    void ordinaryMembersAreIncludedAndSeparateFeedTimingIsTolerated() throws Exception {
        java.util.ArrayList<ArchonUser> identities = new java.util.ArrayList<>();
        java.util.ArrayList<ArchonUser> profiles = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            ArchonUser identity = new ArchonUser();
            identity.setUid("uid-" + index);
            identity.setVeknId(String.valueOf(index));
            identities.add(identity);
            if (index < 95) {
                ArchonUser profile = new ArchonUser();
                profile.setUid(identity.getUid());
                profile.setName("Ordinary Member " + index);
                profile.setRoles(List.of());
                profiles.add(profile);
            }
        }
        scheduler.validateCoverage(identities, profiles);
        assertEquals(95, scheduler.mapMembers(identities, profiles).size());
        profiles.removeLast();
        assertThrows(IOException.class, () -> scheduler.validateCoverage(identities, profiles));
    }

    @Test
    void logsInAgainOnceOnExpiredSnapshotSession() throws Exception {
        ArchonAccessToken token = new ArchonAccessToken();
        token.setAccessToken("api-token");
        when(archonClient.token(any())).thenReturn(token);
        when(archonClient.export(any())).thenReturn(response(EXPORT, false));
        when(websiteClient.login(any())).thenReturn(new ArchonWebsiteClient.WebsiteSession("expired"),
                new ArchonWebsiteClient.WebsiteSession("renewed"));
        when(websiteClient.snapshot("expired")).thenThrow(feign.FeignException.errorStatus("snapshot",
                response("{}", false).toBuilder().status(401).build()));
        when(websiteClient.snapshot("renewed")).thenReturn(response(SNAPSHOT, true));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        scheduler.scrappingUsers();
        verify(websiteClient, times(2)).login(any());
        verify(userRepository).saveAllAndFlush(any());
        verify(websiteClient, never()).snapshot(null);
    }

    @Test
    void authenticationFailureNeverFallsBackToPublicSnapshot() throws Exception {
        ArchonAccessToken token = new ArchonAccessToken();
        token.setAccessToken("api-token");
        when(archonClient.token(any())).thenReturn(token);
        when(archonClient.export(any())).thenReturn(response(EXPORT, false));
        when(websiteClient.login(any())).thenReturn(new ArchonWebsiteClient.WebsiteSession(null));
        scheduler.scrappingUsers();
        verify(websiteClient, never()).snapshot(any());
        verifyNoInteractions(userRepository, transactionManager);
    }

    private Response response(String ndjson, boolean gzip) throws IOException {
        byte[] bytes = ndjson.getBytes(StandardCharsets.UTF_8);
        if (gzip) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream stream = new GZIPOutputStream(output)) {
                stream.write(bytes);
            }
            bytes = output.toByteArray();
        }
        return Response.builder().status(200).reason("OK").headers(Map.of())
                .request(Request.create(Request.HttpMethod.GET, "https://archon.test/feed", Map.of(), null, StandardCharsets.UTF_8, null))
                .body(bytes).build();
    }
}
