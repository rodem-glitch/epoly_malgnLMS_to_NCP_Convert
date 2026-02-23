#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP WAS 서버(newkl-was01) 전용 배포 스크립트입니다.
# GCP deploy-stack.sh에서 Nginx/certbot/SSL/MySQL import 로직을 제거하고
# Docker + Docker Compose + 레거시 권한 설정만 담당합니다.

STACK_SOURCE="${1:-$HOME/polytech-lms-stack}"
STACK_TARGET="/opt/polytech-lms"

log() {
  echo "[deploy-was] $*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    log "루트 권한이 필요합니다. sudo로 다시 실행해 주세요."
    exit 1
  fi
}

install_docker() {
  if command -v docker >/dev/null 2>&1; then
    log "Docker는 이미 설치되어 있습니다."
    return
  fi

  log "Docker를 설치합니다."
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg

  . /etc/os-release
  local codename="${VERSION_CODENAME:-noble}"
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${codename} stable" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable docker
  systemctl start docker
}

install_base_packages() {
  if command -v jq >/dev/null 2>&1 && command -v rsync >/dev/null 2>&1; then
    # 왜: CI에서 이미 준비된 서버에 매번 apt update/install을 반복하면 배포 시간이 늘어납니다.
    log "기본 패키지가 이미 설치되어 있어 apt 업데이트/설치를 건너뜁니다."
    return
  fi

  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg lsb-release jq rsync
}

# 왜: 배포 전 현재 가동 중인 JAR/docker-compose/.env를 백업하여, 문제 시 롤백할 수 있게 합니다.
backup_current() {
  local backup_base="/opt/polytech-lms-backups"
  local timestamp
  timestamp=$(date '+%Y%m%d-%H%M%S')
  local backup_dir="${backup_base}/${timestamp}"

  if [[ ! -d "${STACK_TARGET}" ]]; then
    log "배포 대상이 아직 없어 백업을 건너뜁니다."
    return
  fi

  mkdir -p "${backup_dir}"

  # 왜: JAR, docker-compose, .env만 백업합니다 (레거시 소스는 용량이 커서 제외).
  [[ -f "${STACK_TARGET}/app/polytech-lms-api.jar" ]] && cp "${STACK_TARGET}/app/polytech-lms-api.jar" "${backup_dir}/"
  [[ -f "${STACK_TARGET}/docker-compose.yml" ]] && cp "${STACK_TARGET}/docker-compose.yml" "${backup_dir}/"
  [[ -f "${STACK_TARGET}/.env" ]] && cp "${STACK_TARGET}/.env" "${backup_dir}/"

  log "현재 배포 백업 완료: ${backup_dir}"

  # 왜: 7일 이상 된 백업은 자동 삭제하여 디스크를 확보합니다.
  find "${backup_base}" -maxdepth 1 -type d -mtime +7 -not -path "${backup_base}" -exec rm -rf {} + 2>/dev/null || true
  log "7일 초과 백업 정리 완료."
}

sync_stack_files() {
  if [[ ! -d "${STACK_SOURCE}" ]]; then
    log "스택 소스 폴더가 없습니다: ${STACK_SOURCE}"
    exit 1
  fi

  mkdir -p "${STACK_TARGET}"
  # 왜: --delete로 번들에 없는 파일을 정리하되, 운영 중 생성되는 런타임 데이터는 보호합니다.
  # data/file(업로드), data/log(로그), data/tmp(업로드 임시), WEB-INF/work(JSP 컴파일 캐시)
  rsync -av --delete \
    --exclude 'legacy/public_html/data/file/' \
    --exclude 'legacy/public_html/data/log/' \
    --exclude 'legacy/public_html/data/tmp/' \
    --exclude 'legacy/public_html/WEB-INF/work/' \
    "${STACK_SOURCE}/" "${STACK_TARGET}/"
}

prepare_legacy_permissions() {
  local webinf_dir="${STACK_TARGET}/legacy/public_html/WEB-INF"
  local work_dir="${webinf_dir}/work"
  local classes_dir="${webinf_dir}/classes"
  local data_dir="${STACK_TARGET}/legacy/public_html/data"
  local log_dir="${data_dir}/log"
  local tmp_dir="${data_dir}/tmp"
  local file_dir="${data_dir}/file"

  if [[ ! -d "${webinf_dir}" ]]; then
    return
  fi

  # 왜: Resin 컨테이너 기본 계정(resin)이 JSP를 처음 컴파일할 때 WEB-INF/work에 파일을 생성합니다.
  mkdir -p "${work_dir}"
  chmod -R a+rwX "${work_dir}"

  # 왜: Resin은 JSP/클래스 컴파일 산출물을 WEB-INF/classes에도 기록할 수 있습니다.
  mkdir -p "${classes_dir}"
  chmod -R a+rwX "${classes_dir}"

  # 왜: 레거시 공통 로그(Malgn.errorLog)가 /data/log를 기준으로 동작합니다.
  mkdir -p "${log_dir}"
  chmod -R a+rwX "${log_dir}"

  # 왜: multipart/form-data(파일 업로드) 요청 파싱 시 /data/tmp가 필요합니다.
  mkdir -p "${tmp_dir}"
  chmod -R a+rwX "${tmp_dir}"

  # 왜: 업로드된 파일이 실제로 저장되는 기본 경로(/data/file)도 쓰기 가능해야 합니다.
  mkdir -p "${file_dir}"
  chmod -R a+rwX "${file_dir}"
}

load_env() {
  if [[ ! -f "${STACK_TARGET}/.env" ]]; then
    log ".env 파일이 없습니다: ${STACK_TARGET}/.env"
    exit 1
  fi

  set -a
  # shellcheck disable=SC1091
  source "${STACK_TARGET}/.env"
  set +a
}

start_stack() {
  log "Docker 스택을 시작합니다."
  cd "${STACK_TARGET}"
  if [[ "${DEPLOY_FORCE_PULL:-false}" == "true" ]]; then
    log "DEPLOY_FORCE_PULL=true 이므로 이미지 pull을 수행합니다."
    docker compose pull
  else
    log "이미지 pull을 건너뛰고 즉시 재기동합니다."
  fi
  docker compose up -d
}

configure_resin_jvm() {
  if ! docker ps --format '{{.Names}}' | grep -qx "lms-resin"; then
    log "lms-resin 컨테이너가 없어 JVM 설정을 건너뜁니다."
    return
  fi

  # 왜: Resin 기본 JVM 힙이 256MB로, 학사 동기화(poly_sync.jsp)에서 대량 데이터 처리 시 OOM이 발생합니다.
  # 컨테이너 메모리 제한(4GB) 내에서 JVM 힙을 2GB로 올립니다.
  local current_xmx
  current_xmx=$(docker exec lms-resin sh -c 'ps aux | grep java' 2>/dev/null | grep -oP '\-Xmx\S+' | head -1 || echo "")

  if [[ "${current_xmx}" == "-Xmx2g" ]]; then
    log "Resin JVM 힙이 이미 2GB로 설정되어 있습니다."
    return
  fi

  log "Resin JVM 힙을 2GB로 설정합니다 (기존: ${current_xmx:-기본값})"
  docker exec lms-resin sh -c "sed -i 's/^# jvm_args.*/jvm_args  : -Xmx2g -Xms1g/' /etc/resin/resin.properties"

  docker restart lms-resin >/dev/null
  sleep 15
  log "Resin JVM 힙 설정 완료 (2GB)."
}

apply_kollus_tls_truststore_fix() {
  if ! docker ps --format '{{.Names}}' | grep -qx "lms-resin"; then
    log "lms-resin 컨테이너가 없어 Kollus TLS 보정을 건너뜁니다."
    return
  fi

  # 왜: Resin(Java 8u74) 기본 truststore에는 Kollus 인증서 체인이 누락되어
  # 교수자 채널 API에서 PKIX 에러가 발생할 수 있습니다.
  log "Kollus TLS 인증서 체인을 Resin truststore에 반영합니다."
  openssl s_client -showcerts -servername api.kr.kollus.com -connect api.kr.kollus.com:443 </dev/null 2>/tmp/kollus-sclient.err > /tmp/kollus-chain.txt
  awk '/BEGIN CERTIFICATE/{flag=1;f=sprintf("/tmp/kollus-cert-%d.pem",++i)} flag{print > f} /END CERTIFICATE/{flag=0}' /tmp/kollus-chain.txt

  if [[ ! -f /tmp/kollus-cert-2.pem || ! -f /tmp/kollus-cert-3.pem ]]; then
    log "Kollus 인증서 체인 추출에 실패했습니다."
    exit 1
  fi

  docker cp /tmp/kollus-cert-2.pem lms-resin:/tmp/kollus-thawte-g1.pem
  docker cp /tmp/kollus-cert-3.pem lms-resin:/tmp/kollus-digicert-g2.pem

  docker exec lms-resin sh -lc '
    CACERTS="/usr/java/jdk1.8.0_74/jre/lib/security/cacerts"
    keytool -list -keystore "$CACERTS" -storepass changeit -alias kollus-thawte-g1 >/dev/null 2>&1 || \
      keytool -importcert -noprompt -trustcacerts -alias kollus-thawte-g1 -file /tmp/kollus-thawte-g1.pem -keystore "$CACERTS" -storepass changeit
    keytool -list -keystore "$CACERTS" -storepass changeit -alias kollus-digicert-g2 >/dev/null 2>&1 || \
      keytool -importcert -noprompt -trustcacerts -alias kollus-digicert-g2 -file /tmp/kollus-digicert-g2.pem -keystore "$CACERTS" -storepass changeit
  '

  docker restart lms-resin >/dev/null
  sleep 3
  log "Kollus TLS 인증서 보정이 완료되었습니다."
}

apply_haksa_ddl() {
  local sql_dir="${STACK_TARGET}/sql"
  if [[ ! -d "${sql_dir}" ]]; then
    log "학사 DDL 디렉토리가 없어 건너뜁니다."
    return
  fi

  # 왜: .env에서 로드된 APP_DB_URL을 파싱하여 DB 접속 정보를 추출합니다.
  local db_host db_port db_name
  db_host=$(echo "${APP_DB_URL}" | sed -E 's|jdbc:mysql://([^:/]+).*|\1|')
  db_port=$(echo "${APP_DB_URL}" | sed -E 's|jdbc:mysql://[^:]+:([0-9]+)/.*|\1|')
  db_name=$(echo "${APP_DB_URL}" | sed -E 's|jdbc:mysql://[^/]+/([^?]+).*|\1|')

  for ddl_file in "${sql_dir}"/ddl_poly_*.sql; do
    [[ -f "${ddl_file}" ]] || continue
    log "학사 DDL 적용: $(basename "${ddl_file}")"
    # 왜: CREATE TABLE IF NOT EXISTS이므로 이미 있으면 건너뛰고, 없으면 생성합니다.
    mysql -h "${db_host}" -P "${db_port}" -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${db_name}" < "${ddl_file}" 2>&1 || true
  done
  log "학사 DDL 적용 완료."
}

setup_haksa_cron() {
  local sync_script="${STACK_TARGET}/tools/poly_sync/run_poly_sync.sh"
  if [[ ! -f "${sync_script}" ]]; then
    log "poly_sync 스크립트가 없어 cron 등록을 건너뜁니다."
    return
  fi

  local cron_marker="# polytech-lms-haksa-sync"
  local current_year
  current_year=$(date '+%Y')
  local next_year=$((current_year + 1))

  # 왜: 이미 등록된 cron이 있으면 중복 등록하지 않습니다.
  if crontab -l 2>/dev/null | grep -q "${cron_marker}"; then
    log "학사 동기화 cron이 이미 등록되어 있습니다."
    return
  fi

  local log_dir="/var/log/malgnlms"
  mkdir -p "${log_dir}"

  # 왜: 기존 crontab에 학사 동기화 2건(전체/수강생)을 추가합니다.
  (crontab -l 2>/dev/null || true; cat <<CRON
# --- 학사 미러 동기화 (polytech-lms) ---  ${cron_marker}
# 매일 02:10 전체 동기화
10 2 * * * cd ${STACK_TARGET} && flock -n /tmp/poly_sync.lock POLY_SYNC_BASE_URL=http://127.0.0.1:8080 POLY_SYNC_LOG_FILE=${log_dir}/poly_sync.log ${sync_script} --start-year ${current_year} --end-year ${next_year} >> ${log_dir}/poly_sync_cron.log 2>&1  ${cron_marker}
# 평일 03:10 수강생만 빠른 동기화
10 3 * * 1-5 cd ${STACK_TARGET} && flock -n /tmp/poly_sync_student.lock POLY_SYNC_BASE_URL=http://127.0.0.1:8080 POLY_SYNC_LOG_FILE=${log_dir}/poly_sync_student.log ${sync_script} --mode student_only --start-year ${current_year} --end-year ${next_year} >> ${log_dir}/poly_sync_student_cron.log 2>&1  ${cron_marker}
CRON
  ) | crontab -

  log "학사 동기화 cron 등록 완료 (매일 02:10 전체, 평일 03:10 수강생)."
}

main() {
  require_root
  install_base_packages
  install_docker
  backup_current
  sync_stack_files
  prepare_legacy_permissions
  load_env
  start_stack
  configure_resin_jvm
  apply_kollus_tls_truststore_fix
  apply_haksa_ddl
  setup_haksa_cron
  log "WAS 배포가 완료되었습니다."
}

main "$@"
