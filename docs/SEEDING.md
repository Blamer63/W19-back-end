# Database Seeding Guide

This guide explains how to seed the database with sample users, posts, and meetups for development and testing.

## Prerequisites

- Docker Desktop running
- Backend started via `mvn spring-boot:run` at least once (so Hibernate can auto-generate the tables)

---

## Steps

### 1. Start the database

```bash
docker compose up -d
```

### 2. Start the backend (first time only, to generate tables)

```bash
mvn spring-boot:run
```

Wait until you see `Started DemoApplication in X seconds` in the logs, then you can stop it or leave it running.

### 3. Run the seed script

```bash
docker cp seed.sql w19-back-end-postgres-1:/seed.sql
docker exec w19-back-end-postgres-1 psql -U myuser -d mydatabase -f /seed.sql
```

You should see a series of `INSERT 0 N` lines confirming success.

---

## Seeded Accounts

All accounts use the password: **`demo123`**

| Name | Email | Language | Location |
|------|-------|----------|----------|
| Minso Kim | `minso@locale.app` | Korean (native), learning English | Seoul, South Korea |
| Emma Smith | `emma@locale.app` | English (native), learning Korean & Chinese | London, UK |
| Linh Nguyen | `linh@locale.app` | Vietnamese (native), learning English | Hanoi, Vietnam |
| Wei Chen | `wei@locale.app` | Chinese (native) | Beijing, China |

> **Note:** There is also a `demo@locale.app` / `demo123` account created via app registration (not part of `seed.sql`).

---

## What Gets Seeded

- **Languages** — English (`en`), Korean (`ko`), Vietnamese (`vi`), Chinese (`zh`)
- **Users** — 4 profiles with realistic bios and coordinates
- **Posts** — sample posts in Korean, English, Vietnamese, and Chinese
- **Meetups** — 3 upcoming meetups in Seoul, Hanoi, and Beijing

---

## Re-seeding

The seed script uses `ON CONFLICT DO NOTHING`, so it's safe to run multiple times — it won't create duplicates.

To fully reset and re-seed from scratch:

```bash
docker compose down -v   # wipes the database volume
docker compose up -d     # starts a fresh database
mvn spring-boot:run      # regenerates the tables
# then run Step 3 above again
```
