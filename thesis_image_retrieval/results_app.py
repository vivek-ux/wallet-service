"""Small local/Colab Streamlit viewer for saved source-retrieval results."""
from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import streamlit as st


st.set_page_config(page_title="Image Source Retrieval", layout="wide")
st.title("AI-edited image → likely original")
st.caption("Preliminary experiment viewer. It only displays saved outputs; it does not run or alter the experiment.")

default_root = Path(__file__).parent / "outputs" / "external_dev"
root = Path(st.sidebar.text_input("Results folder", str(default_root))).expanduser()
comparison_path = root / "comparison_table.csv"
rankings_path = root / "per_query_rankings.csv"
record_path = root / "run_record.json"

if not comparison_path.is_file() or not rankings_path.is_file():
    st.info("No completed results found in this folder yet. Run the local experiment or enter the path to a completed output folder.")
    st.code("Expected files: comparison_table.csv, per_query_rankings.csv, run_record.json, preview_images/")
    st.stop()

try:
    record = json.loads(record_path.read_text())
except Exception:
    record = {}

if record.get("status") != "measured":
    st.warning("This run is incomplete. The viewer will not show saved metrics until the run record says ‘measured’.")
    st.stop()
is_train_run = record.get("protocol") == "first original; exact pixel hash grouping; complete sessions; test gallery includes all original distractors; top20 reranking; final-edit-only testing"
is_dev_run = record.get("evaluation_split") == "dev" and record.get("status") == "measured"
if not (is_train_run or is_dev_run):
    st.error("This folder does not identify a completed source-retrieval evaluation. Older pilot outputs may use an invalid protocol.")
    st.stop()

comparison = pd.read_csv(comparison_path)
rankings = pd.read_csv(rankings_path)
if "query_id" not in rankings and {"session_id", "turn"}.issubset(rankings.columns):
    rankings["query_id"] = rankings.apply(lambda row: f"{row['session_id']}_turn{int(row['turn'])}", axis=1)
if "target_id" not in rankings and "target_hash" in rankings:
    rankings["target_id"] = rankings["target_hash"]
if "query_image" not in rankings and {"session_id", "turn"}.issubset(rankings.columns):
    rankings["query_image"] = rankings.apply(lambda row: f"query_{row['session_id']}_turn{int(row['turn'])}.jpg", axis=1)
required = {"method", "query_id", "target_rank", "ranking", "target_id", "query_image"}
if missing := required.difference(rankings.columns):
    st.error(f"Ranking file is missing required columns: {', '.join(sorted(missing))}")
    st.stop()

st.subheader("Same-test-set comparison")
method_names = {
    "sscd_global": "Frozen SSCD (whole image)",
    "sscd_fixed_patch": "SSCD + fixed patch score",
    "fusion_single_edit": "Fusion MLP (trained on first edits)",
    "fusion_multistage": "Fusion MLP (trained on all stages)",
}
comparison_display = comparison.copy()
comparison_display["method"] = comparison_display["method"].map(lambda x: method_names.get(x, x))
st.dataframe(comparison_display, use_container_width=True, hide_index=True)
recall_columns = [c for c in ("Recall@1", "Recall@5", "Recall@10") if c in comparison.columns]
if recall_columns:
    st.bar_chart(comparison_display.set_index("method")[recall_columns])

left, right = st.columns(2)
with left:
    method_keys = sorted(rankings["method"].dropna().unique().tolist())
    method_label = st.selectbox("Ranking method", [method_names.get(x, x) for x in method_keys])
    method = next((key for key in method_keys if method_names.get(key, key) == method_label), method_label)
    method_rows = rankings[rankings["method"] == method].copy()
with right:
    turns = sorted(method_rows["turn"].dropna().astype(int).unique().tolist()) if "turn" in method_rows else []
    turn = st.selectbox("Edit turn", ["All"] + turns)
    if turn != "All" and "turn" in method_rows:
        method_rows = method_rows[method_rows["turn"].astype(int) == int(turn)]

if method_rows.empty:
    st.info("There are no queries for this selection.")
    st.stop()

method_rows = method_rows.sort_values("target_rank", ascending=False)
query_id = st.selectbox("Query (worst-ranked first)", method_rows["query_id"].astype(str).tolist())
row = method_rows[method_rows["query_id"].astype(str) == query_id].iloc[0]
ranked_ids = json.loads(row["ranking"])
target_id = str(row["target_id"])
predicted_id = str(ranked_ids[0])
image_dir = root / "preview_images"

c1, c2, c3 = st.columns(3)
for col, label, path in (
    (c1, "Edited query", image_dir / str(row["query_image"])),
    (c2, "Correct original", image_dir / f"source_{target_id}.jpg"),
    (c3, "Top retrieved original", image_dir / f"source_{predicted_id}.jpg"),
):
    with col:
        st.markdown(f"**{label}**")
        if path.is_file():
            st.image(str(path), use_container_width=True)
        else:
            st.caption(f"Preview not saved: {path.name}")

rank = int(row["target_rank"])
st.write(f"Correct source rank: **{rank}** of {int(row['gallery_size'])} candidates · "
         f"{'top result is correct' if predicted_id == target_id else 'top result is incorrect'}")
st.caption(f"Query: {query_id} · edit instruction: {row.get('instruction', 'not recorded')}")

st.markdown("**Top five retrieved candidates**")
top = st.columns(min(5, len(ranked_ids)))
for i, (col, candidate_id) in enumerate(zip(top, ranked_ids[:5]), start=1):
    with col:
        candidate_path = image_dir / f"source_{candidate_id}.jpg"
        st.caption(f"#{i}{' · correct' if str(candidate_id) == target_id else ''}")
        if candidate_path.is_file():
            st.image(str(candidate_path), use_container_width=True)
        else:
            st.caption("Preview unavailable")

with st.expander("Run details and limitations"):
    st.json(record)
    st.write("Preview images are downsampled for display. Metrics are meaningful only when the run record is measured and the corrected split protocol was used.")
