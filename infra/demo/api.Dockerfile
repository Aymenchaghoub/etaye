FROM eclipse-temurin:25-jdk@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3 AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw apps/api/pom.xml ./
COPY apps/api/src src
# Optional base URL for downloading the Maven distribution (empty: Maven Central). The wrapper checks the
# distribution against the SHA-256 pinned in .mvn/wrapper/maven-wrapper.properties, so a mirror needs no trust.
ARG MVNW_REPOURL=
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre@sha256:4ed132f6832e9804b123e89a849a7b4502756ae64a4ee08ebab939d9d5d0a9fa
WORKDIR /app
COPY --from=build /src/target/etaye-api-0.0.1-SNAPSHOT.jar etaye.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/etaye.jar"]
