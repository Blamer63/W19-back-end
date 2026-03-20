# API Contract - W19 Backend

**Base URL:** `http://localhost:8081/api`  
**Authentication:** Bearer Token (JWT)  
**Format:** JSON (`snake_case`)

---

> **Swagger UI is available at:** `http://localhost:8081/swagger-ui.html`
>
> All endpoint definitions, request/response schemas, and authentication requirements are documented interactively via Swagger. Use the UI to explore and test the API directly.

---

## Key Conventions

- All JSON fields use `snake_case` (e.g. `display_name`, `avatar_url`).
- All IDs are UUIDs.
- Paginated responses follow Spring's `Page<T>` shape: `content`, `totalElements`, `totalPages`, `number`, `size`.
- Protected routes require `Authorization: Bearer <access_token>` header.
- Public routes (no auth required): `POST /api/auth/**`, `GET /api/languages`.

## Future / Pending Endpoints

- Notifications
- Moderation Dashboard
- AI Object Scanner (`POST /api/scanner/analyze`)
