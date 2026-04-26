#!/usr/bin/env bash
#
# Install Android SDK command-line tools for headless builds on an
# exe.dev VM or in an ubuntu docker container.  Run once per
# VM/container.

set -euo pipefail

sudo apt install -y --no-install-recommends default-jdk-headless

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
echo "Installing Android SDK to $ANDROID_HOME"

mkdir -p "$ANDROID_HOME"
cd "$ANDROID_HOME"

# 1. Download command-line tools
if [ ! -f cmdline-tools.zip ]; then
  curl -fSL -o cmdline-tools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
fi

# 2. Unzip and arrange into the expected directory structure
#    The zip extracts as cmdline-tools/ at the top level.
#    Android SDK expects cmdline-tools/latest/.
if [ ! -d cmdline-tools/latest/bin ]; then
  rm -rf cmdline-tools-tmp cmdline-tools
  unzip -qo cmdline-tools.zip
  mv cmdline-tools cmdline-tools-tmp
  mkdir -p cmdline-tools
  mv cmdline-tools-tmp cmdline-tools/latest
fi

# 3. Accept licenses
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

# 4. Install platform-tools (adb), build-tools, and platform
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "ndk;26.1.10909125" `# Must match ../app/build.gradle.kts ndkVersion!`

echo ""
echo "Done. Add to your shell profile:"
echo "  export ANDROID_HOME=$ANDROID_HOME"
echo "  export PATH=\$ANDROID_HOME/platform-tools:\$PATH"
