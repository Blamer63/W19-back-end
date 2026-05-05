# Database Seeding Guide

Seeding is **automatic**. When the backend starts against an empty database, Spring Boot runs `src/main/resources/import.sql` after Hibernate finishes creating all tables.

## Quick Start (Fresh Clone)

```bash
# 1. Start the database
docker compose up -d

# 2. Start the backend (seeding runs automatically on startup)
mvn spring-boot:run
```

That's it — the database is populated automatically. You should see `INSERT` activity in the console logs during startup.

---

## Seeded Accounts

All accounts use the password: **`demo123`**

| Name | Email | Native Language | Learning | Location |
|------|-------|-----------------|----------|----------|
| Minso Kim | `minso@locale.app` | Korean | English | Seoul, South Korea |
| Emma Smith | `emma@locale.app` | English | Korean, Chinese | London, UK |
| Linh Nguyen | `linh@locale.app` | Vietnamese | English | Hanoi, Vietnam |
| Wei Chen | `wei@locale.app` | Chinese | English, Korean | Beijing, China |
| Demo User | `demo@locale.app` | English | Korean | San Francisco, CA |

---

## What Gets Seeded

| Entity | Details |
|--------|---------|
| **Languages** | English, Korean, Vietnamese, Chinese, Japanese |
| **Users** | 5 profiles with realistic bios and GPS coordinates |
| **Posts** | Sample posts in Korean, English, Vietnamese, and Chinese |
| **Meetups** | 4 upcoming meetups in Seoul, Hanoi, Beijing, and London |
| **Conversations & Messages** | Sample chat threads between users |
| **Saved Words** | Vocabulary lists for each user (varies per language) |
| **Friends** | Accepted friend relationships between users |
| **Reactions** | Sample post reactions |
| **Comments** | Sample comments on posts |

---

## Re-seeding / Fresh Reset

The seed script uses `ON CONFLICT DO NOTHING` — it is fully **idempotent** and safe to run multiple times without creating duplicates.

To wipe everything and start from scratch:

```bash
docker compose down -v   # removes the database volume entirely
docker compose up -d     # starts a fresh, empty database
mvn spring-boot:run      # Hibernate recreates all tables, then import.sql seeds them
```

---

## How It Works (Technical)

| Config | Value | Purpose |
|--------|-------|---------|
| `spring.jpa.defer-datasource-initialization` | `true` | Ensures `import.sql` runs **after** Hibernate finishes creating all tables |
| `spring.sql.init.mode` | `always` | Runs the script on every startup (safe because of `ON CONFLICT DO NOTHING`) |
| Script location | `src/main/resources/import.sql` | Picked up automatically by Spring Boot's built-in SQL initializer |

> All seed records include realistic coordinates so that the **Nearby Learners** and **Meetups** geospatial features work out of the box without any manual data entry.