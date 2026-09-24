#!/usr/bin/env bash
set -euo pipefail

fk_root="$(cd "$(dirname "$0")/.." && pwd)"
fk_sdk="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
fk_java_home="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
fk_build_tools="$fk_sdk/build-tools/36.0.0"
fk_release_version="${FK_RELEASE_VERSION:-0.1.1}"
fk_release_code="${FK_RELEASE_CODE:-1001}"
[[ "$fk_release_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid release version' >&2; exit 1; }
[[ "$fk_release_code" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid release code' >&2; exit 1; }
fk_output="$fk_root/release/fk-input-${fk_release_version}-arm64.apk"
fk_temp_dir="$(mktemp -d)"
trap 'rm -f "$fk_temp_dir/fk-aligned.apk"; rmdir "$fk_temp_dir"' EXIT

: "${FK_SIGNING_KEYSTORE:?Set FK_SIGNING_KEYSTORE to a keystore outside this repository}"
: "${FK_SIGNING_PASSWORD:?Set FK_SIGNING_PASSWORD in your shell, not in project files}"
: "${FK_SIGNING_ALIAS:?Set FK_SIGNING_ALIAS to the key alias in that keystore}"
test -f "$FK_SIGNING_KEYSTORE"

cd "$fk_root"
JAVA_HOME="$fk_java_home" ./gradlew "-PfkVersion=$fk_release_version" "-PfkVersionCode=$fk_release_code" :app:assembleRelease
mkdir -p release
"$fk_build_tools/zipalign" -P 16 -f 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  "$fk_temp_dir/fk-aligned.apk"
FK_SIGN_PASS="$FK_SIGNING_PASSWORD" JAVA_HOME="$fk_java_home" \
  "$fk_build_tools/apksigner" sign \
  --ks "$FK_SIGNING_KEYSTORE" --ks-key-alias "$FK_SIGNING_ALIAS" \
  --ks-pass env:FK_SIGN_PASS --key-pass env:FK_SIGN_PASS \
  --out "$fk_output" "$fk_temp_dir/fk-aligned.apk"
JAVA_HOME="$fk_java_home" "$fk_build_tools/apksigner" verify --verbose "$fk_output"
"$fk_build_tools/zipalign" -c -P 16 4 "$fk_output"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$fk_output"
else
  shasum -a 256 "$fk_output"
fi
