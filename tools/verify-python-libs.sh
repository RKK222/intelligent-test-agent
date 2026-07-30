#!/usr/bin/env bash
set -euo pipefail

IMAGE="test-agent-opencode-worker:internal"
LIB_ROOT=""
PLATFORM=""

usage() {
  cat <<'USAGE'
Usage: tools/verify-python-libs.sh --root <python-libs-dir> [options]

Verify the independently deployed Python library bundle in an offline worker container.

Options:
  --image <image>       Worker image. Defaults to test-agent-opencode-worker:internal.
  --root <dir>          Extracted python-libs directory containing site-packages.
  --platform <value>    Optional build-host platform, for example linux/amd64.
  -h, --help            Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)
      IMAGE="$2"
      shift 2
      ;;
    --root)
      LIB_ROOT="$2"
      shift 2
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

[[ -n "${LIB_ROOT}" && -d "${LIB_ROOT}/site-packages" ]] || {
  echo "Python library root is invalid: ${LIB_ROOT:-<empty>}" >&2
  exit 1
}
[[ -f "${LIB_ROOT}/FILES.sha256" && -f "${LIB_ROOT}/VERSION" ]] || {
  echo "Python library metadata is incomplete: ${LIB_ROOT}" >&2
  exit 1
}
command -v docker >/dev/null 2>&1 || {
  echo "Required command not found: docker" >&2
  exit 1
}

docker_args=(run --rm --network none)
if [[ -n "${PLATFORM}" ]]; then
  docker_args+=(--platform "${PLATFORM}")
fi

# 校验和、版本与真实读写能力都在断网 worker 中验证，避免宿主机 Python 掩盖 ABI 问题。
docker "${docker_args[@]}" \
  --entrypoint sh \
  --volume "${LIB_ROOT}:/opt/test-agent/python-libs:ro" \
  "${IMAGE}" -lc \
  'set -eu
   cd /opt/test-agent/python-libs
   sha256sum -c FILES.sha256 >/dev/null
   grep -Fx "PYTHON_VERSION=3.13.14" VERSION >/dev/null
   grep -Fx "PLATFORM=linux/amd64" VERSION >/dev/null
   expected="$(sed -n "s/^REQUIREMENTS_SHA256=//p" VERSION)"
   actual="$(sha256sum requirements.lock)"
   actual="${actual%% *}"
   test "${actual}" = "${expected}"'

python_smoke="$(cat <<'PY'
from io import BytesIO
from importlib.metadata import version
import json

import jsonschema
import openpyxl
import orjson
import pandas as pd
import xlsxwriter
from docx import Document

expected = {
    "pandas": "3.0.3",
    "openpyxl": "3.1.5",
    "XlsxWriter": "3.2.9",
    "python-docx": "1.2.0",
    "jsonschema": "4.26.0",
    "orjson": "3.11.9",
}
for package, wanted in expected.items():
    actual = version(package)
    assert actual == wanted, f"{package}: expected {wanted}, got {actual}"

frame = pd.DataFrame({"name": ["alpha", "beta"], "value": [1, 2]})
excel_stream = BytesIO()
frame.to_excel(excel_stream, index=False, engine="openpyxl")
excel_stream.seek(0)
assert pd.read_excel(excel_stream, engine="openpyxl").to_dict("records") == frame.to_dict("records")

xlsx_stream = BytesIO()
workbook = xlsxwriter.Workbook(xlsx_stream, {"in_memory": True})
workbook.add_worksheet().write(0, 0, "ok")
workbook.close()
assert xlsx_stream.getbuffer().nbytes > 0

docx_stream = BytesIO()
document = Document()
document.add_paragraph("智能体脚本环境")
document.save(docx_stream)
assert docx_stream.getbuffer().nbytes > 0

payload = {"ok": True, "items": [1, 2]}
assert json.loads(json.dumps(payload)) == payload
assert orjson.loads(orjson.dumps(payload)) == payload
jsonschema.validate(payload, {"type": "object", "required": ["ok", "items"]})
print("python libraries functional smoke ok")
PY
)"

docker "${docker_args[@]}" \
  --entrypoint python3 \
  --env "PYTHONPATH=/opt/test-agent/python-libs/site-packages" \
  --env "PYTHONNOUSERSITE=1" \
  --volume "${LIB_ROOT}:/opt/test-agent/python-libs:ro" \
  "${IMAGE}" -c "${python_smoke}"

echo "Python library bundle verified: image=${IMAGE} root=${LIB_ROOT}"
