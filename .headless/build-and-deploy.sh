#!/usr/bin/env bash
#
# Build an APK and deploy it to a connected device.
#
# Prerequisites:
#   1. Run install-android-sdk.sh (once per VM/container)
#   2. Have ANDROID_HOME set (or let this script default to ~/android-sdk)
#   3. ADB device reachable via either USB or wireless debugging via SSH tunnel:
#        laptop$ ssh -R 15555:<phone-ip>:<phone-adb-port> <vm>
#        vm$     adb connect localhost:15555
#     Use a port outside 5554-5585 to avoid ADB's emulator auto-detection.
#
# Usage:
#   ./build-and-deploy.sh               # build + install + launch (debug)
#   ./build-and-deploy.sh --release     # build + install + launch (release)
#   ./build-and-deploy.sh --release-aab # Build bundle for Play Console deployment, no install/launch.
#   ./build-and-deploy.sh --build-only  # just build the APK

set -euo pipefail

cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

PACKAGE="org.fischman.alarmingnotifications"

BUILD_ONLY=false
RELEASE=false
RELEASE_AAB=false
for arg in "$@"; do
  case "$arg" in
    --build-only) BUILD_ONLY=true ;;
    --release)    RELEASE=true ;;
    --release-aab) RELEASE_AAB=true ; BUILD_ONLY=true ;;
  esac
done

KEYSTORE="$HOME/keystores/android-keystore.jks"

APK="app/build/outputs/apk/debug/app-debug.apk"
if $RELEASE; then
  [ -f "$KEYSTORE" ] || { echo "ERROR: Keystore not found: $KEYSTORE" >&2; exit 1; }
  TASK="assembleRelease"
  APK="app/build/outputs/apk/release/app-release-unsigned.apk"
elif $RELEASE_AAB; then
  [ -f "$KEYSTORE" ] || { echo "ERROR: Keystore not found: $KEYSTORE" >&2; exit 1; }
  TASK="bundleRelease"
else
  TASK="assembleDebug"
  PACKAGE="${PACKAGE}.debug"
fi

echo "==> Building $TASK..."
./gradlew --warning-mode all -Dorg.gradle.deprecation.trace=true "$TASK"

function sign-apk() {
  local unsigned="$1" signed="$2"
  APKSIGNER="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)/apksigner"
  "$APKSIGNER" sign --ks-pass env:KS_PASS --ks "$KEYSTORE" --out "$signed" "$unsigned"
  echo "$signed"
}

function sign-aab() {
  jarsigner -keystore "$KEYSTORE" -storepass:env KS_PASS "$1" "key0"
}

if $RELEASE || $RELEASE_AAB; then
    read -s -p "Enter keystore password: " KS_PASS
    export KS_PASS
    echo ""
fi

if $RELEASE; then
  APK="$(sign-apk "$APK" "app/release/app-release.apk")"
fi

if $RELEASE_AAB; then
  AAB="./app/build/outputs/bundle/release/app-release.aab"
  sign-aab "$AAB"
  echo "==> AAB: $(ls -hla "$AAB")"
else
  echo "==> APK: $APK ($(du -h "$APK" | cut -f1))"
fi

if $BUILD_ONLY; then
  exit 0
fi

adb devices | grep -q 'device$' || { echo "ERROR: No ADB device connected." >&2; exit 1; }

echo "==> Installing... "
if adb install "$APK"; then
    echo
else
    echo "==> Installation failed; uninstalling first to avoid key mismatch..."
    adb uninstall "$PACKAGE" 2>/dev/null || true
    adb install "$APK"
fi


echo "==> Launching..."
# Find the launcher activity from the manifest
LAUNCHER="$(adb shell cmd package resolve-activity -c android.intent.category.LAUNCHER "$PACKAGE" | grep name= | head -1 | sed 's/.*name=//')"
if [ -n "$LAUNCHER" ]; then
  adb shell am start -n "$PACKAGE/$LAUNCHER"
else
  echo "(Could not determine launcher activity; skipping launch)" >&2
  exit 1
fi

echo "==> Done."
