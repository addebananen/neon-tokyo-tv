#!/bin/bash
# Builds the shared Rust core for iPhone, Apple TV and their simulators, generates its Swift
# interface, and packs it as Generated/NeonCore.xcframework. Runs on a Mac (CI does it).
set -euo pipefail
cd "$(dirname "$0")/.."
CORE=native/neon-core
OUT=apple/Generated
TARGETS=(aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-tvos aarch64-apple-tvos-sim)

export IPHONEOS_DEPLOYMENT_TARGET=17.0
export TVOS_DEPLOYMENT_TARGET=17.0
rustup target add "${TARGETS[@]}"
for t in "${TARGETS[@]}"; do
  cargo build --manifest-path "$CORE/Cargo.toml" --release --lib --target "$t"
done

# The Swift interface comes from the library itself (UniFFI library mode), built for this Mac
cargo build --manifest-path "$CORE/Cargo.toml" --release --lib
rm -rf "$OUT" && mkdir -p "$OUT/headers"
cargo run --manifest-path "$CORE/Cargo.toml" --release --features cli --bin uniffi-bindgen -- \
  generate --library "$CORE/target/release/libneon_core.dylib" --language swift --out-dir "$OUT/bindings" --no-format
cp "$OUT/bindings/neon_core.swift" "$OUT/neon_core.swift"
cp "$OUT/bindings/neon_coreFFI.h" "$OUT/headers/"
cp "$OUT/bindings/neon_coreFFI.modulemap" "$OUT/headers/module.modulemap"

args=()
for t in "${TARGETS[@]}"; do
  args+=(-library "$CORE/target/$t/release/libneon_core.a" -headers "$OUT/headers")
done
xcodebuild -create-xcframework "${args[@]}" -output "$OUT/NeonCore.xcframework"
echo "Built $OUT/NeonCore.xcframework"
