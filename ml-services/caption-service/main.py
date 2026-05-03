import base64
import io
import os
from fastapi import FastAPI
from pydantic import BaseModel
from PIL import Image
import torch
from transformers import BlipProcessor, BlipForConditionalGeneration
import spacy

app = FastAPI()

device = "cpu"
print(f"Loading caption service on {device}...")

# Load BLIP Model
model_id = "Salesforce/blip-image-captioning-base"
processor = BlipProcessor.from_pretrained(model_id)
model = BlipForConditionalGeneration.from_pretrained(model_id).to(device)
model.eval()

# Load spaCy
print("Loading spaCy en_core_web_sm...")
nlp = spacy.load("en_core_web_sm")

GENERIC_WORDS = {"thing", "object", "item", "background", "image", "photo", "picture", "part", "piece", "stuff"}

def extract_best_noun_chunk(text: str) -> str:
    doc = nlp(text.lower())
    
    # 1. Extract noun chunks
    chunks = [chunk.text for chunk in doc.noun_chunks]
    
    # 2. Filter out generics
    filtered_chunks = []
    for chunk in chunks:
        # Check if the root noun of the chunk is generic
        root = next((token for token in doc if token.text in chunk and token.dep_ == "ROOT"), None)
        # If no strict root match, just check the whole string against generic words
        if not any(gen in chunk.split() for gen in GENERIC_WORDS):
            filtered_chunks.append(chunk)
            
    if not filtered_chunks:
        return "object"
        
    # Return the first meaningful chunk (usually the subject of the caption)
    return filtered_chunks[0]

class CaptionRequest(BaseModel):
    image_base64: str

@app.post("/caption")
def generate_caption(req: CaptionRequest):
    try:
        image_bytes = base64.b64decode(req.image_base64)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        return {"error": f"Invalid image data: {str(e)}", "description": "", "label": ""}

    try:
        inputs = processor(image, return_tensors="pt").to(device)
        
        with torch.no_grad():
            out = model.generate(**inputs, max_new_tokens=20)
            
        description = processor.decode(out[0], skip_special_tokens=True)
        
        # Extract vocabulary word
        label = extract_best_noun_chunk(description)
        
        print(f"[CAPTION] Description: '{description}' | Extracted Label: '{label}'")
        
        return {
            "description": description,
            "label": label
        }
    except Exception as e:
        return {"error": f"Captioning failed: {str(e)}", "description": "", "label": ""}
