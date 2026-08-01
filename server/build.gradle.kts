plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.declaration"
version = "0.0.1-SNAPSHOT"

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
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// --- Embed the built web client as Spring static resources -----------------
// Only wired into the packaging tasks (bootJar/build), never into `classes`/
// `test`, so the routine `./gradlew test` dev loop doesn't pay for a pnpm
// install+build on every run. Local dev still uses `pnpm dev` (Vite, with its
// proxy to the backend) per the two-terminal workflow in CLAUDE.md — this is
// purely for producing a single deployable jar.
val webDir = layout.projectDirectory.dir("../web")
val webDist = webDir.dir("dist")
val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

fun pnpm(vararg args: String): List<String> =
    if (isWindows) listOf("cmd", "/c", "pnpm", *args) else listOf("pnpm", *args)

val pnpmInstall = tasks.register<Exec>("pnpmInstall") {
    description = "Installs web client dependencies."
    workingDir = webDir.asFile
    commandLine(pnpm("install"))
    inputs.file(webDir.file("package.json"))
    inputs.file(webDir.file("pnpm-lock.yaml"))
    outputs.dir(webDir.dir("node_modules"))
}

val buildWeb = tasks.register<Exec>("buildWeb") {
    description = "Builds the React web client for embedding as Spring static resources."
    dependsOn(pnpmInstall)
    workingDir = webDir.asFile
    commandLine(pnpm("build"))
    inputs.dir(webDir.dir("src"))
    inputs.file(webDir.file("index.html"))
    inputs.file(webDir.file("vite.config.ts"))
    outputs.dir(webDist)
}

val copyWebBuild = tasks.register<Copy>("copyWebBuild") {
    description = "Stages the built web client for bootJar to embed as classpath static resources."
    dependsOn(buildWeb)
    from(webDist)
    into(layout.buildDirectory.dir("web-static"))
}

// Fed straight into the jar's own copy spec (BOOT-INF/classes/static, where
// Spring's default static-resource handler looks) rather than through
// processResources/build/resources/main — that directory is shared with
// `test`/`classes`, and Gradle's task-output validation correctly flags
// writing into it from an undeclared task as an implicit-dependency hazard.
// This way the web build stays fully isolated from the resource/test graph.
tasks.named<Jar>("bootJar") {
    dependsOn(copyWebBuild)
    from(layout.buildDirectory.dir("web-static")) {
        into("BOOT-INF/classes/static")
    }
}
