#!/usr/bin/env python3
"""Inspect the exact disposable debug APK installed by the API 34 device test."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[2]
apks = list((root / 'app/build/outputs/apk/fullStandard/debug').glob('*.apk'))
assert len(apks) == 1, apks
apk = apks[0]
aapt = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0/aapt'
badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
assert package and package[1] == 'com.kazumaproject.markdownhelperkeyboard.preview.testbench'
assert 'application-debuggable' in badging
with zipfile.ZipFile(apk) as archive:
    assert archive.read('assets/emoji_search/ja.tsv') == (root / 'symbol_keyboard/src/main/assets/emoji_search/ja.tsv').read_bytes()
    assert b'UNICODE LICENSE V3' in archive.read('assets/emoji_search/NOTICE.txt')
    for abi in ['arm64-v8a', 'x86_64']:
        assert f'lib/{abi}/libzenz.so' in archive.namelist()
    assert not any(re.search(r'(?i)(keystore|\.(jks|p12|pfx|pem|key)$|local\.properties$)', name) for name in archive.namelist())
    search_assets = {name: {'bytes': archive.getinfo(name).file_size, 'compressed_bytes': archive.getinfo(name).compress_size}
                     for name in archive.namelist() if name.startswith('assets/emoji_search/')}
# Verify the generated debug signature without printing keys or certificate details.
apksigner = aapt.parent / 'apksigner'
subprocess.run([str(apksigner), 'verify', str(apk)], check=True)
out = root / 'emoji-search-debug-artifact'
out.mkdir(exist_ok=True)
shutil.copyfile(apk, out / 'sumire-emoji-search-debug-testbench.apk')
metadata = {'commit': os.environ['GITHUB_SHA'], 'sourceTree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], text=True).strip(), 'applicationId': package[1], 'versionCode': int(package[2]),
            'versionName': package[3], 'debuggable': True, 'signedWith': 'CI-generated Android debug key',
            'testedApi': 34, 'abis': ['arm64-v8a', 'x86_64'], 'bytes': apk.stat().st_size,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'searchAssets': search_assets}
(out / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(metadata, indent=2))
