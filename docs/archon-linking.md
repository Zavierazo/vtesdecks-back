# Verified Archon account linking

This integration verifies the current Archon member with OAuth Authorization Code and PKCE S256, requesting only `profile:read`. It associates archived tournament decks by exact VEKN ID. It does not create deck ownership, change archive records, or grant any mutation permission. Existing administrator permissions are unchanged.

## Configuration and release order

The existing `archon.clientId` and `archon.clientSecret` remain server-side. In the Archon developer settings, enable `profile:read` for that client and register the exact callback URL:

- Production: `https://vtesdecks.com/user/archon/callback`
- Local development: `http://localhost:4200/user/archon/callback` (register separately if used)

Set `archon.redirectUri` to the exact registered URL, without an additional trailing slash. Linking reports `available: false` until the client ID, secret and redirect URI are configured. The Member API base defaults to `https://archon.vekn.net` through `archon.memberUrl`; do not point it at `api.archon.vekn.net`. The existing Public API/export client is unchanged. Never put the client secret in Angular configuration.

The local checkout sets `archon.redirectUri=http://localhost:4200/user/archon/callback` directly in `application-live.properties`, since local development uses the `live` profile. Set this property to `https://vtesdecks.com/user/archon/callback` in production. Restart the backend after changing the property. Register both callback URLs in Archon if the client serves both environments; configuring a URL in VTESDecks does not register it with the provider.

Deploy migration `V1.0.105__archon_account_linking.sql` and the backend first, then the frontend. The migration adds one nullable, unique `vekn_id` column to the existing `user` table; it creates no new tables. Register the callback and scope before setting the redirect URI to enable new connections. The existing Redis connection must be available for the temporary login data. Normal startup rebuilds the deck index and invalidates old full-deck payloads, loading the new internal VEKN field. No deck ownership backfill or archive rewrite is needed. Verify the live consent round trip with a real member before announcing availability.

## API contract

All paths below are relative to `/api/1.0`. Private endpoints require the existing VTESDecks access token. OAuth codes, state and errors are submitted in JSON bodies, never as authorization headers. Status and authorization responses use `Cache-Control: no-store`.

| Method and path | Request | Successful response |
| --- | --- | --- |
| `GET /user/archon` | None | `200 { available: boolean, linked: boolean, veknId: string or null }` |
| `POST /user/archon/connect` | Empty object | `200 { authorizationUrl: string }` |
| `POST /user/archon/complete` | `{ state, code }` or `{ state, error }` | `200` with the same status shape; denial consumes the attempt without linking |
| `DELETE /user/archon` | None | `204`; idempotent, including when new connections are disabled |
| `GET /public/user/{username}/tournament-decks` | `offset=0`, `limit=6`, optional `currency=EUR` | `200 ApiDecks`, newest first with deterministic ties |

Pagination accepts nonnegative offsets and limits from 1 to 60. Unknown profiles return `404`. Existing unlinked profiles return an empty page. Only public, non-deleted tournament decks can appear. The server derives the VEKN filter from the verified connection, and never treats a submitted VEKN ID as proof of identity.

`GET /public/user/{username}` also returns optional `veknId` and `archonProfileUrl` fields for linked profiles. The verified ID remains a string, preserving leading zeroes. The direct profile URL uses the Archon UID already present in the imported player catalog (`https://archon.vekn.net/users/{uid}`). If that UID is unavailable, the URL is omitted and the frontend displays the VEKN ID as plain text. This information appears below the public username. Compact user identities in deck, search and follower responses omit these fields. Disconnecting removes both fields from subsequent profile responses.

`ApiDeck.user` is the optional public identity displayed as the deck's author. For linked tournament decks it contains the verified player; otherwise it contains the real owner when one exists. The API does not expose a separate `tournamentPlayer` field. `DeckFactory` resolves verified attribution into a separate internal `tournamentPlayer` when building a deck and stores the compact public identity in both the resident summary and cached full deck. API requests select this cached identity without querying linked accounts again. The internal `user` remains the real owner and continues to determine ownership, visibility and user filters; the response's `owner` flag is calculated from that real owner. The historical `author` remains unchanged. The frontend uses the tournament user's display name (falling back to username) and profile link, or the historical author when no user exists. No account email or Archon OAuth credentials are included in public responses. Linking, disconnecting and profile updates publish the existing deck-sync messages after commit to rebuild matching tournament decks, including cached full decks. Attribution updates when these asynchronous messages are processed; the regular index rebuild also refreshes it. New imports resolve the current connection through the same factory.

Errors: `400` invalid, expired or consumed attempt; `401` unavailable website session; `409` existing connection or identity conflict; `422` missing/invalid VEKN ID; `502` upstream OAuth failure; `503` linking not configured. A conflict never transfers an identity between accounts.

## Lifecycle and guarantees

Only `user.vekn_id` is permanent. It is populated exclusively from the verified Member API response and cleared on disconnect. Ordinary ORM account saves cannot write it; the linking service uses a dedicated repository update under the account lock. This prevents unrelated account edits from overwriting a concurrent verification. The verified ID is exposed only in the private connection status and the full public profile. Public responses continue to use the existing explicit public-user mapper; profile-only fields are added by the public-profile service.

Redis key `archon:authorization:<userId>` holds the state hash, PKCE verifier, authentication version and expiry for ten minutes. The backend claims the attempt by clearing its verifier before contacting Archon. Account locks serialize start, claim, completion and disconnect across instances. Completion rechecks the current consumed attempt and authentication version before storing the VEKN ID, so replaced, expired or disconnected attempts cannot restore a connection. Pending authorization data is deleted after completion or denial; otherwise Redis expires it automatically.

Successful access tokens are kept only in Redis key `archon:access-token:<userId>` with a one-hour TTL. They are never returned to the frontend or written to SQL. Refresh tokens are discarded and never used for renewal. There is no background synchronization, and any future functionality requiring authorization must ask the member again. Failed completion revokes issued tokens on a best-effort basis. Disconnect deletes both Redis keys and clears `user.vekn_id`, then attempts to revoke the cached access token.

Expiration or loss of Redis data does not remove an established VEKN link. The verified ID remains fixed until disconnect/reconnect. Disconnect restores original public attribution and removes the tournament section from the profile without deleting or editing archived decks.

The frontend captures callback parameters in memory before third-party scripts load and removes them from the URL immediately. Reloading that cleaned callback requires restarting the flow. Signing in through Archon is used only to link an existing VTESDecks account.

## Verification

- `ApiArchonServiceTest`: H2-backed user migration, repository updates and transaction boundaries with simulated Redis operations; PKCE/state, VEKN uniqueness, expiry, replay, session version, cancellation, upstream errors, one-hour token retention, and disconnect races.
- `ArchonMemberClientTest`: actual HTTP requests against a local server implementing the documented Member API; form encoding, bearer authorization, revocation and single-attempt behavior for 429/503.
- `DeckFactoryTest`, `ApiDeckSummaryMapperTest`, `DeckIndexTest`, and `ApiDeckBuilderManagementTest`: cached attribution, exact identifiers, public visibility and mutation rejection.
- Angular tests cover one-use callbacks, settings failures, independent profile pagination and attribution fallbacks.

## Next verification session

Implementation is local and has not been deployed or activated. Tests have been adapted to the simplified user-column and Redis design but have not yet been rerun. Browser checks and the real Archon consent round trip are deferred to the next session at the user's request. Verify actual Redis TTL expiration, linked author links and deck links independently on desktop and phones, all profile pages, disconnect/reconnect, and normal preconstructed/community card layout. Exercise the new endpoints through the running backend after applying the migration in the intended test environment. Register the client callback before testing real consent. Do not infer live OAuth success from the local tests.

Source contract: [Archon Public API documentation](https://api.archon.vekn.net/docs#description/introduction), including its Member API OAuth introduction and `/oauth/userinfo` response.
