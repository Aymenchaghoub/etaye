FROM node:26-bookworm-slim@sha256:0042e17d232a87a2cd485be1da7900fca4d10a4ad11b29e9285da1d2e54798c9 AS dependencies
WORKDIR /src
COPY tools/install_corepack.sh /usr/local/bin/install-corepack
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/* \
    && install-corepack
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY apps/web/package.json apps/web/package.json
RUN corepack pnpm install --frozen-lockfile
COPY apps/web apps/web
COPY design design
COPY docs/contracts docs/contracts
COPY docs/design docs/design
COPY docs/decisions docs/decisions
COPY docs/references/hey-api-license.md docs/references/hey-api-license.md
COPY tools tools
FROM dependencies AS build-local
COPY infra/demo/walkthrough.fr.txt /demo-guide.txt
ENV VITE_ETAYE_LOCAL_DEMO=true
RUN corepack pnpm --filter etaye-web build && cp /demo-guide.txt apps/web/dist/demo-guide.txt

FROM dependencies AS build-cloud
RUN corepack pnpm --filter etaye-web build

FROM nginx:1.30.8-alpine-slim@sha256:e9eaa7298465eab06d618c51556fdcf3fb5061f9ff40bd28ee898fbd7ee7f900 AS proxy
COPY infra/demo/security-headers.conf /etc/nginx/etaye/security-headers.conf

FROM proxy AS cloud-proxy
RUN mkdir -p /etc/nginx/cloud-conf && chown nginx:nginx /etc/nginx/cloud-conf
COPY infra/azure/nginx.conf /etc/nginx/nginx.conf
COPY infra/azure/cloud.conf /etc/nginx/cloud-conf/cloud.conf
COPY infra/azure/cloud-api.conf /etc/nginx/etaye/cloud-api.conf
COPY --chmod=755 tools/configure_cloud_edge.sh /docker-entrypoint.d/15-etaye-cloud-edge.sh

FROM cloud-proxy AS cloud
COPY --from=build-cloud /src/apps/web/dist /usr/share/nginx/html
COPY infra/azure/notice.html /usr/share/nginx/html/etaye-demo-notice.html
COPY infra/azure/notice.css /usr/share/nginx/html/etaye-demo-notice.css
USER nginx

FROM proxy AS local
COPY infra/demo/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build-local /src/apps/web/dist /usr/share/nginx/html
