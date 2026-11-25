FROM eclipse-temurin:25-jdk@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3 AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw apps/api/pom.xml ./
COPY apps/api/src src
# Optional mirror of Maven Central for the wrapper download and for dependency resolution (empty: Maven Central). It
# exists for the CI smoke build, where shared runner addresses are rate-limited (HTTP 429). The wrapper checks the
# distribution against the SHA-256 pinned in .mvn/wrapper/maven-wrapper.properties; the dependencies are checked by Maven
# against checksums that the same mirror serves, so a release build must leave this empty.
ARG MAVEN_CENTRAL_MIRROR=
RUN if [ -n "$MAVEN_CENTRAL_MIRROR" ]; then \
      mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>central-mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' "$MAVEN_CENTRAL_MIRROR" > /root/.m2/settings.xml; \
    fi
RUN MVNW_REPOURL="$MAVEN_CENTRAL_MIRROR" ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre@sha256:4ed132f6832e9804b123e89a849a7b4502756ae64a4ee08ebab939d9d5d0a9fa
WORKDIR /app
COPY --from=build /src/target/etaye-api-0.0.1-SNAPSHOT.jar etaye.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/etaye.jar"]
