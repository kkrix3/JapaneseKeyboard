#!/usr/bin/env python3
"""Run this feature's actual IME tests on a disposable isolated emulator package."""
import json, re, subprocess
from pathlib import Path
classes=json.loads(Path('feature-build.json').read_text()).get('deviceTestClasses',[])
assert classes and all(re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.]+',c) for c in classes)
subprocess.run(['bash','./gradlew',':app:connectedFullStandardDebugAndroidTest',
    '-PfeatureDeviceTest=true',
    '-Pandroid.testInstrumentationRunnerArguments.class='+','.join(classes),
    '--no-daemon','--console=plain','--max-workers=2'],check=True)
