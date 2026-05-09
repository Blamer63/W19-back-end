import math
import base64
import io
from fastapi import FastAPI
from pydantic import BaseModel
from PIL import Image
import torch
from transformers import CLIPProcessor, CLIPModel
from ultralytics import YOLO

app = FastAPI()

device = "cpu"
print(f"Loading Google Lens-style vision service on {device}...")

# 1. Load YOLO Model
yolo_model = YOLO("yolov8x.pt")

# 2. Load CLIP Model
clip_model_id = "openai/clip-vit-large-patch14"
clip_processor = CLIPProcessor.from_pretrained(clip_model_id)
clip_model = CLIPModel.from_pretrained(clip_model_id).to(device)
clip_model.eval()

# Deterministic Label Map with synonyms
LABEL_MAP = {
    "cell phone": "phone",
    "mobile phone": "phone",
    "smartphone": "phone",
    "tv": "television",
    "couch": "sofa",
    "cup": "coffee mug",
    "mug": "coffee mug",
    "bottle": "water bottle",
    "potted plant": "plant",
    "houseplant": "plant",
    "red apple": "apple",
    "green apple": "apple",
    "fruit": "apple",
    "desk chair": "chair",
    "office chair": "chair",
    "dining table": "table",
    "coffee table": "table",
    "laptop computer": "laptop",
    "macbook": "laptop"
}

VOCABULARY = [
    # People & body
    "person", "man", "woman", "child", "baby",
    # Animals
    "dog", "cat", "horse", "cow", "sheep", "goat", "pig",
    "bird", "chicken", "duck", "fish", "rabbit",
    # Transportation
    "car", "bus", "truck", "motorcycle", "bicycle", "train", "airplane", "boat", "scooter",
    # Electronics
    "phone", "cell phone", "mobile phone", "smartphone", "laptop", "laptop computer", "macbook", 
    "tablet", "keyboard", "mouse", "monitor", "tv", "television", "remote", "speaker",
    "headphones", "charger", "camera",
    # Furniture
    "chair", "desk chair", "office chair", "sofa", "couch", "table", "dining table", "coffee table", "desk",
    "bed", "bookshelf", "cabinet", "drawer", "stool",
    # Kitchen
    "cup", "mug", "coffee mug", "glass", "bottle", "water bottle",
    "plate", "bowl", "fork", "knife", "spoon",
    "pan", "pot", "fridge", "oven", "microwave",
    # Clothing
    "shirt", "tshirt", "jacket", "coat", "hoodie",
    "pants", "jeans", "shorts", "skirt", "dress",
    "shoe", "sneaker", "boot", "sandals", "hat",
    # Personal items
    "bag", "backpack", "handbag", "wallet", "watch",
    "glasses", "sunglasses", "umbrella",
    # Home objects
    "door", "window", "mirror", "lamp", "light",
    "clock", "fan", "air conditioner", "heater",
    "curtain", "pillow", "blanket", "potted plant", "houseplant", "plant",
    # Office / school
    "book", "notebook", "paper", "pen", "pencil",
    "scissors", "stapler", "folder",
    # Food
    "apple", "red apple", "green apple", "fruit", "banana", "orange", "pizza", "burger",
    "sandwich", "cake", "bread", "egg", "rice",
    # Outdoor / environment
    "tree", "flower", "grass", "road",
    "building", "house", "wall", "floor", "ceiling",
    "street", "sign", "traffic light", "bench",
    # Sports
    "ball", "tennis racket", "baseball bat", "skateboard",
    # Misc common objects
    "box", "container", "cupboard", "trash can",
    "can", "toy", "remote control"
]

CLIP_PROMPTS = [
    "a photo of a {}",
    "a close-up photo of a {}",
    "a clear image of a {}",
    "the object is a {}"
]

# Pre-compute CLIP text features with Prompt Ensembling
with torch.no_grad():
    text_inputs = []
    for word in VOCABULARY:
        for prompt in CLIP_PROMPTS:
            text_inputs.append(prompt.format(word))
            
    # Process in batches to avoid memory issues
    batch_size = 128
    all_features = []
    for i in range(0, len(text_inputs), batch_size):
        batch_text = text_inputs[i:i+batch_size]
        clip_text_tokens = clip_processor(text=batch_text, return_tensors="pt", padding=True).to(device)
        features = clip_model.get_text_features(**clip_text_tokens)
        all_features.append(features)
        
    clip_text_features = torch.cat(all_features, dim=0)
    clip_text_features /= clip_text_features.norm(dim=-1, keepdim=True)
    
    # Reshape to [len(VOCABULARY), len(CLIP_PROMPTS), feature_dim]
    clip_text_features = clip_text_features.view(len(VOCABULARY), len(CLIP_PROMPTS), -1)
    
    # Average across prompts
    clip_text_features = clip_text_features.mean(dim=1)
    clip_text_features /= clip_text_features.norm(dim=-1, keepdim=True)

def crop_image(image: Image.Image, box) -> Image.Image:
    width, height = image.size
    x1, y1, x2, y2 = box.xyxy[0].tolist()
    
    # Expand 20%
    w = x2 - x1
    h = y2 - y1
    pad_x = w * 0.20
    pad_y = h * 0.20
    
    nx1 = max(0, x1 - pad_x)
    ny1 = max(0, y1 - pad_y)
    nx2 = min(width, x2 + pad_x)
    ny2 = min(height, y2 + pad_y)
    
    cropped = image.crop((nx1, ny1, nx2, ny2))
    return cropped.resize((224, 224), Image.Resampling.LANCZOS)

class AnalyzeRequest(BaseModel):
    image: str

@app.post("/analyze")
def analyze_image(req: AnalyzeRequest):
    try:
        image_bytes = base64.b64decode(req.image)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        return {"error": f"Invalid image data: {str(e)}", "labels": [], "description": ""}

    try:
        # 1. YOLO Inference (Region Proposal Only)
        results = yolo_model(image, conf=0.1, iou=0.5, max_det=5, verbose=False)

        print("YOLO results:", results)
        print("boxes:", len(results[0].boxes) if results else 0)
        
        crops = []
        if results and len(results) > 0:
            for box in results[0].boxes:
                crops.append(crop_image(image, box))
                
        if len(crops) == 0:
            return {
                "labels": ["object"],
                "description": "objects detected: object"
            }
                
        # 2. CLIP Inference per crop
        all_crop_predictions = []
        
        for crop in crops:
            with torch.no_grad():
                pixel_values = clip_processor(images=crop, return_tensors="pt").pixel_values.to(device)
                image_features = clip_model.get_image_features(pixel_values)
                # image_features /= image_features.norm(dim=-1, keepdim=True)
                image_features = image_features / (image_features.norm(dim=-1, keepdim=True) + 1e-8)
                
                # Cosine similarity only
                similarity = image_features @ clip_text_features.T
                values, indices = similarity[0].topk(5)
                
                crop_preds = []
                for i in range(5):
                    raw_label = VOCABULARY[indices[i].item()]
                    sim_score = values[i].item()
                    label = LABEL_MAP.get(raw_label, raw_label)
                    crop_preds.append({"label": label, "similarity": sim_score})
                    
                all_crop_predictions.append(crop_preds)
                
        # 3. Aggregation across crops
        label_stats = {}
        for preds in all_crop_predictions:
            for p in preds:
                lbl = p["label"]
                if lbl not in label_stats:
                    label_stats[lbl] = {"count": 0, "sum_sim": 0.0}
                label_stats[lbl]["count"] += 1
                label_stats[lbl]["sum_sim"] += p["similarity"]
                
        final_scores = []
        
        for lbl, stats in label_stats.items():
            freq = stats["count"]
            mean_sim = stats["sum_sim"] / freq
            consistency_boost = math.log(1 + freq)
            
            # Final scoring rule
            final_score = 0.5 * freq + 0.3 * mean_sim + 0.2 * consistency_boost
            
            final_scores.append({
                "label": lbl,
                "score": final_score
            })
            
        final_scores.sort(key=lambda x: x["score"], reverse=True)
        
        # 4. Post-processing
        unique_labels = [item["label"] for item in final_scores[:5]]
        
        if len(unique_labels) == 0:
            unique_labels = ["object"]
        print("FINAL SCORES:")
        for x in final_scores[:10]:
            print(x)    
            
        return {
            "labels": unique_labels,
            "description": "objects detected: " + ", ".join(unique_labels)
        }
        
    except Exception as e:
        return {"error": f"Analysis failed: {str(e)}", "labels": [], "description": ""}