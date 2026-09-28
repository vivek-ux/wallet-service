# Verification commands

Run from the repository root. These checks validate source and notebook consistency; they do not run the image experiment or create retrieval metrics.

```bash
python3 - <<'PY'
import ast, json
from pathlib import Path
root = Path('thesis_image_retrieval')
for name in ('cloud_experiment_v2.py', 'results_app.py'):
    ast.parse((root / name).read_text())
nb = json.loads((root / 'run_thesis_experiments.ipynb').read_text())
embedded = ''.join(nb['cells'][3]['source']).split('%%writefile /content/cloud_experiment_v2.py\n', 1)[1]
assert embedded == (root / 'cloud_experiment_v2.py').read_text()
print('Source syntax, notebook JSON, and embedded runner: OK')
PY
```

The measured experiment must run in Colab with access to the SSCD checkpoint and Hugging Face Dataset Viewer. Its results are valid only after the notebook prints the comparison table and `run_record.json` reports `status: measured`.
