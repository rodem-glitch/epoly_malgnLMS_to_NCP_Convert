#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP WAS 서버(newkl-was01)에서 직접 실행하는 Pull 방식 배포 스크립트입니다.
# 2-repo 패턴: app-repo(앱 소스) + deploy-repo(배포 템플릿)를 분리 관리합니다.
# 사용법: sudo bash pull-deploy-was.sh

# ─── 저장소 설정 ──────────────────────────────────────────────────
# 왜: 앱 소스와 배포 스크립트를 별도 저장소로 분리하여 역할을 명확히 합니다.
APP_REPO_URL="${APP_REPO_URL:-https://github.com/sh-jang-code/polytech-lms.git}"
APP_REPO_BRANCH="${APP_REPO_BRANCH:-main}"

DEPLOY_REPO_URL="${DEPLOY_REPO_URL:-https://github.com/rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git}"
DEPLOY_REPO_BRANCH="${DEPLOY_REPO_BRANCH:-main}"

WORK_DIR="/opt/deploy-workspace"
APP_REPO_DIR="${WORK_DIR}/app-repo"
DEPLOY_REPO_DIR="${WORK_DIR}/deploy-repo"
BUNDLE_DIR="${WORK_DIR}/ncp-was-bundle"

# 왜: Cloud DB 접속 정보. 서버에서 직접 실행하므로 여기서 설정합니다.
NCP_DB_HOST="${NCP_DB_HOST:-192.168.3.6}"
DB_USER="${DB_USER:-lms}"
LMS_DB_PASSWORD="${LMS_DB_PASSWORD:-}"

# 왜: 미등록 키는 빈 값으로 두고 나중에 등록 가능합니다.
QDRANT_API_KEY="${QDRANT_API_KEY:-}"
QDRANT_COLLECTION="${QDRANT_COLLECTION:-video_summary_vectors_gemini}"
GOOGLE_API_KEY="${GOOGLE_API_KEY:-}"
GEMINI_API_KEY="${GEMINI_API_KEY:-}"

# 왜: 선택적 API 키. 없어도 기본 기능은 동작합니다.
KOSIS_CONSUMER_KEY="${KOSIS_CONSUMER_KEY:-}"
KOSIS_CONSUMER_SECRET="${KOSIS_CONSUMER_SECRET:-}"
WORK24_AUTH_KEY="${WORK24_AUTH_KEY:-}"
JOBKOREA_API_KEY="${JOBKOREA_API_KEY:-}"
JOBKOREA_OEM_CODE="${JOBKOREA_OEM_CODE:-}"
STATISTICS_AI_API_KEY="${STATISTICS_AI_API_KEY:-}"
KOLLUS_ACCESS_TOKEN="${KOLLUS_ACCESS_TOKEN:-}"
KOLLUS_SECURITY_KEY="${KOLLUS_SECURITY_KEY:-}"
KOLLUS_CHANNEL_KEY="${KOLLUS_CHANNEL_KEY:-}"
KOLLUS_CLIENT_USER_ID="${KOLLUS_CLIENT_USER_ID:-contentsummary}"

log() {
  echo "[pull-deploy-was] $(date '+%H:%M:%S') $*"
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
  apt-get install -y ca-certificates curl gnupg lsb-release jq rsync git openjdk-17-jdk-headless

  # 왜: Docker 설치
  if ! command -v docker >/dev/null 2>&1; then
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
  else
    log "Docker는 이미 설치되어 있습니다."
  fi
}

# 왜: 범용 clone/pull 함수. 두 저장소에 동일한 로직을 적용합니다.
clone_or_pull() {
  local repo_dir="$1"
  local repo_url="$2"
  local repo_branch="$3"
  local label="$4"

  if [[ -d "${repo_dir}/.git" ]]; then
    log "${label}: 기존 저장소를 pull합니다."
    cd "${repo_dir}"
    git fetch origin
    git checkout "${repo_branch}"
    git reset --hard "origin/${repo_branch}"
  else
    log "${label}: 저장소를 clone합니다."
    mkdir -p "$(dirname "${repo_dir}")"
    rm -rf "${repo_dir}"
    git clone --branch "${repo_branch}" --depth 1 "${repo_url}" "${repo_dir}"
  fi
}

sync_repos() {
  clone_or_pull "${APP_REPO_DIR}" "${APP_REPO_URL}" "${APP_REPO_BRANCH}" "app-repo"
  clone_or_pull "${DEPLOY_REPO_DIR}" "${DEPLOY_REPO_URL}" "${DEPLOY_REPO_BRANCH}" "deploy-repo"
}

build_jar() {
  log "Spring Boot JAR을 빌드합니다."
  cd "${APP_REPO_DIR}/polytech-lms-api"
  chmod +x ./gradlew
  ./gradlew bootJar -x test
  log "JAR 빌드 완료."
}

assemble_bundle() {
  log "WAS 배포 번들을 조립합니다."
  # 왜: 앱 소스는 app-repo, 배포 템플릿은 deploy-repo에서 가져옵니다.
  local app="${APP_REPO_DIR}"
  local deploy="${DEPLOY_REPO_DIR}"
  local app_dir="${BUNDLE_DIR}/app"
  local legacy_dir="${BUNDLE_DIR}/legacy"
  local stats_dir="${BUNDLE_DIR}/statistics_data"

  rm -rf "${BUNDLE_DIR}"
  mkdir -p "${app_dir}" "${legacy_dir}" "${stats_dir}"

  # JAR 복사 (app-repo에서)
  local jar_file
  jar_file=$(find "${app}/polytech-lms-api/build/libs" -name "*.jar" ! -name "*plain*" | head -1)
  cp "${jar_file}" "${app_dir}/polytech-lms-api.jar"
  log "JAR 복사: ${jar_file}"

  # Docker Compose 복사 (deploy-repo 템플릿)
  cp "${deploy}/tools/ncp/templates/docker-compose-was.yml.tpl" "${BUNDLE_DIR}/docker-compose.yml"

  # 배포 스크립트 복사 (deploy-repo 템플릿)
  cp "${deploy}/tools/ncp/templates/deploy-was.sh.tpl" "${BUNDLE_DIR}/deploy-was.sh"
  chmod +x "${BUNDLE_DIR}/deploy-was.sh"

  # 레거시 소스 복사 (app-repo에서)
  cp -r "${app}/public_html" "${legacy_dir}/public_html"
  cp -r "${app}/src" "${legacy_dir}/src"

  # 통계 데이터 복사 (app-repo에서)
  if [[ -d "${app}/통계" ]]; then
    cp -r "${app}/통계/"* "${stats_dir}/"
    log "통계 데이터 복사 완료."
  fi

  # 왜: Cloud DB URL에서 mysql 컨테이너 대신 NCP Cloud DB 호스트를 사용합니다.
  local app_db_url="jdbc:mysql://${NCP_DB_HOST}:3306/lms?useSSL=false&allowPublicKeyRetrieval=true"
  local app_db_url_xml
  # 왜: sed 치환에서 &는 "매칭된 텍스트" 특수문자이므로 \\&로 이스케이프합니다.
  app_db_url_xml=$(echo "${app_db_url}" | sed 's/&/\\&amp;/g')

  # resin-web.xml 렌더링 (deploy-repo 템플릿 + app-repo 출력 경로)
  sed \
    -e "s|__APP_DB_URL__|${app_db_url_xml}|g" \
    -e "s|__DB_USER__|${DB_USER}|g" \
    -e "s|__DB_PASSWORD__|${LMS_DB_PASSWORD}|g" \
    -e "s|__LEGACY_SOURCE_DIR__|/opt/polytech-lms/legacy/src|g" \
    "${deploy}/tools/ncp/templates/resin-web.xml.tpl" \
    > "${legacy_dir}/public_html/WEB-INF/resin-web.xml"
  log "resin-web.xml 렌더링 완료."

  # .env 생성
  {
    echo "MYSQL_USER=${DB_USER}"
    echo "MYSQL_PASSWORD=${LMS_DB_PASSWORD}"
    echo "APP_DB_URL=${app_db_url}"
    echo "QDRANT_API_KEY=${QDRANT_API_KEY}"
    echo "QDRANT_COLLECTION=${QDRANT_COLLECTION}"
    echo "GOOGLE_API_KEY=${GOOGLE_API_KEY}"
    echo "GEMINI_API_KEY=${GEMINI_API_KEY}"
    echo "KOSIS_CONSUMER_KEY=${KOSIS_CONSUMER_KEY}"
    echo "KOSIS_CONSUMER_SECRET=${KOSIS_CONSUMER_SECRET}"
    echo "WORK24_AUTH_KEY=${WORK24_AUTH_KEY}"
    echo "JOBKOREA_API_KEY=${JOBKOREA_API_KEY}"
    echo "JOBKOREA_OEM_CODE=${JOBKOREA_OEM_CODE}"
    echo "STATISTICS_AI_API_KEY=${STATISTICS_AI_API_KEY}"
    echo "KOLLUS_ACCESS_TOKEN=${KOLLUS_ACCESS_TOKEN}"
    echo "KOLLUS_SECURITY_KEY=${KOLLUS_SECURITY_KEY}"
    echo "KOLLUS_CHANNEL_KEY=${KOLLUS_CHANNEL_KEY}"
    echo "KOLLUS_CLIENT_USER_ID=${KOLLUS_CLIENT_USER_ID}"
  } > "${BUNDLE_DIR}/.env"
  log ".env 생성 완료."

  log "번들 조립 완료: ${BUNDLE_DIR}"
}

run_deploy() {
  log "deploy-was.sh를 실행합니다."
  # 왜: 첫 배포이므로 Docker 이미지를 반드시 pull합니다.
  export DEPLOY_FORCE_PULL=true
  bash "${BUNDLE_DIR}/deploy-was.sh" "${BUNDLE_DIR}"
}

verify() {
  log "배포 결과를 확인합니다."
  echo ""
  echo "=== Docker 컨테이너 상태 ==="
  docker ps -a
  echo ""
  echo "=== /opt/polytech-lms 구조 ==="
  ls -la /opt/polytech-lms/
  echo ""
  echo "=== API 헬스 체크 (최대 60초 대기) ==="
  for i in $(seq 1 12); do
    if curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/actuator/health 2>/dev/null; then
      echo ""
      log "API 헬스 체크 성공!"
      return
    fi
    echo "대기 중... ($i/12)"
    sleep 5
  done
  log "API 헬스 체크 타임아웃 (컨테이너 시작 중일 수 있음)"
}

main() {
  require_root
  log "=== NCP WAS Pull 배포 시작 (2-repo 패턴) ==="
  install_prerequisites
  sync_repos
  build_jar
  assemble_bundle
  run_deploy
  verify
  log "=== NCP WAS Pull 배포 완료 ==="
}

main "$@"
