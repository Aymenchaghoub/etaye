FROM postgres:18@sha256:c699303802144847d238196173655dbbecf7a96aeca1565f2b02eb671a1f0b6b
COPY --chmod=755 infra/local/init-roles.sh /docker-entrypoint-initdb.d/10-roles.sh
COPY --chmod=755 infra/demo/mark-demo-database.sh /docker-entrypoint-initdb.d/20-demo-only.sh
