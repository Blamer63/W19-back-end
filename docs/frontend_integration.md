# Frontend Integration Guide - Current Implementation

**Last Updated:** 2026-03-05  
**Base URL:** `http://localhost:8081/api`  
**Auth:** Bearer Token (JWT)

> **IMPORTANT:** All JSON responses now use `snake_case` field names to match the API contract.

---

## Table of Contents

1. [Authentication](#1-authentication) - 4 endpoints ✅
2. [Users & Profiles](#2-users--profiles) - 16 endpoints ✅
3. [Languages](#3-languages) - 2 endpoints ✅
4. [Posts & Content](#4-posts--content) - 9 endpoints ⚠️
5. [Social Features](#5-social-features) - 4 endpoints ✅
6. [Friends](#6-friends) - 6 endpoints ✅

**Total Implemented:** 41 endpoints

---

## 1. Authentication

### Register
**Endpoint:** `POST /auth/register`

**Request:**
```typescript
interface RegisterRequest {
  email: string;
  password: string; // min 6 characters
  username?: string; // optional
  display_name: string;
}
```

**Response:** `201 Created`
```typescript
interface AuthResponse {
  user_id: string; // UUID
  access_token: string;
  refresh_token: string;
  expires_in: number; // seconds
}
```

**Example:**
```typescript
const register = async (email: string, password: string, displayName: string) => {
  const response = await fetch('http://localhost:8081/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email,
      password,
      display_name: displayName
    })
  });
  return await response.json();
};
```

---

### Login
**Endpoint:** `POST /auth/login`

**Request:**
```typescript
interface LoginRequest {
  email: string;
  password: string;
}
```

**Response:** `200 OK` (same as AuthResponse)

**Example:**
```typescript
const login = async (email: string, password: string) => {
  const response = await fetch('http://localhost:8081/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  const data = await response.json();
  localStorage.setItem('access_token', data.access_token);
  localStorage.setItem('refresh_token', data.refresh_token);
  return data;
};
```

---

### Refresh Token
**Endpoint:** `POST /auth/refresh`

**Request:**
```typescript
interface TokenRefreshRequest {
  refresh_token: string;
}
```

**Response:** `200 OK` (new AuthResponse with rotated tokens)

**Example:**
```typescript
const refreshToken = async () => {
  const refreshToken = localStorage.getItem('refresh_token');
  const response = await fetch('http://localhost:8081/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresh_token: refreshToken })
  });
  const data = await response.json();
  localStorage.setItem('access_token', data.access_token);
  localStorage.setItem('refresh_token', data.refresh_token);
  return data;
};
```

---

### Logout
**Endpoint:** `POST /auth/logout`  
**Auth:** Required

**Response:** `204 No Content`

**Example:**
```typescript
const logout = async () => {
  const token = localStorage.getItem('access_token');
  await fetch('http://localhost:8081/api/auth/logout', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  localStorage.clear();
};
```
| Feature | Frontend | Backend | Endpoint | Status |
|---------|----------|---------|----------|--------|
| **Authentication** | ✅ | ✅ | `POST /api/auth/login` | ✅ Working |
| **User Profile** | ✅ | ✅ | `GET /api/users/me` | ✅ Working |
| **Posts/Feed** | ✅ | ✅ | `GET /api/posts` | ✅ Working |
| **Learning - Words** | ✅ | ✅ | `GET /api/words` | ✅ Working |
| **Learning - Stats** | ✅ | ✅ | `GET /api/learn/stats` | ✅ Working |
| **Nearby Learners** | ✅ | ✅ | `GET /api/learners/nearby` | ✅ Working (respects `location_visibility`) |
| **Chat Feature** | ✅ | ✅ | `/api/conversations` | ✅ REST API Done (WS Pending) |
| **Friend System** | ⏳ | ✅ | `/api/users/{id}/friend-request` | 🔨 Backend done, frontend pending |

---

## 💬 Chat API Integration
For detailed documentation on the Chat feature, see: **[Chat Integration Guide](./chat_integration_guide.md)**

---

## 🟡 Frontend Ready, Awaiting Full Testing

| Feature | Frontend | Endpoint | Notes |
|---------|----------|----------|-------|
| **Post Reactions** | ✅ | `POST/DELETE /posts/{id}/reactions` | Need to test like/unlike |
| **Comments** | ✅ | `GET/POST /posts/{id}/comments` | Need to test create/list |
| **Learning Sessions** | ✅ | `POST /api/learn/sessions/start` | Need to add words first |
| **Submit Answer** | ✅ | `POST /api/learn/sessions/{id}/submit` | Requires active session |
| **Complete Session** | ✅ | `POST /api/learn/sessions/{id}/complete` | Requires active session |
| **Session History** | ✅ | `GET /api/learn/sessions` | Need sessions first |
| **User Settings** | ✅ | `GET/PATCH /users/me/settings` | Untested |
| **Languages List** | ✅ | `GET /languages` | Untested |
| **Post Translation** | ✅ | `GET /posts/{id}/translations` | Untested |
| **Reports** | ✅ | `POST /posts/{id}/reports` | Untested |
| **Follow System** | ✅ | `POST/DELETE /users/{id}/follow` | Untested |

---

### Get Public Profile
**Endpoint:** `GET /users/{userId}`  
**Auth:** Required

**Response:**
```typescript
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
  is_following: boolean;  // Does current user follow this user?
  is_followed_by: boolean;  // Does this user follow current user?
  languages: UserLanguage[];
}
```

**Example:**
```typescript
const getUserProfile = async (userId: string) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(`http://localhost:8081/api/users/${userId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
};
```

---

### Get User Posts
**Endpoint:** `GET /users/{userId}/posts`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 10)

**Response:** Paginated `PostResponse` (same structure as feed)

**Example:**
```typescript
const getUserPosts = async (userId: string, page = 0) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/users/${userId}/posts?page=${page}&size=10`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

### Update Privacy Settings
**Endpoint:** `PATCH /users/me/privacy`  
**Auth:** Required

**Request:**
```typescript
interface PrivacySettingsDto {
  show_activity?: boolean;          // Show learning activity on profile
  show_saved_words?: boolean;        // Show saved words count
  location_visibility?: 'PUBLIC' | 'FRIENDS_ONLY' | 'NOBODY'; // Who sees you on the map
}
```

**Response:** Updated `PrivacySettingsDto` (same shape, all fields present)

> **`location_visibility` values:**
> - `PUBLIC` — visible to everyone on the map (default for all existing users)
> - `FRIENDS_ONLY` — only mutual friends can see your location
> - `NOBODY` — you are hidden from the map entirely

**Example:**
```typescript
const updatePrivacy = async (settings: PrivacySettingsDto) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch('http://localhost:8081/api/users/me/privacy', {
    method: 'PATCH',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(settings)
  });
  return await response.json();
};
```

---

### Update Profile
**Endpoint:** `PATCH /users/me`  
**Auth:** Required

**Request:** (partial updates supported)
```typescript
interface UpdateProfileRequest {
  display_name?: string;
  bio?: string;
  avatar_url?: string;
  latitude?: number;
  longitude?: number;
}
```

**Response:** Updated `ProfileResponse`

**Example:**
```typescript
const updateProfile = async (updates: UpdateProfileRequest) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch('http://localhost:8081/api/users/me', {
    method: 'PATCH',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(updates)
  });
  return await response.json();
};
```

---

### Get Followers
**Endpoint:** `GET /users/{userId}/followers`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:**
```typescript
interface PagedProfileResponse {
  content: ProfileResponse[];
  pageable: {...};
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
```

**Example:**
```typescript
const getFollowers = async (userId: string, page = 0) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/users/${userId}/followers?page=${page}&size=20`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

### Get Following
**Endpoint:** `GET /users/{userId}/following`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:** Same as `PagedProfileResponse` above

**Example:**
```typescript
const getFollowing = async (userId: string, page = 0) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/users/${userId}/following?page=${page}&size=20`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

### Get User Languages
**Endpoint:** `GET /users/me/languages`  
**Auth:** Required

**Response:**
```typescript
type UserLanguage[] = Array<{
  code: string;
  name: string;
  flag_emoji: string;
  proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'NATIVE';
  is_learning: boolean;
}>;
```

**Example:**
```typescript
const getUserLanguages = async () => {
  const token = localStorage.getItem('access_token');
  const response = await fetch('http://localhost:8081/api/users/me/languages', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
};
```

---

### Get User Settings
**Endpoint:** `GET /users/me/settings`  
**Auth:** Required
## ✅ Frontend Service Paths (Updated to Match Backend Contract)

| Service | Frontend Path | Matches Contract |
|---------|---------------|------------------|
| Words CRUD | `/api/words` | ✅ |
| Learning Stats | `/api/learn/stats` | ✅ |
| Start Session | `/api/learn/sessions/start` | ✅ |
| Submit Result | `/api/learn/sessions/{id}/submit` | ✅ |
| Complete Session | `/api/learn/sessions/{id}/complete` | ✅ |
| Session History | `/api/learn/sessions` | ✅ |

---

## ❌ Not Yet Implemented (Backend Needed)

### 2. **AI Object Scanner**
Frontend Location: `src/pages/ScannerPage.tsx`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/scanner/analyze` | POST | Upload image for AI analysis |

**Request:**
```typescript
interface CreatePostRequest {
  content: string;
  original_language: string;
  latitude?: number;
  longitude?: number;
  image_url?: string;
}
```

**Response:** `201 Created` (PostResponse)

---

### Get Single Post
**Endpoint:** `GET /posts/{postId}`  
**Auth:** Required

**Response:** PostResponse

---

### Delete Post
**Endpoint:** `DELETE /posts/{postId}`  
**Auth:** Required

**Response:** `204 No Content`

---

### Translate Post
**Endpoint:** `POST /posts/{postId}/translations`  
**Auth:** Required

**Request:**
```typescript
{ target_language: string }
```

**Response:**
```typescript
{
  language_code: string;
  translated_content: string;
}
```

---

### Report Post
**Endpoint:** `POST /posts/{postId}/reports`  
**Auth:** Required

**Request:**
```typescript
{
  reason: 'SPAM' | 'HARASSMENT' | 'HATE_SPEECH' | 'INAPPROPRIATE_CONTENT' | 'OTHER';
  description?: string;
}
```

**Response:** `200 OK`

---

### Get Comments
**Endpoint:** `GET /posts/{postId}/comments`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 10)

**Response:**
```typescript
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
```

---

### Add Comment
**Endpoint:** `POST /posts/{postId}/comments`  
**Auth:** Required

**Request:**
```typescript
{ content: string }
```

**Response:** `201 Created` (CommentResponse)

---

### Delete Comment
**Endpoint:** `DELETE /posts/{postId}/comments/{commentId}`  
**Auth:** Required

**Response:** `204 No Content`

---

### React to Post
**Endpoint:** `POST /posts/{postId}/reactions`  
**Auth:** Required

**Request:**
```typescript
{ reaction: 'LIKE' | 'LOVE' | 'HELPFUL' | 'FUNNY' }
```

**Response:**
```typescript
{
  post_id: string;
  profile_id: string;
  reaction: string;
}
```

---

### Remove Reaction
**Endpoint:** `DELETE /posts/{postId}/reactions`  
**Auth:** Required

**Response:** PostReactionResponse

---

## 5. Social Features

### Follow User
**Endpoint:** `POST /follow`  
**Auth:** Required

**Request:**
```typescript
{ following_id: string }  // UUID of user to follow
```

**Response:** `200 OK`

**Example:**
```typescript
const followUser = async (userId: string) => {
  const token = localStorage.getItem('access_token');
  await fetch('http://localhost:8081/api/follow', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ following_id: userId })
  });
};
```

---

### Unfollow User
**Endpoint:** `DELETE /follow`  
**Auth:** Required

**Request:**
```typescript
{ following_id: string }  // UUID of user to unfollow
```

**Response:** `200 OK`

**Example:**
```typescript
const unfollowUser = async (userId: string) => {
  const token = localStorage.getItem('access_token');
  await fetch('http://localhost:8081/api/follow', {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ following_id: userId })
  });
};
```

---

### Get Followers
**Endpoint:** `GET /follow/followers`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:**
```typescript
interface PagedFollowerResponse {
  content: FollowerDto[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

interface FollowerDto {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
}
```

**Example:**
```typescript
const getFollowers = async (page = 0) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/follow/followers?page=${page}&size=20`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

### Get Following
**Endpoint:** `GET /follow/following`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)

**Response:** Same as `PagedFollowerResponse` above

**Example:**
```typescript
const getFollowing = async (page = 0) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/follow/following?page=${page}&size=20`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

## 6. Friends

> The Friend system is **mutual** — both users must agree. It is separate from the one-way Follow system and controls who sees your location on the map.

### Send Friend Request
**Endpoint:** `POST /users/{id}/friend-request`  
**Auth:** Required

**Response:** `201 Created`
```typescript
interface FriendRequestResponse {
  id: string;              // UUID of the Friend record (used for accept/reject)
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  is_sent_by_me: boolean;
  other_user: ProfileResponse;
  created_at: string;
}
```

**Example:**
```typescript
const sendFriendRequest = async (userId: string) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(`http://localhost:8081/api/users/${userId}/friend-request`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
};
```

---

### Respond to Friend Request
**Endpoint:** `PATCH /users/me/friend-requests/{friendId}?action=accept|reject`  
**Auth:** Required

**Example:**
```typescript
const respondToRequest = async (friendId: string, action: 'accept' | 'reject') => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/users/me/friend-requests/${friendId}?action=${action}`,
    { method: 'PATCH', headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

### Remove Friend
**Endpoint:** `DELETE /users/{id}/friend`  
**Auth:** Required

**Example:**
```typescript
const removeFriend = async (userId: string) => {
  const token = localStorage.getItem('access_token');
  await fetch(`http://localhost:8081/api/users/${userId}/friend`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${token}` }
  });
};
```

---

### Get Friend Status (for Add Friend button state)
**Endpoint:** `GET /users/{id}/friend-status`  
**Auth:** Required

**Response:** `200 OK` with `FriendRequestResponse`, or `204 No Content` if no relationship.

**Example:**
```typescript
const getFriendStatus = async (userId: string) => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(`http://localhost:8081/api/users/${userId}/friend-status`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (response.status === 204) return null; // no relationship
  return await response.json(); // FriendRequestResponse
};
// Use is_sent_by_me + status to determine button label:
// null               → "Add Friend"
// PENDING + sent     → "Pending" (cancel option)
// PENDING + received → "Respond" (accept/reject)
// ACCEPTED           → "Friends" (remove option)
```

---

### Get Friends List
**Endpoint:** `GET /users/{id}/friends`  
**Auth:** Required  
**Query Params:** `page` (default: 0), `size` (default: 20)

**Response:** Paginated `ProfileResponse`

---

### Get Incoming Requests
**Endpoint:** `GET /users/me/friend-requests/incoming`  
**Auth:** Required

**Response:** Paginated `FriendRequestResponse`

---

### Get Outgoing Requests
**Endpoint:** `GET /users/me/friend-requests/outgoing`  
**Auth:** Required

**Response:** Paginated `FriendRequestResponse`

```typescript
import { useState, useEffect } from 'react';

const API_BASE = 'http://localhost:8081/api';

// Auth helper
const getAuthHeaders = () => ({
  'Authorization': `Bearer ${localStorage.getItem('access_token')}`,
  'Content-Type': 'application/json'
});

// Login and store tokens
export const login = async (email: string, password: string) => {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  const data = await response.json();
  localStorage.setItem('access_token', data.access_token);
  localStorage.setItem('refresh_token', data.refresh_token);
  return data;
};

// Feed component
export function Feed() {
  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);

  useEffect(() => {
    loadFeed();
  }, [page]);

  const loadFeed = async () => {
    const response = await fetch(
      `${API_BASE}/posts?page=${page}&size=20`,
      { headers: getAuthHeaders() }
    );
    const data = await response.json();
    setPosts(data.content);
  };

  const reactToPost = async (postId: string, reaction: string) => {
    await fetch(`${API_BASE}/posts/${postId}/reactions`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ reaction })
    });
    loadFeed(); // Refresh
  };

  return (
    <div>
      {posts.map(post => (
        <div key={post.id}>
          <h3>{post.author.display_name}</h3>
          <p>{post.content}</p>
          <button onClick={() => reactToPost(post.id, 'LIKE')}>
            Like ({post.reactions.likes})
          </button>
        </div>
      ))}
    </div>
  );
}
```

---

## Missing Endpoints (Not Yet Implemented)

### Section 2: Users
- None - All user endpoints implemented ✅

### Section 4: Posts
- `PATCH /posts/{post_id}` - Update post
- `GET /posts/{post_id}/translations` - List translations
- `POST /comments/{comment_id}/reports` - Report comment (separate endpoint)

### Section 5-9: Not Implemented
- Learning (Words, Practice, Goals)
- Messaging (Conversations, Messages)
- Meetups (Events, RSVPs)
- Moderation (Admin endpoints)
- Notifications

---

## Testing Checklist

- [x] Authentication flow (register, login, refresh, logout)
- [x] Get current user profile
- [x] User settings (get/update)
- [x] User blocking
- [x] Language management
- [x] Post feed with pagination
- [x] Create/delete posts
- [x] Post reactions
- [x] Comments (get/add/delete)
- [x] Post translations
- [x] Content reporting
- [x] Follow/unfollow users
- [x] All responses use snake_case

---

## Notes

- **Field Naming:** All JSON responses use `snake_case` (e.g., `user_id`, `display_name`, `created_at`)
- **Pagination:** Standard Spring Data pagination with `content`, `totalPages`, `totalElements`
- **Auth:** JWT tokens expire after configured time (default 1 hour)
- **Refresh Tokens:** Automatically rotated on each refresh
- **CORS:** Configured for development (all origins allowed)
