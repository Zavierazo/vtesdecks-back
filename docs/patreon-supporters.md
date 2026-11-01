# Patreon supporter synchronization and admin identifiers

## Account management API

All paths are relative to `/api/1.0` and require the existing `ADMIN` authority.
`GET /admin/users/{identifier}` includes nullable `patreonId` and `veknId` alongside the existing private management fields. The identifier can be the username or email.

- `PUT /admin/users/{identifier}/patreon-id` accepts `{ "patreonId": "00123456" }`.
- `PUT /admin/users/{identifier}/vekn-id` accepts `{ "veknId": "003120065" }`.

Use an explicit `null` to remove an ID. Blank strings are normalized to null; IDs otherwise contain 1–64 digits, are trimmed and remain strings so leading zeroes survive. Responses contain the updated `ApiAdminUser`. Invalid values return 400, unknown accounts 404, and identities already assigned to another account 409. These operations use direct dedicated writes without explicit admin locking. Ordinary ORM account saves cannot change either ID. Patreon IDs are not exposed in public profiles, authentication responses or tokens.

A manual Patreon edit does not immediately change roles. Removing the ID opts the account out of daily management and preserves its current roles. Trusted administrator VEKN edits cancel pending Archon attempts, clear/revoke cached credentials and refresh attribution for both the old and new VEKN IDs. They do not grant ownership of archived decks or require a new OAuth consent round trip.

## Server configuration

Configure these on the backend, outside tracked files:

```properties
patreon.webhook.secret=<existing webhook signing secret>
patreon.campaignId=<the Patreon campaign ID>
patreon.api.accessToken=<creator access token>
jobs.patreonSupportersCron=0 30 5 * * *
```

The cron always uses `Europe/Madrid`. `-` disables its automatic schedule. The default is daily at 05:30. The optional `patreon.api.url` defaults to `https://www.patreon.com` and exists for local HTTP tests. The API uses `PatreonClient` (Spring Cloud OpenFeign), with bounded timeouts, no redirects or automatic retries, and HTTP logging disabled.

The creator token needs `campaigns.members` and `campaigns.members[email]`. It is separate from the webhook signing secret. This integration does not perform a new user OAuth flow or refresh tokens automatically: rotate the configured creator token operationally if required. API authentication errors abort reconciliation without revoking roles. Missing campaign configuration prevents processing membership changes; supply the campaign before enabling the updated backend. Missing API credentials disable only the daily/manual reconciliation.

## Webhook events and identity matching

Keep the current endpoint URL and signing secret. After deploying the updated backend, enable all six v2 triggers on that webhook:

- `members:create`, `members:update`, `members:delete`
- `members:pledge:create`, `members:pledge:update`, `members:pledge:delete`

Signatures are checked against the original bytes. Invalid signatures return 401 and malformed membership requests return 400. Authenticated events intentionally ignored return 204. Other/missing campaigns are ignored; temporary server failures remain retryable.

`patreon_id` is the related **user** ID (`data.relationships.user.data.id`), not `data.id` (the member ID). A known Patreon ID takes precedence over any email. Only a confirmed paid entitlement can bootstrap a missing association by matching email case-insensitively. The webhook never overwrites a different existing Patreon ID, creates a VTESDecks account, or links by name. Conflicts are logged without changing the account or role.

## Role policy and daily reconciliation

Both paths use the same three-state membership policy:

- Positive current entitlement with `last_charge_status=Paid`, excluding free trials: grant or retain `supporter`.
- Zero current entitlement, explicit declined/fraud/refunded payment, or deleted membership without a remaining confirmed paid entitlement: remove `supporter`.
- Missing/ambiguous entitlement or payment data: retain the current state.

Cancellation of renewal does not remove a still-paid current period. Historical lifetime payments are not eligibility. The Patreon ID is retained after role removal so subsequent paid events can restore it without matching email. Other roles are unaffected. On linked accounts this policy also governs previously manual supporter grants.

**The daily job selects only already-linked accounts. It never matches email, links accounts, or changes supporter for a user without `patreon_id`, even if that user's email appears among active/cancelled members or is absent from Patreon.** Clearing or replacing an ID during a download is rechecked before changing roles.

The job fetches and validates every campaign member page before applying changes, follows opaque cursors, rejects repeated/invalid pages and inconsistent totals, and aborts on API failures. A linked user absent from a complete successful snapshot loses only `supporter`, keeping their ID. Unknown member state preserves the role. Logs report granted, removed and unchanged linked accounts. Run `patreon-supporters` through the existing admin scheduler catalog for an immediate pass. Lost or delayed webhook notifications are repaired on the next successful daily/manual pass; this does not guarantee an exact expiry-second transition.

## Verification and release order

The existing JUnit/H2 and Angular/Vitest infrastructure covers account writes, uniqueness, trusted Archon edits, signatures, the six triggers, ID/email precedence, paid-period cancellation, failures, idempotency, unlinked supporter preservation, admin form drafts and profile refresh. `PatreonClientTest` exercises the actual Feign HTTP client against a local provider-contract server, including pagination and 401/403/429/503 failures. These tests do not establish live Patreon delivery or credentials.

1. Apply `V1.0.107__user_patreon_id.sql` and deploy the backend with campaign and signing configuration.
2. Configure the creator API token and verify a successful manual job. Review the linked IDs before enabling automation.
3. Deploy the frontend and enable the additional webhook triggers.
4. Use the Patreon portal to send real test deliveries, checking logs and the resulting account/role. Verify a real cancellation with remaining paid access before relying on automatic expiry.

Provider contract: [Patreon API documentation](https://docs.patreon.com/#member), [webhook triggers](https://docs.patreon.com/#triggers-v2), and [cancellation access](https://support.patreon.com/hc/en-us/articles/4407273239693-What-happens-when-I-cancel).
