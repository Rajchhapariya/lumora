#!/usr/bin/env sh
# Downloads the official Gradle wrapper jar (not stored in this repo) and verifies it.
# Run once after cloning, then use ./gradlew as usual.
set -eu
VERSION=9.6.1
SHA256=497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7
DEST="$(dirname "$0")/../gradle/wrapper/gradle-wrapper.jar"
mkdir -p "$(dirname "$DEST")"
curl -fsSL "https://raw.githubusercontent.com/gradle/gradle/v${VERSION}/gradle/wrapper/gradle-wrapper.jar" -o "$DEST"
echo "$SHA256  $DEST" | sha256sum -c - || { rm -f "$DEST"; echo "Checksum mismatch, removed." >&2; exit 1; }
echo "Wrapper jar ready."
