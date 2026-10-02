# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Think or Drink Poetry: a Spring Boot 4 / Java 21 backend with PostgreSQL 18, a React 19 + Vite + Tailwind 4 SPA in `frontend/`, and Caddy in front, deployed with Docker Compose on a single server behind Cloudflare Tunnel.

## Commands

Backend (repo root; integration tests need Docker for Testcontainers):

```bash
./mvnw test                                   # compile (-Xlint:all -Werror: any warning fails) + all tests
./mvnw test -Dtest=PublicCatalogIntegrationTest            # one class
./mvnw test -Dtest=AuthAndPoemFlowIntegrationTest#signInRecordsLastSeenWithoutChangingTheProfileUpdatedAt
./mvnw spotless:apply                         # format Java (palantir-java-format); CI runs spotless:check
./mvnw spring-boot:run                        # run locally against the Compose Postgres/Mailpit
```

Frontend (`frontend/`):

```bash
npm run check          # prettier check + eslint + tsc + vitest + vite build (what CI runs)
npx vitest run src/home/listView.test.ts      # one test file
npx prettier --write src
```

Node is not installed on this dev machine; run npm commands in a container, e.g.
`docker run --rm -e HOME=/tmp -v "$PWD":/w -w /w node:22-alpine sh -c "npm run check"`.
The Compose `frontend` service also runs as root and mounts `./frontend`, so `node_modules`/`dist` can end up root-owned; chown them back if tools hit EACCES.

Local stack: `docker compose up -d` (Postgres on :5432, Mailpit UI :8025, app :8080, Vite :5173), or `./scripts/deploy-dev.sh`. Backend changes need `docker compose up --detach --build app`; Vite hot-reloads the frontend. Settings come from `.env` (copy `.env.example`; Spring also reads `.env` directly via `spring.config.import`).

Operational commands (deploy, backups, restore, psql) live in `docs/COMMANDS.txt`; production deploy is `./scripts/deploy-server.sh` on the server.

## Request routing (three places must agree)

The backend serves two kinds of pages, and `deploy/Caddyfile` (production) and the proxy in `frontend/vite.config.ts` (dev) must route the same paths:

- **Backend, server-rendered for SEO** (Thymeleaf, `publicpage/PublicPageController`, templates in `src/main/resources/templates/public/`): `/`, `/poems/{id}/{slug}`, `/poets/{id}/{slug}[/bio]`, `/poem-of-the-day`, `/sitemap.xml`, `/robots.txt`. Slugs are decorative; lookups are by id, URLs come from `PublicLinks`.
- **Backend JSON API**: `/api/**`, plus Swagger (`/swagger-ui*`, `/v3/*`, public by design).
- **React SPA** (`frontend/src/App.tsx` routes): `/home`, `/sign-in`, `/account/setup`, `/my-poems/new`, `/my-poems/:id/edit`, `/contact`, `/admin`. Adding an SPA route also means adding it to the Caddyfile `@spa` matcher.

## Backend architecture

Packages are by feature under `com.thinkordrinkpoetry` (`auth`, `poet`, `poem`, `admin`, `discovery`, `publicpage`, `contact`, `web`). Controllers are thin; rules live in services (`PoetService`, `PoemService`, `AdminService`).

- **Two data paths, on purpose.** Writes to a poet's own data go through JPA entities (`Poet`, `Poem`). All public reading (discovery API, server-rendered pages, sitemap) goes through `discovery/PoemCatalog` (JdbcTemplate) over the views `poet_listing` / `poem_listing` (migration V7). The views hold the display rules once: public name = pen name else full name; publication date = 1999 `legacy_submitted_on` if present, else `created_at`; 160-char excerpt. Don't re-derive these in new queries.
- **Database-managed columns.** `created_at`/`updated_at` come from column defaults and `set_updated_at` triggers; `full_name` is a generated column. Entities mark them `@Generated` and services `flush()`/`saveAndFlush()` before building a response so the values are read back. The `poet` trigger deliberately ignores changes to `last_seen_at` (sitemap `lastmod` uses `updated_at`). `last_seen_at` is written only by `PoetRepository.markSeen` (on sign-in, then at most hourly per request).
- **Schema** is owned by Flyway (`src/main/resources/db/migration`, Hibernate `ddl-auto: validate`). Never edit an applied migration; add the next `V{n}__*.sql`.
- **Auth** is passwordless. `POST /api/auth/magic-links` (sign in) only emails addresses that already belong to a poet and returns 204 either way; `POST /api/auth/sign-up` requires a Cloudflare Turnstile token (`TurnstileVerifier`, fails closed without `TURNSTILE_SECRET_KEY`). Clicking the link creates a server-side session (cookie `poetry_session`, SHA-256-hashed tokens in `user_session`); `SessionAuthenticationFilter` resolves it into an `AuthenticatedUser` and grants `ROLE_ADMIN` to active admins, which `SecurityConfiguration` requires for `/api/admin/**`. A person can be signed in with no poet profile yet (`poetId` null) until they finish `/account/setup`. The role is set at profile creation when the email equals `ADMIN_EMAIL`.
- **Magic links are intentionally reusable until they expire** (15 min); do not "fix" this to single-use.
- **Rate limiting** is in-memory fixed windows (`web/FixedWindowRateLimiter`): sign-in email per client IP, per address (silently skipped, so nobody can lock someone out), and site-wide; discovery API per IP. Client IP comes from `CF-Connecting-IP` via `web/ClientIpResolver`. Never use `X-Forwarded-For`, and keep the app port unpublished in production (only Caddy is reachable).
- **Email**: sign-in/notice mail is `@Async` (`MagicLinkMailer`, Thymeleaf templates under `templates/email/`); failures are logged, not returned. The contact form sends synchronously so the poet sees failures.
- **Errors**: `web/ApiExceptionHandler` turns every `@RestController` error into RFC 9457 problem details; throw `ResponseStatusException` with a visitor-safe reason. Server-rendered pages keep normal HTML error pages.
- **Poem of the Day** is assigned once per day in `SITE_TIME_ZONE` (default America/Denver) and stored in `poem_of_the_day`, cycling through all poems before repeating.

Required settings: `CONTACT_EMAIL` and `ADMIN_CONTACT_EMAIL` have no defaults and startup fails without them. Tests supply them in `src/test/resources/application.properties`.

## Frontend architecture

- `session.tsx` loads `/api/auth/me` once (`SessionProvider`/`useSession`); `App.tsx` guards routes with `RequireSession` / `RequirePoet`. Call `refresh()` after anything that changes the session (profile creation, sign-out).
- All HTTP goes through `api.ts` (`getJson`, `sendJson`, `ApiError` with `status`); pages map status codes to their own messages.
- **The URL is the source of truth on `/home`** (`pages/HomePage.tsx`): `?view=mine|all`, `?poet=<id>`, optional `&poem=<id>`. A bare `?poem=<id>` (linked from server-rendered bio pages) resolves to that poem's poet; legacy `?mine=1` still works. The mapping is `home/listView.ts` (`resolveListView`, unit-tested). Clicks change the URL; effects load data and cancel stale responses with a `cancelled` flag.
- ESLint runs the React Compiler hook rules, which reject synchronous `setState` in effects and reading refs during render. Derive values during render or set state in async callbacks; use `useEffectEvent` for callbacks an effect shouldn't depend on.
- Colours are Tailwind theme tokens defined once in `src/index.css` `@theme` (`text-gold`, `bg-night`, `border-line`, …). Use them, never raw hex.
- `useMediaQuery(DESKTOP_QUERY)` renders either the mobile or the desktop layout, not both.

## Tests

- Integration tests use Testcontainers; each test class starts its own Postgres container. Tests must never touch the Compose database. `@MockitoBean` mocks `MagicLinkMailer` and `TurnstileVerifier`; because mail is async, verify with `timeout(...)`/`after(...)`.
- `discovery/PublicCatalogIntegrationTest` pins exactly what the public API and server-rendered pages return (escaping, canonical URLs, legacy dates, sitemap). Keep it passing when touching `PoemCatalog`, the views, or the templates.
- `AdminPoetListIntegrationTest` signs in by inserting a `user_session` row whose hash is the SHA-256 of the cookie value; reuse that pattern for admin tests.

## Deployment notes

- Production: `compose.production.yaml` (app + Postgres on an internal network; Caddy built by `deploy/Caddy.Dockerfile`, which also builds the frontend into `/srv`). Secrets in `.env.production` (see `.env.production.example`). Migrations run on app startup.
- Caddy sets security headers (deferred, so they replace the backend's). The CSP is deliberately narrow; tightening `script-src` would need Turnstile, Google Fonts, and Cloudflare-injected scripts allow-listed.
- Backups: `sudo ./scripts/backup-production-db.sh` reads `.env.backup` (from `.env.backup.example`), keeps 31 days on the SSD, and optionally copies to a USB drive mounted at `USB_BACKUP_MOUNT` (it refuses to write if the drive isn't actually mounted).
