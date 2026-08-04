# Security policy

## Supported version

AI Mode 2.x is the actively maintained code line.

## Reporting a vulnerability

Please report security issues privately to the repository owner. Do not open a public issue containing private images, documents, device paths, signing material, or reproduction data that exposes user content.

## Security model

- Images are read through Android content URIs and combined locally on the device.
- The application does not contain provider credentials, API keys, analytics SDKs, ads, or a custom backend.
- Network access is used only when the user opens Google's HTTPS AI Mode page.
- Cleartext traffic and Android backups are disabled.
- Persisted URI access is requested only for images explicitly selected through Android's document picker.
- Boards are written through MediaStore to the public `Pictures/AI Mode` album.

## Maintainer guidance

- Never commit signing keystores, `local.properties`, device exports, or user images.
- Keep the Android Gradle Plugin and AndroidX dependencies patched.
- Review any future networking, analytics, or cloud-storage dependency as a security and privacy boundary change.
