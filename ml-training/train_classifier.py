import os
import argparse
import json
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader
from torchvision import datasets
from transformers import CLIPProcessor, CLIPModel
from tqdm import tqdm

class LinearClassifierHead(nn.Module):
    """Simple linear classification head to go on top of CLIP embeddings.
       Avoids deep architectures initially to prevent overfitting.
    """
    def __init__(self, input_dim, num_classes):
        super(LinearClassifierHead, self).__init__()
        self.fc = nn.Linear(input_dim, num_classes)
        
    def forward(self, x):
        return self.fc(x)

def train_classifier(data_dir, epochs=20, batch_size=32, lr=1e-3):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Using device: {device}")
    
    # 1. Load CLIP Model and Processor (Frozen Backbone)
    model_name = "openai/clip-vit-base-patch32"
    clip_model = CLIPModel.from_pretrained(model_name).to(device)
    processor = CLIPProcessor.from_pretrained(model_name)
    clip_model.eval() # We only extract features, do not train CLIP
    
    # 2. Setup Dataset
    train_dir = os.path.join(data_dir, "train")
    val_dir = os.path.join(data_dir, "val")
    
    # Custom collate function to use CLIP processor
    def collate_fn(batch):
        images, labels = zip(*batch)
        inputs = processor(images=images, return_tensors="pt", padding=True)
        return inputs, torch.tensor(labels)

    # Use standard ImageFolder to read directory structure
    train_dataset = datasets.ImageFolder(train_dir)
    val_dataset = datasets.ImageFolder(val_dir)
    
    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, collate_fn=collate_fn, num_workers=2)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False, collate_fn=collate_fn, num_workers=2)
    
    classes = train_dataset.classes
    num_classes = len(classes)
    print(f"Found {num_classes} classes: {classes}")
    
    # Save class mapping
    with open("class_mapping.json", "w") as f:
        json.dump(classes, f)
    
    # 3. Setup Linear Head
    embed_dim = clip_model.config.projection_dim # Usually 512 for vit-base-patch32
    head = LinearClassifierHead(embed_dim, num_classes).to(device)
    
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.AdamW(head.parameters(), lr=lr)
    
    # 4. Training Loop
    best_acc = 0.0
    for epoch in range(epochs):
        head.train()
        running_loss = 0.0
        
        # Train phase
        pbar = tqdm(train_loader, desc=f"Epoch {epoch+1}/{epochs} [Train]")
        for inputs, labels in pbar:
            inputs = {k: v.to(device) for k, v in inputs.items()}
            labels = labels.to(device)
            
            optimizer.zero_grad()
            
            with torch.no_grad():
                # Extract image embeddings
                outputs = clip_model.get_image_features(**inputs)
                # Normalize embeddings
                embeddings = outputs / outputs.norm(p=2, dim=-1, keepdim=True)
                
            logits = head(embeddings)
            loss = criterion(logits, labels)
            
            loss.backward()
            optimizer.step()
            
            running_loss += loss.item() * labels.size(0)
            pbar.set_postfix({"loss": loss.item()})
            
        epoch_loss = running_loss / len(train_dataset)
        
        # Eval phase
        head.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for inputs, labels in val_loader:
                inputs = {k: v.to(device) for k, v in inputs.items()}
                labels = labels.to(device)
                
                outputs = clip_model.get_image_features(**inputs)
                embeddings = outputs / outputs.norm(p=2, dim=-1, keepdim=True)
                
                logits = head(embeddings)
                _, predicted = torch.max(logits, 1)
                total += labels.size(0)
                correct += (predicted == labels).sum().item()
                
        val_acc = correct / total
        print(f"Epoch {epoch+1}/{epochs} - Train Loss: {epoch_loss:.4f} - Val Acc: {val_acc:.4f}")
        
        if val_acc > best_acc:
            best_acc = val_acc
            torch.save(head.state_dict(), "classifier_head.pt")
            print("Saved new best model.")
            
    print("Training complete!")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, required=True, help="Path to dataset directory (should contain train/ and val/)")
    parser.add_argument("--epochs", type=int, default=20, help="Number of epochs")
    parser.add_argument("--batch", type=int, default=32, help="Batch size")
    parser.add_argument("--lr", type=float, default=1e-3, help="Learning rate")
    args = parser.parse_args()
    
    train_classifier(args.data, epochs=args.epochs, batch_size=args.batch, lr=args.lr)
