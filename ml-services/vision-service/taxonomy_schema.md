# Taxonomy Schema Documentation

## Overview

This is the **educational visual vocabulary taxonomy** for the Locale visual learning app.  
It is derived from LVIS vocabulary (used as word source only — no model training).  
Designed for compatibility with **SigLIP text embeddings** + **FAISS semantic retrieval**.

---

## File Structure

```
vision-service/
  taxonomy/
    categories.json          ← L1/L2 category index with metadata
    furniture.csv            ← 40 concepts
    electronics.csv          ← 40 concepts
    kitchenware.csv          ← 49 concepts
    clothing.csv             ← 59 concepts
    office.csv               ← 35 concepts
    bathroom.csv             ← 35 concepts
    tools.csv                ← 40 concepts
    household.csv            ← 41 concepts
    food.csv                 ← 69 concepts
    outdoor.csv              ← 35 concepts
  indexes/                   ← reserved: FAISS indexes (Phase 2/3)
  cache/                     ← reserved: SigLIP embedding cache (Phase 2/3)
  scripts/
    build_taxonomy.py        ← main generator — run to regenerate all files
    concept_registry.py      ← unified concept data importer
    concept_registry_a.py    ← concept data: furniture, electronics, kitchenware, clothing, office
    concept_registry_b.py    ← concept data: bathroom, tools, household, food, outdoor
    normalize_labels.py      ← LVIS canonicalization engine
    cluster_synonyms.py      ← percentile-based synonym clustering
    validate_taxonomy.py     ← schema + format + dedup validation
```

---

## CSV Schema

Every taxonomy CSV contains exactly these 7 columns:

| Column | Type | Rules | Example |
|---|---|---|---|
| `concept_id` | UUID v5 string | Stable, derived from canonical label, never changes | `3f2e1a...` |
| `canonical_label` | string | Lowercase, one per concept, globally unique | `office chair` |
| `aliases` | pipe-separated string | Synonyms, never displayed directly | `desk chair\|ergonomic chair` |
| `parent` | string | Canonical label of parent concept, or category name | `chair` |
| `category` | string | Top-level category (L2 name) | `furniture` |
| `tags` | pipe-separated string | Cross-domain metadata — **NOT routing logic** | `seating\|office` |
| `visual_description` | short noun phrase | Max 8 words, no sentences, SigLIP-optimized | `rolling office chair with adjustable height` |

---

## Concept ID Strategy

```python
import uuid
TAXONOMY_NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
concept_id = str(uuid.uuid5(TAXONOMY_NAMESPACE, canonical_label.strip().lower()))
```

- **Deterministic**: same canonical label always produces the same UUID
- **Stable**: safe to use as a foreign key in any database
- **Language-agnostic**: English canonical label is the internal key
- **Collision-free**: UUID v5 in a fixed namespace

---

## Two-Layer Hierarchy

```
L1 (Domain)          L2 (Category)      Concept
physical_objects  →  furniture      →   office chair
physical_objects  →  electronics    →   smartphone
physical_objects  →  apparel        →   sneaker
consumables       →  food           →   avocado
```

`categories.json` stores the L1/L2 mapping for each category.

---

## Tags Rule (CRITICAL)

> **Tags are METADATA ONLY.**  
> They must NOT be used as primary routing logic.  
> Routing in Phase 1 is determined SOLELY by the L1/L2 hierarchy.

Tags exist to support future cross-domain queries (e.g. "find all 'portable' objects").  
They must never replace category-based routing decisions.

---

## Visual Description Rules

```
✅ CORRECT (noun phrase, ≤ 8 words, no sentence):
  "rolling office chair with adjustable height"
  "ceramic mug with handle"
  "slim portable computer with keyboard"

❌ WRONG (sentence structure, too long, starts with article):
  "A chair that is used in offices for computing"
  "This is a laptop computer used for work and study"
```

**Validator** (`validate_taxonomy.py`) enforces these rules automatically.

---

## Phased Rollout Plan

| Phase | Concepts | Trigger |
|---|---|---|
| **Phase 1 (current)** | 600–700 | Manual precision review complete |
| Phase 2 | ~3,000 | Phase 1 retrieval stable in production |
| Phase 3 | 10,000+ | Multilingual expansion validated |

> **Rule**: Prefer 500 clean concepts over 700 noisy ones.  
> CLIP/SigLIP accuracy depends on embedding separation quality, not label count.

---

## Clustering Threshold Strategy

`cluster_synonyms.py` uses **percentile-based thresholds per category** (not fixed cosine values):

| Zone | Rule | Action |
|---|---|---|
| Top 5th percentile | Highest pairwise similarity | Auto-merge as aliases |
| 5th–15th percentile | Medium similarity | Write to `review_required.csv` |
| Below 15th percentile | Low similarity | Keep as distinct concepts |

Why percentile-based: absolute cosine thresholds drift with embedding model version and normalization method. Percentile thresholds are robust across model changes.

---

## Multilingual Expansion (Future)

The `concept_id` UUID is the stable multilingual key.  
Future translation tables will look like:

```json
{
  "concept_id": "3f2e1a...",
  "canonical_label": "office chair",
  "translations": {
    "vi": "ghế văn phòng",
    "ja": "オフィスチェア",
    "ko": "사무용 의자",
    "zh": "办公椅"
  }
}
```

No changes to the CSV schema are needed for multilingual support.

---

## How to Regenerate

```powershell
cd vision-service/scripts

# Regenerate all taxonomy files
python build_taxonomy.py

# Validate schema, duplicates, format
python validate_taxonomy.py

# Check for synonym clusters (review_required.csv)
python cluster_synonyms.py --review-only
```

---

## LVIS Source Notes

LVIS 1,203 categories were used **exclusively as a vocabulary word source**.  
No LVIS training data, annotations, or model configs were used.  
The resulting taxonomy is a new educational ontology, not a detection class list.

Exclusions from LVIS:
- Weapons (`machine gun`, `rifle`, `pistol`)
- People (`person`, `man`, `woman`, `child`)
- Animals (Phase 1 scope exclusion — future category)
- Abstract concepts (`award`, `garbage`, `coloring material`)
- Historical/fantasy (`mammoth`, `gargoyle`, `chain mail`)
