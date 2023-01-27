import java.net.HttpURLConnection
import java.net.URL

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm") version "1.8.0"
    id("app.cash.sqldelight") version "2.0.0-alpha05"
}

group = "com.wbrawner"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.telegram:telegrambots:6.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("app.cash.sqldelight:jdbc-driver:2.0.0-alpha05")
    implementation("org.postgresql:postgresql:42.5.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("ch.qos.logback:logback-classic:1.3.5")
}

kotlin {
    jvmToolchain(17)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

val main = "com.wbrawner.civicsquizbot.Main"

application {
    mainClass.set(main)
}

tasks.shadowJar {
    manifest {
        attributes("Main-Class" to main)
        archiveBaseName.set("civics-quiz-bot")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}

tasks.register("updateQuestions") {
    val urlConnection = URL("https://www.uscis.gov/sites/default/files/document/questions-and-answers/100q.txt")
        .openConnection() as HttpURLConnection
    urlConnection.connect()
    val text = urlConnection.inputStream.bufferedReader().use {
        it.readText()
    }
    File(projectDir, "src/main/resources/questions.txt").writeText(text)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.wbrawner.civicsquizbot")
            dialect("app.cash.sqldelight:postgresql-dialect:2.0.0-alpha05")
        }
    }
}