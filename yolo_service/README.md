# YOLO Object Detection Service

Lightweight FastAPI service for the AI Object Scanner backend.

## Contract

`POST /detect`

- Content type: `multipart/form-data`
- File field: `image`
- Response:

```json
[
  { "label": "apple", "confidence": 0.94 }
]
```

`GET /health`

Returns the service status and configured model name.

## Local Run

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 5001
```

The default model is `yolov8n.pt`. Override it with `YOLO_MODEL` if needed.
