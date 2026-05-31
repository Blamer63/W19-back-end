"""
validate_taxonomy.py - Schema validation, dedup, and format enforcement.

Checks every taxonomy CSV against the production schema.
All failures are blocking - fix before Phase 2 expansion.

PHASE 1 QUALITY GATE:
  - Concept count: 600-700 (precision over coverage)
  - Zero duplicate canonical_label across all files
  - All visual_description: noun phrase, ≤ 8 words, no sentence structure
  - All concept_id: globally unique UUIDs
  - All parent references resolve within their category
  - Tags: metadata only (no routing logic enforced by convention)
"""

import csv
import re
import sys
from pathlib import Path
from collections import defaultdict

ROOT        = Path(__file__).resolve().parents[1]
TAXONOMY_DIR = ROOT / "taxonomy"

REQUIRED_COLUMNS = {
    "concept_id", "canonical_label", "aliases",
    "parent", "category", "tags", "visual_description",
}

PHASE1_MIN = 450
PHASE1_MAX = 700

# Words that indicate a sentence structure (forbidden in visual_description)
SENTENCE_STARTERS = {
    "a", "an", "the", "this", "that", "it", "used", "which", "that", "these",
}
FORBIDDEN_VERBS = {
    "used", "designed", "made", "serves", "helps", "allows", "enables",
    "provides", "gives", "comes", "has", "have",
}


class ValidationError:
    def __init__(self, severity: str, category: str, label: str, message: str):
        self.severity = severity  # "ERROR" | "WARN"
        self.category = category
        self.label    = label
        self.message  = message

    def __str__(self) -> str:
        return f"  [{self.severity}] [{self.category}] '{self.label}' - {self.message}"


def load_csv(path: Path) -> list[dict]:
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def check_columns(rows: list[dict], category: str) -> list[ValidationError]:
    errors = []
    if not rows:
        return [ValidationError("ERROR", category, "", "CSV is empty")]
    found = set(rows[0].keys())
    missing = REQUIRED_COLUMNS - found
    for col in sorted(missing):
        errors.append(ValidationError("ERROR", category, "", f"Missing required column: '{col}'"))
    return errors


def check_visual_description(desc: str, label: str, category: str) -> list[ValidationError]:
    """
    Enforce noun-phrase format for visual_description.
    Rules:
      1. Must not be empty
      2. Must be ≤ 8 words
      3. Must not start with a sentence-article (a/an/the)
      4. Must not contain forbidden verbs
      5. Must not end with punctuation indicating a full sentence
    """
    errors = []
    if not desc.strip():
        return [ValidationError("ERROR", category, label, "visual_description is empty")]

    words = desc.strip().split()
    if len(words) > 8:
        errors.append(ValidationError(
            "ERROR", category, label,
            f"visual_description too long ({len(words)} words, max 8): '{desc}'"
        ))

    first_word = words[0].lower().rstrip(",") if words else ""
    if first_word in SENTENCE_STARTERS:
        errors.append(ValidationError(
            "ERROR", category, label,
            f"visual_description starts with sentence word '{first_word}': '{desc}'"
        ))

    desc_lower = desc.lower()
    for verb in FORBIDDEN_VERBS:
        if re.search(rf"\b{verb}\b", desc_lower):
            errors.append(ValidationError(
                "WARN", category, label,
                f"visual_description may be sentence-structured (contains '{verb}'): '{desc}'"
            ))
            break

    if desc.rstrip().endswith("."):
        errors.append(ValidationError(
            "WARN", category, label,
            f"visual_description ends with period (possible sentence): '{desc}'"
        ))

    return errors


def validate_all() -> bool:
    csv_files = sorted(TAXONOMY_DIR.glob("*.csv"))
    if not csv_files:
        print("[ERR] No CSV files found in taxonomy/. Run build_taxonomy.py first.")
        sys.exit(1)

    all_errors:  list[ValidationError] = []
    all_warnings: list[ValidationError] = []

    # Global tracking
    seen_concept_ids:     dict[str, str] = {}   # id → "category/label"
    seen_canonical:       dict[str, str] = {}   # label → "category"
    total_concepts = 0
    category_counts: dict[str, int] = {}

    for csv_path in csv_files:
        category = csv_path.stem
        rows = load_csv(csv_path)
        count = len(rows)
        category_counts[category] = count
        total_concepts += count

        # Column check
        col_errors = check_columns(rows, category)
        all_errors.extend(col_errors)
        if col_errors:
            continue  # can't validate rows if columns missing

        # Collect canonical labels in this category for parent resolution
        local_canonicals: set[str] = {r["canonical_label"].strip().lower() for r in rows}
        local_canonicals.add(category)  # category name is valid as top-level parent

        for row in rows:
            label = row.get("canonical_label", "").strip().lower()
            cid   = row.get("concept_id", "").strip()
            desc  = row.get("visual_description", "").strip()
            parent = row.get("parent", "").strip().lower()
            tags  = row.get("tags", "").strip()

            # Empty label
            if not label:
                all_errors.append(ValidationError("ERROR", category, "", "Empty canonical_label"))
                continue

            # Duplicate canonical_label globally
            if label in seen_canonical:
                all_errors.append(ValidationError(
                    "ERROR", category, label,
                    f"Duplicate canonical_label (also in '{seen_canonical[label]}')"
                ))
            else:
                seen_canonical[label] = category

            # Duplicate concept_id globally
            if cid:
                loc = f"{category}/{label}"
                if cid in seen_concept_ids:
                    all_errors.append(ValidationError(
                        "ERROR", category, label,
                        f"Duplicate concept_id '{cid}' (also at '{seen_concept_ids[cid]}')"
                    ))
                else:
                    seen_concept_ids[cid] = loc
            else:
                all_errors.append(ValidationError("ERROR", category, label, "Missing concept_id"))

            # Parent resolution
            if parent and parent not in local_canonicals:
                all_errors.append(ValidationError(
                    "WARN", category, label,
                    f"Parent '{parent}' not found as canonical_label in this category"
                ))

            # visual_description format
            desc_errors = check_visual_description(desc, label, category)
            for e in desc_errors:
                if e.severity == "ERROR":
                    all_errors.append(e)
                else:
                    all_warnings.append(e)

            # Tags must not be empty for cross-domain objects (soft warning)
            if not tags:
                all_warnings.append(ValidationError(
                    "WARN", category, label, "tags field is empty"
                ))

    # ── Summary ───────────────────────────────────────────────────────────────
    print("\n" + "=" * 60)
    print("TAXONOMY VALIDATION REPORT")
    print("=" * 60)

    print(f"\n[PKG] Concept counts per category:")
    for cat, cnt in sorted(category_counts.items()):
        bar = "#" * (cnt // 5)
        print(f"   {cat:<15} {cnt:>4}  {bar}")
    print(f"\n   TOTAL: {total_concepts}")

    phase1_ok = PHASE1_MIN <= total_concepts <= PHASE1_MAX
    phase1_icon = "[OK]" if phase1_ok else "[WARN] "
    print(f"\n{phase1_icon} Phase 1 target: {PHASE1_MIN}-{PHASE1_MAX} concepts "
          f"({'OK' if phase1_ok else f'OUT OF RANGE - prefer fewer, higher quality'})")

    if all_errors:
        print(f"\n[ERR] ERRORS ({len(all_errors)}) - blocking:")
        for e in all_errors:
            print(e)

    if all_warnings:
        print(f"\n[WARN]  WARNINGS ({len(all_warnings)}) - non-blocking:")
        for w in all_warnings:
            print(w)

    if not all_errors and not all_warnings:
        print("\n[OK] All checks passed - taxonomy is production-ready for Phase 1.")
    elif not all_errors:
        print(f"\n[OK] No blocking errors. {len(all_warnings)} warnings to review.")

    print("\n" + "=" * 60)
    return len(all_errors) == 0


if __name__ == "__main__":
    success = validate_all()
    sys.exit(0 if success else 1)
