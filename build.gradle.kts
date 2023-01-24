import java.net.HttpURLConnection
import java.net.URL

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm") version "1.8.0"
}

group = "com.wbrawner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.telegram:telegrambots:6.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

kotlin {
    jvmToolchain(17)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

val main = "com.wbrawner.MainKt"

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