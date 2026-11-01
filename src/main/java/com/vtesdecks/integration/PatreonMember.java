package com.vtesdecks.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;

/** Minimal private membership data shared by webhook and full reconciliation. */
public record PatreonMember(String memberId, String userId, String email, Decision decision) {
    public enum Decision { GRANT, REMOVE, KEEP }

    public static PatreonMember read(JsonNode root, JsonNode data, String campaignId, boolean deleted) {
        if (!"member".equals(text(data, "type")) || StringUtils.isBlank(text(data, "id"))) {
            throw new IllegalArgumentException("Missing Patreon member");
        }
        String receivedCampaign = text(data.path("relationships").path("campaign").path("data"), "id");
        if (!campaignId.equals(receivedCampaign)) {
            throw new IllegalArgumentException("Patreon campaign does not match");
        }
        String userId = text(data.path("relationships").path("user").path("data"), "id");
        if (userId == null || userId.length() > 64 || !userId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Missing Patreon user ID");
        }
        JsonNode attributes = data.path("attributes");
        String email = StringUtils.trimToNull(text(attributes, "email"));
        if (email == null && root.path("included").isArray()) {
            for (JsonNode included : root.path("included")) {
                if ("user".equals(text(included, "type")) && userId.equals(text(included, "id"))) {
                    email = StringUtils.trimToNull(text(included.path("attributes"), "email"));
                }
            }
        }
        return new PatreonMember(text(data, "id"), userId, email, decision(attributes, deleted));
    }

    private static Decision decision(JsonNode attributes, boolean deleted) {
        JsonNode amount = attributes.path("currently_entitled_amount_cents");
        String charge = text(attributes, "last_charge_status");
        if ("declined_patron".equals(text(attributes, "patron_status"))
                || "Declined".equals(charge) || "Fraud".equals(charge)
                || "Refunded".equals(charge) || "Refunded by Patreon".equals(charge)) {
            return Decision.REMOVE;
        }
        if (amount.isIntegralNumber() && amount.canConvertToLong() && amount.asLong() >= 0) {
            if (amount.asLong() == 0) {
                return Decision.REMOVE;
            }
            if ("Paid".equals(charge) && !attributes.path("is_free_trial").asBoolean(false)) {
                // A cancelled renewal may still have a fully paid current period.
                return Decision.GRANT;
            }
        }
        return deleted ? Decision.REMOVE : Decision.KEEP;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.textValue() : null;
    }
}
