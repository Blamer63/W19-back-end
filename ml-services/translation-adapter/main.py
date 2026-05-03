from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import os, requests

app = FastAPI()

LIBRETRANSLATE_URL = os.getenv("LIBRETRANSLATE_URL", "http://localhost:5000/translate")
LIBRETRANSLATE_API_KEY = os.getenv("LIBRETRANSLATE_API_KEY", "")

class TranslateRequest(BaseModel):
    text: str
    target_language: str

@app.post("/translate")
def translate(req: TranslateRequest):
    payload = {
        "q": req.text,
        "source": "auto",
        "target": req.target_language,
        "format": "text"
    }
    if LIBRETRANSLATE_API_KEY:
        payload["api_key"] = LIBRETRANSLATE_API_KEY

    r = requests.post(LIBRETRANSLATE_URL, json=payload, timeout=10)
    if r.status_code >= 400:
        raise HTTPException(status_code=502, detail=f"Upstream translation failed: {r.text}")

    body = r.json()
    translated = body.get("translatedText")
    if not translated:
        raise HTTPException(status_code=502, detail="Invalid translation response")

    # Match HttpTranslationProvider expectation exactly:
    return {"translated_text": translated}