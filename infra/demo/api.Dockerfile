FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw apps/api/pom.xml ./
COPY apps/api/src src
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/target/etaye-api-0.0.1-SNAPSHOT.jar etaye.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/etaye.jar"]
