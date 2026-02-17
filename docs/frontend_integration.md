# Frontend Integration Guide - Current Implementation

**Last Updated:** 2026-01-21  
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

**Total Implemented:** 35 endpoints

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

---

## 2. Users & Profiles

### Get Current User
**Endpoint:** `GET /users/me`  
**Auth:** Required

**Response:**
```typescript
interface ProfileResponse {
  id: string;
  username: string;
  email: string;
  display_name: string;
  avatar_url: string | null;
  bio: string | null;
  latitude: number | null;
  longitude: number | null;
  created_at: string; // ISO 8601
  languages: UserLanguage[];
  roles: string[];
  followers_count: number;
  following_count: number;
  posts_count: number;
}

interface UserLanguage {
  code: string;
  name: string;
  flag_emoji: string;
  proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'NATIVE';
  is_learning: boolean;
}
```

**Example:**
```typescript
const getCurrentUser = async () => {
  const token = localStorage.getItem('access_token');
  const response = await fetch('http://localhost:8081/api/users/me', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return await response.json();
};
```

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
  show_activity?: boolean;  // Show learning activity on profile
  show_saved_words?: boolean;  // Show saved words count
}
```

**Response:** Updated `PrivacySettingsDto`

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

**Response:**
```typescript
interface UserSettings {
  notification_prefs: {
    push_enabled: boolean;
    email_enabled: boolean;
    like_notifications: boolean;
    comment_notifications: boolean;
    meetup_notifications: boolean;
  };
  privacy_settings: {
    show_location: boolean;
    allow_messages: 'everyone' | 'following' | 'none';
  };
  theme: 'light' | 'dark' | 'system';
}
```

---

### Update User Settings
**Endpoint:** `PATCH /users/me/settings`  
**Auth:** Required

**Request:** (partial updates supported)
```typescript
await fetch('http://localhost:8081/api/users/me/settings', {
  method: 'PATCH',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    notification_prefs: { email_enabled: false }
  })
});
```

---

### Block User
**Endpoint:** `POST /users/{userId}/block`  
**Auth:** Required

**Response:** `200 OK`

---

### Unblock User
**Endpoint:** `DELETE /users/{userId}/block`  
**Auth:** Required

**Response:** `200 OK`

---

## 3. Languages

### Get All Languages
**Endpoint:** `GET /languages`

**Response:**
```typescript
interface LanguagesResponse {
  languages: Array<{
    code: string;
    name: string;
    native_name: string;
    flag_emoji: string;
  }>;
}
```

**Example:**
```typescript
const getLanguages = async () => {
  const response = await fetch('http://localhost:8081/api/languages');
  return await response.json();
};
```

---

### Update User Languages
**Endpoint:** `PUT /users/me/languages`  
**Auth:** Required

**Request:** (full replacement)
```typescript
await fetch('http://localhost:8081/api/users/me/languages', {
  method: 'PUT',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify([
    { code: 'en', proficiency: 'NATIVE', is_learning: false },
    { code: 'es', proficiency: 'BEGINNER', is_learning: true }
  ])
});
```

---

## 4. Posts & Content

### Get Feed
**Endpoint:** `GET /posts`  
**Auth:** Required

**Query Parameters:**
- `page` (default: 0)
- `size` (default: 20)
- `language` (optional: filter by language code or "all")
- `latitude` (optional: for distance calculation)
- `longitude` (optional: for distance calculation)

**Response:**
```typescript
interface PostResponse {
  id: string;
  content: string;
  original_language: string;
  image_url: string | null;
  latitude: number | null;
  longitude: number | null;
  location: string; // "Nearby" or "Unknown"
  distance: string; // "5.2 km" or "Unknown"
  created_at: string;
  author: {
    id: string;
    username: string;
    display_name: string;
    avatar_url: string | null;
    language: string;
    flag_emoji: string;
  };
  reactions: {
    likes: number;
    comments: number;
  };
  user_reaction: 'LIKE' | 'LOVE' | 'HELPFUL' | 'FUNNY' | null;
}
```

**Example:**
```typescript
const getFeed = async (page = 0, language = 'all') => {
  const token = localStorage.getItem('access_token');
  const response = await fetch(
    `http://localhost:8081/api/posts?page=${page}&size=20&language=${language}`,
    { headers: { 'Authorization': `Bearer ${token}` } }
  );
  return await response.json();
};
```

---

### Create Post
**Endpoint:** `POST /posts`  
**Auth:** Required

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

## Complete React Example

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
