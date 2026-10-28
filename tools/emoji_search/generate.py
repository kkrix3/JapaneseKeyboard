#!/usr/bin/env python3
"""Rebuild the offline search asset from reviewed, vendored inputs. No network needed."""
import hashlib
import json
import re
import unicodedata
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / 'symbol_keyboard/src/main/assets/emoji_search'

def key(text):
    return '-'.join(f'{ord(c):X}' for c in text if ord(c) not in (0xFE0E, 0xFE0F) and not 0x1F3FB <= ord(c) <= 0x1F3FF)

def generate():
    metadata = defaultdict(lambda: {'name': '', 'keywords': set(), 'aliases': set(), 'related': set()})
    for name in ('ja.xml', 'ja-derived.xml'):
        for entry in ET.parse(HERE / 'upstream' / name).iter('annotation'):
            record = metadata[key(entry.attrib['cp'])]
            if entry.attrib.get('type') == 'tts':
                record['name'] = entry.text or ''
            else:
                record['keywords'].update((entry.text or '').split(' | '))
    usage = json.loads((HERE / 'usages.json').read_text())
    for concept in usage['concepts']:
        terms = concept['terms'].split('|')
        for field, target in (('emoji', 'aliases'), ('related', 'related')):
            for emoji in concept.get(field, '').split():
                metadata[key(emoji)][target].update(terms)
    rows = []
    missing = []
    seen = set()
    for line in (HERE / 'bundled-emoji.tsv').read_text().splitlines():
        emoji, readings = line.split('\t')
        identity = key(emoji)
        if identity in seen:
            continue
        seen.add(identity)
        record = metadata[identity]
        if not record['name']:
            missing.append(emoji)
        record['keywords'].update(readings.split('|'))
        fields = [identity, record['name'], '|'.join(sorted(record['keywords'])),
                  '|'.join(sorted(record['aliases'])), '|'.join(sorted(record['related']))]
        assert all('\t' not in f and '\n' not in f for f in fields)
        rows.append('\t'.join(fields))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'ja.tsv').write_text('# Sumire emoji search v1; CLDR 48.2; see NOTICE.txt\n' + '\n'.join(rows) + '\n')
    license_text = (HERE / 'upstream/LICENSE').read_text()
    (OUT / 'NOTICE.txt').write_text('Japanese emoji names/keywords: Unicode CLDR 48.2 annotations/ja.xml and annotationsDerived/ja.xml.\n'
        'https://github.com/unicode-org/cldr/tree/release-48-2\n'
        'Usage concepts: Sumire contributors, MIT (repository LICENSE).\n'
        'Readings: existing bundled Sumire emoji dictionary. Categories: existing Unicode Emoji 17.0 metadata.\n\n' + license_text)
    manifest = {'cldr': '48.2', 'unicode_categories': '17.0', 'bundled_symbols': 1902,
                'identities': len(rows), 'missing_cldr_names': missing,
                'usage_concepts': len(usage['concepts']), 'sha256': {}}
    for path in sorted((HERE / 'upstream').iterdir()):
        manifest['sha256'][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    for path in sorted((ROOT / 'app/src/main/assets/emoji').glob('*.dat')):
        manifest['sha256'][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest['asset_bytes'] = (OUT / 'ja.tsv').stat().st_size
    (HERE / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    generate()
