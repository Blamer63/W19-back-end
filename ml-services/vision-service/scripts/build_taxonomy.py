"""
build_taxonomy.py — Main taxonomy generator.
Reads concept_registry.py, writes all CSV files + categories.json.
Run: python scripts/build_taxonomy.py
"""

import csv
import json
import uuid
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TAXONOMY_DIR = ROOT / "taxonomy"
TAXONOMY_DIR.mkdir(exist_ok=True)
(ROOT / "indexes").mkdir(exist_ok=True)
(ROOT / "cache").mkdir(exist_ok=True)

# Stable project namespace — never change this
TAXONOMY_NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

CATEGORIES_META = {
    "furniture": {
        "l1_domain": "physical_objects",
        "l2_type": "furniture",
        "description": "Indoor furniture, seating, beds, and storage",
        "cross_tags": ["seating", "surfaces", "storage"],
        "vocab_file": "taxonomy/furniture.csv",
        "phase": 1,
    },
    "electronics": {
        "l1_domain": "physical_objects",
        "l2_type": "devices",
        "description": "Consumer electronics and personal devices",
        "cross_tags": ["wearable", "portable", "appliance"],
        "vocab_file": "taxonomy/electronics.csv",
        "phase": 1,
    },
    "kitchenware": {
        "l1_domain": "physical_objects",
        "l2_type": "kitchenware",
        "description": "Cookware, utensils, drinkware, and kitchen appliances",
        "cross_tags": ["appliance", "food_prep", "drinkware"],
        "vocab_file": "taxonomy/kitchenware.csv",
        "phase": 1,
    },
    "clothing": {
        "l1_domain": "physical_objects",
        "l2_type": "apparel",
        "description": "Garments, footwear, headwear, and personal accessories",
        "cross_tags": ["wearable", "footwear", "accessories"],
        "vocab_file": "taxonomy/clothing.csv",
        "phase": 1,
    },
    "office": {
        "l1_domain": "physical_objects",
        "l2_type": "stationery",
        "description": "Office supplies, stationery, and desk equipment",
        "cross_tags": ["stationery", "work", "paper"],
        "vocab_file": "taxonomy/office.csv",
        "phase": 1,
    },
    "bathroom": {
        "l1_domain": "physical_objects",
        "l2_type": "hygiene",
        "description": "Bathroom fixtures and personal hygiene products",
        "cross_tags": ["hygiene", "grooming", "fixtures"],
        "vocab_file": "taxonomy/bathroom.csv",
        "phase": 1,
    },
    "tools": {
        "l1_domain": "physical_objects",
        "l2_type": "tools",
        "description": "Hand tools, power tools, garden and cleaning tools",
        "cross_tags": ["garden", "cleaning", "hardware"],
        "vocab_file": "taxonomy/tools.csv",
        "phase": 1,
    },
    "household": {
        "l1_domain": "physical_objects",
        "l2_type": "household",
        "description": "Home decor, lighting, climate, and linen objects",
        "cross_tags": ["lighting", "climate", "decor"],
        "vocab_file": "taxonomy/household.csv",
        "phase": 1,
    },
    "food": {
        "l1_domain": "consumables",
        "l2_type": "food",
        "description": "Fruits, vegetables, prepared foods, and drinks",
        "cross_tags": ["fruit", "vegetable", "drink", "snack"],
        "vocab_file": "taxonomy/food.csv",
        "phase": 1,
    },
    "outdoor": {
        "l1_domain": "physical_objects",
        "l2_type": "outdoor",
        "description": "Vehicles, street objects, and outdoor sports equipment",
        "cross_tags": ["vehicle", "street", "sports"],
        "vocab_file": "taxonomy/outdoor.csv",
        "phase": 1,
    },
}

CSV_COLUMNS = [
    "concept_id", "canonical_label", "aliases",
    "parent", "category", "tags", "visual_description",
]


def make_concept_id(canonical_label: str) -> str:
    """UUID v5 derived from canonical label — stable across re-generations."""
    return str(uuid.uuid5(TAXONOMY_NAMESPACE, canonical_label.strip().lower()))


def build_concept(raw: tuple, category: str) -> dict:
    """Convert a raw concept tuple to a full concept dict."""
    canonical, aliases, parent, tags, visual_desc = raw
    return {
        "concept_id":         make_concept_id(canonical),
        "canonical_label":    canonical,
        "aliases":            aliases,
        "parent":             parent,
        "category":           category,
        "tags":               tags,
        "visual_description": visual_desc,
    }


def write_csv(category: str, concepts: list[dict]) -> None:
    path = TAXONOMY_DIR / f"{category}.csv"
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerows(concepts)
    print(f"  [OK] {category}.csv - {len(concepts)} concepts")


def write_categories_json(category_counts: dict[str, int]) -> None:
    output = {}
    for cat, meta in CATEGORIES_META.items():
        output[cat] = {
            **meta,
            "concept_count_phase1": category_counts.get(cat, 0),
        }
    path = TAXONOMY_DIR / "categories.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\n  [OK] categories.json written")


def main():
    # Import concept registry (data file)
    sys.path.insert(0, str(ROOT / "scripts"))
    from concept_registry import CONCEPTS

    print("build_taxonomy.py — Phase 1 generator")
    print(f"Output: {TAXONOMY_DIR}")
    print("=" * 55)

    category_counts: dict[str, int] = {}
    total = 0

    for category, raw_list in CONCEPTS.items():
        if category not in CATEGORIES_META:
            print(f"  [WARN] Unknown category '{category}' — skipping")
            continue

        concepts = [build_concept(raw, category) for raw in raw_list]
        write_csv(category, concepts)
        category_counts[category] = len(concepts)
        total += len(concepts)

    write_categories_json(category_counts)

    print(f"\n{'=' * 55}")
    print(f"  TOTAL concepts generated: {total}")
    if 500 <= total <= 700:
        print(f"  [OK] Within Phase 1 precision target (500-700)")
    else:
        print(f"  [WARN] Outside Phase 1 target - prefer fewer, higher quality concepts")
    print(f"\n  Reserved dirs created: indexes/ cache/")
    print(f"\nNext step: python scripts/validate_taxonomy.py")


if __name__ == "__main__":
    main()
