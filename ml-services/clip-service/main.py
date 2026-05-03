import base64
import io
import hashlib
import math
from fastapi import FastAPI
from pydantic import BaseModel
from PIL import Image
import torch
from transformers import CLIPProcessor, CLIPModel

app = FastAPI()

# Load CLIP model (CPU optimized for Docker)
device = "cpu"
print(f"Loading CLIP model on {device}...")
model_name = "openai/clip-vit-base-patch32"
model = CLIPModel.from_pretrained(model_name).to(device)
processor = CLIPProcessor.from_pretrained(model_name)
print("CLIP model loaded successfully.")

class ClassifyRequest(BaseModel):
    image_base64: str
    yolo_label: str = ""
    top_k: int = 5

# Simple LRU cache for embedding reuse (in-memory)
from functools import lru_cache

# Extended dictionary for dynamic label generation based on generic YOLO labels
LABEL_MAP = {
    "shoe": ["sneaker", "running shoe", "Nike shoe", "leather shoe", "boots", "footwear", "high heels"],
    "bottle": ["water bottle", "plastic bottle", "drink bottle", "soda bottle", "thermos", "glass bottle"],
    "apple": ["red apple", "green apple", "fruit", "sliced apple", "Granny Smith apple"],
    "cup": ["coffee mug", "teacup", "paper cup", "plastic cup", "glass"],
    "laptop": ["MacBook", "Windows laptop", "gaming laptop", "notebook computer"],
    "cell phone": ["iPhone", "Android phone", "smartphone", "mobile phone"],
    "book": ["paperback book", "hardcover book", "novel", "textbook", "magazine"],
    "person": ["man", "woman", "child", "boy", "girl", "human"],
    # Generic fallback categories
    "default": ["object", "item", "product", "thing"]
}

def get_candidates(yolo_label: str):
    label_lower = yolo_label.lower().strip()
    candidates = LABEL_MAP.get(label_lower, [])
    # Always include the original label
    if label_lower not in candidates and label_lower:
        candidates.append(label_lower)
    if not candidates:
        candidates = LABEL_MAP["default"]
    
    # Add variations for robustness
    candidates.extend(["branded " + c for c in candidates[:2]])
    return list(set(candidates))

def build_prompts(candidates):
    prompts = []
    templates = [
        "a photo of a {}",
        "a close-up of a {}",
        "a product image of a {}",
        "a realistic image of a {}"
    ]
    for candidate in candidates:
        for t in templates:
            prompts.append((t.format(candidate), candidate))
    return prompts

@app.post("/classify")
def classify(req: ClassifyRequest):
    try:
        image_bytes = base64.b64decode(req.image_base64)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        return {"error": f"Invalid image data: {str(e)}", "predictions": []}

    try:
        candidates = get_candidates(req.yolo_label)
        prompt_pairs = build_prompts(candidates)
        text_prompts = [p[0] for p in prompt_pairs]
        base_labels = [p[1] for p in prompt_pairs]

        # Process inputs
        inputs = processor(text=text_prompts, images=image, return_tensors="pt", padding=True).to(device)

        # Calculate features and similarities
        tau = 1.5
        with torch.no_grad():
            outputs = model(**inputs)
            logits_per_image = outputs.logits_per_image # this is the image-text similarity score
            # Temperature scaling
            scaled_logits = logits_per_image / tau
            probs = scaled_logits.softmax(dim=1).cpu().numpy()[0] # shape: (1, num_prompts)
        
        # Aggregate probabilities by base label (average across prompts)
        label_scores_sum = {}
        label_counts = {}
        for idx, prob in enumerate(probs):
            label = base_labels[idx]
            label_scores_sum[label] = label_scores_sum.get(label, 0.0) + float(prob)
            label_counts[label] = label_counts.get(label, 0) + 1
            
        label_scores = {label: label_scores_sum[label] / label_counts[label] for label in label_scores_sum}
        
        # Ensure scores sum to 1 after averaging
        total_score = sum(label_scores.values())
        if total_score > 0:
            label_scores = {label: score / total_score for label, score in label_scores.items()}
        
        # Sort and get top K
        sorted_labels = sorted(label_scores.items(), key=lambda x: x[1], reverse=True)
        top_predictions = [{"label": label, "confidence": score} for label, score in sorted_labels[:req.top_k]]

        # Calculate metrics for logging
        if len(sorted_labels) > 0:
            top1_score = sorted_labels[0][1]
            top2_score = sorted_labels[1][1] if len(sorted_labels) > 1 else 0.0
            margin = top1_score - top2_score
            entropy = -sum(p * math.log2(p + 1e-9) for p in label_scores.values())
            print(f"[CLIP] YOLO Prior: '{req.yolo_label}', Top-1: '{sorted_labels[0][0]}' ({top1_score:.3f}), Margin: {margin:.3f}, Entropy: {entropy:.3f}")

        return {"predictions": top_predictions}
    except Exception as e:
        return {"error": f"Classification failed: {str(e)}", "predictions": []}
