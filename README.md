# LockPilot Android app

Device Owner app that locks a phone when an installment payment is overdue,
and unlocks it automatically once payment is received.

**Status: unbuilt, unverified.** This was written without Android Studio,
a JDK, or the Android SDK available in the environment that created it — no
file here has been compiled or run. Treat it as a structured starting point,
not working software, until it's opened in Android Studio and actually
built/run on an emulator.

## What's here

- Project scaffold: Gradle (Kotlin DSL), Kotlin, ViewBinding, minSdk 26 / target 35
- `LockPilotDeviceAdminReceiver` — the Device Admin receiver Android binds to
  when this app becomes Device Owner
- `DevicePolicyHelper` — thin wrapper around the three Device Owner APIs the
  product actually needs: `setUninstallBlocked`, `setLockTaskPackages`,
  and launching the lock screen
- `LockScreenActivity` — the unremovable "payment overdue" screen (shows
  over the keyguard, pinned via lock-task mode)
- `MainActivity` — placeholder setup/status screen
- A deliberately minimal `device_admin_receiver.xml` policy set (only
  `force-lock` — no wipe-data, no password management, no camera
  restrictions) matching the "no access to personal data" promise made on
  the marketing site

## What's not here yet

- **A backend.** Nothing in this app talks to a server. There's no way yet
  for it to know which shop/customer/installment plan it belongs to, or to
  receive a real "payment received" event. The database schema for this
  (shops, customers, devices, installment_plans, payments, lock_events) was
  already designed in an earlier conversation but never built.
- **QR provisioning payload.** Production Device Owner setup happens by a
  shop scanning a QR code during the Android setup wizard on a factory-reset
  phone. That QR code encodes a JSON payload (admin component name, APK
  download URL, APK signing certificate checksum) that doesn't exist yet —
  it can only be generated once the app is signed for release.
- **Any UI beyond the lock screen and a placeholder setup screen.** No
  pairing flow, no way to see payment status on-device.

## How to actually start building this

1. Install Android Studio: https://developer.android.com/studio
2. Open this folder (`lockpilot-android`) as a project — Android Studio will
   offer to generate the missing Gradle wrapper; accept that.
3. Let it sync (downloads the SDK platform/build tools this project targets
   if you don't have them yet).
4. Create a virtual device (Pixel, API 34+) in the Device Manager.
5. Run the app normally first — it'll install like any app, not as Device
   Owner yet (`MainActivity` will report "not the Device Owner").
6. To actually grant Device Owner status for development (this is the
   standard workaround — it's what real Device Owner apps' developers use
   instead of the QR flow during development):
   ```
   adb shell dpm set-device-owner com.mylockpilot.app/.LockPilotDeviceAdminReceiver
   ```
   This only works on a device/emulator with **no accounts signed in** and
   **no other apps set as device/profile owner** — same constraint the real
   QR provisioning flow has on a factory-reset phone.
7. From there, `DevicePolicyHelper(context).lockDevice(context)` can be
   triggered manually (e.g., a debug button) to test the lock screen.

## Suggested next steps, in order

1. Verify the lock screen actually pins correctly on an emulator (this repo
   has never been run — start here).
2. Design and build the backend API (matches the schema already sketched:
   shops, customers, devices, installment_plans, payments, lock_events).
3. Wire the app to that backend: pairing a freshly-provisioned device to a
   customer record, polling or push for lock/unlock events.
4. Build the QR provisioning payload generator (needs a signed release
   build first, to get the APK signing certificate's SHA-256 checksum).
5. Test the real QR-scan-during-setup-wizard flow on a factory-reset
   physical phone — this cannot be tested on an emulator alone.
