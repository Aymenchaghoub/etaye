FROM eclipse-temurin:25-jdk@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3 AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw apps/api/pom.xml ./
COPY apps/api/src src
# The package smoke may fall back to this one HTTPS mirror after a confirmed Central transfer failure.
# The wrapper verifies its distribution against the checked-in SHA-256. Maven -C checks repository-supplied
# artifact checksums on both routes; those are integrity checks, not an independent signature.
ARG MAVEN_CENTRAL_SOURCE=central
RUN case "$MAVEN_CENTRAL_SOURCE" in \
      central) mirror= ;; \
      mirror) mirror=https://maven-central.storage-download.googleapis.com/maven2 ;; \
      *) echo "Unsupported Maven Central source" >&2; exit 1 ;; \
    esac \
    && if [ -n "$mirror" ]; then \
      mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>central-mirror</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' "$mirror" > /root/.m2/settings.xml; \
    fi \
    && MVNW_VERBOSE=true MVNW_REPOURL="$mirror" ./mvnw -B -ntp -C -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre@sha256:4ed132f6832e9804b123e89a849a7b4502756ae64a4ee08ebab939d9d5d0a9fa
WORKDIR /app
COPY --from=build /src/target/etaye-api-0.0.1-SNAPSHOT.jar etaye.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/etaye.jar"]
