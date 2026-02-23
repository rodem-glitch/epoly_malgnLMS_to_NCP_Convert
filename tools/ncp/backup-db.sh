#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP Cloud DB의 자동 백업 스크립트입니다.
# mysqldump로 일관된 백업을 생성하고, 일별/주별 보관 정책을 적용합니다.
# cron 예) 0 3 * * * /opt/polytech-lms/tools/backup-db.sh >> /var/log/db-backup.log 2>&1

BACKUP_BASE="/opt/polytech-lms-backups/db"
DAILY_DIR="${BACKUP_BASE}/daily"
WEEKLY_DIR="${BACKUP_BASE}/weekly"

# 왜: .env에서 DB 접속 정보를 로드합니다.
ENV_FILE="${ENV_FILE:-/opt/polytech-lms/.env}"

log() {
  echo "[backup-db] $(date '+%Y-%m-%d %H:%M:%S') $*"
}

load_env() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    log ".env 파일이 없습니다: ${ENV_FILE}"
    exit 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
}

parse_db_url() {
  # 왜: APP_DB_URL에서 호스트/포트/DB명을 추출합니다.
  # 형식: jdbc:mysql://HOST:PORT/DBNAME?params...
  local url="${APP_DB_URL:-}"
  if [[ -z "${url}" ]]; then
    log "APP_DB_URL이 설정되지 않았습니다."
    exit 1
  fi

  DB_HOST=$(echo "${url}" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|')
  DB_PORT=$(echo "${url}" | sed -E 's|jdbc:mysql://[^:]+:([0-9]+)/.*|\1|')
  DB_NAME=$(echo "${url}" | sed -E 's|jdbc:mysql://[^/]+/([^?]+).*|\1|')

  if [[ -z "${DB_HOST}" || -z "${DB_PORT}" || -z "${DB_NAME}" ]]; then
    log "DB URL 파싱 실패: ${url}"
    exit 1
  fi
}

run_backup() {
  local backup_dir="$1"
  local filename="$2"

  mkdir -p "${backup_dir}"

  local backup_file="${backup_dir}/${filename}"

  log "백업 시작: ${DB_HOST}:${DB_PORT}/${DB_NAME} → ${backup_file}"

  # 왜: --single-transaction으로 InnoDB 테이블의 일관된 스냅샷을 생성합니다.
  # --routines로 저장 프로시저/함수도 포함합니다.
  mysqldump \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${MYSQL_USER}" \
    --password="${MYSQL_PASSWORD}" \
    --single-transaction \
    --routines \
    --triggers \
    --set-gtid-purged=OFF \
    "${DB_NAME}" | gzip > "${backup_file}"

  local size
  size=$(du -h "${backup_file}" | cut -f1)
  log "백업 완료: ${backup_file} (${size})"
}

cleanup_old() {
  # 왜: 일별 백업은 7일, 주별 백업은 28일 보관합니다.
  log "오래된 백업을 정리합니다."
  find "${DAILY_DIR}" -name "*.sql.gz" -mtime +7 -delete 2>/dev/null || true
  find "${WEEKLY_DIR}" -name "*.sql.gz" -mtime +28 -delete 2>/dev/null || true
  log "백업 정리 완료."
}

main() {
  load_env
  parse_db_url

  local today
  today=$(date '+%Y%m%d')
  local day_of_week
  day_of_week=$(date '+%u')

  # 왜: 매일 일별 백업을 생성합니다.
  run_backup "${DAILY_DIR}" "lms-${today}.sql.gz"

  # 왜: 일요일(7)이면 주별 백업도 생성합니다.
  if [[ "${day_of_week}" == "7" ]]; then
    run_backup "${WEEKLY_DIR}" "lms-weekly-${today}.sql.gz"
    log "주별 백업 생성 완료."
  fi

  cleanup_old

  log "DB 백업 작업 완료."
}

main "$@"
