# Setting up push notifications (Firebase Cloud Messaging)

This app works fine without this — locking/unlocking still happens via the
15-minute background check either way. This just makes it near-instant.
These steps live in a Google account and a Supabase dashboard, so they have
to be done by hand, once.

## 1. Create a Firebase project

1. Go to https://console.firebase.google.com and sign in with any Google
   account (a personal one is fine, or make one for the business).
2. Click **Add project**. Name it `LockPilot` (or anything). Google
   Analytics is not needed — you can turn it off.
3. Once created, click the **Android icon** to add an Android app to it.
4. For "Android package name", enter exactly: `com.mylockpilot.app`
5. Nickname/SHA-1 are optional — skip them, click **Register app**.
6. Click **Download google-services.json**. Save it anywhere you'll
   remember (e.g. Downloads).
7. Tell me once it's downloaded — I'll move it into the project myself.

## 2. Generate a service account key (lets the backend send pushes)

1. In the Firebase console, click the gear icon → **Project settings**.
2. Go to the **Service accounts** tab.
3. Click **Generate new private key** → confirm. A `.json` file downloads.
4. This file is a real credential — treat it like a password. Don't paste
   its contents into chat; I'll only need to know once it's downloaded.

## 3. Deploy the push-sending function to Supabase

1. In your Supabase project dashboard, go to **Edge Functions** in the left
   sidebar.
2. Click **Create a new function**, name it `send-lock-push`.
3. I'll give you the exact code to paste in once we're at this step.
4. In that function's **Settings**, turn **off** "Verify JWT" (this
   function is only ever called by the database itself, not by users).
5. Still in that function's settings, add two **Secrets**:
   - `FIREBASE_SERVICE_ACCOUNT_JSON` — paste the *entire contents* of the
     service-account file from step 2.
   - `FCM_TRIGGER_SECRET` — I'll generate this value for you.
6. Deploy the function and copy its URL (shown at the top of the page,
   looks like `https://<project-ref>.supabase.co/functions/v1/send-lock-push`).

## 4. Tell the database about the function

Run a short SQL snippet (I'll provide it) that stores the function's URL
and the shared secret in Supabase's built-in secret vault, so the database
trigger can call it securely.

## 5. Rebuild the app

Once `google-services.json` is in place, I rebuild and reinstall the app —
nothing else needed from you after that.
