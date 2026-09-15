// Regenerates the QR provisioning code for a factory-reset phone.
//
// Run once with `npm install qrcode` in this folder (not part of the
// Android/Gradle build — this is a one-off tool, not shipped in the app),
// then:
//
//   node generate-qr.js
//
// Re-run this whenever APK_DOWNLOAD_URL or the signing certificate changes
// (a new keystore means a new checksum — see README.md for how to compute
// SIGNATURE_CHECKSUM below).

const QRCode = require("qrcode");

const APK_DOWNLOAD_URL = "https://lockpilot.vercel.app/downloads/lockpilot.apk";
const SIGNATURE_CHECKSUM = "xbysiMQ_jiL3adp5_abQvlU410jco294u2kq7UTVNMk";

const payload = {
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
    "com.mylockpilot.app/.LockPilotDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": APK_DOWNLOAD_URL,
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": SIGNATURE_CHECKSUM,
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
};

const json = JSON.stringify(payload);
console.log(json);

QRCode.toFile("lockpilot-provisioning-qr.png", json, { width: 800, margin: 2 }, (err) => {
  if (err) throw err;
  console.log("Wrote lockpilot-provisioning-qr.png");
});
