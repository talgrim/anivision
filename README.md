# Anivision

An app that shows the live camera view approximately as a dog's colour vision would capture it.

> **This is an approximation.** Dogs are dichromats (S cone about 429 nm, L cone about 555 nm).
> The app simulates the information their cones capture. It does not show what a dog
> subjectively experiences, and nobody knows that.

## Status

| Part | Where | State |
|---|---|---|
| Python prototype (colour matrix, acuity blur, exports) | `src/prototype/` | Done, see `Python.md` |
| Android app (Kotlin, Jetpack Compose, CameraX, GLSL) | `android/` | Not started, see `Android.md` |

## Repository layout

```
Android.md       Android scaffolding plan
Python.md        Python prototype plan
src/prototype/   Python prototype and the exported constants the app consumes (export/)
android/         Android Studio project (planned)
```

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

Planned stack: Kotlin, Jetpack Compose, CameraX, a GLSL fragment shader, minSdk 26.
Build with Android Studio (latest stable, JDK 17 bundled) by opening `android/`, or from a
terminal:

```bash
cd android
./gradlew assembleDebug
```

The `android/` project does not exist yet. A physical device with USB debugging is recommended,
because the emulator camera is poor for judging colour.

## Branches

Work happens on `feature/*` branches. Pull requests target `develop`. `main` is for releases only.
