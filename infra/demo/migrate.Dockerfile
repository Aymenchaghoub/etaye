FROM flyway/flyway:13.13.0@sha256:53b3979ee3928ffab3019aadac89704dc939d54489e02dc3a600d86a4afb110c
# Keep the Ubuntu base while applying fixes already shipped in its signed repositories.
RUN sed 's/^Components:.*/Components: main/' /etc/apt/sources.list.d/ubuntu.sources > /tmp/main.sources \
    && apt-get -o Dir::Etc::sourcelist=/tmp/main.sources -o Dir::Etc::sourceparts=- \
       -o Acquire::http::Timeout=25 -o Acquire::Retries=1 -o APT::Update::Error-Mode=any update \
    && apt-get install -y --only-upgrade --no-install-recommends apt libapt-pkg6.0t64 libbrotli1 libssl3t64 openssl \
    && rm -rf /var/lib/apt/lists/* /tmp/main.sources
COPY apps/api/src/main/resources/db/migration /flyway/schema
COPY apps/api/src/main/resources/db/demo /flyway/demo
