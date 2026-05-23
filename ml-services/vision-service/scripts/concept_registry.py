"""
concept_registry.py — Unified Phase 1 concept registry.
Merges concept_registry_a.py and concept_registry_b.py into CONCEPTS dict.
"""
from concept_registry_a import CONCEPTS_A
from concept_registry_b import CONCEPTS_B

CONCEPTS: dict[str, list[tuple]] = {**CONCEPTS_A, **CONCEPTS_B}

if __name__ == "__main__":
    total = sum(len(v) for v in CONCEPTS.values())
    print(f"Concept registry loaded: {total} concepts across {len(CONCEPTS)} categories")
    for cat, items in sorted(CONCEPTS.items()):
        print(f"  {cat:<15} {len(items):>4} concepts")
