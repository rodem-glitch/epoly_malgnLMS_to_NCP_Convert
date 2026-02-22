#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP Cloud DB for MySQL 초기 마이그레이션 스크립트입니다.
# GCP Docker MySQL에서 NCP Cloud DB(mysql 8.0.42, Private)로 데이터를 이관합니다.
# WAS 서버(newkl-was01)에서 실행하여 private 네트워크를 통해 Cloud DB에 접근합니다.
#
# 사용법:
#   ./migrate-db.sh <CLOUD_DB_HOST> <DB_NAME> <DB_USER> <DB_PASSWORD> <DUMP_FILE>
#
# 예시:
#   ./migrate-db.sh db-xxxx.vpc-cdb.ntruss.com lms lms 'MyPassword' ./source.sql

CLOUD_DB_HOST="${1:?Cloud DB 호스트를 지정해 주세요}"
DB_NAME="${2:?DB 이름을 지정해 주세요}"
DB_USER="${3:?DB 사용자명을 지정해 주세요}"
DB_PASSWORD="${4:?DB 비밀번호를 지정해 주세요}"
DUMP_FILE="${5:?덤프 파일 경로를 지정해 주세요}"

log() {
  echo "[migrate-db] $*"
}

check_prerequisites() {
  if [[ ! -f "${DUMP_FILE}" ]]; then
    log "덤프 파일이 없습니다: ${DUMP_FILE}"
    exit 1
  fi

  if ! command -v mysql >/dev/null 2>&1; then
    log "mysql 클라이언트를 설치합니다."
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y mysql-client
  fi
}

test_connection() {
  log "Cloud DB 연결을 확인합니다: ${CLOUD_DB_HOST}:3306"
  if ! MYSQL_PWD="${DB_PASSWORD}" mysql -h "${CLOUD_DB_HOST}" -P 3306 -u "${DB_USER}" -e "SELECT 1;" >/dev/null 2>&1; then
    log "Cloud DB 연결에 실패했습니다. 호스트/계정/네트워크를 확인해 주세요."
    exit 1
  fi
  log "Cloud DB 연결 성공."
}

configure_cloud_db() {
  log "Cloud DB 설정을 확인합니다."

  # 왜: NCP Cloud DB는 파라미터 그룹에서 lower_case_table_names를 설정해야 합니다.
  # 이 스크립트에서는 런타임 변경 가능한 항목만 처리합니다.
  # lower_case_table_names는 NCP 콘솔 > Cloud DB > DB Config에서 사전 설정 필요.
  log "주의: lower_case_table_names=1은 NCP 콘솔 DB Config에서 사전 설정이 필요합니다."

  # 왜: 레거시 dump에 함수(FUNCTION) 생성 구문이 있고, MySQL 8 기본 정책에서는
  # DETERMINISTIC/READS SQL DATA 미지정 함수가 차단되어 import가 실패할 수 있습니다.
  log "log_bin_trust_function_creators를 활성화합니다."
  MYSQL_PWD="${DB_PASSWORD}" mysql -h "${CLOUD_DB_HOST}" -P 3306 -u "${DB_USER}" \
    -e "SET GLOBAL log_bin_trust_function_creators = 1;" 2>/dev/null || {
    log "경고: log_bin_trust_function_creators 설정에 실패했습니다."
    log "NCP 콘솔 DB Config에서 수동 설정이 필요할 수 있습니다."
  }
}

normalize_dump() {
  local normalized_file="${DUMP_FILE}.normalized"

  # 왜: 로컬/사설IP 기반 DEFINER가 남아 있으면 Cloud DB에서 권한 오류(1227)로 import가 중단됩니다.
  log "덤프 파일에서 DEFINER를 제거합니다."
  sed -E 's/DEFINER=`[^`]+`@`[^`]+`\s+//g' "${DUMP_FILE}" > "${normalized_file}"

  local definer_count
  definer_count=$(grep -cE 'DEFINER=' "${DUMP_FILE}" || echo "0")
  log "DEFINER 제거: ${definer_count}건"

  DUMP_FILE="${normalized_file}"
  log "정규화된 덤프 파일: ${DUMP_FILE}"
}

create_database() {
  log "데이터베이스 ${DB_NAME}이 존재하는지 확인합니다."
  MYSQL_PWD="${DB_PASSWORD}" mysql -h "${CLOUD_DB_HOST}" -P 3306 -u "${DB_USER}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || {
    log "데이터베이스 생성 실패 (이미 존재할 수 있음). 계속 진행합니다."
  }
}

import_dump() {
  log "DB dump import를 시작합니다: ${DUMP_FILE} → ${CLOUD_DB_HOST}/${DB_NAME}"
  log "덤프 파일 크기: $(du -h "${DUMP_FILE}" | cut -f1)"

  MYSQL_PWD="${DB_PASSWORD}" mysql -h "${CLOUD_DB_HOST}" -P 3306 -u "${DB_USER}" \
    "${DB_NAME}" < "${DUMP_FILE}"

  log "DB dump import가 완료되었습니다."
}

verify_import() {
  log "import 결과를 확인합니다."
  local table_count
  table_count=$(MYSQL_PWD="${DB_PASSWORD}" mysql -h "${CLOUD_DB_HOST}" -P 3306 -u "${DB_USER}" \
    "${DB_NAME}" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")
  log "테이블 수: ${table_count}"

  if [[ "${table_count}" -eq 0 ]]; then
    log "경고: 테이블이 하나도 없습니다. 덤프 파일을 확인해 주세요."
    exit 1
  fi
}

main() {
  log "=== NCP Cloud DB 마이그레이션 시작 ==="
  check_prerequisites
  test_connection
  configure_cloud_db
  normalize_dump
  create_database
  import_dump
  verify_import
  log "=== NCP Cloud DB 마이그레이션 완료 ==="
}

main "$@"
