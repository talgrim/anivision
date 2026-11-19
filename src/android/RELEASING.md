# Building a release APK

## Automated releases

Merging `develop` into `main` makes the `Release` workflow (release-please) open a release PR that bumps
`appVersion` in `app/build.gradle.kts` and writes `CHANGELOG.md` from the Conventional Commits (`feat` = minor,
`fix` = patch, `!` = major). `versionCode` is derived from the version (`major*10000 + minor*100 + patch`).
Merging the release PR tags the release, builds the signed APK in CI and publishes it to
[wip.talgrim.dev](https://wip.talgrim.dev), so it shows up on the download links too. Merge `main` back
into `develop` afterwards. Repository secrets: `WIP_FLY_API_TOKEN`, `ANDROID_KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; the repository setting "Allow GitHub Actions to create and
approve pull requests" must be on. The rest of this file is for building by hand.

A release APK is shrunk (R8) and signed with your own key, so you can hand it to other people.
Debug APKs (`./gradlew assembleDebug`) also install, but are signed with a throwaway debug key.

## One-time: create a signing key

Keep the keystore **outside the repository** (or at least git-ignored) and back it up. Losing it
means you can never ship an update that installs over existing copies. With Play App Signing
(recommended) this key is only the *upload* key, and Google can reset it if it is lost; the key that
signs what users install is held by Google.

From PowerShell (the JDK bundled with Android Studio has `keytool`):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore C:\Users\Shared\RiderProjects\anivision\anivision-release.jks `
  -alias anivision -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `keystore.properties.example` to `keystore.properties` in this folder and fill in the
paths and passwords. `keystore.properties` and `*.jks` are git-ignored.

## Build

```powershell
cd src/android
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Without `keystore.properties` the build
still succeeds but produces `app-release-unsigned.apk`, which phones refuse to install.

## Build an app bundle for Play

```powershell
cd src/android
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Unlike `assembleRelease`, this **fails**
when `keystore.properties` is missing, so you cannot upload an unsigned bundle by accident.

Every release gets a higher `versionCode` automatically (it is derived from the version, see below), which
is what Play requires. Keep `app/build/outputs/mapping/release/mapping.txt` for that version, so
crash traces can be de-obfuscated (Play Console accepts it with the upload).

## Check before sharing

Install the release APK on your own phone first (`adb install -r app-release.apk`) and exercise
the whole app: the Human / Dog / Cat views and Split, the blur and night boost switches and sliders, taking a photo
in each view (check the file in Pictures/AniVision), importing a photo from the gallery with Save
and Share, the About screen, rotation, and backgrounding and resuming.

A debug build and a release build are signed with different keys, so uninstall one before
installing the other (`adb uninstall com.anivision.app`).

### Code shrinking (R8)

R8 is on for release builds (`optimization { enable = true }` in `app/build.gradle.kts`). The keep
rule in `app/src/main/keepRules/rules.keep` is essential: it keeps the Kotlin stdlib's class names.
Without it an earlier build crashed at launch with `IllegalAccessError: Class kotlin.collections.d
extended by class lq is inaccessible`, because R8 moved the stdlib's `CollectionsKt__*` classes into
different packages.

R8 problems only show up in the release build, so always run the checklist above on a **signed
release APK**, not just a debug build. If the release APK crashes or a feature is missing, find the
cause with `adb logcat` (look for `AndroidRuntime`); if it cannot be fixed quickly, set
`enable = false` to ship an unshrunk (larger but working) APK.

## Install on another phone

Send the APK. The recipient opens it and allows "Install unknown apps" for the app they opened
it from. Needs Android 10 (API 29) or newer and OpenGL ES 3.0.

For a larger group, upload an app bundle (`./gradlew bundleRelease`) to a Play Console internal
or closed testing track instead.
