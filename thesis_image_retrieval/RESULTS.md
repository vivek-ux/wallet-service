# Preliminary measured results

## Separate MagicBrush dev evaluation

**Status: measured locally on CPU.** Fusion weights were trained on a bounded MagicBrush `train` sample and then loaded unchanged. Evaluation used the separate public `dev` split: 300 rows yielded 156 complete sessions and one final-edit query per session. The gallery contained all 156 dev originals plus 257 source originals sampled from MagicBrush `train`, for 413 candidates. No exact original-image hash overlapped between the training and dev source sets. No dev examples were used for fusion training or tuning.

| Method | Queries | Gallery originals | Recall@1 | Recall@5 | Recall@10 |
|---|---:|---:|---:|---:|---:|
| Frozen SSCD global cosine | 156 | 413 | 155/156 (0.9936) | 1.00 | 1.00 |
| SSCD + fixed local patch score | 156 | 413 | 155/156 (0.9936) | 1.00 | 1.00 |
| Fusion MLP, first edit only | 156 | 413 | 156/156 (1.00) | 1.00 | 1.00 |
| Fusion MLP, all observed edit stages | 156 | 413 | 156/156 (1.00) | 1.00 | 1.00 |

The global baseline's one rank-1 error was a third-turn edit. Its correct source was at rank 3. Fixed patch scoring moved it to rank 2; both learned fusion models moved it to rank 1. On this evaluation the fusion models corrected one query, a difference too small to establish a reliable improvement. The single-edit and multi-stage models tied exactly, so this run does not show a benefit from multi-stage training.

Recall@1 by final edit turn was 1.00 for all methods on turns 1 and 2 (62 and 44 queries). On turn 3, frozen SSCD and fixed patch scoring were 49/50 (0.98); both fusion models were 50/50. Recall@5 and Recall@10 were 1.00 for every method and turn.

## Earlier train-sample and synthetic checks

On the internal 500-row train-sample holdout (51 queries, 257-image gallery), all four methods had Recall@1/5/10 = 1.00. A separate controlled stress check applied five visual corruptions to those 51 resized source previews; all methods remained at 1.00. That check used synthetic crops, occlusion, blur, and compression, not AI edits. It did not distinguish the methods. The 300-row pilot is preserved under `outputs/multistage_local_300/`.

## Interpretation

The separate dev result is a useful lead: learned score fusion fixed one failure where global SSCD ranked the source third. It is not enough evidence for a thesis improvement claim. Only one query separated the learned models from the baseline, and single-edit training performed identically to multi-stage training. MagicBrush dev is a development split, not the hidden official test split; avoid further tuning on it. The next confirmation should use another untouched dataset or the hidden test split if its access rules allow it, substantially more hard queries, and similar-image distractors. Keep the same gallery and queries for every method.

## Reproduce the train run

From the repository root, with `outputs/multistage_local/requirements_frozen.txt` dependencies and the cached SSCD checkpoint:

```bash
PYTHONPATH=/private/tmp/thesis-torch-runtime /Users/viveknegi/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 thesis_image_retrieval/run_local_multistage.py --rows 500 --root thesis_image_retrieval/outputs/multistage_local --checkpoint thesis_image_retrieval/outputs/pilot_100_sessions/sscd_disc_mixup.torchscript.pt
```

## Reproduce the separate dev evaluation

After the train run has completed, use the saved, unchanged fusion weights:

```bash
PYTHONPATH=/private/tmp/thesis-torch-runtime /Users/viveknegi/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 thesis_image_retrieval/run_external_dev_eval.py --rows 300
```

Outputs are under `outputs/external_dev/`: `run_record.json`, `dataset_manifest.csv`, `comparison_table.csv`, `by_turn.csv`, `per_query_rankings.csv`, and query/source previews. All model embeddings share the same checkpoint SHA-256 recorded in the run records. The Dataset Viewer supports bounded row slices; this runner requests 50 rows per call. The 500-row training run, model weights, and feature cache are saved separately under `outputs/multistage_local/`.
