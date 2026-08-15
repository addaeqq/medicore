# Web (Next.js) — docker-compose local demo and Railway.
# Vercel builds this app from source instead and ignores this file.
FROM node:20-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package*.json ./
RUN npm ci
COPY . .
# The /api/* rewrite destination is resolved by `next build` into
# .next/routes-manifest.json, so API_URL has to be present HERE — setting it only
# at run time leaves the proxy pointing at the compiled-in localhost default.
# compose passes http://api:4000; on Railway set it as a service variable
# (Railway forwards variables to the Docker build as build args).
ARG API_URL=http://localhost:4000
ENV API_URL=${API_URL}
# `output: "standalone"` (next.config.mjs) emits .next/standalone with a server.js
# and only the node_modules it actually reaches.
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
# server.js reads both: HOSTNAME must be 0.0.0.0 to be reachable from outside the
# container, and PORT is injected by the platform (Railway) — never hardcode it.
ENV HOSTNAME=0.0.0.0 PORT=3000
# --chown so the non-root user can write .next/cache (App Router fetch cache).
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
# API_URL points the /api rewrite at the backend (compose: http://api:4000).
CMD ["node", "server.js"]
