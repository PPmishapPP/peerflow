plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.peerflow"
version = "0.0.1-SNAPSHOT"
description = "PeerFlow backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JWT (Bearer): выпуск токена при входе и его проверка на каждом запросе.
    // Стартер даёт JwtEncoder/JwtDecoder поверх Nimbus — свою криптографию не пишем.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Тесты идут на настоящем PostgreSQL в контейнере: H2 не повторяет
    // поведение PostgreSQL (типы, ограничения, расширения, Flyway).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
    // Версию образа PostgreSQL для тестов можно переопределить из командной строки,
    // например чтобы прогнать их на той же мажорной версии, что и локальный сервер:
    //   .\gradlew.bat test -PpeerflowPostgresImage=postgres:13-alpine
    providers.gradleProperty("peerflowPostgresImage").orNull?.let { image ->
        systemProperty("peerflow.testcontainers.postgres.image", image)
    }
}
