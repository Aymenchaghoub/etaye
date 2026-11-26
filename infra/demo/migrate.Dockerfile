FROM flyway/flyway:12.11.0@sha256:d24a5083a9d423bbbd5b8faa49819b6211740efef9c9d4c9d5d6307788952936
# Keep the Ubuntu base while applying fixes already shipped in its signed repositories.
RUN sed 's/^Components:.*/Components: main/' /etc/apt/sources.list.d/ubuntu.sources > /tmp/main.sources \
    && apt-get -o Dir::Etc::sourcelist=/tmp/main.sources -o Dir::Etc::sourceparts=- \
       -o Acquire::http::Timeout=25 -o Acquire::Retries=1 -o APT::Update::Error-Mode=any update \
    && apt-get install -y --only-upgrade --no-install-recommends apt libapt-pkg6.0t64 libbrotli1 libssl3t64 openssl \
    && rm -rf /var/lib/apt/lists/* /tmp/main.sources
COPY apps/api/src/main/resources/db/migration /flyway/schema
COPY apps/api/src/main/resources/db/demo /flyway/demo
