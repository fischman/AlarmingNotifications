# AlarmingNotifications

Never miss a transient notification again, by promoting selected
notifications to full-fledged alarms that must be dismissed
explicitly.

## Motivation
Google Calendar has a notifications feature that would be great except
my phone is usually silenced and desktop notifications are too easy to
ignore (if I'm even at a computer). I found myself setting an Android
Clock alarm for most calendar events to avoid missing events and
realized automatically promoting gcal's notifications to alarms would
be much nicer.

## What it does
Every notification on the phone is reviewed. If it is "interesting",
the phone-default Alarm ringtone will be played and a
high-priority notification will display until its `Stop` button is
tapped.

"Interesting" by default means: came from Calendar, is not a full-day
event, is not a Keep reminder automigrated to Tasks, and title doesn't
end in `/s` (for "silent"). These can be changed in app Settings.

## How it works
This app registers an [Android Notifications Listener Service](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
that gets notified of every notification the phone displays.  When a
notification matches its criteria, it triggers a high-priority
notification with (hopefully) enough text from the original
notification to tell the user what they're late for. This
high-priority notification will display over anything else the user is
currently doing to make dismissing the ringtone easy.

The above requires two permissions: the ability to read all
notifications, and the ability to post notifications of its own. After
installing, you'll have to grant these permissions, and "Read
notifications" permission at leaast will show a scary consent screen
(that might differ among Android vendors; I'm only testing on a Google
Pixel device).

## How to install
This isn't (yet?) available in Google's Play app store.

Either build using Android Studio (or gradle) from source, or download
the latest [release](https://github.com/fischman/AlarmingNotifications/releases) APK and install via adb or your side-loading
mechanism of choice.

## Note to self: How to build a new release

On an exe.dev VM:
- `./.headless/build-and-deploy.sh --release`

In a local dev-containers/sojourn container:
- `sojourn new AlarmingNotifications . -v "${HOME}/keystores:/root/keystores"`
- `./.headless/install-android-sdk.sh` (only needed once per container)
- `./android-sdk/platform-tools/adb pair <IP>:<PORT>` (Developer Options -> Wireless debugging -> Pair device with pairing code; use that popup's port, which will be different to the "IP address & Port" in Wireless debugging!)
- `./android-sdk/platform-tools/adb connect <IP>:<PORT>` (now use the Wireless debugging IP & Port, not pairing port!)
- `./.headless/build-and-deploy.sh --release`

In Android Studio:
- Build -> Generate Signed Bundle/APK -> APK
- Tap next until can select build flavor and select only `release`

Either way, the APK is generated at `./app/release/app-release.apk` so:
- Create GitHub release with: `command gh release create v0.<N> --notes "<NOTES>" ./app/release/app-release.apk`
