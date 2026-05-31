"""
indexer.py — Startup FAISS index loader.

Loads pre-built FAISS indexes and metadata from indexes/.
Indexes are built ONCE offline by scripts/build_indexes.py.
This module ONLY loads — it never builds.

If indexes are missing, raises a clear error with instructions.
"""

import json
from pathlib import Path
from typing import Any

import faiss
import numpy as np

INDEXES_DIR = Path(__file__).resolve().parent / "indexes"
META_FILE   = INDEXES_DIR / "index_meta.json"


class TaxonomyIndexes:
    """
    Container for all preloaded FAISS indexes and lookup tables.

    Attributes:
        faiss_indexes:       {category: faiss.Index}          (for FAISS search)
        id_maps:             {category: [canonical_label, ...]}(position → label)
        category_centroids:  {category: np.ndarray}            (for coarse routing)
        model_id:            SigLIP model used to build these indexes
        total_concepts:      Total concept count across all categories
    """

    def __init__(self):
        self.faiss_indexes:      dict[str, Any]           = {}
        self.id_maps:            dict[str, list[str]]      = {}
        self.category_centroids: dict[str, np.ndarray]     = {}
        self.model_id:           str                       = ""
        self.total_concepts:     int                       = 0

    def __repr__(self) -> str:
        cats = list(self.faiss_indexes.keys())
        return (
            f"TaxonomyIndexes("
            f"model={self.model_id!r}, "
            f"categories={len(cats)}, "
            f"concepts={self.total_concepts})"
        )


def load_indexes(indexes_dir: Path = INDEXES_DIR) -> TaxonomyIndexes:
    """
    Load all FAISS indexes, id maps, and category centroids from disk.

    Expected files:
        indexes/index_meta.json         ← metadata: model_id, categories, id_maps
        indexes/{category}.index        ← one FAISS IndexFlatIP per category
        indexes/centroids.npy           ← stacked centroid matrix
        indexes/centroid_order.json     ← ordered list of category names for matrix rows

    Raises:
        FileNotFoundError if indexes have not been built yet.
        ValueError if model_id in metadata doesn't match runtime SIGLIP_MODEL.
    """
    meta_path = indexes_dir / "index_meta.json"

    if not meta_path.exists():
        raise FileNotFoundError(
            f"\n\n[INDEXER] index_meta.json not found at {meta_path}\n"
            f"Run offline index builder first:\n"
            f"  python scripts/build_indexes.py\n"
            f"Then restart the service.\n"
        )

    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)

    result = TaxonomyIndexes()
    result.model_id = meta.get("model_id", "unknown")
    result.total_concepts = meta.get("total_concepts", 0)

    # ── Load per-category FAISS indexes ──────────────────────────────────────
    id_maps_raw: dict[str, list[str]] = meta.get("id_maps", {})

    for category, labels in id_maps_raw.items():
        index_path = indexes_dir / f"{category}.index"
        if not index_path.exists():
            print(f"  [WARN] {category}.index not found — skipping category")
            continue

        index = faiss.read_index(str(index_path))
        result.faiss_indexes[category] = index
        result.id_maps[category]       = labels

        print(f"  [OK] Loaded {category}.index  ({index.ntotal} vectors)")

    # ── Load category centroids ───────────────────────────────────────────────
    centroid_matrix_path = indexes_dir / "centroids.npy"
    centroid_order_path  = indexes_dir / "centroid_order.json"

    if centroid_matrix_path.exists() and centroid_order_path.exists():
        centroid_matrix = np.load(str(centroid_matrix_path))   # [num_cats, dim]
        with open(centroid_order_path, encoding="utf-8") as f:
            centroid_order = json.load(f)

        for i, cat in enumerate(centroid_order):
            if i < len(centroid_matrix):
                result.category_centroids[cat] = centroid_matrix[i]

        print(f"  [OK] Loaded {len(result.category_centroids)} category centroids")
    else:
        print("  [WARN] centroids.npy not found — coarse routing disabled")

    print(f"\n  TaxonomyIndexes ready: {result}")
    return result


def verify_model_compatibility(indexes: TaxonomyIndexes, runtime_model_id: str) -> None:
    """
    Fail if the model used to build indexes differs from the runtime model.
    Embeddings from different models are incompatible.
    """
    if indexes.model_id and indexes.model_id != runtime_model_id:
        raise ValueError(
            f"Model mismatch: indexes built with '{indexes.model_id}', "
            f"runtime is '{runtime_model_id}'. "
            f"Rebuild: python scripts/build_indexes.py --model {runtime_model_id}"
        )
