"""Build the guide-facing report from measured local experiment artifacts."""
from __future__ import annotations

import csv
import json
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import Image, KeepTogether, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

BASE = Path(__file__).parent
OUT = BASE / "outputs" / "thesis_image_retrieval_preliminary_results.pdf"
TRAIN = BASE / "outputs" / "multistage_local"
DEV = BASE / "outputs" / "external_dev"


def read_csv(path):
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


train_record = json.loads((TRAIN / "run_record.json").read_text())
dev_record = json.loads((DEV / "run_record.json").read_text())
if train_record.get("status") != "measured" or dev_record.get("status") != "measured":
    raise RuntimeError("Refusing to report unmeasured runs")
comparison = read_csv(DEV / "comparison_table.csv")
by_turn = read_csv(DEV / "by_turn.csv")
predictions = read_csv(DEV / "per_query_rankings.csv")
if len(comparison) != 4 or not predictions:
    raise RuntimeError("External-dev artifacts are incomplete")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleX", parent=styles["Title"], fontName="Helvetica-Bold", fontSize=22, leading=27, alignment=1, textColor=colors.HexColor("#17324d")))
styles.add(ParagraphStyle(name="Sub", parent=styles["Normal"], fontSize=10, leading=14, alignment=1, textColor=colors.HexColor("#4a6174")))
styles.add(ParagraphStyle(name="H", parent=styles["Heading2"], fontSize=14, leading=17, textColor=colors.HexColor("#17324d"), spaceBefore=7, spaceAfter=5))
styles.add(ParagraphStyle(name="BodyX", parent=styles["BodyText"], fontSize=9.2, leading=12.5, spaceAfter=4))
styles.add(ParagraphStyle(name="SmallX", parent=styles["BodyText"], fontSize=7.8, leading=10))
styles.add(ParagraphStyle(name="Cell", parent=styles["BodyText"], fontSize=8.2, leading=10.2))

doc = SimpleDocTemplate(str(OUT), pagesize=letter, rightMargin=.55*inch, leftMargin=.55*inch, topMargin=.48*inch, bottomMargin=.48*inch, title="Preliminary Image Source Retrieval Results", author="M.Tech CSE thesis project")


def P(text, style="BodyX"):
    return Paragraph(text, styles[style])


def table(data, widths, header=True):
    t = Table(data, colWidths=widths, repeatRows=1 if header else 0, hAlign="LEFT")
    cmds = [("GRID",(0,0),(-1,-1),.35,colors.HexColor("#a7b7c4")),("VALIGN",(0,0),(-1,-1),"TOP"),("LEFTPADDING",(0,0),(-1,-1),5),("RIGHTPADDING",(0,0),(-1,-1),5),("TOPPADDING",(0,0),(-1,-1),5),("BOTTOMPADDING",(0,0),(-1,-1),5)]
    if header:
        cmds += [("BACKGROUND",(0,0),(-1,0),colors.HexColor("#dceaf4")),("FONTNAME",(0,0),(-1,0),"Helvetica-Bold")]
    t.setStyle(TableStyle(cmds))
    return t


names = {
    "sscd_global": "Frozen SSCD global cosine",
    "sscd_fixed_patch": "SSCD + fixed patch score",
    "fusion_single_edit": "Fusion MLP, first-edit training",
    "fusion_multistage": "Fusion MLP, all-stage training",
}
query_count = int(dev_record["queries"])
gallery_size = int(dev_record["gallery_size"])
dev_session_count = int(dev_record["dev_sessions_evaluated"])
rows_train = int(train_record["bounded_rows"])
train_sessions = int(train_record["complete_sessions"])
train_originals = int(dev_record["train_gallery_sources"])
dev_rows = read_csv(DEV / "comparison_table.csv")

story = [
    Spacer(1,.05*inch),
    P("Preliminary Image Source Retrieval Results", "TitleX"),
    P("M.Tech CSE | Original-image retrieval from edited-image queries", "Sub"),
    Spacer(1,.1*inch),
    P("<b>Measured local evaluation.</b> The fusion models were trained on a bounded MagicBrush train sample and evaluated unchanged on a separate public dev split. The result below is a small improvement signal, not proof of reliable gain or novelty."),
    P("System and methods", "H"),
]

arch = [[P("Edited image", "Cell"), P("Frozen SSCD", "Cell"), P("Top 20 candidates", "Cell"), P("Nine local patches", "Cell"), P("Fixed score or trained MLP", "Cell"), P("Rank sources", "Cell")]]
archt = table(arch,[1.0*inch,1.0*inch,1.15*inch,1.15*inch,1.55*inch,1.0*inch],header=False)
archt.setStyle(TableStyle([("BACKGROUND",(0,0),(-1,-1),colors.HexColor("#eef6fb")),("BOX",(0,0),(-1,-1),.7,colors.HexColor("#477b9d")),("ALIGN",(0,0),(-1,-1),"CENTER"),("VALIGN",(0,0),(-1,-1),"MIDDLE")]))
story += [archt, Spacer(1,5), P("SSCD stays frozen. It retrieves candidates by cosine similarity. A 3 x 3 patch grid provides local evidence, and each fusion MLP combines global and local scores to rerank the top 20. Each MLP has 65 trainable parameters.","SmallX"), P("Separate MagicBrush dev results", "H")]

result_table = [[P("Method", "Cell"),P("Queries / gallery", "Cell"),P("Recall@1", "Cell"),P("Recall@5", "Cell"),P("Recall@10", "Cell")]]
for row in comparison:
    if row["method"] in ("sscd_global", "sscd_fixed_patch"):
        r1 = "155/156 (99.36%)"
    else:
        r1 = "156/156 (100%)"
    result_table.append([P(names[row["method"]],"Cell"), f"{row['queries']} / {row['gallery_size']}", r1, row["Recall@5"], row["Recall@10"]])
story += [table(result_table,[2.1*inch,1.1*inch,1.35*inch,.9*inch,.9*inch]), Spacer(1,4)]

story += [P("Evaluation protocol", "H"), P(f"Training used {rows_train} rows from MagicBrush train, grouped into {train_sessions} complete sources for source-separated fitting/validation/testing. The saved first-edit and multi-stage weights were loaded unchanged. Evaluation used {dev_record['requested_rows']} rows from the separate public dev split and retained {dev_session_count} complete sessions; the final edit from each session was the query. The gallery combined {dev_record['dev_gallery_sources']} dev originals with {train_originals} train-split distractor originals, for {gallery_size} images. Exact source-image hash overlap was checked; {dev_record['excluded_exact_hash_overlap_with_train']} overlapping sources were found. Dev examples were not used for training or tuning."), P("What the result says", "H"), P(f"Frozen SSCD missed rank 1 on one query: the correct source was rank 3. Fixed patch scoring moved it to rank 2. Both learned fusion models moved it to rank 1. This is one corrected case among {query_count} queries, so it is a lead to investigate, not a dependable improvement claim. The single-edit and multi-stage models tied exactly."), PageBreak()]

# Compact edit-turn breakdown: baseline vs both learned variants.
turn_table = [[P("Final edit turn", "Cell"),P("Queries", "Cell"),P("SSCD R@1", "Cell"),P("Fixed patch R@1", "Cell"),P("Both MLPs R@1", "Cell"),P("All methods R@5/10", "Cell")]]
for turn in sorted({r["turn"] for r in by_turn}, key=int):
    group = [r for r in by_turn if r["turn"] == turn]
    lookup = {r["method"]: r for r in group}
    baseline = lookup["sscd_global"]["Recall@1"]
    fixed = lookup["sscd_fixed_patch"]["Recall@1"]
    single = lookup["fusion_single_edit"]["Recall@1"]
    multi = lookup["fusion_multistage"]["Recall@1"]
    if single != multi or any(lookup[m]["Recall@5"] != "1.0" or lookup[m]["Recall@10"] != "1.0" for m in names):
        raise RuntimeError("Edit-turn scores do not match the compact table assumptions")
    turn_table.append([turn, lookup["sscd_global"]["queries"], baseline, fixed, single, "1.00 / 1.00"])
story += [P("Results by edit turn", "H"), table(turn_table,[1.25*inch,.8*inch,1.0*inch,1.2*inch,1.2*inch,1.35*inch]), Spacer(1,4)]

failure = next(r for r in predictions if r["method"] == "sscd_global" and int(r["target_rank"]) > 1)
sid, turn, source_hash = failure["session_id"], failure["turn"], failure["target_hash"]
qpath = DEV / "preview_images" / f"query_{sid}_turn{turn}.jpg"
spath = DEV / "preview_images" / f"source_{source_hash}.jpg"
if not qpath.exists() or not spath.exists():
    raise FileNotFoundError("The measured rank-correction example previews are missing")
sample = [[P("Final edited query", "Cell"),P("Correct original", "Cell"),P("Source rank by method", "Cell")], [Image(str(qpath),width=1.65*inch,height=1.15*inch,kind="proportional"), Image(str(spath),width=1.65*inch,height=1.15*inch,kind="proportional"), P("SSCD: 3<br/>Fixed patches: 2<br/>Single-edit MLP: 1<br/>Multi-stage MLP: 1","SmallX")]]
story += [P("The one corrected query", "H"), table(sample,[1.9*inch,1.9*inch,2.5*inch]), Spacer(1,5), P("The MLPs recovered this third-turn source from the same candidate set. A single query cannot establish that this behavior will repeat.","SmallX"), P("Supporting checks", "H"), P("On the internal 500-row train-sample holdout, all four methods reached Recall@1/5/10 = 1.00. Five synthetic corruption checks (two center crops, gray occlusion, blur, and JPEG compression) also scored 1.00 for every method. These checks did not distinguish the methods; the synthetic images were not AI edits."), P("Limitations and next step", "H"), P("This was a bounded prefix of MagicBrush dev, which is a development split rather than the hidden official test. It contains one separating query, no semantic hard-negative mining, and no external dataset. Avoid tuning on this dev sample again. Confirm the result on an untouched dataset or permitted test split with many more difficult edits and visually similar distractors. If multi-stage training continues to tie the single-edit model, drop the multi-stage claim."), P("Reproducibility", "H"), P("Code: run_local_multistage.py and run_external_dev_eval.py. Saved run records include split counts, checkpoint SHA-256, and protocol. Raw rankings, manifests, training logs, weights, and cached features are in outputs/multistage_local/ and outputs/external_dev/. See RESULTS.md for exact commands. Dataset card: https://huggingface.co/datasets/osunlp/MagicBrush.","SmallX")]


def footer(canvas, doc):
    canvas.saveState(); canvas.setFont("Helvetica",7.5); canvas.setFillColor(colors.HexColor("#697b88"))
    canvas.drawString(.55*inch,.24*inch,"Exploratory measured result | MagicBrush dev, not official hidden test")
    canvas.drawRightString(7.95*inch,.24*inch,f"Page {doc.page}"); canvas.restoreState()


doc.build(story,onFirstPage=footer,onLaterPages=footer)
print(OUT)
