# API Reference - W19 Backend

**Base URL:** `http://localhost:8081/api`  
**Last Updated:** 2026-01-21

> **Note:** All JSON fields are **`snake_case`**.

---

## Table of Contents

1. [Authentication](#authentication)
2. [Users & Profiles](#users--profiles)
3. [Languages](#languages)
4. [Posts & Content](#posts--content)
5. [Learning Core](#learning-core)
6. [Discovery](#discovery)
7. [Meetups](#meetups)

---

## Authentication

### POST /auth/register
Register a new user account.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "username": "johndoe",
  "display_name": "John Doe"
}
```

**Response:** `201 Created`
```json
{
  "user_id": "uuid",
  "access_token": "jwt_token",
  "refresh_token": "refresh_token",
  "expires_in": 3600
}
```

### POST /auth/login
Authenticate and receive tokens.

### POST /auth/refresh
Refresh access token.
```json
{ "refresh_token": "token" }
```

### POST /auth/logout
Invalidate session (client-side).

---

## Users & Profiles

### GET /users/me
Get current user's profile.

**Response:**
```json
{
  "id": "uuid",
  "username": "johndoe",
  "display_name": "John Doe",
  "notifications_prefs": { ... },
  "languages": [
    {
      "code": "en",
      "proficiency": "NATIVE",
      "flag_emoji": "🇬🇧",
      "is_learning": false
    }
  ]
}
```

### GET /users/me/settings
### PATCH /users/me/settings

### GET /users/{id}
Get a user's public profile.

**Response:**
```json
{
  "id": "uuid",
  "username": "johndoe",
  "display_name": "John Doe",
  "avatar_url": "https://...",
  "bio": "Language enthusiast",
  "location": "Sydney, Australia",
  "followers_count": 42,
  "following_count": 18,
  "posts_count": 156,
  "is_following": false,
  "is_followed_by": false,
  "languages": [
    {
      "code": "en",
      "name": "English",
      "flag_emoji": "🇬🇧",
      "proficiency": "NATIVE",
      "is_learning": false
    }
  ]
}
```

### GET /users/{id}/posts
Get posts by a specific user.

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 10)

**Response:** Paginated `PostResponse` (same structure as feed)

### PATCH /users/me/privacy
Update privacy settings.

**Request:**
```json
{
  "show_activity": false,
  "show_saved_words": true
}
```

**Response:**
```json
{
  "show_activity": false,
  "show_saved_words": true
}
```

### PUT /users/me/languages
Update user languages (native/learning).
```json
[
  { "code": "en", "proficiency": "NATIVE", "is_learning": false },
  { "code": "es", "proficiency": "BEGINNER", "is_learning": true }
]
```

### POST /follow
Follow a user.

**Request:**
```json
{ "following_id": "uuid" }
```

### DELETE /follow
Unfollow a user.

**Request:**
```json
{ "following_id": "uuid" }
```

### GET /follow/followers
List followers (paginated).

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "username": "follower1",
      "display_name": "Follower Name",
      "avatar_url": "https://..."
    }
  ],
  "totalElements": 42,
  "totalPages": 3
}
```

### GET /follow/following
List following (paginated).

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:** Same structure as followers

---

## Languages

### GET /languages
List all supported languages.

---

## Posts & Content

### GET /posts
Get the feed.
**Params:** `page`, `size`, `language`, `latitude`, `longitude`

### POST /posts
Create a post.
```json
{
  "content": "Hello",
  "original_language": "en"
}
```

### GET /posts/{id}/translations
Get translations for a post.
**Params:** `target_language` (e.g., `es`)

**Response:**
```json
{
  "language_code": "es",
  "translated_content": "Hola"
}
```

### POST /posts/{id}/comments
### GET /posts/{id}/comments

### POST /posts/{id}/reactions
### DELETE /posts/{id}/reactions

### POST /posts/{id}/reports
Report a post.
**Reasons:** `SPAM`, `HARASSMENT`, `INAPPROPRIATE`, `MISINFORMATION`, `OTHER`

---

## Learning Core (Base: `/api/learn`)

### POST /api/learn/sessions/start
Start a session.
```json
{ "session_size": 10, "language_code": "es" }
```

### POST /api/learn/sessions/{id}/submit
Submit an answer.
```json
{ "word_id": "uuid", "is_correct": true }
```

### POST /api/learn/sessions/{id}/complete
Finish session and get summary.

### GET /api/learn/sessions
Get history.

### GET /api/words
Get vocabulary list.

### POST /api/words
Save a word manually.

### GET /api/learn/stats
Get learning statistics (XP, streaks, etc).

---

## Discovery

### GET /api/learners/nearby
Find nearby language learners based on geolocation.
**Note:** Only users with `show_location=true` enabled in their settings will be visible. The requesting user is excluded from results.

**Authentication:** Required (JWT)

**Query Parameters:**
- `latitude` (required): Latitude coordinate (-90 to 90)
- `longitude` (required): Longitude coordinate (-180 to 180)
- `radius_km` (optional, default: 10): Search radius in kilometers (must be > 0)
- `language` (optional): Filter by language code (e.g., `es`, `ja`)

**Response:** `200 OK`
```json
{
  "learners": [
    {
      "id": "uuid",
      "display_name": "John Doe",
      "avatar_url": "https://...",
      "latitude": -33.8700,
      "longitude": 151.2100,
      "distance_km": 1.23,
      "languages": [
        {
          "code": "es",
          "name": "Spanish",
          "flag_emoji": "🇪🇸",
          "proficiency": "B1",
          "is_learning": true
        }
      ]
    }
  ]
}
```

**Error Responses:**
- `400 Bad Request`: Missing or invalid parameters
- `403 Forbidden`: Not authenticated

**Example Request:**
```bash
GET /api/learners/nearby?latitude=-33.8688&longitude=151.2093&radius_km=5&language=es
Authorization: Bearer <jwt_token>
```

**Notes:**
- Results are sorted by distance (closest first)
- Current user is excluded from results
- Users without location data are excluded
- Distance calculated using Haversine formula

---

## Meetups

### GET /api/meetups
List meetups with optional filters.

**Authentication:** Required (JWT)

**Query Parameters:**
- `language` (optional): Filter by language code
- `latitude` (optional): User's latitude for geospatial search
- `longitude` (optional): User's longitude for geospatial search
- `radius_km` (optional): Search radius in kilometers (requires lat/long)
- `page` (optional, default: 0): Page number
- `size` (optional, default: 10): Page size

**Response:** `200 OK`
```json
{
  "meetups": [
    {
      "id": "uuid",
      "organizer": {
        "id": "uuid",
        "display_name": "John Doe",
        "avatar_url": "https://..."
      },
      "title": "Spanish Conversation Practice",
      "description": "Casual meetup for intermediate learners",
      "language": {
        "code": "es",
        "name": "Spanish",
        "flag_emoji": "🇪🇸"
      },
      "meetup_date": "2026-01-20T18:00:00",
      "location": "Central Park Cafe",
      "latitude": -33.8688,
      "longitude": 151.2093,
      "max_attendees": 10,
      "attendee_count": 5,
      "is_attending": false,
      "is_organizer": false,
      "status": "UPCOMING",
      "created_at": "2026-01-10T12:00:00"
    }
  ],
  "total_pages": 1,
  "total_elements": 1,
  "current_page": 0
}
```

### POST /api/meetups
Create a new meetup.

**Authentication:** Required (JWT)

**Request Body:**
```json
{
  "title": "Spanish Conversation Practice",
  "description": "Casual meetup for intermediate learners",
  "language_code": "es",
  "meetup_date": "2026-01-20T18:00:00",
  "location": "Central Park Cafe",
  "latitude": -33.8688,
  "longitude": 151.2093,
  "max_attendees": 10
}
```

**Response:** `201 Created`
```json
{
  "id": "uuid",
  "organizer": { ... },
  "title": "Spanish Conversation Practice",
  "attendee_count": 1,
  "is_attending": true,
  "is_organizer": true,
  ...
}
```

**Notes:**
- Organizer is automatically added as first attendee
- `meetup_date` must be in the future

### GET /api/meetups/{id}
Get meetup details.

**Authentication:** Required (JWT)

**Response:** `200 OK` (same structure as list response)

### PUT /api/meetups/{id}
Update a meetup (organizer only).

**Authentication:** Required (JWT)

**Request Body:** (all fields optional)
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "language_code": "ja",
  "meetup_date": "2026-01-21T18:00:00",
  "location": "New Location",
  "latitude": -33.8700,
  "longitude": 151.2100,
  "max_attendees": 15
}
```

**Response:** `200 OK`

**Error:** `400 Bad Request` if not organizer

### DELETE /api/meetups/{id}
Delete a meetup (organizer only).

**Authentication:** Required (JWT)

**Response:** `204 No Content`

**Error:** `400 Bad Request` if not organizer

### POST /api/meetups/{id}/join
Join a meetup.

**Authentication:** Required (JWT)

**Response:** `200 OK`

**Errors:**
- `400 Bad Request`: Already joined, meetup full, or meetup is in the past

### POST /api/meetups/{id}/leave
Leave a meetup.

**Authentication:** Required (JWT)

**Response:** `200 OK`

**Error:** `400 Bad Request` if user is the organizer (must delete instead)

### GET /api/meetups/{id}/attendees
List all attendees of a meetup.

**Authentication:** Required (JWT)

**Response:** `200 OK`
```json
{
  "attendees": [
    {
      "id": "uuid",
      "display_name": "John Doe",
      "avatar_url": "https://...",
      "joined_at": "2026-01-10T12:30:00Z"
    }
  ]
}
```

