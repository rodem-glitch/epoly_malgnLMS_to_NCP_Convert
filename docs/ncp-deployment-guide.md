# NCP WEB/WAS 분리 배포 실행 가이드

> **실행 방식:** MobaXterm에서 각 Phase별 명령어 블록을 복사-붙여넣기 → 결과 확인 → 다음 Phase 진행
>
> **작성일:** 2026-02-23
> **대상 브랜치:** `feature/polytech-lms_to_NCP_Web_WAS`

---

## 서버 정보

| 구분 | 호스트명 | 사설IP | 역할 | 스펙 |
|------|---------|--------|------|------|
| WAS | newkl-was01 | 192.168.2.6 | Docker (Resin+API+Qdrant) | 4Core/16GB |
| WEB | newkl-web01 | 192.168.1.6 | Nginx reverse proxy | 2Core/4GB |
| DB | growai-db | vpc-cdb.ntruss.com:3306 | Cloud DB for MySQL 8.0 | - |

---

## 배포 순서 요약

```
Phase 0: 사전 점검 (양쪽 서버, ~5분)
Phase 1: DB 마이그레이션 (GCP 덤프 추출 → Cloud DB 적재)
Phase 2: WAS 배포 (Docker + 3 컨테이너)
Phase 3: WEB 배포 (Nginx reverse proxy)
Phase 4: E2E 검증
```

---

# Phase 0: 사전 점검

## 0-1. WAS 서버 (newkl-was01)

```bash
echo "=== [WAS] Phase 0: 사전 점검 ==="
echo ""
echo "--- 1) 인터넷 연결 ---"
curl -sI https://github.com | head -3
echo ""
echo "--- 2) Java 버전 ---"
java -version 2>&1 || echo "Java 미설치 (Phase 2에서 설치)"
echo ""
echo "--- 3) Cloud DB 포트 연결 ---"
nc -z -w5 growai-db.vpc-cdb.ntruss.com 3306 && echo "Cloud DB 3306 OK" || echo "Cloud DB 3306 FAIL"
echo ""
echo "--- 4) 디스크 여유 ---"
df -h /opt
echo ""
echo "--- 5) Docker 상태 ---"
docker --version 2>&1 || echo "Docker 미설치 (Phase 2에서 설치 예정)"
echo ""
echo "--- 6) OS 정보 ---"
cat /etc/os-release | head -4
echo ""
echo "=== Phase 0 WAS 점검 완료 ==="
```

**기대 결과:**

| 항목 | 기대값 |
|------|--------|
| 인터넷 | `HTTP/2 200` |
| Java | 미설치 OK (Phase 2에서 설치) |
| Cloud DB 3306 | `OK` |
| 디스크 /opt | 10GB 이상 여유 |
| Docker | 미설치 OK (Phase 2에서 설치) |

---

## 0-2. WEB 서버 (newkl-web01)

```bash
echo "=== [WEB] Phase 0: 사전 점검 ==="
echo ""
echo "--- 1) 인터넷 연결 ---"
curl -sI https://github.com | head -3
echo ""
echo "--- 2) Nginx 버전 ---"
nginx -v 2>&1 || echo "Nginx 미설치 (Phase 3에서 설치)"
echo ""
echo "--- 3) WAS 서버 네트워크 ---"
nc -z -w5 192.168.2.6 22 && echo "WAS SSH(22) OK" || echo "WAS SSH(22) FAIL"
nc -z -w5 192.168.2.6 8080 && echo "WAS 8080 OK" || echo "WAS 8080 FAIL (아직 배포 전이면 정상)"
nc -z -w5 192.168.2.6 8081 && echo "WAS 8081 OK" || echo "WAS 8081 FAIL (아직 배포 전이면 정상)"
echo ""
echo "--- 4) 디스크 여유 ---"
df -h /
echo ""
echo "--- 5) OS 정보 ---"
cat /etc/os-release | head -4
echo ""
echo "=== Phase 0 WEB 점검 완료 ==="
```

**기대 결과:**

| 항목 | 기대값 |
|------|--------|
| 인터넷 | `HTTP/2 200` |
| Nginx | 미설치 OK (Phase 3에서 설치) |
| WAS SSH(22) | `OK` |
| WAS 8080/8081 | FAIL (배포 전이므로 정상) |

---

# Phase 1: DB 마이그레이션

## 1-1. GCP 서버에서 덤프 추출

> GCP 서버(기존 운영)에서 실행

```bash
mysqldump -u lms -p \
  --single-transaction \
  --routines \
  --triggers \
  --events \
  lms > lms_dump_$(date +%Y%m%d).sql

# 덤프 파일 크기 확인
ls -lh lms_dump_*.sql
```

## 1-2. 덤프 파일을 WAS 서버로 전송

> MobaXterm SFTP 사용

1. MobaXterm에서 WAS 서버(newkl-was01) 접속
2. 좌측 SFTP 패널에서 `/home/ncloud/` 로 이동
3. GCP에서 추출한 `lms_dump_YYYYMMDD.sql` 파일을 드래그 앤 드롭으로 업로드

```bash
# WAS 서버에서 파일 확인
ls -lh /home/ncloud/lms_dump_*.sql
```

## 1-3. WAS에서 mysql-client 설치 + Cloud DB 연결 테스트

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 1-3: mysql-client 설치 + DB 연결 테스트 ==="

# mysql-client 설치
sudo apt-get update -y && sudo apt-get install -y mysql-client

# Cloud DB 연결 테스트 (비밀번호 입력 프롬프트가 나옵니다)
mysql -h growai-db.vpc-cdb.ntruss.com -P 3306 -u lms -p -e "SELECT 1 AS connection_test;"

echo "=== 연결 테스트 완료 ==="
```

**기대 결과:** `connection_test = 1` 출력

## 1-4. migrate-db.sh 실행

> WAS 서버(newkl-was01)에서 실행
>
> `<DB_PASSWORD>` 를 실제 비밀번호로 교체하세요

```bash
echo "=== [WAS] Phase 1-4: DB 마이그레이션 ==="

# 작업 디렉토리 생성 + 저장소 클론
sudo mkdir -p /opt/deploy-workspace
cd /opt/deploy-workspace

sudo git clone \
  --branch feature/polytech-lms_to_NCP_Web_WAS \
  --depth 1 \
  https://github.com/rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git repo

# 덤프 파일 경로 확인 (아래에서 실제 파일명으로 교체)
ls -lh /home/ncloud/lms_dump_*.sql

# migrate-db.sh 실행
# !! <DB_PASSWORD> 를 실제 비밀번호로 교체 !!
sudo bash /opt/deploy-workspace/repo/tools/ncp/migrate-db.sh \
  growai-db.vpc-cdb.ntruss.com \
  lms \
  lms \
  '<DB_PASSWORD>' \
  /home/ncloud/lms_dump_YYYYMMDD.sql

echo "=== DB 마이그레이션 완료 ==="
```

## 1-5. DB 마이그레이션 검증

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 1-5: DB 검증 ==="

# Cloud DB 접속 (비밀번호 입력 프롬프트)
mysql -h growai-db.vpc-cdb.ntruss.com -P 3306 -u lms -p <<'EOSQL'
-- 테이블 수 확인
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'lms';

-- 핵심 테이블 존재 확인
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'lms'
AND table_name IN ('member', 'course', 'site', 'board', 'lecture')
ORDER BY table_name;

-- 데이터 건수 샘플
SELECT 'member' AS tbl, COUNT(*) AS cnt FROM lms.member
UNION ALL
SELECT 'course', COUNT(*) FROM lms.course
UNION ALL
SELECT 'site', COUNT(*) FROM lms.site;
EOSQL

echo "=== DB 검증 완료 ==="
```

**기대 결과:**
- 테이블 수: GCP와 동일
- member, course, site, board, lecture 테이블 존재
- 각 테이블 데이터 건수가 GCP와 일치

---

# Phase 2: WAS 서버 배포

## 2-1. Docker 설치

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 2-1: Docker 설치 ==="

# Docker GPG 키 추가
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Docker 저장소 추가
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Docker 설치
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 현재 사용자를 docker 그룹에 추가 (재접속 후 sudo 없이 사용 가능)
sudo usermod -aG docker $USER

# 설치 확인
docker --version
docker compose version

# 동작 확인
sudo docker run --rm hello-world

echo "=== Docker 설치 완료 ==="
```

**기대 결과:** `Hello from Docker!` 메시지 출력

## 2-2. 소스 코드 최신화

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 2-2: 소스 코드 최신화 ==="

cd /opt/deploy-workspace/repo
sudo git fetch origin
sudo git reset --hard origin/feature/polytech-lms_to_NCP_Web_WAS
sudo git log --oneline -5

echo "=== 소스 최신화 완료 ==="
```

## 2-3. 기본 패키지 설치

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 2-3: 기본 패키지 설치 ==="

sudo apt-get install -y ca-certificates curl gnupg git openjdk-17-jdk rsync jq

java -version
rsync --version | head -1

echo "=== 기본 패키지 설치 완료 ==="
```

## 2-4. Spring Boot JAR 빌드

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 2-4: Spring Boot JAR 빌드 ==="

cd /opt/deploy-workspace/repo/polytech-lms-api
sudo chmod +x ./gradlew

# 빌드 (테스트 스킵)
sudo ./gradlew bootJar -x test

# 빌드 결과 확인
ls -lh build/libs/*.jar

echo "=== JAR 빌드 완료 ==="
```

**기대 결과:** `polytech-lms-api-*.jar` 파일 생성 (50~100MB)

## 2-5. WAS 번들 조립

> WAS 서버(newkl-was01)에서 실행
>
> `<DB_PASSWORD>`, `<QDRANT_API_KEY>`, `<GOOGLE_API_KEY>`, `<GEMINI_API_KEY>` 를 실제 값으로 교체하세요

```bash
echo "=== [WAS] Phase 2-5: WAS 번들 조립 ==="

BUNDLE_DIR=/opt/deploy-workspace/ncp-was-bundle
REPO_DIR=/opt/deploy-workspace/repo
NCP_DB_HOST="growai-db.vpc-cdb.ntruss.com"
DB_USER="lms"

# !! 아래 값들을 실제로 교체하세요 !!
LMS_DB_PASSWORD='<DB_PASSWORD>'
QDRANT_API_KEY='<QDRANT_API_KEY>'
GOOGLE_API_KEY='<GOOGLE_API_KEY>'
GEMINI_API_KEY='<GEMINI_API_KEY>'

# --- 번들 디렉토리 생성 ---
sudo rm -rf ${BUNDLE_DIR}
sudo mkdir -p ${BUNDLE_DIR}/app

# --- JAR 복사 ---
JAR_FILE=$(ls ${REPO_DIR}/polytech-lms-api/build/libs/*.jar | head -1)
sudo cp "${JAR_FILE}" ${BUNDLE_DIR}/app/polytech-lms-api.jar
echo "JAR: $(ls -lh ${BUNDLE_DIR}/app/polytech-lms-api.jar)"

# --- docker-compose.yml 복사 ---
sudo cp ${REPO_DIR}/tools/ncp/templates/docker-compose-was.yml.tpl \
  ${BUNDLE_DIR}/docker-compose.yml
echo "docker-compose.yml 복사 완료"

# --- deploy-was.sh 복사 ---
sudo cp ${REPO_DIR}/tools/ncp/templates/deploy-was.sh.tpl \
  ${BUNDLE_DIR}/deploy-was.sh
echo "deploy-was.sh 복사 완료"

# --- legacy/public_html 복사 ---
sudo mkdir -p ${BUNDLE_DIR}/legacy
sudo cp -r ${REPO_DIR}/public_html ${BUNDLE_DIR}/legacy/public_html
echo "legacy/public_html 복사 완료: $(du -sh ${BUNDLE_DIR}/legacy/public_html | cut -f1)"

# --- legacy/src 복사 ---
sudo cp -r ${REPO_DIR}/src ${BUNDLE_DIR}/legacy/src
echo "legacy/src 복사 완료: $(du -sh ${BUNDLE_DIR}/legacy/src | cut -f1)"

# --- statistics_data 복사 (존재하면) ---
if [ -d "${REPO_DIR}/통계" ]; then
  sudo cp -r "${REPO_DIR}/통계" ${BUNDLE_DIR}/statistics_data
  echo "statistics_data 복사 완료"
else
  sudo mkdir -p ${BUNDLE_DIR}/statistics_data
  echo "statistics_data 디렉토리 생성 (원본 없음)"
fi

# --- resin-web.xml 렌더링 ---
APP_DB_URL="jdbc:mysql://${NCP_DB_HOST}:3306/lms?useSSL=false&allowPublicKeyRetrieval=true"
LEGACY_SOURCE_DIR="/opt/polytech-lms/legacy/src"

sudo sed \
  -e "s|__APP_DB_URL__|${APP_DB_URL}|g" \
  -e "s|__DB_USER__|${DB_USER}|g" \
  -e "s|__DB_PASSWORD__|${LMS_DB_PASSWORD}|g" \
  -e "s|__LEGACY_SOURCE_DIR__|${LEGACY_SOURCE_DIR}|g" \
  ${REPO_DIR}/tools/ncp/templates/resin-web.xml.tpl \
  > /tmp/resin-web.xml
sudo cp /tmp/resin-web.xml ${BUNDLE_DIR}/legacy/public_html/WEB-INF/resin-web.xml
echo "resin-web.xml 렌더링 완료"

# --- .env 생성 ---
sudo tee ${BUNDLE_DIR}/.env > /dev/null <<ENVEOF
# NCP WAS 환경변수 — $(date +%Y-%m-%d)
APP_DB_URL=${APP_DB_URL}
SPRING_DATASOURCE_USERNAME=${DB_USER}
SPRING_DATASOURCE_PASSWORD=${LMS_DB_PASSWORD}
QDRANT_API_KEY=${QDRANT_API_KEY}
QDRANT_COLLECTION=video_summary_vectors_gemini
GOOGLE_API_KEY=${GOOGLE_API_KEY}
GEMINI_API_KEY=${GEMINI_API_KEY}
KOLLUS_CLIENT_USER_ID=contentsummary
ENVEOF
sudo chmod 600 ${BUNDLE_DIR}/.env
echo ".env 생성 완료"

# --- 번들 전체 구조 확인 ---
echo ""
echo "=== 번들 구조 ==="
find ${BUNDLE_DIR} -maxdepth 3 -not -path '*/public_html/*' -not -path '*/src/*' | sort
echo ""
echo "=== 번들 총 크기 ==="
du -sh ${BUNDLE_DIR}

echo ""
echo "=== WAS 번들 조립 완료 ==="
```

## 2-6. deploy-was.sh 실행

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 2-6: WAS 배포 실행 ==="

export DEPLOY_FORCE_PULL=true
sudo -E bash /opt/deploy-workspace/ncp-was-bundle/deploy-was.sh \
  /opt/deploy-workspace/ncp-was-bundle

echo "=== WAS 배포 실행 완료 ==="
```

## 2-7. WAS 배포 검증

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 2-7: 배포 검증 ==="

echo "--- 1) Docker 컨테이너 상태 ---"
sudo docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "--- 2) Spring Boot API 헬스체크 ---"
# 최대 60초 대기
for i in $(seq 1 12); do
  HEALTH=$(curl -sf http://127.0.0.1:8081/actuator/health 2>/dev/null)
  if [ $? -eq 0 ]; then
    echo "API Health: ${HEALTH}"
    break
  fi
  echo "대기 중... ($i/12)"
  sleep 5
done

echo ""
echo "--- 3) Resin 응답 ---"
curl -sI http://127.0.0.1:8080/ | head -5

echo ""
echo "--- 4) Qdrant 상태 ---"
curl -sf http://127.0.0.1:6333/healthz 2>/dev/null && echo "Qdrant OK" || echo "Qdrant FAIL"

echo ""
echo "--- 5) Docker 로그 (최근 10줄) ---"
echo "[lms-api]"
sudo docker logs lms-api --tail 10 2>&1
echo ""
echo "[lms-resin]"
sudo docker logs lms-resin --tail 10 2>&1

echo ""
echo "=== WAS 검증 완료 ==="
```

**기대 결과:**

| 항목 | 기대값 |
|------|--------|
| Docker 컨테이너 | lms-api, lms-resin, qdrant 모두 `Up` |
| API /actuator/health | `{"status":"UP"}` |
| Resin / | `HTTP/1.1 200` 또는 `302` |
| Qdrant | `OK` |

---

# Phase 3: WEB 서버 배포

## 3-1. 저장소 클론

> WEB 서버(newkl-web01)에서 실행

```bash
echo "=== [WEB] Phase 3-1: 저장소 클론 ==="

sudo mkdir -p /opt/deploy-workspace
cd /opt/deploy-workspace

sudo git clone \
  --branch feature/polytech-lms_to_NCP_Web_WAS \
  --depth 1 \
  https://github.com/rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git repo

echo "=== 저장소 클론 완료 ==="
```

## 3-2. Nginx 설정 렌더링

> WEB 서버(newkl-web01)에서 실행

```bash
echo "=== [WEB] Phase 3-2: Nginx 설정 렌더링 ==="

REPO_DIR=/opt/deploy-workspace/repo
CONF_DIR=/opt/deploy-workspace/nginx-conf

sudo mkdir -p ${CONF_DIR}

# nginx-web.conf 렌더링 (WAS IP, 도메인 치환)
sudo sed \
  -e 's/__WAS_IP__/192.168.2.6/g' \
  -e 's/__WEB_DOMAIN__/growai.co.kr/g' \
  ${REPO_DIR}/tools/ncp/templates/nginx-web.conf.tpl \
  > /tmp/lms-web.conf
sudo cp /tmp/lms-web.conf ${CONF_DIR}/lms-web.conf

# deploy-web.sh 복사
sudo cp ${REPO_DIR}/tools/ncp/templates/deploy-web.sh.tpl \
  ${CONF_DIR}/deploy-web.sh

# 설정 내용 확인 (WAS IP가 제대로 치환되었는지)
echo "--- upstream 설정 확인 ---"
grep -A1 'upstream' ${CONF_DIR}/lms-web.conf
echo ""
echo "--- server_name 확인 ---"
grep 'server_name' ${CONF_DIR}/lms-web.conf

echo ""
echo "=== Nginx 설정 렌더링 완료 ==="
```

## 3-3. deploy-web.sh 실행

> WEB 서버(newkl-web01)에서 실행

```bash
echo "=== [WEB] Phase 3-3: WEB 배포 실행 ==="

# 도메인만 전달, SSL은 DNS 연결 후 별도 진행
sudo bash /opt/deploy-workspace/nginx-conf/deploy-web.sh \
  /opt/deploy-workspace/nginx-conf \
  growai.co.kr \
  ""

echo "=== WEB 배포 실행 완료 ==="
```

## 3-4. WEB 배포 검증

> WEB 서버(newkl-web01)에서 실행

```bash
echo "=== [WEB] Phase 3-4: 배포 검증 ==="

echo "--- 1) Nginx 설정 검증 ---"
sudo nginx -t

echo ""
echo "--- 2) Nginx 상태 ---"
sudo systemctl status nginx --no-pager | head -10

echo ""
echo "--- 3) API 프록시 테스트 (Nginx → WAS API) ---"
curl -sf http://127.0.0.1/actuator/health && echo "" || echo "API 프록시 FAIL"

echo ""
echo "--- 4) 메인 페이지 테스트 (Nginx → WAS Resin) ---"
curl -sI http://127.0.0.1/ | head -5

echo ""
echo "--- 5) Nginx 설정 파일 확인 ---"
ls -la /etc/nginx/sites-enabled/

echo ""
echo "=== WEB 검증 완료 ==="
```

**기대 결과:**

| 항목 | 기대값 |
|------|--------|
| nginx -t | `syntax is ok`, `test is successful` |
| /actuator/health | `{"status":"UP"}` |
| / | `HTTP/1.1 200` 또는 `302` |

---

# Phase 4: E2E 검증

## 4-1. WEB 서버에서 종합 테스트

> WEB 서버(newkl-web01)에서 실행

```bash
echo "=== Phase 4: E2E 검증 ==="
echo ""

PASS=0
FAIL=0

# 1) API 헬스체크
echo "--- 1) API Health ---"
RESULT=$(curl -sf http://127.0.0.1/actuator/health 2>/dev/null)
if echo "$RESULT" | grep -q '"status":"UP"'; then
  echo "PASS: API Health = UP"
  PASS=$((PASS+1))
else
  echo "FAIL: API Health = ${RESULT:-timeout}"
  FAIL=$((FAIL+1))
fi

# 2) 메인 페이지
echo ""
echo "--- 2) 메인 페이지 ---"
HTTP_CODE=$(curl -so /dev/null -w '%{http_code}' http://127.0.0.1/)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
  echo "PASS: 메인 페이지 HTTP ${HTTP_CODE}"
  PASS=$((PASS+1))
else
  echo "FAIL: 메인 페이지 HTTP ${HTTP_CODE}"
  FAIL=$((FAIL+1))
fi

# 3) 통계 페이지
echo ""
echo "--- 3) 통계 페이지 ---"
HTTP_CODE=$(curl -so /dev/null -w '%{http_code}' http://127.0.0.1/statistics/)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
  echo "PASS: 통계 페이지 HTTP ${HTTP_CODE}"
  PASS=$((PASS+1))
else
  echo "FAIL: 통계 페이지 HTTP ${HTTP_CODE}"
  FAIL=$((FAIL+1))
fi

# 4) 교수자 SPA
echo ""
echo "--- 4) 교수자 SPA ---"
HTTP_CODE=$(curl -so /dev/null -w '%{http_code}' http://127.0.0.1/tutor_lms/app/index.html)
if [ "$HTTP_CODE" = "200" ]; then
  echo "PASS: 교수자 SPA HTTP ${HTTP_CODE}"
  PASS=$((PASS+1))
else
  echo "FAIL: 교수자 SPA HTTP ${HTTP_CODE}"
  FAIL=$((FAIL+1))
fi

# 5) DB 연결 상태 (actuator에서 확인)
echo ""
echo "--- 5) DB 연결 ---"
DB_STATUS=$(curl -sf http://127.0.0.1/actuator/health 2>/dev/null | grep -o '"db":{[^}]*}' || echo "")
if echo "$DB_STATUS" | grep -q '"status":"UP"'; then
  echo "PASS: DB 연결 UP"
  PASS=$((PASS+1))
else
  echo "WARN: DB 상태 확인 불가 (actuator 상세정보 비활성화일 수 있음)"
  echo "  raw: ${DB_STATUS:-없음}"
fi

echo ""
echo "======================================="
echo "  E2E 결과: PASS=${PASS} / FAIL=${FAIL}"
echo "======================================="
```

## 4-2. WAS 서버에서 Docker 상태 확인

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== Phase 4: WAS Docker 상태 ==="
echo ""

echo "--- Docker 컨테이너 ---"
sudo docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "--- 디스크 사용량 ---"
df -h /opt
sudo du -sh /opt/polytech-lms/ 2>/dev/null || echo "/opt/polytech-lms 없음"

echo ""
echo "--- Docker 이미지 ---"
sudo docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

echo ""
echo "=== WAS Docker 상태 확인 완료 ==="
```

## 4-3. 로드밸런서 테스트 (외부 접속)

> 로컬 PC 브라우저에서 실행

```
http://27.96.144.249/
```

**기대 결과:** LMS 메인 화면 표시

---

# Phase 5: NCP 서버 Git 구성 (2-repo 패턴)

> 배포 완료 후, CI/CD 및 pull-deploy 스크립트가 정상 동작하도록 Git 환경을 구성합니다.

## 저장소 역할

| 저장소 | 역할 | 서버 디렉토리 |
|--------|------|--------------|
| `sh-jang-code/polytech-lms` (앱 소스) | 소스 코드 + CI/CD 워크플로우 | WAS: `/opt/deploy-workspace/app-repo` |
| `rodem-glitch/epoly_malgnLMS_to_NCP_Convert` (배포 도구) | 배포 스크립트·템플릿 | WAS/WEB: `/opt/deploy-workspace/deploy-repo` |

## 5-1. WAS 서버 Git 설정 (newkl-was01)

> WAS 서버(newkl-was01)에서 실행

```bash
echo "=== [WAS] Phase 5-1: Git 구성 ==="

# Git credential 설정 (PAT 방식 — SSH 키는 NCP에서 실패 이력 있음)
sudo git config --global credential.helper store
sudo git config --global user.name "ncp-was-deploy"
sudo git config --global user.email "was-deploy@polytech-lms.ncp"

# 기존 repo 디렉토리를 deploy-repo로 이동 (이미 있으면)
if [ -d /opt/deploy-workspace/repo ]; then
  sudo mv /opt/deploy-workspace/repo /opt/deploy-workspace/deploy-repo
  echo "repo → deploy-repo 이동 완료"
fi

# deploy-repo 최신화
cd /opt/deploy-workspace/deploy-repo
sudo git checkout main
sudo git pull origin main

# app-repo 클론 (앱 소스 — 최초 1회)
# !! 최초 실행 시 GitHub PAT(ghp_...) 입력 프롬프트가 나옵니다 !!
if [ ! -d /opt/deploy-workspace/app-repo ]; then
  sudo git clone --branch main --depth 1 \
    https://github.com/sh-jang-code/polytech-lms.git \
    /opt/deploy-workspace/app-repo
  echo "app-repo 클론 완료"
else
  cd /opt/deploy-workspace/app-repo
  sudo git pull origin main
  echo "app-repo 최신화 완료"
fi

# credential 파일 권한 설정
sudo chmod 600 ~/.git-credentials 2>/dev/null || true

# 디렉토리 구조 확인
echo ""
echo "=== /opt/deploy-workspace 구조 ==="
ls -la /opt/deploy-workspace/

echo ""
echo "=== Phase 5-1 완료 ==="
```

**기대 결과:**

```
/opt/deploy-workspace/
├── app-repo/          ← sh-jang-code/polytech-lms (main 추적)
├── deploy-repo/       ← rodem-glitch/epoly_malgnLMS_to_NCP_Convert (main 추적)
└── ncp-was-bundle/    ← 이전 배포 번들 (있으면)
```

## 5-2. WEB 서버 Git 설정 (newkl-web01)

> WEB 서버(newkl-web01)에서 실행

```bash
echo "=== [WEB] Phase 5-2: Git 구성 ==="

# Git credential 설정
sudo git config --global credential.helper store
sudo git config --global user.name "ncp-web-deploy"
sudo git config --global user.email "web-deploy@polytech-lms.ncp"

# 기존 repo 디렉토리를 deploy-repo로 이동
if [ -d /opt/deploy-workspace/repo ]; then
  sudo mv /opt/deploy-workspace/repo /opt/deploy-workspace/deploy-repo
  echo "repo → deploy-repo 이동 완료"
fi

# deploy-repo 최신화
cd /opt/deploy-workspace/deploy-repo
sudo git checkout main
sudo git pull origin main

# credential 파일 권한 설정
sudo chmod 600 ~/.git-credentials 2>/dev/null || true

# WEB에는 app-repo 불필요 (Nginx 설정 템플릿만 사용)

echo ""
echo "=== /opt/deploy-workspace 구조 ==="
ls -la /opt/deploy-workspace/

echo ""
echo "=== Phase 5-2 완료 ==="
```

**기대 결과:**

```
/opt/deploy-workspace/
├── deploy-repo/       ← rodem-glitch/epoly_malgnLMS_to_NCP_Convert (main 추적)
└── nginx-conf/        ← 이전 배포 설정 (있으면)
```

## 5-3. 연결 테스트

> 각 서버에서 실행

```bash
echo "=== Git 연결 테스트 ==="

# WAS에서:
cd /opt/deploy-workspace/app-repo && sudo git pull origin main && echo "app-repo OK"
cd /opt/deploy-workspace/deploy-repo && sudo git pull origin main && echo "deploy-repo OK"

# WEB에서:
cd /opt/deploy-workspace/deploy-repo && sudo git pull origin main && echo "deploy-repo OK"

echo "=== 연결 테스트 완료 ==="
```

## 5-4. Pull 방식 배포 테스트

> WAS 서버에서 실행 (Phase 5-1, 5-3 완료 후)

```bash
# pull-deploy-was.sh가 2-repo 패턴을 사용하는지 확인
sudo bash /opt/deploy-workspace/deploy-repo/tools/ncp/pull-deploy-was.sh
```

> WEB 서버에서 실행 (Phase 5-2, 5-3 완료 후)

```bash
sudo bash /opt/deploy-workspace/deploy-repo/tools/ncp/pull-deploy-web.sh
```

## GitHub PAT 설정 가이드

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. **Generate new token (classic)** 클릭
3. Note: `ncp-deploy-bot`
4. Scopes: `repo` (read-only 추천)
5. **Generate token** → `ghp_...` 복사
6. NCP 서버에서 `git clone` 시 비밀번호 프롬프트에 PAT 입력
7. `credential.helper store`가 `~/.git-credentials`에 자동 저장

**보안 권장사항:**
- PAT 만료일 설정 (90일 추천)
- 향후 전용 GitHub 머신 계정(deploy-bot) 생성 권장
- `~/.git-credentials` 파일 권한: `chmod 600`

---

# 트러블슈팅

## Docker 컨테이너가 올라오지 않을 때

```bash
# 컨테이너 로그 확인
sudo docker logs lms-api --tail 50
sudo docker logs lms-resin --tail 50

# docker-compose 직접 실행 (foreground로 오류 확인)
cd /opt/polytech-lms
sudo docker compose up
```

## API가 DB에 연결하지 못할 때

```bash
# WAS에서 Cloud DB 직접 연결 테스트
mysql -h growai-db.vpc-cdb.ntruss.com -P 3306 -u lms -p -e "SELECT 1;"

# .env 파일의 DB URL 확인
sudo cat /opt/polytech-lms/.env | grep DB
```

## Nginx가 WAS에 연결하지 못할 때

```bash
# WEB에서 WAS 포트 확인
nc -z -w5 192.168.2.6 8080 && echo "8080 OK" || echo "8080 FAIL"
nc -z -w5 192.168.2.6 8081 && echo "8081 OK" || echo "8081 FAIL"

# Nginx 에러 로그 확인
sudo tail -20 /var/log/nginx/error.log
```

## Resin JSP 컴파일 오류

```bash
# Resin 컨테이너 내부 로그
sudo docker logs lms-resin --tail 100

# WEB-INF 디렉토리 권한 확인
sudo docker exec lms-resin ls -la /var/resin/webapps/ROOT/WEB-INF/
```

---

# 후속 작업 (배포 완료 후)

| 순서 | 작업 | 설명 |
|------|------|------|
| 1 | SSL 인증서 | DNS 연결 후 `sudo certbot --nginx -d growai.co.kr` |
| 2 | VPN 라우팅 | NCP 인프라팀에 172.28.x Route Table 추가 요청 (Oracle DB 연계) |
| 3 | CI/CD 활성화 | main 브랜치 머지 후 GitHub Actions 자동 배포 설정 |
| 4 | 모니터링 | Cloud Insight 또는 docker healthcheck 설정 |

---

# 롤백 절차

## WAS 롤백

```bash
# 컨테이너 중지
cd /opt/polytech-lms
sudo docker compose down

# 이전 번들로 복원 (백업이 있는 경우)
# sudo cp -r /opt/polytech-lms.bak/* /opt/polytech-lms/
# sudo docker compose up -d
```

## WEB 롤백

```bash
# Nginx 설정 제거
sudo rm /etc/nginx/sites-enabled/lms-web.conf
sudo ln -sf /etc/nginx/sites-available/default /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl restart nginx
```
