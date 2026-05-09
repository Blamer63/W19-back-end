FROM python:3.10

WORKDIR /app

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1

# --------------------------
# System deps (safe baseline)
# --------------------------
RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# --------------------------
# Upgrade pip
# --------------------------
RUN pip install --upgrade pip

# --------------------------
# Install ALL deps in ONE SHOT (prevents conflicts)
# --------------------------
RUN pip install --no-cache-dir \
    numpy==1.26.4 \
    torch==2.1.2 \
    torchvision==0.16.2 \
    ultralytics==8.1.0 \
    transformers==4.38.2 \
    fastapi==0.103.1 \
    uvicorn==0.23.2 \
    pillow==10.0.0

# --------------------------
# VERIFY (THIS MUST PASS)
# --------------------------
RUN python -c "import numpy, torch; print('NUMPY OK:', numpy.__version__)"

# --------------------------
# PRELOAD MODELS
# --------------------------
RUN python -c "from ultralytics import YOLO; YOLO('yolov8x.pt')"

RUN python -c "from transformers import CLIPProcessor, CLIPModel; \
    CLIPProcessor.from_pretrained('openai/clip-vit-large-patch14'); \
    CLIPModel.from_pretrained('openai/clip-vit-large-patch14')"

COPY main.py .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]