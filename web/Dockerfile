# Web (Next.js) — used by docker-compose for local full-stack demos.
# For cloud, Vercel builds this app itself; no Dockerfile needed there.
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app ./
EXPOSE 3000
# API_URL points the /api rewrite at the backend container
CMD ["npx", "next", "start", "-p", "3000"]
