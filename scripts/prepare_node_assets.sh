#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/nodejs-project"
npm install --legacy-peer-deps --omit=dev
rm -f "$ROOT/app/src/main/assets/nodejs-project.zip"
python3 - <<'PY'
from pathlib import Path
import zipfile
root=Path.cwd()
out=root.parent/'app/src/main/assets/nodejs-project.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in root.rglob('*'):
        if p.is_file():
            z.write(p,p.relative_to(root))
print(out)
PY
