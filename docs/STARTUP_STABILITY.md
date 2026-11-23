# Startup stability — BATTLE UNIVERSE

## Finding
`MainActivity.onCreate()` previously called `startContentSetup()` whenever `ContentPackInstaller.isInstalled()` returned false. `startContentSetup()` immediately started a download and extraction workflow for the Season 02 content archive (approximately 429 MB), before the lobby was created.

Android removes app-private files when an app is uninstalled. Therefore, reinstalling the APK without the optional private content pack caused the first launch to enter the content setup path rather than opening the base game.

## Change
- The base game now opens immediately, whether or not the optional Season 02 pack is installed.
- The 4K pack can be downloaded explicitly from the in-game content option, or an already-downloaded ZIP can be selected.
- Cancelling the ZIP picker no longer starts a background download.
- The package remains separately stored and is not bundled into the APK.

## Validation still required
- Android CI build and native library checks.
- Device test on the Redmi 12: first launch, close, second launch, and launch without the optional pack.
- Monitor memory use and verify that no large download starts unless the user explicitly selects it.

This change is a targeted startup fix, not a claim that every possible crash has been eliminated.