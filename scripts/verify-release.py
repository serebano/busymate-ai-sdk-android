from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
m=json.loads((root/'release-manifest.json').read_text())
for name,expected in m['files'].items():
    actual=hashlib.sha256((root/name).read_bytes()).hexdigest()
    assert actual==expected, f"Frozen source changed: {name}"
    print(f"verified {name} {actual}")
# The SDK's own version constant (BusymateLaunch.SDK_VERSION) must equal the manifest: one source, no drift.
import re
launch=(root/'sdk/src/main/java/ai/busymate/BusymateLaunch.kt').read_text()
const=re.search(r'const val SDK_VERSION = "([^"]+)"',launch)
assert const and const.group(1)==m['version'], 'BusymateLaunch.SDK_VERSION must equal release-manifest.json version'

# Every release attaches each adapter source under its basename (plus SHA256SUMS).
assets=m.get('adapterAssets') or []
assert assets, 'release-manifest.json must list adapterAssets'
names=[Path(a).name for a in assets]
assert len(set(names))==len(names), 'adapterAssets basenames must be unique'
for a in assets:
    assert a in m['files'], f'adapter asset {a} must be pinned in files'
    actual=hashlib.sha256((root/a).read_bytes()).hexdigest()
    assert actual==m['files'][a], f'adapter asset changed: {a}'
    print(f"verified adapter asset {Path(a).name} {actual}")
