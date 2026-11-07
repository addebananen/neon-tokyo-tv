#!/bin/bash
# Builds Neon Tokyo on this Mac and installs it on an Apple TV (or iPhone) on the same network,
# signed with your own Apple ID in Xcode (a free one works; the app then runs for 7 days).
#
#   apple/mac-install.sh            Apple TV (default)
#   apple/mac-install.sh iOS        iPhone
#
# Before the first run:
#   1. Xcode › Settings › Accounts: add your Apple ID.
#   2. Apple TV: Settings › Remotes and Devices › Remote App and Devices.
#      Mac: Xcode › Window › Devices and Simulators › pick the Apple TV › Pair, type the code.
# Options: NEON_TEAM=XXXXXXXXXX (your team id), NEON_BUNDLE_ID=... (if the default id is taken),
#          NEON_DEVICE=<name or id> (when more than one device is paired).
set -euo pipefail
cd "$(dirname "$0")"
PLATFORM="${1:-tvOS}"
if [ "$PLATFORM" = iOS ]; then SDK=iphoneos; RUST_T=aarch64-apple-ios; KIND=iPhone
else PLATFORM=tvOS; SDK=appletvos; RUST_T=aarch64-apple-tvos; KIND=appleTV; fi
say() { printf '\n\033[1;35m▸ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

say "Checking tools"
xcodebuild -version >/dev/null 2>&1 || die "Xcode is not selected. Run: sudo xcode-select -s /Applications/Xcode.app && sudo xcodebuild -license accept"
xcodebuild -version | head -1
if ! command -v xcodegen >/dev/null; then
  command -v brew >/dev/null || die "Homebrew is missing. Install it from https://brew.sh and run this again."
  brew install xcodegen
fi
if ! command -v cargo >/dev/null; then
  [ -x "$HOME/.cargo/bin/cargo" ] || curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
  export PATH="$HOME/.cargo/bin:$PATH"
fi
cargo --version

say "Finding your Apple TV"
J=$(mktemp); xcrun devicectl list devices --json-output "$J" >/dev/null 2>&1 || true
DEV=$(/usr/bin/python3 - "$J" "$KIND" "${NEON_DEVICE:-}" <<'PY'
import json, sys
path, kind, want = sys.argv[1], sys.argv[2].lower(), sys.argv[3].lower()
try: devs = json.load(open(path))["result"]["devices"]
except Exception: devs = []
hits = []
for d in devs:
    hw, props = d.get("hardwareProperties", {}), d.get("deviceProperties", {})
    name = props.get("name", "")
    if hw.get("deviceType", "").lower() != kind: continue
    if want and want not in (name.lower(), d.get("identifier", "").lower(), hw.get("udid", "").lower()): continue
    hits.append((d.get("connectionProperties", {}).get("tunnelState") != "unavailable", d["identifier"], hw.get("udid", ""), name))
hits.sort(reverse=True)
if hits: print("\t".join(hits[0][1:]))
PY
)
[ -n "$DEV" ] || die "No paired $PLATFORM device found. Pair it in Xcode › Window › Devices and Simulators first (see the top of this script)."
IFS=$'\t' read -r DEV_ID DEV_UDID DEV_NAME <<< "$DEV"
echo "$DEV_NAME ($DEV_UDID)"

say "Finding your signing team"
TEAM="${NEON_TEAM:-}"
if [ -z "$TEAM" ]; then
  TEAM=$(security find-certificate -a -c "Apple Development" -p 2>/dev/null \
    | openssl x509 -noout -subject 2>/dev/null | sed -n 's/.*OU *= *\([A-Z0-9]\{10\}\).*/\1/p' | head -1 || true)
fi
if [ -z "$TEAM" ]; then
  TEAM=$(defaults read com.apple.dt.Xcode IDEProvisioningTeamByIdentifier 2>/dev/null \
    | sed -n 's/.*teamID = \([A-Z0-9]\{10\}\).*/\1/p' | head -1 || true)
fi
[ -n "$TEAM" ] || die "No team found. Add your Apple ID in Xcode › Settings › Accounts, or run with NEON_TEAM=<your team id>."
BUNDLE="${NEON_BUNDLE_ID:-com.neontokyo.tv}"
echo "team $TEAM, app id $BUNDLE"

say "Building the shared core for $PLATFORM (first time takes a few minutes)"
NEON_CORE_TARGETS="$RUST_T" ./build-core.sh

say "Making the Xcode project"
xcodegen generate --quiet

say "Building and signing Neon Tokyo"
LOG=build/mac-$PLATFORM.log; mkdir -p build
if ! xcodebuild -project NeonTokyo.xcodeproj -scheme NeonTokyo-$PLATFORM -configuration Release -sdk $SDK \
    -destination "id=$DEV_UDID" -derivedDataPath build/mac-$PLATFORM \
    -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
    DEVELOPMENT_TEAM="$TEAM" PRODUCT_BUNDLE_IDENTIFIER="$BUNDLE" CODE_SIGN_STYLE=Automatic \
    NEON_VERSION="0.2.0-mac.$(git rev-parse --short HEAD 2>/dev/null || echo local)" NEON_BUILT_AT="$(date +%s)000" \
    TRAKT_CLIENT_ID="${TRAKT_CLIENT_ID:-}" TRAKT_CLIENT_SECRET="${TRAKT_CLIENT_SECRET:-}" build > "$LOG" 2>&1; then
  grep -E "error:|fatal" "$LOG" | sort -u | head -40
  tail -20 "$LOG"
  die "Build failed (full log: apple/$LOG). If it says the app id is not available, run again with NEON_BUNDLE_ID=com.<yourname>.neontokyo"
fi
APP=$(find build/mac-$PLATFORM/Build/Products -maxdepth 2 -name NeonTokyo.app | head -1)

say "Installing on $DEV_NAME"
xcrun devicectl device install app --device "$DEV_ID" "$APP"
xcrun devicectl device process launch --device "$DEV_ID" "$BUNDLE" || \
  echo "Installed. If it does not open: on the Apple TV, Settings › General › Privacy & Security › Developer Mode, and trust your Apple ID under Settings › General › Device Management."
say "Done: Neon Tokyo is on $DEV_NAME"
