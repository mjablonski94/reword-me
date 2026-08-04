#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DESTINATION="${1:?usage: prepare-local-runtime.sh DESTINATION}"
TAG="b10246"

case "$(uname -m)" in
    arm64)
        ASSET="llama-b10246-bin-macos-arm64.tar.gz"
        SHA256="0cb070a34c6a242e0c66b9e10db2610b554bfb35de364c7276114129cd059961"
        ;;
    x86_64)
        ASSET="llama-b10246-bin-macos-x64.tar.gz"
        SHA256="53b88d3ed5abb0bfed65c6036c55935228b35c804b61ad791ab386cd94cca4e0"
        ;;
    *)
        echo "Unsupported macOS architecture: $(uname -m)" >&2
        exit 1
        ;;
esac

CACHE_DIR="$SCRIPT_DIR/LocalAIRuntime/$TAG/$(uname -m)"
ARCHIVE="$CACHE_DIR/$ASSET"
EXTRACTED="$CACHE_DIR/extracted"
URL="https://github.com/ggml-org/llama.cpp/releases/download/$TAG/$ASSET"
mkdir -p "$CACHE_DIR"

valid_archive() {
    [ -f "$ARCHIVE" ] && [ "$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')" = "$SHA256" ]
}

if ! valid_archive; then
    rm -f "$ARCHIVE.partial"
    echo "Downloading pinned llama.cpp runtime ($ASSET)..."
    curl --fail --location --progress-bar "$URL" --output "$ARCHIVE.partial"
    ACTUAL="$(shasum -a 256 "$ARCHIVE.partial" | awk '{print $1}')"
    if [ "$ACTUAL" != "$SHA256" ]; then
        rm -f "$ARCHIVE.partial"
        echo "llama.cpp SHA-256 mismatch: expected $SHA256, received $ACTUAL" >&2
        exit 1
    fi
    mv "$ARCHIVE.partial" "$ARCHIVE"
fi

# Re-extract from the verified archive on every build. This is quick for the
# small runtime and prevents a stale or locally damaged cache from being signed
# into a release merely because llama-server itself still has its executable bit.
rm -rf "$EXTRACTED"
mkdir -p "$EXTRACTED"
tar -xzf "$ARCHIVE" -C "$EXTRACTED" --strip-components=1

rm -rf "$DESTINATION"
mkdir -p "$DESTINATION"
ditto "$EXTRACTED" "$DESTINATION"
test -x "$DESTINATION/llama-server"
echo "Prepared local AI runtime: $DESTINATION"
