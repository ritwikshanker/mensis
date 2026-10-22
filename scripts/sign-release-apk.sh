#!/usr/bin/env bash
# Signs the unsigned release APK built by the release workflow with the app signing key, which stays on
# this computer (RELEASING.md). apksigner asks for the passwords; MENSIS_KS_PASS and MENSIS_KEY_PASS
# exist only for non-interactive use with a throwaway key (the workflow's dry run).
#
# Usage: scripts/sign-release-apk.sh <unsigned.apk> <keystore> <key-alias> <signed.apk>
set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo "usage: $0 <unsigned.apk> <keystore> <key-alias> <signed.apk>" >&2
  exit 2
fi
unsigned=$1
keystore=$2
key_alias=$3
signed=$4

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
tools=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
if [ -z "$tools" ] || [ ! -x "$tools/apksigner" ]; then
  echo "apksigner not found under $sdk/build-tools; set ANDROID_HOME" >&2
  exit 1
fi
# zipalign -P (page alignment of native libraries in KB) needs build-tools 35 or later.
tools_major=$(basename "$tools" | cut -d. -f1)
if [ "$tools_major" -lt 35 ]; then
  echo "build-tools 35 or later is needed, found $(basename "$tools")" >&2
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Alignment must happen before signing. AGP already aligns the APK (SQLCipher's native libraries on
# 16 KB pages), so it is only checked; an APK from elsewhere is realigned.
if "$tools/zipalign" -c -P 16 4 "$unsigned" > /dev/null; then
  cp "$unsigned" "$work/aligned.apk"
else
  echo "Realigning $unsigned"
  "$tools/zipalign" -f -P 16 4 "$unsigned" "$work/aligned.apk"
fi

passwords=()
if [ -n "${MENSIS_KS_PASS:-}" ]; then passwords+=(--ks-pass env:MENSIS_KS_PASS); fi
if [ -n "${MENSIS_KEY_PASS:-}" ]; then passwords+=(--key-pass env:MENSIS_KEY_PASS); fi

# minSdk 26 means v2 and v3 signatures only; no .idsig (v4) file next to the APK.
"$tools/apksigner" sign \
  --ks "$keystore" \
  --ks-key-alias "$key_alias" \
  ${passwords[@]+"${passwords[@]}"} \
  --v4-signing-enabled false \
  --out "$signed" \
  "$work/aligned.apk"

"$tools/zipalign" -c -P 16 4 "$signed" > /dev/null
"$tools/apksigner" verify --verbose --print-certs "$signed" | grep -E '^(Verified using|Signer #1 certificate (DN|SHA-256|SHA-1))'
echo "Signed: $signed"
