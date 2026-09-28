"""Evaluate saved train-only models on MagicBrush's separate public dev split."""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import random
import shutil
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import local

import numpy as np
import pandas as pd
import requests
import torch
import torch.nn.functional as F
from PIL import Image

BASE = Path(__file__).parent
TRAIN_ROOT = BASE / "outputs" / "multistage_local"
OUT = BASE / "outputs" / "external_dev"
CKPT = BASE / "outputs" / "pilot_100_sessions" / "sscd_disc_mixup.torchscript.pt"
API = "https://datasets-server.huggingface.co"
DATASET = "osunlp/MagicBrush"
parser = argparse.ArgumentParser()
parser.add_argument("--rows", type=int, default=300)
args = parser.parse_args()
if args.rows < 100 or args.rows > 400:
    raise ValueError("Choose 100-400 rows so this bounded public-dev evaluation stays manageable.")
OUT.mkdir(parents=True, exist_ok=True)
cache = TRAIN_ROOT / "features"
seed = 20260927
torch.set_num_threads(2)
device = "cpu"
ck_hash = hashlib.sha256(CKPT.read_bytes()).hexdigest()
ck_short = ck_hash[:12]
thread_state = local()


def session():
    if not hasattr(thread_state, "http"):
        thread_state.http = requests.Session()
        thread_state.http.headers.update({"User-Agent": "MTech-Source-Retrieval-External-Dev/1.0"})
    return thread_state.http


def get(url, params=None, attempts=4):
    last = None
    for attempt in range(attempts):
        try:
            response = session().get(url, params=params, timeout=(15, 60))
            response.raise_for_status()
            return response
        except Exception as exc:
            last = exc
            if attempt + 1 < attempts:
                time.sleep(min(8, 2 ** attempt))
    raise RuntimeError(f"Dataset request failed: {url}") from last


def key(image: Image.Image) -> str:
    return hashlib.sha256(str(image.size).encode() + image.tobytes()).hexdigest()


def preprocess(image: Image.Image) -> torch.Tensor:
    image = image.convert("RGB").resize((288, 288), Image.Resampling.BILINEAR)
    arr = np.asarray(image, dtype=np.float32) / 255.0
    arr = (arr - np.asarray([.485,.456,.406], dtype=np.float32)) / np.asarray([.229,.224,.225], dtype=np.float32)
    return torch.from_numpy(arr.transpose(2,0,1).copy())


model = torch.jit.load(str(CKPT), map_location="cpu").eval()
for parameter in model.parameters():
    parameter.requires_grad_(False)


@torch.inference_mode()
def features(image: Image.Image) -> torch.Tensor:
    path = cache / f"{key(image)}_{ck_short}_288_grid3.pt"
    if path.exists():
        return torch.load(path, map_location="cpu", weights_only=True)
    w, h = image.size
    crops = [image]
    crops += [image.crop((round(x*w/3), round(y*h/3), round((x+1)*w/3), round((y+1)*h/3))) for y in range(3) for x in range(3)]
    zs = []
    for i in range(0, len(crops), 2):
        zs.append(F.normalize(model(torch.stack([preprocess(x) for x in crops[i:i+2]]).float()).float(), dim=-1).cpu())
    result = torch.cat(zs)
    torch.save(result, path)
    return result


# Fetch a contiguous prefix plus a one-row look-ahead; discard any incomplete final session.
split_reply = get(f"{API}/splits", {"dataset": DATASET}).json()
if not any(s["split"] == "dev" for s in split_reply["splits"]):
    raise RuntimeError("MagicBrush public dev split is unavailable through the Dataset Viewer.")
split_size = get(f"{API}/size", {"dataset": DATASET}).json()["size"]["splits"]
dev_rows_total = next(x["num_rows"] for x in split_size if x["split"] == "dev")
target_n = min(args.rows + 1, dev_rows_total)
wrapped = []
offset = 0
while offset < target_n:
    length = min(50, target_n - offset)
    page = get(f"{API}/rows", {"dataset": DATASET, "config": "default", "split": "dev", "offset": offset, "length": length}).json()
    wrapped.extend(page["rows"])
    offset += len(page["rows"])
    print(f"DEV ROWS {len(wrapped)}/{target_n}", flush=True)
    if not page["rows"]:
        break
if len(wrapped) < target_n:
    raise RuntimeError(f"Incomplete public-dev slice: {len(wrapped)}/{target_n}")
complete = len(wrapped) >= dev_rows_total or len(wrapped) > args.rows and wrapped[args.rows]["row"]["img_id"] != wrapped[args.rows-1]["row"]["img_id"]
wrapped = wrapped[:args.rows]
if len(wrapped) == args.rows and not complete:
    boundary_id = wrapped[-1]["row"]["img_id"]
    wrapped = [item for item in wrapped if item["row"]["img_id"] != boundary_id]
    print("DROPPED INCOMPLETE BOUNDARY SESSION", boundary_id, flush=True)

groups = {}
for item in wrapped:
    row = item["row"]
    groups.setdefault(str(row["img_id"]), []).append(row)
for sid, group in groups.items():
    group.sort(key=lambda r: int(r["turn_index"]))
    turns = [int(r["turn_index"]) for r in group]
    if turns != list(range(min(turns), min(turns) + len(turns))):
        raise RuntimeError(f"Nonconsecutive turn sequence in {sid}: {turns}")

# The previous run's gallery and model-fitting data come only from MagicBrush train.
manifest = pd.read_csv(TRAIN_ROOT / "dataset_manifest.csv")
train_original_ids = sorted(manifest.original_sha256.unique().tolist())
train_source_ids = set(manifest.original_sha256)
train_gallery_features = {}
for source_hash in train_original_ids:
    path = cache / f"{source_hash}_{ck_short}_288_grid3.pt"
    if not path.exists():
        raise FileNotFoundError(f"Missing cached train gallery feature: {path}")
    train_gallery_features[source_hash] = torch.load(path, map_location="cpu", weights_only=True)

# Download only each dev session's first original and final edited query.
needed_urls = set()
session_urls = {}
for sid, group in groups.items():
    first = group[0]
    last = group[-1]
    src = first["source_img"].get("src")
    tgt = last["target_img"].get("src")
    if not src or not tgt:
        raise RuntimeError(f"Missing public image URL in dev session {sid}")
    session_urls[sid] = (src, tgt, group)
    needed_urls.update((src, tgt))


def download(url):
    response = get(url)
    return url, Image.open(io.BytesIO(response.content)).convert("RGB")


downloaded = {}
with ThreadPoolExecutor(max_workers=6) as pool:
    for i, (url, image) in enumerate(pool.map(download, sorted(needed_urls)), start=1):
        downloaded[url] = image
        if i % 30 == 0 or i == len(needed_urls):
            print(f"DEV IMAGES {i}/{len(needed_urls)}", flush=True)

dev_sessions = {}
excluded_train_overlap = 0
for sid, (src_url, tgt_url, group) in session_urls.items():
    original = downloaded[src_url]
    target = downloaded[tgt_url]
    source_hash = key(original)
    if source_hash in train_source_ids:
        excluded_train_overlap += 1
        continue
    dev_sessions[sid] = {"original": original, "query": target, "source_hash": source_hash, "turn": int(group[-1]["turn_index"]), "row_indices": [int(x["row_idx"]) for x in wrapped if str(x["row"]["img_id"]) == sid]}
if len(dev_sessions) < 20:
    raise RuntimeError("Too few source-disjoint public-dev sessions remain after duplicate filtering.")

# Form one fair gallery from train distractor originals plus every held-out dev original.
dev_originals = {entry["source_hash"]: entry["original"] for entry in dev_sessions.values()}
if len(dev_originals) != len({entry["source_hash"] for entry in dev_sessions.values()}):
    # Repeated source image across sessions is retained once in the gallery; all its edits remain in dev.
    pass
gallery_images = {}
for source_hash in train_original_ids:
    # Only cached embeddings are needed for the 257 train distractors.
    gallery_images[source_hash] = train_gallery_features[source_hash]
for source_hash, image in dev_originals.items():
    gallery_images[source_hash] = features(image)
gallery_ids = sorted(gallery_images)
g = torch.stack([gallery_images[h][0] for h in gallery_ids])
gp = torch.stack([gallery_images[h][1:] for h in gallery_ids])
target_indices = {h: gallery_ids.index(h) for h in gallery_ids}


class Fusion(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.net = torch.nn.Sequential(torch.nn.Linear(2, 16), torch.nn.ReLU(), torch.nn.Linear(16, 1))
    def forward(self, x):
        return self.net(x).flatten()


def load_fusion(path):
    network = Fusion().eval()
    checkpoint = torch.load(path, map_location="cpu", weights_only=False)
    network.load_state_dict(checkpoint["state_dict"])
    return network


single = load_fusion(TRAIN_ROOT / "fusion_single_edit.pt")
multi = load_fusion(TRAIN_ROOT / "fusion_multistage.pt")
predictions = []
preview = OUT / "preview_images"
preview.mkdir(exist_ok=True)
train_preview = TRAIN_ROOT / "preview_images"
for source_hash in train_original_ids:
    source_preview = train_preview / f"source_{source_hash}.jpg"
    target_preview = preview / source_preview.name
    if source_preview.is_file() and not target_preview.exists():
        shutil.copy2(source_preview, target_preview)
for sid, entry in dev_sessions.items():
    q = features(entry["query"])
    global_score = q[0] @ g.T
    order = torch.argsort(global_score, descending=True)
    top = order[:min(20, len(gallery_ids))]
    sims = torch.einsum("pd,ckd->cpk", q[1:], gp[top])
    local_score = sims.max(dim=2).values.topk(3, dim=1).values.mean(dim=1)
    x = torch.stack([global_score[top], local_score], dim=1)
    target = target_indices[entry["source_hash"]]
    with torch.no_grad():
        method_scores = {
            "sscd_global": None,
            "sscd_fixed_patch": .75*x[:, 0] + .25*x[:, 1],
            "fusion_single_edit": single(x),
            "fusion_multistage": multi(x),
        }
        for method, scores in method_scores.items():
            if method == "sscd_global":
                ranked = order.tolist()
            else:
                reranked = top[torch.argsort(scores, descending=True)].tolist()
                ranked = reranked + order[len(top):].tolist()
            predictions.append({"method": method, "session_id": sid, "turn": entry["turn"], "target_rank": ranked.index(target) + 1, "target_hash": entry["source_hash"], "gallery_size": len(gallery_ids), "ranking": json.dumps([gallery_ids[i] for i in ranked])})
    thumb = entry["query"].copy(); thumb.thumbnail((600,600)); thumb.save(preview / f"query_{sid}_turn{entry['turn']}.jpg", quality=86)
    thumb = entry["original"].copy(); thumb.thumbnail((600,600)); thumb.save(preview / f"source_{entry['source_hash']}.jpg", quality=86)
    print(f"EVALUATED DEV SESSION {sid}", flush=True)

df = pd.DataFrame(predictions)
df.to_csv(OUT / "per_query_rankings.csv", index=False)
summary = []
for method, group in df.groupby("method"):
    summary.append({"method": method, "queries": group.session_id.nunique(), "gallery_size": int(group.gallery_size.iloc[0]), **{f"Recall@{k}": float((group.target_rank <= k).mean()) for k in (1,5,10)}})
pd.DataFrame(summary).to_csv(OUT / "comparison_table.csv", index=False)
by_turn = []
for (method, turn), group in df.groupby(["method","turn"]):
    by_turn.append({"method":method,"turn":int(turn),"queries":len(group),"gallery_size":int(group.gallery_size.iloc[0]), **{f"Recall@{k}":float((group.target_rank<=k).mean()) for k in (1,5,10)}})
pd.DataFrame(by_turn).to_csv(OUT / "by_turn.csv", index=False)
pd.DataFrame([{"session_id":sid,"source_hash":entry["source_hash"],"turn":entry["turn"],"source_edit_rows":len(entry["row_indices"]),"split":"dev_evaluation"} for sid,entry in dev_sessions.items()]).to_csv(OUT / "dataset_manifest.csv", index=False)
record = {
    "status":"measured", "dataset":DATASET,"source_split":"train","evaluation_split":"dev",
    "requested_rows":args.rows,"rows_used":len(wrapped),"dev_sessions_evaluated":len(dev_sessions),
    "train_gallery_sources":len(train_original_ids),"dev_gallery_sources":len(dev_originals),"gallery_size":len(gallery_ids),
    "excluded_exact_hash_overlap_with_train":excluded_train_overlap,"queries":df.session_id.nunique(),
    "model_training":"trained on MagicBrush train only; loaded existing weights unchanged; no dev fitting or threshold tuning",
    "test_protocol":"final edited image per complete dev session; gallery is train originals plus all sampled dev originals; exact source-image hash overlap removed",
    "seed":seed,"device":device,"checkpoint_sha256":ck_hash,
    "limitations":["MagicBrush dev is a development split, not the hidden official test split","bounded 300-row prefix","no semantic hard-negative mining","dev set must not be reused repeatedly for model selection"]
}
(OUT/"run_record.json").write_text(json.dumps(record,indent=2))
print("MEASURED EXTERNAL DEV RESULTS\n"+pd.DataFrame(summary).to_string(index=False),flush=True)
print("RUN RECORD",json.dumps(record),flush=True)
