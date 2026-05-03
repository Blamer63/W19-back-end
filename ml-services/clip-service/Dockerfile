FROM python:3.10-slim

WORKDIR /app

# Install system dependencies (no opencv needed for CLIP usually, but good practice for image handling)
RUN apt-get update && apt-get install -y \
    && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY . .

# Install dependencies (CPU optimized to save space)
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir fastapi uvicorn pillow transformers

EXPOSE 8002

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8002"]
