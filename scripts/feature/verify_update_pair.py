#!/usr/bin/env python3
"""Verify update compatibility using public APK metadata; never reads a private key."""
import argparse
import os
from pathlib import Path
import re
import subprocess


def inspect(apk, tools):
    info = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)'", info)
    assert package, 'Missing package metadata'
    signatures = subprocess.check_output([
        str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)
    ], text=True)
    certificates = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: (.+)$', signatures, re.M)
    assert certificates, 'No valid signing certificate'
    return package.group(1), int(package.group(2)), certificates


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('old_apk', type=Path)
    parser.add_argument('new_apk', type=Path)
    args = parser.parse_args()
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    old = inspect(args.old_apk, tools)
    new = inspect(args.new_apk, tools)
    assert old[0] == new[0] == 'com.kazumaproject.markdownhelperkeyboard.feature'
    assert old[2] == new[2], 'Signing certificates differ'
    assert new[1] > old[1], 'New versionCode must increase'
    print(f'Compatible package and signer; versionCode {old[1]} -> {new[1]}')
    print('Actual settings/DB retention and IME behavior still require device validation.')


if __name__ == '__main__':
    main()
