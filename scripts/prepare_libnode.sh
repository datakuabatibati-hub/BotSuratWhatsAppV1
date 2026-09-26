#!/usr/bin/env bash
set -euo pipefail
VERSION="${NODE_MOBILE_VERSION:-24.21.0-0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
URL="https://github.com/fogtape/nodejs-mobile/releases/download/v${VERSION}/nodejs-mobile-android-${VERSION}.zip"
echo "Downloading $URL"
curl -fL "$URL" -o "$TMP/node.zip"
unzip -q "$TMP/node.zip" -d "$TMP/unpacked"
LIB="$(find "$TMP/unpacked" -type f -path '*arm64-v8a*' -name libnode.so | head -1)"
if [[ -z "$LIB" ]]; then
  echo "libnode.so arm64-v8a tidak ditemukan" >&2; exit 1
fi
mkdir -p "$ROOT/app/libnode/bin/arm64-v8a" "$ROOT/app/libnode/include"
cp "$LIB" "$ROOT/app/libnode/bin/arm64-v8a/libnode.so"
NODE_H="$(find "$TMP/unpacked" -type f -name node.h | head -1)"
if [[ -z "$NODE_H" ]]; then
  echo "node.h tidak ditemukan" >&2; exit 1
fi
INCROOT="$(dirname "$(dirname "$NODE_H")")"
rm -rf "$ROOT/app/libnode/include"/*
cp -R "$INCROOT"/* "$ROOT/app/libnode/include/"
echo "libnode siap: $LIB"
