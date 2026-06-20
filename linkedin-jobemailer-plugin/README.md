# JobEmailer LinkedIn Plugin

This browser extension adds a small JobEmailer chat box to LinkedIn pages. Paste a LinkedIn post URL into the box and it forwards the URL to the local JobEmailer WebSocket at `ws://localhost:8080/chat`.

## Run JobEmailer

Set `jobemailer.input-provider=custom-ui`, then start the app from the project root:

```bash
mvn spring-boot:run
```

The extension expects the app to be available at `http://localhost:8080`.

## Load the Extension

1. Open Chrome or Edge and go to `chrome://extensions`.
2. Enable Developer mode.
3. Choose Load unpacked.
4. Select this folder: `linkedin-jobemailer-plugin`.
5. Open or refresh LinkedIn.

The chat box appears in the bottom-right corner on LinkedIn pages. The extraction and email sending still happen inside the local Spring Boot app, using your existing JobEmailer settings.
