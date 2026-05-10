FROM python:3.10

WORKDIR /app

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1 \
    SIGLIP_MODEL=google/siglip-base-patch16-224 \
    YOLO_MODEL=yolov8m.pt \
    ENABLE_CUDA=0 \
    FORCE_CPU=0

# ── System dependencies ───────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# ── Python packages ───────────────────────────────────────────────────────────
RUN pip install --upgrade pip

RUN pip install --no-cache-dir \
    numpy==1.26.4 \
    torch==2.1.2 \
    torchvision==0.16.2 \
    ultralytics==8.1.0 \
    transformers==4.38.2 \
    fastapi==0.103.1 \
    uvicorn==0.23.2 \
    pillow==10.0.0 \
    faiss-cpu==1.13.2 \
    sentencepiece \
    protobuf

# ── Verify core imports ───────────────────────────────────────────────────────
RUN python -c "import numpy, torch, faiss; print('Core deps OK — numpy:', numpy.__version__, 'faiss:', faiss.__version__)"

# ── Preload YOLOv8m (proposals only — lighter than yolov8x) ──────────────────
RUN python -c "from ultralytics import YOLO; YOLO('yolov8m.pt'); print('YOLOv8m OK')"

# ── Preload SigLIP base model (default tier, CPU-friendly) ───────────────────
RUN python -c "\
from transformers import AutoProcessor, AutoModel; \
AutoProcessor.from_pretrained('google/siglip-base-patch16-224'); \
AutoModel.from_pretrained('google/siglip-base-patch16-224'); \
print('SigLIP base OK')"

# ── Copy taxonomy data (pre-built by scripts/build_taxonomy.py) ───────────────
COPY taxonomy/ ./taxonomy/

# ── Copy application modules ──────────────────────────────────────────────────
COPY scripts/ ./scripts/
COPY image_utils.py .
COPY retrieval.py .
COPY indexer.py .
COPY main.py .

# ── Build FAISS indexes offline (Option C — pre-built in image) ───────────────
# Uses siglip-base-patch16-224 (matches SIGLIP_MODEL env default above).
# If you change SIGLIP_MODEL, rebuild with the matching model.
RUN python scripts/build_indexes.py \
    --model google/siglip-base-patch16-224 \
    --force-cpu

# ── Verify indexes were built ─────────────────────────────────────────────────
RUN python -c "\
from pathlib import Path; \
import json; \
meta = json.loads((Path('indexes/index_meta.json')).read_text()); \
print('Indexes OK —', meta['total_concepts'], 'concepts,', len(meta['categories']), 'categories')"

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]