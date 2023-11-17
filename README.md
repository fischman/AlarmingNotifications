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
Every notification on the phone is reviewed. If it came from Google
Calendar and isn't a "Tomorrow" notification (i.e. for a full-day
event), the phone-default Alarm ringtone will be played and a
full-screen UI will display until a dismiss button is tapped.

## How it works
This "app" registers an [Android Notifications Listener Service](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
that gets notified of every notification the phone displays.  When a
notification matches its criteria, it triggers an AlarmActivity with
(hopefully) enough text from the notification to tell the user what
they're late for. This activity UI will display over anything else the
user is currently doing to make dismissing the ringtone easy.

The above requires two powerful permissions: the ability to read all
notifications and the ability to show a UI regardless of what else the
user is doing. After installing, you'll have to grant these
permissions using a not-very-intuitive UI (that might differ among
Android vendors; I'm only testing on a Google Pixel device).  Because
of the power of these permissions, at least one of them will show a
scary consent screen.

## How to install
This isn't (yet?) available in Google's Play app store.

Either build using Android Studio (or gradle) from source, or download
the latest [release](https://github.com/fischman/AlarmingNotifications/releases) APK and install via adb or your side-loading
mechanism of choice.


## Potential future features
- Non-stock launcher icon...
- Allow the user to configure arbitrary matching rules for notifications to trigger alarms.
- Make the alarm ringtone and volume configurable.

## Note to self: How to build a new release
In Android Studio:
- Build -> Generate Signed Bundle/APK -> APK
- Tap next until can select build flavor and select only `release`
- APK is generated at `./app/release/app-release.apk`
- Create GitHub release with: `command gh release create v0.<N> --notes "<NOTES>" ./app/release/app-release.apk`
