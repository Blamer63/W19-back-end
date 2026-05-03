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
| Learning — Words | ✅ | ✅ | ✅ Working |
| Learning — Stats | ✅ | ✅ | ✅ Working |
| Nearby Learners | ✅ | ✅ | ✅ Working (respects `location_visibility`) |
| Chat (REST) | ✅ | ✅ | ✅ Working |
| Chat (WebSocket) | ⏳ | ✅ | 🔨 Backend done, frontend pending |
| Friend System | ⏳ | ✅ | 🔨 Backend done, frontend pending |
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

---

## TypeScript Interface Reference

### Auth

```typescript
interface RegisterRequest {
  email: string;
  password: string;       // min 6 characters
  username?: string;      // optional
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
  user_id: string;        // UUID
  access_token: string;
  refresh_token: string;
  expires_in: number;     // seconds
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
  avatar_url?: string;
  latitude?: number;
  longitude?: number;
}

interface PrivacySettingsDto {
  show_activity?: boolean;
  show_saved_words?: boolean;
  location_visibility?: 'PUBLIC' | 'FRIENDS_ONLY' | 'NOBODY';
}

interface UserLanguageDTO {
  code: string;
  name: string;
  flag_emoji: string;
  proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'NATIVE';
  is_learning: boolean;
}
```

### Posts

```typescript
interface CreatePostRequest {
  content: string;
  original_language: string;
  latitude?: number;
  longitude?: number;
  image_url?: string;
}

interface PostResponse {
  id: string;
  content: string;
  original_language: string;
  author: ProfileResponse;
  reaction_count: number;
  comment_count: number;
  user_reaction: string | null;
  created_at: string;
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

interface PostReactionRequest {
  reaction: 'LIKE' | 'LOVE' | 'HELPFUL' | 'FUNNY';
}

interface PostReactionResponse {
  post_id: string;
  profile_id: string;
  reaction: string;
}

interface ReportRequest {
  reason: 'SPAM' | 'HARASSMENT' | 'INAPPROPRIATE' | 'MISINFORMATION' | 'OTHER';
  description?: string;
}
```

### Friends

```typescript
interface FriendRequestResponse {
  id: string;                               // UUID of the Friend record
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  is_sent_by_me: boolean;
  other_user: ProfileResponse;
  created_at: string;
}
```

> Use `is_sent_by_me` + `status` to determine button state:
> - `null` → "Add Friend"
> - `PENDING + is_sent_by_me` → "Pending" (cancel option)
> - `PENDING + !is_sent_by_me` → "Respond" (accept/reject)
> - `ACCEPTED` → "Friends" (remove option)

`GET /users/{id}/friend-status` returns `204 No Content` (no relationship) or `200 OK` with `FriendRequestResponse`.

### Learning

```typescript
interface StartSessionRequest {
  session_size: number;
  language_code: string;
}

interface SubmitResultRequest {
  word_id: string;
  is_correct: boolean;
}

interface CreateWordRequest {
  word: string;
  translation: string;
  language_code: string;
  notes?: string;
}

interface SavedWordResponse {
  id: string;
  word: string;
  translation: string;
  language_code: string;
  notes?: string;
  created_at: string;
}
```

### Chat

```typescript
// REST: POST /conversations or POST /conversations/{id}/messages
interface ChatRequest {
  recipient_id?: string;  // required for new conversations
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
    display_name: string;
    avatar_url: string | null;
  };
  last_message_preview: string;
  last_message_at: string;
}
```

### AI Object Scanner

```typescript
interface ScannerAnalyzeRequest {
  image_base64: string;
  target_language: string;
  confidence_threshold?: number; // default 0.4
  max_results?: number;          // default 6
}

interface DetectedObjectResponse {
  label: string;
  translated_label: string;
  confidence: number;
  translated: boolean;
}

interface ScannerAnalyzeResponse {
  status: 'OK' | 'NO_OBJECTS';
  message: string;
  target_language: string;
  detection_count: number;
  detections: DetectedObjectResponse[];
}
```

Frontend loop guidance for real-time use:
- Capture camera frames on an interval (e.g. 300-700ms), not every rendered frame.
- Send each frame to `POST /api/scanner/analyze`.
- If `status === "NO_OBJECTS"`, show a neutral empty state (e.g. "No objects detected").
- If `translated` is `false`, show the original `label` as fallback.

---

## WebSocket Integration (STOMP over SockJS)

```typescript
// Connect
const socket = new SockJS('http://localhost:8081/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({ Authorization: `Bearer ${token}` }, () => {
  // Subscribe to conversation messages
  stompClient.subscribe(`/topic/conversation.${conversationId}`, (msg) => {
    const message: MessageResponse = JSON.parse(msg.body);
    // handle incoming message
  });

  // Subscribe to typing indicators
  stompClient.subscribe(`/topic/conversation.${conversationId}.typing`, (msg) => {
    const typing = JSON.parse(msg.body);
    // handle typing status
  });
});

// Send message
stompClient.send('/app/chat.send', {}, JSON.stringify({
  recipient_id: recipientId,
  content: 'Hello!'
}));

// Send typing indicator
stompClient.send('/app/chat.typing', {}, JSON.stringify({ cid: conversationId }));
```

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

---

## Not Yet Implemented (Backend Needed)

- **Notifications**
- **Moderation Dashboard**
