FROM flyway/flyway:12.4.0
COPY apps/api/src/main/resources/db/migration /flyway/schema
COPY apps/api/src/main/resources/db/demo /flyway/demo
