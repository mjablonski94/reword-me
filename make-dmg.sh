#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

# Build a drag-to-install DMG containing the app and an /Applications
# shortcut. Works on any build (ad-hoc for local testing); dist.sh signs
# and notarizes the result for a real release.

APP="RewordMe.app"
VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' Info.plist 2>/dev/null || echo 1.0)"
DMG="RewordMe-$VERSION.dmg"
VOL="RewordMe"

[ -d "$APP" ] || ./build.sh

STAGE="$(mktemp -d)"
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"

rm -f "$DMG"
hdiutil create -volname "$VOL" -srcfolder "$STAGE" -fs HFS+ -format UDZO -ov "$DMG" >/dev/null
rm -rf "$STAGE"

echo "Built ./$DMG"
