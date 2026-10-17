const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');

function verifyApk(file,{tools=path.join(process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT||'', 'build-tools/35.0.0')}={}) {
  assert(fs.existsSync(file),'Missing APK');
  const signature=execFileSync(path.join(tools,'apksigner'),['verify','--verbose','--print-certs',file],{encoding:'utf8'});
  assert(/Verified using v2 scheme .*: true/.test(signature),'APK v2 signature missing');
  const fingerprint=/Signer #1 certificate SHA-256 digest: ([a-f0-9]+)/i.exec(signature);
  const expected=new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256.replaceAll(':','').toLowerCase();
  assert(fingerprint && fingerprint[1].toLowerCase()===expected,'APK and watch signing identities differ');
  const metadata=execFileSync(path.join(tools,'aapt'),['dump','badging',file],{encoding:'utf8'});
  const version=JSON.parse(fs.readFileSync('package.json')).version;
  const watch=JSON.parse(fs.readFileSync('src/manifest.json'));
  assert(metadata.includes("package: name='com.vela.rw6ha'"),'Companion package must match watch');
  assert(metadata.includes("versionName='"+version+"'"),'APK version mismatch');
  assert(metadata.includes("versionCode='"+watch.versionCode+"'"),'APK version code mismatch');
  assert(metadata.includes("sdkVersion:'26'"),'Unexpected minimum Android version');
  assert(metadata.includes("targetSdkVersion:'35'"),'Unexpected Android target');
  assert(metadata.includes("launchable-activity: name='com.vela.rw6ha.MainActivity'"),'Missing launcher');
  const permissions=execFileSync(path.join(tools,'aapt'),['dump','permissions',file],{encoding:'utf8'});
  assert(permissions.includes('android.permission.INTERNET'),'Missing foreground Home Assistant network permission');
  assert(!/android\.permission\.(CAMERA|ACCESS_NETWORK_STATE|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE)/.test(permissions),'Unexpected network/camera/storage permission');
  const components=execFileSync(path.join(tools,'aapt'),['dump','xmltree',file,'AndroidManifest.xml'],{encoding:'utf8'});
  assert(!/E: (service|receiver)\b/.test(components),'Background service or receiver must not be shipped');
  console.log('Verified APK signatures, shared watch identity, version, launcher and foreground network permissions:',file);
}
module.exports={verifyApk};
if(require.main===module) {try {assert(process.argv[2],'Usage: node scripts/verify-apk.cjs <file.apk>');verifyApk(process.argv[2]);}catch(e){console.error('APK verification failed:',e.message);process.exitCode=1;}}
