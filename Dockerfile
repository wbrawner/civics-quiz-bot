FROM openjdk:17-jdk as builder
MAINTAINER William Brawner <me@wbrawner.com>

RUN microdnf install findutils

RUN groupadd --system --gid 1000 gradle \
    && useradd --system --gid gradle --uid 1000 --shell /bin/bash --create-home gradle

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN /home/gradle/src/gradlew --console=plain --no-daemon shadowJar

FROM openjdk:17-slim
EXPOSE 8080
RUN groupadd --system --gid 1000 civicsbot \
    && useradd --system --gid civicsbot --uid 1000 --create-home civicsbot
COPY --from=builder --chown=civicsbot:civicsbot /home/gradle/src/build/libs/civics-quiz-bot.jar civics-quiz-bot.jar
USER civicsbot
CMD /usr/local/openjdk-17/bin/java $JVM_ARGS -jar /civics-quiz-bot.jar
