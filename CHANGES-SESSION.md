# Session Changelog — Draft Tool Back End

Summary of all changes made during this working session (security/audit fixes,
compatibility hardening, and cleanup). All changes are uncommitted in the working
tree unless otherwise noted.

## 1. Compatibility fixes (frontend audit)

- **`X-Player-Token` CORS header** — added `X-Player-Token` to
  `quarkus.http.cors.headers` in `src/main/resources/application.properties` so
  cross-origin polling/drafting works.
- **Player token removed from URLs** — `playerToken` moved from query strings to
  the `X-Player-Token` header on pyramid (`GameResource`), classic
  (`classicDraft/GameResource`), and lobby heartbeat (`GET /lobby/{code}`).
  Tokens are no longer captured in access logs.
- **Leave-lobby token proof** — `LeaveLobbyRequest` now requires the caller's own
  `playerToken`; `LobbyService.findPlayerByToken` resolves identity. An
  `accountID` alone can no longer remove another player.
- **Public, PII-free support read** — `GET /support/{id}` returns a
  `PublicSupportRequest` view (no `contactEmail`, no `accountID`).

## 2. Rate limiting

- New `RateLimiter` (sliding-window, in-memory, `@Scheduled` cleanup, bounded
  key table that fails open) and `RateLimitFilter` (429 + `Retry-After`).
- Limits configured in `application.properties` (`rate-limit.*`):
  - `account/login` — 20/min
  - `scryfall/**` — 120/min
  - `lobby/**` (create/join/poll/kick/leave/start) — 60/min
  - `POST support` (create) — 20/min
  - `game/**` and `classic-game/**` (draftCard/draftCheck/draftData/merge) — 120/min
- **Trusted-proxy hardening** — `X-Forwarded-For` is honored only when the direct
  peer is a proxy listed in `rate-limit.trusted-proxies`; client-supplied header
  values are otherwise ignored and the socket peer address is used. Empty
  `rate-limit.trusted-proxies` = header never trusted
  (`rate-limit.trust-forwarded-header=false` default).

## 3. Input validation hardening (bean validation → 400)

- `LoginRequest` (8 KB cap), `UpdateDisplayNameRequest` (50 chars, no control chars).
- `CreateDeckRequest` / `UpdateDeckRequest` (name 100, description 2000,
  ≤500 cardIds each ≤64).
- `GameCreationInfo` (gameID 64, cubeID pattern, players 1–2, double picks 0–10),
  `PlayerCreationInfo` (name 50, accountID 100, token 64).
- `ClassicGameCreationInfo` (players 4–12, packs 1–300, cardsPerPack 1–25,
  packsPerPlayer 1–30); `ClassicPlayerCreationInfo.accountID` made optional for
  guest-created classic games.
- Lobby request DTOs (draftType 30, config ≤50 keys, min/max players 2–12,
  display names 50 + no control chars, tokens 64).
- Support request DTOs (`@Email` 254, title 200, description 4000, accountID 100);
  `UpdateStatusRequest.status` `@NotNull`.
- `ScryfallResource.BatchNamesRequest` (≤300 names each ≤64).
- Resource path/header params annotated with `@Size`/`@Min`/`@Max` (deckID,
  lobbyCode, support id, scryfall id/query params, game IDs).
- `LobbyService.startGame` validates cubeID format in the opaque config path.

## 4. Enum validation → 400 (was 500)

- `LobbyResource.createLobby`: unknown `draftType` now returns 400 (was 500 from
  `LobbyService.findHandler`'s `IllegalStateException`).
- `AdminResource.deleteGamesWithStatus`: invalid `gameState` now returns 400
  (was 500 from `GameState.fromString`'s `IllegalArgumentException`).

## 5. Server-side logout + token revocation

- `JwtService.issueToken` now stamps a `jti` claim on every issued token.
- New `TokenRevocationService`: in-memory store of SHA-256 token hashes with
  revocation timestamps; entries expire after token TTL; bounded table
  (evicts oldest at cap); `@Scheduled` cleanup.
- New `TokenRevocationFilter`: rejects any request presenting a revoked bearer
  token with 401 before JWT auth.
- `POST /account/logout` (PermitAll): revokes the presented bearer token, returns
  204; 400 if no bearer token. Note: revocation is process-local (single-instance).

## 6. CVE remediation

- Quarkus BOM/plugin `3.20.0` → `3.20.6.2` (CVE-2026-50559, CVE-2026-39852,
  CVE-2025-49574).
- Netty → `4.1.137.Final`, Jackson → `2.18.9`, Vert.x → `4.5.27` via
  `resolutionStrategy` in `build.gradle`.
- Added `org.owasp.dependencycheck` plugin (`failBuildOnCVSS=7`, `failOnError=false`).
- `dependency-check-suppressions.xml`: single remaining OSV hit
  (`opentelemetry-api 1.44.1` GHSA-rcgg-9c38-7xpx) documented as not applicable
  (inert API-only jars).

## 7. Logging / noise cleanup

- `PyramidDraftDbHandler`, `ClassicDraftDbHandler`, `GameCoordinationWorker` log
  game IDs only, not whole game objects.
- `SecurityHeadersFilter` adds `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`.

## 8. Tests

- New: `RateLimiterTest`, `RateLimitFilterTest`, `TokenRevocationServiceTest`,
  `TokenRevocationFilterTest`, `SupportResourceTest` (public list + PII),
  `AdminResourceTest` (invalid gameState → 400, account lookup), `AccountResourceTest`
  (logout).
- Total suite: **387 tests passing**; full `./gradlew build` green including the
  Jacoco coverage gate.

## 9. Admin cross-account lookup (AccountSearch)

New admin-only endpoints under `AdminResource` (identity from the caller's JWT;
class is `@RolesAllowed("admin")`):

- `GET /admin/accounts/{accountID}` — account record (404 if missing)
- `GET /admin/accounts/{accountID}/game/history` — aggregated history across all
  draft types (`GameHistoryService.getGameHistoryByAccountID`)
- `GET /admin/accounts/{accountID}/decks` — decks for the account
- `DELETE /admin/accounts/{accountID}/decks/{deckID}` — 204/404; the deck is
  verified to belong to the account before deletion

Frontend `adminApi.getAccountByID` / `getGameHistoryByID` / `getDecksByID` /
`deleteDeckByID` will now succeed.

## 10. Real-mode kick by slot index

- `KickPlayerRequest` now takes `playerToken` (host's own) + `targetSlotIndex`
  instead of `targetPlayerToken`. `LobbyService.kickPlayer` resolves the target
  by `slotIndex` server-side; player tokens are never exposed to clients.
- Frontend change required: `LobbyView` should call `onKick` with
  `player.slotIndex` and `lobbyApi.kick` should send `{ playerToken,
  targetSlotIndex }` to `POST /lobby/{code}/kick`.

## 11. OpenAPI spec for all REST resources

- Added `io.quarkus:quarkus-smallrye-openapi` to `build.gradle`; the spec is
  auto-generated from the JAX-RS endpoints and served at `/q/openapi` with
  Swagger UI at `/q/swagger-ui` (`quarkus.swagger-ui.always-include=true`).
- Info block set in `application.properties`: title `Magic Draft Tool API`,
  version `1.0.0`, plus a description.
- Added MicroProfile OpenAPI `@Tag` + `@Operation` (summary/description) to every
  resource and endpoint:
  - `AccountResource` + `GameHistoryResource` — tag "Account" (9 + 1 endpoints)
  - `LobbyResource` — tag "Lobby" (6 endpoints)
  - `SupportResource` — tag "Support" (7 endpoints)
  - `AdminResource` — tag "Admin" (10 endpoints, incl. `/admin/accounts/*`)
  - `ScryfallResource` — tag "Scryfall" (5 endpoints)
  - `classicDraft/GameResource` — tag "Classic Draft" (6 endpoints)
  - `pyramidDraft/GameResource` — tag "Pyramid Draft" (5 endpoints)
  - `DevResource` — tag "Dev" (1 endpoint, dev profile only)
- Descriptions document auth requirements (admin/user/anonymous), the
  `X-Player-Token` header on draft actions, and the main error codes.
- Build green; **387 tests pass, 0 failures**.
- **Startup fix**: `rate-limit.trusted-proxies=` (empty) caused SmallRye Config
  to treat the value as null for a non-nullable `String`, failing app startup
  with `SRCFG00040`. `RateLimitFilter` now injects `Optional<String>` and
  resolves to an empty `Set` — the app boots and the empty value still means
  "X-Forwarded-For never trusted".

## Notes / known items

- Token revocation is process-local; a multi-instance deployment needs a shared
  store (e.g. Redis) for cross-instance revocation.
- `quarkus.http.cors.paths` produces an "unrecognized configuration key" warning
  (cosmetic; the CORS extension is enabled via `quarkus.http.cors=true`).

## 12. JWT signing/verification key config fix (login 500)

- **Symptom**: `POST /account/login` returned 500 —
  `JwtSignatureException SRJWT05009` caused by
  `SRJWT05021: Please set 'smallrye.jwt.sign.key.location' or 'smallrye.jwt.sign.key'`.
- **Root cause** (verified against the bundled jars, smallrye-jwt(-build) 4.6.2 /
  Quarkus 3.20.6.2): the signing key was configured as
  `smallrye.jwt.new-token.signing-key.secret-key`, which no class in the stack
  reads — `JwtSignatureImpl.sign()` reads only `smallrye.jwt.sign.key.location`
  / `smallrye.jwt.sign.key`. The verify key (`smallrye.jwt.verify.key.secret-key`)
  was likewise dead; the real property is `smallrye.jwt.verify.secretkey`.
- **Second wrinkle**: in 4.6.2 the key *content* for both properties must be a
  JWK (`kty: oct`) — a raw secret string is not interpreted (no base64/raw
  fallback in `JwtSignatureImpl.getSigningKeyFromKeyContent` /
  `KeyLocationResolver.initializeKeyContent`).
- **Fix** in `application.properties`, `application-dev.properties`,
  `application-prod.properties`:
  - `smallrye.jwt.sign.key={"kty":"oct","k":"${APP_JWT_SECRET}"}`
  - `smallrye.jwt.verify.secretkey={"kty":"oct","k":"${APP_JWT_SECRET}"}`
  - `smallrye.jwt.verify.algorithm=HS256`
  - kept `smallrye.jwt.new-token.signature-algorithm=HS256` (sets the `alg` header).
  - Dev fallback replaced with a proper base64url-encoded key so the JWK parses.
- **Contract change**: `APP_JWT_SECRET` must now be a **base64url-encoded key of
  >= 32 bytes when decoded** (e.g. a 48-byte secret → 64 base64url chars). Prod
  has no fallback; a missing env fails config expansion at startup.
- **Verified end-to-end**: `GET /dev/token` → `POST /account/login` returns a
  JWT whose header is `{"alg":"HS256","typ":"JWT"}`; the token authenticates a
  `@RolesAllowed("user")` request (`GET /account`) → 200. Build green; **387 tests
  pass, 0 failures**.
