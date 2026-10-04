#!/usr/bin/env python3
"""Build the same source using official Kotlin 1.9.24 and Android Build Tools 34.0.0.
Set ANDROID_SDK_ROOT and CS2_KOTLIN_LIB_DIR. No cached APK or precompiled app code is reused.
"""
import hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];build=root/'build/manual';build.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['ANDROID_SDK_ROOT']);kt=Path(os.environ['CS2_KOTLIN_LIB_DIR']);bt=sdk/'build-tools/34.0.0';android=sdk/'platforms/android-34/android.jar'
log=(build/'build.log').open('w')
def run(args):
    print('RUN',args[0],flush=True);r=subprocess.run([str(x) for x in args],cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    log.write(r.stdout);log.flush();print(r.stdout[-9000:],flush=True)
    if r.returncode:raise SystemExit(r.returncode)
def files(directory,pattern):return sorted(Path(directory).rglob(pattern))
for name in ('generated','classes','dex','test-classes'):
    p=build/name
    if p.exists():shutil.rmtree(p)
    p.mkdir()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text().replace('<manifest ','<manifest package="com.ali.cs2utility" ',1)
manifest=manifest.replace('android:name=".ui.','android:name="com.ali.cs2utility.ui.')
(build/'AndroidManifest.xml').write_text(manifest)
run([bt/'aapt2','compile','--dir',root/'app/src/main/res','-o',build/'resources.zip'])
run([bt/'aapt2','link','-I',android,'--manifest',build/'AndroidManifest.xml','--java',build/'generated',
     '--min-sdk-version','24','--target-sdk-version','34','--version-code','6','--version-name','0.6.0-test2',
     '--rename-manifest-package','com.ali.cs2utility.dust2','-0','gz','-0','png','-A',root/'app/src/main/assets','-o',build/'base.apk',build/'resources.zip'])
run(['java','com.sun.tools.javac.Main','-source','17','-target','17','-classpath',android,'-d',build/'classes',*files(build/'generated','*.java')])
compiler_cp=os.pathsep.join(str(p) for p in kt.glob('*.jar'))
runtime_cp=os.pathsep.join(str(p) for p in [android,build/'classes',kt/'kotlin-stdlib-1.9.24.jar',kt/'annotations-13.0.jar'])
run(['java','-Xmx2g','-cp',compiler_cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',runtime_cp,'-d',build/'classes',*files(root/'app/src/main/java','*.kt')])
testcp=runtime_cp+os.pathsep+str(kt/'junit-4.13.2.jar')+os.pathsep+str(kt/'hamcrest-core-1.3.jar')
run(['java','-cp',compiler_cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',testcp,'-d',build/'test-classes',*files(root/'app/src/test/java','*.kt')])
run(['java','-cp',testcp+os.pathsep+str(build/'test-classes'),'org.junit.runner.JUnitCore','com.ali.cs2utility.DomainTest','com.ali.cs2utility.RunMapTest'])
with zipfile.ZipFile(build/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
    for p in files(build/'classes','*.class'):z.write(p,p.relative_to(build/'classes').as_posix())
run([bt/'d8','--lib',android,'--min-api','24','--output',build/'dex',build/'classes.jar',kt/'kotlin-stdlib-1.9.24.jar',kt/'annotations-13.0.jar'])
with zipfile.ZipFile(build/'base.apk','a',zipfile.ZIP_DEFLATED) as z:
    for p in files(build/'dex','*.dex'):z.write(p,p.name)
run([bt/'zipalign','-f','4',build/'base.apk',build/'aligned.apk'])
out=root.parent/'CS2_Dust2_v0.6.0-test2.apk'
run([bt/'apksigner','sign','--ks',root/'tools/debug.keystore','--ks-key-alias','androiddebugkey',
     '--ks-pass','pass:android','--key-pass','pass:android','--out',out,build/'aligned.apk'])
run([bt/'apksigner','verify','--verbose','--print-certs',out]);run([bt/'zipalign','-c','4',out]);run([bt/'aapt2','dump','badging',out])
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    for p in (root/'app/src/main/assets').rglob('*'):
        if p.is_file():assert z.read('assets/'+p.relative_to(root/'app/src/main/assets').as_posix())==p.read_bytes(),str(p)
j={'apk':out.name,'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'build':'official Kotlin 1.9.24 + Build Tools 34.0.0','tests':'19 JUnit tests (10 baseline + 9 camera/loading); complete APK asset byte comparison; signature; alignment; CRC','androidRuntime':'not run; no attached device or emulator','gradleLint':'not run; offline official compiler path used'}
(root/'docs/apk-build.json').write_text(json.dumps(j,indent=2)+'\n');log.close();print(json.dumps(j,indent=2))
