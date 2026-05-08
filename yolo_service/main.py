import io
import os

from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from ultralytics import YOLO


MODEL_NAME = os.getenv("YOLO_MODEL", "yolov8n.pt")

app = FastAPI(title="W19 YOLO Object Detection Service")
model = YOLO(MODEL_NAME)


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/detect")
async def detect(image: UploadFile = File(...)):
    if image.content_type and not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Uploaded file must be an image")

    contents = await image.read()
    if not contents:
        raise HTTPException(status_code=400, detail="Uploaded image is empty")

    try:
        frame = Image.open(io.BytesIO(contents)).convert("RGB")
    except UnidentifiedImageError as exc:
        raise HTTPException(status_code=400, detail="Uploaded file is not a readable image") from exc

    image_width, image_height = frame.size
    results = model(frame)
    detections = []

    for box in results[0].boxes:
        label = model.names[int(box.cls)]
        confidence = float(box.conf)
        x1, y1, x2, y2 = box.xyxy[0].tolist()
        detections.append({
            "label": label,
            "confidence": confidence,
            "box": {
                "x": max(0.0, min(1.0, x1 / image_width)),
                "y": max(0.0, min(1.0, y1 / image_height)),
                "width": max(0.0, min(1.0, (x2 - x1) / image_width)),
                "height": max(0.0, min(1.0, (y2 - y1) / image_height)),
            },
        })

    return detections
