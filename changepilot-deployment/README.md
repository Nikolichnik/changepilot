# ChangePilot deployment

This directory contains Docker images, Compose definitions, and convenience scripts for local development and production-like runtime builds.

## Layout

- `docker/compose/docker-compose.base.yaml` - runtime-oriented Compose file.
- `docker/compose/docker-compose.local.yaml` - local development overlay.
- `docker/image/changepilot-api/Dockerfile` - multi-stage backend image.
- `docker/image/changepilot-frontend/Dockerfile` - multi-stage frontend image.
- `docker/image/changepilot-frontend/nginx.conf` - SPA-friendly nginx runtime config.
- `docker/deploy.sh` and `docker/deploy.ps1` - local layered Compose helpers.

## Direct Compose commands

Runtime-only stack:

```bash
docker compose -f changepilot-deployment/docker/compose/docker-compose.base.yaml up --build
docker compose -f changepilot-deployment/docker/compose/docker-compose.base.yaml down
```

Local development stack:

```bash
docker compose \
  -f changepilot-deployment/docker/compose/docker-compose.base.yaml \
  -f changepilot-deployment/docker/compose/docker-compose.local.yaml \
  up -d --build

docker compose \
  -f changepilot-deployment/docker/compose/docker-compose.base.yaml \
  -f changepilot-deployment/docker/compose/docker-compose.local.yaml \
  down
```

Optional environment file:

```bash
cp changepilot-deployment/docker/compose/.env.example changepilot-deployment/docker/compose/.env
```

If `compose/.env` exists, the deploy scripts pass it with `--env-file`. Defaults work without it.

## Base vs local behavior

### Base Compose

- Builds the API `runtime` target and serves the packaged Spring Boot jar.
- Builds the frontend `runtime` target and serves the built SPA with nginx.
- No source mounts.
- Useful for clean, shared, production-like local runs.

### Local overlay

- Switches the API image to the `development` target and mounts `changepilot-api/`.
- Runs `./mvnw -B -ntp spring-boot:run` inside the container.
- Switches the frontend image to the `development` target and mounts `changepilot-frontend/src/`.
- Runs the Vite dev server on `0.0.0.0:8080` with polling enabled for Docker file watching while preserving the host URL `http://localhost:5173`.
- Keeps container dependencies from the image, so host `node_modules` is never mounted.

## Rebuild boundaries

- Frontend source changes in the local overlay should reload automatically.
- Backend source changes in the local overlay require restarting the API container, for example:

  ```bash
  docker compose \
    -f changepilot-deployment/docker/compose/docker-compose.base.yaml \
    -f changepilot-deployment/docker/compose/docker-compose.local.yaml \
    restart changepilot-api
  ```

- Use `up --build` when Dockerfile, package metadata, frontend configuration, or other image-baked files change.
- Base runtime image rebuilds are required for production-like image content changes because the frontend bundle and backend jar are built into the images.

## Environment variables

- `CHANGEPILOT_API_PORT` default: `8080`
- `CHANGEPILOT_FRONTEND_PORT` default: `5173`
- `VITE_API_BASE_URL` default: `http://localhost:8080`
- `CHANGEPILOT_ALLOWED_ORIGIN` default: `http://localhost:5173`

If you change host ports, update the URL/origin variables to the matching host URLs. The Compose files intentionally do not try to infer those values from each other.

## Scripts

### Bash

```bash
./changepilot-deployment/docker/deploy.sh up
./changepilot-deployment/docker/deploy.sh up --build
./changepilot-deployment/docker/deploy.sh down
```

### PowerShell

```powershell
./changepilot-deployment/docker/deploy.ps1 up
./changepilot-deployment/docker/deploy.ps1 up -Build
./changepilot-deployment/docker/deploy.ps1 down
```

## Troubleshooting

- If the frontend cannot reach the API, verify `VITE_API_BASE_URL` points to a host URL such as `http://localhost:8080`, not `http://changepilot-api:8080`.
- If browser requests fail CORS checks after changing ports, also update `CHANGEPILOT_ALLOWED_ORIGIN`.
- If backend changes are not visible in the local overlay, restart `changepilot-api`.
- If the frontend dependency tree changes, rerun `up --build`; dependencies are image-owned and are refreshed by `npm ci` during the build.
- If Compose configuration changes are not taking effect, run `docker compose ... down` first and then bring the stack back up with `--build`.
