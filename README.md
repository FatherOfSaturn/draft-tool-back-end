# Pyramid Draft Backend

Quarkus backend for the Pyramid Draft MTG cube drafting application.

## Draft formats

All formats are launched from a lobby (`POST /lobby`, `draftType` = `pyramid`,
`classic` or `burn`) or directly through their REST API:

- **pyramid** — `/game`, two-player pyramid draft.
- **classic** — `/classic-game`, rotisserie draft (one card per pass).
- **burn** — `/burn-game`, rotisserie draft where each turn drafts N cards and
  burns M cards from the pack before passing it on.

Burn draft lobby config keys, with defaults:

| key | default | meaning |
|---|---|---|
| `cubeID` | (required) | CubeCobra cube to draft |
| `packsPerPlayer` | 3 | packs dealt to each player |
| `cardsPerPack` | 15 | cards in each pack |
| `cardsDraftedPerPack` | 1 | cards a player drafts per turn |
| `cardsBurnedPerPack` | 1 | cards a player burns per turn |
| `remainderPolicy` | `burn_extras` | partial-pass handling: `burn_extras` or `draft_extras` |

A turn is submitted as `POST /burn-game/{gameID}/draft` with
`{ "draftCardIds": [...], "burnCardIds": [...] }` and the `X-Player-Token`
header. When a pack holds fewer cards than the draft+burn quota, the turn drafts
as much of the draft quota as possible and the leftovers are burned
(`burn_extras`) or drafted too (`draft_extras`). Game creation is rejected when
`players × packsPerPlayer × cardsPerPack` exceeds the cube's card count.

## Reactive architecture

The request path is fully reactive (Mutiny end-to-end):

- REST resources return `Uni` and run on the event loop (Quarkus REST).
- Persistence uses reactive MongoDB Panache repositories
  (`ReactivePanacheMongoRepository`); workers and services chain `Uni`
  instead of wrapping blocking calls.
- The Scryfall cache is a reactive Panache repository; the bulk-data
  import (gzip download + line parsing) and Google ID-token verification
  are the only blocking operations left. They are explicitly dispatched
  to worker threads (`ManagedExecutor` / `runSubscriptionOn`).

Nothing on an HTTP request path may block an event-loop thread; if you
add a blocking client, offload it with `runSubscriptionOn` or convert it
to a reactive API.

## Running the tests

```bash
./gradlew build             # unit + architecture tests + coverage (no database needed)
./gradlew integrationTest   # MongoDB-backed end-to-end suites
```

The end-to-end suites are tagged `mongo` and excluded from `build`, so a CI
runner without a database still builds. Run `integrationTest` where MongoDB is
available; the address defaults to `mongodb://127.0.0.1:27017` and can be
overridden with `MONGO_TEST_CONNECTION_STRING` (e.g. a container service alias
such as `mongodb://mongodb:27017` in GitLab CI). GitHub Actions runs both
steps on an `ubuntu-24.04` runner with a `mongo:7.0.45` service container —
MongoDB 8.x refuses to start on Linux kernels 6.19–7.0.13 (SERVER-121912) and
the runner kernels are in that range, whereas 7.0 is supported on all kernels.

## Test coverage

The automated suites verify, against a real MongoDB:

- every REST endpoint and its exact JSON wire shape (field names/order) —
  these wire-format tests were captured against the pre-refactor code and
  have stayed green through the Quarkus 3.40/reactive migration;
- the Scryfall and CubeCobra integrations over **real HTTP**: the reactive
  REST clients are pointed at an in-process stub (`StubExternalApis`) that
  serves canned API responses, so config-key URL resolution, request
  building, JSON decoding, `Uni` completion and non-2xx failure handling
  are exercised end-to-end (including a full `POST /game` that downloads
  the cube over HTTP);
- the Scryfall card cache against real MongoDB: document shape, searchable
  `id`/`name` fields, full DTO round-trip, and cache-served reads with an
  unreachable API URL (proving no fallback call happens);
- local JWT issue/verify (HS256) and token revocation via the login flows.

Only these need manual verification after deployment: a real Google OAuth
login (the JWKS/RS256 path; tests use dev-mode tokens), live Scryfall
rate-limit behaviour (429/Retry-After), and the Scryfall bulk-data import
job (downloads hundreds of MB and is disabled in tests).

## Prerequisites

- Java 25 (Gradle 9.9 wrapper ships with the repo)
- Docker (for the local MongoDB/JSON-server compose stack)
- Node.js (for json-server)
- A MongoDB on `localhost:27017` for the integration tests (or Docker:
  `docker run -d -p 27017:27017 mongo:8`) — the test profile connects there
  directly instead of using Dev Services

## Quick Start

```bash
# Start MongoDB
docker compose -f src/main/docker/docker-compose.yml up -d

# Start json-server (dev only)
json-server --watch db.json

# Run the app
./gradlew clean build quarkusDev
```

## Profiles

| Profile | Use |
|---------|-----|
| `dev`   | Local development with json-server |
| `prod`  | Production with CubeCobra API |

Configure via `quarkus.profile` in `application.properties`.

## Testing the Account System

### Quick Start (Dev Mode)

The backend ships with a **dev mode** that bypasses Google JWT signature verification.
Dev mode is enabled by default in `application.properties` via `google.oauth.dev-mode=true`.

To test account endpoints with Insomnia:

1. Start the app: `./gradlew quarkusDev`
2. Open the included `insomnia-collection.json` in Insomnia
3. Run **Dev Resources → Generate Dev Token** — this creates a signed JWT with your desired user info
4. Copy the `idToken` from the response
5. Paste it into **Account Service → Login** (replace `<google-id-token>` with the copied value)
6. Run **Login** — the response contains the `accountID` to use in subsequent requests

You can customize the dev token with query parameters:
`GET /dev/token?sub=my-id&email=test@example.com&name=My+Name`

When `google.oauth.dev-mode=true`, the `GoogleTokenVerifier` parses the JWT header and payload
without verifying the cryptographic signature, so any properly-formed JWT will work.

### Full End-to-End with Real Google Tokens

For production or end-to-end testing with real Google authentication:

1.  Go to the [Google Cloud Console](https://console.cloud.google.com/)
2.  Create a project (or select an existing one)
3.  Navigate to **APIs & Services > Credentials**
4.  Click **Create Credentials > OAuth client ID**
5.  Choose **Web application** as the application type
6.  Add your front-end URL to **Authorized JavaScript origins** (e.g. `http://localhost:5173`)
7.  Add your front-end URL to **Authorized redirect URIs** (e.g. `http://localhost:5173`)
8.  Copy the generated **Client ID**
9.  Set it in `application.properties`:
    ```properties
    google.oauth.client-id=YOUR_CLIENT_ID
    google.oauth.dev-mode=false
    ```
10. Your front-end should use the [Google Identity Services library](https://developers.google.com/identity/gsi/web) to obtain an ID token, then POST it to `/account/login`.

To get a test ID token *without* a front-end:
- Visit [Google's OAuth 2.0 Playground](https://developers.google.com/oauthplayground/)
- Under "Scopes & APIs", enter `openid email profile` and click "Authorize APIs"
- Exchange the authorization code for tokens
- Use the `id_token` value in your login request

**Important:** The ID token must have `email_verified: true` for the backend to accept it.
Google only sets this for accounts with a verified email (most Google accounts qualify).

## Testing

```bash
./gradlew test
```

Coverage reports are generated at `build/reports/jacoco/test/html/index.html`.

## Build & Deploy

```bash
# Build native executable
./gradlew build -Dquarkus.package.type=native

# Docker build
docker build -f src/main/docker/Dockerfile.jvm -t pyramid-draft-backend .
```
# BUGS
1. Multiface card data is strange, Meld structure different from Day/Night, not sure about other flips. Will need something to get image URL's in a good structure in the base Object.

# Cleanup
1. Think of a good way to create commonality between the cube cobra structure of cards, and scryfall structure of cards. Or, rip out cube cobra structure and only take ID's (Would lose preffered art in the cube if you do not use the cube data)