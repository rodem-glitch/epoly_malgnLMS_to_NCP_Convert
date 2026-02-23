#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP 배포 후 학사 연동을 한 번에 초기화하는 원스탑 스크립트입니다.
# 사용법: sudo bash setup-haksa-sync.sh [--dry-run]
#
# 수행 항목:
# 1) .env 로드 → DB 접속 정보 파싱
# 2) 학사 DDL 적용 (CREATE TABLE IF NOT EXISTS)
# 3) 기존 MyISAM 테이블 → InnoDB 마이그레이션
# 4) SiteConfig 기본값 초기화 (poly_auto_delete_yn 등)
# 5) 첫 동기화 수동 실행 (dry-run)
# 6) cron 등록 확인

STACK_TARGET="/opt/polytech-lms"
DRY_RUN=false

for arg in "$@"; do
  case "${arg}" in
    --dry-run) DRY_RUN=true ;;
  esac
done

log() {
  echo "[setup-haksa] $(date '+%H:%M:%S') $*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    log "루트 권한이 필요합니다: sudo bash $0"
    exit 1
  fi
}

# ─── 1) .env 로드 → DB 접속 정보 파싱 ───────────────────────────
load_db_config() {
  local env_file="${STACK_TARGET}/.env"
  if [[ ! -f "${env_file}" ]]; then
    log ".env 파일이 없습니다: ${env_file}"
    exit 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a

  DB_HOST=$(echo "${APP_DB_URL}" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|')
  DB_PORT=$(echo "${APP_DB_URL}" | sed -E 's|jdbc:mysql://[^:]+:([0-9]+)/.*|\1|')
  DB_NAME=$(echo "${APP_DB_URL}" | sed -E 's|jdbc:mysql://[^/]+/([^?]+).*|\1|')

  log "DB 접속: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
}

# 왜: mysql 커맨드를 반복 사용하므로 헬퍼 함수로 추출합니다.
run_sql() {
  mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${DB_NAME}" -e "$1" 2>&1
}

# ─── 2) 학사 DDL 적용 ─────────────────────────────────────────
apply_ddl() {
  local sql_dir="${STACK_TARGET}/sql"
  if [[ ! -d "${sql_dir}" ]]; then
    log "sql/ 디렉토리가 없습니다. 번들에 DDL이 포함되어 있는지 확인하세요."
    return 1
  fi

  for ddl_file in "${sql_dir}"/ddl_poly_*.sql; do
    [[ -f "${ddl_file}" ]] || continue
    log "DDL 적용: $(basename "${ddl_file}")"
    mysql -h "${DB_HOST}" -P "${DB_PORT}" -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${DB_NAME}" < "${ddl_file}" 2>&1
  done
  log "학사 DDL 적용 완료."
}

# ─── 3) MyISAM → InnoDB 마이그레이션 ────────────────────────────
migrate_to_innodb() {
  log "MyISAM → InnoDB 마이그레이션을 확인합니다."

  # 왜: LM_POLY_ 접두사 테이블 중 MyISAM인 것만 InnoDB로 변환합니다.
  local tables
  tables=$(run_sql "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}' AND TABLE_NAME LIKE 'LM_POLY_%' AND ENGINE='MyISAM'" 2>/dev/null | tail -n +2)

  if [[ -z "${tables}" ]]; then
    log "MyISAM 테이블이 없습니다. 마이그레이션 불필요."
    return
  fi

  for tbl in ${tables}; do
    log "ALTER TABLE ${tbl} ENGINE=InnoDB"
    if [[ "${DRY_RUN}" == "true" ]]; then
      log "(dry-run) 건너뜁니다."
    else
      run_sql "ALTER TABLE \`${tbl}\` ENGINE=InnoDB" || true
    fi
  done
  log "InnoDB 마이그레이션 완료."
}

# ─── 4) SiteConfig 기본값 초기화 ────────────────────────────────
init_site_config() {
  log "SiteConfig 기본값을 확인합니다."

  # 왜: poly_auto_delete_yn이 없으면 안전 기본값(N)으로 초기화합니다.
  # 운영자가 직접 Y로 변경해야 자동삭제가 활성화됩니다.
  local existing
  existing=$(run_sql "SELECT COUNT(*) FROM LM_SITE_CONFIG WHERE config_key='poly_auto_delete_yn'" 2>/dev/null | tail -1)

  if [[ "${existing}" == "0" ]]; then
    if [[ "${DRY_RUN}" == "true" ]]; then
      log "(dry-run) poly_auto_delete_yn=N INSERT를 건너뜁니다."
    else
      run_sql "INSERT INTO LM_SITE_CONFIG (site_id, config_key, config_value, reg_date) VALUES (1, 'poly_auto_delete_yn', 'N', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'))" || true
      log "poly_auto_delete_yn=N 초기화 완료."
    fi
  else
    log "poly_auto_delete_yn이 이미 설정되어 있습니다."
  fi
}

# ─── 5) 첫 동기화 수동 실행 ─────────────────────────────────────
run_first_sync() {
  local sync_script="${STACK_TARGET}/tools/poly_sync/run_poly_sync.sh"
  if [[ ! -f "${sync_script}" ]]; then
    log "poly_sync 스크립트가 없습니다: ${sync_script}"
    return 1
  fi

  local current_year
  current_year=$(date '+%Y')
  local next_year=$((current_year + 1))

  log "첫 동기화를 실행합니다 (${current_year}~${next_year})..."

  if [[ "${DRY_RUN}" == "true" ]]; then
    log "(dry-run) 동기화 실행을 건너뜁니다."
    return
  fi

  # 왜: 첫 실행이므로 결과를 JSON으로 저장하여 확인합니다.
  local result_json="/tmp/poly_sync_first_run.json"
  POLY_SYNC_BASE_URL="http://127.0.0.1:8080" \
  POLY_SYNC_RESPONSE_JSON="${result_json}" \
    bash "${sync_script}" --start-year "${current_year}" --end-year "${next_year}" || true

  if [[ -f "${result_json}" ]]; then
    log "동기화 결과:"
    cat "${result_json}"
    echo ""
  fi
}

# ─── 6) cron 등록 확인 ──────────────────────────────────────────
verify_cron() {
  local cron_marker="# polytech-lms-haksa-sync"
  if crontab -l 2>/dev/null | grep -q "${cron_marker}"; then
    log "학사 동기화 cron이 등록되어 있습니다."
    crontab -l 2>/dev/null | grep "${cron_marker}"
  else
    log "학사 동기화 cron이 등록되어 있지 않습니다."
    log "deploy-was.sh를 실행하면 자동 등록됩니다."
  fi
}

# ─── 결과 요약 ──────────────────────────────────────────────────
show_summary() {
  echo ""
  echo "============================================"
  echo "  학사 연동 초기화 결과 요약"
  echo "============================================"

  # 테이블 수 확인
  local table_count
  table_count=$(run_sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}' AND TABLE_NAME LIKE 'LM_POLY_%'" 2>/dev/null | tail -1)
  echo "  미러 테이블: ${table_count}개"

  # 과목 수 확인
  local course_count
  course_count=$(run_sql "SELECT COUNT(*) FROM LM_POLY_COURSE" 2>/dev/null | tail -1 || echo "0")
  echo "  과목 (LM_POLY_COURSE): ${course_count}건"

  # 수강생 수 확인
  local student_count
  student_count=$(run_sql "SELECT COUNT(*) FROM LM_POLY_STUDENT" 2>/dev/null | tail -1 || echo "0")
  echo "  수강생 (LM_POLY_STUDENT): ${student_count}건"

  # 회원 수 확인
  local member_count
  member_count=$(run_sql "SELECT COUNT(*) FROM LM_POLY_MEMBER" 2>/dev/null | tail -1 || echo "0")
  echo "  회원 (LM_POLY_MEMBER): ${member_count}건"

  echo ""
  echo "  로그 디렉토리: /var/log/malgnlms/"
  echo "  수동 실행: POLY_SYNC_BASE_URL=http://127.0.0.1:8080 ${STACK_TARGET}/tools/poly_sync/run_poly_sync.sh --start-year $(date '+%Y') --end-year $(($(date '+%Y') + 1))"
  echo "============================================"
}

main() {
  require_root
  log "=== 학사 연동 초기화 시작 ==="
  [[ "${DRY_RUN}" == "true" ]] && log "(dry-run 모드)"

  load_db_config
  apply_ddl
  migrate_to_innodb
  init_site_config
  run_first_sync
  verify_cron
  show_summary

  log "=== 학사 연동 초기화 완료 ==="
}

main "$@"
