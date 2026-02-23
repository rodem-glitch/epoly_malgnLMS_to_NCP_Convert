#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP WEB 서버(newkl-web01)에서 직접 실행하는 Pull 방식 배포 스크립트입니다.
# 1-repo 패턴: 저장소에서 Nginx 템플릿을 가져와 렌더링 → deploy-web.sh 실행
# WEB 서버에는 앱 소스가 불필요합니다 (Nginx 설정 템플릿만 사용).
# 사용법: sudo bash pull-deploy-web.sh

# ─── 설정 ───────────────────────────────────────────────────────────
# 왜: 1-repo 패턴으로 단일 저장소에서 Nginx 템플릿을 가져옵니다.
REPO_URL="${REPO_URL:-https://github.com/rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git}"
REPO_BRANCH="${REPO_BRANCH:-prod}"
WORK_DIR="/opt/deploy-workspace"
REPO_DIR="${WORK_DIR}/repo"
CONF_DIR="${WORK_DIR}/nginx-conf"

# 왜: WAS 서버의 사설 IP. Nginx가 이 IP로 reverse proxy합니다.
WAS_IP="${WAS_IP:-192.168.2.6}"
WEB_DOMAIN="${WEB_DOMAIN:-growai.co.kr}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"

log() {
  echo "[pull-deploy-web] $(date '+%H:%M:%S') $*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    log "루트 권한이 필요합니다: sudo bash $0"
    exit 1
  fi
}

install_prerequisites() {
  log "필수 패키지를 설치합니다."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y ca-certificates curl git
}

clone_or_pull_repo() {
  if [[ -d "${REPO_DIR}/.git" ]]; then
    log "기존 저장소를 pull합니다."
    cd "${REPO_DIR}"
    git fetch origin
    git checkout "${REPO_BRANCH}"
    git reset --hard "origin/${REPO_BRANCH}"
  else
    log "저장소를 clone합니다."
    mkdir -p "${WORK_DIR}"
    rm -rf "${REPO_DIR}"
    git clone --branch "${REPO_BRANCH}" --depth 1 "${REPO_URL}" "${REPO_DIR}"
  fi
}

render_nginx_config() {
  log "Nginx 설정을 렌더링합니다."

  mkdir -p "${CONF_DIR}"

  # 왜: Nginx 템플릿의 플레이스홀더를 실제 WAS IP와 도메인으로 치환합니다.
  sed \
    -e "s|__WAS_IP__|${WAS_IP}|g" \
    -e "s|__WEB_DOMAIN__|${WEB_DOMAIN:-_}|g" \
    "${REPO_DIR}/tools/ncp/templates/nginx-web.conf.tpl" \
    > "${CONF_DIR}/lms-web.conf"

  # 배포 스크립트 복사
  cp "${REPO_DIR}/tools/ncp/templates/deploy-web.sh.tpl" "${CONF_DIR}/deploy-web.sh"
  chmod +x "${CONF_DIR}/deploy-web.sh"

  log "Nginx 설정 렌더링 완료."
}

run_deploy() {
  log "deploy-web.sh를 실행합니다."
  bash "${CONF_DIR}/deploy-web.sh" "${CONF_DIR}" "${WEB_DOMAIN}" "${LETSENCRYPT_EMAIL}"
}

verify() {
  log "배포 결과를 확인합니다."
  echo ""
  echo "=== Nginx 상태 ==="
  nginx -v 2>&1
  nginx -t 2>&1
  systemctl status nginx --no-pager | head -5
  echo ""
  echo "=== Nginx 설정 파일 ==="
  ls -la /etc/nginx/sites-available/
  ls -la /etc/nginx/sites-enabled/
  echo ""
  echo "=== WAS 연결 테스트 ==="
  # 왜: WEB→WAS 사설 네트워크 통신이 정상인지 확인합니다.
  if curl -fsS -o /dev/null -w 'API(8081): %{http_code}\n' "http://${WAS_IP}:8081/actuator/health" 2>/dev/null; then
    log "WAS API 연결 성공"
  else
    log "WAS API 연결 실패 (WAS 서버가 아직 시작되지 않았을 수 있음)"
  fi
  if curl -fsS -o /dev/null -w 'Resin(8080): %{http_code}\n' "http://${WAS_IP}:8080/" 2>/dev/null; then
    log "WAS Resin 연결 성공"
  else
    log "WAS Resin 연결 실패 (WAS 서버가 아직 시작되지 않았을 수 있음)"
  fi
}

main() {
  require_root
  log "=== NCP WEB Pull 배포 시작 ==="
  install_prerequisites
  clone_or_pull_repo
  render_nginx_config
  run_deploy
  verify
  log "=== NCP WEB Pull 배포 완료 ==="
}

main "$@"
