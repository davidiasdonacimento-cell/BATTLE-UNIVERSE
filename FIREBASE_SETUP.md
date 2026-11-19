# Google account and cloud save setup

The game keeps an offline profile in SharedPreferences and syncs these fields to
Cloud Firestore after Google authentication: nickname, XP, virtual diamonds,
daily reward marker, 100K redemption marker, selected mode/map/character,
graphics settings, sensitivity, and owned cosmetic items.

## 1. Create the Firebase Android app

1. Create a Firebase project using a Google account you control.
2. Add an Android app with package name `com.battleuniverse.game`.
3. Enable **Authentication > Sign-in method > Google**.
4. Create a **Cloud Firestore** database.
5. Add the SHA-1 fingerprint for the certificate that signs the APK you install.
6. Download a fresh `google-services.json` after enabling Google sign-in.

## 2. Add the configuration to GitHub Actions

Do not commit `google-services.json` to this public repository.

In the repository's Actions secrets, add a secret named
`FIREBASE_GOOGLE_SERVICES_JSON` containing the complete contents of that file.
The Android workflow writes it to `app/google-services.json` only during the build.
Without this secret, the app still builds and remains playable offline, but Google
login is unavailable.

## 3. Firestore security rules

Copy the rules from `firestore.rules` into Firebase Console > Firestore >
Rules and publish them. Each player can read/write only their own UID document.

## 4. APK signing matters

Android only installs an APK as an update when its application ID and signing
certificate match the installed app. The GitHub workflow currently creates a
debug APK. For Google login across future CI builds, register the SHA-1 of the
certificate used by those builds. A stable private signing key should be stored
in GitHub Actions secrets, never committed to this public repository.

## Important

- The first successful Google login uploads the current local profile.
- Later installs/builds signed for the same Android app can sign into the same
  Google account and restore the cloud profile.
- Offline play remains available. Changes made while signed in are queued by
  Firestore and retried when connectivity returns.
- This is a client-side game profile save. It is not an anti-cheat or a secure
  payment/diamond economy; never use it for real-money balances.
