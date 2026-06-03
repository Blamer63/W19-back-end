# Database Seeding Guide

Seeding is automatic. When the backend starts against an empty database, Spring Boot creates/updates tables and runs `src/main/resources/data.sql`. The root `seed.sql` mirrors that seed script for manual/team reference.

## Quick Start

```bash
docker compose up -d
```

The database is populated during backend startup. You should see `INSERT` activity in the backend logs.

## Seeded Accounts

All demo accounts use the password: `123456`

| Name | Email | Native Language | Learning | Location |
| --- | --- | --- | --- | --- |
| Minso Kim | `minso@locale.app` | Korean | English | Seoul, South Korea |
| Emma Smith | `emma@locale.app` | English | Korean, French | London, UK |
| Linh Nguyen | `linh@locale.app` | Vietnamese | English | Hanoi, Vietnam |
| Wei Chen | `wei@locale.app` | Japanese | English, Korean | Tokyo, Japan |
| Demo User | `demo@locale.app` | English | Japanese | San Francisco, CA |
| Demo Spanish Learner | `demo.spanish@locale.app` | English | Spanish | Sydney, Australia |
| Demo Japanese Learner | `demo.japanese@locale.app` | English | Japanese | Sydney, Australia |

The generic `demo@locale.app` account is intentionally kept for development/testing. On startup, if it still contains old Korean word-bank demo data, the backend cleans that vocabulary and reseeds it as an English-native Japanese learner.

## What Gets Seeded

| Entity | Details |
| --- | --- |
| Languages | English, Spanish, French, Japanese, Portuguese, Korean, Vietnamese |
| Users | Base demo profiles plus two dedicated video demo learners |
| Posts | Sample posts in Korean, English, Vietnamese, Japanese, Spanish, and French |
| Meetups | Upcoming meetups for seeded language communities |
| Conversations | Sample chat threads between users |
| Saved Words | Vocabulary lists for each demo learner |
| Friends | Accepted friend relationships between users |
| Reactions and Comments | Sample post activity |

## New-User Starter Mock Data

Local/demo environments also support one-time starter data for newly registered users. The feature is controlled by `app.starter-seed.enabled`, which defaults to `true` locally and `false` in production and tests.

When enabled, registration creates a pending row in the unmanaged JDBC table `user_starter_seed_runs`. No mock content is created at registration time. The first authenticated language setup that includes at least one learning language consumes that pending marker and seeds starter data once.

Starter data is supported for English, Spanish, French, Japanese, Korean, Portuguese, and Vietnamese. It creates demo peers near Sydney, accepted friendships, direct-message threads, feed posts with comments/reactions, upcoming meetups with attendees, and at least ten saved words per learning language. Each language pack includes one multi-image carousel post so feed/collection UIs can demonstrate multiple uploaded pictures.

Japanese starter content uses native Japanese script for user-facing Japanese text, including hiragana, kanji, and common kana/kanji vocabulary rather than romaji.

Existing accounts do not get a pending marker, and repeat language updates do not seed again after the marker is completed.

## Re-seeding / Fresh Reset

The SQL seed uses `ON CONFLICT DO NOTHING` and is safe to run repeatedly. The startup demo-account seeder also skips existing Spanish/Japanese learner data. New-user starter seeding uses the `user_starter_seed_runs` marker table to run once per newly registered profile.

To wipe everything and start from scratch:

```bash
docker compose down -v
docker compose up -d --build
```

## How It Works

| Config | Value | Purpose |
| --- | --- | --- |
| `spring.jpa.hibernate.ddl-auto` | `update` | Creates/updates tables for local development |
| `spring.jpa.defer-datasource-initialization` | `true` | Runs SQL seed after Hibernate table setup |
| `spring.sql.init.mode` | `always` | Runs `data.sql` on startup |
| SQL script | `src/main/resources/data.sql` | Automatic Spring Boot seed script |
| Manual reference | `seed.sql` | Mirrors `data.sql` for teammate visibility/manual DB loading |
| Demo account seeder | `DemoAccountSeedService` | Ensures dedicated Spanish/Japanese demo accounts and cleans the generic demo user |
| New-user starter seed | `app.starter-seed.enabled` | Enables one-time starter data after first learning-language setup |
