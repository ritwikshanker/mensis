#!/usr/bin/env bash
# Completes the draft GitHub release that the release workflow created for a tag: downloads the unsigned
# APK, signs it with the app signing key (sign-release-apk.sh), uploads the signed APK and SHA256SUMS and
# removes the unsigned APK. The release stays a draft; publishing it is a separate, deliberate step.
#
# Usage: scripts/finish-github-release.sh <tag> <keystore> <key-alias>
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <tag, e.g. v0.1.0> <keystore> <key-alias>" >&2
  exit 2
fi
tag=$1
keystore=$2
key_alias=$3
version=${tag#v}
unsigned="mensis-$version-unsigned.apk"
signed="mensis-$version.apk"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

gh release download "$tag" --pattern "$unsigned" --dir "$work"
"$(dirname "$0")/sign-release-apk.sh" "$work/$unsigned" "$keystore" "$key_alias" "$work/$signed"
(cd "$work" && shasum -a 256 "$signed" > SHA256SUMS && cat SHA256SUMS)

read -r -p "Upload $signed and SHA256SUMS to the draft release $tag? [y/N] " answer
if [ "$answer" != "y" ]; then
  echo "Nothing uploaded."
  exit 1
fi
gh release upload "$tag" "$work/$signed" "$work/SHA256SUMS" --clobber
gh release delete-asset "$tag" "$unsigned" --yes
echo "Draft $tag now holds $signed and SHA256SUMS. Review it, then publish:"
echo "  gh release edit $tag --draft=false"
