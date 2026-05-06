### W19 Backend – Architecture & Code Map

This document explains how the backend is structured: the **main packages**, **core classes**, key **interfaces/repositories**, important **methods**, and how everything connects from an incoming HTTP request down to the database and back.

It’s written as a *study guide* so you can quickly understand and navigate the codebase.

---

### 1. Technologies & High-level Architecture

- **Runtime & framework**
  - **Java 21**, **Spring Boot 3.4.1**
  - Spring MVC (REST), Spring Security, Spring Data JPA, WebSocket (STOMP)
- **Persistence**
  - **PostgreSQL** (main DB), JPA/Hibernate entities and repositories
- **Auth & security**
  - Stateless **JWT** authentication (access + refresh tokens)
- **API documentation**
  - OpenAPI / Swagger (`springdoc-openapi`)
- **External integrations**
  - Google Places API (via `RestTemplate`)
  - WebSockets for chat & typing indicators
  - **AWS S3** (object storage for images, audio, and video uploads via AWS SDK v2)

**Architecture style**: classic layered Spring Boot app

- **Controller layer** – HTTP & WebSocket entrypoints (`controller` package)
- **Service layer** – business logic (`service` package)
- **Data layer** – JPA entities & repositories (`entity`, `repository` packages)
- **Cross-cutting** – config, security, exception handling, DTOs, enums

---

### 2. Package Structure & Main Classes

All Java code lives under `com.example.demo`:

- `W19BackendApplication` – main application entrypoint (`@SpringBootApplication`).
- `config` – Spring configuration:
  - `SecurityConfig`, `ApplicationConfig`, `WebSocketConfig`, `OpenApiConfig`, `RestTemplateConfig`, `S3Config`.
- `security` – authentication and JWT:
  - `JwtAuthenticationFilter`, `JwtUtils`, `CustomUserDetailsService`.
- `controller` – all REST and WebSocket controllers.
- `service` – business logic per feature/domain.
- `entity` – JPA entities (users, posts, practice, meetups, chat, etc.).
- `repository` – Spring Data JPA interfaces.
- `dto`, `enums`, `exception`, `common` – payloads, enums, errors, common base classes.

**Entrypoint class**

- `W19BackendApplication`
  - `main(String[] args)` – boots Spring and auto-scans all components in `com.example.demo`.

#### 2.1 Full folder structure and files

Below is the complete project layout (source and config; `target/` and `.git/` are omitted). Paths use `/` for readability.

```
W19-back-end/
├── .env
├── .env.example
├── .gitattributes
├── .gitignore
├── BACKEND_OVERVIEW.md
├── compose.yaml
├── Dockerfile
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
├── seed.sql
│
├── .agent/
│   └── workflows/
│       └── setup-ngrok.md
│
├── .idea/                    # IDE (optional)
│   ├── .gitignore
│   ├── aws.xml
│   ├── compiler.xml
│   ├── encodings.xml
│   ├── jarRepositories.xml
│   ├── misc.xml
│   ├── vcs.xml
│   └── workspace.xml
│
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
│
├── docs/
│   ├── api_contract.md
│   ├── api_reference.md
│   ├── chat_integration_guide.md
│   ├── frontend_integration.md
│   ├── SEEDING.md
│   └── technical_notes.md
│
├── src/
│   ├── main/
│   │   ├── java/com/example/demo/
│   │   │   ├── W19BackendApplication.java
│   │   │   │
│   │   │   ├── common/
│   │   │   │   └── BaseEntity.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── ApplicationConfig.java
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── RestTemplateConfig.java
│   │   │   │   ├── S3Config.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── WebSocketConfig.java
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── ChatController.java
│   │   │   │   ├── CommentController.java
│   │   │   │   ├── FileController.java
│   │   │   │   ├── FollowController.java
│   │   │   │   ├── FriendController.java
│   │   │   │   ├── LanguageController.java
│   │   │   │   ├── LearnerController.java
│   │   │   │   ├── MeetupController.java
│   │   │   │   ├── PlacesController.java
│   │   │   │   ├── PostController.java
│   │   │   │   ├── PracticeController.java
│   │   │   │   ├── ReactionController.java
│   │   │   │   ├── SavedWordController.java
│   │   │   │   ├── StatsController.java
│   │   │   │   ├── UserController.java
│   │   │   │   └── UserLanguageController.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── AuthResponse.java
│   │   │   │   ├── ChatRequest.java
│   │   │   │   ├── CommentResponse.java
│   │   │   │   ├── CompleteSessionResponse.java
│   │   │   │   ├── ConversationResponse.java
│   │   │   │   ├── CreateCommentRequest.java
│   │   │   │   ├── CreateMeetupRequest.java
│   │   │   │   ├── CreatePostRequest.java
│   │   │   │   ├── CreateWordRequest.java
│   │   │   │   ├── FollowerDto.java
│   │   │   │   ├── FriendRequestResponse.java
│   │   │   │   ├── LanguageInfo.java
│   │   │   │   ├── LanguageStatDTO.java
│   │   │   │   ├── LearnerLanguageDto.java
│   │   │   │   ├── LearnerResponse.java
│   │   │   │   ├── LearningLanguageDTO.java
│   │   │   │   ├── LearningStatsResponse.java
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── MasteryDistributionDTO.java
│   │   │   │   ├── MeetupAttendeeResponse.java
│   │   │   │   ├── MeetupResponse.java
│   │   │   │   ├── MessageResponse.java
│   │   │   │   ├── PostReactionRequest.java
│   │   │   │   ├── PostReactionResponse.java
│   │   │   │   ├── PostResponse.java
│   │   │   │   ├── PostTranslationResponse.java
│   │   │   │   ├── PracticeWordDTO.java
│   │   │   │   ├── PrivacySettingsDto.java
│   │   │   │   ├── ProfileResponse.java
│   │   │   │   ├── PublicUserProfileDto.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── ReportRequest.java
│   │   │   │   ├── SavedWordResponse.java
│   │   │   │   ├── StartSessionRequest.java
│   │   │   │   ├── StartSessionResponse.java
│   │   │   │   ├── SubmitResultRequest.java
│   │   │   │   ├── SubmitResultResponse.java
│   │   │   │   ├── TokenRefreshRequest.java
│   │   │   │   ├── UpdateMeetupRequest.java
│   │   │   │   ├── UpdateProfileRequest.java
│   │   │   │   ├── UpdateWordRequest.java
│   │   │   │   ├── UserLanguageDTO.java
│   │   │   │   ├── UserSettingsDTO.java
│   │   │   │   └── WordResultDTO.java
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── ContentReport.java
│   │   │   │   ├── Conversation.java
│   │   │   │   ├── Friend.java
│   │   │   │   ├── Language.java
│   │   │   │   ├── LearningLanguage.java
│   │   │   │   ├── Meetup.java
│   │   │   │   ├── MeetupAttendee.java
│   │   │   │   ├── Message.java
│   │   │   │   ├── NotificationPrefs.java
│   │   │   │   ├── Post.java
│   │   │   │   ├── PostComment.java
│   │   │   │   ├── PostReaction.java
│   │   │   │   ├── PostTranslation.java
│   │   │   │   ├── PracticeResult.java
│   │   │   │   ├── PracticeSession.java
│   │   │   │   ├── PrivacySettings.java
│   │   │   │   ├── Profile.java
│   │   │   │   ├── RefreshToken.java
│   │   │   │   ├── SavedWord.java
│   │   │   │   ├── UserBlock.java
│   │   │   │   ├── UserFollow.java
│   │   │   │   ├── UserLanguage.java
│   │   │   │   ├── UserRole.java
│   │   │   │   └── UserSettings.java
│   │   │   │
│   │   │   ├── enums/
│   │   │   │   ├── AppRole.java
│   │   │   │   ├── FriendStatus.java
│   │   │   │   ├── LocationVisibility.java
│   │   │   │   ├── MeetupStatus.java
│   │   │   │   ├── PostStatus.java
│   │   │   │   ├── ProficiencyLevel.java
│   │   │   │   ├── ReactionType.java
│   │   │   │   ├── ReportReason.java
│   │   │   │   ├── SanctionType.java
│   │   │   │   └── SourceType.java
│   │   │   │
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   └── ResourceNotFoundException.java
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── ContentReportRepository.java
│   │   │   │   ├── ConversationRepository.java
│   │   │   │   ├── FollowRepository.java
│   │   │   │   ├── FriendRepository.java
│   │   │   │   ├── LanguageRepository.java
│   │   │   │   ├── MeetupAttendeeRepository.java
│   │   │   │   ├── MeetupRepository.java
│   │   │   │   ├── MessageRepository.java
│   │   │   │   ├── PostCommentRepository.java
│   │   │   │   ├── PostReactionRepository.java
│   │   │   │   ├── PostRepository.java
│   │   │   │   ├── PostTranslationRepository.java
│   │   │   │   ├── PracticeResultRepository.java
│   │   │   │   ├── PracticeSessionRepository.java
│   │   │   │   ├── ProfileRepository.java
│   │   │   │   ├── RefreshTokenRepository.java
│   │   │   │   ├── SavedWordRepository.java
│   │   │   │   ├── UserBlockRepository.java
│   │   │   │   ├── UserLanguageRepository.java
│   │   │   │   └── UserSettingsRepository.java
│   │   │   │
│   │   │   ├── security/
│   │   │   │   ├── CustomUserDetailsService.java
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── JwtUtils.java
│   │   │   │
│   │   │   └── service/
│   │   │       ├── AuthService.java
│   │   │       ├── ChatService.java
│   │   │       ├── CommentService.java
│   │   │       ├── FollowService.java
│   │   │       ├── FriendService.java
│   │   │       ├── LearnerService.java
│   │   │       ├── MeetupService.java
│   │   │       ├── PlacesService.java
│   │   │       ├── PostService.java
│   │   │       ├── PracticeService.java
│   │   │       ├── ProfileService.java
│   │   │       ├── ReactionService.java
│   │   │       ├── RefreshTokenService.java
│   │   │       ├── ReportService.java
│   │   │       ├── S3Service.java
│   │   │       ├── SavedWordService.java
│   │   │       ├── StatsService.java
│   │   │       ├── TranslationService.java
│   │   │       └── UserService.java
│   │   │
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│       ├── java/com/example/demo/
│       │   ├── W19BackendApplicationTests.java
│       │   ├── controller/
│       │   │   ├── AuthControllerLoginTest.java
│       │   │   ├── AuthControllerTest.java
│       │   │   ├── ChatControllerTest.java
│       │   │   ├── CommentControllerTest.java
│       │   │   ├── FollowControllerTest.java
│       │   │   ├── LanguageControllerTest.java
│       │   │   ├── LearnerControllerTest.java
│       │   │   ├── MeetupControllerTest.java
│       │   │   ├── PlacesControllerTest.java
│       │   │   ├── PostControllerTest.java
│       │   │   ├── PracticeControllerTest.java
│       │   │   ├── ReactionControllerTest.java
│       │   │   ├── SavedWordControllerTest.java
│       │   │   ├── StatsControllerTest.java
│       │   │   └── UserControllerTest.java
│       │   └── service/
│       │       ├── ChatServiceTest.java
│       │       ├── PracticeServiceTest.java
│       │       └── SavedWordServiceTest.java
│       │
│       └── resources/
│           └── application.yml
```

**Notes:**

- **Root:** Build and run: `pom.xml`, `mvnw`/`mvnw.cmd`, `compose.yaml`, `Dockerfile`. Env: `.env` / `.env.example`. Data: `seed.sql`.
- **`src/main/java/com/example/demo/`:** All application code (controllers, services, entities, repositories, config, security, dto, enums, exception, common).
- **`src/main/resources/`:** Main config file `application.yml`.
- **`src/test/`:** Unit/integration tests mirroring `controller` and `service` packages; test config in `src/test/resources/application.yml`.
- **`docs/`:** API contracts, seeding, integration guides.
- **`target/`** (not listed): Maven build output (compiled classes, JARs, test reports); generated by `mvn compile` / `mvn test` / `mvn package`.

---

### 3. Request & Authentication Flow (How Things Connect)

**HTTP (REST) request path**

1. **Client** calls some endpoint, e.g. `GET /api/posts`.
2. **Servlet filter chain** runs:
   - `JwtAuthenticationFilter`:
     - Extracts `Authorization: Bearer <token>` header.
     - Uses `JwtUtils` to parse and validate JWT.
     - Loads user via `CustomUserDetailsService` → `ProfileRepository`.
     - Creates `UsernamePasswordAuthenticationToken` and stores it in Spring’s `SecurityContext`.
3. **Spring MVC** routes the request to a matching `@RestController` method.
4. Controller method:
   - Reads request params/body.
   - Accesses current user via `Authentication` or `SecurityContextHolder`.
   - Delegates to a **service**.
5. **Service**:
   - Enforces business rules.
   - Calls one or more **repositories** to load/persist **entities**.
   - Maps entities to DTOs (`*Response`, `*Dto`).
6. **Controller** returns DTOs inside `ResponseEntity`, which Spring serializes to JSON.
7. On errors, `GlobalExceptionHandler` (`@ControllerAdvice`) converts exceptions to HTTP responses.

**WebSocket (STOMP) flow (chat)**

1. Client connects to `/ws` (configured in `WebSocketConfig`) using SockJS/STOMP.
2. Client sends messages to `/app/...` destinations.
3. `ChatController` methods annotated with `@MessageMapping` receive messages.
4. `ChatService` handles conversation/message persistence via repositories.
5. `ChatController` broadcasts results to `/topic/...` or `/user/...` destinations using `SimpMessagingTemplate`.

---

### 4. Configuration & Security Layer

#### 4.1 `SecurityConfig`

- **Responsibility**: define HTTP security rules and attach JWT filter.
- **Key methods & connections**:
  - `SecurityFilterChain securityFilterChain(HttpSecurity http)`
    - Disables CSRF (`csrf(AbstractHttpConfigurer::disable)`).
    - Configures CORS via `corsConfigurationSource()`.
    - Sets session policy to `STATELESS`.
    - `authorizeHttpRequests`:
      - Permits: `OPTIONS /**`, `/api/auth/**`, `/error`, Swagger/OpenAPI paths.
      - Requires authentication for any other request.
    - Registers `authenticationProvider` from `ApplicationConfig`.
    - Adds `jwtAuthFilter` before `UsernamePasswordAuthenticationFilter`.
  - `CorsConfigurationSource corsConfigurationSource()`
    - Defines allowed origins/methods/headers.

Connections:
- Uses `JwtAuthenticationFilter` (from `security` package).
- Uses `AuthenticationProvider` defined in `ApplicationConfig`.

#### 4.2 `ApplicationConfig`

- **Responsibility**: beans for auth and password encoding.
- **Key methods**:
  - `UserDetailsService userDetailsService()` → returns `CustomUserDetailsService`.
  - `AuthenticationProvider authenticationProvider()` → `DaoAuthenticationProvider` wired with `userDetailsService` and `passwordEncoder`.
  - `AuthenticationManager authenticationManager(AuthenticationConfiguration)` → used by `AuthService`.
  - `PasswordEncoder passwordEncoder()` → `BCryptPasswordEncoder`.

Connections:
- Used by `SecurityConfig` and `AuthService`.

#### 4.3 `CustomUserDetailsService`

- Implements `UserDetailsService`.
- **Key method**:
  - `loadUserByUsername(String email)`:
    - Uses `ProfileRepository.findByEmail(email)` to fetch a `Profile`.
    - Wraps it into Spring Security `UserDetails` with roles from `Profile.roles`.

#### 4.4 `JwtAuthenticationFilter`

- Extends `OncePerRequestFilter`.
- **Key method**:
  - `doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)`:
    - Gets `Authorization` header.
    - If it starts with `"Bearer "`, extracts token and asks `JwtUtils` for username.
    - Loads user with `UserDetailsService`.
    - Validates token with `JwtUtils`.
    - If valid, sets authenticated `UserDetails` into `SecurityContext`.
    - Always continues `filterChain.doFilter`.

#### 4.5 `JwtUtils`

- **Key methods** (names may vary slightly):
  - `String generateToken(UserDetails userDetails)` – generate access token.
  - `String getUsernameFromToken(String token)` – extract subject (email).
  - `boolean validateToken(String token, UserDetails userDetails)` – check signature and expiry.
  - `Claims getAllClaimsFromToken(String token)` – parse claims.

Connections:
- Used by `AuthService` (to issue tokens) and `JwtAuthenticationFilter` (to validate).

#### 4.6 Other config

- `WebSocketConfig`
  - `configureMessageBroker(MessageBrokerRegistry)` – enables `/topic`, `/queue`, `/user`.
  - `registerStompEndpoints(StompEndpointRegistry)` – registers `/ws` endpoint (SockJS).
- `RestTemplateConfig`
  - `RestTemplate restTemplate()` – used by `PlacesService`.
- `OpenApiConfig`
  - Declares OpenAPI metadata and security scheme (JWT bearer).
- `S3Config`
  - `S3Client s3Client()` – builds an AWS SDK v2 `S3Client` using `DefaultCredentialsProvider` and `aws.region` from `application.yml`.
  - Used exclusively by `S3Service`.

---

### 5. Controllers (API Endpoints)

This section lists the major controllers, their base paths, important methods, and which services they call.

#### 5.1 `AuthController` – `/api/auth`

**Dependencies**: `AuthService`

- `POST /register` → `register(RegisterRequest request)`
  - Calls `AuthService.register(request)` → returns `AuthResponse` (JWT + refresh token).
- `POST /login` → `login(LoginRequest request)`
  - Calls `AuthService.login(request)` → `AuthResponse`.
- `POST /refresh` → `refreshToken(RefreshTokenRequest request)`
  - Calls `AuthService.refreshToken(request)` → new tokens.
- `POST /logout` → `logout(Authentication auth)`
  - Calls `AuthService.logout(auth)` to remove refresh tokens.

#### 5.2 `UserController` – `/api/users`

**Dependencies**: `UserService`, `ProfileService`, `ProfileRepository`, `PostService`, `S3Service`

- `GET /me` → `getCurrentUser(Authentication auth)`
  - Loads `Profile` via `ProfileRepository.findByEmail`, maps to `ProfileResponse` using `ProfileService.mapToResponse`.
- `PATCH /me` → `updateProfile(UpdateProfileRequest request, Authentication auth)`
  - Modifies fields on current user’s `Profile`. If `avatarUrl` changes and the old avatar is an S3 object, calls `S3Service.deleteFile` to remove it first. Saves via `ProfileRepository.save`, maps to `ProfileResponse`.
- `GET /{userId}` → `getPublicProfile(Long userId, Authentication auth)`
  - Calls `UserService.getPublicProfile(userId, currentUserIdOrNull)`.
- `GET /{userId}/posts` → `getUserPosts(...)`
  - Delegates to `PostService.getPostsByUser(userId, currentUserEmail, pageable)`.
- `GET /me/settings` → `getUserSettings(Authentication auth)`
  - Calls `UserService.getUserSettings(profileId)`.
- `PATCH /me/settings` → `updateUserSettings(UserSettingsRequest request, Authentication auth)`
  - Calls `UserService.updateUserSettings(profileId, request)`.
- `PATCH /me/privacy` → `updatePrivacySettings(PrivacySettingsRequest request, Authentication auth)`
  - Calls `UserService.updatePrivacySettings(profileId, request)`.
- `POST /{userId}/block` / `DELETE /{userId}/block`
  - Calls `UserService.blockUser` / `UserService.unblockUser`.

#### 5.3 `FollowController` – `/api/users`

**Dependency**: `FollowService`

- `POST /{id}/follow` → `followUser(Long id, Authentication auth)`
  - Calls `FollowService.followUser(currentUserId, id)`.
- `DELETE /{id}/follow` → `unfollowUser(Long id, Authentication auth)`
  - Calls `FollowService.unfollowUser(currentUserId, id)`.
- `GET /{id}/followers` / `GET /{id}/following`
  - `FollowService.getFollowers(id, pageable)` / `getFollowing(id, pageable)`.

#### 5.4 `FriendController` – `/api/users`

**Dependency**: `FriendService`

- `POST /{id}/friend-request` → `sendFriendRequest(Long id, Authentication auth)`
  - Calls `FriendService.sendRequest(currentUserId, id)`.
- `PATCH /me/friend-requests/{friendId}` with `action=accept|reject`
  - Calls `FriendService.respondToRequest(currentUserId, friendId, action)`.
- `DELETE /{id}/friend` → `removeFriend(Long id, Authentication auth)`
  - Calls `FriendService.removeFriend(currentUserId, id)`.
- `GET /{id}/friends` → `getFriends(Long id, Pageable pageable)`
  - Calls `FriendService.getFriends(id, pageable)`.
- `GET /me/friend-requests/incoming` / `/outgoing`
  - `FriendService.getIncomingRequests(currentUserId)` / `getOutgoingRequests(currentUserId)`.
- `GET /{id}/friend-status` → `getFriendStatus(Long id, Authentication auth)`
  - Calls `FriendService.getFriendStatus(currentUserId, id)`.

#### 5.5 `PostController` – `/api/posts`

**Dependencies**: `PostService`, `TranslationService`, `ReportService`, `ProfileRepository`

- `GET /` → `getFeed(String language, Double lat, Double lon, Pageable pageable, Authentication auth)`
  - Calls `PostService.getFeed(language, pageable, lat, lon, currentUserEmail)`.
- `POST /` → `createPost(CreatePostRequest request, Authentication auth)`
  - Calls `PostService.createPost(request, currentUserEmail)`.
- `GET /{postId}` → `getPost(Long postId, Authentication auth)`
  - Calls `PostService.getPost(postId, currentUserEmailOrNull)`.
- `DELETE /{postId}` → `deletePost(Long postId, Authentication auth)`
  - Calls `PostService.deletePost(postId, currentUserEmail)` (checks author).
- `GET /{postId}/translations` → `getTranslations(Long postId, String targetLanguage)`
  - Calls `TranslationService.getTranslation(postId, targetLanguage)`.
- `POST /{postId}/reports` → `reportPost(Long postId, ReportRequest request, Authentication auth)`
  - Loads current `Profile`, sets post info on `request`, calls `ReportService.createReport`.

#### 5.6 `ReactionController` – `/api/posts/{postId}/reactions`

**Dependency**: `ReactionService`

- `POST /` → `reactToPost(Long postId, ReactionRequest request, Authentication auth)`
  - Calls `ReactionService.reactToPost(postId, request, currentUserEmail)`.
- `DELETE /` → `removeReaction(Long postId, Authentication auth)`
  - Calls `ReactionService.removeReaction(postId, currentUserEmail)`.

#### 5.7 `CommentController` – `/api/posts/{postId}/comments`

**Dependency**: `CommentService`

- `GET /` → `getComments(Long postId, Pageable pageable)`
  - Calls `CommentService.getComments(postId, pageable)`.
- `POST /` → `addComment(Long postId, CreateCommentRequest request, Authentication auth)`
  - Calls `CommentService.addComment(postId, request, currentUserEmail)`.
- `DELETE /{commentId}` → `deleteComment(Long postId, Long commentId, Authentication auth)`
  - Calls `CommentService.deleteComment(postId, commentId, currentUserEmail)`.

#### 5.8 `SavedWordController` – `/api/words`

**Dependency**: `SavedWordService`

- `GET /` → `getUserWords(String language, String sort, Pageable pageable, Authentication auth)`
  - Calls `SavedWordService.getUserWords(currentUserEmail, language, sort, pageable)`.
- `GET /{wordId}` → `getWord(UUID wordId, Authentication auth)`
  - Calls `SavedWordService.getWord(currentUserEmail, wordId)`.
- `POST /` → `saveWord(SaveWordRequest request, Authentication auth)`
  - Calls `SavedWordService.saveWord(currentUserEmail, request)`.
- `PATCH /{wordId}` → `updateWord(UUID wordId, UpdateWordRequest request, Authentication auth)`
  - Calls `SavedWordService.updateWord(currentUserEmail, wordId, request)`.
- `DELETE /{wordId}` → `deleteWord(UUID wordId, Authentication auth)`
  - Calls `SavedWordService.deleteWord(currentUserEmail, wordId)`.

#### 5.9 `PracticeController` – `/api/learn`

**Dependency**: `PracticeService`

- `POST /sessions/start` → `startSession(PracticeStartRequest request, Authentication auth)`
  - Calls `PracticeService.startSession(currentUserEmail, request)`.
- `POST /sessions/{sessionId}/submit` → `submitResult(UUID sessionId, PracticeSubmitRequest request, Authentication auth)`
  - Calls `PracticeService.submitResult(currentUserEmail, sessionId, request)`.
- `POST /sessions/{sessionId}/complete` → `completeSession(UUID sessionId, Authentication auth)`
  - Calls `PracticeService.completeSession(currentUserEmail, sessionId)`.
- `GET /sessions` → `getPracticeHistory(Pageable pageable, Authentication auth)`
  - Calls `PracticeService.getPracticeHistory(currentUserEmail, pageable)`.

#### 5.10 `StatsController` – `/api/learn`

**Dependency**: `StatsService`

- `GET /stats` → `getLearningStats(Authentication auth)`
  - Calls `StatsService.getLearningStats(currentUserEmail)`.

#### 5.11 `LanguageController` – `/api/languages`

**Dependency**: `LanguageRepository`

- `GET /` → `getAllLanguages()`
  - Calls `LanguageRepository.findAll()` and wraps in a response object.

#### 5.12 `UserLanguageController` – `/api/users`

**Dependencies**: `ProfileRepository`, `LanguageRepository`, `UserLanguageRepository`

- `GET /me/languages` → `getMyLanguages(Authentication auth)`
  - Loads `Profile` and returns its `languages` collection.
- `PUT /me/languages` → `updateMyLanguages(List<UserLanguageRequest> requests, Authentication auth)`
  - Deletes existing `UserLanguage` rows via `UserLanguageRepository.deleteByProfileId`.
  - For each request:
    - Loads `Language` by code via `LanguageRepository`.
    - Creates and saves new `UserLanguage`.

#### 5.13 `LearnerController` – `/api/learners`

**Dependency**: `LearnerService`

- `GET /nearby` → `findNearbyLearners(...)`
  - Validates coordinates and radius.
  - Calls `LearnerService.findNearbyLearners(currentUserEmail, filters)` for geospatial search.

#### 5.14 `PlacesController` – `/api/places`

**Dependency**: `PlacesService`

- `GET /autocomplete` → `autocomplete(String input)`
  - Calls `PlacesService.autocomplete(input)`.
- `GET /{placeId}` → `getPlaceDetails(String placeId)`
  - Calls `PlacesService.getPlaceDetails(placeId)`.

#### 5.15 `MeetupController` – `/api/meetups`

**Dependencies**: `MeetupService`, `ProfileRepository`

- `GET /` → `listMeetups(...)`
  - Calls `MeetupService.listMeetups(currentUserId, filters...)`.
- `POST /` → `createMeetup(CreateMeetupRequest request, Authentication auth)`
  - Calls `MeetupService.createMeetup(currentUserId, request)`.
- `GET /{id}` → `getMeetup(Long id, Authentication auth)`
  - Calls `MeetupService.getMeetupById(currentUserId, id)`.
- `PUT /{id}` → `updateMeetup(Long id, UpdateMeetupRequest request, Authentication auth)`
  - Calls `MeetupService.updateMeetup(currentUserId, id, request)`.
- `DELETE /{id}` → `deleteMeetup(Long id, Authentication auth)`
  - Calls `MeetupService.deleteMeetup(currentUserId, id)`.
- `POST /{id}/join` / `POST /{id}/leave`
  - Join/leave via `MeetupService.joinMeetup` / `leaveMeetup`.
- `GET /{id}/attendees` → `getAttendees(Long id)`
  - Calls `MeetupService.getAttendees(id)`.

#### 5.16 `FileController` – `/api/files`

**Dependency**: `S3Service`

- `POST /upload?type=<audio|videos>` → `uploadFile(MultipartFile file, String type)`
  - Validates `type`, file emptiness, MIME type, and file size (20 MB / 100 MB limits for audio / videos respectively).
  - Delegates to `S3Service.uploadFile(file, type)`.
  - Returns `{ "url": "<cloudfront-url>" }`.
- `DELETE /delete?key=<s3-key>` → `deleteFile(String key)`
  - Rejects entity-owned image keys and delegates standalone audio/video keys to `S3Service.deleteFile(key)`.
  - Returns `{ "message": "File deleted successfully" }`.

#### 5.17 `ChatController` – `/api/conversations` & `/app/*` (WebSocket)

**Dependencies**: `ChatService`, `SimpMessagingTemplate`

**REST**

- `GET /` → `getUserConversations(Pageable pageable, Authentication auth)`
  - Calls `ChatService.getUserConversations(currentUserEmail, pageable)`.
- `POST /` → `startConversation(CreateConversationRequest request, Authentication auth)`
  - Calls `ChatService.sendMessage(currentUserEmail, request)` (creates conversation if needed).
- `GET /{id}/messages` → `getMessages(Long id, Pageable pageable, Authentication auth)`
  - Calls `ChatService.getConversationMessages(currentUserEmail, id, pageable)`.
- `POST /{id}/messages` → `sendMessage(Long id, CreateMessageRequest request, Authentication auth)`
  - Calls `ChatService.sendMessageToConversation(currentUserEmail, id, request)` and broadcasts over WebSocket.
- `POST /{id}/read` → `markAsRead(Long id, Authentication auth)`
  - Calls `ChatService.markAsRead(currentUserEmail, id)`.

**WebSocket**

- `@MessageMapping("/chat.send")` → `handleChatSend(ChatMessageDto message, Authentication auth)`
  - Calls `ChatService.sendMessage(...)`.
  - Broadcasts to `/topic/conversation.{conversationId}`.
- `@MessageMapping("/chat.typing")` → `handleTyping(TypingNotificationDto dto, Authentication auth)`
  - Broadcasts typing indicators to `/topic/conversation.{conversationId}.typing`.

#### 5.18 `GlobalExceptionHandler`

- `@ControllerAdvice` with `@ExceptionHandler` methods for:
  - Validation errors.
  - Entity not found.
  - Access denied.
  - Generic errors.
- Converts them into unified API error responses.

---

### 6. Services (Business Logic Layer)

This section describes the main services, their dependencies, and their high-level methods. Method names may be simplified for readability but correspond closely to the actual implementation.

#### 6.1 `AuthService`

**Depends on**: `AuthenticationManager`, `ProfileRepository`, `PasswordEncoder`, `JwtUtils`, `RefreshTokenService`

- `AuthResponse register(RegisterRequest request)`
  - Validates unique email/username.
  - Creates new `Profile` with encoded password, default role.
  - Saves profile and creates refresh token.
  - Returns `AuthResponse` with access + refresh token.
- `AuthResponse login(LoginRequest request)`
  - Uses `AuthenticationManager` to authenticate credentials.
  - Loads `Profile`, generates new access and refresh tokens.
- `AuthResponse refreshToken(RefreshTokenRequest request)`
  - Validates supplied refresh token via `RefreshTokenService`.
  - Issues new access token.
- `void logout(Authentication auth)`
  - Deletes stored refresh tokens for the current user.

#### 6.2 `UserService`

**Depends on**: `ProfileRepository`, `UserSettingsRepository`, `UserBlockRepository`, `PostRepository`, `FollowRepository`

- `PublicUserProfileDto getPublicProfile(Long userId, Long currentUserId)`
  - Loads `Profile`, counts followers/following posts using repositories.
  - Incorporates privacy and block status.
- `UserSettings getUserSettings(Long profileId)`
  - Loads or creates default `UserSettings`.
- `UserSettings updateUserSettings(Long profileId, UserSettingsRequest request)`
  - Updates notification and privacy settings.
- `void updatePrivacySettings(Long profileId, PrivacySettingsRequest request)`
  - Sets privacy flags on `Profile`.
- `void blockUser(Long currentUserId, Long targetId)` / `unblockUser(...)`
  - Uses `UserBlockRepository` to manage blocks.

#### 6.3 `ProfileService`

**Depends on**: none (mainly mapping)

- `ProfileResponse mapToResponse(Profile profile, Long currentUserId)`
  - Builds a rich DTO including:
    - Basic profile info.
    - Languages with names and flags.
    - Counts (friends, followers, following, posts).
    - Relationship status (friend/follow/block).

#### 6.4 `PostService`

**Depends on**: `PostRepository`, `ProfileRepository`, `PostReactionRepository`, `PostCommentRepository`, `S3Service`

- `Page<PostResponse> getFeed(String language, Pageable pageable, Double lat, Double lon, String currentUserEmail)`
  - Fetches posts (optionally by language).
  - Computes:
    - Author profile info and languages.
    - Reaction counts, current user’s reaction.
    - Comment counts.
    - Distance from user if coordinates are given.
- `PostResponse createPost(CreatePostRequest request, String currentUserEmail)`
  - Creates `Post` for current user and returns mapped `PostResponse`.
- `PostResponse getPost(Long postId, String currentUserEmailOrNull)`
  - Loads single post and maps to response.
- `void deletePost(Long postId, String currentUserEmail)`
  - Checks if current user is the author; if yes, extracts the S3 key from `post.imageUrl` via `S3Service.extractKey` and calls `S3Service.deleteFile` before deleting the post record.
- `Page<PostResponse> getPostsByUser(Long userId, String currentUserEmail, Pageable pageable)`
  - Posts for a specific user, with same mapping logic as feed.

#### 6.5 `ReactionService`

**Depends on**: `PostReactionRepository`, `PostRepository`, `ProfileRepository`, `PostCommentRepository`

- `PostReactionResponse reactToPost(Long postId, ReactionRequest request, String currentUserEmail)`
  - Creates or updates a `PostReaction` for `(post, profile)`.
- `PostReactionResponse removeReaction(Long postId, String currentUserEmail)`
  - Deletes the user’s reaction.

#### 6.6 `CommentService`

**Depends on**: `PostCommentRepository`, `PostRepository`, `ProfileRepository`

- `Page<CommentResponse> getComments(Long postId, Pageable pageable)`
  - Returns comments for a post, mapped with author info.
- `CommentResponse addComment(Long postId, CreateCommentRequest request, String currentUserEmail)`
  - Creates `PostComment`.
- `void deleteComment(Long postId, Long commentId, String currentUserEmail)`
  - Ensures current user is author or post owner, then deletes.

#### 6.7 `SavedWordService`

**Depends on**: `SavedWordRepository`, `ProfileRepository`, `LanguageRepository`

- `Page<SavedWordResponse> getUserWords(String email, String language, String sort, Pageable pageable)`
  - Filters words for user and language, applies sort (e.g., newest, highest mastery).
- `SavedWordResponse getWord(String email, UUID wordId)`
  - Loads a word belonging to the user.
- `SavedWordResponse saveWord(String email, SaveWordRequest request)`
  - Creates a new `SavedWord` if not already present for the combination.
- `SavedWordResponse updateWord(String email, UUID wordId, UpdateWordRequest request)`
  - Updates translation/context/mastery if allowed.
- `void deleteWord(String email, UUID wordId)`
  - Deletes a user’s saved word.

#### 6.8 `PracticeService`

**Depends on**: `PracticeSessionRepository`, `PracticeResultRepository`, `SavedWordRepository`, `ProfileRepository`, `LanguageRepository`

- `PracticeSessionResponse startSession(String email, PracticeStartRequest request)`
  - Selects a set of words using weighted randomness based on mastery.
  - Creates `PracticeSession` and returns selected words.
- `PracticeResultResponse submitResult(String email, UUID sessionId, PracticeSubmitRequest request)`
  - Records a result (`PracticeResult`) for one word.
  - Updates that word’s mastery and next review date.
- `PracticeSessionSummary completeSession(String email, UUID sessionId)`
  - Finalizes session and computes total accuracy, duration, per-word summary.
- `Page<PracticeSessionSummary> getPracticeHistory(String email, Pageable pageable)`
  - Returns past sessions and their stats.

#### 6.9 `StatsService`

**Depends on**: `SavedWordRepository`, `ProfileRepository`, `LanguageRepository`

- `LearningStatsResponse getLearningStats(String email)`
  - Aggregates all `SavedWord` rows to build:
    - Overall counts and average mastery.
    - Per-language stats.
    - Mastery distribution buckets.

#### 6.10 `FriendService`

**Depends on**: `FriendRepository`, `ProfileRepository`, `ProfileService`

- `void sendRequest(Long requesterId, Long receiverId)`
  - Creates `Friend` with PENDING status.
- `void respondToRequest(Long currentUserId, Long requesterId, String action)`
  - Accepts or rejects.
- `void removeFriend(Long currentUserId, Long friendId)`
  - Deletes friendship record.
- `Page<ProfileResponse> getFriends(Long userId, Pageable pageable)`
  - Lists accepted friends with mapped `ProfileResponse`.
- `List<FriendRequestDto> getIncomingRequests(Long currentUserId)` / `getOutgoingRequests(...)`
  - Lists pending friend requests.
- `FriendStatusDto getFriendStatus(Long currentUserId, Long otherUserId)`
  - Returns the current friendship state.

#### 6.11 `FollowService`

**Depends on**: `FollowRepository`, `ProfileRepository`, `ProfileService`

- `void followUser(Long followerId, Long followingId)`
  - Creates `UserFollow` and increments counts.
- `void unfollowUser(Long followerId, Long followingId)`
  - Deletes `UserFollow` and decrements counts.
- `Page<ProfileResponse> getFollowers(Long userId, Pageable pageable)` / `getFollowing(...)`
  - Lists followers or following users.

#### 6.12 `MeetupService`

**Depends on**: `MeetupRepository`, `MeetupAttendeeRepository`, `ProfileRepository`, `LanguageRepository`

- `Page<MeetupResponse> listMeetups(Long currentUserId, MeetupFilter filter, Pageable pageable)`
  - Uses `MeetupRepository` queries (including distance filtering) to find meetups.
- `MeetupResponse createMeetup(Long organizerId, CreateMeetupRequest request)`
  - Creates `Meetup`, auto-adds organizer as `MeetupAttendee`.
- `MeetupResponse getMeetupById(Long currentUserId, Long meetupId)`
  - Loads meetup + attendee info, including flags like `isOrganizer`, `isAttendee`.
- `MeetupResponse updateMeetup(Long organizerId, Long meetupId, UpdateMeetupRequest request)`
  - Organizer-only update.
- `void deleteMeetup(Long organizerId, Long meetupId)`
  - Organizer-only delete.
- `MeetupResponse joinMeetup(Long userId, Long meetupId)` / `leaveMeetup(...)`
  - Manage `MeetupAttendee` rows with capacity/time checks.
- `List<MeetupAttendeeResponse> getAttendees(Long meetupId)`
  - Lists attendees.

#### 6.13 `ChatService`

**Depends on**: `ConversationRepository`, `MessageRepository`, `ProfileRepository`, `ProfileService`

- `ConversationResponse getUserConversations(String email, Pageable pageable)`
  - Finds conversations where user is a participant.
- `ConversationResponse sendMessage(String senderEmail, CreateConversationRequest request)`
  - Finds or creates one-to-one `Conversation`, saves `Message`, updates last message info.
- `Page<MessageResponse> getConversationMessages(String email, Long conversationId, Pageable pageable)`
  - Ensures user is a participant, then returns messages.
- `MessageResponse sendMessageToConversation(String email, Long conversationId, CreateMessageRequest request)`
  - Adds message to existing conversation.
- `void markAsRead(String email, Long conversationId)`
  - Marks all messages for that conversation as read by that user.

#### 6.14 `LearnerService`

**Depends on**: `ProfileRepository`, `LanguageRepository`, possibly custom queries

- `List<LearnerResponse> findNearbyLearners(String currentUserEmail, LearnerFilter filter)`
  - Uses location, language preferences, and privacy flags to find suitable learners.

#### 6.15 `PlacesService`

**Depends on**: `RestTemplate`, configuration properties (`app.google.places-key`)**

- `PlacesAutocompleteResponse autocomplete(String input)`
  - Calls Google Places Autocomplete endpoint.
- `PlaceDetailsResponse getPlaceDetails(String placeId)`
  - Calls Places Details endpoint.

#### 6.16 `S3Service`

**Depends on**: `S3Client` (from `S3Config`), `aws.s3.buckets.customer` and `aws.region` properties

- `String uploadFile(MultipartFile file, String folder)`
  - Builds a unique S3 key: `<folder>/<UUID>-<originalFilename>`.
  - Calls `S3Client.putObject` with the file bytes and `ContentType`.
  - Returns the full public URL: `https://<cloudfront-domain>/<key>`.
- `void deleteFile(String key)`
  - Calls `S3Client.deleteObject` for the given key.
- `String getFileUrl(String key)`
  - Reconstructs the full public URL from a stored key.
- `String extractKey(String url)`
  - Strips the bucket-URL prefix to get back the S3 key; returns `null` if the URL doesn't belong to this bucket.

Used by `FileController` (upload/delete endpoints), `PostService` (image cleanup on post deletion), and `UserController` (old avatar cleanup on profile update).

#### 6.17 `TranslationService`, `ReportService`, `RefreshTokenService`

- `TranslationService`
  - `PostTranslationResponse getTranslation(Long postId, String targetLanguage)` – loads/creates `PostTranslation`.
- `ReportService`
  - `ContentReport createReport(ReportRequest request, Profile reporter)` – saves `ContentReport`.
- `RefreshTokenService`
  - `RefreshToken createToken(Profile profile)`, `RefreshToken validate(...)`, `void deleteByProfileId(Long profileId)` – manages refresh tokens.

---

### 7. Data Model & Repository Interfaces

Below is a conceptual overview of the main entities and their repository interfaces. (Method names are representative of standard Spring Data style.)

#### 7.1 Core User & Social Entities

- **`Profile`**
  - Fields: `id`, `username`, `email`, `passwordHash`, `displayName`, `avatarUrl`, `bio`, `latitude`, `longitude`, `location`, `locationVisibility`, follower/following counts, privacy flags.
  - Relationships:
    - `List<UserRole> roles` (`AppRole` enum).
    - `List<UserLanguage> languages`.
    - `UserSettings`, `RefreshToken`.
  - **Repository**: `ProfileRepository`
    - Typical methods: `findByEmail`, `findById`, `existsByEmail`, `existsByUsername`, and various count queries.

- **`UserRole`**
  - Fields: `id`, `Profile profile`, `AppRole role` (e.g. `USER`, `ADMIN`).

- **`UserSettings`**
  - Fields: notification toggles, privacy toggles, etc.
  - **Repository**: `UserSettingsRepository`

- **`Friend`**
  - Fields: `id`, `Profile requester`, `Profile receiver`, `FriendStatus status`, timestamps.
  - **Repository**: `FriendRepository`
    - Methods for finding friend relationships by users and status.

- **`UserFollow`**
  - Fields: `id`, `Profile follower`, `Profile following`, `createdAt`.
  - **Repository**: `FollowRepository`

- **`UserBlock`**
  - Fields: `id`, `Profile blocker`, `Profile blocked`.
  - **Repository**: `UserBlockRepository`

#### 7.2 Language & Learning Entities

- **`Language`**
  - Fields: `code` (PK), `name`, `nativeName`, `flagEmoji`.
  - **Repository**: `LanguageRepository`

- **`UserLanguage`**
  - Fields: `id`, `Profile profile`, `Language language`, `ProficiencyLevel level`, `boolean isLearning`.
  - **Repository**: `UserLanguageRepository`
    - Includes methods like `deleteByProfileId`.

- **`SavedWord`**
  - Fields: `UUID id`, `Profile user`, `String word`, `String translation`, `String languageCode`, `SourceType source`, `String sourceId`, `String context`, `int masteryLevel`, `Instant nextReview`, `Instant createdAt`.
  - **Repository**: `SavedWordRepository`

- **`PracticeSession`**
  - Fields: `UUID id`, `Profile user`, `Instant startedAt`, `Instant completedAt`, `int wordsPracticed`, `int correctCount`.
  - Relationships: `List<PracticeResult> results`.
  - **Repository**: `PracticeSessionRepository`

- **`PracticeResult`**
  - Fields: `id`, `PracticeSession session`, `SavedWord word`, `boolean correct`, `long responseTimeMs`, timestamps.
  - **Repository**: `PracticeResultRepository`

#### 7.3 Post, Reaction, Comment, Translation

- **`Post`**
  - Fields: `id`, `Profile author`, `String content`, `String originalLanguage`, `String imageUrl`, `Double latitude`, `Double longitude`, `PostStatus status`, timestamps.
  - **Repository**: `PostRepository`

- **`PostReaction`**
  - Composite ID: `PostReactionId { Long postId, Long profileId }`.
  - Fields: `Post post`, `Profile profile`, `ReactionType type`, timestamps.
  - **Repository**: `PostReactionRepository`

- **`PostComment`**
  - Fields: `id`, `Post post`, `Profile author`, `String content`, timestamps.
  - **Repository**: `PostCommentRepository`

- **`PostTranslation`**
  - Fields: `id`, `Post post`, `String targetLanguage`, `String translatedContent`, timestamps.
  - **Repository**: `PostTranslationRepository`

#### 7.4 Meetups & Nearby Learners

- **`Meetup`**
  - Fields: `id`, `Profile organizer`, `Language language`, title, description, datetime, location name, `Double latitude`, `Double longitude`, `Integer maxAttendees`, `MeetupStatus status`, timestamps.
  - **Repository**: `MeetupRepository`
    - Custom queries for upcoming meetups and distance filtering.

- **`MeetupAttendee`**
  - Fields: `id`, `Meetup meetup`, `Profile attendee`, `Instant joinedAt`.
  - **Repository**: `MeetupAttendeeRepository`

#### 7.5 Chat & Messaging

- **`Conversation`**
  - Fields: `id`, `Set<Profile> participants`, `String lastMessagePreview`, `Instant lastMessageAt`, timestamps.
  - **Repository**: `ConversationRepository`

- **`Message`**
  - Fields: `id`, `Conversation conversation`, `Profile sender`, `String content`, `boolean read`, timestamps.
  - **Repository**: `MessageRepository`

#### 7.6 Other Entities

- **`ContentReport`**
  - Fields: `id`, `Post post`, `Profile reporter`, `String reason`, `String details`, timestamps.
  - **Repository**: `ContentReportRepository`

- **`RefreshToken`**
  - Fields: `id`, `Profile profile`, `String token`, `Instant expiry`.
  - **Repository**: `RefreshTokenRepository`

---

### 8. Putting It All Together – Example Flows

#### 8.1 User registration & login

1. Client sends `POST /api/auth/register` with user details.
2. `AuthController.register` → `AuthService.register`.
3. `AuthService`:
   - Validates data.
   - Creates `Profile` via `ProfileRepository`.
   - Creates `UserRole` rows.
   - Issues JWT and refresh token via `JwtUtils` + `RefreshTokenService`.
4. Controller returns `AuthResponse` (tokens + profile info).
5. For future requests, client sends `Authorization: Bearer <jwt>`.
6. `JwtAuthenticationFilter` authenticates user and attaches them to `SecurityContext`.

#### 8.2 Creating and viewing posts

1. Client sends `POST /api/posts` with `CreatePostRequest`.
2. `PostController.createPost` → `PostService.createPost`.
3. `PostService`:
   - Loads author `Profile` via `ProfileRepository`.
   - Saves `Post` via `PostRepository`.
   - Maps `Post` → `PostResponse`.
4. For feed, client calls `GET /api/posts`.
5. `PostController.getFeed` → `PostService.getFeed`.
6. `PostService`:
   - Fetches posts via `PostRepository`.
   - Uses `PostReactionRepository` and `PostCommentRepository` for counts.
   - Computes optional distance and current user reaction.
   - Returns paginated `PostResponse` list.

#### 8.3 Vocabulary practice

1. Client sends `POST /api/learn/sessions/start`.
2. `PracticeController.startSession` → `PracticeService.startSession`.
3. `PracticeService`:
   - Loads user’s `SavedWord`s via `SavedWordRepository`.
   - Selects words using weighted random logic.
   - Creates `PracticeSession`.
4. For each word, client calls `POST /sessions/{sessionId}/submit`.
5. `PracticeService.submitResult`:
   - Creates `PracticeResult`.
   - Updates `SavedWord` mastery.
6. When finished, client calls `POST /sessions/{sessionId}/complete`.
7. `PracticeService.completeSession` builds a summary that `PracticeController` returns.

#### 8.4 Real-time chat message

1. Client is connected to `/ws` and subscribed to `/topic/conversation.{id}`.
2. Client sends STOMP message to `/app/chat.send` with message payload.
3. `ChatController.handleChatSend`:
   - Calls `ChatService.sendMessage`.
   - `ChatService` saves `Message` and updates `Conversation`.
4. `ChatController` uses `SimpMessagingTemplate` to broadcast result to `/topic/conversation.{id}`.
5. All subscribed clients see the new message in real time.

---

### 9. How to Use This Document

- To explore a specific feature:
  - Start with the **controller** section above.
  - Jump to the corresponding **service**.
  - Then open the related **entities** and **repositories**.
- To understand security:
  - Follow `SecurityConfig` → `ApplicationConfig` → `JwtAuthenticationFilter` → `JwtUtils` → `AuthService`.
- To understand learning features:
  - Follow `SavedWordController` → `SavedWordService` → `SavedWord`/`Language`/`UserLanguage`.
  - Then `PracticeController` → `PracticeService` → `PracticeSession`/`PracticeResult`.

This should give you a complete mental model of how the backend is wired together and where to look when you want to modify or extend behavior.

