# API Contract — W19 Backend

**Base URL:** `http://localhost:8081/api`
**Authentication:** Bearer Token (JWT)
**Format:** JSON (`snake_case`)

> **Swagger UI:** `http://localhost:8081/swagger-ui.html`
> Full interactive schemas, request/response shapes, and try-it-out are available via Swagger. This document captures the **complete endpoint inventory** and conventions not visible from the schema alone.

---

## Conventions

- All JSON fields use `snake_case` (e.g. `display_name`, `avatar_url`, `is_correct`).
- All timestamps are ISO-8601 strings (e.g. `"2026-04-12T10:00:00Z"`).
- Most IDs are UUIDs (`string`). `Profile`, `Post`, `Meetup`, and `Conversation` IDs are `Long` (`number`).
- Paginated responses follow Spring's `Page<T>` envelope: `content`, `totalElements`, `totalPages`, `number` (0-indexed), `size`.
- Protected routes require `Authorization: Bearer <access_token>` header.
- Public routes (no auth required): `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /languages`, `GET /places/**`.
- Partial updates (`PATCH`) only modify fields explicitly included in the request body.

---

## Endpoint Inventory

### Auth — `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | None | Register new account → `AuthResponse` |
| POST | `/auth/login` | None | Login → `AuthResponse` |
| POST | `/auth/refresh` | None | Exchange refresh token → new `AuthResponse` |
| POST | `/auth/logout` | Bearer | Invalidate refresh token server-side |

### Users — `/api/users`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/users/me` | Bearer | Own profile |
| PATCH | `/users/me` | Bearer | Partial profile update (display_name, bio, avatar_url, lat/lon) |
| GET | `/users/{userId}` | Bearer | Public profile of another user |
| GET | `/users/{userId}/posts` | Bearer | Paginated posts by user |
| GET | `/users/me/settings` | Bearer | Notification/theme settings |
| PATCH | `/users/me/settings` | Bearer | Update settings |
| PATCH | `/users/me/privacy` | Bearer | Update privacy (location_visibility, show_activity, show_saved_words) |
| GET | `/users/me/languages` | Bearer | Own language list |
| PUT | `/users/me/languages` | Bearer | Replace entire language list |
| POST | `/users/{userId}/block` | Bearer | Block a user |
| DELETE | `/users/{userId}/block` | Bearer | Unblock a user |

### Follow — `/api/users`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/users/{id}/follow` | Bearer | Follow user |
| DELETE | `/users/{id}/follow` | Bearer | Unfollow user |
| GET | `/users/{id}/followers` | Bearer | Paginated follower list |
| GET | `/users/{id}/following` | Bearer | Paginated following list |

### Friends — `/api/users`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/users/{id}/friend-request` | Bearer | Send friend request |
| PATCH | `/users/me/friend-requests/{friendId}` | Bearer | Accept or reject (`?action=accept\|reject`) |
| DELETE | `/users/{id}/friend` | Bearer | Remove friend |
| GET | `/users/{id}/friends` | Bearer | Paginated friend list |
| GET | `/users/me/friend-requests/incoming` | Bearer | Incoming pending requests |
| GET | `/users/me/friend-requests/outgoing` | Bearer | Outgoing pending requests |
| GET | `/users/{id}/friend-status` | Bearer | Current relationship status (`204` if none) |

### Posts — `/api/posts`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/posts` | Bearer | Paginated feed (`?language=`, `?latitude=`, `?longitude=`) |
| POST | `/posts` | Bearer | Create post |
| GET | `/posts/{postId}` | Bearer | Single post |
| DELETE | `/posts/{postId}` | Bearer | Delete post (author only; also deletes S3 image) |
| GET | `/posts/{postId}/translations` | Bearer | Get/auto-create cached Google translation (`?target_language=<code>`) |
| POST | `/posts/{postId}/reports` | Bearer | Report post |

### Reactions — `/api/posts/{postId}/reactions`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/posts/{postId}/reactions` | Bearer | React (upsert) — one per user per post |
| DELETE | `/posts/{postId}/reactions` | Bearer | Remove reaction |

### Comments — `/api/posts/{postId}/comments`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/posts/{postId}/comments` | Bearer | Paginated comments (oldest first) |
| POST | `/posts/{postId}/comments` | Bearer | Add comment |
| DELETE | `/posts/{postId}/comments/{commentId}` | Bearer | Delete comment (author only) |

### Vocabulary — `/api/words`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/words` | Bearer | Paginated word list (`?language=`, `?sort=newest`) |
| GET | `/words/{wordId}` | Bearer | Single word |
| POST | `/words` | Bearer | Save new word |
| PATCH | `/words/{wordId}` | Bearer | Update word/translation/notes |
| DELETE | `/words/{wordId}` | Bearer | Delete word |

### Practice — `/api/learn`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/learn/sessions/start` | Bearer | Start session (`session_size`: 5, 10, or 15; `language_code`) |
| POST | `/learn/sessions/{id}/submit` | Bearer | Submit one word answer |
| POST | `/learn/sessions/{id}/complete` | Bearer | Complete session → accuracy/duration summary |
| GET | `/learn/sessions` | Bearer | Paginated session history |
| GET | `/learn/stats` | Bearer | Aggregated vocabulary statistics |

### Meetups — `/api/meetups`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/meetups` | Bearer | Paginated upcoming meetups (`?language=`, geo params) |
| POST | `/meetups` | Bearer | Create meetup (organiser auto-joined) |
| GET | `/meetups/{id}` | Bearer | Meetup detail with `is_attending`, `is_organizer` flags |
| PUT | `/meetups/{id}` | Bearer | Update meetup (organiser only) |
| DELETE | `/meetups/{id}` | Bearer | Delete meetup + attendees (organiser only) |
| POST | `/meetups/{id}/join` | Bearer | Join meetup |
| POST | `/meetups/{id}/leave` | Bearer | Leave meetup (non-organiser only) |
| GET | `/meetups/{id}/attendees` | Bearer | Attendee list |

### Nearby Learners — `/api/learners`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/learners/nearby` | Bearer | Geospatial learner search (`?latitude=`, `?longitude=`, `?radius_km=`, `?language=`) |

### Chat — `/api/conversations`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/conversations` | Bearer | Paginated conversation list |
| POST | `/conversations` | Bearer | Start conversation or send first message |
| GET | `/conversations/{id}/messages` | Bearer | Paginated message history |
| POST | `/conversations/{id}/messages` | Bearer | Send message (also broadcasts via WebSocket) |
| POST | `/conversations/{id}/read` | Bearer | Mark all messages as read |

### Files — `/api/files`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/files/upload?type=images\|audio\|videos` | Bearer | Upload file to S3 → `{ url: string }` |
| DELETE | `/files/delete?key=<s3-key>` | Bearer | Delete file from S3 |

**File size limits:**

| Type | Max size |
|------|----------|
| `images` | 5 MB |
| `audio` | 20 MB |
| `videos` | 100 MB |

### Languages — `/api/languages`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/languages` | None | Full language list (code, name, nativeName, flagEmoji) |

### Places — `/api/places`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/places/autocomplete?input=<query>` | Bearer | Google Places autocomplete suggestions |
| GET | `/places/{placeId}` | Bearer | Place details (lat/lng, displayName, formattedAddress) |

---

## WebSocket (STOMP over SockJS)

**Endpoint:** `http://localhost:8081/ws` (with SockJS fallback)

| STOMP destination | Direction | Purpose |
|------------------|-----------|---------|
| `/app/chat.send` | Client → Server | Send a message |
| `/app/chat.typing` | Client → Server | Broadcast typing indicator (not persisted) |
| `/topic/conversation.{id}` | Server → Client | Receive new messages |
| `/topic/conversation.{id}.typing` | Server → Client | Receive typing status |

---

## Standard Error Response

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found with id: 99",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

| HTTP Status | Cause |
|-------------|-------|
| 400 | Validation error, business rule violation |
| 401 | Missing or invalid JWT |
| 403 | Authenticated but not authorised (e.g. not organiser) |
| 404 | Entity not found |
| 409 | Duplicate / conflict (e.g. duplicate friend request) |

---

## Future / Pending Endpoints

- `POST /api/scanner/analyze` — AI object scanner (image → vocabulary suggestions)
- Notifications system
- Moderation / admin dashboard
