# Business Logic — W19 Social Language Learning

This document describes the **domain rules, algorithms, and constraints** enforced by the service layer. It complements the Swagger UI (`http://localhost:8081/swagger-ui.html`) which documents the API schemas.

---

## Table of Contents

1. [Authentication & Sessions](#1-authentication--sessions)
2. [Users & Profiles](#2-users--profiles)
3. [Follow System](#3-follow-system)
4. [Friend System](#4-friend-system)
5. [Posts & Feed](#5-posts--feed)
6. [File Storage (AWS S3)](#6-file-storage-aws-s3)
7. [Vocabulary (Saved Words)](#7-vocabulary-saved-words)
8. [Practice Sessions](#8-practice-sessions)
9. [Learning Stats](#9-learning-stats)
10. [Discovery — Nearby Learners](#10-discovery--nearby-learners)
11. [Meetups](#11-meetups)
12. [Messaging & Chat](#12-messaging--chat)
13. [User Blocking](#13-user-blocking)
14. [Enums Reference](#14-enums-reference)

---

## 1. Authentication & Sessions

### Registration

- Email must be **unique** — duplicate registration throws `400`.
- `username` is optional. If not provided or blank, it is auto-derived from the email prefix (e.g. `john@example.com` → `john`).
- If the derived username is already taken, a 3-digit timestamp suffix is appended (e.g. `john847`).
- Password is **bcrypt-hashed** before storage; the plaintext is never stored or logged.
- New accounts are assigned the `USER` role automatically.
- Tokens (access + refresh) are issued immediately after registration — no separate login step required.

### Token Flow

| Token | Lifetime | Notes |
|-------|----------|-------|
| `access_token` | 1 hour (via `app.jwt.expiration-ms`) | JWT signed with `JWT_SECRET` |
| `refresh_token` | Longer-lived | Stored in DB; rotated on each use |

- **Token Rotation**: every `POST /auth/refresh` issues a **new** refresh token and invalidates the old one.
- **Logout**: deletes the refresh token server-side. The access token remains valid until its natural expiry (stateless JWT — no server-side session). Clients must also clear local storage.

### Public Routes (no auth required)

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/languages`

---

## 2. Users & Profiles

### Profile Updates (`PATCH /users/me`)

- Partial updates: only fields included in the request body are modified.
- Updatable fields: `display_name`, `bio`, `avatar_url`, `latitude`, `longitude`.
- If `avatar_url` changes and the previous value was an S3 URL, `S3Service.deleteFile` is called automatically to clean up the orphaned file — **no client action needed**.

### Privacy Settings (`PATCH /users/me/privacy`)

Controls what other users can see. Stored on the `Profile` entity.

| Setting | Type | Effect |
|---------|------|--------|
| `location_visibility` | enum | Controls map visibility (see [Enums](#14-enums-reference)) |
| `show_activity` | boolean | Whether learning activity appears on public profile |
| `show_saved_words` | boolean | Whether saved word count appears on public profile |

> **Default**: All users start with `PUBLIC` location visibility.

### Public Profile (`GET /users/{id}`)

- `is_following` / `is_followed_by` reflect the **currently authenticated caller's** relationship with the target.
- Follower/following/post counts are computed live via repository queries (not cached counters).
- `posts_count` counts only posts with status `APPROVED`.

### User Settings (`GET/PATCH /users/me/settings`)

Separate from privacy — stores notification and theme preferences. If no settings record exists for a user, **defaults are auto-created** on first access (never returns `404`).

### Language List (`PUT /users/me/languages`)

**Replaces** the entire language list atomically: deletes all existing `UserLanguage` rows for the user, then inserts the new set. This prevents partial state between old and new entries.

---

## 3. Follow System

- **One-way relationship** (like Twitter) — User A following User B does not imply B follows A.
- Self-follow is **prevented** at the service layer (`400`).
- Follow and unfollow are **idempotent** — repeating either does not throw an error.
- Follower/following **counter columns** on `Profile` are updated on every follow/unfollow (incremented/decremented). This avoids a COUNT query on every profile load.

**Endpoints:**

- `POST /api/users/{id}/follow` — follow user `{id}`
- `DELETE /api/users/{id}/follow` — unfollow user `{id}`
- `GET /api/users/{id}/followers` — users who follow `{id}`
- `GET /api/users/{id}/following` — users that `{id}` follows

---

## 4. Friend System

The Friend system is **separate from Follow** — it is a **mutual, bi-directional relationship** where both users must agree.

### Why Friends Exist

Friendship controls **map visibility**: users with `location_visibility = FRIENDS_ONLY` are only visible on the map to their mutual friends. This is enforced in `LearnerService` using `FriendRepository.findAcceptedFriendIds()` to build an efficient set lookup — no N+1 queries.

### Request Lifecycle

```
        ┌──────────────┐         ┌──────────────┐
        │   Requester  │──SEND──▶│   Receiver   │
        └──────────────┘         └──────────────┘
                                        │
                         ┌──────────────┴──────────────┐
                       ACCEPT                        REJECT
                         │                              │
                      ACCEPTED                      REJECTED
                                                        │
                                              (requester may re-send;
                                               old record is deleted)
```

### Rules

| Scenario | Outcome |
|----------|---------|
| Send request to self | `400 Bad Request` |
| Already friends | `409 Conflict` |
| Request already pending (either direction) | `409 Conflict` |
| Previous request was rejected | Old record deleted; new `PENDING` record created |
| Responder is not the receiver | `403 Forbidden` |
| Action is not `accept` or `reject` | `400 Bad Request` |
| Accept/reject a non-pending request | `400 Bad Request` |

### Querying

- `FriendRepository.findBetween(A, B)` checks **both directions** (A→B and B→A), so callers never need to know who sent the original request.
- `GET /users/{id}/friend-status` returns `204 No Content` when no relationship exists.

### Removing a Friend

- Only valid for `ACCEPTED` friendships. Attempting to remove a non-friend throws `400`.
- Deletes the `Friend` record entirely — no history is retained.

---

## 5. Posts & Feed

### Creating a Post

- Required fields: `content`, `original_language` (language code, e.g. `"en"`, `"ko"`).
- Optional fields: `latitude`, `longitude`, `image_url` (S3 public URL — upload first via `POST /api/files/upload`).
- Only the **author** can delete their post.

### Feed (`GET /api/posts`)

- Returns posts ordered by `created_at DESC`.
- Optional `language` filter: returns only posts in that language code. Omitting or passing `"all"` returns all languages.
- Optional `latitude` / `longitude`: computes a `distance` string (e.g. `"1.2 km"`) per post using the **Haversine formula**.

> **Known TODO**: Post status filtering is temporarily disabled. All posts are returned regardless of status. This will be re-enabled after fixing existing seed data statuses.

### Author Language Display

Each `PostResponse` includes the author's **native language** (first `UserLanguage` where `is_learning = false`) name and flag emoji for display purposes.

### Reactions

- One reaction per user per post (**upsert** — a new reaction replaces the existing one).
- Reaction types: `LIKE`, `LOVE`, `HELPFUL`, `FUNNY`.
- `user_reaction` on `PostResponse` reflects the **current user's** reaction (or `null`).

### Comments

- Paginated, ordered by `created_at ASC` (oldest first).
- Only the comment author can delete their comment.

### Translations

- Cached per `(post_id, target_language)` in the `post_translation` table. Repeat requests do not re-call any external translation service.

### Post Deletion & S3 Cleanup

When `DELETE /posts/{id}` is called:
1. `PostService` verifies the caller is the author.
2. Calls `S3Service.extractKey(post.imageUrl)` to get the S3 key (returns `null` if URL is not from this bucket — safe to call unconditionally).
3. If a key is found, calls `S3Service.deleteFile(key)` to remove the file from S3.
4. Deletes the `Post` record from the database.

### Reports

- Valid `reason` values: `SPAM`, `HARASSMENT`, `INAPPROPRIATE`, `MISINFORMATION`, `OTHER`.
- Reports are stored in `ContentReport` for moderation review. No automated action is taken.

---

## 6. File Storage (AWS S3)

All media files are stored in the `fs-kaiday-customer-test` S3 bucket (region `ap-southeast-2`). The backend exposes a thin authenticated proxy at `/api/files`.

### Upload — `POST /api/files/upload?type=<images|audio|videos>`

1. Validates `type` parameter is one of `images`, `audio`, `videos`.
2. Validates file is not empty.
3. Validates MIME type matches the declared type.
4. Validates file size against per-type limits.
5. Generates a unique S3 key: `<type>/<UUID>-<originalFilename>`.
6. Uploads via `S3Client.putObject` with `ContentType` header.
7. Returns `{ "url": "https://<bucket>.s3.<region>.amazonaws.com/<key>" }`.

| Type | Max size |
|------|----------|
| `images` | 5 MB |
| `audio` | 20 MB |
| `videos` | 100 MB |

### Delete — `DELETE /api/files/delete?key=<s3-key>`

Calls `S3Client.deleteObject` for the given key. Used explicitly by the frontend or triggered automatically by the backend on post/avatar deletion.

### Automatic S3 Cleanup (backend-initiated)

| Trigger | Where | What gets deleted |
|---------|-------|-------------------|
| `DELETE /posts/{id}` | `PostService.deletePost` | `post.imageUrl` key (via `extractKey`) |
| `PATCH /users/me` with new `avatar_url` | `UserController.updateProfile` | Old `profile.avatarUrl` key (via `extractKey`) |

`S3Service.extractKey(url)` strips the bucket URL prefix to recover the key. Returns `null` for non-S3 URLs, making the cleanup call unconditionally safe.

---

## 7. Vocabulary (Saved Words)

Each `SavedWord` belongs to a user and tracks:

| Field | Description |
|-------|-------------|
| `word` | The foreign word or phrase |
| `translation` | The user's translation |
| `language_code` | Target language |
| `mastery_level` | Integer 0–100, starts at `0` |
| `notes` | Optional personal context/notes |
| `next_review` | Timestamp for next scheduled practice (spaced repetition) |

### Filtering & Sorting

- `GET /api/words` supports `?language=<code>` to filter by language and `?sort=newest` (default) for ordering.

### Mastery Level

- Starts at `0` on creation.
- Updated automatically when a practice answer is submitted (see [Practice Sessions](#8-practice-sessions)).
- The `next_review` timestamp is also updated after each submission to schedule the word's next appearance.

---

## 8. Practice Sessions

Practice sessions drill saved vocabulary using a **mastery-weighted selection algorithm**.

### Session Size

- Must be exactly `5`, `10`, or `15` words. Other values are rejected with `400`.
- Requires at least as many saved words in the selected language as the requested session size.

### Word Selection Algorithm

Words are selected using a **weighted random** algorithm that **favours lower-mastery words**:

```
weight(word) = (100 - mastery_level)² + 10
```

| Mastery | Weight | Relative likelihood |
|---------|--------|---------------------|
| 0 | 10,010 | Very high |
| 50 | 2,510 | Medium |
| 100 | 10 | Small but non-zero |

- Words are sampled **without replacement** within a session.

### Submitting an Answer

Mastery is updated **immediately** after each submission:

```
If CORRECT:
  increase = max(5, (100 - currentMastery) / 5)
  newMastery = min(100, currentMastery + increase)

If WRONG:
  decrease = max(10, currentMastery / 4)
  newMastery = max(0, currentMastery - decrease)
```

- **Diminishing returns on correct answers** — harder to go from 90→100 than from 10→20.
- **Harsher penalty for high mastery** — a mastery-80 word drops by ~20 on wrong vs. ~10 for mastery-40.
- Each word can only be submitted **once per session** (duplicate submissions throw `400`).
- Submitting to a completed session throws `400`.

### Completing a Session

Returns a summary with:

| Field | Description |
|-------|-------------|
| `words_practiced` | Total words in session |
| `correct_count` | Number answered correctly |
| `accuracy` | Percentage (0–100) |
| `duration_seconds` | Time from session start to complete call |
| `results` | Per-word breakdown: word, correct/wrong, old mastery, new mastery |

---

## 9. Learning Stats

`GET /api/learn/stats` aggregates the user's **entire vocabulary**:

| Field | Description |
|-------|-------------|
| `total_words` | Count of all saved words |
| `average_mastery` | Mean mastery level across all words (0–100) |
| `languages[]` | Per-language breakdown: word count + avg mastery |
| `mastery_distribution` | Count of words in each mastery tier |

### Mastery Distribution Tiers

| Tier | Range |
|------|-------|
| `beginner` | 0 – 25 |
| `learning` | 26 – 50 |
| `familiar` | 51 – 75 |
| `mastered` | 76 – 100 |

---

## 10. Discovery — Nearby Learners

`GET /api/learners/nearby` returns other learners within a geographic radius.

### Filtering Rules (applied in order)

1. **Self-exclusion** — the requesting user is always excluded.
2. **Location data** — users without `latitude`/`longitude` are excluded.
3. **Privacy filter** — based on each candidate's `location_visibility`:
   - `PUBLIC` → always included
   - `FRIENDS_ONLY` → only included if the requester and candidate are **mutual friends** (`FriendStatus.ACCEPTED`). Friend IDs are fetched via `FriendRepository.findAcceptedFriendIds` (single query, no N+1).
   - `NOBODY` → always excluded
4. **Language filter** — optional `?language=<code>` narrows to users with that language tagged (native or learning).
5. **Radius filter** — only users within `radius_km` (default: 10 km).

### Distance Calculation

Haversine formula, Earth radius = 6371 km. Results sorted **by distance ascending** (closest first).

### Parameters

| Param | Required | Default | Constraint |
|-------|----------|---------|------------|
| `latitude` | No | — | -90 to 90 |
| `longitude` | No | — | -180 to 180 |
| `radius_km` | No | 10 | Must be > 0 |
| `language` | No | — | Language code |

---

## 11. Meetups

### Creation Rules

- `language_code` must exist in the `languages` table.
- Organiser is **automatically added as the first attendee** on creation.
- Initial status is always `UPCOMING`.
- `meetup_date` is expected to be a future datetime (no hard server-side validation currently — tracked as a TODO).

### Listing / Filtering

- Only `UPCOMING` meetups where `meetupDate >= now` are returned.
- Filter priority: if `latitude + longitude + radius_km` all provided → **geospatial search**; else if `language` provided → **language filter**; else → all upcoming meetups.
- Sorted by `meetupDate` ascending (soonest first).

### Per-Response Context Flags

Each `MeetupResponse` includes flags for the calling user:

- `is_attending` — whether the caller is currently an attendee
- `is_organizer` — whether the caller created the meetup

### Join / Leave Rules

| Action | Who | Condition | Error |
|--------|-----|-----------|-------|
| Join | Any authenticated user | Not already joined | `400` |
| Join | Any authenticated user | Meetup not in the past | `400` |
| Join | Any authenticated user | Meetup not full (`maxAttendees`) | `400` |
| Leave | Non-organiser only | Must be an attendee | `400` |
| Leave | **Organiser** | Not allowed — must delete instead | `400` |
| Update | **Organiser only** | Partial field updates | `400` if not organiser |
| Delete | **Organiser only** | Cascades attendee records | `400` if not organiser |

---

## 12. Messaging & Chat

### Conversation Deduplication

`POST /conversations` (with `recipient_id`) is **idempotent**:

1. Checks for an existing conversation between sender and recipient (`ConversationRepository.findBetweenUsers`).
2. If found → reuses it (sends into the existing conversation).
3. If not found → creates a new conversation and sends the first message.

This guarantees **exactly one conversation per user pair**.

### `last_message_preview`

Updated on every sent message (both REST and WebSocket paths) so the conversation list always reflects the most recent message.

### WebSocket

- Sending via WebSocket (`/app/chat.send`) and the REST endpoint (`POST /conversations/{id}/messages`) both **save to the database AND broadcast** to `/topic/conversation.{id}`.
- Typing events (`/app/chat.typing`) are broadcast to `/topic/conversation.{id}.typing` and are **not persisted**.

### Mark as Read

`POST /conversations/{id}/read` sets `is_read = true` on all messages in the conversation for the current user.

---

## 13. User Blocking

- `POST /users/{id}/block` — creates a `UserBlock` record.
- Blocking yourself (`blockerId == blockedId`) throws `400`.
- Blocking an already-blocked user throws `400` (not idempotent — by design).
- `DELETE /users/{id}/block` — removes the block. Throws `400` if no block exists.

> **Known TODO**: Blocking currently does **not** automatically unfollow or remove the friendship. This is a noted gap in the implementation.

---

## 14. Enums Reference

### `LocationVisibility`

| Value | Effect |
|-------|--------|
| `PUBLIC` | Default. Visible to everyone on the map |
| `FRIENDS_ONLY` | Only visible to mutual friends (`FriendStatus.ACCEPTED`) |
| `NOBODY` | Hidden from all map results |

### `FriendStatus`

| Value | Meaning |
|-------|---------|
| `PENDING` | Request sent, awaiting response |
| `ACCEPTED` | Mutual friendship established |
| `REJECTED` | Request declined (re-send is allowed) |

### `ReactionType`

`LIKE`, `LOVE`, `HELPFUL`, `FUNNY`

### `ReportReason`

`SPAM`, `HARASSMENT`, `INAPPROPRIATE`, `MISINFORMATION`, `OTHER`

### `ProficiencyLevel`

`BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `NATIVE`

### `MeetupStatus`

`UPCOMING` *(only status currently active — past/cancelled states are not yet implemented)*

### `PostStatus`

`APPROVED`, `PENDING`, `HIDDEN` *(status filtering temporarily disabled — see [Posts & Feed](#5-posts--feed))*

### `AppRole`

`USER` *(admin roles are defined but not yet implemented)*

### `SourceType`

`POST`, `CHAT`, `MANUAL` — indicates where a `SavedWord` originated (from a post, from a chat message, or manually entered by the user)