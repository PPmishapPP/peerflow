# syntax=docker/dockerfile:1
# Сервер PeerFlow: сборка и запуск Spring Boot в одном образе.
#
# ВАЖНО: gradle.properties в образ НЕ копируется (исключён в .dockerignore).
# В нём локальный путь к JDK Windows — внутри Linux-контейнера он неверен.

# --- Сборка ---
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Сначала только зависимости сборки — слой кэшируется, пока не менялся build-скрипт.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
# Кэш Gradle между сборками: без него каждая пересборка тянет всё заново.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# --- Запуск ---
FROM eclipse-temurin:21-jre

WORKDIR /app

# Непривилегированный пользователь: приложение не должно работать от root.
RUN useradd --system --uid 1001 --home-dir /app peerflow

COPY --from=build /app/build/libs/*.jar app.jar

# Каталог вложений (аватары). На сервере он монтируется томом.
RUN mkdir -p /app/storage && chown -R peerflow:peerflow /app
USER peerflow

EXPOSE 8080

# Ограничение памяти задаётся в compose (JAVA_OPTS/mem_limit): по умолчанию
# JVM забрала бы четверть всей памяти хоста, а хост делят с другим приложением.
ENV JAVA_OPTS="-Xmx256m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
