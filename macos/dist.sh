#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

# Developer ID signing + notarization for direct distribution (produces a DMG).
# Prerequisites (Apple Developer Program):
#   export DEVELOPER_ID="Developer ID Application: Your Name (TEAMID)"
#   export NOTARY_PROFILE="a-notarytool-keychain-profile"   # xcrun notarytool store-credentials
: "${DEVELOPER_ID:?set DEVELOPER_ID}"
: "${NOTARY_PROFILE:?set NOTARY_PROFILE}"

APP="RewordMe.app"
VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' Info.plist)"
DMG="RewordMe-$VERSION.dmg"

CODESIGN_IDENTITY="$DEVELOPER_ID" ./build.sh release

# Sign with Developer ID (hardened runtime).
codesign --force --options runtime --timestamp --sign "$DEVELOPER_ID" "$APP"
codesign --verify --strict --verbose=2 "$APP"

# Notarize and staple the app first, so it validates offline once copied out of the DMG.
ditto -c -k --keepParent "$APP" "notarize-app.zip"
xcrun notarytool submit "notarize-app.zip" --keychain-profile "$NOTARY_PROFILE" --wait
xcrun stapler staple "$APP"
rm -f "notarize-app.zip"

# Build the DMG from the stapled app, then sign + notarize + staple the DMG itself.
./make-dmg.sh
codesign --force --sign "$DEVELOPER_ID" "$DMG"
xcrun notarytool submit "$DMG" --keychain-profile "$NOTARY_PROFILE" --wait
xcrun stapler staple "$DMG"

echo "Signed, notarized, stapled. Release artifact: $DMG"
