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

## Scanner Endpoint

- `POST /api/scanner/analyze`
- Requires auth token.
- Request body (`snake_case`):
  - `image_base64` (required): Base64-encoded frame/image captured by frontend camera flow.
  - `target_language` (required): User preferred language code (e.g. `vi`, `es`).
  - `confidence_threshold` (optional): Defaults to `0.4`.
  - `max_results` (optional): Defaults to `6`.
- Response:
  - `status`: `OK` or `NO_OBJECTS`
  - `message`: user-friendly status text
  - `target_language`
  - `detection_count`
  - `detections[]`: each has `label`, `translated_label`, `confidence`, `translated`

## Future / Pending Endpoints

- Notifications
- Moderation Dashboard
