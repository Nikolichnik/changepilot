# syntax=docker/dockerfile:1.7

FROM node:20-alpine AS deps
WORKDIR /workspace/changepilot-frontend

COPY changepilot-frontend/package.json changepilot-frontend/package-lock.json ./
RUN npm ci

FROM deps AS build
COPY changepilot-frontend/ ./
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM deps AS development
WORKDIR /workspace/changepilot-frontend
COPY changepilot-frontend/ ./
EXPOSE 8080
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "8080"]

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY changepilot-deployment/docker/image/changepilot-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/changepilot-frontend/dist /usr/share/nginx/html
EXPOSE 8080
