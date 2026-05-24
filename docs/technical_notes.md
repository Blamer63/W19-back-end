# Technical Notes & Decisions

## 1. Architecture Style

The application follows a **classic layered (n-tier) architecture**:

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `controller` | HTTP & WebSocket entrypoints, input validation, DTO return |
| Service | `service` | Business rules, entity ↔ DTO mapping, orchestration |
| Repository | `repository` | Spring Data JPA data access interfaces |
| Entity | `entity` | JPA-mapped database tables |
| DTO | `dto` | API request/response payloads — entities are never exposed directly |
| Config | `config` | Spring beans: security, CORS, WebSocket, S3, OpenAPI |
| Security | `security` | JWT filter, user details loading, token utilities |
| Common | `common` | `BaseEntity` (UUID PK, audit timestamps) |
| Enums | `enums` | Typed constants for all status/type fields |
| Exception | `exception` | `GlobalExceptionHandler` + custom exception classes |

---

## 2. Key Components

### Application Entry Point

`W19BackendApplication` (`@SpringBootApplication`) — boots Spring and auto-scans all components under `com.example.demo`.

### BaseEntity (`com.example.demo.common.BaseEntity`)

A `@MappedSuperclass` inherited by all JPA entities. Provides:

- **Primary Key**: `UUID id` (auto-generated via `GenerationType.UUID` — safe for distributed systems, avoids sequential ID guessing)
- **Auditing**: automatic `createdAt` (`updatable = false`) and `updatedAt` managed by `@EntityListeners(AuditingEntityListener.class)`

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

> **Note:** `Profile` and a few other entities use a `Long` PK instead of UUID for simpler foreign-key joins and URL paths.

---

## 3. API Standards & Best Practices

### JSON Naming Convention

- **Strategy:** `snake_case` in all API responses and request bodies
- **Implementation:** DTOs use `@JsonProperty("field_name")` annotations so JSON output is `snake_case` while Java code remains `camelCase`
- **Reasoning:** aligns with common frontend/JSON standards and prevents inconsistencies across different client implementations

### RESTful Design

Resources are nouns; actions are HTTP methods. Sub-resources are nested:

```
POST   /api/learn/sessions/start          ✓  (resource action)
GET    /api/posts/{id}/translations       ✓  (sub-resource)
POST   /api/posts/{id}/comments           ✓  (sub-resource creation)
POST   /api/practice/start                ✗  (ambiguous verb in path)
```

### Pagination

All list endpoints that may return large result sets accept `Pageable` parameters (`page`, `size`, `sort`) and return `Page<T>` — never unbounded lists.

### CORS Configuration

Permissive CORS (all origins, all methods) is enabled in `SecurityConfig.corsConfigurationSource()` to facilitate frontend development. Tighten allowed origins before production deployment.

---

## 4. Security Architecture (JWT)

The application uses **Spring Security** with a stateless JWT mechanism.

### Components

| Class | Role |
|---|---|
| `SecurityConfig` | Disables CSRF, sets `STATELESS` session, defines public vs protected routes, registers `JwtAuthenticationFilter` |
| `ApplicationConfig` | `BCryptPasswordEncoder`, `DaoAuthenticationProvider`, `AuthenticationManager` beans |
| `JwtAuthenticationFilter` | `OncePerRequestFilter` — validates `Authorization: Bearer <token>`, populates `SecurityContext` |
| `JwtUtils` | `generateToken`, `validateToken`, `getUsernameFromToken`, `getAllClaimsFromToken` |
| `CustomUserDetailsService` | `loadUserByUsername(email)` → `ProfileRepository.findByEmail` → Spring `UserDetails` |

### Token lifecycle

- **Access token**: JWT, 1-hour TTL, signed with `JWT_SECRET`, carried in `Authorization: Bearer` header
- **Refresh token**: opaque string, stored in `refresh_token` DB table, used at `POST /api/auth/refresh` to issue new access tokens
- **Logout**: deletes all refresh tokens for the user via `RefreshTokenService.deleteByProfileId`

### Public routes (no token required)

```
OPTIONS /**
/api/auth/**
/api/languages
/error
/v3/api-docs/**
/swagger-ui/**
```

---

## 5. Database Strategy

- **ORM**: Spring Data JPA / Hibernate with `ddl-auto: update` (auto-migrates schema on startup)
- **Database**: PostgreSQL, dialect `org.hibernate.dialect.PostgreSQLDialect`
- **DTO projection**: entities are mapped to DTOs in the service layer — controllers only ever receive/return DTOs
- **Enums stored as strings**: `@Enumerated(EnumType.STRING)` throughout for human-readable DB values and safe schema evolution

Key enums:

| Enum | Values | Used in |
|---|---|---|
| `AppRole` | `USER`, `ADMIN` | `UserRole` |
| `FriendStatus` | `PENDING`, `ACCEPTED`, `REJECTED` | `Friend` |
| `LocationVisibility` | `PUBLIC`, `FRIENDS_ONLY`, `NOBODY` | `Profile` |
| `MeetupStatus` | `UPCOMING`, `COMPLETED`, `CANCELLED` | `Meetup` |
| `PostStatus` | `PUBLISHED`, `HIDDEN`, ... | `Post` |
| `ProficiencyLevel` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, ... | `UserLanguage` |
| `ReactionType` | various emoji reactions | `PostReaction` |
| `ReportReason` | `SPAM`, `HATE_SPEECH`, ... | `ContentReport` |
| `SourceType` | `POST`, `CHAT`, `MANUAL` | `SavedWord` |

---

## 6. Feature-Specific Implementations

### Friend System

The `Friend` entity models a **mutual** relationship, distinct from the one-directional follow system:

- **`requester`** / **`receiver`**: who sent vs received the request
- **Status lifecycle**: `PENDING → ACCEPTED | REJECTED`. A rejected request deletes the old record, allowing the requester to try again
- **Unique constraint** on `(requester_id, receiver_id)` prevents duplicates
- **Bidirectional queries**: `FriendRepository.findBetween(u1, u2)` and `areFriends(u1, u2)` check both directions, so callers never need to try both orderings
- **Map integration**: `FriendRepository.findAcceptedFriendIds(userId)` builds a friend ID set in `LearnerService` to avoid N+1 queries during privacy filtering

### Location Visibility

Replaced the old `showLocation` boolean with a 3-way `LocationVisibility` enum:

- `PUBLIC` (default) — visible to everyone on the map
- `FRIENDS_ONLY` — only mutual friends (status = `ACCEPTED`) can see this user
- `NOBODY` — completely hidden from all map queries

The filter is applied in `LearnerService.findNearbyLearners()` before results are returned. Exposed via `PATCH /api/users/me/privacy`.

### Follow System

`UserFollow` models a **one-directional** follow (Twitter-style):

- `follower` → the user doing the following
- `following` → the user being followed
- `FollowService` handles self-follow prevention and idempotency
- Follower/following counts are maintained as counter columns on `Profile` (updated on follow/unfollow)

### Saved Words & Spaced Repetition (Learning Module)

The `SavedWord` entity tracks:

- `word`, `translation`, `languageCode`, `context` (example sentence)
- `masteryLevel` (0–100) — updated after each practice answer
- `nextReview` (`Instant`) — controls when a word should be shown again

`PracticeService` implements weighted word selection: words with lower mastery scores and earlier `nextReview` dates are selected more frequently for practice sessions.

Session lifecycle:
1. `POST /api/learn/sessions/start` — creates `PracticeSession`, returns selected words
2. `POST /api/learn/sessions/{id}/submit` — records `PracticeResult`, updates `masteryLevel` and `nextReview`
3. `POST /api/learn/sessions/{id}/complete` — finalises session, returns accuracy + duration summary

### Messaging & Chat

- **Conversation deduplication**: `ConversationRepository.findBetweenUsers(u1, u2)` ensures at most one `Conversation` exists per user pair — starting a conversation is idempotent
- **Participant list mutability**: participants are stored in a `Set<Profile>` backed by `ArrayList` (not `Arrays.asList`, which is fixed-size and would prevent saving new conversations)
- **WebSocket + REST**: WebSocket (`/app/chat.send`) handles real-time delivery; REST endpoints (`POST /api/conversations/{id}/messages`) provide reliable fallback and history retrieval
- **Typing indicators**: `@MessageMapping("/chat.typing")` broadcasts to `/topic/conversation.{id}.typing` without persisting anything to DB

### AWS S3 File Storage

Files are stored in the private `fs-kaiday-customer-test` bucket (region `ap-southeast-2`) and served through CloudFront. `S3Service` manages S3 writes/deletes and URL/key conversion:

- **Upload**: key pattern `<folder>/<UUID>-<sanitizedOriginalFilename>` ensures uniqueness and prevents path-like filenames from influencing keys; returns a CloudFront URL
- **Standalone media**: `FileController` accepts only `audio` and `videos`; image uploads are handled by profile, post, and message endpoints
- **Delete**: called in two cleanup flows:
  - `PostService.deletePost` — deletes the post's image before removing the DB record
  - `UserController.updateProfile` — deletes the old avatar when a new one is set
- **`extractKey(url)`**: strips the bucket URL prefix to recover the S3 key from a stored URL; returns `null` for non-S3 URLs, making cleanup safe to call unconditionally

File size limits enforced by `FileController`:

| Type | Limit |
|---|---|
| `images` | 5 MB |
| `audio` | 20 MB |
| `videos` | 100 MB |

Image uploads are capped at 5 MB by `S3Service.validateImageFile`.

Message image cleanup is handled by `ChatService.deleteMessage`. During the CloudFront migration, `extractKey(url)` supports both old direct S3 URLs and new CloudFront URLs.

### Meetups

- Organiser is automatically added as the first `MeetupAttendee` on creation
- Join/leave checks: capacity (`maxAttendees`) and timing (cannot join past meetups)
- `MeetupRepository` supports geo-distance filtering for the `GET /api/meetups` feed

### Google Places Integration

`PlacesService` proxies the Google Places API via `RestTemplate` (configured in `RestTemplateConfig`). API key is injected from `${GOOGLE_PLACES_KEY}`. Used for meetup location autocomplete and place detail lookup.

---

## 7. Global Error Handling

`GlobalExceptionHandler` (`@ControllerAdvice`) converts all unhandled exceptions into a consistent JSON structure:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Resource not found with id: 99",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 Not Found |
| `MethodArgumentNotValidException` | 400 Bad Request (includes field-level errors) |
| `AccessDeniedException` | 403 Forbidden |
| General `RuntimeException` | 400 Bad Request (message masked where sensitive) |

---

## 8. Configuration Summary

`src/main/resources/application.yml`:

| Property | Value / Source |
|---|---|
| Server port | `8081` |
| DB URL | `jdbc:postgresql://${DB_HOST:localhost}:5432/mydatabase` |
| JPA DDL auto | `update` |
| AWS region | `ap-southeast-2` |
| S3 bucket | `fs-kaiday-customer-test` |
| JWT expiry | `3600000` ms (1 hour) |
| JWT secret | `${JWT_SECRET}` (env var) |
| Google Places key | `${GOOGLE_PLACES_KEY}` (env var) |

AWS credentials are resolved via `DefaultCredentialsProvider` — environment variables, `~/.aws/credentials`, or an attached IAM role all work without code changes.

For local Docker, copy `.env.example` to `.env` and provide `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `AWS_S3_BUCKET_CUSTOMER`, and
`AWS_CLOUDFRONT_DOMAIN`. `AWS_SESSION_TOKEN` is only needed for temporary AWS
credentials. `AWS_S3_MOCK=true` can be used for upload-only smoke tests, but
post-image scanning requires real S3 download access and should use real AWS
credentials.
