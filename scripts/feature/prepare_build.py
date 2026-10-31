#!/usr/bin/env python3
"""Validate the merge provenance and allocate from the one shared workflow counter."""
import json, os, re, subprocess, urllib.request
from pathlib import Path

def git(*args): return subprocess.check_output(['git', *args],text=True).strip()
def version_code(run_number):
    code = 1000000000 + int(run_number)
    assert 1000000000 < code <= 2100000000, 'Feature versionCode exhausted'
    return code

def main():
    assert os.environ['GITHUB_REPOSITORY'] == 'kkrix3/JapaneseKeyboard'
    assert os.environ['GITHUB_RUN_ATTEMPT'] == '1', 'Start a NEW workflow run to rebuild; retry signing only'
    config = json.loads(Path('feature-build.json').read_text())
    for name in ['baseSha', 'featureSha', 'infrastructureSha']:
        assert re.fullmatch('[0-9a-f]{40}', config[name])
        subprocess.run(['git','merge-base','--is-ancestor',config[name],'HEAD'],check=True)
    for key, prefix in [('featureRef','feature/'),('infrastructureRef','build/')]:
        assert config[key].startswith(prefix) and re.fullmatch('[a-zA-Z0-9/._-]+',config[key])
        # The specified source must belong to this fork's named branch, not merely be an arbitrary ancestor.
        subprocess.run(['git','merge-base','--is-ancestor',config[key.replace('Ref','Sha')], 'origin/'+config[key]],check=True)
    assert not git('diff',config['infrastructureSha'],'HEAD','--','.github/workflows/feature-ci.yml','scripts/feature','app/build.gradle','app/src/feature')
    assert git('rev-parse','HEAD') == os.environ['GITHUB_SHA']
    number = int(os.environ['GITHUB_RUN_NUMBER'])
    code = version_code(number)
    # Catch an old queued run resumed after a newer successful build. No signed downgrade is produced.
    url='https://api.github.com/repos/kkrix3/JapaneseKeyboard/actions/workflows/feature-ci.yml/runs?status=success&per_page=100'
    req=urllib.request.Request(url,headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json'})
    previous=json.load(urllib.request.urlopen(req))['workflow_runs']
    assert all(int(run['run_number']) < number for run in previous), 'Older Feature run: start a new run'
    name=config['featureRef'].removeprefix('feature/')
    assert re.fullmatch('[a-z0-9._-]{1,60}',name)
    tag=f'{name}-{git("rev-parse","--short=12","HEAD")}'
    config.update(verificationSha=git('rev-parse','HEAD'),versionCode=code,buildTag=tag,
        runId=os.environ['GITHUB_RUN_ID'],runNumber=number,repository=os.environ['GITHUB_REPOSITORY'])
    Path('feature-build-resolved.json').write_text(json.dumps(config,indent=2)+'\n')
    with open(os.environ['GITHUB_ENV'],'a') as f: f.write(f'FEATURE_VERSION_CODE={code}\nFEATURE_BUILD_TAG={tag}\n')
    with open(os.environ['GITHUB_OUTPUT'],'a') as f:
        for k in ['featureSha','infrastructureSha','verificationSha','versionCode']: f.write(f'{k}={config[k]}\n')
    print(json.dumps(config,indent=2))
if __name__=='__main__': main()
