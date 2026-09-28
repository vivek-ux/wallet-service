"""Controlled, non-AI edit stress test on the held-out MagicBrush sources.

These corruptions are not a substitute for real AI edits. They test whether the
retrieval pipeline is sensitive to common visual changes under the same gallery.
"""
from __future__ import annotations

import csv
import hashlib
import json
import time
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn.functional as F
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

BASE = Path(__file__).parent
ROOT = BASE / "outputs" / "multistage_local"
PREVIEW = ROOT / "preview_images"
CKPT = BASE / "outputs" / "pilot_100_sessions" / "sscd_disc_mixup.torchscript.pt"
OUT = ROOT / "synthetic_stress"
OUT.mkdir(exist_ok=True)
torch.set_num_threads(2)
device = "cpu"
ck_hash = hashlib.sha256(CKPT.read_bytes()).hexdigest()
ck_short = ck_hash[:12]
model = torch.jit.load(str(CKPT), map_location="cpu").eval()
for param in model.parameters():
    param.requires_grad_(False)


def image_key(image: Image.Image) -> str:
    return hashlib.sha256(str(image.size).encode() + image.tobytes()).hexdigest()


def preprocess(image: Image.Image) -> torch.Tensor:
    image = image.convert("RGB").resize((288, 288), Image.Resampling.BILINEAR)
    array = np.asarray(image, dtype=np.float32) / 255.0
    array = (array - np.asarray([.485, .456, .406], dtype=np.float32)) / np.asarray([.229, .224, .225], dtype=np.float32)
    return torch.from_numpy(array.transpose(2, 0, 1).copy())


@torch.inference_mode()
def get_features(image: Image.Image) -> torch.Tensor:
    key = image_key(image)
    path = ROOT / "features" / f"{key}_{ck_short}_288_grid3.pt"
    if path.exists():
        return torch.load(path, map_location="cpu", weights_only=True)
    w, h = image.size
    crops = [image]
    crops += [image.crop((round(x*w/3), round(y*h/3), round((x+1)*w/3), round((y+1)*h/3))) for y in range(3) for x in range(3)]
    out = []
    for start in range(0, len(crops), 2):
        z = model(torch.stack([preprocess(im) for im in crops[start:start+2]]))
        out.append(F.normalize(z.float(), dim=-1).cpu())
    result = torch.cat(out)
    torch.save(result, path)
    return result


def variants(image: Image.Image) -> dict[str, Image.Image]:
    image = image.convert("RGB")
    w, h = image.size
    result = {}
    for label, fraction in (("center_crop_70", .70), ("center_crop_50", .50)):
        cw, ch = round(w*fraction), round(h*fraction)
        x0, y0 = (w-cw)//2, (h-ch)//2
        result[label] = image.crop((x0, y0, x0+cw, y0+ch)).resize((w, h), Image.Resampling.BICUBIC)
    result["gaussian_blur_r3"] = image.filter(ImageFilter.GaussianBlur(radius=3))
    import io
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=15)
    buffer.seek(0)
    result["jpeg_quality_15"] = Image.open(buffer).convert("RGB")
    occluded = image.copy()
    draw = ImageDraw.Draw(occluded)
    side = round(min(w, h) * .35)
    x0, y0 = (w-side)//2, (h-side)//2
    draw.rectangle((x0, y0, x0+side, y0+side), fill=(128, 128, 128))
    result["center_occlusion_35"] = occluded
    return result


rank_file = pd.read_csv(ROOT / "per_query_rankings.csv")
rank_file = rank_file[rank_file.method == "sscd_global"].drop_duplicates("target_id")
sources = sorted(p.stem.removeprefix("source_") for p in PREVIEW.glob("source_*.jpg"))
gallery_ids = sources
gallery_images = {gid: Image.open(PREVIEW / f"source_{gid}.jpg").convert("RGB") for gid in gallery_ids}
gallery_feats = {}
for gid in gallery_ids:
    path = ROOT / "features" / f"{gid}_{ck_short}_288_grid3.pt"
    if not path.exists():
        raise FileNotFoundError(f"Missing cached original-image embedding: {path}")
    gallery_feats[gid] = torch.load(path, map_location="cpu", weights_only=True)
g = torch.stack([gallery_feats[gid][0] for gid in gallery_ids])
gp = torch.stack([gallery_feats[gid][1:] for gid in gallery_ids])
single_state = torch.load(ROOT / "fusion_single_edit.pt", map_location="cpu", weights_only=False)["state_dict"]
multi_state = torch.load(ROOT / "fusion_multistage.pt", map_location="cpu", weights_only=False)["state_dict"]


class Fusion(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.net = torch.nn.Sequential(torch.nn.Linear(2, 16), torch.nn.ReLU(), torch.nn.Linear(16, 1))

    def forward(self, x):
        return self.net(x).flatten()


single = Fusion().eval(); single.load_state_dict(single_state)
multi = Fusion().eval(); multi.load_state_dict(multi_state)
records = []
for row in rank_file.itertuples(index=False):
    source_id = row.target_id
    path = PREVIEW / f"source_{source_id}.jpg"
    if not path.exists():
        continue
    original = Image.open(path).convert("RGB")
    for edit_type, query in variants(original).items():
        q = get_features(query)
        global_score = q[0] @ g.T
        order = torch.argsort(global_score, descending=True)
        top = order[:min(20, len(gallery_ids))]
        sims = torch.einsum("pd,ckd->cpk", q[1:], gp[top])
        local = sims.max(dim=2).values.topk(3, dim=1).values.mean(dim=1)
        x = torch.stack([global_score[top], local], dim=1)
        target_idx = gallery_ids.index(source_id)
        with torch.no_grad():
            scores = {
                "sscd_global": global_score,
                "sscd_fixed_patch": None,
                "fusion_single_edit": None,
                "fusion_multistage": None,
            }
            scores["sscd_fixed_patch"] = .75*x[:, 0] + .25*x[:, 1]
            scores["fusion_single_edit"] = single(x)
            scores["fusion_multistage"] = multi(x)
            for method, values in scores.items():
                if method == "sscd_global":
                    full_order = order.tolist()
                else:
                    reranked = top[torch.argsort(values, descending=True)].tolist()
                    full_order = reranked + order[len(top):].tolist()
                rank = full_order.index(target_idx) + 1
                records.append({
                    "method": method,
                    "edit_type": edit_type,
                    "source_id": source_id,
                    "target_rank": rank,
                    "gallery_size": len(gallery_ids),
                })
    print(f"SYNTHETIC SOURCES {source_id}", flush=True)

pd.DataFrame(records).to_csv(OUT / "per_query_rankings.csv", index=False)
metrics = []
df = pd.DataFrame(records)
for (method, edit_type), group in df.groupby(["method", "edit_type"]):
    metrics.append({"method": method, "edit_type": edit_type, "queries": len(group), "gallery_size": len(gallery_ids), **{f"Recall@{k}": float((group.target_rank <= k).mean()) for k in (1, 5, 10)}})
summary = pd.DataFrame(metrics).sort_values(["edit_type", "method"])
summary.to_csv(OUT / "comparison_by_edit_type.csv", index=False)
record = {
    "status": "measured", "scope": "synthetic visual-corruption stress test, not AI edits",
    "source_run_rows": 500, "source_run_test_queries": int(rank_file.shape[0]),
    "gallery_size": len(gallery_ids), "edit_types": sorted(df.edit_type.unique()),
    "query_count_per_edit_type": int(rank_file.shape[0]), "methods": sorted(df.method.unique()),
    "checkpoint_sha256": ck_hash, "device": device,
    "transformations": {"center_crop_70": "center crop 70% of width and height; resize to original preview dimensions", "center_crop_50": "center crop 50% of width and height; resize to original preview dimensions", "gaussian_blur_r3": "Gaussian blur radius 3", "jpeg_quality_15": "JPEG re-encode at quality 15", "center_occlusion_35": "cover central square 35% of shorter image side with gray"},
    "limitation": "Queries are synthetic corruptions applied to resized JPEG source previews; results do not measure AI editing performance."
}
(OUT / "run_record.json").write_text(json.dumps(record, indent=2))
print("MEASURED SYNTHETIC STRESS RESULTS\n" + summary.to_string(index=False), flush=True)
