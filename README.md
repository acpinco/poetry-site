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

Swagger is intentionally public so people can explore the API. Its controls do not bypass normal
authentication and authorization: anonymous visitors can use public read endpoints, but write
operations still require a valid session.

1. Start the local services, then start the application:

   ```bash
   ./mvnw spring-boot:run
   ```

2. Open http://localhost:8080/swagger-ui.html. The raw OpenAPI description is at
   http://localhost:8080/v3/api-docs.

`SWAGGER_ENABLED` defaults to `true`. Set it to `false` only if the public documentation must be
disabled. The website root remains the normal landing page; it never redirects to Swagger.

## Production deployment on the server

Cloudflare Tunnel terminates public HTTPS and forwards traffic to Docker Caddy on the server. The
production stack exposes Caddy only at `127.0.0.1:8081`; PostgreSQL and the application have no
host ports. The intended path is:

```text
Internet -> Cloudflare -> cloudflared -> 127.0.0.1:8081 -> Docker Caddy -> application -> PostgreSQL
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

   Replace the PostgreSQL password before starting. Set SMTP values and `MAIL_FROM` when you are ready to test magic-link delivery. `MAIL_FROM` must use a domain verified with the email provider. Do not add `.env.production` to Git.

4. Build and start the stack on the server:

   ```bash
   ./scripts/deploy-server.sh
   ```

5. From the server, verify Docker Caddy responds locally:

   ```bash
   curl -iL http://127.0.0.1:8081/swagger-ui.html
   ```

6. Confirm https://poetry.timberlinelab.com/swagger-ui.html loads publicly.

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
