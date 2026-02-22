#!/usr/bin/env bash
set -euo pipefail

# 왜: 이 스크립트는 "클릭 최소화" 목표라서, VM 초기 패키지 설치와 앱 배포를 한 번에 처리합니다.
STACK_SOURCE="${1:-$HOME/polytech-lms-stack}"
STACK_TARGET="/opt/polytech-lms"

log() {
  echo "[deploy-stack] $*"
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
  local codename="${VERSION_CODENAME:-jammy}"
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${codename} stable" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable docker
  systemctl start docker
}

install_base_packages() {
  if command -v jq >/dev/null 2>&1 && command -v rsync >/dev/null 2>&1 && command -v nginx >/dev/null 2>&1 && command -v certbot >/dev/null 2>&1; then
    # 왜: CI에서 이미 준비된 VM에 매번 apt update/install을 반복하면 배포 시간이 크게 늘어 타임아웃이 자주 발생합니다.
    log "기본 패키지가 이미 설치되어 있어 apt 업데이트/설치를 건너뜁니다."
    return
  fi

  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg lsb-release jq rsync nginx certbot python3-certbot-nginx
}

configure_nginx() {
  log "Nginx 리버스 프록시 설정을 적용합니다."
  install -m 0644 "${STACK_TARGET}/nginx/lms-api.conf" /etc/nginx/sites-available/lms-api.conf
  ln -sfn /etc/nginx/sites-available/lms-api.conf /etc/nginx/sites-enabled/lms-api.conf
  rm -f /etc/nginx/sites-enabled/default
  nginx -t
  systemctl enable nginx
  systemctl restart nginx
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
    # 왜: 운영 복구 속도를 우선하기 위해, 기본 동작은 pull 없이 즉시 재기동합니다.
    log "이미지 pull을 건너뛰고 즉시 재기동합니다."
  fi
  docker compose up -d
}

start_stack_for_import() {
  log "DB import 선행을 위해 mysql/qdrant만 먼저 시작합니다."
  cd "${STACK_TARGET}"
  if [[ "${DEPLOY_FORCE_PULL:-false}" == "true" ]]; then
    log "DEPLOY_FORCE_PULL=true 이므로 mysql/qdrant 이미지 pull을 수행합니다."
    docker compose pull mysql qdrant
  else
    log "mysql/qdrant 이미지 pull을 건너뛰고 즉시 기동합니다."
  fi
  docker compose up -d mysql qdrant
}

reset_mysql_volume_if_import() {
  if [[ "${DB_IMPORT_ON_DEPLOY:-false}" != "true" ]]; then
    return
  fi

  # 왜: DB 이관 모드에서는 기존 MySQL 볼륨의 초기 비밀번호와 새 .env 비밀번호가 어긋날 수 있어,
  # import 전에 MySQL 볼륨을 초기화해 비밀번호/스키마를 일관되게 맞춥니다.
  log "DB import 모드이므로 기존 MySQL 볼륨을 초기화합니다."
  cd "${STACK_TARGET}"
  docker compose down --remove-orphans || true
  docker volume rm -f polytech-lms_mysql_data >/dev/null 2>&1 || true
}

import_db_if_requested() {
  local import_flag="${DB_IMPORT_ON_DEPLOY:-false}"
  local dump_file="${STACK_TARGET}/migration/source.sql"

  if [[ "${import_flag}" != "true" ]]; then
    log "DB_IMPORT_ON_DEPLOY=false 이므로 DB import를 건너뜁니다."
    return
  fi

  if [[ ! -f "${dump_file}" ]]; then
    log "DB_IMPORT_ON_DEPLOY=true 인데 dump 파일이 없습니다: ${dump_file}"
    exit 1
  fi

  log "MySQL 준비 상태를 확인합니다."
  local ready=0
  for i in $(seq 1 60); do
    if docker exec lms-mysql sh -lc 'mysqladmin -h127.0.0.1 -P3306 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" ping --silent' >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 2
  done
  if [[ "${ready}" != "1" ]]; then
    log "MySQL 준비 대기 시간 초과로 import를 중단합니다."
    exit 1
  fi

  # 왜: 레거시 dump에 함수(FUNCTION) 생성 구문이 있고, MySQL 8 기본 정책에서는
  # DETERMINISTIC/READS SQL DATA 미지정 함수가 차단되어 import가 실패할 수 있습니다.
  # import 전에 신뢰 플래그를 켜서 이관 실패를 방지합니다.
  log "MySQL 함수 생성 신뢰 옵션(log_bin_trust_function_creators)을 활성화합니다."
  docker exec lms-mysql sh -lc 'mysql -h127.0.0.1 -P3306 -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SET GLOBAL log_bin_trust_function_creators = 1;"'

  log "DB dump import를 시작합니다."
  cd "${STACK_TARGET}"
  docker cp "${dump_file}" lms-mysql:/tmp/source.sql
  docker exec lms-mysql sh -lc 'mysql -h127.0.0.1 -P3306 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < /tmp/source.sql'
  docker exec lms-mysql rm -f /tmp/source.sql
  log "DB dump import가 완료되었습니다."
}

try_issue_certificate() {
  local api_domain="${API_DOMAIN:-}"
  local le_email="${LETSENCRYPT_EMAIL:-}"

  if [[ -z "${api_domain}" || "${api_domain}" == "api.example.com" ]]; then
    log "API 도메인이 예시값이라 SSL 발급을 건너뜁니다."
    return
  fi

  if [[ -z "${le_email}" ]]; then
    log "LETSENCRYPT_EMAIL이 비어 있어 SSL 발급을 건너뜁니다."
    return
  fi

  local public_ip resolved_ip
  public_ip="$(curl -4s https://ifconfig.me || true)"
  resolved_ip="$(getent ahostsv4 "${api_domain}" | awk '{print $1; exit}' || true)"

  if [[ -z "${public_ip}" || -z "${resolved_ip}" || "${public_ip}" != "${resolved_ip}" ]]; then
    log "DNS가 아직 VM IP와 일치하지 않아 SSL 발급을 건너뜁니다."
    log "현재 VM IP: ${public_ip:-미확인}, 도메인 IP: ${resolved_ip:-미확인}"
    return
  fi

  log "Let's Encrypt 인증서를 발급합니다."
  certbot --nginx --agree-tos --non-interactive --redirect -m "${le_email}" -d "${api_domain}"
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
  local log_dir="${STACK_TARGET}/legacy/public_html/data/log"
  local tmp_dir="${data_dir}/tmp"
  local file_dir="${data_dir}/file"

  if [[ ! -d "${webinf_dir}" ]]; then
    return
  fi

  # 왜: Resin 컨테이너 기본 계정(resin)이 JSP를 처음 컴파일할 때 WEB-INF/work에 파일을 생성합니다.
  # 이 디렉터리에 쓰기 권한이 없으면 첫 요청부터 500이 발생하므로 배포 시 권한을 선제 보정합니다.
  mkdir -p "${work_dir}"
  chmod -R a+rwX "${work_dir}"

  # 왜: Resin은 JSP/클래스 컴파일 산출물을 WEB-INF/classes에도 기록할 수 있어
  # 이 경로가 없거나 쓰기 불가이면 /tutor_lms 포함 JSP 요청이 500으로 실패합니다.
  mkdir -p "${classes_dir}"
  chmod -R a+rwX "${classes_dir}"

  # 왜: 레거시 공통 로그(Malgn.errorLog)가 /data/log를 기준으로 동작하므로
  # 경로가 없으면 추천/로그인 흐름의 예외 로그 기록 시점에 추가 예외가 발생합니다.
  mkdir -p "${log_dir}"
  chmod -R a+rwX "${log_dir}"

  # 왜: multipart/form-data(파일 업로드 포함) 요청을 파싱할 때 /data/tmp가 필요합니다.
  # 이 폴더가 없거나 쓰기 불가이면 `init.jsp`의 `f.setRequest()`가 예외로 중단되어,
  # API 응답이 Content-Length 0(빈 바디)로 떨어지고 프론트에서는 `rst_code 없음`처럼 보입니다.
  mkdir -p "${tmp_dir}"
  chmod -R a+rwX "${tmp_dir}"

  # 왜: 업로드된 파일이 실제로 저장되는 기본 경로(/data/file)도 Resin 계정이 쓸 수 있어야 합니다.
  mkdir -p "${file_dir}"
  chmod -R a+rwX "${file_dir}"
}

apply_kollus_tls_truststore_fix() {
  if ! docker ps --format '{{.Names}}' | grep -qx "lms-resin"; then
    log "lms-resin 컨테이너가 없어 Kollus TLS 보정을 건너뜁니다."
    return
  fi

  # 왜: Resin(Java 8u74) 기본 truststore에는 Kollus 인증서 체인(Thawte G1/G2)이 누락되어
  # 교수자 채널 API에서 PKIX 에러가 발생할 수 있습니다. 배포 시 truststore를 자동 보정합니다.
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
  reset_mysql_volume_if_import
  if [[ "${DB_IMPORT_ON_DEPLOY:-false}" == "true" ]]; then
    start_stack_for_import
    import_db_if_requested
    start_stack
  else
    start_stack
    import_db_if_requested
  fi
  apply_kollus_tls_truststore_fix
  configure_nginx
  try_issue_certificate
  log "배포가 완료되었습니다."
}

main "$@"
