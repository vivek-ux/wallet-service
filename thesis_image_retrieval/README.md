# Image-source retrieval experiments

This folder contains a reproducible implementation of four retrieval methods for an AI-edited query image and a gallery of original images:

1. frozen SSCD global cosine retrieval;
2. SSCD plus fixed local patch-consensus scoring;
3. the same small fusion MLP trained only on each training source's first edit;
4. the same fusion MLP trained on all observed edit stages from each training source.

The key experiment evaluates only the final edit from complete held-out sessions. Both fusion models use the same final-edit validation set and identical held-out test queries/gallery; their training data is the only intended difference.

The latest measured check was executed locally on CPU: models trained from 500 bounded MagicBrush train rows were evaluated unchanged on 156 final edits from the separate public dev split, using a shared gallery of 413 originals. Frozen SSCD and fixed patch scoring each missed rank 1 once; both learned fusion models corrected that one query. Single-edit and multi-stage fusion tied, so this does not show a multi-stage advantage or establish a reliable gain. MagicBrush dev is not its hidden official test set. Earlier internal and synthetic checks remain documented separately. The local runner caches SSCD features and saves the split manifest, checkpoint identity, raw rankings, and metrics. Dataset rows were fetched through the [Hugging Face Dataset Viewer rows API](https://huggingface.co/docs/dataset-viewer/rows), which supports bounded row requests and exposes image fields as temporary URLs.

See [RESULTS.md](RESULTS.md) and [RUN_STATUS.md](RUN_STATUS.md) for the external-dev evaluation, other measured runs, interpretation, limits, and exact commands. External-dev rankings and previews are under `outputs/external_dev/`. The original 300-row run is preserved in `outputs/multistage_local_300/`.

## Colab notebook

`run_thesis_experiments.ipynb` remains available for a future Colab run. The results currently reported in this folder are from `run_local_multistage.py`, saved under `outputs/multistage_local`; they are not Colab results. For the exact local reproduction command, use `RESULTS.md`.

## Visual results viewer

In a Python environment with the optional packages installed, run:

```bash
python -m pip install -r thesis_image_retrieval/requirements-frontend.txt
streamlit run thesis_image_retrieval/results_app.py
```

The viewer defaults to `outputs/external_dev`. It shows the comparison table, per-query correct-source rank, and visual query/correct-source/top-result examples. It only displays saved outputs; it does not run the experiment or make results “measured.”

MagicBrush is used only when the Hugging Face dataset is accessible and its license/terms permit the planned use. The notebook does not silently substitute synthetic or undocumented images.
