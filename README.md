<p align="center">
  <img src="docs/lords-space-logo.jpg" alt="Lords Community" width="220">
</p>

<h1 align="center">Lord's-Space</h1>
<p align="center"><b>Multiple Spaces. One App.</b></p>

Lord's-Space is an Android app that lets you run extra copies of your apps in separate, isolated **spaces**
on one phone, with a built-in **Fight Mode** for a loud, clear microphone.

> Built on top of [WaxMoon/MultiApp](https://github.com/WaxMoon/MultiApp) (see [Credits](#credits-and-license)).

![UI preview](docs/lords-space-ui-preview.png)

*The picture above is the design the app follows (Loader, Home, Add Apps, Fight Mode, Play Store).
The real app can differ slightly in spacing and glow.*

## Features

- **Spaces**: Space 0, Space 1, Space 2 ... Switch with the arrows on Home. Each space keeps its own apps and data.
- **Clone apps**: *+ Add app* shows the apps on your phone, with search. Select one or more and tap **CLONE**.
- **Google Play in a space**: the *Play Store* row opens the real Google Play inside the space, so apps you
  install there stay in that space. Needs Google Play to be installed on the phone itself.
- **Fight Mode**: ON/OFF, live mic level, gain up to 100 dB, volume, voice styles
  (Natural, Clear, Loud & Clear, Megaphone), clarity, punch (compressor), 13 EQ presets and a 6-band equalizer.
  A noise gate and a soft limiter keep the sound from turning into pure noise.
- **App loader**: logo, tagline and a progress bar when the app starts.
- **No ads, no locked options** in this source code. Everything in the app is open.

## How Fight Mode works

```
App inside a space  ->  AudioRecord.read()  ->  Fight Mode DSP (C++)  ->  the app gets the processed sound
```

Lord's-Space hooks `AudioRecord.read()` inside the process of the cloned app (using
[Pine](https://github.com/canyie/pine)) and runs the microphone samples through a native DSP:
high-pass, noise gate, 6-band EQ, clarity, punch compressor, gain, volume, soft limiter.

Good to know:

- It only affects apps that are **opened inside Lord's-Space**. Apps running outside are not touched.
- It only affects apps that record through the Java `AudioRecord` API (Telegram does). Apps that use native
  audio (AAudio / OpenSL ES) are not affected.
- Fight Mode screen shows a status line: green *"Mic hook: active in ..."* means the hook is working,
  red *"FAILED"* means it could not attach on that phone.
- The processing runs before the app's own audio processing (for example WebRTC echo cancel and gain control),
  so the app can still reduce the loudness a little.

## Project status

This is an early build. It has **not been tested on a real phone yet**. What has been checked on a computer:
the DSP (unit tests), the Java code compiles, and the patch applies cleanly. What still needs a phone:
the full Gradle build, cloning and opening apps, and the Fight Mode hook.

Android support: the engine inside (`moon.jar`, closed source) lists support up to **Android 14**. Android 15 and 16
are untested. The app targets API 26 on purpose, because the engine needs it.

## Build

You need Android Studio, **JDK 11**, Android SDK 33, Build-Tools 31.0.0, NDK 21.4.7075529 and CMake 3.10.2.

- Step by step on Windows: [BUILD_STEPS.md](BUILD_STEPS.md)
- Build on GitHub without Android Studio (GitHub Actions): [GITHUB_BUILD.md](GITHUB_BUILD.md)

Quick version:

```
gradlew.bat :app:assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

| Path | What it is |
| --- | --- |
| `app/src/main/java/org/waxmoon/ui` | Loader, Home, Add Apps screens |
| `app/src/main/java/org/waxmoon/fightmode` | Fight Mode screen, settings, mic hook |
| `app/src/main/cpp` | Native DSP (`FightModeDsp.h`) and JNI glue |
| `app/src/main/java/org/waxmoon/AppListActivity.kt` | Classic app list (Home menu > Classic app list) |
| `opensdk` | API wrapper around the closed engine (`moon.jar`) |
| `assist` | 32-bit helper app |
| `docs/README-upstream-MultiApp.md` | Original MultiApp README |

## Credits and license

- Engine and base app: [WaxMoon/MultiApp](https://github.com/WaxMoon/MultiApp). The `moon.jar` and native
  `libl.so` / `libpl.so` files are prebuilt binaries from that project.
  `opensdk/src/main/assets/com.waxmoon.ma.gp.apk` is also a prebuilt upstream APK and contains Google
  ads code. Lord's-Space never uses it.
- Method hooking: [Pine](https://github.com/canyie/pine).
- Headline font: Cinzel (SIL Open Font License, see `app/src/main/assets/fonts/OFL-Cinzel.txt`).
- Icons: Material Design icons (Apache License 2.0).
- Logo: Lords Community.

This project is licensed under the **GNU AGPL v3** (see [LICENSE](LICENSE)), the same as MultiApp.
If you share the app, you must share the source code under the same license. The MultiApp authors also ask
to be contacted before you publish software built on it (see their README).