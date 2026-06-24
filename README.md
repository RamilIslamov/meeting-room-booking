# Meeting Room Booking — Backend

[![CI](https://github.com/RamilIslamov/meeting-room-booking/actions/workflows/ci.yml/badge.svg)](https://github.com/RamilIslamov/meeting-room-booking/actions/workflows/ci.yml)

REST API for booking meeting rooms. Users browse rooms and reserve time slots;
administrators manage rooms. Bookings are validated against business rules,
including time‑overlap conflict detection. Built as a portfolio project on a
current Spring Boot 4 / Java 21 stack.

> Developed a meeting room booking application backend with Java 21 and Spring Boot.
> Implemented JWT authentication, role‑based access control, room management, and
> booking creation/cancellation with time‑conflict validation. Added Liquibase
> database migrations, a REST API documented with OpenAPI/Swagger, Dockerized
> PostgreSQL, and unit + Testcontainers integration tests.

## Tech stack

- **Java 21**, **Spring Boot 4.1** (Spring Web MVC, Spring Data JPA, Spring Security)
- **PostgreSQL 16** + **Liquibase** migrations
- **JWT** auth (JJWT, HS256), **BCrypt** password hashing
- **springdoc-openapi** 3.0 (Swagger UI)
- **JUnit 5**, **Mockito**, **Testcontainers** (real PostgreSQL in integration tests)
- **Docker Compose** for local PostgreSQL
- Build: **Maven** (wrapper included)

## Features

- Registration and login returning a JWT; `GET /api/users/me`
- Roles `USER` and `ADMIN`; admin‑only room writes via method security
- Rooms: list/get for any authenticated user; create/update/soft‑delete for admins
- Bookings:
  - create with validation — `start < end`, not in the past, room exists & active,
    within working hours, under the max duration, inside the advance horizon, and
    **no overlap with an existing active booking** in the same room (`409`)
  - overlap is also enforced by a PostgreSQL exclusion constraint, closing the
    concurrent check‑then‑insert race
  - list my bookings, list a room's bookings for a given date
  - cancel your own booking (admins can cancel any); cancelling frees the slot
- Consistent JSON error responses (`400/401/403/404/409/429`)
- Rate limiting on `/api/auth/**` and configurable CORS for the browser frontend
- OpenAPI 3 spec + Swagger UI with a JWT "Authorize" button

## Running locally

Prerequisites: **Docker** (and **JDK 21** for the local‑dev option).

### Option A — full stack with Docker Compose (app + db)

```bash
docker compose up -d --build      # builds the app image and starts app + PostgreSQL
```

The API comes up at http://localhost:8080 once PostgreSQL is healthy.

### Option B — app from source, PostgreSQL in Docker

```bash
docker compose up -d postgres     # just the database
./mvnw spring-boot:run            # app on http://localhost:8080
```

A default admin user is seeded on first startup:

| Field    | Value                 |
|----------|-----------------------|
| email    | `admin@booking.local` |
| password | `admin12345`          |

Override in production with the `ADMIN_EMAIL` / `ADMIN_PASSWORD` (and `JWT_SECRET`)
environment variables.

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI spec:** http://localhost:8080/v3/api-docs
- **Health:** http://localhost:8080/api/health

### Configuration

Defaults live in `src/main/resources/application.yaml` and can be overridden via
environment variables:

| Variable                     | Default                                  |
|------------------------------|------------------------------------------|
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/booking` |
| `SPRING_DATASOURCE_USERNAME` | `booking`                                |
| `SPRING_DATASOURCE_PASSWORD` | `booking`                                |
| `JWT_SECRET`                 | dev‑only secret (change in prod)         |
| `JWT_EXPIRATION_MINUTES`     | `60`                                     |
| `ADMIN_EMAIL`                | `admin@booking.local`                    |
| `ADMIN_PASSWORD`             | `admin12345`                             |
| `RATE_LIMIT_ENABLED`         | `true`                                   |
| `RATE_LIMIT_CAPACITY`        | `20` (requests per window, per client)   |
| `RATE_LIMIT_WINDOW_SECONDS`  | `60`                                     |

CORS origins (`app.cors.allowed-origins`) and booking rules
(`app.booking.opening-time` / `closing-time` / `max-duration-hours` /
`max-advance-days`, defaulting to 08:00–20:00, 4h, 30 days) are set in
`application.yaml`.

## API examples

```bash
# Register (returns a JWT)
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123","fullName":"Alice"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@booking.local","password":"admin12345"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Create a room (admin only)
curl -X POST http://localhost:8080/api/rooms \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Sky Room","capacity":8,"location":"Floor 3"}'

# Book a slot
curl -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"roomId":1,"title":"Team sync","startTime":"2026-07-01T10:00:00","endTime":"2026-07-01T11:00:00"}'

# My bookings / a room's bookings for a date
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/bookings/my
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/bookings?roomId=1&date=2026-07-01"

# Cancel a booking
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/bookings/1
```

### Endpoints

| Method | Path                          | Access            |
|--------|-------------------------------|-------------------|
| POST   | `/api/auth/register`          | public            |
| POST   | `/api/auth/login`             | public            |
| GET    | `/api/users/me`               | authenticated     |
| GET    | `/api/rooms`, `/api/rooms/{id}` | authenticated   |
| POST/PUT/DELETE | `/api/rooms`, `/api/rooms/{id}` | **admin** |
| POST   | `/api/bookings`               | authenticated     |
| GET    | `/api/bookings/my`            | authenticated     |
| GET    | `/api/bookings?roomId&date`   | authenticated     |
| DELETE | `/api/bookings/{id}`          | owner or **admin** |

## Database schema

Managed by Liquibase (`src/main/resources/db/changelog`).

```
users                       rooms                    bookings
-----                       -----                    --------
id            (PK)          id           (PK)        id          (PK)
email (unique)              name (unique)            room_id     (FK -> rooms)
password_hash               capacity                 user_id     (FK -> users)
full_name                   location                 title
role                        description              start_time
created_at                  active                   end_time
                                                     status (ACTIVE|CANCELLED)
                                                     created_at
                                                     cancelled_at
```

Time‑overlap rule for a room: a new booking conflicts with an existing **ACTIVE**
booking when `existing.start_time < new.end_time AND existing.end_time > new.start_time`.

## Testing

```bash
./mvnw test
```

- **Unit** (`BookingServiceTest`, Mockito): booking validation and cancellation rules.
- **Integration** (`*IntegrationTest`, Testcontainers + MockMvc): the full
  register → login → create room → book → conflict → cancel → rebook flow against a
  real PostgreSQL, plus auth/role checks. Requires Docker.

## Future improvements

- Frontend (React + TypeScript)
- Prevent the rare concurrent double‑booking race (DB constraint / locking)
- Search & filters (capacity, location, free rooms for a time range)
- Booking rules (working hours, max duration, booking horizon), recurring bookings
- Email notifications, calendar integration, WebSocket live updates
- Publish the Docker image to a registry and add a deployment pipeline
```
