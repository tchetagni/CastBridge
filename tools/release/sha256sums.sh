#!/bin/bash
# Generate SHA256SUMS file for APK releases
# Usage: bash tools/release/sha256sums.sh DIR
# Writes DIR/SHA256SUMS in shasum -a 256 format, compatible with macOS/Linux
# Supports --check flag to verify checksums

set -e

if [ $# -lt 1 ]; then
    echo "Usage: $0 DIR [--check]" >&2
    exit 1
fi

DIR="${1%/}"  # Remove trailing slash
CHECK=false

if [ "$2" = "--check" ]; then
    CHECK=true
fi

if [ ! -d "$DIR" ]; then
    echo "Error: Directory '$DIR' does not exist" >&2
    exit 1
fi

SUMS_FILE="$DIR/SHA256SUMS"

if [ "$CHECK" = true ]; then
    if [ ! -f "$SUMS_FILE" ]; then
        echo "Error: $SUMS_FILE does not exist" >&2
        exit 1
    fi
    cd "$DIR"
    shasum -a 256 --check SHA256SUMS
    exit $?
fi

# Generate SHA256SUMS
cd "$DIR"
find . -maxdepth 1 -type f ! -name "SHA256SUMS" ! -name ".*" -print0 | \
    sort -z | \
    xargs -0 shasum -a 256 | \
    sed 's|^\([a-f0-9]*\)  \./\(.*\)$|\1  \2|' > SHA256SUMS

echo "Generated $SUMS_FILE"
head -1 "$SUMS_FILE"
