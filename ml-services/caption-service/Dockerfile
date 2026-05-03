FROM python:3.10-slim

WORKDIR /app

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1

# -----------------------------
# System dependencies
# -----------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

# -----------------------------
# Upgrade pip (important for dependency resolution stability)
# -----------------------------
RUN pip install --upgrade pip

# -----------------------------
# Core ML stack (PINNED - prevents ABI mismatch)
# -----------------------------
RUN pip install --no-cache-dir \
    numpy==1.26.4 \
    torch==2.1.2+cpu torchvision==0.16.2+cpu --index-url https://download.pytorch.org/whl/cpu

# Disable GPU dependency chain
ENV THINC_NO_CUDA=1 \
    CUPY_DISABLE=1

# Uninstall cupy to completely prevent CUDA usage
RUN pip uninstall -y cupy cupy-cuda11x cupy-cuda12x || true

# Install spacy and thinc
# Note: --no-deps is omitted because spacy relies on subdependencies (like requests/packaging)
# to correctly resolve its version string. Without them, `spacy download` fails with HTTP 404.
RUN pip install --no-cache-dir \
    spacy==3.7.4 \
    thinc==8.2.3

# -----------------------------
# App dependencies
# -----------------------------
COPY requirements.txt .

# Install requirements (torch has been removed to prevent pulling CUDA version)
RUN pip install --no-cache-dir -r requirements.txt

# -----------------------------
# Download spaCy model AFTER install is stable
# -----------------------------
RUN python -m spacy download en_core_web_sm

# -----------------------------
# Pre-download BLIP model (cached into image)
# -----------------------------
RUN python -c "from transformers import BlipProcessor, BlipForConditionalGeneration; \
    BlipProcessor.from_pretrained('Salesforce/blip-image-captioning-base'); \
    BlipForConditionalGeneration.from_pretrained('Salesforce/blip-image-captioning-base')"

# -----------------------------
# Copy application code
# -----------------------------
COPY main.py .

# -----------------------------
# Expose service port
# -----------------------------
EXPOSE 8000

# -----------------------------
# Start server
# -----------------------------
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]