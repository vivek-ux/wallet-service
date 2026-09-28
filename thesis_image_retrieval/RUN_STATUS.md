# Run status

## Latest result: separate MagicBrush dev evaluation

Fusion models were trained on the bounded MagicBrush train run and frozen for evaluation on 156 final edits from a separate dev split. The gallery had 413 originals: 156 dev sources plus 257 train-split distractors. The exact source-image hash leakage check found zero overlaps.

Frozen SSCD and SSCD plus fixed patch scoring each reached Recall@1 on 155/156 queries, with Recall@5/10 of 1.00. Both the first-edit fusion MLP and multi-stage fusion MLP reached 156/156 at Recall@1. They corrected the same single failure: a third-turn query whose original was rank 3 with SSCD and rank 2 with fixed patch scoring. This is a promising example but only one query; the two fusion models tied, so there is no measured advantage for multi-stage training.

The dev split is for development, not the hidden official test. Do not tune repeatedly or claim a reliable gain from this single difference. See `RESULTS.md` and `outputs/external_dev/` for the full measurements and per-query ranks.

## Supporting pilot results

The internal 500-row train-sample holdout and five synthetic visual-corruption checks all had perfect Recall@1/5/10 for every method. They did not distinguish the methods. The synthetic checks are not AI edits. The original 300-row run remains under `outputs/multistage_local_300/`.

## Visualizer

`results_app.py` currently displays the internal train-sample run. It defaults to `outputs/multistage_local`; external-dev rankings are available as CSV under `outputs/external_dev/`.
