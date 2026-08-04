# Security and privacy

- The embedded portal starts at `https://www.google.com/ai` and renders it directly with Android System WebView.
- Image decoding, preview, prompt handling, and collage generation happen on-device.
- The app does not inject JavaScript, expose a JavaScript bridge, inspect WebView cookies or page content, imitate Google sign-in, or proxy Google traffic.
- Mixed content and WebView file access are disabled, Safe Browsing is enabled where supported, and non-HTTPS navigation is handed to the operating system rather than loaded in the portal.
- Camera and microphone requests are accepted only from a verified HTTPS Google origin and still require Android runtime permission from the user.
- The current page can be moved to a browser-owned Custom Tab at any time, including when Google authentication rejects an embedded user agent.
- Cleartext network traffic and Android backups are disabled.
- Please report security issues privately to the repository owner rather than opening a public issue.
