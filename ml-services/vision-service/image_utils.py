"""
image_utils.py — Aspect-ratio-preserving crop processing.

Replaces the distorted resize logic in the original pipeline.
All crops are letterboxed to the model's native input size.
"""

from PIL import Image


def get_model_input_size(model_id: str) -> int:
    """Return the native square input size for a given SigLIP model ID."""
    if "384" in model_id:
        return 384
    return 224  # base-patch16-224 and all other variants


def letterbox_resize(image: Image.Image, target: int = 224) -> Image.Image:
    """
    Resize image to target×target while preserving aspect ratio.
    Pads with neutral gray (128, 128, 128) — same as YOLO preprocessing convention.

    Args:
        image:  PIL RGB image of any size
        target: square output dimension (224 for base, 384 for so400m)

    Returns:
        PIL RGB image of size (target, target)
    """
    w, h = image.size
    if w == 0 or h == 0:
        return Image.new("RGB", (target, target), (128, 128, 128))

    scale = target / max(w, h)
    new_w = max(1, int(w * scale))
    new_h = max(1, int(h * scale))

    resized = image.resize((new_w, new_h), Image.Resampling.LANCZOS)

    canvas = Image.new("RGB", (target, target), (128, 128, 128))
    offset_x = (target - new_w) // 2
    offset_y = (target - new_h) // 2
    canvas.paste(resized, (offset_x, offset_y))
    return canvas


def extract_crop(
    image: Image.Image,
    box,
    expand: float = 0.20,
    target_size: int = 224,
) -> Image.Image:
    """
    Extract a padded crop from a YOLO bounding box.

    Expands the box by `expand` fraction in each direction (prevents edge clipping),
    clamps to image boundaries, then letterbox-resizes to target_size.

    Args:
        image:       Full PIL image
        box:         YOLO box object with .xyxy attribute
        expand:      Fractional padding on each side (default 20%)
        target_size: Output square size (matches model input resolution)

    Returns:
        Letterboxed PIL crop at target_size × target_size
    """
    width, height = image.size
    x1, y1, x2, y2 = box.xyxy[0].tolist()

    w = x2 - x1
    h = y2 - y1

    # Expand bounding box
    pad_x = w * expand
    pad_y = h * expand

    nx1 = max(0.0, x1 - pad_x)
    ny1 = max(0.0, y1 - pad_y)
    nx2 = min(float(width),  x2 + pad_x)
    ny2 = min(float(height), y2 + pad_y)

    # Guard against degenerate boxes
    if nx2 - nx1 < 2 or ny2 - ny1 < 2:
        return letterbox_resize(image, target_size)

    crop = image.crop((nx1, ny1, nx2, ny2))
    return letterbox_resize(crop, target_size)


def make_image_grid(images: list[Image.Image]) -> Image.Image:
    """Debug helper: arrange multiple PIL images in a horizontal strip."""
    if not images:
        return Image.new("RGB", (224, 224), (0, 0, 0))
    w, h = images[0].size
    grid = Image.new("RGB", (w * len(images), h), (0, 0, 0))
    for i, img in enumerate(images):
        grid.paste(img, (i * w, 0))
    return grid
