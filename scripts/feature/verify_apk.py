#!/usr/bin/env python3
"""Inspect the APK itself, then stage only a key-free unsigned APK and provenance."""
import argparse, hashlib, json, os, re, shutil, subprocess, zipfile
from pathlib import Path
APP='com.kazumaproject.markdownhelperkeyboard.feature'

def method_resource_path(resources):
    # Release resource optimization may shorten res/xml/method.xml to res/<id>.xml.
    # Resolve the named resource from the compiled table instead of assuming a ZIP path.
    entry=re.search(r'^\s*resource (0x[0-9a-fA-F]+) (?:\S+:)?xml/method\b([^\n]*\n.*?)(?=^\s*resource |\Z)',
                    resources,re.M|re.S)
    assert entry, 'xml/method is missing from the compiled resource table'
    paths=re.findall(r'\(file\) (res/[^\s]+\.xml) type=XML',entry[2])
    assert len(set(paths))==1, f'Expected one method.xml resource: {paths}'
    return entry[1],paths[0]

def inspect(apk, code):
    tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0'
    def aapt(*args): return subprocess.check_output([str(tools/'aapt'),*args,str(apk)],text=True)
    badge=aapt('dump','badging')
    package=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badge)
    assert package and package[1]==APP and int(package[2])==code
    assert "application-label:'Sumire Feature'" in badge
    assert 'application-debuggable' not in badge
    manifest=subprocess.check_output([str(tools/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
    assert '.preview' not in manifest
    authorities=re.findall(r'android:authorities[^=]*="([^"]+)"',manifest)
    assert authorities and all(a.startswith(APP+'.') for a in authorities), authorities
    assert APP+'.fileprovider' in authorities
    assert '.gemma.runtime.GemmaRuntimeService' in manifest and '.zenz.runtime.ZenzRuntimeService' in manifest
    assert '.setting_activity.MainActivity' in manifest and '.ime_service.IMEService' in manifest
    resources=subprocess.check_output([str(tools/'aapt2'),'dump','resources',str(apk)],text=True)
    resource_id,method_path=method_resource_path(resources)
    assert re.search(r'android:resource[^\n]*@'+resource_id+r'\b',manifest), 'IME metadata must reference xml/method'
    method=subprocess.check_output([str(tools/'aapt'),'dump','xmltree',str(apk),method_path],text=True)
    assert 'com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity' in method
    subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
    sig=subprocess.run([str(tools/'apksigner'),'verify',str(apk)],capture_output=True)
    assert sig.returncode != 0, 'Gradle must not sign Feature APKs'
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        assert not any(re.search(r'(?i)(keystore|\.(jks|p12|pfx|pem|key)$|local\.properties$)',n) for n in names)
        assert not any(re.match(r'META-INF/[^/]+\.(RSA|DSA|EC|SF)$',n) for n in names)
        assert 'lib/arm64-v8a/libzenz.so' in names
        assert any(n.startswith('lib/arm64-v8a/') and 'litert' in n.lower() for n in names), 'Gemma runtime missing'
        model=[n for n in names if 'ggml-model' in n and n.endswith('.gguf')]
        assert model and all(z.getinfo(n).file_size > 1000000 for n in model)
        assert any(n.endswith('system.compact.kdict') for n in names)
        native=sorted(n for n in names if n.startswith('lib/') and n.endswith('.so'))
    return dict(applicationId=APP,versionCode=code,versionName=package[3],label='Sumire Feature',
        unsigned=True,debuggable=False,sha256=hashlib.file_digest(open(apk,'rb'),'sha256').hexdigest(),
        authorities=authorities,nativeLibraries=native,zenzModels=model),manifest,method

def main():
    p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--output-dir',type=Path,required=True);args=p.parse_args()
    meta=json.loads(Path('feature-build-resolved.json').read_text())
    info,manifest,method=inspect(args.apk,meta['versionCode']);meta.update(info)
    assert meta['buildTag'] in meta['versionName']
    args.output_dir.mkdir(exist_ok=False)
    target=args.output_dir/f"sumire-feature-{meta['buildTag']}-full-unsigned.apk"
    shutil.copyfile(args.apk,target);meta['apkFile']=target.name
    (args.output_dir/'metadata.json').write_text(json.dumps(meta,indent=2)+'\n')
    Path('feature-apk-inspection.txt').write_text(manifest+'\n'+method+'\n'+json.dumps(info,indent=2))
    print(json.dumps(meta,indent=2))
if __name__=='__main__':main()
