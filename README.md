# 🌏 Locale — Backend

Spring Boot backend for the Locale language exchange app.

## Prerequisites

- **Docker Desktop** (that's it — no Java or Maven needed!)

---

## 🚀 Running With Docker (Recommended for Team)

> Both repos must be cloned side-by-side on your machine:
> ```
> Desktop/
> ├── W19-back-end/   ← this repo
> └── W19-front-end/  ← frontend repo
> ```

### 1. Copy the environment file
```bash
cp .env.example .env
```
Fill in the values your teammate shared with you (JWT secret, Google Places key, and Google Translation key).

### 2. Start everything
```bash
docker compose up --build
```

This spins up **3 containers**:
| Container | URL |
|---|---|
| Frontend (React) | http://localhost:8080 |
| Backend (Spring Boot) | http://localhost:8081 |
| PostgreSQL | localhost:5432 |

The database is **automatically seeded** with demo data on first start.

### 3. Demo accounts (password: `demo123`)
| Username | Email | Native language |
|---|---|---|
| minso_k | minso@locale.app | Korean |
| emma_uk | emma@locale.app | English |
| linh_vn | linh@locale.app | Vietnamese |
| wei_cn | wei@locale.app | Chinese |

---

## 🔄 Useful Commands

| Action | Command |
|---|---|
| Start (first time or after code changes) | `docker compose up --build` |
| Start (no code changes) | `docker compose up` |
| Stop | `docker compose down` |
| **Reset database** (re-runs seed) | `docker compose down -v && docker compose up --build` |
| View logs | `docker compose logs -f backend` |

---

## 💻 Running Locally (Without Docker)

Requirements: Java 21, Maven, PostgreSQL 14+

1. Start PostgreSQL and create database `mydatabase` with user `myuser` / password `secret`
2. Copy `.env.example` → `.env` and fill in values
3. Run: `./mvnw spring-boot:run`

The API will be available at `http://localhost:8081`.

Required environment variables:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signs JWT access tokens |
| `GOOGLE_PLACES_KEY` | Calls Google Places proxy endpoints |
| `GOOGLE_TRANSLATE_API_KEY` | Calls Google Cloud Translation for post translations |
