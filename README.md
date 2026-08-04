# AI Mode — Context OS

AI Mode is no longer a long form followed by a browser button. It is a focused Android context workspace: define the outcome, sequence visual evidence, choose how the problem should be approached, and launch one precise context capsule into [Google AI Mode](https://www.google.com/ai).

## The product idea

Most AI interfaces begin with an empty box. Context OS begins with intent.

- **Thinking lenses** reshape the request for analysis, comparison, extraction, creation, or problem solving.
- **Visual storyline** imports up to five images into private app storage, numbers them, lets the user reorder them, and assigns each one a role.
- **Context pulse** scores prompt readiness locally and points to the single most useful next action.
- **Precision controls** add explicit uncertainty, image citations, and clarify-before-assuming behavior.
- **One-tap launch capsule** compiles and copies the prompt, creates a numbered visual board locally, then opens Google AI Mode in an adjustable Custom Tab.
- **Adaptive workspace** becomes a two-pane command center on tablets and large screens while staying thumb-friendly on phones.

Nothing in the preparation flow is sent to an app server. Google authentication remains inside the user's browser-owned Custom Tab.

## Technology

- Kotlin 2.4.10 with Android Gradle Plugin 9.3.1 built-in Kotlin support
- Jetpack Compose BOM 2026.06.01 and Material 3
- Activity Compose 1.13.0 and Lifecycle 2.11.0
- AndroidX Browser 1.10.0 partial Custom Tabs
- Android 17 / API 37 target with Platform 37.0 and Build Tools 37.0.0, Android 8+ support
- Deterministic local prompt compiler, private file imports, and MediaStore export

The versions are intentionally pinned so CI and local builds are reproducible.

## Build

Use JDK 17 and Android SDK 37:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Every pull request runs the same quality gate and uploads the APK as a workflow artifact.

## Architecture

```text
UI (adaptive Compose workspace)
  ├─ StudioViewModel (state, share intents, launch orchestration)
  ├─ PromptEngine (pure deterministic context compiler + readiness)
  ├─ StudioRepository (private imports + persisted workspace)
  └─ CollageExporter (numbered local context board + MediaStore)
```

The core handoff remains intentionally narrow: the app prepares context; the browser owns Google AI Mode, cookies, and sign-in.
