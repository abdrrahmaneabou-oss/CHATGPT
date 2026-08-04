# AI Mode Studio 1.1

AI Mode Studio is a polished Android workspace for preparing visual context and prompts before continuing in [Google AI Mode](https://www.google.com/ai).

## Product experience

- Aurora dark visual system with premium glass-like cards and a custom launcher icon.
- **AI Portal:** a real in-app Google AI surface that morphs out of the launch button and remains framed by the Studio interface.
- Minimize-to-pill continuity: return to the workspace and restore the live page without losing navigation state.
- Secure HTTPS-only WebView defaults, Safe Browsing, origin-scoped camera/microphone permission handling, file upload support, and an explicit external-browser escape hatch.
- Up to five local images with preview, persistent access, share-sheet intake, and long-press removal.
- Prompt studio with one-tap clipboard handoff.
- Local collage engine with branded export, smart layouts, rounded tiles, and MediaStore support.
- Production-safe manifest defaults: no backups, no cleartext traffic, and no debug flag.

## Build

Open the project in Android Studio, use JDK 17, sync Gradle, and run the `app` configuration.

The application ID is `app.aimode.studio`. It intentionally differs from the early prototype so development builds can be installed beside it without a signing-certificate conflict.

## Core promise

Images are prepared locally. Google AI Mode opens in the app's isolated WebView surface; the app does not inject scripts, inspect form contents, or intercept Google credentials. The external-browser button remains available when Google requires a browser-owned sign-in session.
