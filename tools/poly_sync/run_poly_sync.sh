#!/usr/bin/env bash
set -euo pipefail

# 왜 필요한가:
# - 서버에서는 이 쉘만 호출하면 동일한 Python 배치를 실행하도록 고정하기 위함입니다.
# - URL/로그 경로를 환경변수로 바꿀 수 있게 해 운영 크론 등록을 단순화합니다.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${POLY_SYNC_PYTHON:-python3}"
BASE_URL="${POLY_SYNC_BASE_URL:-http://127.0.0.1}"
ENDPOINT="${POLY_SYNC_ENDPOINT:-/main/poly_sync.jsp}"

ARGS=(
  --base-url "$BASE_URL"
  --endpoint "$ENDPOINT"
)

if [[ -n "${POLY_SYNC_LOG_FILE:-}" ]]; then
  ARGS+=(--log-file "$POLY_SYNC_LOG_FILE")
fi

if [[ -n "${POLY_SYNC_RESPONSE_JSON:-}" ]]; then
  ARGS+=(--save-response-json "$POLY_SYNC_RESPONSE_JSON")
fi

exec "$PYTHON_BIN" "$SCRIPT_DIR/run_poly_sync.py" "${ARGS[@]}" "$@"
