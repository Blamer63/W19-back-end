# API Reference - W19 Backend

**Base URL:** `http://localhost:8081/api`

> **Swagger UI:** `http://localhost:8081/swagger-ui.html`  
> Full endpoint schemas, request/response shapes, and try-it-out are available via Swagger. This document captures **business rules and behavioural notes** that are not visible from the schema alone.

---

## Authentication

- Tokens are JWTs. The `access_token` is short-lived; use `refresh_token` via `POST /auth/refresh` to rotate.
- `POST /auth/logout` invalidates the refresh token server-side. Clients should also clear local storage.
- Public (no auth): `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /languages`.

---

## Users & Profiles

- `GET /users/{id}` — public profile. The `is_following` / `is_followed_by` flags reflect the **current authenticated user's** relationship with the target.
- `PATCH /users/me` — supports partial updates; only provided fields are changed.
- `PATCH /users/me/privacy` — controls map visibility:
  - `location_visibility`: `"PUBLIC"` (default, visible to everyone), `"FRIENDS_ONLY"` (mutual friends only), `"NOBODY"` (hidden from map entirely).

---

## Follow vs. Friend System

Two separate social graphs exist:

| Feature | Type | Endpoint prefix |
|---------|------|----------------|
| **Follow** | One-way (like Twitter) | `POST/DELETE /users/{id}/follow` |
| **Friend** | Mutual (both must agree) | `/users/{id}/friend-request` etc. |

**Friendship** controls location visibility on the map. **Follow** does not affect map visibility.

### Friend Request Lifecycle
`PENDING` → `ACCEPTED` or `REJECTED`. A rejected user **can re-send** a request (the old record is deleted). Duplicate requests return `409 Conflict`.

### `FriendRequestResponse` status interpretation
| `status` | `is_sent_by_me` | Meaning |
|----------|----------------|---------|
| `PENDING` | `true` | You sent, awaiting response |
| `PENDING` | `false` | You received, action required |
| `ACCEPTED` | either | Friends |
| `REJECTED` | — | Rejected (re-send is allowed) |

`GET /users/{id}/friend-status` returns `204 No Content` when no relationship exists.

---

## Posts & Content

- `GET /posts` (feed) supports optional `latitude`/`longitude` for location-based sorting.
- `GET /posts/{id}/translations?target_language=<code>` — cached per post per language.
- `POST /posts/{id}/reports` — valid `reason` values: `SPAM`, `HARASSMENT`, `INAPPROPRIATE`, `MISINFORMATION`, `OTHER`.
- Reactions: only one reaction per user per post. `POST /posts/{id}/reactions` upserts (replaces existing).

---

## Learning Core (`/api/learn`, `/api/words`)

### Session Flow
1. `POST /api/learn/sessions/start` — supply `session_size` and `language_code`.
2. `POST /api/learn/sessions/{id}/submit` — submit one answer at a time (`word_id`, `is_correct`).
3. `POST /api/learn/sessions/{id}/complete` — finalise; returns XP and score summary.

`GET /api/learn/sessions` returns history paginated.

### Words (`/api/words`)
Full CRUD for the current user's saved vocabulary. Supports filtering by `language` and sorting by `newest` (default).

---

## Discovery — Nearby Learners (`GET /api/learners/nearby`)

Results respect each user's `location_visibility` setting. The requesting user is always excluded. Sorted by distance (Haversine formula). Users without location data are excluded.

---

## Meetups (`/api/meetups`)

- `meetup_date` must be a **future** datetime.
- The organiser is automatically added as the first attendee on creation.
- Only the organiser can `PUT` or `DELETE` a meetup.
- Organiser cannot `POST /{id}/leave` — they must delete the meetup instead.
- `POST /{id}/join` fails with `400` if: already joined, meetup is full, or meetup is in the past.

---

## Places (`/api/places`)

Proxies to Google Places API (server-side, keeps API key secure). No auth required.

- `GET /places/autocomplete?input=<query>` — returns `suggestions[]` from Google Autocomplete (New).
- `GET /places/{placeId}` — returns `location` (lat/lng), `displayName`, `formattedAddress`.

---

## Messaging & Chat (`/api/conversations`)

- `POST /conversations` — starts a new conversation **or** sends the first message to an existing one (deduplication: only one conversation per user pair).
- `POST /conversations/{id}/messages` — REST fallback; also broadcasts via WebSocket to `/topic/conversation.{id}`.
- `POST /conversations/{id}/read` — marks all messages in the conversation as read for the current user.

### WebSocket (STOMP over SockJS)
| Destination | Direction | Purpose |
|------------|-----------|---------|
| `/app/chat.send` | Client → Server | Send a message |
| `/app/chat.typing` | Client → Server | Broadcast typing indicator |
| `/topic/conversation.{id}` | Server → Client | Receive messages |
| `/topic/conversation.{id}.typing` | Server → Client | Receive typing status |

---

## AI Object Scanner (`/api/scanner`)

- `POST /scanner/analyze` accepts camera frame payload (`image_base64`) and `target_language`.
- Detection is delegated to configured YOLO API (`YOLO_API_URL`).
- Object labels are translated through configured translation API (`TRANSLATION_API_URL`).
- Duplicate labels are translated once per request, then cached in-memory for low-latency repeated frames.
- When no objects are detected, endpoint returns `status=NO_OBJECTS` with empty `detections`.
- If translation fails for a label, backend falls back to original English label for that item.
