import os
import argparse
from ultralytics import YOLO

def train_yolo(data_yaml, epochs=50, batch_size=16, imgsz=640, freeze_backbone=True):
    print(f"Starting YOLOv8 training using {data_yaml}")
    
    # Load a pretrained model
    model = YOLO("yolov8m.pt")
    
    # Staged Training Approach
    
    # Stage 1: Freeze backbone, train only the detection head
    if freeze_backbone:
        print("Stage 1: Freezing backbone layers (0-9) and training the head...")
        # In YOLOv8, layers 0-9 represent the backbone
        # We freeze them by setting requires_grad to False internally via the `freeze` parameter
        results = model.train(
            data=data_yaml,
            epochs=epochs // 2, # Use half epochs for stage 1
            batch=batch_size,
            imgsz=imgsz,
            freeze=10, # Freezes first 10 layers
            name="yolo_stage1_frozen",
            device="cuda" if os.environ.get("USE_CUDA") == "1" else "cpu"
        )
        print("Stage 1 complete. Best weights saved in runs/detect/yolo_stage1_frozen/weights/best.pt")
        
        # Load the best model from stage 1 for stage 2
        model = YOLO("runs/detect/yolo_stage1_frozen/weights/best.pt")
        print("Stage 2: Unfreezing backbone for full fine-tuning...")
        # Train for the remaining epochs with all layers unfrozen and a smaller learning rate
        results = model.train(
            data=data_yaml,
            epochs=epochs - (epochs // 2),
            batch=batch_size,
            imgsz=imgsz,
            freeze=0, # Unfreeze all
            lr0=0.001, # Lower learning rate for fine-tuning
            name="yolo_stage2_full",
            device="cuda" if os.environ.get("USE_CUDA") == "1" else "cpu"
        )
        print("Stage 2 complete. Final best weights saved in runs/detect/yolo_stage2_full/weights/best.pt")
    else:
        # Standard full fine-tuning directly
        print("Running full fine-tuning directly...")
        results = model.train(
            data=data_yaml,
            epochs=epochs,
            batch=batch_size,
            imgsz=imgsz,
            name="yolo_full_finetune",
            device="cuda" if os.environ.get("USE_CUDA") == "1" else "cpu"
        )
        print("Training complete. Best weights saved in runs/detect/yolo_full_finetune/weights/best.pt")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, default="dataset.yaml", help="Path to dataset.yaml")
    parser.add_argument("--epochs", type=int, default=100, help="Total number of epochs")
    parser.add_argument("--batch", type=int, default=16, help="Batch size")
    parser.add_argument("--no-freeze", action="store_true", help="Skip staged training and fine-tune all layers directly")
    args = parser.parse_args()
    
    train_yolo(args.data, epochs=args.epochs, batch_size=args.batch, freeze_backbone=not args.no_freeze)
