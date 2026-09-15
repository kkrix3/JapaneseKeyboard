#!/usr/bin/env python3
"""Inspect an unsigned Full Preview APK and stage a strictly allowlisted artifact."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--version-code', required=True, type=int)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--output-dir', required=True, type=Path)
    args = parser.parse_args()
    assert 1 <= args.version_code <= 2100000000
    assert re.fullmatch(r'[0-9a-f]{40}', args.commit)
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    badging = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    app_id = 'com.kazumaproject.markdownhelperkeyboard.preview'
    assert f"name='{app_id}'" in badging.splitlines()[0]
    assert f"versionCode='{args.version_code}'" in badging.splitlines()[0]
    assert "application-label:'Sumire Preview'" in badging
    assert 'application-debuggable' not in badging
    subprocess.run([str(sdk / 'zipalign'), '-c', '-P', '16', '4', str(args.apk)], check=True)
    signature = subprocess.run([str(sdk / 'apksigner'), 'verify', str(args.apk)], capture_output=True)
    assert signature.returncode != 0, 'The input must be unsigned; Gradle must not access a key'
    with zipfile.ZipFile(args.apk) as archive:
        names = archive.namelist()
        assert not any(re.search(r'(?i)(keystore|\.(jks|p12|pfx|pem|key)$|local\.properties$)', n) for n in names)
        assert any(n.startswith('lib/arm64-v8a/') for n in names), 'Missing ARM64 native libraries'
        assert any('ggml-model' in n and n.endswith('.gguf') for n in names), 'Missing bundled Zenz model'
    args.output_dir.mkdir(parents=True, exist_ok=False)
    target = args.output_dir / 'sumire-preview-full-unsigned.apk'
    shutil.copyfile(args.apk, target)
    (args.output_dir / 'metadata.json').write_text(json.dumps({
        'commit': args.commit, 'applicationId': app_id, 'versionCode': args.version_code,
        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
    }, indent=2) + '\n')
    print(f'Verified {app_id} versionCode={args.version_code}, unsigned, Full Zenz asset present')


if __name__ == '__main__':
    main()
