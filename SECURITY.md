# Security and privacy

- The in-app AI Portal starts at `https://www.google.com/ai`, blocks mixed content and local-file access, and keeps Safe Browsing enabled.
- Image decoding, preview, prompt handling, and collage generation happen on-device.
- The app does not inject JavaScript, expose a JavaScript bridge, inspect form contents, intercept credentials, or proxy Google traffic.
- Camera and microphone requests are accepted only from secure `google.com` origins and still require Android's system permission dialog.
- An explicit external-browser action is available whenever Google requires a browser-owned sign-in session.
- Cleartext network traffic and Android backups are disabled.
- Please report security issues privately to the repository owner rather than opening a public issue.
