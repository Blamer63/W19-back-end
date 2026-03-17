# Database Seeding Guide

Seeding is now **automatic**. When the backend starts for the first time against an empty database, Spring Boot automatically runs `src/main/resources/import.sql` after Hibernate finishes creating all tables.

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

| Name | Email | Language | Location |
|------|-------|----------|----------|
| Minso Kim | `minso@locale.app` | Korean (native), learning English | Seoul, South Korea |
| Emma Smith | `emma@locale.app` | English (native), learning Korean & Chinese | London, UK |
| Linh Nguyen | `linh@locale.app` | Vietnamese (native), learning English | Hanoi, Vietnam |
| Wei Chen | `wei@locale.app` | Chinese (native), learning English & Korean | Beijing, China |
| Demo User | `demo@locale.app` | English (native), learning Korean | San Francisco, CA |

---

## What Gets Seeded

- **Languages** — English, Korean, Vietnamese, Chinese, Japanese
- **Users** — 5 profiles with realistic bios and coordinates
- **Posts** — sample posts in Korean, English, Vietnamese, and Chinese
- **Meetups** — 4 upcoming meetups in Seoul, Hanoi, Beijing, and London
- **Conversations & Messages** — sample chat threads between users
- **Saved Words** — vocabulary lists for each user
- **Friends, Reactions, Comments** — social activity data

---

## Re-seeding / Fresh Reset

The seed script uses `ON CONFLICT DO NOTHING` — it is fully **idempotent** and safe to run multiple times. It will not create duplicates.

To wipe everything and start from scratch:

```bash
docker compose down -v   # wipes the database volume
docker compose up -d     # starts a fresh database
mvn spring-boot:run      # recreates tables and re-seeds automatically
```

---

## How It Works (Technical)

- `src/main/resources/import.sql` is picked up by Spring Boot's built-in SQL initializer.
- `spring.jpa.defer-datasource-initialization: true` in `application.yml` guarantees that `import.sql` runs **after** Hibernate has finished creating all tables.
- `spring.sql.init.mode: always` tells Spring to always run the script (safe because of `ON CONFLICT DO NOTHING`).
