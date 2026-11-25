FROM flyway/flyway:12.4.0@sha256:5be18367a9b3979a9f37234c371b80da457a448fb9577bd94d4bf24d923fafac
COPY apps/api/src/main/resources/db/migration /flyway/schema
COPY apps/api/src/main/resources/db/demo /flyway/demo
