import base64
import io
import json
import os
from fastapi import FastAPI
from pydantic import BaseModel
from PIL import Image
import torch
import torch.nn as nn
from transformers import CLIPProcessor, CLIPModel

app = FastAPI()

device = "cpu"
print(f"Loading classifier service on {device}...")

# Load CLIP Backbone
model_name = "openai/clip-vit-base-patch32"
clip_model = CLIPModel.from_pretrained(model_name).to(device)
processor = CLIPProcessor.from_pretrained(model_name)
clip_model.eval()

class LinearClassifierHead(nn.Module):
    def __init__(self, input_dim, num_classes):
        super(LinearClassifierHead, self).__init__()
        self.fc = nn.Linear(input_dim, num_classes)
        
    def forward(self, x):
        return self.fc(x)

# Try to load custom classifier head and class mapping
HEAD_WEIGHTS_PATH = os.environ.get("CLASSIFIER_HEAD", "classifier_head.pt")
CLASS_MAP_PATH = os.environ.get("CLASS_MAP", "class_mapping.json")

classifier_head = None
classes = []

if os.path.exists(HEAD_WEIGHTS_PATH) and os.path.exists(CLASS_MAP_PATH):
    print("Loading custom classifier head...")
    with open(CLASS_MAP_PATH, "r") as f:
        classes = json.load(f)
    
    num_classes = len(classes)
    embed_dim = clip_model.config.projection_dim
    classifier_head = LinearClassifierHead(embed_dim, num_classes).to(device)
    classifier_head.load_state_dict(torch.load(HEAD_WEIGHTS_PATH, map_location=device))
    classifier_head.eval()
    print(f"Successfully loaded classifier head with {num_classes} classes.")
else:
    print(f"WARNING: Classifier weights ({HEAD_WEIGHTS_PATH}) or class map ({CLASS_MAP_PATH}) not found.")
    print("Service will return errors until model is trained and provided.")

class ClassifyRequest(BaseModel):
    image_base64: str

@app.post("/classify")
def classify(req: ClassifyRequest):
    if classifier_head is None:
        return {"error": "Classifier model not loaded", "predictions": []}
        
    try:
        image_bytes = base64.b64decode(req.image_base64)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        return {"error": f"Invalid image data: {str(e)}", "predictions": []}

    try:
        inputs = processor(images=image, return_tensors="pt", padding=True).to(device)
        
        with torch.no_grad():
            # Get CLIP embeddings
            outputs = clip_model.get_image_features(**inputs)
            embeddings = outputs / outputs.norm(p=2, dim=-1, keepdim=True)
            
            # Pass through classifier head
            logits = classifier_head(embeddings)
            probs = torch.softmax(logits, dim=1).cpu().numpy()[0]
            
        # Get all predictions sorted by probability
        predictions = [{"label": classes[i], "confidence": float(probs[i])} for i in range(len(classes))]
        predictions.sort(key=lambda x: x["confidence"], reverse=True)
        
        # Calculate margin
        top1 = predictions[0]["confidence"] if len(predictions) > 0 else 0.0
        top2 = predictions[1]["confidence"] if len(predictions) > 1 else 0.0
        margin = top1 - top2
        
        print(f"[CLASSIFIER] Top-1: '{predictions[0]['label']}' ({top1:.3f}), Margin: {margin:.3f}")
        
        # We only need to return the top few
        return {
            "predictions": predictions[:5],
            "margin": margin
        }
    except Exception as e:
        return {"error": f"Classification failed: {str(e)}", "predictions": []}
