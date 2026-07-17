#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./deploy.sh up [--build|-b]
  ./deploy.sh down
  ./deploy.sh --help

Commands:
  up      Start the local layered Compose stack in detached mode.
  down    Stop and remove the local layered Compose stack.

Options:
  -b, --build  Build images before starting the stack.
  -h, --help   Show this help message.
EOF
}

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compose_dir="$script_dir/compose"
env_file="$compose_dir/.env"

command_name=""
build_flag=false

while (($# > 0)); do
  case "$1" in
    up|down)
      if [[ -n "$command_name" ]]; then
        printf 'Error: command already set to %s.\n' "$command_name" >&2
        usage >&2
        exit 1
      fi
      command_name="$1"
      ;;
    -b|--build)
      build_flag=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'Error: unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

if [[ -z "$command_name" ]]; then
  usage >&2
  exit 1
fi

if [[ "$command_name" == "down" && "$build_flag" == true ]]; then
  printf 'Error: --build can only be used with the up command.\n' >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  printf 'Error: docker is not installed or not on PATH.\n' >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  printf 'Error: modern docker compose is required.\n' >&2
  exit 1
fi

compose_args=(
  -f "$compose_dir/docker-compose.base.yaml"
  -f "$compose_dir/docker-compose.local.yaml"
)

if [[ -f "$env_file" ]]; then
  compose_args+=(--env-file "$env_file")
fi

docker_compose_cmd=(docker compose "${compose_args[@]}")

case "$command_name" in
  up)
    up_args=(up -d)
    if [[ "$build_flag" == true ]]; then
      up_args+=(--build)
    fi
    "${docker_compose_cmd[@]}" "${up_args[@]}"
    ;;
  down)
    "${docker_compose_cmd[@]}" down
    ;;
esac
