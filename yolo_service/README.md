# YOLO Object Detection Service

Lightweight FastAPI service for the AI Object Scanner backend.

## Contract

`POST /detect`

- Content type: `multipart/form-data`
- File field: `image`
- Response:

```json
[
  {
    "label": "apple",
    "confidence": 0.94,
    "box": {
      "x": 0.32,
      "y": 0.22,
      "width": 0.18,
      "height": 0.24
    }
  }
]
```

`box` coordinates are normalized to the original image dimensions, where `x` and `y`
represent the top-left corner and `width`/`height` represent the object box size.

`GET /health`

Returns the service status and configured model name.

## Local Run

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 5001
```

The default model is `yolov8n.pt`. Override it with `YOLO_MODEL` if needed.
