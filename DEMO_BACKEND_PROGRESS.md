# Demo Backend Progress

## Scope

Backend work for the video/demo language-learning cleanup:

- Japanese demo content must use real Japanese script, not romaji.
- Scanner saved words must land in the Learn word bank with the target-language word as `word`.
- Demo login password is `123456`.
- Two dedicated demo users are available for the video.

## Demo Accounts

Password for both accounts: `123456`

| Email | Background | Learning | Seed behavior |
| --- | --- | --- | --- |
| `demo.spanish@locale.app` | English native | Spanish | Seeds Spanish friends, messages, posts with images, meetups, and saved words. |
| `demo.japanese@locale.app` | English native | Japanese | Seeds Japanese friends, messages, posts with images, meetups, and saved words using hiragana/katakana/kanji. |

The legacy `demo@locale.app` account is also updated to password `123456`. If it still has old Korean word-bank data, startup cleanup removes that vocabulary/language setup and reseeds it as English native learning Japanese.

## Backend Changes

- Added `DemoAccountSeedService`.
- Updated `DemoLearningSeedService` Japanese content:
  - Replaced romaji post text with Japanese script.
  - Added real Japanese word-bank entries such as `こんにちは`, `ありがとう`, `駅`, `水`, `本当に？`, and `お願いします`.
- Updated scanner detection save behavior:
  - Before: `word = nativeWord`, `translation = learningWord`.
  - Now: `word = learningWord`, `translation = nativeWord`.
  - This makes scanner saves appear in Learn as the target-language vocabulary item.
- Added a `saved_words_source_check` seed patch so Docker databases accept scanner-saved words with source `SCANNER`.
- Updated static seed comments/hashes in `import.sql`, `data.sql`, and `seed.sql` to document/use `123456`.
- Restored root `seed.sql` as a usable mirror of `data.sql` after removing the conflicted/stale version.

## Frontend Handoff

Backend owns the data and persistence fixes above. Frontend still owns:

- Replacing the scanner save button with Beth-style star UI.
- Invalidating Learn word-bank queries after saving selected text from a post.
- Verifying Learn page grouping/lesson UI against Beth design.
- Fixing word-bank category matching in `LearnPage.tsx`: `apple` currently matches Electronics because substring matching sees `app` inside `apple`. Use token/word-boundary matching or move categories into backend data.

## 2026-05-24 Language Flow / Duplicate Demo Data Follow-up

- Feed defaults were tightened: when the frontend requests `/api/posts` without a concrete language, the backend now returns posts in the signed-in user's learning languages instead of every active post.
- Meetup defaults were tightened the same way for `/api/meetups` when no concrete language is supplied.
- Learner discovery defaults were tightened: when `/api/learners/nearby` has no concrete language filter, profiles are limited to languages overlapping the signed-in user's learning languages.
- Demo peer seeding now uses stable peer identities per language, for example `demo_ja_aiko@locale.demo` and `demo_es_sofia@locale.demo`, instead of generating another peer copy for every learner id.
- Existing databases are repaired on demo seeding by retiring legacy generated demo peers:
  - old generated `demo_*@locale.demo` peer profiles are hidden from learner discovery,
  - posts from old generated peers are marked `HIDDEN`,
  - meetups from old generated peers are marked `CANCELLED`,
  - stale non-demo language posts/meetups outside Spanish/Japanese are also hidden/cancelled for the current demo scope.

## Verification To Run

- `mvnw test`
- `docker compose config`
- Clean Docker rebuild from an empty volume:
  - `docker compose down -v`
  - `docker compose up -d --build`
- Login smoke test for:
  - `demo.spanish@locale.app` / `123456`
  - `demo.japanese@locale.app` / `123456`
- API check:
  - `GET /api/words` after login should show Spanish/Japanese target-language words.
  - `POST /api/scan/detections/{id}/save` should create a saved word where `word` is the target-language text.

## Verification Completed

- `mvnw test`: passed, 232 tests.
- `docker compose config`: passed.
- Clean Docker reset was run with `docker compose down -v`.
- Clean Docker rebuild was run with `docker compose up -d --build`.
  - Backend, Postgres, and vision-service started healthy.
  - Frontend image built, but the frontend container could not bind host port 8080 on this machine because that port was already in use.
- Backend health: `GET /actuator/health` returned 200.
- Login smoke checks returned 200 for:
  - `demo@locale.app`
  - `demo.spanish@locale.app`
  - `demo.japanese@locale.app`
- Fresh DB seed check:
  - `demo.spanish@locale.app`: English native, Spanish learning, 5 Spanish saved words.
  - `demo.japanese@locale.app`: English native, Japanese learning, 6 Japanese saved words.
  - `demo@locale.app`: English native, Japanese learning, 6 Japanese saved words.
- Fresh DB constraints include:
  - `notifications_type_check` with `FRIEND_POST`.
  - `scan_detections_translation_source_check` with `TAXONOMY`.
  - `saved_words_source_check` with `SCANNER`.
