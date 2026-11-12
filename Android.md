# AniVision: Android Scaffolding Plan

## 1. Context

**Goal:** An Android app that shows the live camera view as a dog would see it, i.e. with canine color vision. Photo/video capture and other animals (cat, etc.) come later.

**Why Android first:** Building for iOS needs a Mac and Xcode (the paid Apple Developer Program is only needed to distribute or to run on-device beyond 7 days). Android needs neither. A one-time $25 Google Play fee applies only at publishing time.

### What we are simulating

Dogs are **dichromats**. They have two cone types:

| Cone | Peak | Human equivalent |
|---|---|---|
| S | ~429-435 nm (blue-violet) | S (~420 nm) |
| L/M | ~555 nm (yellow-green) | closest to L (~560 nm) |

This makes canine vision close to human **deuteranopia**:

- Blues and yellows are distinguishable.
- Reds, oranges and greens collapse into yellowish, brownish or gray tones.
- The neutral point (a wavelength that looks gray) is around 480 nm.

Optional realism extras:

- **Lower acuity** (~20/75): apply a mild blur.
- **Better low-light sensitivity** (tapetum lucidum): slightly lift shadows.

Nobody knows what dogs subjectively experience. The app simulates the information their cones capture, so UI copy should say "approximation of a dog's color vision".

### The color transform

1. sRGB to linear RGB.
2. Linear RGB to LMS (Hunt-Pointer-Estevez or Viénot).
3. Project onto the dichromat plane (replace the missing cone's signal with a combination of the others).
4. LMS back to linear RGB, then re-encode to sRGB.

Steps 2-4 collapse into one 3x3 matrix applied in linear RGB. A starting point is the standard deuteranopia matrix (gamma-space approximation):

```
R' = 0.625 R + 0.375 G
G' = 0.700 R + 0.300 G
B' = 0.300 G + 0.700 B
```

The final matrix should be derived from canine cone sensitivities (peaks 429 / 555 nm, Govardovskii nomogram) against typical sRGB display primaries. This is validated in the Python prototype (see section 6).

## 2. Decisions

| Topic | Decision | Reason |
|---|---|---|
| Language / framework | Kotlin, native Android | Per-frame GPU image processing is the core of the app. Native gives the cleanest camera and GL access. |
| UI | Jetpack Compose | Current default. `PreviewView` is embedded via `AndroidView`. |
| Camera | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`; later `camera-video`, `camera-effects`) | Lifecycle-aware, handles device quirks. |
| Processing | GPU (GLSL fragment shader). Not CPU pixel loops. | Real-time at 30 fps without draining the battery. |
| minSdk | 26 | Covers nearly all active devices. Does not depend on the API 31 `RenderEffect`. |
| targetSdk / compileSdk | Latest stable | Play Store requirement. |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) | Android Studio default. |
| Package / applicationId | `com.anivision.app` (kept unless changed before the first release) | Hard to change after publishing. |
| VCS | Git, git-flow style: work on `feature/*`, PRs target `develop`, `main` is for releases only | Repo convention. |

Library versions are not pinned in this document. Use the latest stable releases from the Android Studio template and Google Maven when scaffolding, and record them in `libs.versions.toml`.

## 3. Target project layout

```
anivision/
├── Android.md                  # this file
├── Python.md                   # prototype plan (done)
├── .gitignore
├── README.md
└── src/
    ├── prototype/              # Python color-transform prototype (section 6, done)
    │   ├── anivision/          # package, run with `python -m anivision`
    │   ├── export/             # matrix, golden vectors, Kotlin and GLSL constants for the app
    │   ├── samples/
    │   ├── scripts/
    │   ├── tests/
    │   └── requirements.txt
    └── android/                # Android Studio project root
        ├── settings.gradle.kts
        ├── build.gradle.kts
        ├── gradle/libs.versions.toml
        └── app/
            ├── build.gradle.kts
            └── src/main/
                ├── AndroidManifest.xml
                ├── java/com/anivision/app/
                │   ├── MainActivity.kt
                │   ├── camera/        # CameraX setup, permission handling
                │   ├── render/        # GL renderer / shader pipeline
                │   ├── vision/        # AnimalVision profiles (matrix, blur, etc.)
                │   └── ui/            # Compose screens and theme
                ├── assets/shaders/    # *.glsl (vertex + fragment)
                └── res/
```

The Android project lives in `src/android/`, next to `src/prototype/`, in the same repo. Git ignore rules live only in the root `.gitignore`; the project has no `.gitignore` files of its own.

## 4. Prerequisites (developer machine)

The working copy is the current directory (`C:\Users\Shared\RiderProjects\anivision`, seen from WSL2 as `/mnt/c/Users/Shared/RiderProjects/anivision`). It lives on the Windows filesystem, so Android Studio opens `src/android/` directly and Gradle builds run on the Windows side. Use WSL for git, the Python prototype and file scaffolding.

Needed:

- JDK 17 (bundled with Android Studio)
- Android Studio (latest stable) with the SDK Platform, Build-Tools and Platform-Tools
- A **physical Android device** with USB debugging (the test device runs Android 11, API 30, which is above minSdk 26). The emulator's virtual camera is poor for judging color, so real hardware is strongly preferred.
- Python 3.10+ with `numpy`, `pillow` for the prototype

## 5. Scaffolding steps

### Phase 0: Repo hygiene
1. Extend `.gitignore` with Android/Gradle (`.gradle/`, `build/`, `local.properties`, `*.iml`, `captures/`, `.cxx/`) and OS junk. `.idea/` and the Python entries already exist.
2. Add a minimal `README.md` (what the app is, how to build).
3. Commit on `feature/android-scaffold`.

### Phase 1: Empty Android app
1. In Android Studio: New Project, "Empty Activity" (Compose). Name `AniVision`, package `com.anivision.app`, minSdk 26, Kotlin DSL. Save it into `src/android/`.
2. Confirm it builds and launches on the device ("Hello" screen).
3. Commit.

### Phase 2: Camera preview (no filter)
1. Add CameraX dependencies to the version catalog and `app/build.gradle.kts`.
2. Declare `<uses-permission android:name="android.permission.CAMERA"/>` and `<uses-feature android:name="android.hardware.camera" android:required="true"/>` in the manifest.
3. Implement runtime permission request in Compose (with a rationale/denied state).
4. Implement `CameraPreview` composable: `PreviewView` via `AndroidView`, bound to the lifecycle with `ProcessCameraProvider`, back camera, `Preview` use case.
5. Verify a live, unfiltered preview on the device.
6. Commit.

### Phase 3: Quick MVP filter
1. Create `vision/AnimalVision.kt`: a data class holding the 3x3 matrix (and later blur radius, brightness) plus a `DOG` profile. Take the matrix from the prototype export, not from memory: `DOG_V2_COLOR_MATRIX_4X5` (default) or `DOG_V0_COLOR_MATRIX_4X5` (fallback) in `src/prototype/export/AnimalVisionConstants.kt.txt`. Keep the matrix as data so the default can change without a code change (`export/matrix.json` holds both, plus the generating commit).
2. Set `PreviewView` to `ImplementationMode.COMPATIBLE` (TextureView) and apply a `ColorMatrixColorFilter` via the view's layer paint (`setLayerType(LAYER_TYPE_HARDWARE, paint)`).
3. Add a toggle: normal vs. dog view.
4. Verify on-device against the Python prototype output (`python -m anivision in.jpg out.png --variant v2`). Color will be slightly off, since `ColorMatrix` runs on gamma-encoded values. This is acceptable for the MVP.
5. Commit.

### Phase 4: Proper GPU pipeline
1. Replace the color-filter hack with a CameraX `SurfaceProcessor` / `CameraEffect` (or a custom `GLSurfaceView` fed by a `SurfaceTexture`).
2. Write the fragment shader using `samplerExternalOES`:
   - sample camera texture
   - linearize (sRGB to linear)
   - multiply by the profile matrix (uniform `mat3`)
   - optional separable blur (acuity) and brightness lift
   - re-encode to sRGB
3. Pass the `AnimalVision` profile as uniforms. Use `export/animal_vision.glsl` (`mat3` arguments are **column-major**; `DOG_V2_COLUMN_MAJOR` in the Kotlin snippet is the same data for `glUniformMatrix3fv(..., false, ...)`). Gamut policy: clamp each channel to [0, 1].
4. Blur (acuity), switch **hidden by default**: compute sigma from the real frame with `acuitySigmaPx(longEdgePx, exaggeration)` (formula and constants in `export/matrix.json` under `acuity`; default exaggeration 1.0, maximum 8.0). Blur in linear light; mirrored edges are preferred, but clamp-to-edge is fine (external camera textures may not support mirroring, and at a few pixels of sigma the difference is invisible). Shadow lift (`shadow_lift` in the same file) is optional and off by default.
5. Verify the colour step against `export/golden_vectors.json` (73 sRGB colours, tolerance of 1 per 8-bit channel for 32-bit float math; this is unverified on a device, so check it on the target GPU). Then verify correct orientation, aspect ratio and no frame drops (target 30 fps).
6. Commit.

### Phase 5: Features
- Side-by-side or draggable split-screen "normal | dog" comparison.
- Sliders for blur (acuity) and low-light boost.
- Photo capture with the effect baked in (`ImageCapture` plus effect), saved via MediaStore.
- Video recording with the effect (`VideoCapture` plus effect).
- Additional profiles (cat, etc.) behind a selector.
- Gallery import: apply the filter to existing photos.

### Phase 6: Polish and release prep
- App icon, name, theme (light/dark).
- About screen with the "approximation" disclaimer and short science notes.
- Release build config (R8/minify, signing config kept out of git), privacy policy (camera use only, no data collected), Play Console listing.

## 6. Python prototype (done, merged in PR #1)

The prototype validated the look on still photos before the shader work. Details are in `Python.md` and `src/prototype/NOTES.md`. What it covered:

1. `src/prototype/anivision/` (`python -m anivision`): load an image, linearize, apply the transform, re-encode, save.
2. Compute the matrix from cone sensitivity curves (peaks 429 / 555 nm) and compare with the stock deuteranopia matrix.
3. Test with a handful of scenes: a red ball on green grass (the classic case), blue and yellow objects, a gray card, skin tones, sky.
4. Export the final matrix as constants for `AnimalVision.kt` and the shader.

## 7. Verification checklist

- [ ] Project builds from a clean clone (`./gradlew assembleDebug`)
- [ ] Camera permission flow works (grant, deny, deny permanently)
- [ ] Live preview runs at about 30 fps with no visible stutter on a mid-range device
- [ ] Red/green scenes collapse to yellow/brown as expected; blues and yellows stay distinct; grays stay neutral
- [ ] Preview matches the Python prototype output within a small tolerance
- [ ] Rotation, front/back camera and app backgrounding/resume don't crash
- [ ] Captured photos/videos match what the preview showed (Phase 5)

## 8. Decisions on the former open questions

1. **applicationId / app name:** `com.anivision.app` stays unless changed before the first release.
2. **Working copy:** the current directory (Windows filesystem, also reachable from WSL).
3. **Test device:** one physical phone on Android 11 (API 30).
4. **Video recording:** out of v1 scope for now; photo capture comes in Phase 5.
5. **Other animals:** dog only for now. Cat stays a later extension.
