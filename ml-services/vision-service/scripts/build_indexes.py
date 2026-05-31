"""
build_indexes.py — Offline FAISS index builder.

Run this script ONCE to precompute SigLIP text embeddings for all taxonomy
concepts and build per-category FAISS indexes.

The SAME model used here MUST be used at inference time.
Model is controlled by --model flag (or SIGLIP_MODEL env var).

Usage:
    # Default (CPU, base model, fast):
    python scripts/build_indexes.py

    # High-quality tier (GPU recommended):
    python scripts/build_indexes.py --model google/siglip-so400m-patch14-384

    # Force CPU even if GPU available:
    python scripts/build_indexes.py --force-cpu

Output (written to indexes/):
    {category}.index      ← FAISS IndexFlatIP per category
    centroids.npy         ← stacked L2-normalized centroid matrix
    centroid_order.json   ← ordered category names for centroid rows
    index_meta.json       ← model_id, id_maps, total_concepts, build timestamp
"""

import argparse
import csv
import json
import os
import sys
import time
from pathlib import Path

import faiss
import numpy as np
import torch
from transformers import AutoModel, AutoProcessor

# ─────────────────────────────────────────────────────────────────────────────
ROOT = Path(__file__).resolve().parents[1]
TAXONOMY_DIR = ROOT / "taxonomy"
INDEXES_DIR  = ROOT / "indexes"
INDEXES_DIR.mkdir(exist_ok=True)

DEFAULT_MODEL = "google/siglip-base-patch16-224"
BATCH_SIZE    = 32

# Text prompt templates for embedding generation
# Embedding = average across all prompts for canonical + all aliases
PROMPT_TEMPLATES = [
    "a photo of {label}",
    "{visual_description}",
    "a clear image of {label}",
]


# ─────────────────────────────────────────────────────────────────────────────
# Device selection
# ─────────────────────────────────────────────────────────────────────────────

def resolve_device(force_cpu: bool = False) -> str:
    if force_cpu or os.getenv("FORCE_CPU", "0") == "1":
        return "cpu"
    enable_cuda = os.getenv("ENABLE_CUDA", "1") == "1"
    if enable_cuda and torch.cuda.is_available():
        return "cuda"
    return "cpu"


# ─────────────────────────────────────────────────────────────────────────────
# Taxonomy loading
# ─────────────────────────────────────────────────────────────────────────────

def load_taxonomy() -> dict[str, list[dict]]:
    """
    Load all taxonomy CSVs from taxonomy/ directory.
    Returns {category: [{canonical_label, aliases, visual_description}, ...]}
    """
    taxonomy: dict[str, list[dict]] = {}
    csv_files = sorted(TAXONOMY_DIR.glob("*.csv"))

    if not csv_files:
        raise FileNotFoundError(
            f"No CSV files in {TAXONOMY_DIR}. Run build_taxonomy.py first."
        )

    for csv_path in csv_files:
        category = csv_path.stem
        concepts = []
        with open(csv_path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                concepts.append({
                    "canonical_label":    row.get("canonical_label", "").strip(),
                    "aliases":            row.get("aliases", "").strip(),
                    "visual_description": row.get("visual_description", "").strip(),
                })
        taxonomy[category] = concepts
        print(f"  Loaded {category}: {len(concepts)} concepts")

    total = sum(len(v) for v in taxonomy.values())
    print(f"  Total: {total} concepts across {len(taxonomy)} categories\n")
    return taxonomy


# ─────────────────────────────────────────────────────────────────────────────
# Text generation
# ─────────────────────────────────────────────────────────────────────────────

def build_text_list(concept: dict) -> list[str]:
    """
    Generate all text prompts for one concept (canonical + aliases).
    Returns deduplicated list of strings to encode.
    """
    canonical     = concept["canonical_label"]
    visual_desc   = concept["visual_description"] or canonical
    aliases_str   = concept["aliases"]

    alias_list = [a.strip() for a in aliases_str.split("|") if a.strip()] if aliases_str else []

    texts: list[str] = []
    for label in [canonical] + alias_list:
        for template in PROMPT_TEMPLATES:
            if "{visual_description}" in template:
                texts.append(visual_desc)
            else:
                texts.append(template.format(label=label))

    # Deduplicate preserving order
    seen = set()
    unique = []
    for t in texts:
        if t not in seen:
            seen.add(t)
            unique.append(t)
    return unique


# ─────────────────────────────────────────────────────────────────────────────
# SigLIP text encoding
# ─────────────────────────────────────────────────────────────────────────────

def encode_texts(
    text_list: list[str],
    model,
    processor,
    device: str,
) -> np.ndarray:
    """
    Encode a list of text strings using SigLIP text encoder.
    Returns L2-normalized embeddings as float32 numpy array [N, dim].
    """
    all_features = []
    for i in range(0, len(text_list), BATCH_SIZE):
        batch = text_list[i: i + BATCH_SIZE]
        inputs = processor(
            text=batch,
            return_tensors="pt",
            padding="max_length",
            truncation=True,
        ).to(device)

        with torch.no_grad():
            out = model.get_text_features(**inputs)
            # get_text_features returns a plain tensor for SiglipModel
            if isinstance(out, torch.Tensor):
                features = out
            else:
                # Fallback: BaseModelOutputWithPooling → use pooler_output
                features = out.pooler_output
            features = features / (features.norm(dim=-1, keepdim=True) + 1e-8)

        all_features.append(features.cpu().float().numpy())

    return np.concatenate(all_features, axis=0)


def average_embed(embeddings: np.ndarray) -> np.ndarray:
    """Average multiple embeddings and re-normalize."""
    avg = embeddings.mean(axis=0)
    norm = np.linalg.norm(avg)
    if norm > 0:
        avg = avg / norm
    return avg.astype(np.float32)


# ─────────────────────────────────────────────────────────────────────────────
# Index building
# ─────────────────────────────────────────────────────────────────────────────

def build_category_index(
    concepts: list[dict],
    model,
    processor,
    device: str,
) -> tuple[faiss.Index, list[str], np.ndarray]:
    """
    Build one FAISS IndexFlatIP for a single category.

    Returns:
        index:    faiss.IndexFlatIP (normalized embeddings → cosine via IP)
        labels:   [canonical_label, ...] — position i maps to labels[i]
        centroid: L2-normalized mean embedding for the category
    """
    # SiglipModel.get_text_features outputs projection_dim (not hidden_size)
    embed_dim = getattr(model.config, "projection_dim",
                        getattr(model.config.text_config, "hidden_size", 768))
    index     = faiss.IndexFlatIP(embed_dim)
    labels:   list[str]           = []
    all_embeds: list[np.ndarray]  = []

    for concept in concepts:
        canonical = concept["canonical_label"]
        if not canonical:
            continue

        texts  = build_text_list(concept)
        embeds = encode_texts(texts, model, processor, device)  # [N, dim]
        avg    = average_embed(embeds)                           # [dim]

        index.add(avg.reshape(1, -1))
        labels.append(canonical)
        all_embeds.append(avg)

    centroid = average_embed(np.stack(all_embeds)) if all_embeds else np.zeros(embed_dim, dtype=np.float32)
    return index, labels, centroid


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main(model_id: str, force_cpu: bool) -> None:
    device = resolve_device(force_cpu)
    print(f"\nbuild_indexes.py")
    print(f"  Model:   {model_id}")
    print(f"  Device:  {device}")
    print(f"  Output:  {INDEXES_DIR}\n")

    # ── Load taxonomy ─────────────────────────────────────────────────────────
    print("Loading taxonomy...")
    taxonomy = load_taxonomy()

    # ── Load SigLIP model ────────────────────────────────────────────────────
    print(f"Loading {model_id}...")
    processor = AutoProcessor.from_pretrained(model_id)
    model     = AutoModel.from_pretrained(model_id).to(device)
    model.eval()
    print(f"  Model loaded on {device}\n")

    # ── Build indexes ─────────────────────────────────────────────────────────
    id_maps:          dict[str, list[str]] = {}
    centroid_matrix:  list[np.ndarray]     = []
    centroid_order:   list[str]            = []
    total_concepts    = 0

    for category, concepts in sorted(taxonomy.items()):
        t0 = time.time()
        print(f"  Building {category} ({len(concepts)} concepts)...")

        index, labels, centroid = build_category_index(concepts, model, processor, device)

        # Save FAISS index
        index_path = INDEXES_DIR / f"{category}.index"
        faiss.write_index(index, str(index_path))

        id_maps[category]    = labels
        centroid_matrix.append(centroid)
        centroid_order.append(category)
        total_concepts      += len(labels)

        print(f"    -> {len(labels)} vectors  ({time.time()-t0:.1f}s)")

    # ── Save centroids ────────────────────────────────────────────────────────
    centroid_arr = np.stack(centroid_matrix)  # [num_categories, dim]
    np.save(str(INDEXES_DIR / "centroids.npy"), centroid_arr)

    with open(INDEXES_DIR / "centroid_order.json", "w", encoding="utf-8") as f:
        json.dump(centroid_order, f, indent=2)

    # ── Save metadata ─────────────────────────────────────────────────────────
    meta = {
        "model_id":       model_id,
        "device_used":    device,
        "total_concepts": total_concepts,
        "categories":     centroid_order,
        "id_maps":        id_maps,
        "build_time_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    with open(INDEXES_DIR / "index_meta.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)

    print(f"\n[DONE] Built {total_concepts} concept vectors across {len(taxonomy)} categories")
    print(f"  Indexes written to: {INDEXES_DIR}")
    print(f"  IMPORTANT: Use model '{model_id}' at inference time.")
    print(f"  Set SIGLIP_MODEL={model_id} in your deployment environment.\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Build FAISS taxonomy indexes offline")
    parser.add_argument(
        "--model",
        default=os.getenv("SIGLIP_MODEL", DEFAULT_MODEL),
        help=f"SigLIP model ID (default: {DEFAULT_MODEL})",
    )
    parser.add_argument(
        "--force-cpu",
        action="store_true",
        help="Force CPU even if GPU available",
    )
    args = parser.parse_args()
    main(model_id=args.model, force_cpu=args.force_cpu)
