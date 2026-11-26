FROM postgres:18@sha256:c699303802144847d238196173655dbbecf7a96aeca1565f2b02eb671a1f0b6b
# The PostgreSQL base digest predates the Debian fix for zstd (CVE-2026-124851).
RUN apt-get -o Acquire::http::Timeout=25 -o Acquire::Retries=1 -o APT::Update::Error-Mode=any update \
    && apt-get install -y --only-upgrade --no-install-recommends libzstd1 zstd \
    && rm -rf /var/lib/apt/lists/*
COPY --chmod=755 infra/local/init-roles.sh /docker-entrypoint-initdb.d/10-roles.sh
COPY --chmod=755 infra/demo/mark-demo-database.sh /docker-entrypoint-initdb.d/20-demo-only.sh
