# Builds the single deployable jar (Spring Boot API + embedded React web client,
# see server/build.gradle.kts) and runs it. One process, one dyno — RoomRegistry
# state lives in that process's memory, so this must never be scaled beyond
# web=1 (see CLAUDE.md "Architecture invariants").

FROM eclipse-temurin:21-jdk-jammy AS build

# Gradle's bootJar task shells out to pnpm to build web/ (server/build.gradle.kts),
# so the build stage needs Node + pnpm alongside the JDK.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && corepack enable \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY server/ server/
COPY web/ web/

WORKDIR /app/server
RUN ./gradlew bootJar --no-daemon \
    && cp $(ls build/libs/*.jar | grep -v plain) /app/app.jar

# ---- Runtime: slim JRE only, no Node/pnpm needed to just run the jar ----
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build /app/app.jar app.jar

# Heroku injects $PORT; application.properties reads server.port=${PORT:8080}.
CMD ["java", "-jar", "app.jar"]
