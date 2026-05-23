"""
cluster_synonyms.py — Percentile-based synonym clustering.

Uses sentence-transformers as a lightweight proxy for SigLIP to
identify near-duplicate concepts BEFORE taxonomy finalization.

IMPORTANT — Tags/routing rule:
  Tags in the taxonomy are METADATA ONLY.
  Routing in Phase 1 is determined SOLELY by L1/L2 hierarchy.
  This script must NOT generate routing logic.

Threshold strategy (percentile-based, NOT fixed cosine values):
  Top  5th percentile similarity  → auto-merge as aliases
  5th–15th percentile             → write to review_required.csv
  Below 15th percentile           → kept as distinct concepts

Why percentile-based?
  Absolute cosine thresholds drift with embedding model version,
  prompt template, and normalization method.
  Percentile-relative thresholds are stable across model changes.
"""

import sys
import csv
import json
import argparse
import warnings
from pathlib import Path
from itertools import combinations
from collections import defaultdict

# ─────────────────────────────────────────────────────────────────────────────
ROOT = Path(__file__).resolve().parents[1]
TAXONOMY_DIR = ROOT / "taxonomy"
SCRIPTS_DIR = ROOT / "scripts"
REVIEW_FILE = SCRIPTS_DIR / "review_required.csv"
MERGE_FILE  = SCRIPTS_DIR / "merged_concepts.json"

AUTO_MERGE_PERCENTILE  = 5   # top 5%  → auto alias
REVIEW_PERCENTILE      = 15  # top 15% → human review zone


def load_all_canonical_labels() -> dict[str, list[str]]:
    """
    Load canonical labels from all category CSVs.
    Returns {category: [canonical_label, ...]}
    """
    labels: dict[str, list[str]] = {}
    csv_files = list(TAXONOMY_DIR.glob("*.csv"))
    if not csv_files:
        print("[WARN] No CSV files found in taxonomy/. Run build_taxonomy.py first.")
        return {}

    for csv_path in csv_files:
        category = csv_path.stem
        labels[category] = []
        with open(csv_path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                lbl = row.get("canonical_label", "").strip()
                if lbl:
                    labels[category].append(lbl)
    return labels


def encode_labels(label_list: list[str]) -> "np.ndarray":
    """
    Encode labels using sentence-transformers (SigLIP proxy).
    Falls back to a simple character-level hash if model unavailable.
    """
    try:
        from sentence_transformers import SentenceTransformer
        model = SentenceTransformer("all-MiniLM-L6-v2", device="cpu")
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            embeddings = model.encode(label_list, normalize_embeddings=True, show_progress_bar=False)
        return embeddings
    except ImportError:
        print("[WARN] sentence-transformers not installed. Using character-hash fallback.")
        import numpy as np
        vecs = []
        for lbl in label_list:
            vec = [0.0] * 64
            for i, ch in enumerate(lbl):
                vec[i % 64] += ord(ch) / 1000.0
            norm = sum(x * x for x in vec) ** 0.5 or 1.0
            vecs.append([x / norm for x in vec])
        return np.array(vecs)


def cosine_similarity_matrix(embeddings: "np.ndarray") -> "np.ndarray":
    """Compute pairwise cosine similarity (embeddings already L2-normalized)."""
    import numpy as np
    return embeddings @ embeddings.T


def percentile_thresholds(sim_values: list[float]) -> tuple[float, float]:
    """
    Compute auto-merge and review thresholds from the percentile distribution
    of all pairwise similarities within a category.

    Returns (auto_merge_threshold, review_threshold).
    """
    if not sim_values:
        return 0.95, 0.90

    import numpy as np
    arr = np.array(sim_values)
    # Top AUTO_MERGE_PERCENTILE% → highest similarity scores
    auto_thresh  = float(np.percentile(arr, 100 - AUTO_MERGE_PERCENTILE))
    review_thresh = float(np.percentile(arr, 100 - REVIEW_PERCENTILE))
    return auto_thresh, review_thresh


def cluster_category(
    category: str,
    labels: list[str],
    review_only: bool = False,
) -> tuple[list[dict], list[dict]]:
    """
    Cluster synonyms within a single category.

    Returns:
        auto_merges  — list of {canonical, aliases, similarity}
        review_pairs — list of {label_a, label_b, similarity, category}
    """
    import numpy as np

    if len(labels) < 2:
        return [], []

    embeddings = encode_labels(labels)
    sim_matrix = cosine_similarity_matrix(embeddings)

    # Collect all off-diagonal upper-triangle similarities
    n = len(labels)
    pair_sims: list[tuple[float, int, int]] = []
    for i, j in combinations(range(n), 2):
        sim = float(sim_matrix[i, j])
        pair_sims.append((sim, i, j))

    sim_values = [s for s, _, _ in pair_sims]
    auto_thresh, review_thresh = percentile_thresholds(sim_values)

    auto_merges: list[dict]  = []
    review_pairs: list[dict] = []

    # Union-Find for clustering
    parent = list(range(n))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(x: int, y: int) -> None:
        parent[find(x)] = find(y)

    for sim, i, j in sorted(pair_sims, reverse=True):
        if sim >= auto_thresh:
            union(i, j)
            auto_merges.append({
                "category":   category,
                "canonical":  labels[min(i, j)],
                "alias":      labels[max(i, j)],
                "similarity": round(sim, 4),
                "threshold":  round(auto_thresh, 4),
            })
        elif sim >= review_thresh:
            review_pairs.append({
                "category":   category,
                "label_a":    labels[i],
                "label_b":    labels[j],
                "similarity": round(sim, 4),
                "review_threshold": round(review_thresh, 4),
                "auto_threshold":   round(auto_thresh, 4),
            })

    return auto_merges, review_pairs


def run(review_only: bool = False) -> None:
    """Main entry point."""
    import numpy as np

    all_labels = load_all_canonical_labels()
    if not all_labels:
        print("No labels found. Exiting.")
        sys.exit(1)

    all_auto:   list[dict] = []
    all_review: list[dict] = []

    for category, labels in sorted(all_labels.items()):
        print(f"  Clustering '{category}' ({len(labels)} labels)...")
        auto, review = cluster_category(category, labels, review_only=review_only)
        all_auto.extend(auto)
        all_review.extend(review)

    # Write auto-merge suggestions
    if not review_only:
        MERGE_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(MERGE_FILE, "w", encoding="utf-8") as f:
            json.dump(all_auto, f, indent=2)
        print(f"\n✅ Auto-merge candidates written → {MERGE_FILE}")
        print(f"   {len(all_auto)} pairs found (top {AUTO_MERGE_PERCENTILE}th percentile)")

    # Write review file
    REVIEW_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(REVIEW_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "category", "label_a", "label_b",
            "similarity", "review_threshold", "auto_threshold",
        ])
        writer.writeheader()
        writer.writerows(all_review)

    print(f"📋 Review required → {REVIEW_FILE}")
    print(f"   {len(all_review)} pairs in the {AUTO_MERGE_PERCENTILE}–{REVIEW_PERCENTILE}th percentile zone")

    # Quality gate
    if len(all_review) > 20:
        print(f"\n⚠️  WARNING: {len(all_review)} pairs need review (threshold: 20)")
        print("   Consider tightening synonyms before Phase 2 expansion.")
    else:
        print(f"\n✅ Review list within quality gate (≤ 20 pairs)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Percentile-based synonym clustering")
    parser.add_argument("--review-only", action="store_true",
                        help="Only write review_required.csv, skip auto-merge output")
    args = parser.parse_args()

    print("cluster_synonyms.py — percentile-based synonym detection")
    print(f"  Auto-merge threshold : top {AUTO_MERGE_PERCENTILE}th percentile per category")
    print(f"  Review zone          : {AUTO_MERGE_PERCENTILE}th–{REVIEW_PERCENTILE}th percentile per category")
    print(f"  Routing rule         : Tags are METADATA ONLY. L1/L2 hierarchy is sole routing authority.\n")

    run(review_only=args.review_only)
