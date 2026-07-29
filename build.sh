#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

CONFIG="${1:-release}"
APP="RewordMe.app"
CONTENTS="$APP/Contents"

echo "Building ($CONFIG)..."
swift build -c "$CONFIG" --product RewordMeApp
BIN="$(swift build -c "$CONFIG" --show-bin-path)"

rm -rf "$APP"
mkdir -p "$CONTENTS/MacOS" "$CONTENTS/Resources"
cp "$BIN/RewordMeApp" "$CONTENTS/MacOS/RewordMeApp"
cp Info.plist "$CONTENTS/Info.plist"
[ -f AppIcon.icns ] && cp AppIcon.icns "$CONTENTS/Resources/AppIcon.icns"

# Ad-hoc sign by default: launches without a provisioning profile. An Apple Development cert
# needs a provisioning profile (spawn fails with error 163 without one), so only sign with a
# real identity when you explicitly pass one - e.g. a Developer ID for the notarized release:
#   CODESIGN_IDENTITY="Developer ID Application: ... (TEAMID)" ./build.sh
# Trade-off of ad-hoc: the signature changes each build, so macOS drops the Accessibility grant
# on rebuild - re-grant once after building. A Developer ID build keeps the grant.
IDENTITY="${CODESIGN_IDENTITY:-}"
if [ -n "$IDENTITY" ] && codesign --force --sign "$IDENTITY" "$APP" 2>/dev/null; then
    echo "Signed with: $IDENTITY"
    codesign --verify --strict "$APP" && echo "Signature verified."
else
    codesign --force --sign - "$APP" >/dev/null 2>&1 || true
    # The fresh ad-hoc signature invalidates any previous Accessibility
    # grant, but System Settings would keep showing the stale entry as
    # enabled. Dropping it keeps the UI truthful: the app prompts again
    # and the new grant actually matches this binary.
    tccutil reset Accessibility com.mjablonski.rewordme >/dev/null 2>&1 || true
    echo "Ad-hoc signed. Accessibility grant was reset - grant it again on next launch"
    echo "(a Developer ID build keeps the grant across rebuilds)."
fi

echo "Built ./$APP"
echo "Run:  open \"./$APP\""
