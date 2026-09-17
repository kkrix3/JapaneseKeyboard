#!/usr/bin/env python3
import json, re, subprocess
from pathlib import Path
config=json.loads(Path('feature-build.json').read_text())
filters=config.get('appTestFilters',[])
assert filters and all(re.fullmatch(r'[A-Za-z0-9.*_]+',p) for p in filters)
subprocess.run(['bash','./gradlew',':core:testDebugUnitTest',':custom_keyboard:testDebugUnitTest',
    '--no-daemon','--console=plain','--max-workers=2'],check=True)
subprocess.run(['bash','./gradlew',':app:testFullStandardDebugUnitTest',
    *[arg for pattern in filters for arg in ('--tests',pattern)],
    '--no-daemon','--console=plain','--max-workers=2'],check=True)
