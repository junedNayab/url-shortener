# URL Shortener Service

A URL shortener REST API built with **Spring Boot 3.2**, **PostgreSQL**, **Redis**, and **JWT authentication**.

```
┌─────────┐     ┌──────────────────┐     ┌────────────┐
│  Client  │────▶│  Spring Boot App │────▶│ PostgreSQL │
│ (cURL /  │◀────│  (REST API)      │     │ (URLs,     │
│ Browser) │     │                  │────▶│  Users,    │
└─────────┘     │  - JWT Auth      │     │  Clicks)   │
                │  - Rate Limiting │     └────────────┘
                │  - Validation    │
                │  - Swagger UI    │────▶┌────────────┐
                └──────────────────┘     │   Redis    │
                                         │ (Cache +   │
                                         │ Rate Limit)│
                                         └────────────┘
```

## Tech Stack

| Layer       | Technology                              |
|-------------|-----------------------------------------|
| Language    | Java 17                                 |
| Framework   | Spring Boot 3.2, Spring Security, JPA   |
| Database    | PostgreSQL 16                            |
| Cache       | Redis 7.2                                |
| Auth        | JWT (jjwt 0.12)                          |
| Docs        | Swagger / OpenAPI 3 (springdoc)          |
| Build       | Maven                                    |
| Container   | Docker, Docker Compose                   |
| Tests       | JUnit 5, Mockito, MockMvc                |

## API Endpoints

### Authentication (Public)
| Method | Endpoint            | Description            |
|--------|---------------------|------------------------|
| POST   | `/api/auth/signup`  | Register a new user    |
| POST   | `/api/auth/login`   | Login, returns JWT     |

### URL Operations (Requires JWT)
| Method | Endpoint                | Description                    |
|--------|-------------------------|--------------------------------|
| POST   | `/api/urls/shorten`     | Shorten a URL                  |
| GET    | `/api/urls`             | List your shortened URLs       |
| DELETE | `/api/urls/{shortCode}` | Delete a shortened URL         |

### Redirect (Public)
| Method | Endpoint          | Description                     |
|--------|-------------------|---------------------------------|
| GET    | `/s/{shortCode}`  | 302 redirect to original URL    |

### Analytics (Requires JWT)
| Method | Endpoint                     | Description                 |
|--------|------------------------------|-----------------------------|
| GET    | `/api/analytics/{shortCode}` | Click stats for a URL       |

## Quick Start

### Option 1: Docker Compose (Recommended)

Create your local environment file and replace `JWT_SECRET` with a random value of at least 32 characters:

```bash
cp .env.example .env
```

```bash
docker compose up -d --build
```

App starts at `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Option 2: Local Development

**Prerequisites:** Java 17+, Maven, PostgreSQL, Redis running locally.

1. Create the database:
```sql
CREATE DATABASE urlshortener;
```

2. Run the app:
```bash
export JWT_SECRET="replace-with-a-random-secret-at-least-32-characters-long"
mvn spring-boot:run
```

On Windows PowerShell, set the variable with `$env:JWT_SECRET = "..."` before starting the app.

### Option 3: No Docker, no database install

For a quick look at the API, run against an in-memory H2 database with Redis absent. H2 is already
on the test classpath, so nothing needs to be installed:

```powershell
$env:JWT_SECRET = "LocalDemoSecretKeyThatIsAtLeast32CharactersLong"
$env:SPRING_DATASOURCE_URL = "jdbc:h2:mem:demo"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.h2.Driver"
$env:SPRING_DATASOURCE_USERNAME = "sa"
$env:SPRING_DATASOURCE_PASSWORD = ""
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.useTestClasspath=true"
```

Data is discarded when the app stops.

## Web Interface

There is no custom front end. The browser-facing interface is **Swagger UI** at
`http://localhost:8080/swagger-ui.html`, which lists every endpoint and can call them directly.
To use the authenticated endpoints, call `/api/auth/signup`, copy the `token` from the response,
click **Authorize**, and enter `Bearer <token>`.

## Deployment

This is a stateful container app, so platforms built for static sites and serverless functions
(Vercel, Netlify, GitHub Pages) cannot host it. It needs a long-running JVM plus PostgreSQL.
Redis is optional: the app falls back to PostgreSQL if Redis is unreachable.

### Free option: Render + Neon

1. **Database:** create a free project at [neon.com](https://neon.com). Copy the connection details.
2. **App:** at [render.com](https://render.com), choose **New → Web Service**, connect this repo,
   and pick runtime **Docker** with the **Free** instance type.
3. Set these environment variables on the Render service:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>/<db>?sslmode=require
SPRING_DATASOURCE_USERNAME=<neon-user>
SPRING_DATASOURCE_PASSWORD=<neon-password>
JWT_SECRET=<random value, at least 32 characters>
APP_BASE_URL=https://<your-service>.onrender.com
```

4. Optionally add a free **Render Key Value** instance for caching and rate limiting, then set
   `SPRING_DATA_REDIS_HOST` and `SPRING_DATA_REDIS_PORT` to its internal host and port.

Free-tier limits worth knowing: Render spins the service down after 15 minutes of inactivity, so the
next request takes roughly a minute to respond, and Neon suspends the database after 5 minutes idle.
Render's own free PostgreSQL is deleted 30 days after creation, which is why Neon is used instead.

Swagger UI is then available at `https://<your-service>.onrender.com/swagger-ui.html`.

## Usage Examples

### 1. Sign up
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"John","email":"john@example.com","password":"secret123"}'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "john@example.com",
  "name": "John"
}
```

### 2. Shorten a URL
```bash
curl -X POST http://localhost:8080/api/urls/shorten \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"originalUrl":"https://www.google.com","customAlias":"google","expiresInDays":30}'
```

Response:
```json
{
  "shortUrl": "http://localhost:8080/s/google",
  "shortCode": "google",
  "originalUrl": "https://www.google.com",
  "createdAt": "2026-04-01T10:00:00",
  "expiresAt": "2026-05-01T10:00:00"
}
```

### 3. Redirect
```bash
curl -L http://localhost:8080/s/google
# → Redirects to https://www.google.com
```

### 4. View Analytics
```bash
curl http://localhost:8080/api/analytics/google \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Project Structure

```
src/main/java/com/urlshortener/
├── UrlShortenerApplication.java    # Entry point
├── config/                         # Security, Redis, OpenAPI, Web config
├── controller/                     # REST controllers
├── dto/
│   ├── request/                    # Request records (validated)
│   └── response/                   # Response records
├── entity/                         # JPA entities (User, Url, ClickEvent)
├── exception/                      # Custom exceptions + global handler
├── ratelimit/                      # Redis-based rate limiting
├── repository/                     # Spring Data JPA repositories
├── security/                       # JWT provider, filter, UserDetailsService
├── service/                        # Business logic
└── util/                           # Base62 encoder
```

## Key Features to Study

1. **JWT Authentication** — Token generation, validation, filter chain (`security/` package)
2. **Spring Security 6** — Lambda DSL configuration, stateless sessions (`config/SecurityConfig.java`)
3. **Redis Caching** — Manual cache with StringRedisTemplate for URL lookups
4. **Rate Limiting** — Redis-based IP rate limiting with HandlerInterceptor
5. **Validation** — Bean validation with `@Valid`, custom error responses
6. **Exception Handling** — `@RestControllerAdvice` with typed exception handlers
7. **Java Records** — DTOs as records (immutable, concise)
8. **Docker** — Multi-stage Dockerfile, Docker Compose for full stack
9. **OpenAPI/Swagger** — Auto-generated API docs with JWT auth support
10. **Testing** — Unit tests (Mockito), integration tests (MockMvc, @WithMockUser)

## Configuration

| Property                       | Default                    | Description              |
|--------------------------------|----------------------------|--------------------------|
| `JWT_SECRET`                   | Required                   | JWT signing key          |
| `app.jwt.expiration-ms`        | 86400000 (24h)             | Token expiry             |
| `APP_BASE_URL`                 | http://localhost:8080      | Base URL for short links |
| `app.rate-limit.max-requests`  | 100                        | Max requests per window  |
| `app.rate-limit.window-seconds`| 60                         | Rate limit window        |

## License

MIT
