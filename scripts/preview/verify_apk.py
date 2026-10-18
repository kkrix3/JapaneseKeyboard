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
    parser.add_argument('--version-name', required=True)
    parser.add_argument('--output-dir', required=True, type=Path)
    args = parser.parse_args()
    assert 1 <= args.version_code <= 2100000000
    assert re.fullmatch(r'[0-9a-f]{40}', args.commit)
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    badging = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    app_id = 'com.kazumaproject.markdownhelperkeyboard.preview'
    assert f"name='{app_id}'" in badging.splitlines()[0]
    assert f"versionCode='{args.version_code}'" in badging.splitlines()[0]
    assert f"versionName='{args.version_name}'" in badging.splitlines()[0]
    assert "application-label:'Sumire Preview'" in badging
    assert 'application-debuggable' not in badging
    manifest = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'xmltree', str(args.apk), 'AndroidManifest.xml'], text=True)
    assert 'com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService' in manifest
    assert 'android.permission.BIND_INPUT_METHOD' in manifest
    assert '.gemma.runtime.GemmaRuntimeService' in manifest and '.zenz.runtime.ZenzRuntimeService' in manifest
    authorities = re.findall(r'android:authorities[^=]*="([^"]+)"', manifest)
    assert authorities and all(a.startswith(app_id + '.') for a in authorities), authorities
    assert app_id + '.fileprovider' in authorities
    resources = subprocess.check_output([str(sdk / 'aapt2'), 'dump', 'resources', str(args.apk)], text=True)
    entry = re.search(r'^\s*resource (0x[0-9a-fA-F]+) (?:\S+:)?xml/method\b([^\n]*\n.*?)(?=^\s*resource |\Z)', resources, re.M | re.S)
    assert entry, 'Missing IME method resource'
    paths = set(re.findall(r'\(file\) (res/[^\s]+\.xml) type=XML', entry[2]))
    assert len(paths) == 1, paths
    assert re.search(r'android:resource[^\n]*@' + entry[1] + r'\b', manifest)
    method = subprocess.check_output([str(sdk / 'aapt'), 'dump', 'xmltree', str(args.apk), paths.pop()], text=True)
    assert 'com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity' in method
    subprocess.run([str(sdk / 'zipalign'), '-c', '-P', '16', '4', str(args.apk)], check=True)
    signature = subprocess.run([str(sdk / 'apksigner'), 'verify', str(args.apk)], capture_output=True)
    assert signature.returncode != 0, 'The input must be unsigned; Gradle must not access a key'
    with zipfile.ZipFile(args.apk) as archive:
        names = archive.namelist()
        assert not any(re.search(r'(?i)(keystore|\.(jks|p12|pfx|pem|key)$|local\.properties$)', n) for n in names)
        for abi in ('arm64-v8a', 'x86_64'):
            assert f'lib/{abi}/libzenz.so' in names, f'Missing Zenz runtime: {abi}'
            assert any(n.startswith(f'lib/{abi}/') and 'litert' in n.lower() for n in names), f'Missing Gemma runtime: {abi}'
        assert not any(re.match(r'META-INF/[^/]+\.(RSA|DSA|EC|SF)$', n) for n in names)
        assert any(n.endswith('system.compact.kdict') for n in names), 'Missing compiled dictionary'
        assert any('ggml-model' in n and n.endswith('.gguf') for n in names), 'Missing bundled Zenz model'
    args.output_dir.mkdir(parents=True, exist_ok=False)
    target = args.output_dir / 'sumire-preview-full-unsigned.apk'
    shutil.copyfile(args.apk, target)
    (args.output_dir / 'metadata.json').write_text(json.dumps({
        'commit': args.commit, 'applicationId': app_id, 'versionCode': args.version_code,
        'versionName': args.version_name, 'unsigned': True, 'imeService': app_id + '/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService',
        'authorities': authorities, 'nativeLibraries': sorted(n for n in names if n.startswith('lib/') and n.endswith('.so')),
        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
    }, indent=2) + '\n')
    (args.output_dir / 'inspection.txt').write_text(badging + '\n' + manifest + '\n' + method)
    print(f'Verified {app_id} versionCode={args.version_code}, unsigned, Full Zenz asset present')


if __name__ == '__main__':
    main()
