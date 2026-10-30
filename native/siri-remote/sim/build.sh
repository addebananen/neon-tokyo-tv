#!/usr/bin/env bash
# Builds the feel test: the engine compiled to WebAssembly, inlined into feel.html, so the
# scrolling can be tried with a finger on a phone. Writes sim/out/siri-remote-feel.html.
set -e
cd "$(dirname "$0")/.."
cargo build --release --target wasm32-unknown-unknown --lib
mkdir -p sim/out
python3 - <<'PY'
import base64
w = base64.b64encode(open("target/wasm32-unknown-unknown/release/neon_siri.wasm", "rb").read()).decode()
open("sim/out/siri-remote-feel.html", "w").write(open("sim/feel.html").read().replace("__WASM__", w))
PY
echo "sim/out/siri-remote-feel.html"
