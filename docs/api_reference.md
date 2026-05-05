# API Reference — W19 Backend

**Base URL:** `http://localhost:8081/api`

> **Swagger UI:** `http://localhost:8081/swagger-ui.html`
> Full endpoint schemas, request/response shapes, and try-it-out are available via Swagger. This document captures **business rules and behavioural notes** not visible from the schema alone.

---

## Authentication

- Tokens are JWTs signed with `JWT_SECRET`. The `access_token` is short-lived (1 hour); use `refresh_token` at `POST /auth/refresh` to rotate.
- **Token rotation**: every `POST /auth/refresh` call issues a **new** refresh token and invalidates the old one.
- `POST /auth/logout` deletes the refresh token server-side. The access token remains valid until its natural expiry (stateless — no server-side session). Clients must also clear local storage.
- Public (no auth required): `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /languages`.

---

## Users & Profiles

- `GET /users/{id}` — public profile. The `is_following` / `is_followed_by` flags reflect the **current authenticated caller's** relationship with the target.
- `PATCH /users/me` — partial updates: only fields included in the body are changed. If `avatar_url` changes and the previous value was an S3 URL, the old file is **automatically deleted from S3**.
- `PATCH /users/me/privacy` — controls map visibility:
  - `location_visibility: "PUBLIC"` (default) — visible to everyone on the map
  - `location_visibility: "FRIENDS_ONLY"` — only visible to mutual friends (`FriendStatus.ACCEPTED`)
  - `location_visibility: "NOBODY"` — hidden from all map results
- `GET /users/me/settings` — auto-creates a default settings record on first access; never returns `404`.
- `PUT /users/me/languages` — **replaces** the entire language list. Deletes all existing `UserLanguage` rows for the user before inserting the new ones.

---

## Follow vs. Friend System

Two separate social graphs:

| Feature | Type | Endpoint prefix |
|---------|------|-----------------|
| **Follow** | One-way (like Twitter) | `POST/DELETE /users/{id}/follow` |
| **Friend** | Mutual (both must agree) | `/users/{id}/friend-request`, etc. |

**Friendship** controls `location_visibility = FRIENDS_ONLY` on the map. Following alone does not affect map visibility.

### Friend Request Lifecycle

`PENDING` → `ACCEPTED` or `REJECTED`. A **rejected** requester can re-send (old record is deleted). Duplicate pending requests return `409 Conflict`.

### `FriendRequestResponse` status interpretation

| `status` | `is_sent_by_me` | Meaning |
|----------|-----------------|---------|
| `PENDING` | `true` | You sent, awaiting response |
| `PENDING` | `false` | You received, action required |
| `ACCEPTED` | either | Mutual friends |
| `REJECTED` | — | Declined (re-send is allowed) |

`GET /users/{id}/friend-status` returns `204 No Content` when no relationship exists.

### Follow behaviour

- Self-follow is **prevented** at the service layer (`400`).
- Follow and unfollow are **idempotent** — repeating either does not throw.

---

## Posts & Content

- `GET /posts` (feed) accepts optional `latitude`/`longitude` to compute a `distance` string (Haversine formula) on each post.
- Optional `language` filter returns only posts in that language code. Omitting or passing `"all"` returns all languages.
- `GET /posts/{id}/translations?target_language=<code>` — result is **cached** per `(post_id, target_language)`. Repeat requests do not re-call any translation API.
- `DELETE /posts/{id}` — author only. Also extracts the S3 key from `image_url` and **deletes the file from S3** before removing the DB record.
- Reactions: **one per user per post** (upsert — posting a new reaction replaces the previous one). `DELETE /posts/{id}/reactions` removes it entirely.
- Comments: ordered `created_at ASC` (oldest first). Only the comment author can delete their comment.
- Reports: stored for moderation; no automated action is taken. Valid `reason` values: `SPAM`, `HARASSMENT`, `INAPPROPRIATE`, `MISINFORMATION`, `OTHER`.

> **Known TODO**: Post status filtering is temporarily disabled. All posts are returned in the feed regardless of status. This will be re-enabled after fixing seed data statuses.

---

## File Upload — AWS S3 (`/api/files`)

Authenticated endpoint for standalone audio/video uploads. Files are stored in the private S3 bucket (`fs-kaiday-customer-test`, region `ap-southeast-2`) and returned as CloudFront URLs.

Profile, post, and message images are not uploaded here. They are uploaded inline through their owning endpoints so the backend can attach them to DB records and clean them up later.

### `POST /files/upload?type=<audio|videos>`

- Body: `multipart/form-data` with field `file`.
- Validates MIME type and file size before uploading.
- Returns `{ "url": "https://<cloudfront-domain>/<key>" }`.
- S3 key pattern: `<type>/<UUID>-<sanitizedOriginalFilename>`.

| `type` param | Allowed MIME types | Max size |
|---|---|---|
| `audio` | `audio/*` | 20 MB |
| `videos` | `video/*` | 100 MB |

### `DELETE /files/delete?key=<s3-key>`

- Deletes standalone `audio/` or `videos/` objects only.
- Image objects must be deleted through their owning profile/post/message workflows.
- Returns `{ "message": "File deleted successfully" }`.

### Automatic S3 cleanup

S3 files are automatically deleted (no manual call needed) when:
- A **message is deleted** (`DELETE /conversations/{id}/messages/{messageId}`) - the message's `image_url` S3 key is cleaned up.
- During migration, cleanup supports both old direct S3 URLs and new CloudFront URLs.
- A **post is deleted** (`DELETE /posts/{id}`) — the post's `image_url` S3 key is cleaned up.
- A **profile avatar is replaced** (`PATCH /users/me` with a new `avatar_url`) — the old avatar key is cleaned up.

---

## Learning Core (`/api/learn`, `/api/words`)

### Session Flow

1. `POST /api/learn/sessions/start` — supply `session_size` (must be `5`, `10`, or `15`) and `language_code`. Requires at least `session_size` saved words in that language.
2. `POST /api/learn/sessions/{id}/submit` — submit one answer at a time (`word_id`, `is_correct`). Each word can only be submitted **once per session**. Returns old and new mastery levels.
3. `POST /api/learn/sessions/{id}/complete` — finalise; returns accuracy percentage, duration in seconds, and per-word mastery changes.

`GET /api/learn/sessions` returns history paginated.

### Mastery Algorithm

Words are selected by **weighted random** — lower mastery words appear more frequently:

```
weight(word) = (100 - mastery_level)² + 10
```

Mastery update on submit:
- **Correct**: `increase = max(5, (100 - mastery) / 5)` → diminishing returns near 100
- **Wrong**: `decrease = max(10, mastery / 4)` → harsher penalty the higher the mastery

### Words (`/api/words`)

Full CRUD for the current user's saved vocabulary. Supports `?language=<code>` filter and `?sort=newest` (default) ordering.

---

## Discovery — Nearby Learners (`GET /api/learners/nearby`)

Results respect each user's `location_visibility` setting. Filtering applied in order:

1. **Self-exclusion** — the requesting user is never returned.
2. **Location required** — users without `latitude`/`longitude` are excluded.
3. **Privacy filter** — `PUBLIC` always shown; `FRIENDS_ONLY` only if mutual friends; `NOBODY` always excluded.
4. **Language filter** — optional `?language=<code>`.
5. **Radius filter** — only users within `radius_km` (default: 10 km). Distance calculated using the Haversine formula.

Results sorted by distance ascending (closest first).

---

## Meetups (`/api/meetups`)

- `meetup_date` must be a future datetime.
- The organiser is **automatically added as the first attendee** on creation. Initial status is `UPCOMING`.
- Only `UPCOMING` meetups are returned in list results (past meetups filtered out).
- Only the organiser can `PUT` or `DELETE` a meetup.
- Organiser **cannot** `POST /{id}/leave` — they must delete the meetup.
- `POST /{id}/join` returns `400` if: already joined, meetup is full (`maxAttendees` reached), or meetup is in the past.
- Listing supports geo filter (lat + lon + radius) or language filter.
- Each response includes contextual flags: `is_attending`, `is_organizer`.
- Deleting a meetup **cascades** and removes all `MeetupAttendee` records.

---

## Places (`/api/places`)

Proxies to Google Places API server-side (API key never exposed to client).

- `GET /places/autocomplete?input=<query>` — returns `suggestions[]` from Google Places Autocomplete (New API).
- `GET /places/{placeId}` — returns `location` (lat/lng), `displayName`, `formattedAddress` from Places Details.

Both endpoints require a Bearer token.

---

## Messaging & Chat (`/api/conversations`)

- `POST /conversations` — **idempotent**: finds existing conversation between sender and recipient or creates a new one. Always exactly one conversation per user pair.
- `POST /conversations/{id}/messages` — REST fallback; also broadcasts to `/topic/conversation.{id}` via WebSocket.
- `POST /conversations/{id}/read` — marks **all** messages in the conversation as `is_read = true` for the current user.
- `last_message_preview` is updated on every sent message regardless of REST or WebSocket path.

### WebSocket (STOMP over SockJS)

| Destination | Direction | Purpose |
|------------|-----------|---------|
| `/app/chat.send` | Client → Server | Send a message (saved to DB + broadcast) |
| `/app/chat.typing` | Client → Server | Typing indicator (broadcast only, not persisted) |
| `/topic/conversation.{id}` | Server → Client | Receive new `MessageResponse` objects |
| `/topic/conversation.{id}.typing` | Server → Client | Receive typing status events |

---

## User Blocking

- `POST /users/{id}/block` — creates a `UserBlock` record. Throws `400` if blocking self or if block already exists (not idempotent).
- `DELETE /users/{id}/block` — removes the block. Throws `400` if no block exists.

> **Known TODO**: Blocking currently does **not** automatically unfollow or remove friendship. This is a noted gap in the implementation.

---

## Language List (`/api/languages`)

`GET /api/languages` — public, no auth required. Returns all supported languages with `code`, `name`, `native_name`, and `flag_emoji`. Backed directly by `LanguageRepository.findAll()`.
