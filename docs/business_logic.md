# Business Logic — W19 Social Language Learning

This document describes the **domain rules, algorithms, and constraints** enforced by the service layer. It complements the Swagger UI (`http://localhost:8081/swagger-ui.html`) which documents the API schemas.

---

## Table of Contents

1. [Authentication & Sessions](#1-authentication--sessions)
2. [Users & Profiles](#2-users--profiles)
3. [Follow System](#3-follow-system)
4. [Friend System](#4-friend-system)
5. [Posts & Feed](#5-posts--feed)
6. [Vocabulary (Saved Words)](#6-vocabulary-saved-words)
7. [Practice Sessions](#7-practice-sessions)
8. [Learning Stats](#8-learning-stats)
9. [Discovery — Nearby Learners](#9-discovery--nearby-learners)
10. [Meetups](#10-meetups)
11. [Messaging & Chat](#11-messaging--chat)
12. [User Blocking](#12-user-blocking)
13. [Enums Reference](#13-enums-reference)

---

## 1. Authentication & Sessions

### Registration
- Email must be **unique** — duplicate registration throws `400`.
- `username` is optional. If not provided or blank, it is auto-derived from the email prefix (e.g. `john@example.com` → `john`).
- If the derived username is already taken, a 3-digit timestamp suffix is appended (e.g. `john847`).
- Password is **bcrypt-hashed** before storage; the plaintext is never stored.
- New accounts are assigned the `USER` role automatically.
- Tokens (access + refresh) are issued immediately after registration — no separate login step required.

### Token Flow
| Token | Lifetime | Notes |
|-------|----------|------|
| `access_token` | Short-lived (configured via `app.jwt.expiration-ms`) | JWT signed with secret |
| `refresh_token` | Longer-lived | Stored server-side; rotated on each use |

- **Token Rotation**: Every call to `POST /auth/refresh` issues a **new** refresh token and invalidates the old one.
- **Logout**: Deletes the refresh token server-side. The access token remains valid until its natural expiry (stateless). Clients should also clear local storage.

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

### Privacy Settings (`PATCH /users/me/privacy`)
Controls what other users can see. Settings are stored directly on the `Profile` entity (not in `UserSettings`).

| Setting | Type | Effect |
|---------|------|--------|
| `location_visibility` | enum | Controls map visibility (see [Enums](#13-enums-reference)) |
| `show_activity` | boolean | Whether learning activity appears on public profile |
| `show_saved_words` | boolean | Whether the saved word count appears on public profile |

> **Default**: All users default to `PUBLIC` location visibility.

### Public Profile (`GET /users/{id}`)
- The `is_following` flag reflects whether the **currently authenticated caller** follows the target user.
- Follower/following/post counts are computed **live via repository queries** (not cached counters) to ensure accuracy.
- `posts_count` only counts posts with status `APPROVED`.

### User Settings (`GET/PATCH /users/me/settings`)
Separate from privacy — stores notification preferences and theme preferences. If no settings record exists for a user, **defaults are auto-created** on first access (no 404).

---

## 3. Follow System

- **One-way relationship** (like Twitter) — User A following User B does not imply B follows A.
- Self-follow is **prevented** at the service layer.
- Follow is **idempotent**: following an already-followed user does not throw an error.
- Unfollow is also idempotent.

**Endpoints use path variable** (not request body):
- `POST /api/users/{id}/follow` — follow user `{id}`
- `DELETE /api/users/{id}/follow` — unfollow user `{id}`
- `GET /api/users/{id}/followers` — users who follow `{id}`
- `GET /api/users/{id}/following` — users that `{id}` follows

---

## 4. Friend System

The Friend system is **separate from Follow** — it is a **mutual, bi-directional relationship** where both users must agree.

### Why Friends Exist
- Friendship controls **map visibility**: users with `location_visibility = FRIENDS_ONLY` are only visible on the map to their mutual friends.
- Friendship is tracked via a `Friend` entity with `requester`, `receiver`, and `status` fields.

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
                                              (requester may re-send)
```

### Rules
| Scenario | Outcome |
|----------|---------|
| Send request to self | `400 Bad Request` |
| Already friends | `409 Conflict` |
| Request already pending (either direction) | `409 Conflict` |
| Previous request was rejected | Old record deleted; new `PENDING` request created |
| Responder is not the receiver | `403 Forbidden` |
| Action is not `accept` or `reject` | `400 Bad Request` |
| Try to accept/reject a non-pending request | `400 Bad Request` |

### Querying
- `findBetween(A, B)` checks **both directions** (A→B and B→A), so callers don't need to know who sent the original request.
- `GET /users/{id}/friend-status` returns `204 No Content` when no relationship exists.

### Removing a Friend
- Only works if friendship status is `ACCEPTED`. Attempting to remove a non-friend throws `400`.
- Deletes the `Friend` record entirely — neither party can see the old history.

---

## 5. Posts & Feed

### Creating a Post
- Required: `content`, `original_language` (language code, e.g. `"en"`, `"es"`).
- Optional: `latitude`, `longitude`, `image_url`.
- Only the **author** can delete their post.

### Feed (`GET /api/posts`)
- Returns all posts, ordered by `created_at DESC`.
- Optional `language` filter: returns only posts in that language. Passing `"all"` (or omitting) returns all languages.
- Optional `latitude` / `longitude`: used to compute and display a `distance` string (e.g. `"1.2 km"`) on each post. Distance is calculated using the **Haversine formula**.

> ⚠️ **Note**: Post status filtering is temporarily disabled. All posts (regardless of status) are returned in the feed. This is a known TODO to re-enable after fixing existing seed data statuses.

### Post Response — Author Language Display
On each post response, the author's **native language** (first `UserLanguage` where `is_learning = false`) name and flag emoji are included for display.

### Reactions
- One reaction per user per post (**upsert** — posting a new reaction replaces the old one).
- Reaction types: `LIKE`, `LOVE`, `HELPFUL`, `FUNNY`.
- The `user_reaction` field on `PostResponse` reflects the **current user's** reaction (or `null`).

### Comments
- Paginated, ordered by `created_at ASC` (oldest first).
- Only the author of a comment can delete it.

### Translations
- Translations are **cached** per `(post_id, target_language)` — repeat requests don't re-call the translation API.

### Reports
- Report `reason` values: `SPAM`, `HARASSMENT`, `INAPPROPRIATE`, `MISINFORMATION`, `OTHER`.
- Reports are stored for moderation; no immediate action is taken automatically.

---

## 6. Vocabulary (Saved Words)

Each `SavedWord` belongs to a user and has:
- `word` — the foreign word/phrase
- `translation` — the user's language translation
- `language_code` — which language it belongs to
- `mastery_level` — integer 0–100 (starts at 0)
- `notes` — optional personal notes

### Filtering & Sorting
- `GET /api/words` supports `?language=<code>` to filter by language and `?sort=newest` (default) for ordering.

### Mastery Level
- Set to `0` on creation.
- Updated automatically when a practice session answer is submitted (see [Practice Sessions](#7-practice-sessions)).

---

## 7. Practice Sessions

Practice sessions allow users to drill their saved vocabulary with a **mastery-weighted selection algorithm**.

### Session Size
- Must be exactly `5`, `10`, or `15` words. Other values are rejected with `400`.
- There must be **at least as many saved words** in the selected language as the requested session size.

### Word Selection Algorithm
Words are selected using a **weighted random** algorithm that **favours lower-mastery words**:

```
weight(word) = (100 - mastery_level)² + 10
```

- A word at mastery `0` → weight = 10,010 (very likely to appear)
- A word at mastery `50` → weight = 2,510
- A word at mastery `100` → weight = 10 (still has a small non-zero chance)
- Words are sampled **without replacement** within a session.

### Submitting an Answer
For each word answered, the mastery level is updated **immediately** and the change is returned:

```
If CORRECT:
  increase = max(5, (100 - currentMastery) / 5)
  newMastery = min(100, currentMastery + increase)

If WRONG:
  decrease = max(10, currentMastery / 4)
  newMastery = max(0, currentMastery - decrease)
```

- ✅ **Diminishing returns on correct answers** — harder to go from 90→100 than from 10→20.
- ❌ **Harsher penalty the more you know** — a mastery-80 word drops by ~20 on wrong answer vs. ~10 for a mastery-40 word.
- Each word can only be submitted **once per session** (duplicate submissions throw `400`).
- Submitting to a completed session throws `400`.

### Completing a Session
Returns a summary with:
- `words_practiced`, `correct_count`
- `accuracy` — percentage (0–100)
- `duration_seconds` — time from session start to complete call
- `results` — per-word breakdown showing old/new mastery

---

## 8. Learning Stats

`GET /api/learn/stats` aggregates the user's **entire vocabulary**:

| Field | Description |
|-------|-------------|
| `total_words` | Count of all saved words |
| `average_mastery` | Mean mastery level across all words (0–100) |
| `languages[]` | Per-language breakdown: word count + avg mastery |
| `mastery_distribution` | Count of words in each tier (see below) |

### Mastery Distribution Tiers

| Tier | Mastery Range |
|------|--------------|
| `beginner` | 0 – 25 |
| `learning` | 26 – 50 |
| `familiar` | 51 – 75 |
| `mastered` | 76 – 100 |

---

## 9. Discovery — Nearby Learners

`GET /api/learners/nearby` returns other learners within a geographic radius.

### Filtering Rules (applied in order)
1. **Self-exclusion**: The requesting user is always excluded from results.
2. **Location data**: Users without `latitude`/`longitude` set are excluded.
3. **Privacy filter**: Applied based on each candidate user's `location_visibility`:
   - `PUBLIC` → always included
   - `FRIENDS_ONLY` → only included if the requester and candidate are **mutual friends** (`ACCEPTED` status)
   - `NOBODY` → always excluded
4. **Language filter**: Optional `?language=<code>` filters to users who have that language tagged (native or learning).
5. **Radius filter**: Only users within `radius_km` (default: 10 km) of the given `latitude`/`longitude`.

### Distance Calculation
Haversine formula, Earth radius = 6371 km. Results are **sorted by distance ascending** (closest first).

### Parameters
| Param | Required | Default | Constraint |
|-------|----------|---------|-----------|
| `latitude` | No | — | -90 to 90 |
| `longitude` | No | — | -180 to 180 |
| `radius_km` | No | 10 | Must be > 0 |
| `language` | No | — | Language code |

---

## 10. Meetups

### Creation Rules
- `language_code` must exist in the `languages` table.
- The organiser is **automatically added as the first attendee**.
- Initial status is always `UPCOMING`.
- `meetup_date` is stored as-is; validation that it's a future date is implicitly expected by callers (no hard server-side check currently).

### Listing / Filtering
- Only `UPCOMING` meetups are returned in list results.
- Meetups in the past (where `meetupDate < now`) are excluded.
- Filter priority: if `latitude + longitude + radius_km` are all provided → **geospatial search**; else if `language` provided → **language filter**; else → **all upcoming**.
- Sorted by `meetupDate` ascending (soonest first).

### Per-Response Fields
Each `MeetupResponse` includes contextual flags for the calling user:
- `is_attending` — whether the caller is currently an attendee
- `is_organizer` — whether the caller created the meetup

### Join / Leave Rules

| Action | Who | Condition | Error |
|--------|-----|-----------|-------|
| Join | Any authenticated user | Not already joined | `400` |
| Join | Any authenticated user | Meetup not in the past | `400` |
| Join | Any authenticated user | Meetup not full | `400` |
| Leave | Non-organiser only | Must be an attendee | `400` |
| Leave | **Organiser** | Not allowed — must delete | `400` |
| Update | **Organiser only** | Always partial updates | `400` if not organiser |
| Delete | **Organiser only** | — | `400` if not organiser |

> Deleting a meetup cascades and removes all attendee records.

---

## 11. Messaging & Chat

### Conversation Deduplication
`POST /conversations` (with `recipient_id`) will:
1. Look for an existing conversation between the sender and recipient.
2. If found → **reuse it** (send into the existing conversation).
3. If not found → **create a new conversation** and send the first message.

This ensures there is **always exactly one conversation per user pair**.

### `last_message_preview`
Updated on every sent message (regardless of REST or WebSocket path) so the conversation list always shows the most recent message snippet.

### WebSocket
- Sending via WebSocket (`/app/chat.send`) and the REST fallback (`POST /conversations/{id}/messages`) both save to the database **and** broadcast to `/topic/conversation.{id}`.
- Typing events (`/app/chat.typing`) are broadcast to `/topic/conversation.{id}.typing` and are **not persisted**.

### Mark as Read
`POST /conversations/{id}/read` marks all messages in the conversation as `is_read = true`. Currently marks all unread messages in the conversation regardless of sender.

---

## 12. User Blocking

- A user can block another user via `POST /users/{id}/block`.
- Blocking yourself (`blockerId == blockedId`) throws `400`.
- Blocking an already-blocked user throws `400` (not idempotent).
- Unblocking removes the `UserBlock` record; throws `400` if no block exists.

> **Note**: Blocking currently **does not** automatically unfollow or remove friendship. This is a noted TODO in the codebase.

---

## 13. Enums Reference

### `LocationVisibility`
| Value | Effect |
|-------|--------|
| `PUBLIC` | Default. Visible to everyone on the map |
| `FRIENDS_ONLY` | Only visible to mutual friends (status = `ACCEPTED`) |
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

### `MeetupStatus`
`UPCOMING` *(only status currently in use — past/cancelled states are not yet implemented)*

### `AppRole`
`USER` *(admin roles are not yet implemented)*
