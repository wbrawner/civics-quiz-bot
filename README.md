# Civics Quiz Bot

This is a small project I put together to help my wife study for her
[US Civics Exam]. It's essentially a flashcards app, with the following
features:

- automatic question updates
- spaced repetition
- scheduled reminders

## Usage

### Prerequisites

- [Telegram bot token]
- JDK 17 or higher
- PostgreSQL 14

### Building

#### Gradle

The project uses [gradle]. You can build a JAR file with the following command:

```bash
./gradlew shadowJar
```

You can then run it with the following command:

```bash
java -jar build/libs/civics-quiz-bot.jar
```

#### Docker

There is also a [Dockerfile] included. If you'd like to use that instead, you
can run the following command:

```bash
docker build -t civics-quiz-bot .
```

You can then run it with the following command:

```bash
docker run civics-quiz-bot
```

### Configuration

Regardless of how you build the JAR, there are a few configuration options that
need to be set via environment variables.

| Environment Variable | Default Value | Description                    |
|:--------------------:|:-------------:|:-------------------------------|
|   `TELEGRAM_TOKEN`   |               | [Telegram bot token]           |
|   `CIVICS_DB_HOST`   |  `localhost`  | Hostname for PostgreSQL server |
|   `CIVICS_DB_PORT`   |    `5432`     | Port for PostgreSQL server     |
|   `CIVICS_DB_USER`   |  `postgres`   | Username for PostgreSQL server |
| `CIVICS_DB_PASSWORD` |  `postgres`   | Password for PostgreSQL server |

[US Civics Exam]: https://www.uscis.gov/citizenship/find-study-materials-and-resources/study-for-the-test/100-civics-questions-and-answers-with-mp3-audio-english-version

[Telegram bot token]: https://core.telegram.org/bots/tutorial#obtain-your-bot-token

[Dockerfile]: ./Dockerfile

[gradle]: https://gradle.org/