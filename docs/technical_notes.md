# Technical Notes & Decisions

## 1. Project Structure
We have adopted a **Layered Architecture** (Technical Layering) for the Spring Boot application.
- `controller`: REST API endpoints.
- `service`: Business logic.
- `repository`: Data access interfaces.
- `entity`: JPA entities.
- `common`: Shared utilities and base classes.
- `dto`: Data Transfer Objects for API requests/responses.

## 2. Key Components

### Application Entry Point
The main application class is **`W19BackendApplication`**.

### BaseEntity (`com.example.demo.common.BaseEntity`)
A mapped superclass for all JPA entities to inherit from. It provides:
- **Primary Key**: `UUID id` (Changed from Long to UUID for distributed safety).
- **Auditing**: Automatic `createdAt` and `updatedAt` management.

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

## 3. API Standards & Best Practices

### JSON Naming Convention
- **Strategy:** `snake_case`
- **Implementation:** All DTOs use `@JsonProperty("field_name")` to ensure JSON responses are strictly `snake_case`, while Java code remains `camelCase`.
- **Reasoning:** aligns with common frontend/JSON standards and ensures consistency across different clients.

### RESTful Design
- **Resources:** URLs represent resources (nouns), not actions.
  - Good: `POST /api/learn/sessions/start` (Resource action for session creation)
  - Bad: `POST /api/practice/start` (Ambiguous)
- **Sub-resources:** proper nesting.
  - `GET /api/posts/{id}/translations` (Get translations for a post)
  - `POST /api/learn/sessions/{id}/submit` (Submit a result to a session)

### CORS Configuration
- **Development:** Permissive CORS (All origins `*`, all methods `*`) enabled in `SecurityConfig` to facilitate frontend development.

## 4. Security Architecture (JWT)
The application uses **Spring Security** with a stateless **JWT** mechanism.
- **`JwtAuthenticationFilter`**: Intercepts requests, validates `Authorization: Bearer <token>`, and sets the `SecurityContext`.
- **Public Routes:** `/api/auth/**`, `/api/languages`, `/error`.
- **Protected Routes:** All other `/api/**` endpoints.

## 5. Database Strategy
- **ORM**: Spring Data JPA.
- **DTO Projection**: Entities are never exposed directly; they are mapped to DTOs in the Service layer.
- **Enums**: Used strictly for state to ensure data integrity. Key enums:
  - `ReactionType`, `ReportReason` — content interaction
  - `FriendStatus` (`PENDING`, `ACCEPTED`, `REJECTED`) — friend request lifecycle
  - `LocationVisibility` (`PUBLIC`, `FRIENDS_ONLY`, `NOBODY`) — map privacy control

## 6. Feature Specific Implementations

### Friend System
The Friend system (`Friend` entity) is a **mutual** relationship, distinct from the one-way Follow system:
- **`requester`**: The user who sent the friend request.
- **`receiver`**: The user who received the request.
- **`status`**: `PENDING` → `ACCEPTED` or `REJECTED`. A rejected user can re-send a request (old record is deleted).
- **Unique constraint** on `(requester_id, receiver_id)` prevents duplicate rows.
- **Bidirectional queries**: `FriendRepository.findBetween()` and `areFriends()` check both directions so callers don't need to care who sent the request.
- **Map integration**: `FriendRepository.findAcceptedFriendIds()` is called in `LearnerService` to efficiently build a friend set for privacy filtering without N+1 queries.

### Location Visibility
Replaces the old `showLocation` boolean with a 3-way `LocationVisibility` enum stored as a string column:
- `PUBLIC` (default) — visible to everyone on the map. Existing users retain this automatically.
- `FRIENDS_ONLY` — only mutual friends (status = `ACCEPTED`) can see this user on the map.
- `NOBODY` — user is completely hidden from all map queries.

The filter is applied in `LearnerService.findNearbyLearners()` before returning results to the frontend. The `PrivacySettings` embeddable and `PrivacySettingsDto` expose this via `PATCH /api/users/me/privacy`.
### Follow System
The Follow system (`UserFollow` entity) uses a join table strategy with explicit relationship management:
- **`following`**: The user being followed (target).
- **Service Layer**: A dedicated `FollowService` handles business logic including self-follow prevention and idempotency.

### Learning Module
The learning module (`api/learn`) is designed around "Sessions":
1. **Start**: Initialize a session with a set of words.
2. **Interact**: Submit answers one by one or in batch.
3. **Complete**: Finalize the session and calculate XP/Score.

### Messaging & Chat
The Chat module provides a REST-based foundation for real-time communication:
- **Conversation Management**: Automatically finds existing conversations between users or creates a new one upon the first message.
- **Mutable Participant Lists**: Fixed a critical JPA issue where `Arrays.asList()` (fixed-size) prevented saving new conversations. Switched to `new ArrayList<>(Arrays.asList(...))` for mutability.
- **REST Fallback**: While WebSockets are the goal, the REST API provides robust endpoints for message history and background state sync.
- **Deduplication**: `ConversationRepository.findBetweenUsers` ensuring only one conversation exists between any two users.

---

## 7. Global Error Handling
A centralized `GlobalExceptionHandler` ensures consistent error responses:
- `ResourceNotFoundException` -> 404 Not Found
- `MethodArgumentNotValidException` -> 400 Bad Request
- General `RuntimeException` -> 400 Bad Request (masked where necessary for security)

