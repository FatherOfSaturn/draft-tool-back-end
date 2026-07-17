# Pyramid Draft Backend

Quarkus backend for the Pyramid Draft MTG cube drafting application.

## Prerequisites

- Java 17
- Docker
- Node.js (for json-server)

## Quick Start

```bash
# Start MongoDB
docker compose -f src/main/docker/docker-compose.yml up -d

# Start json-server (dev only)
json-server --watch db.json

# Run the app
./gradlew clean build quarkusDev
```

## Configuration

Configuration uses YAML files with Quarkus profile support:

| File | Purpose |
|------|---------|
| `application.yaml` | Shared config (CORS, Scryfall, CubeCobra, OAuth client ID) |
| `application-dev.yaml` | Dev overrides (localhost MongoDB, dev CORS origins) |
| `application-prod.yaml` | Prod overrides (env var MongoDB, production CORS origin) |

### Profiles

| Profile | Use |
|---------|-----|
| `dev`   | Local development with local MongoDB |
| `prod`  | Production with CubeCobra API |

- **Default:** `prod` is active by default in Quarkus
- **Dev mode:** `./gradlew quarkusDev` automatically activates the `dev` profile
- **Override:** Set `QUARKUS_PROFILE=dev` or `QUARKUS_PROFILE=prod` as an environment variable

## Testing the Account System

### Quick Start (Dev Mode)

The backend ships with a **dev mode** that bypasses Google JWT signature verification.
Dev mode is enabled by default in `application-dev.yaml` via `google.oauth.dev-mode=true`.

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
9.  Set it in `application.yaml`:
    ```yaml
    google.oauth.client-id: YOUR_CLIENT_ID
    ```
    And disable dev mode in `application-dev.yaml`:
    ```yaml
    google.oauth.dev-mode: false
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
