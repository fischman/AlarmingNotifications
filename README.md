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
high-priority notification will display until its `Stop` button is
tapped.

## How it works
This "app" registers an [Android Notifications Listener Service](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
that gets notified of every notification the phone displays.  When a
notification matches its criteria, it triggers a high-priority
notification with (hopefully) enough text from the original
notification to tell the user what they're late for. This
high-priority notification will display over anything else the user is
currently doing to make dismissing the ringtone easy.

The above requires two permissions: the ability to read all
notifications, and the ability to post notifications of its own. After
installing, you'll have to grant these permissions using a
not-very-intuitive UI (that might differ among Android vendors; I'm
only testing on a Google Pixel device).  At least the "Read
notifications" permission will show a scary consent screen.

## How to install
This isn't (yet?) available in Google's Play app store.

Either build using Android Studio (or gradle) from source, or download
the latest [release](https://github.com/fischman/AlarmingNotifications/releases) APK and install via adb or your side-loading
mechanism of choice.


## Potential future features
- Add a "snooze" functionality
- Allow the user to configure arbitrary matching rules for notifications to trigger alarms.
- Make the alarm ringtone and volume configurable.

## Features Not likely to be implemented
- Add (silent) notifications of upcoming alarms. 
  - Motivation: I've found myself missing Clock's Alarms' behavior of
    showing notifications for Alarms that are scheduled for less than
    2h away.
  - Pitfall: for calendar it's easy to imagine how to implement this,
    though it would require yet another permission. On the other hand
    for alarming based on arbitrary notifications, it's less obvious
    how to predict when an alarm (notification) will fire.

## Note to self: How to build a new release
In Android Studio:
- Build -> Generate Signed Bundle/APK -> APK
- Tap next until can select build flavor and select only `release`
- APK is generated at `./app/release/app-release.apk`
- Create GitHub release with: `command gh release create v0.<N> --notes "<NOTES>" ./app/release/app-release.apk`
