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

## Profiles

| Profile | Use |
|---------|-----|
| `dev`   | Local development with json-server |
| `prod`  | Production with CubeCobra API |
| `gapped`| Dev variant without local json-server |

Configure via `quarkus.profile` in `application.properties`.

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
