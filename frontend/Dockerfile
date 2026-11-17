# Build stage: build the React app.
FROM node:22 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Serve stage: Caddy serves the app, forwards /api to the backend and gets the HTTPS certificate by itself.
FROM caddy:2
COPY --from=build /app/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
