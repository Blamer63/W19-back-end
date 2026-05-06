# Frontend Integration Guide

**Base URL:** `http://localhost:8081/api`
**Auth:** Bearer Token (JWT)
**Swagger UI:** `http://localhost:8081/swagger-ui.html`

> All endpoint schemas are documented in Swagger. This guide covers **frontend integration patterns**, TypeScript types, and implementation status.

---

## Quick Reference — Implementation Status

| Feature | Frontend | Backend | Status |
|---------|----------|---------|--------|
| Authentication | ✅ | ✅ | ✅ Working |
| User Profile | ✅ | ✅ | ✅ Working |
| Posts / Feed | ✅ | ✅ | ✅ Working |
| S3 File Upload | ✅ | ✅ | ✅ Working |
| Learning — Words | ✅ | ✅ | ✅ Working |
| Learning — Stats | ✅ | ✅ | ✅ Working |
| Nearby Learners | ✅ | ✅ | ✅ Working (respects `location_visibility`) |
| Chat (REST) | ✅ | ✅ | ✅ Working |
| Chat (WebSocket) | ⏳ | ✅ | 🔨 Backend done, frontend pending |
| Friend System | ⏳ | ✅ | 🔨 Backend done, frontend pending |
| Meetups | ⏳ | ✅ | 🔨 Backend done, frontend pending |
| Post Reactions | ✅ | ✅ | ⚠️ Needs testing |
| Comments | ✅ | ✅ | ⚠️ Needs testing |
| Learning Sessions | ✅ | ✅ | ⚠️ Needs testing |
| User Settings | ✅ | ✅ | ⚠️ Needs testing |
| Post Translation | ✅ | ✅ | ⚠️ Needs testing |
| Reports | ✅ | ✅ | ⚠️ Needs testing |
| Follow System | ✅ | ✅ | ⚠️ Needs testing |

---

## Frontend Service Paths (match backend contract)

| Feature | Path |
|---------|------|
| Words CRUD | `/api/words` |
| Learning Stats | `/api/learn/stats` |
| Start Session | `/api/learn/sessions/start` |
| Submit Result | `/api/learn/sessions/{id}/submit` |
| Complete Session | `/api/learn/sessions/{id}/complete` |
| Session History | `/api/learn/sessions` |
| Conversations | `/api/conversations` |
| Nearby Learners | `/api/learners/nearby` |
| File Upload | `/api/files/upload?type=images\|audio\|videos` |
| File Delete | `/api/files/delete?key=<s3-key>` |
| Meetups | `/api/meetups` |
| Places Autocomplete | `/api/places/autocomplete?input=<query>` |
| Place Details | `/api/places/{placeId}` |

---

## TypeScript Interface Reference

### Auth

```typescript
interface RegisterRequest {
  email: string;
  password: string;       // min 6 characters
  username?: string;      // optional; auto-derived from email prefix if omitted
  display_name: string;
}

interface LoginRequest {
  email: string;
  password: string;
}

interface TokenRefreshRequest {
  refresh_token: string;
}

interface AuthResponse {
  user_id: string;        // Long serialised as string
  access_token: string;
  refresh_token: string;
  expires_in: number;     // seconds (matches app.jwt.expiration-ms / 1000)
}
```

### User & Profile

```typescript
interface ProfileResponse {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  bio: string | null;
  latitude?: number;
  longitude?: number;
  location?: string;
  followers_count: number;
  following_count: number;
  languages: UserLanguageDTO[];
}

interface PublicUserProfileDto {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  bio: string | null;
  location: string | null;
  followers_count: number;
  following_count: number;
  posts_count: number;
  is_following: boolean;    // does current user follow this user?
  is_followed_by: boolean;  // does this user follow current user?
  languages: UserLanguageDTO[];
}

interface UpdateProfileRequest {
  display_name?: string;
  bio?: string;
  avatar_url?: string;     // S3 public URL after uploading via /api/files/upload
  latitude?: number;
  longitude?: number;
}

interface PrivacySettingsDto {
  location_visibility?: 'PUBLIC' | 'FRIENDS_ONLY' | 'NOBODY';
  show_activity?: boolean;
  show_saved_words?: boolean;
}

interface UserLanguageDTO {
  code: string;
  name: string;
  native_name: string;
  flag_emoji: string;
  proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'NATIVE';
  is_learning: boolean;
}

// PUT /users/me/languages — replaces entire language list
type UpdateLanguagesRequest = Array<{
  language_code: string;
  proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'NATIVE';
  is_learning: boolean;
}>;
```

### Posts

```typescript
interface CreatePostRequest {
  content: string;
  original_language: string;  // language code, e.g. "en", "ko"
  latitude?: number;
  longitude?: number;
  image_url?: string;          // S3 public URL (upload first via /api/files/upload)
}

interface PostResponse {
  id: string;
  content: string;
  original_language: string;
  image_url: string | null;
  latitude?: number;
  longitude?: number;
  distance?: string;           // e.g. "1.2 km" — only present if caller sent coordinates
  author: ProfileResponse;
  reaction_count: number;
  comment_count: number;
  user_reaction: ReactionType | null;  // current user's reaction, or null
  created_at: string;
  updated_at: string;
}

type ReactionType = 'LIKE' | 'LOVE' | 'HELPFUL' | 'FUNNY';

interface PostReactionRequest {
  reaction: ReactionType;
}

interface PostReactionResponse {
  post_id: string;
  profile_id: string;
  reaction: ReactionType;
}

interface CreateCommentRequest {
  content: string;
}

interface CommentResponse {
  id: string;
  content: string;
  created_at: string;
  author: {
    id: string;
    username: string;
    display_name: string;
    avatar_url: string | null;
  };
}

interface ReportRequest {
  reason: 'SPAM' | 'HARASSMENT' | 'INAPPROPRIATE' | 'MISINFORMATION' | 'OTHER';
  description?: string;
}

interface PostTranslationResponse {
  post_id: string;
  target_language: string;
  translated_content: string;
}
```

### File Upload (S3)

```typescript
// POST /api/files/upload?type=images|audio|videos
// Body: multipart/form-data with field "file"
interface FileUploadResponse {
  url: string;   // full public S3 URL — store this on the entity (post.image_url, profile.avatar_url, etc.)
}

// DELETE /api/files/delete?key=<s3-key>
interface FileDeleteResponse {
  message: string;  // "File deleted successfully"
}

// File size limits:
// images → 5 MB
// audio  → 20 MB
// videos → 100 MB

// Upload pattern:
// 1. POST /api/files/upload?type=images  (multipart form)
// 2. Use returned `url` in CreatePostRequest.image_url or UpdateProfileRequest.avatar_url
```

### Friends

```typescript
interface FriendRequestResponse {
  id: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  is_sent_by_me: boolean;
  other_user: ProfileResponse;
  created_at: string;
}
```

> Use `is_sent_by_me` + `status` to determine button state:
> - No relationship (`204`) → "Add Friend"
> - `PENDING + is_sent_by_me` → "Pending" (cancel option)
> - `PENDING + !is_sent_by_me` → "Respond" (accept/reject)
> - `ACCEPTED` → "Friends" (remove option)

`GET /users/{id}/friend-status` returns `204 No Content` (no relationship) or `200 OK` with `FriendRequestResponse`.

### Learning — Words & Sessions

```typescript
interface CreateWordRequest {
  word: string;
  translation: string;
  language_code: string;
  notes?: string;
}

interface UpdateWordRequest {
  translation?: string;
  notes?: string;
}

interface SavedWordResponse {
  id: string;             // UUID
  word: string;
  translation: string;
  language_code: string;
  notes?: string;
  mastery_level: number;  // 0–100
  next_review: string;    // ISO-8601 timestamp
  created_at: string;
}

interface StartSessionRequest {
  session_size: 5 | 10 | 15;
  language_code: string;
}

interface StartSessionResponse {
  session_id: string;     // UUID
  words: PracticeWordDTO[];
}

interface PracticeWordDTO {
  word_id: string;
  word: string;
  translation: string;
  mastery_level: number;
}

interface SubmitResultRequest {
  word_id: string;
  is_correct: boolean;
}

interface SubmitResultResponse {
  word_id: string;
  is_correct: boolean;
  old_mastery: number;
  new_mastery: number;
}

interface CompleteSessionResponse {
  session_id: string;
  words_practiced: number;
  correct_count: number;
  accuracy: number;         // 0–100 percentage
  duration_seconds: number;
  results: WordResultDTO[];
}

interface WordResultDTO {
  word_id: string;
  word: string;
  correct: boolean;
  old_mastery: number;
  new_mastery: number;
}
```

### Learning — Stats

```typescript
interface LearningStatsResponse {
  total_words: number;
  average_mastery: number;
  languages: LanguageStatDTO[];
  mastery_distribution: MasteryDistributionDTO;
}

interface LanguageStatDTO {
  language_code: string;
  language_name: string;
  word_count: number;
  average_mastery: number;
}

interface MasteryDistributionDTO {
  beginner: number;    // mastery 0–25
  learning: number;    // mastery 26–50
  familiar: number;    // mastery 51–75
  mastered: number;    // mastery 76–100
}
```

### Meetups

```typescript
interface CreateMeetupRequest {
  title: string;
  description?: string;
  language_code: string;
  meetup_date: string;     // ISO-8601 datetime (must be future)
  location_name: string;
  latitude: number;
  longitude: number;
  max_attendees?: number;
}

interface UpdateMeetupRequest {
  title?: string;
  description?: string;
  meetup_date?: string;
  location_name?: string;
  latitude?: number;
  longitude?: number;
  max_attendees?: number;
}

interface MeetupResponse {
  id: string;
  title: string;
  description: string | null;
  language: LanguageInfo;
  organizer: ProfileResponse;
  meetup_date: string;
  location_name: string;
  latitude: number;
  longitude: number;
  max_attendees: number | null;
  attendee_count: number;
  status: 'UPCOMING' | 'COMPLETED' | 'CANCELLED';
  is_attending: boolean;
  is_organizer: boolean;
  created_at: string;
}

interface MeetupAttendeeResponse {
  profile: ProfileResponse;
  joined_at: string;
}

interface LanguageInfo {
  code: string;
  name: string;
  flag_emoji: string;
}
```

### Nearby Learners

```typescript
// GET /api/learners/nearby
// Query params: latitude, longitude, radius_km (default 10), language (optional)
interface LearnerResponse {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  bio: string | null;
  distance_km: number;
  languages: UserLanguageDTO[];
}
```

### Chat

```typescript
// POST /conversations  — new conversation or first message
interface CreateConversationRequest {
  recipient_id: string;
  content: string;
}

// POST /conversations/{id}/messages
interface CreateMessageRequest {
  content: string;
}

interface MessageResponse {
  id: string;
  conversation_id: string;
  sender_id: string;
  content: string;
  is_read: boolean;
  created_at: string;
}

interface ConversationResponse {
  id: string;
  recipient: {
    id: string;
    username: string;
    display_name: string;
    avatar_url: string | null;
  };
  last_message_preview: string | null;
  last_message_at: string | null;
  unread_count: number;
}
```

---

## WebSocket Integration (STOMP over SockJS)

```typescript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8081/ws'),
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
  onConnect: () => {
    // Subscribe to incoming messages for a conversation
    client.subscribe(`/topic/conversation.${conversationId}`, (frame) => {
      const message: MessageResponse = JSON.parse(frame.body);
      // add to local message list
    });

    // Subscribe to typing indicators
    client.subscribe(`/topic/conversation.${conversationId}.typing`, (frame) => {
      const { sender_id } = JSON.parse(frame.body);
      // show "X is typing..." UI
    });
  },
});

client.activate();

// Send a message
client.publish({
  destination: '/app/chat.send',
  body: JSON.stringify({ recipient_id: recipientId, content: 'Hello!' }),
});

// Send typing indicator
client.publish({
  destination: '/app/chat.typing',
  body: JSON.stringify({ cid: conversationId }),
});
```

> **Note:** Typing events are not persisted — they are broadcast and forgotten. The REST `POST /conversations/{id}/messages` endpoint is a reliable fallback and saves to the database.

---

## Pagination

All paginated responses follow Spring's `Page<T>` envelope:

```typescript
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // current page (0-indexed)
  size: number;
}
```

Common query params: `?page=0&size=20&sort=createdAt,desc`

---

## S3 Image Upload Flow

```
1.  Pick file in UI
2.  POST /api/files/upload?type=images
    Content-Type: multipart/form-data
    Body: file=<binary>
    → { url: "https://fs-kaiday-customer-test.s3.ap-southeast-2.amazonaws.com/images/<uuid>-photo.jpg" }

3a. Creating a post:
    POST /api/posts  { content: "...", image_url: "<url from step 2>" }

3b. Updating avatar:
    PATCH /api/users/me  { avatar_url: "<url from step 2>" }
    → Backend automatically deletes the old avatar from S3

4.  Deleting a post:
    DELETE /api/posts/{id}
    → Backend automatically deletes the associated S3 image
```

---

## Not Yet Implemented (Backend Needed)

- **AI Object Scanner** — `POST /api/scanner/analyze` (image upload → vocabulary suggestions)
- **Notifications** — push/in-app notification endpoints
- **Moderation Dashboard** — admin endpoints for reviewing content reports
