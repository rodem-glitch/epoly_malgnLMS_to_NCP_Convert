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

sync_stack_files() {
  if [[ ! -d "${STACK_SOURCE}" ]]; then
    log "스택 소스 폴더가 없습니다: ${STACK_SOURCE}"
    exit 1
  fi

  mkdir -p "${STACK_TARGET}"
  rsync -av --delete "${STACK_SOURCE}/" "${STACK_TARGET}/"
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

main() {
  require_root
  install_base_packages
  install_docker
  sync_stack_files
  prepare_legacy_permissions
  load_env
  start_stack
  apply_kollus_tls_truststore_fix
  log "WAS 배포가 완료되었습니다."
}

main "$@"
