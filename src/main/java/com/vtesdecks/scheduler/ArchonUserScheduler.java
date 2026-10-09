package com.vtesdecks.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.vtesdecks.integration.ArchonClient;
import com.vtesdecks.integration.ArchonWebsiteClient;
import com.vtesdecks.jpa.entity.ArchonUserEntity;
import com.vtesdecks.jpa.repositories.ArchonUserRepository;
import com.vtesdecks.model.archon.ArchonUser;
import feign.Response;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchonUserScheduler {
    private final ArchonClient archonClient;
    private final ArchonWebsiteClient websiteClient;
    private final ArchonUserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Value("${archon.clientId:}")
    private String clientId;
    @Value("${archon.clientSecret:}")
    private String clientSecret;
    @Value("${archon.websiteEmail:}")
    private String websiteEmail;
    @Value("${archon.websitePassword:}")
    private String websitePassword;

    @Scheduled(cron = "${jobs.scrappingArchonUsersCron:0 0 6 * * SUN}")
    public void scrappingUsers() {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            log.warn("Skipping Archon user synchronization because credentials are not configured");
            return;
        }
        if (StringUtils.isBlank(websiteEmail) || StringUtils.isBlank(websitePassword)) {
            log.warn("Skipping Archon user synchronization: archon.websiteEmail and archon.websitePassword are required for the complete member directory");
            return;
        }
        log.info("Starting Archon user synchronization");
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            String token = StringUtils.trimToNull(archonClient.token(form).getAccessToken());
            if (token == null) {
                throw new IOException("Archon token response has no access token");
            }
            List<ArchonUser> identities;
            try (Response response = archonClient.export("Bearer " + token)) {
                identities = readUsers(response, false);
            }
            List<ArchonUser> profiles = authenticatedProfiles();
            validateCoverage(identities, profiles);
            List<ArchonUserEntity> members = mapMembers(identities, profiles);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Map<String, ArchonUserEntity> existing = new HashMap<>();
                userRepository.findAllById(members.stream().map(ArchonUserEntity::getVeknId).toList())
                        .forEach(user -> existing.put(user.getVeknId(), user));
                for (ArchonUserEntity member : members) {
                    ArchonUserEntity previous = existing.get(member.getVeknId());
                    if (previous != null) {
                        member.setCreationDate(previous.getCreationDate());
                    }
                }
                userRepository.saveAllAndFlush(members);
            });
            log.info("Finished Archon user synchronization: {} profiles read, {} members mapped", profiles.size(), members.size());
        } catch (Exception e) {
            // Feign exceptions may include a URL containing the session token.
            if (e instanceof FeignException exception) {
                log.error("Unable to synchronize Archon users: HTTP {} (no anonymous fallback)", exception.status());
            } else if (e instanceof IOException || e instanceof IllegalStateException) {
                log.error("Unable to synchronize Archon users: {}", e.getMessage());
            } else {
                log.error("Unable to synchronize Archon users ({})", e.getClass().getSimpleName());
            }
        }
    }

    private String websiteToken() throws IOException {
        ArchonWebsiteClient.WebsiteSession session = websiteClient.login(
                new ArchonWebsiteClient.LoginRequest(websiteEmail.trim(), websitePassword));
        String token = (session != null) ? StringUtils.trimToNull(session.accessToken()) : null;
        if (token == null) {
            throw new IOException("Archon website login did not return an access token");
        }
        return token;
    }

    private List<ArchonUser> authenticatedProfiles() throws IOException {
        // Renew by logging in again once if the session expires while requesting the feed.
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = websiteToken();
            try (Response response = websiteClient.snapshot(token)) {
                if ((response.status() == 401) && (attempt == 0)) {
                    continue;
                }
                if (response.status() != 200) {
                    throw new IOException("Archon authenticated snapshot returned HTTP " + response.status());
                }
                return readUsers(response, true);
            } catch (FeignException exception) {
                if ((exception.status() != 401) || (attempt != 0)) {
                    throw exception;
                }
            }
        }
        throw new IOException("Archon authenticated snapshot could not be obtained");
    }

    void validateCoverage(List<ArchonUser> identities, List<ArchonUser> profiles) throws IOException {
        Set<String> expected = identities.stream()
                .filter(user -> StringUtils.isNotBlank(user.getUid()) && StringUtils.isNotBlank(user.getVeknId())
                        && StringUtils.isBlank(user.getDeletedAt()))
                .map(ArchonUser::getUid).collect(Collectors.toSet());
        Set<String> received = profiles.stream().map(ArchonUser::getUid).collect(Collectors.toSet());
        long matched = expected.stream().filter(received::contains).count();
        // The feeds are rebuilt separately. Allow small timing differences, but reject a restricted snapshot.
        if (expected.isEmpty() || (matched * 100 < expected.size() * 95L)) {
            throw new IOException("Archon member snapshot coverage is insufficient: " + matched + "/" + expected.size()
                    + " VEKN members present; check website account permissions");
        }
    }

    List<ArchonUser> readUsers(Response response, boolean snapshot) throws IOException {
        if (response.body() == null) {
            throw new IOException("Archon feed response has no body");
        }
        List<ArchonUser> users = new ArrayList<>();
        try (PushbackInputStream source = new PushbackInputStream(response.body().asInputStream(), 2)) {
            byte[] prefix = source.readNBytes(2);
            source.unread(prefix);
            InputStream decoded = (prefix.length == 2 && (prefix[0] & 0xff) == 0x1f && (prefix[1] & 0xff) == 0x8b)
                    ? new GZIPInputStream(source) : source;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
                boolean eof = false;
                long count = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.isBlank(line)) {
                        continue;
                    }
                    JsonNode record = mapper.readTree(line);
                    String type = record.path("type").asText();
                    if ("header".equals(type)) {
                        if (snapshot && record.path("version").asInt() != 2) {
                            throw new IOException("Unsupported Archon snapshot version");
                        }
                    } else if ("eof".equals(type)) {
                        if (snapshot && record.path("count").asLong(-1) != count) {
                            throw new IOException("Archon snapshot object count does not match");
                        }
                        eof = true;
                    } else {
                        count++;
                        if ("user".equals(type) && record.path("data").isObject()) {
                            users.add(mapper.treeToValue(record.get("data"), ArchonUser.class));
                        }
                    }
                }
                if (snapshot && !eof) {
                    throw new IOException("Archon snapshot ended without eof");
                }
            }
        }
        return users;
    }

    List<ArchonUserEntity> mapMembers(List<ArchonUser> identities, List<ArchonUser> profiles) {
        Map<String, String> veknIds = new HashMap<>();
        for (ArchonUser identity : identities) {
            if (StringUtils.isNotBlank(identity.getUid()) && StringUtils.isNotBlank(identity.getVeknId())
                    && StringUtils.isBlank(identity.getDeletedAt())) {
                veknIds.put(identity.getUid(), identity.getVeknId().trim());
            }
        }
        Map<String, ArchonUserEntity> members = new HashMap<>();
        for (ArchonUser profile : profiles) {
            String veknId = veknIds.get(profile.getUid());
            if (veknId == null || StringUtils.isBlank(profile.getName()) || StringUtils.isNotBlank(profile.getDeletedAt())) {
                continue;
            }
            ArchonUserEntity member = new ArchonUserEntity();
            member.setVeknId(veknId);
            member.setArchonUserUid(profile.getUid());
            member.setName(profile.getName().trim());
            member.setAlias(StringUtils.trimToNull(profile.getNickname()));
            member.setCountry(StringUtils.trimToNull(profile.getCountry()));
            member.setCity(StringUtils.trimToNull(profile.getCity()));
            member.setRoles(mapper.valueToTree(profile.getRoles() != null ? profile.getRoles() : List.of()));
            if (members.putIfAbsent(veknId, member) != null) {
                throw new IllegalStateException("Multiple Archon profiles for VEKN ID " + veknId);
            }
        }
        return new ArrayList<>(members.values());
    }
}
