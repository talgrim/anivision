# Building a release APK

A release APK is optimised (R8) and signed with your own key, so you can hand it to other people.
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
Normal / Dog / Dog + blur, rotation and resume.

Code shrinking (R8) is switched off for release builds. With the template's settings the APK
crashed at launch with `IllegalAccessError: Class kotlin.collections.d extended by class lq is
inaccessible`: R8 renamed the Kotlin stdlib's `CollectionsKt__*` classes into different packages.
The APK is larger without it, but works. If you turn it back on in `app/build.gradle.kts`, add keep
rules as needed and re-test a signed release build on a device.

## Install on another phone

Send the APK. The recipient opens it and allows "Install unknown apps" for the app they opened
it from. Needs Android 10 (API 29) or newer and OpenGL ES 3.0.

For a larger group, upload an app bundle (`./gradlew bundleRelease`) to a Play Console internal
or closed testing track instead.
