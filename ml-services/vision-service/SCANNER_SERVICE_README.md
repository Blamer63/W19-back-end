# Scanner Service README

> **Scope:** `W19-back-end/ml-services/vision-service/`  
> **FastAPI port:** `8000`  
> **Audience:** Developers running or extending the object-scanner pipeline independently.

---

## Table of Contents

1. [What the Scanner Service Does](#1-what-the-scanner-service-does)
2. [Service Architecture](#2-service-architecture)
3. [Running the Scanner Service](#3-running-the-scanner-service)
4. [Required Models & Libraries](#4-required-models--libraries)
5. [Model Initialization Behaviour](#5-model-initialization-behaviour)
6. [API Endpoints](#6-api-endpoints)
7. [Translation System](#7-translation-system)
8. [Environment Variables](#8-environment-variables)
9. [Supported Languages](#9-supported-languages)
10. [Taxonomy Translation Structure](#10-taxonomy-translation-structure)
11. [Troubleshooting](#11-troubleshooting)
12. [Performance Notes](#12-performance-notes)

---

## 1. What the Scanner Service Does

The scanner service powers the **real-world object vocabulary learning** feature of the Locale app.  
A user points their camera at everyday objects; the service identifies what it sees and returns the vocabulary
word for that object in the user's target language.

**Core responsibilities:**

- Accept a base64-encoded camera image from the Spring Boot backend.
- Use **YOLOv8m** to propose bounding-box regions of interest (class predictions from YOLO are ignored — only box coordinates are used).
- Crop and encode each region using **SigLIP** semantic image embeddings.
- Retrieve the closest matching concept from pre-built **FAISS** per-category indexes.
- Apply confidence filtering and context re-ranking across all detected crops.
- Look up the canonical English label and its localized equivalent from static JSON taxonomy files.
- Return a structured detection response to the backend.

There are no external APIs involved. All inference and localization run inside the container.

---

## 2. Service Architecture

```
Spring Boot backend (port 8081)
    │
    │  POST /analyze  { image: <base64>, language: "es" }
    ▼
FastAPI vision-service (port 8000)
    │
    ├─ Step 1: YOLOv8m → bounding box proposals (class labels ignored)
    ├─ Step 2: extract_crop() → aspect-preserving letterbox crop per box
    ├─ Step 3: SigLIP image encoder → L2-normalized embedding per crop
    ├─ Step 4: coarse_route() → top-K categories via centroid similarity
    ├─ Step 5: fine_retrieve() → per-category FAISS IndexFlatIP search
    ├─ Step 6: apply_confidence_filter() → primary threshold + ambiguity gate
    ├─ Step 7: context_rerank() → importance-weighted + co-occurrence boost
    ├─ Step 8: deduplicate_labels() → canonical English labels only
    └─ Step 9: get_translation() → static JSON lookup → localized label
    │
    └─ Response: { detections: [{canonical_label, translated_label}], language }
```

**Translation** is the last and simplest step — a dictionary lookup in a local JSON file.  
No network calls are made during translation.

### Key modules

| File | Role |
|---|---|
| `main.py` | FastAPI app, startup sequence, `/analyze` endpoint |
| `retrieval.py` | Coarse routing, FAISS retrieval, confidence filtering, re-ranking |
| `indexer.py` | Loads pre-built FAISS indexes from `indexes/` at startup |
| `image_utils.py` | Letterbox cropping, input-size resolution |
| `translation_store.py` | Loads and queries `taxonomy/translations/*.json` |
| `taxonomy/` | CSV concept definitions, `categories.json`, translation JSON files |
| `scripts/build_indexes.py` | Offline index builder (run once; output committed or built in Docker) |

---

## 3. Running the Scanner Service

### Full stack (recommended)

From `W19-back-end/`:

```bash
docker compose up --build
```

This starts PostgreSQL, the Spring Boot backend, and the vision-service together.

### Vision service only

```bash
docker compose up --build vision-service
```

Useful when iterating on the ML pipeline without rebuilding the Java backend.

### First-startup delay

**Expect 5–15 minutes on first build.** The Dockerfile pre-downloads and caches:

1. YOLOv8m weights (~52 MB) from Ultralytics
2. SigLIP `google/siglip-base-patch16-224` weights (~400 MB) from Hugging Face
3. FAISS indexes are built offline inside the image (`scripts/build_indexes.py`)

Subsequent restarts reuse the cached Docker layer — startup takes under 30 seconds.

### Verify the service is up

```bash
curl http://localhost:8000/health
```

Expected response:

```json
{
  "status": "ok",
  "device": "cpu",
  "siglip_model": "google/siglip-base-patch16-224",
  "yolo_model": "yolov8m.pt",
  "total_concepts": 443,
  "categories": ["furniture", "electronics", "food", "clothing", "kitchenware", "office", "bathroom", "tools", "household", "outdoor"]
}
```

---

## 4. Required Models & Libraries

| Component | Version | Purpose |
|---|---|---|
| **YOLOv8m** (`ultralytics==8.1.0`) | `yolov8m.pt` | Region proposal — bounding boxes only |
| **SigLIP** (`transformers==4.38.2`) | `google/siglip-base-patch16-224` | Semantic image embedding (224×224 input) |
| **FAISS** (`faiss-cpu==1.13.2`) | `IndexFlatIP` per category | Nearest-neighbour retrieval over concept embeddings |
| **PyTorch** (`torch==2.1.2`) | — | SigLIP inference backend |
| `torchvision==0.16.2` | — | Image transforms |
| `Pillow==10.0.0` | — | Image decoding and crop extraction |
| `FastAPI==0.103.1` / `uvicorn==0.23.2` | — | HTTP service layer |
| `numpy==1.26.4` | — | Embedding arithmetic |

> **Note:** `faiss-cpu` is used by default. The Dockerfile does not install CUDA-enabled FAISS.  
> GPU support requires a separate `faiss-gpu` build and a CUDA-capable Docker base image.

All packages are pinned in `requirements.txt`. The Dockerfile installs them directly without a venv.

---

## 5. Model Initialization Behaviour

Initialization happens **synchronously at container startup**, in this order:

```
1. Resolve compute device (CPU / CUDA)
2. Load YOLOv8m                      → downloads yolov8m.pt if not cached
3. Load SigLIP processor + model     → downloads from Hugging Face if not cached
4. Load FAISS indexes (indexes/)     → reads pre-built .index files from disk
5. Load translation store            → reads taxonomy/translations/*.json into memory
```

**The service does not accept requests until all five steps complete.**  
uvicorn only begins listening after the module-level setup finishes.

If FAISS indexes are missing (e.g. `indexes/` is empty), startup will fail with:

```
FileNotFoundError: index_meta.json not found at indexes/index_meta.json
Run offline index builder first:
  python scripts/build_indexes.py
Then restart the service.
```

In the Docker build, `scripts/build_indexes.py` runs as a `RUN` step, so indexes are always present in the image.

---

## 6. API Endpoints

### `GET /health`

Returns service status and loaded model details.

**Request:** no body.

**Response:**

```json
{
  "status": "ok",
  "device": "cpu",
  "siglip_model": "google/siglip-base-patch16-224",
  "yolo_model": "yolov8m.pt",
  "total_concepts": 443,
  "categories": ["furniture", "electronics", "food", ...]
}
```

---

### `GET /debug-test`

Returns a hardcoded response that matches the `/analyze` schema exactly.  
Use this to verify Spring Boot ↔ vision-service HTTP transport and DTO deserialization **without** running any ML inference.

**Request:** no body.

**Response:**

```json
{
  "detections": [
    { "canonical_label": "chair", "translated_label": "chair" },
    { "canonical_label": "vase",  "translated_label": "vase"  }
  ],
  "description": "objects detected: chair, vase",
  "language": "en"
}
```

> If Spring Boot receives `detections: []` from this endpoint, the bug is in the Spring Boot `VisionResponse` DTO or Jackson deserialization — not in the ML pipeline.

---

### `POST /analyze`

Main inference endpoint. Accepts a base64-encoded image and a target language code.

**Request body:**

```json
{
  "image": "<base64-encoded JPEG or PNG>",
  "language": "es"
}
```

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `image` | `string` | ✅ | — | Standard base64, no `data:image/...` prefix needed |
| `language` | `string` | ❌ | `"en"` | One of: `en`, `es`, `fr`, `ja` |

**Success response:**

```json
{
  "detections": [
    { "canonical_label": "laptop",   "translated_label": "ordinateur portable" },
    { "canonical_label": "keyboard", "translated_label": "clavier" }
  ],
  "description": "objects detected: ordinateur portable, clavier",
  "language": "fr"
}
```

**Error / no-match response:**

```json
{
  "detections": [
    { "canonical_label": "unknown object", "translated_label": "objet inconnu" }
  ],
  "description": "objects detected: objet inconnu",
  "language": "fr"
}
```

**Invalid image response:**

```json
{
  "error": "Invalid image data: ...",
  "detections": [],
  "description": "",
  "language": "fr"
}
```

**Key behaviours:**

- YOLO class predictions are **ignored** — only bounding boxes are used.
- If YOLO produces no boxes, the full image is treated as one crop.
- Up to `MAX_LABELS` (default: 3) canonical labels are returned per image.
- Detections that fail the confidence gate return `"unknown object"` rather than a wrong label.

---

## 7. Translation System

**Translation is deterministic and taxonomy-based. No external translation APIs are used.**

At startup, `translation_store.py` reads all files under `taxonomy/translations/` into memory as Python dictionaries:

```
taxonomy/translations/
├── en.json   ←  ~484 entries  (English canonical label → English display label)
├── es.json   ←  ~484 entries  (English canonical label → Spanish label)
├── fr.json   ←  ~484 entries  (English canonical label → French label)
└── ja.json   ←  ~484 entries  (English canonical label → Japanese label)
```

Each file is a flat JSON object mapping the **English canonical label** to a **localized vocabulary label**:

```json
{
  "laptop":   "ordinateur portable",
  "chair":    "chaise",
  "keyboard": "clavier"
}
```

The lookup chain is:

1. Look up `canonical_label` in the requested language's dictionary → return if found.
2. Fall back to `en.json` if the label is missing in the target language → log a warning.
3. Fall back to the raw canonical label string if missing in both → log a warning.

**To add or correct a translation:** edit the relevant `.json` file and restart the service.  
**To add a new language:** add `<lang_code>.json` to `taxonomy/translations/` and rebuild/restart.

---

## 8. Environment Variables

All variables have safe defaults and can be overridden in `compose.yaml` or via `docker run -e`.

### Device control

| Variable | Default | Effect |
|---|---|---|
| `FORCE_CPU` | `0` | Set to `1` to always use CPU regardless of CUDA availability |
| `ENABLE_CUDA` | `0` | Set to `0` to disable GPU even if hardware is present (Dockerfile default) |

### Model selection

| Variable | Default | Effect |
|---|---|---|
| `SIGLIP_MODEL` | `google/siglip-base-patch16-224` | Hugging Face model ID for SigLIP |
| `YOLO_MODEL` | `yolov8m.pt` | Ultralytics model file name |

> ⚠️ If you change `SIGLIP_MODEL`, you **must** rebuild the FAISS indexes with the matching model.  
> Indexes built with one SigLIP variant are incompatible with another.  
> The service will log a `[CRITICAL WARNING]` on startup if a model mismatch is detected.

### YOLO inference

| Variable | Default | Effect |
|---|---|---|
| `YOLO_CONF` | `0.10` | Minimum YOLO box confidence to consider a proposal |
| `YOLO_IOU` | `0.50` | NMS IoU threshold |
| `YOLO_MAX_DET` | `5` | Maximum region proposals per image |

### Output

| Variable | Default | Effect |
|---|---|---|
| `MAX_LABELS` | `3` | Maximum number of canonical labels returned per image |

### Confidence filtering

| Variable | Default | Effect |
|---|---|---|
| `CONF_PRIMARY` | `0.05` | Minimum SigLIP cosine score to pass Gate 1 |
| `CONF_AMBIGUITY` | `1.02` | top1/top2 score ratio required to pass Gate 2 (2% margin) |

### Retrieval

| Variable | Default | Effect |
|---|---|---|
| `COARSE_TOP_K` | `3` | Number of categories to search in the coarse routing step |
| `FINE_TOP_K` | `5` | Top-K candidates retrieved per FAISS category index |
| `CONTEXT_BOOST` | `1.15` | Score multiplier applied to the dominant scene category |

---

## 9. Supported Languages

| Code | Language |
|---|---|
| `en` | English |
| `es` | Spanish |
| `fr` | French |
| `ja` | Japanese |

Pass the language code in the `language` field of the `/analyze` request body.  
If an unsupported code is provided, the service silently falls back to `en`.

---

## 10. Taxonomy Translation Structure

The vocabulary taxonomy covers **~484 concepts** across **10 categories**:

| Category | Approx. concepts |
|---|---|
| `food` | 69 |
| `clothing` | 59 |
| `kitchenware` | 49 |
| `household` | 41 |
| `furniture` | 40 |
| `electronics` | 40 |
| `tools` | 40 |
| `outdoor` | 35 |
| `office` | 35 |
| `bathroom` | 35 |

Each concept has a **canonical English label** (the internal key used everywhere in the pipeline) and a per-language translation in the corresponding JSON file.

**The canonical label is never displayed to the user.** The `translated_label` field in the API response is what the frontend shows.

Example — the same object across all supported languages:

| Canonical label | `en` | `es` | `fr` | `ja` |
|---|---|---|---|---|
| `laptop` | laptop | portátil | ordinateur portable | ノートパソコン |
| `chair` | chair | silla | chaise | 椅子 |
| `coffee maker` | coffee maker | cafetera | cafetière | コーヒーメーカー |

To inspect the full concept list for a category, open the relevant CSV in `taxonomy/`:

```
taxonomy/
├── furniture.csv        # concept_id, canonical_label, aliases, parent, category, tags, visual_description
├── electronics.csv
├── food.csv
...
├── categories.json      # L1/L2 category index
└── translations/
    ├── en.json
    ├── es.json
    ├── fr.json
    └── ja.json
```

See [`taxonomy_schema.md`](taxonomy_schema.md) for the full CSV column specification and concept ID strategy.

---

## 11. Troubleshooting

### Service takes 5–15 minutes to start

**Expected on first build.** The Dockerfile downloads YOLOv8m (~52 MB) and SigLIP (~400 MB) and builds FAISS indexes, all as Docker `RUN` steps. Subsequent builds use the Docker layer cache and are fast.

If the download stalls, check your internet connection or Hugging Face / Ultralytics CDN availability.

---

### `FileNotFoundError: index_meta.json not found`

The FAISS indexes were not built. This should not happen with the normal Docker build, but can occur if:

- You are running `main.py` directly outside Docker and have not run `scripts/build_indexes.py`.
- The `indexes/` directory was cleared.

**Fix:**

```bash
cd ml-services/vision-service
python scripts/build_indexes.py --model google/siglip-base-patch16-224 --force-cpu
```

Then restart the service.

---

### `[CRITICAL WARNING] Model mismatch detected!`

The `SIGLIP_MODEL` env var does not match the model used to build the indexes.  
Retrieval results will be incorrect.

**Fix:** Rebuild the indexes with the correct model:

```bash
python scripts/build_indexes.py --model <your-siglip-model-id> --force-cpu
```

---

### All detections return `"unknown object"`

The confidence filter is rejecting all crops. Possible causes:

- The image is very dark, blurry, or contains objects not in the taxonomy.
- `CONF_PRIMARY` is set too high — lower it via env var (default: `0.05`).
- `CONF_AMBIGUITY` is set too tight — lower it slightly (default: `1.02`).
- The FAISS indexes were built with a different SigLIP model than is running at inference time.

Check the container logs for `[ConfFilter] REJECT` lines — they include the rejection reason and scores.

---

### Empty `detections: []` from Spring Boot (not from the service)

The vision service itself returned data, but Spring Boot deserialized it to an empty list.

**Diagnostic step:** call `/debug-test` from Spring Boot (via `VisionServiceClient`).  
If Spring Boot also receives `detections: []` from the hardcoded `/debug-test` response, the bug is in `VisionResponse` DTO mapping or Jackson configuration — not in the ML pipeline.

---

### Docker container runs out of memory / OOM killed

SigLIP (`base-patch16-224`) requires approximately **1.5–2 GB RAM** for inference on CPU.  
If Docker Desktop is configured with less than 4 GB total memory, the container may be killed.

**Fix:** increase Docker Desktop memory limit (Settings → Resources → Memory → set to ≥ 4 GB).

---

### `Invalid image data: ...` in the response

The base64 string sent by the backend is malformed or empty.  
Check that the Spring Boot scanner controller is base64-encoding the raw bytes correctly before sending.

---

## 12. Performance Notes

- **CPU-only execution is fully supported.** The default Docker configuration (`ENABLE_CUDA=0`) runs entirely on CPU. No GPU is required.
- **GPU is optional.** Set `ENABLE_CUDA=1` and use a CUDA-enabled Docker base image to enable GPU acceleration. `faiss-gpu` must also be installed separately.
- **First inference is slower.** PyTorch and SigLIP perform JIT compilation and warm-up on the first forward pass. Expect the first `/analyze` call after startup to take 3–8 seconds on CPU. Subsequent calls are significantly faster (typically 0.5–2 seconds per image on CPU).
- **YOLO is region-proposal only.** YOLOv8m runs with a low confidence threshold (`0.10`) because its class labels are discarded — only bounding boxes matter. This produces more proposals but does not reduce accuracy.
- **FAISS search is fast.** Each per-category `IndexFlatIP` index holds at most ~70 vectors. Retrieval across all categories is effectively instantaneous.
- **Coarse routing is O(num_categories).** The centroid comparison step runs in memory with simple dot products — no FAISS involvement. Negligible overhead.
- **Taxonomy size is intentionally bounded.** Phase 1 covers ~484 well-separated concepts. Expanding to 3,000+ concepts (Phase 2) will not significantly impact latency — FAISS scales efficiently.

---

*For the overall backend architecture, see the top-level [`README.md`](../../README.md).  
For the taxonomy CSV schema and concept ID strategy, see [`taxonomy_schema.md`](taxonomy_schema.md).*
