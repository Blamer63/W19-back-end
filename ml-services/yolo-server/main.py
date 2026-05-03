from fastapi import FastAPI
from pydantic import BaseModel
from ultralytics import YOLO
from PIL import Image
import base64, io, os
import sys
import math

app = FastAPI()

MODEL_PATH = os.getenv("YOLO_MODEL", "best.pt")
if not os.path.exists(MODEL_PATH) and MODEL_PATH == "best.pt":
    print(f"WARNING: {MODEL_PATH} not found. Falling back to yolov8m.pt for now.")
    MODEL_PATH = "yolov8m.pt"

print(f"Loading YOLO model from {MODEL_PATH}...")
try:
    model = YOLO(MODEL_PATH)
    # Warmup inference
    print("Running warmup inference...")
    dummy_img = Image.new('RGB', (640, 640), color='white')
    model.predict(dummy_img, verbose=False)
    print("Warmup complete.")
except Exception as e:
    print(f"Failed to load or warmup model: {e}")
    sys.exit(1)

class DetectRequest(BaseModel):
    image_base64: str
    confidence_threshold: float = 0.25
    max_results: int = 50

@app.post("/detect")
def detect(req: DetectRequest):
    # Validate payload size (~2MB limit for base64 is ~2.67MB)
    if len(req.image_base64) > 2_800_000:
        return {"error": "Payload too large", "detections": []}

    try:
        image_bytes = base64.b64decode(req.image_base64)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        print(f"[DEBUG] Image loaded. Shape: {image.size}, Format: {image.format}")
        # Log min/max to ensure it's valid
        extrema = image.getextrema()
        print(f"[DEBUG] Image RGB Extrema: {extrema}")
    except Exception as e:
        return {"error": f"Invalid image data: {str(e)}", "detections": []}

    try:
        # Tuning parameters: conf=req.confidence_threshold, iou=0.45, imgsz=640, max_det=50
        results = model.predict(
            image, 
            conf=req.confidence_threshold, 
            iou=0.45, 
            imgsz=640, 
            max_det=50, 
            verbose=False
        )
        boxes = results[0].boxes
        print(f"[DEBUG] Raw YOLO detections count: {len(boxes)}")

        width, height = image.size
        detections = []
        for b in boxes:
            cls_id = int(b.cls[0].item())
            label = model.names[cls_id]
            confidence = float(b.conf[0].item())
            
            # Extract bounding box (xyxy)
            x1, y1, x2, y2 = b.xyxy[0].tolist()
            
            # Adaptive padding based on object area vs image area
            obj_w, obj_h = x2 - x1, y2 - y1
            obj_area = obj_w * obj_h
            img_area = width * height
            area_ratio = obj_area / img_area if img_area > 0 else 0
            
            # If object is small (<10%), 25% padding. If large (>50%), 5% padding.
            pad_ratio = 0.25 - (min(max(area_ratio - 0.1, 0), 0.4) * 0.5)
            pad_w = obj_w * pad_ratio
            pad_h = obj_h * pad_ratio
            
            x1_pad = max(0, int(x1 - pad_w))
            y1_pad = max(0, int(y1 - pad_h))
            x2_pad = min(width, int(x2 + pad_w))
            y2_pad = min(height, int(y2 + pad_h))
            
            # Validate crop dimensions
            if x2_pad <= x1_pad or y2_pad <= y1_pad:
                continue
                
            # Crop image
            crop = image.crop((x1_pad, y1_pad, x2_pad, y2_pad))
            
            # Letterbox to 224x224
            target_size = 224
            scale = min(target_size / crop.width, target_size / crop.height)
            new_w, new_h = int(crop.width * scale), int(crop.height * scale)
            resized = crop.resize((new_w, new_h), Image.Resampling.LANCZOS)
            
            letterboxed = Image.new("RGB", (target_size, target_size), (0, 0, 0))
            offset_x = (target_size - new_w) // 2
            offset_y = (target_size - new_h) // 2
            letterboxed.paste(resized, (offset_x, offset_y))
            
            # Convert crop to base64
            buffered = io.BytesIO()
            letterboxed.save(buffered, format="JPEG", quality=85)
            crop_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")

            detections.append({
                "bbox": [x1, y1, x2, y2],
                "yolo_label": label, 
                "yolo_confidence": confidence,
                "crop_base64": crop_base64
            })

        # Sort by confidence
        detections.sort(key=lambda x: x["yolo_confidence"], reverse=True)
        detections = detections[:req.max_results]
        
        print(f"[DEBUG] Final filtered detections count: {len(detections)}")

        return {"detections": detections}
    except Exception as e:
        return {"error": f"Inference failed: {str(e)}", "detections": []}