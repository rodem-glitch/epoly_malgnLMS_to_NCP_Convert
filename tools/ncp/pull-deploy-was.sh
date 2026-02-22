#!/usr/bin/env bash
set -euo pipefail

# 왜: NCP WAS 서버(newkl-was01)에서 직접 실행하는 Pull 방식 배포 스크립트입니다.
# GitHub에서 소스를 clone → JAR 빌드 → 번들 조립 → deploy-was.sh 실행
# 사용법: sudo bash pull-deploy-was.sh

# ─── 설정 ───────────────────────────────────────────────────────────
REPO_URL="${REPO_URL:-https://github.com/rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git}"
REPO_BRANCH="${REPO_BRANCH:-main}"
WORK_DIR="/opt/deploy-workspace"
BUNDLE_DIR="${WORK_DIR}/ncp-was-bundle"

# 왜: Cloud DB 접속 정보. 서버에서 직접 실행하므로 여기서 설정합니다.
NCP_DB_HOST="${NCP_DB_HOST:-growai-db.vpc-cdb.ntruss.com}"
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

clone_or_pull_repo() {
  if [[ -d "${WORK_DIR}/repo/.git" ]]; then
    log "기존 저장소를 pull합니다."
    cd "${WORK_DIR}/repo"
    git fetch origin
    git checkout "${REPO_BRANCH}"
    git reset --hard "origin/${REPO_BRANCH}"
  else
    log "저장소를 clone합니다."
    mkdir -p "${WORK_DIR}"
    rm -rf "${WORK_DIR}/repo"
    git clone --branch "${REPO_BRANCH}" --depth 1 "${REPO_URL}" "${WORK_DIR}/repo"
  fi
}

build_jar() {
  log "Spring Boot JAR을 빌드합니다."
  cd "${WORK_DIR}/repo/polytech-lms-api"
  chmod +x ./gradlew
  ./gradlew bootJar -x test
  log "JAR 빌드 완료."
}

assemble_bundle() {
  log "WAS 배포 번들을 조립합니다."
  local repo="${WORK_DIR}/repo"
  local app_dir="${BUNDLE_DIR}/app"
  local legacy_dir="${BUNDLE_DIR}/legacy"
  local stats_dir="${BUNDLE_DIR}/statistics_data"

  rm -rf "${BUNDLE_DIR}"
  mkdir -p "${app_dir}" "${legacy_dir}" "${stats_dir}"

  # JAR 복사
  local jar_file
  jar_file=$(find "${repo}/polytech-lms-api/build/libs" -name "*.jar" ! -name "*plain*" | head -1)
  cp "${jar_file}" "${app_dir}/polytech-lms-api.jar"
  log "JAR 복사: ${jar_file}"

  # Docker Compose 복사
  cp "${repo}/tools/ncp/templates/docker-compose-was.yml.tpl" "${BUNDLE_DIR}/docker-compose.yml"

  # 배포 스크립트 복사
  cp "${repo}/tools/ncp/templates/deploy-was.sh.tpl" "${BUNDLE_DIR}/deploy-was.sh"
  chmod +x "${BUNDLE_DIR}/deploy-was.sh"

  # 레거시 소스 복사
  cp -r "${repo}/public_html" "${legacy_dir}/public_html"
  cp -r "${repo}/src" "${legacy_dir}/src"

  # 통계 데이터 복사
  if [[ -d "${repo}/통계" ]]; then
    cp -r "${repo}/통계/"* "${stats_dir}/"
    log "통계 데이터 복사 완료."
  fi

  # 왜: Cloud DB URL에서 mysql 컨테이너 대신 NCP Cloud DB 호스트를 사용합니다.
  local app_db_url="jdbc:mysql://${NCP_DB_HOST}:3306/lms?useSSL=false&allowPublicKeyRetrieval=true"
  local app_db_url_xml
  app_db_url_xml=$(echo "${app_db_url}" | sed 's/&/\&amp;/g')

  # resin-web.xml 렌더링
  sed \
    -e "s|__APP_DB_URL__|${app_db_url_xml}|g" \
    -e "s|__DB_USER__|${DB_USER}|g" \
    -e "s|__DB_PASSWORD__|${LMS_DB_PASSWORD}|g" \
    -e "s|__LEGACY_SOURCE_DIR__|/opt/polytech-lms/legacy/src|g" \
    "${repo}/tools/ncp/templates/resin-web.xml.tpl" \
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
  log "=== NCP WAS Pull 배포 시작 ==="
  install_prerequisites
  clone_or_pull_repo
  build_jar
  assemble_bundle
  run_deploy
  verify
  log "=== NCP WAS Pull 배포 완료 ==="
}

main "$@"
