# JobEmailer

Spring Boot version of the Telegram -> LinkedIn -> Gemini -> Email workflow.

## Features
- Polls Telegram for LinkedIn post URLs
- Extracts post content from public LinkedIn HTML
- Extracts recruiter email from post content only
- Generates email drafts with Gemini, with model rotation and API-key failover
- Supports test mode vs actual sending
- Tracks sent emails and skips re-sending within 7 days
- Can use either Telegram polling or a local browser chat UI over WebSocket

## Configure

Non-secret defaults live in `src/main/resources/application.properties`.

**Secrets** (Telegram token, Gemini keys, SMTP password) belong in a `.env` file at the project root:

```bash
cp .env.example .env
# edit .env with your values
```

Spring Boot loads `.env` automatically (`spring.config.import`). `.env` is gitignored.

You can also override any property with environment variables (e.g. `JOBEEMAILER_SMTP_PASSWORD`) or CLI args (`--jobemailer.smtp-password=...`).

Important settings:
- `jobemailer.auto-send-email=true`
- `jobemailer.test-mode=true` for safe testing
- `jobemailer.test-mode=false` for actual sending
- `jobemailer.input-provider=custom-ui` for the local chat UI
- `jobemailer.input-provider=telegram` for Telegram polling
- `jobemailer.run-once-url=` to process one LinkedIn post and exit

## Run Once
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--jobemailer.run-once-url=https://www.linkedin.com/posts/..."
```

## Run Polling
```bash
mvn spring-boot:run
```

## Run Custom Chat UI
Set `jobemailer.input-provider=custom-ui`, then run:

```bash
mvn spring-boot:run
```

Open `http://localhost:8080/` and paste a LinkedIn post URL. The page sends the URL to the app through a WebSocket connection at `/chat`.

## LinkedIn Browser Plugin
The `linkedin-jobemailer-plugin/` folder contains a Chrome/Edge extension that injects the same chat workflow directly into LinkedIn pages.

Start JobEmailer in custom UI mode, then load `linkedin-jobemailer-plugin/` as an unpacked extension from `chrome://extensions`. The chat box connects to `ws://localhost:8080/chat`.

## Tracking Files
- `telegram_state.json`
- `bot_history.jsonl`
- `sent_email_history.json`
