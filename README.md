# JobEmailer

Spring Boot version of the Telegram -> LinkedIn -> Gemini -> Email workflow.

## Features
- Polls Telegram for LinkedIn post URLs
- Extracts post content from public LinkedIn HTML
- Extracts recruiter email from post content only
- Generates email drafts with Gemini, with model rotation and API-key failover
- Supports test mode vs actual sending
- Tracks sent emails and skips re-sending within 7 days

## Configure

Non-secret defaults live in `src/main/resources/application.properties`.

**Secrets** (Telegram token, Gemini keys, SMTP password) belong in a `.env` file at the project root:

```bash
cp .env.example .env
# edit .env with your values
```

Spring Boot loads `.env` automatically (`spring.config.import`). `.env` is gitignored.

The default resume is bundled with the app at `src/main/resources/resume/Ramij-Amed-Sardar.pdf` and referenced via `jobemailer.resume-path=classpath:resume/Ramij-Amed-Sardar.pdf`.

You can also override any property with environment variables (e.g. `JOBEEMAILER_SMTP_PASSWORD`) or CLI args (`--jobemailer.smtp-password=...`).

Important settings:
- `jobemailer.auto-send-email=true`
- `jobemailer.test-mode=true` for safe testing
- `jobemailer.test-mode=false` for actual sending
- `jobemailer.run-once-url=` to process one LinkedIn post and exit

## Run Once
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--jobemailer.run-once-url=https://www.linkedin.com/posts/..."
```

## Run Polling
```bash
mvn spring-boot:run
```

## Tracking Files
- `telegram_state.json`
- `bot_history.jsonl`
- `sent_email_history.json`
