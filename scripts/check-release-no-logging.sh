#!/usr/bin/env bash
# Counts android.util.Log calls left in an APK's bytecode. Exits 1 if any remain.
# Usage: scripts/check-release-no-logging.sh path/to/app.apk
set -euo pipefail
apk="${1:?usage: $0 path/to/app.apk}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
dexdump="$(ls -d "$sdk"/build-tools/*/dexdump | sort -V | tail -1)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$tmp"
count=0
for dex in "$tmp"/classes*.dex; do
  n=$("$dexdump" -d "$dex" | grep -cE 'Landroid/util/Log;\.(v|d|i|w|e|wtf|println):' || true)
  count=$((count + n))
done
echo "android.util.Log calls in $(basename "$apk"): $count"
[ "$count" -eq 0 ]
