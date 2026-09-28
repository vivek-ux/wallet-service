# Corrected Google Colab run

1. Open `run_thesis_experiments.ipynb` in Colab and choose a GPU runtime if one is available. The runner detects CPU or GPU automatically.
2. Run the setup and Drive cells. Use the existing persistent path `MyDrive/thesis_image_retrieval/cloud_v2`.
3. Run the script-write cell, then run the experiment cell. It fetches at most 300 rows plus one look-ahead row in 100-row pages from Hugging Face's official Dataset Viewer, instead of streaming a large Parquet shard. It drops a session cut off by the sample boundary, fetches images with bounded parallelism and retries, and saves checkpoint/feature caches.
4. Wait for the cell to finish. On success, it prints `MEASURED RESULTS` and saves a run record with `status: measured`. If it stops on a download error, the run record stays `running` at its last saved stage; no metrics should be reported.
5. Run the final table-display cell after the experiment completes. Check `comparison_table.csv`, `per_query_rankings.csv`, `dataset_manifest.csv`, `training_log.csv`, `fusion_mlp.pt`, and `run_record.json` in the Drive folder.
6. To visualize the test cases locally, download the `cloud_v2` folder and run the Streamlit app described in `README.md`.

The main comparison is frozen SSCD, fixed patch scoring, fusion trained on only first edits, and the same fusion model trained on all available edit stages. Validation and test use only each complete session's final edit. The sample remains a bounded prefix of the train split, and an interrupted download is not a measured experiment. The runner records dataset row indices, random seed, checkpoint SHA-256, dependency versions, per-query ranks, and preview images for audit.
