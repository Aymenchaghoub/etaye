FROM node:26-bookworm-slim AS build
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
COPY infra/demo/README.md /demo-guide.txt
ENV VITE_ETAYE_LOCAL_DEMO=true
RUN corepack pnpm --filter etaye-web build && cp /demo-guide.txt apps/web/dist/demo-guide.txt

FROM nginx:1.28-alpine
COPY infra/demo/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/apps/web/dist /usr/share/nginx/html
