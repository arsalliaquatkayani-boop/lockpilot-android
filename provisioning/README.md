# Provisioning a phone (no computer required)

This is Phase 4: turning a factory-reset phone into a LockPilot device by
scanning a QR code during Android's own setup wizard, instead of needing
`adb shell dpm set-device-owner` on a computer.

## One-time setup (already done)

1. Generated `../lockpilot-release.jks` — the permanent signing key for this
   app. **Never lose this file or its password.** Every future release must
   be signed with the same key, or the checksum below changes and every
   already-issued QR code stops matching the app.
2. Wired `app/build.gradle.kts` to sign release builds with it, reading the
   real password from `../keystore.properties` (gitignored, never committed).
3. Built the signed APK: `gradle assembleRelease`, output at
   `app/build/outputs/apk/release/app-release.apk`.
4. Copied that APK to `lockpilot/public/downloads/lockpilot.apk` (the website
   repo) so it's served at `https://lockpilot.vercel.app/downloads/lockpilot.apk`
   once committed and deployed. Update `lockpilot/src/config/config.ts`'s
   `APK_DOWNLOAD_URL` if this location ever changes.
5. Computed the certificate checksum the QR code needs:
   ```bash
   keytool -exportcert -alias lockpilot -keystore lockpilot-release.jks -storepass <password> -rfc -file cert.pem
   openssl x509 -in cert.pem -outform DER -out cert.der
   openssl dgst -sha256 -binary cert.der | openssl base64 | tr '+/' '-_' | tr -d '='
   ```
   Current value: `xbysiMQ_jiL3adp5_abQvlU410jco294u2kq7UTVNMk` (baked into
   `generate-qr.js` — not secret, it's derived from the public certificate).
6. Generated the QR code: `npm install qrcode` then `node generate-qr.js`,
   producing `lockpilot-provisioning-qr.png`.

## When to regenerate the QR code

- The APK's download URL changes (e.g. once mylockpilot.com replaces the
  Vercel URL) — update `APK_DOWNLOAD_URL` in `generate-qr.js` and re-run it.
- Never needs regenerating just because the app's *code* changed — only the
  download URL or the signing certificate affect the QR payload.

## How a shop actually uses it

1. Factory-reset (or brand new, still in setup) phone, first "Welcome"/
   language screen of Android's setup wizard.
2. Tap that screen 6 times in the same spot — this is a standard Android
   feature (not something LockPilot adds) that opens a QR scanner for
   exactly this kind of provisioning.
3. Scan `lockpilot-provisioning-qr.png`.
4. The phone downloads the APK from the URL in the QR code, verifies it
   against the signature checksum, installs it, and sets it as Device Owner
   automatically — no typing, no computer, no adb.

## What's not verified yet

This has not yet been tested against a real phone's actual setup wizard
(Phase 5). The emulator's setup flow doesn't reliably support QR provisioning
the same way real OEM setup wizards do, so this needs a real, factory-reset
Android phone to confirm end-to-end.
