"""
retrieval.py — Hierarchical coarse-to-fine vocabulary retrieval engine.

Pipeline:
  coarse_route()         → top-K categories (O(num_categories), in-memory)
  fine_retrieve()        → top-N concepts per category via FAISS
  apply_confidence()     → filter or return "unknown object"
  score_crop_importance() → soft positional weight per crop
  context_rerank()       → importance-weighted + category-boosted aggregation

IMPORTANT — Tags rule:
  Tags in the taxonomy are METADATA ONLY.
  Routing in Phase 1 is determined SOLELY by category centroids (L2 hierarchy).
  Tags are NOT used in any routing logic here.
"""

import math
import os
from collections import Counter
from typing import Any

import numpy as np

# ─────────────────────────────────────────────────────────────────────────────
# Config (overrideable via env vars)
# ─────────────────────────────────────────────────────────────────────────────
PRIMARY_THRESHOLD  = float(os.getenv("CONF_PRIMARY",   "0.05"))
# AMBIGUITY_RATIO: top1/top2 must exceed this to accept the top result.
# 1.06 was too tight — SigLIP cosine scores for correct matches often differ
# by only 2-4%, causing valid detections to be silently rejected.
# 1.02 (2% margin) keeps meaningful ambiguity protection while allowing
# legitimate close-but-correct top results through.
AMBIGUITY_RATIO    = float(os.getenv("CONF_AMBIGUITY",  "1.02"))
COARSE_TOP_K       = int(os.getenv("COARSE_TOP_K",      "3"))
FINE_TOP_K         = int(os.getenv("FINE_TOP_K",        "5"))
CONTEXT_BOOST      = float(os.getenv("CONTEXT_BOOST",   "1.15"))
UNKNOWN_LABEL      = "unknown object"


# ─────────────────────────────────────────────────────────────────────────────
# Step 1 — Coarse Routing
# ─────────────────────────────────────────────────────────────────────────────

def coarse_route(
    image_embed: np.ndarray,
    category_centroids: dict[str, np.ndarray],
    top_k: int = COARSE_TOP_K,
) -> list[str]:
    """
    Compare image embedding against in-memory category centroids.

    Returns the top_k category names ordered by cosine similarity.
    Both image_embed and centroids must be L2-normalized (dot product = cosine).

    Time complexity: O(num_categories) — negligible at any scale.
    """
    if not category_centroids:
        return []

    scores: dict[str, float] = {}
    for cat, centroid in category_centroids.items():
        scores[cat] = float(np.dot(image_embed, centroid))

    return sorted(scores, key=lambda c: scores[c], reverse=True)[:top_k]


# ─────────────────────────────────────────────────────────────────────────────
# Step 2 — Fine-Grained FAISS Retrieval
# ─────────────────────────────────────────────────────────────────────────────

def fine_retrieve(
    image_embed: np.ndarray,
    selected_categories: list[str],
    faiss_indexes: dict[str, Any],       # category → faiss.Index
    id_maps: dict[str, list[str]],        # category → [canonical_label, ...]
    top_k: int = FINE_TOP_K,
) -> list[tuple[str, float, str]]:
    """
    Search per-category FAISS indexes for the top-k nearest concept embeddings.

    Returns list of (canonical_label, score, category) sorted by score desc.

    Why per-category (not one flat index):
      - Eliminates cross-category nearest-neighbor pollution
      - 10× smaller search space per category
      - Enables category-level confidence to reinforce coarse route
    """
    results: list[tuple[str, float, str]] = []

    q = image_embed.reshape(1, -1).astype(np.float32)

    for category in selected_categories:
        if category not in faiss_indexes:
            continue

        index = faiss_indexes[category]
        labels = id_maps[category]

        if index.ntotal == 0:
            continue

        k = min(top_k, index.ntotal)
        scores, indices = index.search(q, k)

        for score, idx in zip(scores[0], indices[0]):
            if idx < 0:
                continue
            canonical = labels[idx]
            results.append((canonical, float(score), category))

    return sorted(results, key=lambda x: x[1], reverse=True)


# ─────────────────────────────────────────────────────────────────────────────
# Step 3 — Confidence Filtering
# ─────────────────────────────────────────────────────────────────────────────

import logging as _logging
_conf_log = _logging.getLogger(__name__)

def apply_confidence_filter(
    results: list[tuple[str, float, str]],
    primary_threshold: float = PRIMARY_THRESHOLD,
    ambiguity_ratio: float = AMBIGUITY_RATIO,
) -> list[tuple[str, float, str]]:
    """
    Filter results using two gates:

    Gate 1 — Primary threshold:
      If best score < primary_threshold → abstain ("unknown object" upstream).
      PRIMARY_THRESHOLD=0.05 is intentionally conservative for SigLIP cosine
      scores, which typically sit in the 0.07–0.20 range on CPU for
      base-patch16-224. Raise via env CONF_PRIMARY if you need stricter gating.

    Gate 2 — Ambiguity check:
      If top1_score / top2_score < ambiguity_ratio → too ambiguous → abstain.
      AMBIGUITY_RATIO=1.02 means top candidate must beat second by at least 2%.
      This prevents coin-flip labels while allowing close-but-correct results.

    Returns filtered list (may be empty → caller should output UNKNOWN_LABEL).
    Logs the rejection reason at DEBUG level for diagnostics.
    """
    if not results:
        _conf_log.debug("  [ConfFilter] REJECT — empty results list")
        return []

    best_label, best_score, best_cat = results[0]

    # Gate 1 — primary threshold
    if best_score < primary_threshold:
        _conf_log.debug(
            f"  [ConfFilter] REJECT Gate1 — best '{best_label}' "
            f"score={best_score:.4f} < threshold={primary_threshold:.4f}"
        )
        return []

    # Gate 2 — ambiguity check
    if len(results) > 1:
        second_label, second_score, _ = results[1]
        if second_score > 0:
            ratio = best_score / second_score
            if ratio < ambiguity_ratio:
                _conf_log.debug(
                    f"  [ConfFilter] REJECT Gate2 — ambiguous: "
                    f"'{best_label}'({best_score:.4f}) vs "
                    f"'{second_label}'({second_score:.4f}) "
                    f"ratio={ratio:.4f} < required={ambiguity_ratio:.4f}"
                )
                return []

    _conf_log.debug(
        f"  [ConfFilter] PASS — best '{best_label}' score={best_score:.4f} "
        f"(threshold={primary_threshold:.4f})"
    )
    return results


# ─────────────────────────────────────────────────────────────────────────────
# Step 3.5 — Crop Importance Scoring
# ─────────────────────────────────────────────────────────────────────────────

# Weights for importance formula (must sum to 1.0)
_W_CENTER       = float(os.getenv("IMP_W_CENTER",       "0.45"))
_W_SIZE         = float(os.getenv("IMP_W_SIZE",         "0.35"))
_W_COMPLETENESS = float(os.getenv("IMP_W_COMPLETENESS", "0.20"))
_EDGE_MARGIN    = float(os.getenv("IMP_EDGE_MARGIN",    "0.02"))  # 2% = "touching edge"


def score_crop_importance(
    box_xyxy: tuple[float, float, float, float],
    image_size: tuple[int, int],
) -> tuple[float, dict]:
    """
    Compute soft importance score for one YOLO crop.

    formula:
      importance = 0.45 * center_score
                 + 0.35 * size_score
                 + 0.20 * completeness_score

    center_score:
      1 - normalized Euclidean distance(bbox_center, image_center).
      Max distance = corner-to-center diagonal (normalizer).

    size_score:
      bbox_area / image_area, clamped to [0, 1].
      Large central objects naturally dominate small background ones.

    completeness_score:
      Penalizes objects whose edges touch/cross the image boundary.
      Objects fully inside the frame → near 1.0.
      Objects clipped by any edge → lower score.

    Args:
        box_xyxy:   (x1, y1, x2, y2) in pixel coordinates
        image_size: (width, height) of the full image

    Returns:
        (importance, components_dict) where components_dict contains
        center_score, size_score, completeness_score, importance for logging.
    """
    x1, y1, x2, y2 = box_xyxy
    img_w, img_h = float(image_size[0]), float(image_size[1])

    if img_w <= 0 or img_h <= 0:
        return 1.0, {"center_score": 1.0, "size_score": 1.0,
                     "completeness_score": 1.0, "importance": 1.0}

    # ── center_score ──────────────────────────────────────────────────────────
    bbox_cx = (x1 + x2) / 2.0
    bbox_cy = (y1 + y2) / 2.0
    img_cx  = img_w / 2.0
    img_cy  = img_h / 2.0

    # Max possible distance: image corner to center
    max_dist = math.sqrt(img_cx ** 2 + img_cy ** 2)
    dist     = math.sqrt((bbox_cx - img_cx) ** 2 + (bbox_cy - img_cy) ** 2)
    norm_dist   = dist / max_dist if max_dist > 0 else 0.0
    center_score = max(0.0, 1.0 - norm_dist)

    # ── size_score ────────────────────────────────────────────────────────────
    bbox_area = max(0.0, (x2 - x1) * (y2 - y1))
    img_area  = img_w * img_h
    size_score = min(1.0, bbox_area / img_area) if img_area > 0 else 0.0

    # ── completeness_score ────────────────────────────────────────────────────
    # Measure how much each edge intrudes into the boundary margin zone.
    # Penalty per edge = fraction of the margin zone that is inside the crop.
    m_x = _EDGE_MARGIN * img_w   # absolute margin in pixels (x-axis)
    m_y = _EDGE_MARGIN * img_h   # absolute margin in pixels (y-axis)

    left_clip   = max(0.0, (m_x - x1)        / m_x) if m_x > 0 else 0.0
    top_clip    = max(0.0, (m_y - y1)        / m_y) if m_y > 0 else 0.0
    right_clip  = max(0.0, (x2 - (img_w - m_x)) / m_x) if m_x > 0 else 0.0
    bottom_clip = max(0.0, (y2 - (img_h - m_y)) / m_y) if m_y > 0 else 0.0

    # Sum of clip fractions; each capped at 1.0, total capped at 0 → 1 penalty
    total_clip        = left_clip + top_clip + right_clip + bottom_clip
    completeness_score = max(0.0, 1.0 - total_clip / 4.0)

    # ── weighted combination ──────────────────────────────────────────────────
    importance = (
        _W_CENTER       * center_score
        + _W_SIZE         * size_score
        + _W_COMPLETENESS * completeness_score
    )

    components = {
        "center_score":       round(center_score,       3),
        "size_score":         round(size_score,          3),
        "completeness_score": round(completeness_score,  3),
        "importance":         round(importance,          3),
    }
    return importance, components


# ─────────────────────────────────────────────────────────────────────────────
# Step 4 — Context Re-ranking (across crops)
# ─────────────────────────────────────────────────────────────────────────────

def context_rerank(
    per_crop_results: list[list[tuple[str, float, str]]],
    crop_importances: list[float] | None = None,
    boost: float = CONTEXT_BOOST,
) -> list[tuple[str, float, str]]:
    """
    Re-rank all crop results using crop importance weights and scene co-occurrence.

    Two-stage scoring:
      Stage 1 — Importance weighting:
        Each crop's retrieval scores are multiplied by that crop's importance.
        importance = 0.45*center + 0.35*size + 0.20*completeness
        This causes central, large, complete objects to outrank small
        background objects even if background has higher raw cosine similarity.

      Stage 2 — Category co-occurrence boost:
        Count dominant category across all weighted results.
        Apply CONTEXT_BOOST multiplier to candidates from dominant category.
        Suppresses semantic neighbors that leaked across category boundaries.

    crop_importances: list of float scores [0, 1], one per crop in per_crop_results.
                      If None or mismatched, all crops default to importance=1.0.
    Does NOT modify labels — only reorders final scores.
    """
    if not per_crop_results:
        return []

    # Normalize importance list length to match crops
    n = len(per_crop_results)
    if crop_importances is None or len(crop_importances) != n:
        importances = [1.0] * n
    else:
        importances = [max(0.0, float(w)) for w in crop_importances]

    # ── Stage 1: Apply importance weighting ──────────────────────────────────
    # Scale each crop's retrieval scores by that crop's importance.
    # This is a soft weight — non-central crops are NOT discarded, just downweighted.
    all_results: list[tuple[str, float, str]] = []
    for crop_results, imp in zip(per_crop_results, importances):
        for label, score, category in crop_results:
            all_results.append((label, score * imp, category))

    if not all_results:
        return []

    # ── Stage 2: Category co-occurrence boost ────────────────────────────────
    category_votes = Counter(cat for _, _, cat in all_results)
    if not category_votes:
        return sorted(all_results, key=lambda x: x[1], reverse=True)

    dominant_category = category_votes.most_common(1)[0][0]

    def boosted_score(result: tuple[str, float, str]) -> float:
        label, score, category = result
        return score * (boost if category == dominant_category else 1.0)

    return sorted(all_results, key=boosted_score, reverse=True)


# ─────────────────────────────────────────────────────────────────────────────
# Utility: deduplicate labels preserving order
# ─────────────────────────────────────────────────────────────────────────────

def deduplicate_labels(results: list[tuple[str, float, str]], max_labels: int = 5) -> list[str]:
    """
    Extract unique canonical labels from results in score order.
    Skips duplicates (same label from different categories).
    """
    seen: set[str] = set()
    labels: list[str] = []
    for label, _, _ in results:
        if label not in seen:
            seen.add(label)
            labels.append(label)
        if len(labels) >= max_labels:
            break
    return labels
