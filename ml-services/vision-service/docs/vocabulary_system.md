# Vocabulary System Architecture & Extensibility Guide

This document explains the end-to-end vocabulary pipeline for the Vision Scanner and provides strict, step-by-step instructions for expanding the taxonomy.

## 1. How the Current System Works

The Vision Scanner operates on a multi-stage retrieval architecture designed for high precision and stability:

**Pipeline Flow:**
`Image` → `YOLO region proposals` → `SigLIP embedding` → `Coarse category routing` → `FAISS fine retrieval` → `Canonical English label` → `Local translation lookup` → `Final multilingual response`

### Core Concepts

*   **Concept**: A unique, real-world object class in our taxonomy (e.g., a specific type of chair or fruit).
*   **Canonical Label**: The permanent, English-centric identifier for a concept (e.g., `armchair`). It is the absolute source of truth for the system.
*   **Translation Independence**: Translations are entirely decoupled from the retrieval engine. The FAISS indexes and embeddings ONLY know about the English canonical labels. This ensures retrieval accuracy remains stable regardless of the target user's language.

## 2. Taxonomy Expansion Guide

Follow these exact steps when adding new vocabulary concepts to the system.

### STEP 1: Add Concept to Taxonomy CSV
Determine the appropriate category and add a new row to the corresponding CSV file in `taxonomy/*.csv`.

**Example (`taxonomy/furniture.csv`):**
```csv
concept_id,canonical_label,aliases,parent,category,tags,visual_description
f5a91b2c-...,gaming chair,gamer chair|ergonomic gaming chair,chair,furniture,seating|office|gaming,black gaming chair with headrest
```
*   `concept_id`: A deterministic UUIDv5 derived from the canonical label. Do not generate this manually; use `scripts/build_taxonomy.py` so IDs remain stable across rebuilds.
*   `canonical_label`: The primary English label. Must be lowercase and exact.
*   `aliases`: Pipe-separated synonyms (e.g., `gamer chair`) used to improve retrieval robustness. These are *never* returned to the user.
*   `visual_description`: Detailed prompt-like description used to generate the text embedding.

### STEP 2: Add Translations
Add the exact `canonical_label` as a key to ALL language translation files in `taxonomy/translations/`.

*   `en.json`: `{"gaming chair": "gaming chair"}`
*   `es.json`: `{"gaming chair": "silla de gaming"}`
*   `fr.json`: `{"gaming chair": "chaise de jeu"}`
*   `ja.json`: `{"gaming chair": "ゲーミングチェア"}`

*Note: Translation files are for runtime presentation only. Adding translations does NOT affect embeddings or retrieval logic.*

### STEP 3: Rebuild Embeddings & FAISS Indexes
New concepts are NOT searchable until the offline FAISS indexes are rebuilt. Run the builder script from the `vision-service` directory:

```bash
python scripts/build_indexes.py
```
This script will parse the updated CSVs, embed the new descriptions using SigLIP, and reconstruct the `.index` files in the `indexes/` directory.

### STEP 4: Restart Service
Restart the Docker container or local server so it can load the newly built indexes and updated translation dictionaries into memory.

---

## 3. Important Architectural Rules

Adhere to these rules strictly to prevent systemic regressions:

> [!IMPORTANT]
> **RULE:** `canonical_label` is the permanent identity of a concept.
> **RULE:** Translations are presentation-layer only.
> **RULE:** Aliases improve retrieval quality but are NEVER returned to users.
> **RULE:** Changing canonical labels requires rebuilding indexes.
> **RULE:** Adding translations does NOT require rebuilding indexes.
> **RULE:** Tags are metadata only, not routing logic.
> **RULE:** The retrieval system is English-centric internally.

---

## 4. Future Scaling Guidance

The system is designed for massive but safe scalability.

*   **Adding Languages is Cheap:** Because translations are decoupled, you can add 10 new languages without recalculating a single embedding. Just drop in a new `.json` file and the system adapts instantly.
*   **Adding Concepts is Expensive:** Every new concept requires embedding generation and index rebuilding.
*   **The Bottleneck:** Embeddings and FAISS indexes are the real scalability bottleneck. As we move to Phase 2 (3,000+ concepts), rebuilding indexes will take longer, necessitating CI/CD pipeline integration for the `build_indexes.py` step.
*   **Multilingual Expansion:** In the future, we may allow dynamic translation delivery via CDN, but the core retrieval engine will remain purely English.

---

## 5. Common Mistakes

> [!WARNING]
> Watch out for these frequent errors when extending the vocabulary system:

*   **Forgetting to rebuild indexes:** Adding a row to a CSV does *nothing* until you run the build script. The system will silently ignore the new concept.
*   **Mismatched Translation Keys:** If a translation key does not perfectly match the `canonical_label`, the system will fall back to English.
*   **Changing Canonical Labels without Rebuilding:** This causes a fatal mismatch between the in-memory maps and the offline index.
*   **Adding Aliases as Separate Concepts:** Do not create a new UUID for "sofa" if "couch" already exists. Add it as an alias.
*   **Using Translated Words as Retrieval Labels:** Do not put Spanish or French words into the `canonical_label` column of the CSV.
*   **Using Realtime Machine Translation:** Never wire up an LLM or external translation API to the scanner. Responses must remain deterministic and fast.

---

## 6. Example Full Workflow

Here is a complete worked example of adding "gaming chair".

**1. Update `taxonomy/furniture.csv`:**
```csv
uuid...,gaming chair,gamer chair|ergonomic gaming chair,chair,furniture,seating|office|gaming,black gaming chair with headrest
```

**2. Update Translations:**
*   `en.json`: Add `"gaming chair": "gaming chair"`
*   `es.json`: Add `"gaming chair": "silla de gaming"`
*   `fr.json`: Add `"gaming chair": "chaise de jeu"`
*   `ja.json`: Add `"gaming chair": "ゲーミングチェア"`

**3. Rebuild Indexes:**
```bash
python scripts/build_indexes.py
```

**4. Restart Service & Test API:**
```http
POST /analyze
{
    "image": "<base64_data>",
    "language": "es"
}

Response:
{
    "language": "es",
    "description": "objects detected: silla de gaming",
    "detections": [
        {
            "canonical_label": "gaming chair",
            "translated_label": "silla de gaming",
            "confidence": 0.12,
            "box": {
                "x": 0.15,
                "y": 0.10,
                "width": 0.62,
                "height": 0.88
            }
        }
    ]
}
```
