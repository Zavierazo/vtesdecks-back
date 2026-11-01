package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import com.vtesdecks.integration.PatreonClient;
import com.vtesdecks.integration.PatreonMember;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatreonMembershipService {
    private final PatreonWebhookConfiguration configuration;
    private final PatreonClient client;

    public boolean available() {
        return StringUtils.isNotBlank(configuration.getCampaignId())
                && StringUtils.isNotBlank(configuration.getAccessToken());
    }

    /** No state is changed until the entire paginated response is validated. */
    public Map<String, PatreonMember> members() {
        if (!available()) {
            throw new IllegalStateException("Patreon campaign and creator access token are required");
        }
        Map<String, PatreonMember> members = new HashMap<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        Long total = null;
        do {
            String pageCursor = cursor;
            Map<String, Object> query = new HashMap<>();
            query.put("include", "user,campaign");
            query.put("fields[member]", "email,currently_entitled_amount_cents,last_charge_status,patron_status,is_free_trial");
            query.put("fields[user]", "email");
            query.put("page[count]", 1000);
            if (pageCursor != null) {
                query.put("page[cursor]", pageCursor);
            }
            JsonNode page = client.memberPage("Bearer " + configuration.getAccessToken(), configuration.getCampaignId(), query);
            if (page == null || page.has("errors") || !page.path("data").isArray()
                    || !page.path("meta").path("pagination").isObject()
                    || !page.path("meta").path("pagination").path("cursors").isObject()) {
                throw new IllegalStateException("Incomplete Patreon member page");
            }
            JsonNode pageTotal = page.path("meta").path("pagination").path("total");
            if (pageTotal.isIntegralNumber()) {
                if (total != null && total.longValue() != pageTotal.asLong()) {
                    throw new IllegalStateException("Patreon pagination changed during download");
                }
                total = pageTotal.asLong();
            }
            for (JsonNode data : page.path("data")) {
                PatreonMember member = PatreonMember.read(page, data, configuration.getCampaignId(), false);
                if (members.putIfAbsent(member.userId(), member) != null) {
                    throw new IllegalStateException("Duplicate Patreon user in member pages");
                }
            }
            JsonNode next = page.path("meta").path("pagination").path("cursors").path("next");
            if (!next.isMissingNode() && !next.isNull() && !next.isTextual()) {
                throw new IllegalStateException("Invalid Patreon pagination cursor");
            }
            cursor = next.isTextual() ? StringUtils.trimToNull(next.textValue()) : null;
            if ((cursor != null && !cursors.add(cursor))
                    || (cursor == null && page.path("links").path("next").isTextual()
                    && StringUtils.isNotBlank(page.path("links").path("next").asText()))) {
                throw new IllegalStateException("Incomplete Patreon pagination");
            }
        } while (cursor != null);
        if (total != null && total != members.size()) {
            throw new IllegalStateException("Incomplete Patreon member count");
        }
        return Map.copyOf(members);
    }
}
