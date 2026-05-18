# 🌏 Locale — Backend

Spring Boot backend for the Locale language exchange app.

## Prerequisites

- **Docker Desktop** (that's it — no Java or Maven needed!)

---

## 🚀 Running With Docker (Recommended for Team)

> Both repos must be cloned side-by-side on your machine:
> ```
> Desktop/
> ├── W19-back-end/   ← this repo
> └── W19-front-end/  ← frontend repo
> ```

### 1. Copy the environment file
```bash
cp .env.example .env
```
Fill in the values your teammate shared with you (JWT secret, Google Places key, and Google Translation key).

### 2. Start everything
```bash
docker compose up --build
```

This spins up the app stack:
| Container | URL |
|---|---|
| Frontend (React) | http://localhost:8080 |
| Backend (Spring Boot) | http://localhost:8081 |
| PostgreSQL | localhost:5433 |
| Vision scanner service | http://localhost:8000 |

The database is **automatically seeded** with demo data on first start.

### 3. Demo accounts (password: `demo123`)
| Username | Email | Native language |
|---|---|---|
| minso_k | minso@locale.app | Korean |
| emma_uk | emma@locale.app | English |
| linh_vn | linh@locale.app | Vietnamese |
| wei_cn | wei@locale.app | Chinese |

---

## 🔄 Useful Commands

| Action | Command |
|---|---|
| Start (first time or after code changes) | `docker compose up --build` |
| Start (no code changes) | `docker compose up` |
| Stop | `docker compose down` |
| **Reset database** (re-runs seed) | `docker compose down -v && docker compose up --build` |
| View logs | `docker compose logs -f backend` |

---

## 💻 Running Locally (Without Docker)

Requirements: Java 21, Maven, PostgreSQL 14+

1. Start PostgreSQL and create database `mydatabase` with user `myuser` / password `secret`
2. Copy `.env.example` → `.env` and fill in values
3. Run: `./mvnw spring-boot:run`

The API will be available at `http://localhost:8081`.

Required environment variables:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signs JWT access tokens |
| `GOOGLE_PLACES_KEY` | Calls Google Places proxy endpoints |
| `GOOGLE_TRANSLATE_API_KEY` | Calls Google Cloud Translation for post translations |
| `VISION_API_URL` | Scanner vision-service base URL, defaults to `http://localhost:8000` |
| `VISION_TIMEOUT_MS` | Backend timeout for scanner requests, defaults to `60000` |
| `VISION_SUPPORTED_LANGUAGES` | Taxonomy languages served directly by vision-service, defaults to `en,es,fr,ja` |
| `VISION_YOLO_MODEL` | YOLO proposal model loaded by vision-service, defaults to `yolov8m.pt` |
| `VISION_SIGLIP_MODEL` | SigLIP reranking/index model, defaults to `google/siglip-base-patch16-224` |

## AI Object Scanner Runtime

The scanner uses the Spring Boot backend plus the Python ML vision-service in `ml-services/vision-service/`.
Docker Compose starts both services together and points the backend at `http://vision-service:8000`.
The backend appends `/analyze` in Spring config, so `VISION_API_URL` should be the base URL only.

Useful scanner settings:

| Setting | Where | Purpose |
|---|---|---|
| `VISION_API_URL` | `.env` / compose | Base URL for vision-service; Docker uses `http://vision-service:8000` |
| `VISION_TIMEOUT_MS` | `.env` / Spring config | Caps backend wait time for object detection calls |
| `VISION_SUPPORTED_LANGUAGES` | `.env` / Spring config | Languages that use vision-service taxonomy translations directly |
| `VISION_YOLO_MODEL` | `.env` / compose | YOLO proposal model used by vision-service |
| `VISION_SIGLIP_MODEL` | `.env` / compose | SigLIP model used for retrieval/reranking and indexes |

The vision-service exposes `GET /health`. The backend exposes Actuator `GET /actuator/health` for Docker healthchecks.
Vision-service confidence scores are SigLIP/FAISS similarity scores, not classic YOLO confidence. Do not restore the old `0.60` Java post-filter for scanner results.
