#!/usr/bin/env python3
"""Prepare pinned Android tools under /workspace; keep TLS and artifact checks enabled."""
import hashlib
import os
from pathlib import Path
import shutil
import tarfile
import urllib.parse
import urllib.request
import zipfile

TOOLS = Path('/workspace/tools')
SDK = Path('/workspace/android-sdk')
CACHE = Path('/tmp/totp-tool-downloads')
CACHE.mkdir(parents=True, exist_ok=True)

def download(url, digest):
    target = CACHE / url.rsplit('/', 1)[-1]
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != digest:
        temporary = target.with_suffix('.partial')
        urllib.request.urlretrieve(url, temporary)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != digest:
            temporary.unlink()
            raise RuntimeError('Artifact SHA256 verification failed')
        temporary.replace(target)
    return target

jdk = TOOLS / 'jdk-21.0.12.1+1'
if not (jdk / 'bin/javac').exists():
    archive = download(
        'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz',
        'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94')
    with tarfile.open(archive) as bundle:
        bundle.extractall(TOOLS, filter='data')

for name, folder, installed, digest in [
    ('platform-35_r02.zip', 'platforms', 'android-35', '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
    ('build-tools_r35_linux.zip', 'build-tools', '35.0.0', 'bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88'),
]:
    target = SDK / folder / installed
    if (target / 'source.properties').exists():
        continue
    archive = download('https://dl.google.com/android/repository/' + name, digest)
    temporary = CACHE / ('extract-' + installed)
    if temporary.exists():
        shutil.rmtree(temporary)
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(temporary)
    roots = list(temporary.iterdir())
    if len(roots) != 1 or not roots[0].is_dir():
        raise RuntimeError('Unexpected SDK archive layout')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(roots[0]), str(target))
    for executable in ['aapt', 'aapt2', 'aidl', 'apksigner', 'd8', 'dexdump', 'zipalign', 'split-select']:
        file = target / executable
        if file.exists():
            file.chmod(0o755)
    temporary.rmdir()

gradle_home = Path('/tmp/totp-gradle-home')
gradle_home.mkdir(exist_ok=True)
proxy = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    if proxy.username or proxy.password:
        raise RuntimeError('Use the platform HTTPS proxy without copying credentials')
    config = '\n'.join('systemProp.' + scheme + '.proxy' + key + '=' + value
        for scheme in ['https', 'http']
        for key, value in [('Host', proxy.hostname), ('Port', str(proxy.port or 80))]) + '\n'
    # This helper owns only its dedicated temporary Gradle home.
    (gradle_home / 'gradle.properties').write_text(config)
Path('/tmp/totp-android-user').mkdir(exist_ok=True)
Path('/tmp/totp-java-home/.android').mkdir(parents=True,exist_ok=True)
print('Android SDK 35 / Build-Tools 35.0.0 / Temurin JDK 21 prepared; artifact verification preserved.')
