# Dataset Requirements for Domain-Adapted AI Scanner

To successfully transition from a zero-shot model to a domain-adapted system, you must collect a high-quality dataset following these explicit guidelines.

## 1. Class Definitions & Balance
Define a set list of target classes (e.g., `shoe`, `bottle`, `cup`, `laptop`, `cell phone`, `book`, `person`).
- **Minimum Volume**: Collect 500–2000 images **per class**.
- **Class Balance**: Ensure roughly equal representation across all target classes. Do not have 2,000 images for `person` but only 100 for `cup`.

## 2. Hard Negatives (Critical)
The current zero-shot system frequently misclassifies certain objects (e.g., "shoe" classified as "person").
- You must actively include images of these confusing objects in the training set for both classes.
- Make sure annotations precisely bound the actual object. If a person is wearing shoes, bounding boxes should clearly separate the `shoe` from the `person`.

## 3. Variation and Robustness
The dataset must reflect real-world usage conditions expected from the mobile app or web frontend.
- **Lighting**: Include well-lit, dim, and unevenly lit environments.
- **Angles**: Capture objects from multiple perspectives (top-down, side, angled).
- **Backgrounds**: Use diverse backgrounds (tables, floors, outdoors, cluttered scenes).
- **Scale**: Include both close-ups and objects further away.

## 4. Dataset Structure
Both YOLO and the Classifier require a proper train/validation split.
- **Split Ratio**: Use an 80/20 or 70/20/10 (Train/Val/Test) split.
- **Format**:
  - For YOLO: Follow standard YOLO format (images directory + labels directory with `.txt` files containing `class x_center y_center width height`). Provide a `dataset.yaml` file.
  - For Classifier: Follow a standard ImageNet-like directory structure, where images are organized in folders named after their class. Crops generated from the YOLO dataset can be used here.

```
dataset_yolo/
├── images/
│   ├── train/
│   └── val/
└── labels/
    ├── train/
    └── val/
dataset.yaml

dataset_classifier/
├── train/
│   ├── shoe/
│   ├── person/
│   └── bottle/
└── val/
    ├── shoe/
    ├── person/
    └── bottle/
```
