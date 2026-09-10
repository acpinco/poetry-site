# Poetry Site

## Local development environment

This project uses Docker Compose for its local PostgreSQL database and Mailpit test inbox.

1. The local `.env` file is created during setup and is ignored by Git. To recreate it from the safe template if needed:

   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and replace `POSTGRES_PASSWORD` with a unique local password.

3. Validate the configuration and start services:

   ```bash
   docker compose config
   docker compose up -d
   docker compose ps
   ```

4. Open Mailpit at http://localhost:8025.

PostgreSQL is available only from this computer at `localhost:5432`.
Mailpit SMTP is available only from this computer at `localhost:1025`.

## Manual API testing

The local `.env` activates the `local` Spring profile, which enables Swagger UI only on your computer.

1. Start the local services, then start the application:

   ```bash
   ./mvnw spring-boot:run
   ```

2. Open http://localhost:8080/swagger-ui.html. The raw OpenAPI description is at http://localhost:8080/v3/api-docs.

Swagger and its OpenAPI endpoints are disabled by default. While `SWAGGER_ENABLED=true`, the site root (`/`) redirects to Swagger UI as a temporary landing page. Set it to `false` after manual API testing; the root redirect then disappears. Do not set `SPRING_PROFILES_ACTIVE=local` in production.

## Production deployment on the server

Tailscale remains installed on the server host and terminates public HTTPS. The production Docker stack exposes Caddy only at `127.0.0.1:8081`; PostgreSQL and the application have no host ports. The intended path is:

```text
Internet -> Tailscale Funnel -> 127.0.0.1:8081 -> Docker Caddy -> application -> PostgreSQL
```

1. If the GitHub repository is private, give the server read-only access before cloning. On the server, generate a dedicated key:

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519_github_poetry -C "thinkordrinkpoetry server"
   cat ~/.ssh/id_ed25519_github_poetry.pub
   ```

   In the GitHub repository, open **Settings** -> **Deploy keys** -> **Add deploy key**, paste that public key, and leave **Allow write access** unchecked. Create `~/.ssh/config` on the server with this entry:

   ```text
   Host github.com
     IdentityFile ~/.ssh/id_ed25519_github_poetry
     IdentitiesOnly yes
   ```

2. On the server, clone the repository and enter it:

   ```bash
   git clone git@github.com:acpinco/poetry-site.git ~/projects/poetry-site
   cd ~/projects/poetry-site
   ```

3. Create the server-only secret file and lock down its permissions:

   ```bash
   cp .env.production.example .env.production
   chmod 600 .env.production
   nano .env.production
   ```

   Replace the PostgreSQL password before starting. Set SMTP values when you are ready to test magic-link delivery. Do not add `.env.production` to Git.

4. Build and start the stack on the server:

   ```bash
   ./scripts/deploy-server.sh
   ```

5. From the server, verify Docker Caddy responds locally before changing Funnel:

   ```bash
   curl -iL http://127.0.0.1:8081/swagger-ui.html
   ```

   A `200` response confirms the temporary public Swagger UI is available. Then replace the existing Funnel listener with Docker Caddy. This causes a brief public interruption but does not affect the local containers:

   ```bash
   sudo tailscale funnel reset
   sudo tailscale funnel --bg --https=443 http://127.0.0.1:8081
   sudo tailscale funnel status
   ```

6. Test `https://thinkordrinkpoetry.tail0e35ab.ts.net/swagger-ui.html` from a non-tailnet browser. Keep the old host Caddy running as rollback until this succeeds. Afterwards, disable it:

   ```bash
   sudo systemctl disable --now caddy
   ```

For an update, pull the reviewed Git commit first, then rerun the script:

```bash
git pull --ff-only
./scripts/deploy-server.sh
```

To inspect the production stack:

```bash
sudo docker compose -f compose.production.yaml ps
sudo docker compose -f compose.production.yaml logs --follow
```

## Stopping services

```bash
docker compose down
```

This preserves the named PostgreSQL volume and its data.

## Test database isolation

Automated tests must never use this Compose PostgreSQL database. Spring integration tests will use Testcontainers to create an isolated temporary PostgreSQL container for each test run. Unit tests will not require a database.
