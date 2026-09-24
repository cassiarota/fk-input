#!/usr/bin/env bash
set -euo pipefail

# Pinned, permissively licensed Android archives built by fcitx5-android-prebuilder.
fk_commit=e9a277e3f3151f7b978945a3f8701285146d609d
fk_root="$(cd "$(dirname "$0")/.." && pwd)"
fk_checkout="$fk_root/.gradle/fk-native-source"
fk_target="$fk_root/app/src/main/cpp/prebuilt"

if [[ ! -d "$fk_checkout/.git" ]]; then
  git init "$fk_checkout"
  git -C "$fk_checkout" remote add origin https://github.com/fcitx5-android/prebuilt.git
  git -C "$fk_checkout" sparse-checkout init --no-cone
fi

git -C "$fk_checkout" sparse-checkout set --no-cone \
  '/librime/arm64-v8a/**' '/glog/arm64-v8a/**' '/leveldb/arm64-v8a/**' \
  '/lua/arm64-v8a/**' '/marisa/arm64-v8a/**' '/opencc/arm64-v8a/**' \
  '/yaml-cpp/arm64-v8a/**'
git -C "$fk_checkout" fetch --depth 1 --filter=blob:none origin "$fk_commit"
git -C "$fk_checkout" checkout --detach FETCH_HEAD

for fk_name in librime glog leveldb lua marisa opencc yaml-cpp; do
  mkdir -p "$fk_target/$fk_name"
  cp -R "$fk_checkout/$fk_name/arm64-v8a/." "$fk_target/$fk_name/"
done

echo "Native archives: $fk_commit"
