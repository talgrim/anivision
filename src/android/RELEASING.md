# Building a release APK

A release APK is shrunk (R8) and signed with your own key, so you can hand it to other people.
Debug APKs (`./gradlew assembleDebug`) also install, but are signed with a throwaway debug key.

## One-time: create a signing key

Keep the keystore **outside the repository** (or at least git-ignored) and back it up. Losing it
means you can never ship an update that installs over existing copies.

From PowerShell (the JDK bundled with Android Studio has `keytool`):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore C:\Users\Shared\RiderProjects\anivision-release.jks `
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

## Check before sharing

Install the release APK on your own phone first (`adb install -r app-release.apk`) and exercise
the whole app: Normal / Split / Dog, the blur and night boost switches and sliders, taking a photo
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
