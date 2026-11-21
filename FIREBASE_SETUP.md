# Google account and cloud save setup

The game keeps an offline profile in SharedPreferences and syncs these fields to
Cloud Firestore after username/password or Google authentication: nickname, XP, virtual diamonds,
daily reward marker, 100K redemption marker, selected mode/map/character,
graphics settings, sensitivity, and owned cosmetic items.

## 1. Create the Firebase Android app

1. Create a Firebase project using a Google account you control.
2. Add an Android app with package name `com.battleuniverse.game.debug`.
3. In **Authentication > Sign-in method**, enable **Email/Password**. This is required for the game's username + password login.
4. (Optional) Enable **Google** only if you also want Google sign-in.
5. Create a **Cloud Firestore** database.
6. Add the SHA-1 fingerprint for the certificate that signs the APK you install.
7. Download a fresh `google-services.json` after configuring the Android app.

The username login uses Firebase Authentication's server-side unique account registration: the game converts a validated nickname into an internal, non-public email alias (`nickname@accounts.battleuniverse.app`). Firebase stores and verifies the password; the game does not keep the password in the profile or Firestore. Firebase's unique-email constraint prevents two accounts from claiming the same nickname. Players sign in using that nickname and password. Never create a public collection containing password data.

## 2. Add the configuration to GitHub Actions

Do not commit `google-services.json` to this public repository.

The workflow currently builds the **debug** APK, whose application ID is
`com.battleuniverse.game.debug`. Register that exact package in Firebase.

In the repository's Actions secrets, add a secret named
`FIREBASE_GOOGLE_SERVICES_JSON` containing the complete contents of that file.
The Android workflow writes it to `app/google-services.json` only during the build.
Without this secret, the app still builds and remains playable offline, but username/password cloud accounts and Google login are unavailable. The profile remains local only.

## 3. Firestore security rules

Copy the rules from `firestore.rules` into Firebase Console > Firestore >
Rules and publish them. Each player can read/write only their own UID document.

## 4. Keep the APK signature stable

Android only installs an APK as an update when its application ID and signing
certificate match the installed app. Create one private JKS keystore and store
it in GitHub Actions secrets (never commit it to this public repository):

- `BATTLE_UNIVERSE_KEYSTORE_BASE64`: Base64 of the JKS file
- `BATTLE_UNIVERSE_KEYSTORE_PASSWORD`
- `BATTLE_UNIVERSE_KEY_ALIAS`
- `BATTLE_UNIVERSE_KEY_PASSWORD`

The workflow uses that key to sign each debug APK when all four secrets are set.
Add that keystore's SHA-1 fingerprint to the Firebase Android app before
downloading the final `google-services.json`. Without a stable key, CI falls
back to a temporary debug key and in-place updates/Google sign-in can break.

## Important

- The first successful username/password or Google login uploads the current local profile.
- Later installs/builds signed for the same Android app can sign into the same
  Google account and restore the cloud profile.
- Offline play remains available. Changes made while signed in are queued by
  Firestore and retried when connectivity returns.
- This is a client-side game profile save. It is not an anti-cheat or a secure
  payment/diamond economy; never use it for real-money balances.
