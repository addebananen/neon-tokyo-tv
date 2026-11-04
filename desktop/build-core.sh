#!/bin/bash
# Builds the shared Rust core for this computer and writes its Kotlin interface, for the Windows
# app (and, when run on Linux, for local tests). CI runs it on Windows before Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."
CORE=native/neon-core
GEN=desktop/build/generated
cargo build --manifest-path "$CORE/Cargo.toml" --release --lib
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) LIB="$CORE/target/release/neon_core.dll"; DIR=win32-x86-64 ;;
  Darwin) LIB="$CORE/target/release/libneon_core.dylib"; DIR=darwin-aarch64 ;;
  *) LIB="$CORE/target/release/libneon_core.so"; DIR=linux-x86-64 ;;
esac
rm -rf "$GEN/uniffi" "$GEN/native"
mkdir -p "$GEN/native/$DIR"
# (bindgen reads cargo metadata, so it runs inside the crate)
ROOT="$PWD"
(cd "$CORE" && cargo run --release --features cli --bin uniffi-bindgen -- \
  generate --library "$ROOT/$LIB" --language kotlin --out-dir "$ROOT/$GEN/uniffi" --no-format)
cp "$LIB" "$GEN/native/$DIR/"
echo "core ready: $GEN/native/$DIR/$(basename "$LIB")"
