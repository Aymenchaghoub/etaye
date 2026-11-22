FROM postgres:18
COPY --chmod=755 infra/local/init-roles.sh /docker-entrypoint-initdb.d/10-roles.sh
COPY --chmod=755 infra/demo/mark-demo-database.sh /docker-entrypoint-initdb.d/20-demo-only.sh
