#!/usr/bin/env bash
set -euo pipefail

# 왜: 배포 후 문제 발생 시, 가장 최근(또는 지정) 백업에서 JAR/docker-compose를 복원합니다.
# 사용법: sudo bash rollback-was.sh [타임스탬프]
#   예) sudo bash rollback-was.sh 20260224-153000
#   인자 없으면 가장 최근 백업을 사용합니다.

BACKUP_BASE="/opt/polytech-lms-backups"
STACK_TARGET="/opt/polytech-lms"

log() {
  echo "[rollback-was] $(date '+%H:%M:%S') $*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    log "루트 권한이 필요합니다: sudo bash $0"
    exit 1
  fi
}

find_backup() {
  local timestamp="${1:-}"

  if [[ -n "${timestamp}" ]]; then
    local target="${BACKUP_BASE}/${timestamp}"
    if [[ ! -d "${target}" ]]; then
      log "지정된 백업이 없습니다: ${target}"
      log "사용 가능한 백업:"
      ls -1 "${BACKUP_BASE}" 2>/dev/null || echo "(없음)"
      exit 1
    fi
    echo "${target}"
  else
    # 왜: 가장 최근 백업 디렉터리를 자동 선택합니다.
    local latest
    latest=$(find "${BACKUP_BASE}" -maxdepth 1 -mindepth 1 -type d | sort -r | head -1)
    if [[ -z "${latest}" ]]; then
      log "사용 가능한 백업이 없습니다: ${BACKUP_BASE}"
      exit 1
    fi
    echo "${latest}"
  fi
}

rollback() {
  local backup_dir="$1"

  log "백업에서 복원합니다: ${backup_dir}"

  # 왜: 컨테이너를 먼저 내립니다.
  cd "${STACK_TARGET}"
  docker compose down || true

  # 왜: JAR 복원
  if [[ -f "${backup_dir}/polytech-lms-api.jar" ]]; then
    cp "${backup_dir}/polytech-lms-api.jar" "${STACK_TARGET}/app/polytech-lms-api.jar"
    log "JAR 복원 완료."
  else
    log "백업에 JAR이 없습니다. 기존 JAR을 유지합니다."
  fi

  # 왜: docker-compose.yml 복원
  if [[ -f "${backup_dir}/docker-compose.yml" ]]; then
    cp "${backup_dir}/docker-compose.yml" "${STACK_TARGET}/docker-compose.yml"
    log "docker-compose.yml 복원 완료."
  fi

  # 왜: .env 복원
  if [[ -f "${backup_dir}/.env" ]]; then
    cp "${backup_dir}/.env" "${STACK_TARGET}/.env"
    log ".env 복원 완료."
  fi

  # 왜: 컨테이너 재시작
  docker compose up -d
  log "롤백 완료. 컨테이너 상태를 확인합니다."
  docker compose ps
}

main() {
  require_root
  local backup_dir
  backup_dir=$(find_backup "${1:-}")
  rollback "${backup_dir}"
}

main "$@"
