# vtesdecks-back springboot

This project is an Springboot application serving as the back-end for [VTES Decks](vtesdecks.com), a online card search,
TWD browser and deck builder for Vampire the Eternal Struggle (VTES)

## Prerequisites

Before you begin, ensure you have met the following requirements:

- Java 21
- Maven 3.6.0 or higher
- MySQL 8.0.21
- A tool for API testing (e.g., Postman or cURL)

## Getting Started

Follow these steps to set up the project on your local machine.

### 1. Clone the Repository

```bash
Copiar código
git clone <repository-url>
cd <project-directory>
```

### 2. Set Up MySQL Database

- Start your MySQL server.
- Create a database for the project:

```sql 
CREATE DATABASE vtes_decks_v1;
````

- Update the src/main/resources/application-local.properties file with your MySQL database configuration:

```properties
datasource.jdbcUrl=jdbc:mysql://localhost:3306/<database_name>
datasource.username=<your_mysql_username>
datasource.password=<your_mysql_password>
```

### 3. Build the Application

Build the application using Maven:

```bash
mvn clean install
```

### 4. Run the Application with Local Profile

Run the Spring Boot application using the local profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Alternatively, you can run the application as a JAR file:

```bash
java -jar target/spring-boot-vtes-decks-<version>.jar --spring.profiles.active=local
```

### 5. Run the Scheduler Manually

After starting the application, manually trigger the scheduler to scrape tournament winning decks by sending a request
to the following endpoint:

- Endpoint: /admin/scheduler/scrap_decks
- Method: POST

Use an API testing tool or cURL to trigger the scheduler:

```bash
curl -X POST http://localhost:8080/admin/scheduler/scrap_decks
```

### Card shop synchronization

VTES Market supplies DriveThruCards, GamePod, TCG Market and eBay offers through
`/api/card_offers`. The market scheduler synchronizes them every six hours and
is available as the `market` manual admin job. CardGameGeek retains its separate
scheduler.

The import checks the complete paginated total and unique offer IDs before
reconciling offers. Failed downloads or imports leave existing offers intact.
Legacy DriveThruCards and GamePod rows without a locale are matched as English;
the feed supplies the stored locale, including separate Spanish GamePod offers.

Offers retain nullable `seller`, `country` and `continent` fields from Market.
The cheapest offer is retained per card, platform, printing, language and seller;
location changes update that offer without changing its identity. Missing sellers
are stored as SQL NULL. The unique index treats missing printing, language and
seller as one value while leaving their stored values nullable. Conflicting
legacy duplicates stop the migration without deleting records.

Both the card-info shop list and `/cards/{id}/shops` expose these optional fields.
Only the seller name `tcgmarket` is omitted from API responses (case-insensitive).
Its stored value remains unchanged so offer identity and reconciliation retain
the upstream seller; all other seller names are exposed when present.
The initial card-info response uses the existing compact summary for every
platform, including eBay. Show more reveals all retained sellers and printings.
Deploy migration
`V1.0.106__add_card_shop_seller_and_location.sql` and the backend before the
frontend. The next successful market synchronization fills the new metadata.
Market supplies seller as a nullable string; older responses without it remain
compatible.

The MySQL migration tests can be run against a disposable loopback MySQL 8
instance with an empty-password root account:

```bash
mvn test -Dtest=CardShopSellerMigrationTest -Dvtes.test.mysql.url=jdbc:mysql://127.0.0.1:3306/
```

Each test creates and removes its own isolated schema. Without that property,
the MySQL-specific tests are skipped; the other offer tests run normally.

### Archon tournament deck schedule

Archon finalist decks are imported daily at 07:30, with extra passes on Friday,
Saturday and Sunday at 18:30, 20:30 and 22:30. Follow-up passes at 00:30 and 02:30
on Saturday, Sunday and Monday pick up results published after midnight,
including Sunday tournaments. All times use the application's scheduler time zone,
as the other scheduled jobs do.

Each pass downloads the Archon export and processes finished tournaments from all
available years. Published decklists are imported, and existing tournament decks
can receive missing VEKN IDs from event results even when no decklist is available.
Existing VEKN IDs are preserved, and verified decks only receive missing VEKN IDs.
An unfinished tournament is picked up by a later pass.

Before creating a tournament deck, both importers also check for an exact match
of all crypt and library card IDs and quantities, the normalized author, and a
compatible finishing position on the same calendar day, ignoring the time.
This check works even when tournament names and event IDs differ. Matching lists
on different days are accepted as separate results unless their event identity
already resolves to an existing result. Archon skips duplicate imports and only
fills an empty VEKN ID when there is exactly one matching deck; its event ID,
cards, and other metadata are preserved. Ambiguous matches are skipped without
enrichment.

Archon and TWDA each process their input with up to four workers. Set
`jobs.tournamentImportWorkers` to change this limit, or use `1` for sequential
processing. Each scheduler waits for its workers before reporting completion.
Imports sharing a calendar day, deck ID, or event ID are coordinated before
opening their transactions, so duplicate checks see previously committed results.
Card changes are flushed at transaction commit rather than after each card.

New tournament decks from either importer require nonblank `eventId` and `veknId`.
TWDA resolves the player ID before inserting the deck. If either ID is missing,
the importer skips the insertion and logs a warning with the source and tournament
details for review. Existing decks can still be updated or enriched without both
IDs; duplicate detection and verified-deck protection continue to apply.

TWDA also fills missing VEKN IDs by looking up the deck's full author name in
`archon_user`, ignoring letter case, accents, and surrounding whitespace. Player
names and IDs are loaded once per run through a Spring Data projection and matched
in Java, using the same immutable lookup across workers. If there is no
exact match, it tries name containment in either direction using whole words
and at least two words in each name (for example, `Pedro Coelho` and
`Pedro Coelho Mendes Bueno Barbosa`). It also accepts the deck's words as prefixes
of Archon name words in the same order, allowing intermediate names (for example,
`Greg Williams` and `Gregory Alan Williams`). Each lookup must return
exactly one player; missing or ambiguous names are left unchanged without
logging warnings. Existing IDs are preserved. This enrichment is allowed for
verified decks and unique duplicate matches, and updates only `vekn_id`, preserving
the author, event ID, cards, ownership, and modification timestamp.

The three schedules can be overridden independently:

| Property | Default cron |
| --- | --- |
| `jobs.scrappingArchonDecksCron` | `0 30 7 * * *` |
| `jobs.scrappingArchonDecksWeekendCron` | `0 30 18,20,22 * * FRI-SUN` |
| `jobs.scrappingArchonDecksOvernightCron` | `0 30 0,2 * * SAT,SUN,MON` |

Set a property to `-` to disable that schedule. To disable all automatic Archon
deck imports, set all three properties to `-`.

## Additional Information

The application will listen on port 8080 by default. You can change this by updating the server.port property in the
application-local.properties file.

Make sure the MySQL service is running, and the credentials provided in the application-local.properties are correct to
avoid connection issues.

### Health endpoint

`GET /health` (also available at `/api/health` for the reverse proxy) returns HTTP 200
with `{"status":"UP"}` without authentication. Responses use `Cache-Control: no-store`.
This is a lightweight liveness check; it does not check the database or external services.

## Troubleshooting

### Builder archetype classification

`POST /api/1.0/user/decks/builder/archetype` requires authentication and accepts an
`ApiDeckBuilder` JSON body. It classifies the supplied `cards` without loading or
saving the submitted deck ID. HTTP 200 returns the best archetype's `id`, `name`,
`icon` and `type`; HTTP 204 means no archetype matched, including an empty draft.
Invalid card entries, unknown active cards and duplicate IDs return HTTP 400.
Zero-quantity considering cards are ignored.

`DeckArchetypeMatcher` is shared with scheduled classification: every card and
attribute requirement must pass, the best similarity across both reference decks
must reach 0.5, and the strongest qualifying archetype wins. Equal scores prefer
the lowest archetype ID. This identifies an archetype, not deck legality; challenge
validation must also retain the existing deck-building checks and require the
returned archetype ID to equal the challenge's target.

### Anonymous proxy PDFs

`POST /api/1.0/proxy` caps PDF output at 20 MiB while writing (HTTP 413).

If you encounter any issues with the database connection, ensure that your MySQL server is running and that the
credentials and database URL are correct.
Check the application logs for any errors during startup or when triggering the scheduler.

## CONTRIBUTION

Contributions are welcome! Please create an issue to discuss your proposed feature or fix before starting work on it.

We don't have specific coding style or test requirements at this time.
