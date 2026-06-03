"""
main.py — Vision service: SigLIP + FAISS + Hierarchical Taxonomy pipeline.

API contract:
  POST /analyze
  Input:  { "image": "<base64 string>", "language": "es" }
  Output: {
    "detections": [
      {
        "canonical_label": "chair",
        "translated_label": "silla",
        "confidence": 0.12,
        "box": { "x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4 }
      }
    ],
    "description": "objects detected: silla",
    "language": "es"
  }

Architecture:
  YOLOv8m (proposals) → Letterbox crop → SigLIP image encoder
  → Coarse routing (category centroids)
  → Fine FAISS retrieval (per-category IndexFlatIP)
  → Confidence filtering
  → Context re-ranking
  → Canonical label output

Device resolution (three-way):
  FORCE_CPU=1  → always CPU
  ENABLE_CUDA=0 → disable GPU even if available
  default      → cuda if available, else cpu
"""

import base64
import io
import logging
import os

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("vision.main")

import torch
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from PIL import Image
from pydantic import BaseModel
from transformers import AutoModel, AutoProcessor
from ultralytics import YOLO

from image_utils import extract_crop, get_model_input_size
from indexer import TaxonomyIndexes, load_indexes, verify_model_compatibility
from retrieval import (
    UNKNOWN_LABEL,
    apply_confidence_filter,
    context_rerank,
    coarse_route,
    deduplicate_labels,
    fine_retrieve,
    score_crop_importance,
)
from translation_store import init_translation_store, get_translation

# ─────────────────────────────────────────────────────────────────────────────
# Configuration (all overrideable via environment variables)
# ─────────────────────────────────────────────────────────────────────────────

DEFAULT_SIGLIP_MODEL = "google/siglip-base-patch16-224"
SIGLIP_MODEL_ID      = os.getenv("SIGLIP_MODEL", DEFAULT_SIGLIP_MODEL)

YOLO_MODEL_NAME      = os.getenv("YOLO_MODEL",   "yolov8m.pt")
YOLO_CONF_THRESHOLD  = float(os.getenv("YOLO_CONF",    "0.10"))
YOLO_IOU_THRESHOLD   = float(os.getenv("YOLO_IOU",     "0.50"))
YOLO_MAX_DET         = int(os.getenv("YOLO_MAX_DET",   "10"))

MAX_OUTPUT_LABELS    = int(os.getenv("MAX_LABELS",     "2"))
MIN_OUTPUT_CONFIDENCE = float(os.getenv("MIN_OUTPUT_CONF", "0.045"))
MIN_BOX_AREA_RATIO    = float(os.getenv("MIN_BOX_AREA",   "0.0015"))
MAX_BOX_ASPECT_RATIO  = float(os.getenv("MAX_BOX_ASPECT", "8.0"))

# Some fine-grained taxonomy concepts are too specific for noisy YOLO crops.
# Return user-facing, general vocabulary unless the app later adds stronger
# context-aware disambiguation for these subclasses.
CANONICAL_LABEL_OVERRIDES = {
    "piano stool": "stool",
}


def resolve_device() -> str:
    """
    Three-way device resolution:
      FORCE_CPU=1   → always CPU (overrides everything)
      ENABLE_CUDA=0 → disable GPU even if hardware available
      default       → use GPU if available, else CPU
    """
    if os.getenv("FORCE_CPU", "0") == "1":
        return "cpu"
    if os.getenv("ENABLE_CUDA", "1") == "0":
        return "cpu"
    return "cuda" if torch.cuda.is_available() else "cpu"


# ─────────────────────────────────────────────────────────────────────────────
# Startup
# ─────────────────────────────────────────────────────────────────────────────

app    = FastAPI(title="Vision Service", version="2.0.0")
DEVICE = resolve_device()

print(f"\n{'='*60}")
print(f"  Vision Service v2 — SigLIP + FAISS + Hierarchical Taxonomy")
print(f"  Device:      {DEVICE}")
print(f"  YOLO:        {YOLO_MODEL_NAME}")
print(f"  SigLIP:      {SIGLIP_MODEL_ID}")
print(f"{'='*60}\n")

# 1. Load YOLOv8m — for region proposals only (class predictions ignored)
print("Loading YOLO detection model...")
yolo_model = YOLO(YOLO_MODEL_NAME)
print(f"  [OK] {YOLO_MODEL_NAME} loaded\n")

# 2. Load SigLIP — image encoder for crop embeddings
print(f"Loading SigLIP image encoder ({SIGLIP_MODEL_ID})...")
siglip_processor = AutoProcessor.from_pretrained(SIGLIP_MODEL_ID)
siglip_model     = AutoModel.from_pretrained(SIGLIP_MODEL_ID).to(DEVICE)
siglip_model.eval()
INPUT_SIZE = get_model_input_size(SIGLIP_MODEL_ID)
print(f"  [OK] SigLIP loaded  (input: {INPUT_SIZE}x{INPUT_SIZE})\n")

# 3. Load prebuilt FAISS taxonomy indexes
print("Loading FAISS taxonomy indexes...")
taxonomy_indexes: TaxonomyIndexes = load_indexes()
verify_model_compatibility(taxonomy_indexes, SIGLIP_MODEL_ID)
print(f"  [OK] {taxonomy_indexes}\n")

# 4. Initialize local translation store
print("Initializing translation store...")
translations_dir = os.path.join(os.path.dirname(__file__), "taxonomy", "translations")
init_translation_store(translations_dir)
print(f"  [OK] Translation store ready\n")

print("Service ready.\n")


# ─────────────────────────────────────────────────────────────────────────────
# Request / Response
# ─────────────────────────────────────────────────────────────────────────────

class AnalyzeRequest(BaseModel):
    image: str  # base64-encoded image
    language: str = "en"  # optional target language (en, es, fr, ja)


def normalize_box(
    box_xyxy: tuple[float, float, float, float],
    image_size: tuple[int, int],
) -> dict[str, float]:
    width, height = float(image_size[0]), float(image_size[1])
    if width <= 0 or height <= 0:
        return {"x": 0.0, "y": 0.0, "width": 1.0, "height": 1.0}

    x1, y1, x2, y2 = box_xyxy
    x = max(0.0, min(1.0, x1 / width))
    y = max(0.0, min(1.0, y1 / height))
    box_width = max(0.0, min(1.0, (x2 - x1) / width))
    box_height = max(0.0, min(1.0, (y2 - y1) / height))
    return {
        "x": round(x, 6),
        "y": round(y, 6),
        "width": round(box_width, 6),
        "height": round(box_height, 6),
    }


def normalize_output_label(label: str) -> str:
    return CANONICAL_LABEL_OVERRIDES.get(label, label)


def is_supported_box(
    box_xyxy: tuple[float, float, float, float],
    image_size: tuple[int, int],
) -> bool:
    image_width, image_height = float(image_size[0]), float(image_size[1])
    if image_width <= 0 or image_height <= 0:
        return False

    x1, y1, x2, y2 = box_xyxy
    box_width = max(0.0, x2 - x1)
    box_height = max(0.0, y2 - y1)
    if box_width <= 0 or box_height <= 0:
        return False

    area_ratio = (box_width * box_height) / (image_width * image_height)
    aspect_ratio = max(box_width / box_height, box_height / box_width)

    return area_ratio >= MIN_BOX_AREA_RATIO and aspect_ratio <= MAX_BOX_ASPECT_RATIO


def normalize_retrieval_results(
    results: list[tuple[str, float, str]],
) -> list[tuple[str, float, str]]:
    normalized: list[tuple[str, float, str]] = []
    best_by_label: dict[str, tuple[str, float, str]] = {}

    for label, score, category in results:
        normalized_label = normalize_output_label(label)
        candidate = (normalized_label, score, category)
        current = best_by_label.get(normalized_label)
        if current is None or score > current[1]:
            best_by_label[normalized_label] = candidate

    seen: set[str] = set()
    for label, _, _ in results:
        normalized_label = normalize_output_label(label)
        if normalized_label in seen:
            continue
        candidate = best_by_label.get(normalized_label)
        if candidate is not None:
            normalized.append(candidate)
            seen.add(normalized_label)

    return normalized


def build_detection_metadata(
    final_labels: list[str],
    reranked: list[tuple[str, float, str]],
    per_crop_results: list[list[tuple[str, float, str]]],
    raw_box_coords: list[tuple[float, float, float, float]],
    crop_importances: list[float],
    image_size: tuple[int, int],
) -> dict[str, dict]:
    best_reranked_score = {}
    for label, score, _ in reranked:
        best_reranked_score.setdefault(label, float(score))

    metadata: dict[str, dict] = {}
    for crop_index, crop_results in enumerate(per_crop_results):
        importance = crop_importances[crop_index] if crop_index < len(crop_importances) else 1.0
        box = normalize_box(raw_box_coords[crop_index], image_size)
        for label, score, _ in crop_results:
            weighted_score = float(score) * max(0.0, float(importance))
            current = metadata.get(label)
            if current is None or weighted_score > current["source_score"]:
                metadata[label] = {
                    "source_score": weighted_score,
                    "confidence": best_reranked_score.get(label, weighted_score),
                    "box": box,
                }

    full_image_box = normalize_box(
        (0.0, 0.0, float(image_size[0]), float(image_size[1])),
        image_size,
    )
    return {
        label: metadata.get(label, {
            "source_score": 0.0,
            "confidence": best_reranked_score.get(label, 0.0),
            "box": full_image_box,
        })
        for label in final_labels
    }


def filter_final_labels(
    final_labels: list[str],
    detection_metadata: dict[str, dict],
) -> list[str]:
    return [
        label
        for label in final_labels
        if float(detection_metadata.get(label, {}).get("confidence", 0.0)) >= MIN_OUTPUT_CONFIDENCE
    ]


# ─────────────────────────────────────────────────────────────────────────────
# Embedding helper
# ─────────────────────────────────────────────────────────────────────────────

def embed_crop(crop: Image.Image) -> "torch.Tensor":
    """
    Encode a PIL crop using SigLIP image encoder.
    Returns a 1D L2-normalized float32 tensor on CPU.
    """
    inputs = siglip_processor(images=crop, return_tensors="pt").to(DEVICE)
    with torch.no_grad():
        out = siglip_model.get_image_features(**inputs)
        features = out if isinstance(out, torch.Tensor) else out.pooler_output
        features = features / (features.norm(dim=-1, keepdim=True) + 1e-8)
    return features[0].cpu().float()


# ─────────────────────────────────────────────────────────────────────────────
# /analyze endpoint
# ─────────────────────────────────────────────────────────────────────────────

@app.post("/analyze")
def analyze_image(req: AnalyzeRequest):
    # ── Decode image ──────────────────────────────────────────────────────────
    try:
        image_bytes = base64.b64decode(req.image)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        logger.warning("Image decode failed: %s", e)
        return JSONResponse(
            status_code=400,
            content={"error": "Invalid image data", "detections": [], "description": "", "language": req.language},
        )

    try:
        # ── Step 1: YOLO region proposals ────────────────────────────────────
        # YOLO class predictions are IGNORED — only bounding boxes used.
        results = yolo_model(
            image,
            conf=YOLO_CONF_THRESHOLD,
            iou=YOLO_IOU_THRESHOLD,
            max_det=YOLO_MAX_DET,
            verbose=False,
        )

        boxes = results[0].boxes if results and len(results) > 0 else []
        logger.debug("  YOLO: %s proposal(s)", len(boxes))

        # Extract raw box coordinates (pixel space) for importance scoring
        # before cropping — importance is computed on the original image geometry.
        image_wh = image.size  # (width, height)

        if len(boxes) == 0:
            # Fallback: treat full image as one crop with maximum importance
            crops          = [image]
            raw_box_coords = [(0.0, 0.0, float(image_wh[0]), float(image_wh[1]))]
        else:
            candidate_boxes = [
                (box, tuple(box.xyxy[0].tolist()))
                for box in boxes
            ]
            supported_boxes = [
                (box, box_coords)
                for box, box_coords in candidate_boxes
                if is_supported_box(box_coords, image_wh)
            ]
            logger.debug(
                "  Geometry filter: kept %s/%s proposal(s)",
                len(supported_boxes),
                len(candidate_boxes),
            )

            if not supported_boxes:
                # Fallback: all YOLO proposals were too small/thin to be useful.
                crops          = [image]
                raw_box_coords = [(0.0, 0.0, float(image_wh[0]), float(image_wh[1]))]
            else:
                crops          = [extract_crop(image, box, target_size=INPUT_SIZE) for box, _ in supported_boxes]
                raw_box_coords = [box_coords for _, box_coords in supported_boxes]

        # ── Step 2 → 5: Per-crop embedding + retrieval ────────────────────────
        per_crop_results  = []
        crop_importances  = []

        for i, (crop, box_coords) in enumerate(zip(crops, raw_box_coords)):
            # — Importance scoring (before embedding, uses only geometry) —
            imp_score, imp_info = score_crop_importance(box_coords, image_wh)
            crop_importances.append(imp_score)

            x1, y1, x2, y2 = box_coords
            logger.debug(
                "  Crop %s bbox=(%.0f,%.0f,%.0f,%.0f)  "
                "importance: center=%s  size=%s  completeness=%s  final=%s",
                i,
                x1,
                y1,
                x2,
                y2,
                imp_info["center_score"],
                imp_info["size_score"],
                imp_info["completeness_score"],
                imp_info["importance"],
            )

            # 2. Letterbox crop already done in extract_crop()
            # 3. Encode with SigLIP
            image_embed = embed_crop(crop).numpy()

            # 4. Coarse routing — pick top-K categories via centroid similarity
            top_categories = coarse_route(
                image_embed,
                taxonomy_indexes.category_centroids,
            )
            logger.debug("  Crop %s: coarse route -> %s", i, top_categories)

            # 5. Fine-grained FAISS retrieval within top categories
            raw_results = fine_retrieve(
                image_embed,
                top_categories,
                taxonomy_indexes.faiss_indexes,
                taxonomy_indexes.id_maps,
            )
            logger.debug(
                "  Crop %s: top candidates -> %s",
                i,
                [(label, f"{score:.3f}") for label, score, _ in raw_results[:3]],
            )

            # 6. Confidence filtering (per crop)
            filtered = normalize_retrieval_results(apply_confidence_filter(raw_results))
            if filtered:
                logger.debug(
                    f"  Crop {i}: confidence PASS — "
                    f"top='{filtered[0][0]}' score={filtered[0][1]:.4f}"
                )
            else:
                top_label = raw_results[0][0] if raw_results else "n/a"
                top_score = raw_results[0][1] if raw_results else 0.0
                logger.debug(
                    f"  Crop {i}: confidence REJECT — "
                    f"top='{top_label}' score={top_score:.4f} (see ConfFilter debug log)"
                )
            # Keep only the strongest interpretation for each crop. Returning
            # top-N alternatives from one crop produced confusing duplicates
            # such as "bar stool", "stool", and "piano stool" for the same box.
            per_crop_results.append(filtered[:1] if filtered else [])

        # ── Step 7: Context re-ranking across all crops ───────────────────────
        # Boosts the dominant co-occurring category across crops
        valid_crops = [r for r in per_crop_results if r]
        valid_importances = [
            crop_importances[i] for i, r in enumerate(per_crop_results) if r
        ]

        logger.debug(
            f"  Rerank input: {len(valid_crops)} valid crop(s) "
            f"of {len(per_crop_results)} total — importances={[round(w,3) for w in valid_importances]}"
        )

        if not valid_crops:
            # All crops rejected by confidence gate
            logger.warning(
                "  All crops rejected by confidence filter — returning UNKNOWN_LABEL. "
                "Check ConfFilter DEBUG logs above for rejection reasons."
            )
            unknown_translated = get_translation(UNKNOWN_LABEL, req.language)
            return {
                "detections": [
                    {
                        "canonical_label": UNKNOWN_LABEL,
                        "translated_label": unknown_translated,
                        "confidence": 0.0,
                        "box": normalize_box(
                            (0.0, 0.0, float(image_wh[0]), float(image_wh[1])),
                            image_wh,
                        ),
                    }
                ],
                "description": f"objects detected: {unknown_translated}",
                "language": req.language,
            }

        reranked = context_rerank(valid_crops, crop_importances=valid_importances)
        logger.debug(
            f"  Rerank output: {len(reranked)} candidate(s) — "
            f"top={[(l, f'{s:.4f}') for l, s, _ in reranked[:3]]}"
        )

        # ── Step 8: Deduplicate → canonical labels ────────────────────────────
        # Tags are METADATA only — not in output.
        # Canonical labels only — never aliases.
        final_labels = deduplicate_labels(reranked, max_labels=MAX_OUTPUT_LABELS)

        detection_metadata = build_detection_metadata(
            final_labels,
            reranked,
            per_crop_results,
            raw_box_coords,
            crop_importances,
            image_wh,
        )
        final_labels = filter_final_labels(final_labels, detection_metadata)

        if not final_labels:
            logger.warning(
                "  Final labels fell below MIN_OUTPUT_CONF=%.3f — returning UNKNOWN_LABEL.",
                MIN_OUTPUT_CONFIDENCE,
            )
            final_labels = [UNKNOWN_LABEL]
            detection_metadata = build_detection_metadata(
                final_labels,
                reranked,
                per_crop_results,
                raw_box_coords,
                crop_importances,
                image_wh,
            )

        logger.debug(f"  FINAL labels (pre-translation): {final_labels}")

        # ── Step 9: Local translation lookup — builds canonical + translated pairs ──
        detections = [
            {
                "canonical_label": label,
                "translated_label": get_translation(label, req.language),
                "confidence": round(float(detection_metadata[label]["confidence"]), 6),
                "box": detection_metadata[label]["box"],
            }
            for label in final_labels
        ]

        translated_labels = [d["translated_label"] for d in detections]
        logger.debug(f"  FINAL detections (lang='{req.language}'): {detections}")

        return {
            "detections":  detections,
            "description": "objects detected: " + ", ".join(translated_labels),
            "language":    req.language,
        }

    except Exception:
        logger.exception("Analysis failed")
        return JSONResponse(
            status_code=500,
            content={"error": "Analysis failed", "detections": [], "description": "", "language": req.language},
        )


# ─────────────────────────────────────────────────────────────────────────────
# Health check
# ─────────────────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {
        "status":         "ok",
        "device":         DEVICE,
        "siglip_model":   SIGLIP_MODEL_ID,
        "yolo_model":     YOLO_MODEL_NAME,
        "total_concepts": taxonomy_indexes.total_concepts,
        "categories":     list(taxonomy_indexes.faiss_indexes.keys()),
    }


# ─────────────────────────────────────────────────────────────────────────────
# Debug / integration test endpoint
# ─────────────────────────────────────────────────────────────────────────────

if os.getenv("DEBUG_ENDPOINTS", "0") == "1":
    @app.get("/debug-test")
    def debug_test():
        """
        Returns a hardcoded response that exactly matches the /analyze schema.

        Use this from Spring Boot (via VisionServiceClient) to verify:
          - the HTTP transport works
          - Jackson can deserialize VisionResponse correctly
          - detections arrive with labels, confidence, and box fields

        If this returns labels=[] in Spring Boot, the bug is 100% in
        VisionResponse DTO / Jackson deserialization, NOT in the ML pipeline.

        If this returns labels=["chair", "vase"], the bug is in /analyze itself.
        """
        return {
            "detections": [
                {
                    "canonical_label": "chair",
                    "translated_label": "chair",
                    "confidence": 0.12,
                    "box": {"x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4},
                },
                {
                    "canonical_label": "vase",
                    "translated_label": "vase",
                    "confidence": 0.09,
                    "box": {"x": 0.55, "y": 0.25, "width": 0.2, "height": 0.35},
                },
            ],
            "description": "objects detected: chair, vase",
            "language": "en",
        }
