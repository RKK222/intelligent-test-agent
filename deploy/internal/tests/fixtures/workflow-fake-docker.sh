#!/usr/bin/env bash
set -euo pipefail

case "${1:-} ${2:-}" in
  "buildx build")
    exit 0
    ;;
  "image inspect")
    if [[ "${3:-}" == "--format" ]]; then
      case "${4:-}" in
        *Architecture*) printf 'amd64\n' ;;
        *Config.User*) printf '10001:10003\n' ;;
        *Id*) printf 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n' ;;
        *) printf 'fixture\n' ;;
      esac
    else
      printf '[{"fixture":true}]\n'
    fi
    ;;
  "save -o")
    printf 'fake-image-tar\n' >"${3}"
    ;;
  "run --rm")
    printf '{"fixture":true}\n'
    ;;
  *)
    printf 'Unexpected fake Docker invocation: %s\n' "$*" >&2
    exit 1
    ;;
esac
