# API Reference - W19 Backend

**Base URL:** `http://localhost:8081/api`  
**Last Updated:** 2026-03-05

> **Note:** All JSON fields are **`snake_case`**.

---

## Table of Contents

1. [Authentication](#authentication)
2. [Users & Profiles](#users--profiles)
3. [Friends](#friends)
4. [Languages](#languages)
5. [Posts & Content](#posts--content)
6. [Learning Core](#learning-core)
7. [Discovery](#discovery)
8. [Meetups](#meetups)
9. [Places](#places)
10. [Messaging & Chat](#messaging--chat)

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
  "show_saved_words": true,
  "location_visibility": "FRIENDS_ONLY"
}
```

**Response:**
```json
{
  "show_activity": false,
  "show_saved_words": true,
  "location_visibility": "FRIENDS_ONLY"
}
```

> `location_visibility` values: `"PUBLIC"` (default, visible to everyone on map), `"FRIENDS_ONLY"` (only mutual friends), `"NOBODY"` (hidden from map entirely).

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

## Friends

> The Friend system is a **mutual** relationship (both sides must agree). It is separate from the one-way Follow system. Friendship controls location visibility on the map.

### POST /users/{id}/friend-request
Send a friend request to another user.

**Auth:** Required  
**Response:** `201 Created`
```json
{
  "id": "uuid",
  "status": "PENDING",
  "is_sent_by_me": true,
  "other_user": { "id": "uuid", "username": "...", "display_name": "..." },
  "created_at": "2026-03-05T00:00:00Z"
}
```
**Errors:** `409 Conflict` if already friends or request pending. Re-sending after a rejection is allowed.

### PATCH /users/me/friend-requests/{friendId}?action=accept|reject
Accept or reject a pending incoming friend request.

**Auth:** Required  
**Query Param:** `action` — `accept` or `reject`  
**Response:** `200 OK` (updated `FriendRequestResponse`)

### DELETE /users/{id}/friend
Remove an existing friend.

**Auth:** Required  
**Response:** `204 No Content`

### GET /users/{id}/friends
List all accepted friends of a user (paginated).

**Auth:** Required  
**Query Parameters:** `page` (default: 0), `size` (default: 20)  
**Response:** Paginated `ProfileResponse`

### GET /users/me/friend-requests/incoming
List all pending friend requests sent to the current user.

**Auth:** Required  
**Response:** Paginated `FriendRequestResponse`

### GET /users/me/friend-requests/outgoing
List all pending friend requests sent by the current user.

**Auth:** Required  
**Response:** Paginated `FriendRequestResponse`

### GET /users/{id}/friend-status
Check the friendship status between the current user and another user.

**Auth:** Required  
**Response:** `200 OK` with `FriendRequestResponse`, or `204 No Content` if no relationship exists.

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

**Note:** Results respect each user's `location_visibility` setting:
- `PUBLIC` — always visible (default)
- `FRIENDS_ONLY` — only visible to mutual friends
- `NOBODY` — hidden from all map results

The requesting user is always excluded from results.

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
          "code": "en",
          "name": "English",
          "flag_emoji": "🇺🇸",
          "proficiency": "NATIVE",
          "is_learning": false
        }
      ],
      "learning_languages": [
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


---

## Places

### GET /places/autocomplete
Search for places using Google Places Autocomplete (New) API.

**Query Parameters:**
- `input` (required): Search query string (e.g., "UOW Library")

**Response:**
```json
{
  "suggestions": [
    {
      "placePrediction": {
        "placeId": "ChIJ...",
        "text": { "text": "University of Wollongong Library, Wollongong NSW" },
        "structuredFormat": {
          "mainText": { "text": "University of Wollongong Library" },
          "secondaryText": { "text": "Wollongong NSW, Australia" }
        }
      }
    }
  ]
}
```

### GET /places/{placeId}
Get place details (location, address).

**Path Variables:**
- `placeId` (required): Google Place ID.

**Response:**
```json
{
  "location": {
    "latitude": -34.406,
    "longitude": 150.878
  },
  "displayName": {
    "text": "University of Wollongong Library"
  },
  "formattedAddress": "Northfields Ave, Wollongong NSW 2522"
}

---

## Messaging & Chat

### GET /conversations
List user's active conversations.

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "recipient": {
        "id": "uuid",
        "display_name": "Jane Doe",
        "avatar_url": "https://..."
      },
      "last_message_preview": "Hey, how are you?",
      "last_message_at": "2026-02-18T12:00:00Z"
    }
  ],
  "totalElements": 5
}
```

### POST /conversations
Initiate a new conversation or send a message to a recipient.

**Request Body:**
```json
{
  "recipient_id": "uuid",
  "content": "Hello!"
}
```

**Response:** `200 OK` (MessageResponse)

### GET /conversations/{id}/messages
Get message history for a conversation.

**Query Parameters:**
- `page` index
- `size` count

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "conversation_id": "uuid",
      "sender_id": "uuid",
      "content": "Hello!",
      "is_read": false,
      "created_at": "2026-02-18T12:00:00Z"
    }
  ]
}
```

### POST /conversations/{id}/messages
Reply to an existing conversation.

**Request Body:**
```json
{ "content": "Hello back!" }
```
```
