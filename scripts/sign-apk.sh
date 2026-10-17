#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$sdk_dir"
test -f sign/release/private.pem
test -f sign/release/certificate.pem
apk_version=$(node -p 'require("./package.json").version')
apk_out="dist/rw6-ha-android-${apk_version}.apk"
sign_temp=$(mktemp -d)
trap 'rm -rf "$sign_temp"' EXIT
umask 077
openssl rand -hex 32 > "$sign_temp/password"
openssl pkcs12 -export -inkey sign/release/private.pem -in sign/release/certificate.pem \
  -name rw6ha -out "$sign_temp/signer.p12" -passout "file:$sign_temp/password"
mkdir -p dist
"$sdk_dir/build-tools/35.0.0/zipalign" -P 16 -f 4 android/app/build/outputs/apk/release/app-release-unsigned.apk "$sign_temp/aligned.apk"
"$sdk_dir/build-tools/35.0.0/apksigner" sign --ks "$sign_temp/signer.p12" --ks-type PKCS12 \
  --ks-key-alias rw6ha --ks-pass "file:$sign_temp/password" --out "$apk_out" "$sign_temp/aligned.apk"
node scripts/verify-apk.cjs "$apk_out"
