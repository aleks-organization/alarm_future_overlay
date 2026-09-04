# alarm_overlay

A Flutter plugin for full-screen alarms on Android and iOS.

## Features

- Schedule exact-time alarms
  - Android: `AlarmManager.setExactAndAllowWhileIdle` → full-screen intent → `AlarmActivity`
  - iOS: `UNUserNotificationCenter` local notification with sound
- Full-screen alarm over the lock screen when the screen is off (Android)
- Alarm sound (custom asset or system default) + vibration
- Snooze/Dismiss actions streamed back to Dart
- Permission handling

## Usage

```dart
import 'package:alarm_overlay/alarm_overlay.dart';

// Schedule an alarm
await AlarmOverlay.schedule(
  42,
  DateTime.now().add(Duration(minutes: 1)),
  label: 'Wake up!',
  sound: 'over_the_horizon.mp3', // must be in app assets (Android) / bundle (iOS)
);

// Cancel a scheduled alarm
await AlarmOverlay.cancel(42);

// Listen for user actions
AlarmOverlay.onAction.listen((event) {
  if (event.action == OverlayAction.close) {
    print('Alarm ${event.id} dismissed');
  } else if (event.action == OverlayAction.snooze) {
    print('Alarm ${event.id} snoozed');
  }
});

// Check / request permissions
if (!await AlarmOverlay.hasPermission()) {
  await AlarmOverlay.requestPermission();
}
```

## How the Android alarm works

1. `AlarmOverlay.schedule()` calls the native side, which persists the alarm
   (SharedPreferences) and schedules an exact alarm via
   `AlarmManager.setExactAndAllowWhileIdle()`.
2. At the alarm time, `OverlayAlarmReceiver` fires and checks the screen state:
   - **Screen off / locked** → posts a notification with `setFullScreenIntent()`
     targeting `AlarmActivity`. The system launches it **full-screen over the lock
     screen** (`setShowWhenLocked(true)` + `setTurnScreenOn(true)`), with looping
     sound and vibration.
   - **Screen on / unlocked** → posts a heads-up notification with **Snooze/Dismiss
     action buttons** in the top part of the screen (no full-screen takeover).
3. Snooze and Dismiss are handled **natively** (independent of Flutter):
   - **Dismiss** cancels the alarm and removes it from persistence.
   - **Snooze** reschedules the alarm for +10 minutes.
   The `onAction` stream is also notified (best-effort) so the Dart app can
   update its UI. Avoid double-handling snooze/dismiss in Dart.

### Background behavior & reboot

- Alarms are persisted natively, so they survive the app being killed.
- After a device reboot (or app update), `BootReceiver` restores all alarms whose
  time is still in the future.
- **Force-stop** (Settings → Force stop) cancels alarms and stops `BootReceiver`
  until the app is opened again — this is an Android system restriction.
- Snooze/Dismiss work even when the Flutter engine is not running, because they
  are handled natively. However, repeating alarms (weekly/daily) need the Dart
  app to compute and schedule the *next* occurrence, so those only continue
  while the app is alive.

## Setup

### Android

Add a **sound file** (e.g. `over_the_horizon.mp3`) to your app's
`android/app/src/main/assets/` directory.

The plugin's `AndroidManifest.xml` declares all required permissions and
components. No additional manifest changes are needed.

Required permissions (declared automatically):

| Permission | Purpose |
|------------|---------|
| `POST_NOTIFICATIONS` | Show the alarm notification (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Launch the alarm full-screen over the lock screen |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Exact alarm timing |
| `VIBRATE`, `WAKE_LOCK` | Vibration and keeping the CPU awake for sound |

> **Note (Android 13+):** the user must grant notification permission. Call
> `AlarmOverlay.requestPermission()` (or use `permission_handler`) before
> scheduling.
>
> **Note (Android 14+):** full-screen intents are restricted by the system.
> The user can enable "Allow full screen notifications" for your app in
> Settings → Apps → [Your app]. Alarm/clock apps may be auto-granted.

### iOS

Add a **sound file** (e.g. `over_the_horizon.mp3`) to your Xcode project bundle.

The iOS notification includes **Snooze/Dismiss action buttons** and uses the
**critical alert** interruption level (iOS 15+). Critical alerts require the
user to grant the "Critical Alerts" permission — request it with
`AlarmOverlay.requestPermission()`.

**Important:** iOS cannot show a custom full-screen view when the app is in the
background or terminated. The alarm fires as a notification with sound. To show
the full-screen alarm view when the user opens/taps the notification, forward
`UNUserNotificationCenterDelegate` calls to the plugin in your `AppDelegate`:

```swift
import alarm_overlay

override func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
) {
    let plugin = AlarmOverlayPlugin()
    if !plugin.handleNotificationWillPresent(notification, completionHandler: completionHandler) {
        completionHandler([.alert, .sound, .badge])
    }
}

override func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
) {
    let plugin = AlarmOverlayPlugin()
    if !plugin.handleNotificationDidReceive(response, completionHandler: completionHandler) {
        completionHandler()
    }
}
```

## API Reference

### `AlarmOverlay.schedule(id, when, {label, sound})`
Schedule an alarm overlay at the given `DateTime`.

### `AlarmOverlay.cancel(id)`
Cancel a previously scheduled alarm.

### `AlarmOverlay.stop()`
Dismiss the currently displayed alarm (full-screen activity on Android, alarm
view controller on iOS) without changing the scheduled alarm state. Useful when
deleting an alarm that is currently ringing.

### `AlarmOverlay.hasPermission()`
Returns `true` if the platform-specific permission is granted.

### `AlarmOverlay.requestPermission()`
Opens system settings for the user to grant the required permission.

### `AlarmOverlay.onAction`
A `Stream<OverlayActionEvent>` emitting user interactions from the alarm.

### `OverlayActionEvent`
| Field | Type | Description |
|-------|------|-------------|
| `action` | `OverlayAction` | `close`, `snooze`, or `unknown` |
| `id` | `int?` | The alarm ID |
| `time` | `DateTime?` | Timestamp of the action |

## License

MIT
