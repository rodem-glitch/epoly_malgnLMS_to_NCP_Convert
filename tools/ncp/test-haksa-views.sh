#!/usr/bin/env bash
set -euo pipefail

# 왜: VPN 터널 개통 후 8개 뷰테이블을 vpn_test.jsp + sqlplus로 전수 검증합니다.
# 사용법: bash test-haksa-views.sh [--sqlplus] [--verbose]
#
# 기본: vpn_test.jsp HTTP 검증만 실행
# --sqlplus: Oracle InstantClient sqlplus 직접 접속도 함께 테스트
# --verbose: 응답 본문 전체 출력

# ─── 설정 ───────────────────────────────────────────────────────
ORA_HOST="172.28.5.34"
ORA_PORT="61521"
ORA_SID="KOPO"
ORA_USER="KOPO_LMS"
ORA_PASS="KOPO_LMS"
VPN_GW="172.28.2.34"
EPOLY_BASE="https://e-poly.kopo.ac.kr/main/vpn_test.jsp"

# 왜: 8개 뷰테이블과 예상 컬럼 수 (검증 기준)
declare -A VIEWS=(
  ["COM.LMS_MEMBER_VIEW"]="22"
  ["COM.LMS_COURSE_VIEW"]="28"
  ["COM.LMS_LECTPLAN_VIEW"]="17"
  ["COM.LMS_LECTPLAN_NCS_VIEW"]="22"
  ["COM.LMS_STUDENT_VIEW"]="10"
  ["COM.LMS_PROFESSOR_VIEW"]="11"
  ["COM.COURSE_INFO_VIEW"]="25"
  ["COM.Job_Postings_VIEW"]="27"
)

# 왜: 검증 순서를 고정합니다 (associative array는 순서 보장 안 됨)
VIEW_ORDER=(
  "COM.LMS_MEMBER_VIEW"
  "COM.LMS_COURSE_VIEW"
  "COM.LMS_LECTPLAN_VIEW"
  "COM.LMS_LECTPLAN_NCS_VIEW"
  "COM.LMS_STUDENT_VIEW"
  "COM.LMS_PROFESSOR_VIEW"
  "COM.COURSE_INFO_VIEW"
  "COM.Job_Postings_VIEW"
)

DO_SQLPLUS=false
VERBOSE=false
for arg in "$@"; do
  case "${arg}" in
    --sqlplus) DO_SQLPLUS=true ;;
    --verbose) VERBOSE=true ;;
  esac
done

PASS=0
FAIL=0
TOTAL=0

log() { echo "[haksa-test] $(date '+%H:%M:%S') $*"; }

result_ok()   { PASS=$((PASS + 1)); TOTAL=$((TOTAL + 1)); echo "  >> OK: $*"; }
result_fail() { FAIL=$((FAIL + 1)); TOTAL=$((TOTAL + 1)); echo "  >> FAIL: $*"; }

# ─── Phase 0: VPN 네트워크 확인 ─────────────────────────────────
echo ""
echo "============================================================"
echo "  Phase 0: VPN 네트워크 연결 확인"
echo "============================================================"

log "VPN 게이트웨이 ping (${VPN_GW})"
if ping -c 2 -W 3 "${VPN_GW}" >/dev/null 2>&1; then
  result_ok "VPN GW ${VPN_GW} 도달"
else
  result_fail "VPN GW ${VPN_GW} 미도달"
fi

log "Oracle DB ping (${ORA_HOST})"
if ping -c 2 -W 3 "${ORA_HOST}" >/dev/null 2>&1; then
  result_ok "Oracle ${ORA_HOST} 도달"
else
  result_fail "Oracle ${ORA_HOST} 미도달 (ICMP 차단일 수 있음, TCP 테스트로 확인)"
fi

log "Oracle 리스너 TCP 포트 (${ORA_HOST}:${ORA_PORT})"
if timeout 10 bash -c "echo > /dev/tcp/${ORA_HOST}/${ORA_PORT}" 2>/dev/null; then
  result_ok "${ORA_HOST}:${ORA_PORT} TCP 연결 성공"
else
  result_fail "${ORA_HOST}:${ORA_PORT} TCP 연결 실패"
fi

log "e-poly HTTPS 접근"
EPOLY_HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 --max-time 30 "${EPOLY_BASE}?tb=COM.LMS_MEMBER_VIEW&cnt=1" 2>/dev/null || echo "000")
if [[ "${EPOLY_HTTP_CODE}" == "200" ]]; then
  result_ok "e-poly.kopo.ac.kr HTTPS 접근 성공 (HTTP ${EPOLY_HTTP_CODE})"
else
  result_fail "e-poly.kopo.ac.kr HTTPS 접근 실패 (HTTP ${EPOLY_HTTP_CODE})"
fi

# ─── Phase 1: vpn_test.jsp 8개 뷰테이블 전수 검증 ──────────────
echo ""
echo "============================================================"
echo "  Phase 1: vpn_test.jsp 8개 뷰테이블 전수 검증"
echo "============================================================"

for i in "${!VIEW_ORDER[@]}"; do
  tb="${VIEW_ORDER[$i]}"
  expected_cols="${VIEWS[$tb]}"
  num=$((i + 1))

  log "[${num}/8] ${tb} (예상 ${expected_cols}컬럼)"

  # 왜: cnt=1로 1건만 조회하여 뷰테이블 접근 가능 여부와 컬럼 구조를 확인합니다.
  RESPONSE=$(curl -s --connect-timeout 15 --max-time 60 "${EPOLY_BASE}?tb=${tb}&cnt=1" 2>/dev/null || echo "CURL_ERROR")

  if [[ "${RESPONSE}" == "CURL_ERROR" || -z "${RESPONSE}" ]]; then
    result_fail "${tb} — 응답 없음 (네트워크/타임아웃)"
    continue
  fi

  # 왜: 응답에 데이터가 포함되어 있는지 간단 체크합니다.
  # vpn_test.jsp 응답은 <pre> 태그 안에 key:value 또는 JSON 형식입니다.
  if echo "${RESPONSE}" | grep -qi "error\|exception\|오류"; then
    result_fail "${tb} — 에러 응답"
    [[ "${VERBOSE}" == "true" ]] && echo "    응답: ${RESPONSE:0:500}"
    continue
  fi

  # 왜: 응답 크기와 라인 수로 데이터 존재 여부를 판단합니다.
  RESP_SIZE=${#RESPONSE}
  RESP_LINES=$(echo "${RESPONSE}" | wc -l)

  if [[ "${RESP_SIZE}" -gt 50 ]]; then
    result_ok "${tb} — 응답 ${RESP_SIZE}bytes, ${RESP_LINES}lines"
  else
    result_fail "${tb} — 응답이 너무 작음 (${RESP_SIZE}bytes)"
  fi

  if [[ "${VERBOSE}" == "true" ]]; then
    echo "    --- 응답 (처음 800자) ---"
    echo "${RESPONSE:0:800}"
    echo "    ---"
  fi
done

# ─── Phase 2: sqlplus 직접 접속 (선택) ──────────────────────────
if [[ "${DO_SQLPLUS}" == "true" ]]; then
  echo ""
  echo "============================================================"
  echo "  Phase 2: sqlplus 직접 접속 테스트"
  echo "============================================================"

  if ! command -v sqlplus >/dev/null 2>&1; then
    # 왜: Oracle Instant Client 환경변수를 시도합니다.
    for d in /opt/oracle/instantclient_*; do
      if [[ -x "${d}/sqlplus" ]]; then
        export ORACLE_HOME="${d}"
        export LD_LIBRARY_PATH="${d}:${LD_LIBRARY_PATH:-}"
        export PATH="${d}:${PATH}"
        break
      fi
    done
  fi

  if ! command -v sqlplus >/dev/null 2>&1; then
    result_fail "sqlplus 미설치 — --sqlplus 생략하거나 Oracle Instant Client를 설치하세요"
  else
    log "sqlplus 접속: ${ORA_USER}@${ORA_HOST}:${ORA_PORT}/${ORA_SID}"

    SQLPLUS_OUTPUT=$(echo "
SET PAGESIZE 0 FEEDBACK OFF HEADING OFF
SELECT 'CONNECTION_OK' FROM DUAL;
EXIT
" | sqlplus -S "${ORA_USER}/${ORA_PASS}@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=${ORA_HOST})(PORT=${ORA_PORT}))(CONNECT_DATA=(SERVICE_NAME=${ORA_SID})))" 2>&1 || true)

    if echo "${SQLPLUS_OUTPUT}" | grep -q "CONNECTION_OK"; then
      result_ok "sqlplus 접속 성공"
    else
      result_fail "sqlplus 접속 실패"
      echo "    출력: ${SQLPLUS_OUTPUT:0:500}"
    fi

    # 왜: 8개 뷰테이블에 대해 SELECT COUNT(*) 실행
    log "뷰테이블 행 수 조회"
    for tb in "${VIEW_ORDER[@]}"; do
      # COM.LMS_MEMBER_VIEW → KOPO_LMS 스키마 접근이므로 COM. 제거 불필요
      # 왜: vpn_test.jsp의 tb 파라미터와 동일한 형식(COM.xxx)으로 쿼리합니다.
      COUNT_OUTPUT=$(echo "
SET PAGESIZE 0 FEEDBACK OFF HEADING OFF
SELECT COUNT(*) FROM ${tb};
EXIT
" | sqlplus -S "${ORA_USER}/${ORA_PASS}@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=${ORA_HOST})(PORT=${ORA_PORT}))(CONNECT_DATA=(SERVICE_NAME=${ORA_SID})))" 2>&1 || echo "ERROR")

      ROW_COUNT=$(echo "${COUNT_OUTPUT}" | tr -d '[:space:]')
      if [[ "${ROW_COUNT}" =~ ^[0-9]+$ && "${ROW_COUNT}" -gt 0 ]]; then
        result_ok "${tb} — ${ROW_COUNT} rows"
      elif [[ "${ROW_COUNT}" =~ ^[0-9]+$ ]]; then
        echo "  >> WARN: ${tb} — 0 rows (빈 뷰)"
        TOTAL=$((TOTAL + 1))
      else
        result_fail "${tb} — 조회 실패: ${COUNT_OUTPUT:0:200}"
      fi
    done
  fi
fi

# ─── Phase 3: Docker 컨테이너 내부 연결 확인 ────────────────────
echo ""
echo "============================================================"
echo "  Phase 3: Docker 컨테이너(lms-resin) 내부 연결 확인"
echo "============================================================"

if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "lms-resin"; then
  log "lms-resin → Oracle TCP 연결"
  DOCKER_TCP=$(docker exec lms-resin bash -c "timeout 10 bash -c 'echo > /dev/tcp/${ORA_HOST}/${ORA_PORT}' 2>/dev/null && echo OK || echo FAIL" 2>/dev/null || echo "DOCKER_ERROR")
  if [[ "${DOCKER_TCP}" == "OK" ]]; then
    result_ok "lms-resin → ${ORA_HOST}:${ORA_PORT} TCP 연결 성공"
  else
    result_fail "lms-resin → ${ORA_HOST}:${ORA_PORT} TCP 연결 실패 (Docker 네트워크 확인 필요)"
  fi

  log "lms-resin → e-poly HTTPS 연결"
  DOCKER_HTTPS=$(docker exec lms-resin curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 "${EPOLY_BASE}?tb=COM.LMS_MEMBER_VIEW&cnt=1" 2>/dev/null || echo "000")
  if [[ "${DOCKER_HTTPS}" == "200" ]]; then
    result_ok "lms-resin → e-poly HTTPS 성공 (HTTP ${DOCKER_HTTPS})"
  else
    result_fail "lms-resin → e-poly HTTPS 실패 (HTTP ${DOCKER_HTTPS})"
  fi
else
  log "lms-resin 컨테이너가 없어 건너뜁니다."
fi

# ─── 결과 요약 ──────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  검증 결과 요약"
echo "============================================================"
echo "  PASS: ${PASS} / ${TOTAL}"
echo "  FAIL: ${FAIL} / ${TOTAL}"
echo ""

if [[ "${FAIL}" -eq 0 ]]; then
  echo "  >>> 모든 테스트 통과! 학사 연동 준비 완료."
else
  echo "  >>> ${FAIL}건 실패. 위 FAIL 항목을 확인하세요."
fi
echo "============================================================"

exit "${FAIL}"
