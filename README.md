# magic-draft

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

1. Run MongoDB in docker
2. Run Json-server locally (Serves the dummy local cubes)
3. Update app.prop file to point to these locations
4. Run with ./gradlew clean build quarkusDev

To inspect the DB
mongoexport   --db MTGames   --collection Games   --out games.json

Small
20
After Merge
13

=32 Total in tool?

Medium


Large picks
32
after Merge
18

## TODO

### Core Improvements
- [ ] Add good unit tests
- [ ] Fix any weird styling errors
- [ ] Add better documentation
- [ ] Make the dev lifecycle easier and better documented

### Quarkus Upgrade
- [ ] Upgrade Quarkus to **3.20** and resolve related issues
- [ ] Migrate configuration to `application.yaml` after upgrade

### API & Cleanup
- [ ] Clean up dummy REST endpoints (remove if not needed)
- [ ] Add OpenAPI specifications

### Nice-to-Haves
- [ ] Investigate adding security / quality scans

# Project Overall Upgrades
- [ ] Get some nice display for reading through game data, is ELK the only option?
- [ ] Same thing for metrics, it would be cool to look at
- [ ] Add some integration tests for once the service is live
- [ ] Add easier way to test new images out
- [ ] Fix the auto renew process for good with the https Cert
- [ ] Yet Another UI Overhaul
- [ ] A draft engine partner would be cool for mock drafts, Timefold maybe?
- [ ] Integration of booster pack creations, scryfall integration as well
- [ ] Account Management for Games
