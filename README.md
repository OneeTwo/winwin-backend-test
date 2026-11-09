# WinWin.travel Backend Engineer Test Task

Two Java 21 / Spring Boot 4.1.2 services provide registration, JWT authentication,
and authenticated text transformation with PostgreSQL audit records.

## Architecture

```text
Client -- JWT --> auth-api:8080 -- X-Internal-Token --> data-api:8081
                       |
                       +--> postgres:5432 (users, processing_log)
```

`auth-api` hashes passwords with BCrypt, issues JWTs, and exposes `/api/process`.
It calls `/api/transform` in `data-api`, then stores the input, output, timestamp,
and authenticated JWT subject in `processing_log`. Failed transformations are not stored.
Compose provides service-name DNS on its default network.

## Requirements

- Docker Engine with Docker Compose v2 or later.
- For tests or running outside Docker: Java 21 and network access for the Maven
  Wrapper's first dependency download. A separate Maven installation is unnecessary.
- Available host ports: 8080, 8081, and 5434 (PostgreSQL, bound to localhost).

## Environment configuration

```bash
cp .env.example .env
# Edit .env before starting. Generate two independent secrets with:
openssl rand -hex 32
openssl rand -hex 32
```

| Variable | Docker Compose value / purpose |
| --- | --- |
| `POSTGRES_URL` | `jdbc:postgresql://postgres:5432/winwin_db` |
| `POSTGRES_USER` | Database username; `winwin` in the example |
| `POSTGRES_PASSWORD` | Set a strong database password |
| `JWT_SECRET` | Random signing secret, at least 32 bytes |
| `INTERNAL_TOKEN` | Separate random shared secret for both APIs |
| `DATA_API_URL` | `http://data-api:8081` |

`.env` is ignored by Git. The example contains placeholders, not production secrets.
Compose loads `.env` automatically. Spring Boot started directly does not: export
these variables in your shell or configure them in your IDE. For local processes,
use `POSTGRES_URL=jdbc:postgresql://localhost:5434/winwin_db` and
`DATA_API_URL=http://localhost:8081`.

For an existing database volume, use its **existing username and password**.
Changing initialization variables does not change users or passwords already stored
in PostgreSQL. The existing `postgres_data` volume and `winwin_db` database name
are preserved. Keep the same Compose project name/directory to reuse that volume.

## Start with Docker Compose

After configuring `.env`:

```bash
docker compose config --quiet
docker compose up --build
```

Or start in the background and wait for healthchecks:

```bash
docker compose up --build -d --wait
docker compose ps
docker compose logs -f auth-api data-api
```

Auth-api starts after PostgreSQL and data-api are healthy. API healthchecks make
unauthenticated HTTP requests and expect 401 from auth-api and 403 from data-api;
no secrets are needed for probes. These are HTTP readiness checks, not ongoing
checks of every downstream dependency. Images use separate build/runtime stages
and run the applications as non-root users. Tests are run separately below.

Stop the stack with `docker compose down`. It preserves PostgreSQL data.
Do not use `docker compose down -v` or remove the database volume if you need the data.

## API examples

Register (201, empty response; a repeated email returns 409):

```bash
curl -i http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"example-password-123"}'
```

Login (200, `{"token":"..."}`):

```bash
curl -sS http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"example-password-123"}'
```

Copy the returned token into `JWT` in your shell, then process text (200):

```bash
read -r -s -p 'JWT: ' JWT; echo
curl -i http://localhost:8080/api/process \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello World"}'
unset JWT
```

Response: `{"result":"HELLO WORLD"}`. The user ID comes from the verified JWT,
never from the request body. Missing/invalid JWTs return 401; missing, null, or
blank text returns 400; downstream failures return 502 without a processing log.

Direct transform (200, for internal-service testing):

```bash
# Enter the INTERNAL_TOKEN configured in .env.
read -r -s -p 'Internal token: ' INTERNAL_TOKEN; echo
curl -i http://localhost:8081/api/transform \
  -H "X-Internal-Token: $INTERNAL_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello World"}'
unset INTERNAL_TOKEN
```

Response: `{"result":"HELLO WORLD"}`. Missing or incorrect internal tokens return 403.
The `read` examples use Bash and keep secrets out of shell command history.

## Tests

```bash
(cd auth-api && ./mvnw clean test)
(cd data-api && ./mvnw clean test)
```

Tests do not require Docker or external PostgreSQL. Auth-api tests use H2 and mock
the data-api HTTP interaction; some JWT tests start a local HTTP server. Coverage
includes registration, login, JWT protection, text validation, downstream failures,
processing-log persistence and ownership, and internal-token validation.

## Security notes

- Use independent random secrets; never commit `.env`, JWTs, or passwords.
- Tokens are sent only to the service that needs them. Internal tokens and
  downstream error details are not returned to clients; Compose disables SQL logging.
- Ports 8080 and 8081 are published for this task. Restrict data-api access to the
  internal network in a production deployment, and terminate HTTPS at a trusted proxy.
- Environment variables suit this exercise; use a secret manager for production.
- Hibernate's existing schema-update setting is retained. Use reviewed migrations,
  backups, and managed credential rotation for a production database.
