#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP WEB 서버(newkl-web01) 전용 배포 스크립트입니다.
# Nginx 설치, 설정 배포, SSL 인증서 발급을 담당합니다.
# Firebase proxy를 대체하여 Nginx가 WAS 서버로 직접 reverse proxy합니다.

CONF_SOURCE="${1:-$HOME/nginx-conf}"
WEB_DOMAIN="${2:-}"
LETSENCRYPT_EMAIL="${3:-}"

log() {
  echo "[deploy-web] $*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    log "루트 권한이 필요합니다. sudo로 다시 실행해 주세요."
    exit 1
  fi
}

install_nginx() {
  if command -v nginx >/dev/null 2>&1; then
    log "Nginx는 이미 설치되어 있습니다."
    return
  fi

  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y nginx certbot python3-certbot-nginx
  systemctl enable nginx
}

configure_nginx() {
  log "Nginx 리버스 프록시 설정을 적용합니다."

  if [[ ! -f "${CONF_SOURCE}/lms-web.conf" ]]; then
    log "Nginx 설정 파일이 없습니다: ${CONF_SOURCE}/lms-web.conf"
    exit 1
  fi

  install -m 0644 "${CONF_SOURCE}/lms-web.conf" /etc/nginx/sites-available/lms-web.conf
  ln -sfn /etc/nginx/sites-available/lms-web.conf /etc/nginx/sites-enabled/lms-web.conf
  rm -f /etc/nginx/sites-enabled/default

  nginx -t
  systemctl restart nginx
}

try_issue_certificate() {
  if [[ -z "${WEB_DOMAIN}" || "${WEB_DOMAIN}" == "example.com" ]]; then
    log "도메인이 예시값이라 SSL 발급을 건너뜁니다."
    return
  fi

  if [[ -z "${LETSENCRYPT_EMAIL}" ]]; then
    log "LETSENCRYPT_EMAIL이 비어 있어 SSL 발급을 건너뜁니다."
    return
  fi

  # 왜: certbot이 이미 설치되어 있지 않으면 설치합니다.
  if ! command -v certbot >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y certbot python3-certbot-nginx
  fi

  local public_ip resolved_ip
  public_ip="$(curl -4s https://ifconfig.me || true)"
  resolved_ip="$(getent ahostsv4 "${WEB_DOMAIN}" | awk '{print $1; exit}' || true)"

  if [[ -z "${public_ip}" || -z "${resolved_ip}" || "${public_ip}" != "${resolved_ip}" ]]; then
    log "DNS가 아직 서버 IP와 일치하지 않아 SSL 발급을 건너뜁니다."
    log "현재 서버 IP: ${public_ip:-미확인}, 도메인 IP: ${resolved_ip:-미확인}"
    return
  fi

  log "Let's Encrypt 인증서를 발급합니다."
  certbot --nginx --agree-tos --non-interactive --redirect -m "${LETSENCRYPT_EMAIL}" -d "${WEB_DOMAIN}"
}

main() {
  require_root
  install_nginx
  configure_nginx
  try_issue_certificate
  log "WEB 배포가 완료되었습니다."
}

main "$@"
