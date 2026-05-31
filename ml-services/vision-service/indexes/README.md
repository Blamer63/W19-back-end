# indexes/
# Reserved for Phase 2/3: FAISS index files per L1 domain.
# Do NOT store any files here until retrieval pipeline is implemented.
#
# Expected future structure:
#   indexes/furniture.index
#   indexes/electronics.index
#   indexes/food.index
#   ... (one per category)
#
# Index format: FAISS FlatIP (inner product on L2-normalized SigLIP embeddings)
# Partitioning: one index per L2 category for coarse-to-fine routing.
