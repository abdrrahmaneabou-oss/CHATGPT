# AI Mode 2.1.1 — Visual OS

AI Mode is an Android image studio wrapped around a living in-app Google AI Portal. The two paths are deliberately independent: the portal opens immediately, while the optional image studio collects and merges up to five images into a high-quality adaptive mosaic.

There is no message composer, prompt compiler, readiness gate, answer-shape selector, automatic clipboard write, or generated text handoff.

## The experience

- **Direct AI Portal** opens from an empty workspace with one tap. Nothing is generated or copied first.
- **Living portal session** remains mounted while minimized, so Google AI and the image studio can coexist without a reload.
- **Visual studio** imports up to five images into private app storage, reorders them, and adds an optional label that belongs only to its image.
- **Shared smart layout** drives both the live Compose preview and the exported bitmap, so the result is visible before export.
- **Adaptive mosaic planner** compares image aspect ratios against multiple portrait, square, and landscape layouts, then chooses the layout with the lowest crop loss.
- **Space-filling output** uses the entire frame for one to five images, including side-by-side portraits, stacked landscapes, hero grids, 2×2 grids, and balanced 2+3 arrangements.
- **High-quality renderer** exports a 3200 px long edge at JPEG quality 98, decodes each source for its actual cell size, applies EXIF rotation, and renders images sequentially to control memory use.
- **Purpose-built browser chrome** provides close, minimize, secure-origin status, back, forward, reload, loading progress, file upload, camera/microphone mediation, and an external-browser escape hatch.

## Privacy

Imported image copies, ordering, labels, previews, and mosaic rendering stay on-device. Export is an explicit action and opening the AI Portal never exports an image.

Google content is rendered by Android System WebView directly from its HTTPS origin. The app does not inject JavaScript, expose a JavaScript bridge, inspect cookies or page content, imitate Google sign-in, or proxy traffic.

## Technology

- Kotlin 2.4.10 with Android Gradle Plugin 9.3.1 built-in Kotlin support
- Jetpack Compose BOM 2026.06.01 and Material 3
- Activity Compose 1.13.0 and Lifecycle 2.11.0
- Android System WebView for the embedded portal and AndroidX Browser 1.10.0 for the external fallback
- Android 17 / API 37 target with Android 8+ support
- Pure Kotlin mosaic planner with deterministic layout tests
- MediaStore HD export and private local image imports

## Build

Use JDK 17 and Android SDK 37:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Every pull request runs the same quality gate and uploads the APK plus lint reports as a workflow artifact.

## Architecture

```text
UI (adaptive Compose Visual OS)
  ├─ AI Portal (morphing WebView surface + browser controls)
  ├─ StudioViewModel (image state, imports, ordering, export)
  ├─ MosaicPlanner (shared aspect-aware layout selection)
  ├─ StudioRepository (private image copies + metadata)
  └─ CollageExporter (3200 px sequential HD renderer + MediaStore)
```
