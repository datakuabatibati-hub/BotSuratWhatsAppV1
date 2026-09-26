#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/nodejs-project"

npm install --legacy-peer-deps --omit=dev

# Workaround untuk bug pairing-code Baileys 2026:
# link_code_companion_reg kadang datang tanpa primary_identity_pub dan
# versi upstream saat ini dapat melempar "Invalid buffer".
python3 - <<'PY'
from pathlib import Path

candidates = [
    Path('node_modules/@whiskeysockets/baileys/lib/Socket/messages-recv.js'),
    Path('node_modules/baileys/lib/Socket/messages-recv.js'),
]

p = next((x for x in candidates if x.exists()), None)
if p is None:
    raise SystemExit('ERROR: messages-recv.js Baileys tidak ditemukan')

s = p.read_text(encoding='utf-8')
marker = "const linkCodeCompanionReg = getBinaryNodeChild(node, 'link_code_companion_reg');"
guard = """const linkCodeCompanionReg = getBinaryNodeChild(node, 'link_code_companion_reg');
            if (!getBinaryNodeChildBuffer(linkCodeCompanionReg, 'primary_identity_pub')) {
                logger.debug({ node }, 'Skipping incomplete link_code_companion_reg notification');
                break;
            }"""

if 'Skipping incomplete link_code_companion_reg notification' not in s:
    if marker not in s:
        raise SystemExit('ERROR: titik patch pairing Baileys tidak ditemukan; struktur versi berubah')
    s = s.replace(marker, guard, 1)
    p.write_text(s, encoding='utf-8')
    print(f'Patched Baileys pairing guard: {p}')
else:
    print(f'Baileys pairing guard already present: {p}')
PY

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
