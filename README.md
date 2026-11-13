# AniVision

An app that shows the live camera view approximately as a dog's or a cat's colour vision would capture it.

> **This is an approximation.** Dogs and cats are dichromats (dog: S cone about 429 nm, L cone about 555 nm;
> cat: S cone about 450 nm, L cone about 555 nm). The app simulates the information their cones
> capture. It does not show what an animal subjectively experiences, and nobody knows that.

## Features

- Live camera preview as **Human**, **Dog** or **Cat**, or a draggable **split** screen comparing two of them.
- Optional acuity **blur** and **night boost** (a shadow lift), both off by default.
- Photo capture with the effect applied, saved to `Pictures/AniVision`, with captions on split photos.
- Gallery import: apply the same filter to an existing photo, then save or share it.
- An About screen with the "approximation" disclaimer and short science notes.

## Status

| Part | Where | State |
|---|---|---|
| Python prototype (colour matrix, acuity blur, shadow lift, exports) | `src/prototype/` | Done, see `Python.md` |
| Android app (Kotlin, Jetpack Compose, CameraX, OpenGL ES / GLSL) | `src/android/` | Working app: preview, split, cat view, photos, gallery import, About, signed release build. See `Android.md` for the plan and per-phase state |
| Documentation for beginners (Polish) | `docs/` | Done, start at `docs/README.md` |

Not in v1: video recording and the front camera.

## Repository layout

```
Android.md       Android plan and per-phase state
Python.md        Python prototype plan
LowLight.md      Design note for the night boost (shadow lift)
docs/            Step-by-step documentation in Polish (no prior knowledge assumed)
src/prototype/   Python prototype and the exported constants the app consumes (export/)
src/android/     Android Studio project (RELEASING.md explains the release build)
```

## Documentation

`docs/README.md` (in Polish) explains the whole project from scratch: how human, dog and cat vision
differ, the image-processing maths, the Python prototype, Android and Kotlin basics, the app
architecture, the GPU pipeline, tests and releasing. The English files above are the design notes
written while building the project.

## Python prototype

Requires Python 3.10+.

```bash
cd src/prototype
python -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -m pytest -q
PYTHONPATH=. .venv/bin/python -m anivision in.jpg out.png --variant v2
```

More in `src/prototype/README.md`.

## Android app

Stack: Kotlin, Jetpack Compose, CameraX, OpenGL ES 3.0 with a GLSL fragment shader, minSdk 29.
Build with Android Studio (latest stable) by opening `src/android/`, or from a terminal:

```bash
cd src/android
./gradlew assembleDebug
```

The Gradle daemon requests a JDK toolchain (version in `src/android/gradle/gradle-daemon-jvm.properties`)
and downloads it on the first build, so that build needs network access. Release builds are
described in `src/android/RELEASING.md`.

A physical device with USB debugging is recommended,
because the emulator camera is poor for judging colour.

Tests: `./gradlew test` runs the JVM tests; `./gradlew connectedDebugAndroidTest` runs the
instrumented tests (including the GPU golden vectors) on a connected device.

## Branches

Work happens on `feature/*` branches. Pull requests target `develop`. `main` is for releases only.
