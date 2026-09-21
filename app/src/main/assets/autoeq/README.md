# AutoEq preset assets

Runtime loader (`AutoEqCatalog`) concatenates ASCII files:

`assets/autoeq/b64/part00.txt` … `part22.txt`

then Base64-decodes and gunzips to obtain `presets.json` (6028 recommended FixedBandEQ presets).

## Why split base64?

`presets.json.gz` is ~200 KB binary. Some tooling (e.g. MCP `push_files`) corrupts non-UTF-8 / large binary blobs. Shipping the gzip as US-ASCII base64 parts avoids that.

## Regenerate

From the Auralis repo (with feature-pack / AutoEq clone available):

```sh
python3 feature-pack/autoeq/scripts/generate_autoeq_bundle.py \
  --autoeq-root /path/to/AutoEq \
  --out /tmp/presets.json.gz \
  --mode dedupe

# Optional: rebuild MCP-safe parts (12 KB each)
python3 - <<'PY'
from pathlib import Path
import base64
raw = Path('/tmp/presets.json.gz').read_bytes()
b64 = base64.b64encode(raw).decode('ascii')
out = Path('app/src/main/assets/autoeq/b64')
out.mkdir(parents=True, exist_ok=True)
size = 12000
for i, start in enumerate(range(0, len(b64), size)):
    (out / f'part{i:02d}.txt').write_text(b64[start:start+size], encoding='ascii')
print('parts', (len(b64) + size - 1) // size)
PY
```

Update `AutoEqCatalog.PART_COUNT` if the part count changes.

See `NOTICE` for AutoEq MIT + measurement attribution.
