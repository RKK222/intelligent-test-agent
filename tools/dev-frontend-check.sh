#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
FRONTEND_DIR="${ROOT_DIR}/frontend"

usage() {
  cat <<'USAGE'
Usage: tools/dev-frontend-check.sh [--skip-install] [--help]

Run the frontend checks expected before local integration.

Commands are executed through Corepack because pnpm might not be on PATH.

Options:
  --skip-install  Do not run corepack pnpm install.
  --help          Show this help.
USAGE
}

skip_install=false
for arg in "$@"; do
  case "${arg}" in
    --skip-install)
      skip_install=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: ${arg}" >&2
      usage >&2
      exit 2
      ;;
  esac
done

cd "${FRONTEND_DIR}"

if [[ "${skip_install}" != "true" ]]; then
  corepack pnpm install
fi

corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
