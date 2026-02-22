# NCP 배포 가이드 (polytech-lms)

> GCP(Firebase Hosting + VM) → NCP(WEB/WAS 분리) 마이그레이션 배포 도구

## 아키텍처

```
사용자 요청
    │
    ▼
┌─────────────────────────────────────┐
│  newkl-web01 (WEB) 192.168.1.6     │
│  2Core / 4GB / ubuntu-24.04        │
│                                     │
│  Nginx (reverse proxy)              │
│  ├── /statistics/  ──┐              │
│  ├── /job/           │              │
│  ├── /student/       │              │
│  ├── /tutor/         ├──▶ WAS:8081  │
│  ├── /contentsummary/│   (API)      │
│  ├── /reco-contents/ │              │
│  ├── /global/        │              │
│  ├── /antifraud/     │              │
│  ├── /livesession/   │              │
│  ├── /actuator/    ──┘              │
│  └── / (그 외)   ────▶ WAS:8080    │
│                       (Resin/JSP)   │
└─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────┐
│  newkl-was01 (WAS) 192.168.2.6     │
│  4Core / 16GB / ubuntu-24.04       │
│                                     │
│  Docker Compose                     │
│  ├── lms-api   (Spring Boot :8081)  │
│  ├── lms-resin (Resin/JSP  :8080)  │
│  └── lms-qdrant (벡터DB    :6333)  │
│                    │                │
│                    ▼                │
│  Cloud DB for MySQL (Private)       │
│  mysql 8.0.42 / :3306               │
└─────────────────────────────────────┘
```

## GCP → NCP 주요 변경점

| GCP (기존) | NCP (전환) |
|------------|-----------|
| Firebase Hosting (정적 파일) | Nginx (newkl-web01) |
| Firebase Functions (vmproxy) | Nginx reverse proxy |
| GCP VM - 단일 서버 | WEB/WAS 분리 |
| Docker MySQL 컨테이너 | Cloud DB for MySQL |
| `127.0.0.1` 포트 바인딩 | `0.0.0.0` (WEB→WAS 접근) |
| gcloud SSH 배포 | SSH 직접 배포 |

## 파일 구조

```
tools/ncp/
├── README.md                          ← 이 문서
├── migrate-db.sh                      ← Cloud DB 초기 마이그레이션
└── templates/
    ├── nginx-web.conf.tpl             ← WEB 서버 Nginx 설정
    ├── docker-compose-was.yml.tpl     ← WAS Docker Compose (MySQL 제외)
    ├── deploy-was.sh.tpl              ← WAS 배포 스크립트
    ├── deploy-web.sh.tpl              ← WEB 배포 스크립트
    └── resin-web.xml.tpl              ← Resin DB/JNDI 설정
```

## 사전 준비

### 1. NCP 인프라 (이미 프로비저닝 완료)
- VPC: 192.168.0.0/16
- WEB: newkl-web01 (192.168.1.6)
- WAS: newkl-was01 (192.168.2.6)
- Cloud DB: mysql 8.0.42 (Private, :3306)

### 2. Cloud DB 설정 (NCP 콘솔)
- `lower_case_table_names = 1`
- `log_bin_trust_function_creators = 1`
- 문자셋: `utf8mb4`, Collation: `utf8mb4_unicode_ci`

### 3. GitHub Secrets 설정

| 시크릿 | 용도 |
|--------|------|
| `NCP_WAS_SSH_KEY` | WAS 서버 SSH 프라이빗 키 |
| `NCP_WEB_SSH_KEY` | WEB 서버 SSH 프라이빗 키 |
| `NCP_WAS_SERVER_IP` | WAS 서버 IP (192.168.2.6) |
| `NCP_WEB_SERVER_IP` | WEB 서버 IP (192.168.1.6) |
| `NCP_DB_HOST` | Cloud DB 엔드포인트 |
| `NCP_DB_PASSWORD` | Cloud DB 비밀번호 |
| `LMS_DB_PASSWORD` | 앱 DB 비밀번호 |
| `QDRANT_API_KEY` | Qdrant 벡터DB 키 |
| `GOOGLE_API_KEY` | Google API 키 |
| `GEMINI_API_KEY` | Gemini AI 키 |
| `WEB_DOMAIN` | 웹 도메인 (선택) |
| `LETSENCRYPT_EMAIL` | SSL 인증서 이메일 (선택) |

## DB 마이그레이션 (초회만)

WAS 서버(newkl-was01)에서 실행:

```bash
# 1. 덤프 파일을 WAS 서버로 전송
scp source.sql ubuntu@newkl-was01:~/

# 2. 마이그레이션 스크립트 실행
sudo bash tools/ncp/migrate-db.sh \
  db-xxxx.vpc-cdb.ntruss.com \
  lms \
  lms \
  'DB_PASSWORD' \
  ~/source.sql
```

## CI/CD 배포 (GitHub Actions)

워크플로우: `.github/workflows/deploy-lms-ncp.yml`

```
1. 체크아웃 + 시크릿 점검
2. Java 17 설정 + Gradle 빌드
3. SSH 키 설정 (WAS/WEB)
4. WAS 번들 생성 (JAR + docker-compose + .env + legacy + 통계)
5. WAS 번들 전송 + deploy-was.sh 실행
6. WEB Nginx 설정 전송 + deploy-web.sh 실행
7. 스모크 테스트 (/actuator/health + /)
```

## 수동 배포

### WAS 서버

```bash
# 번들 전송
scp -r ncp-was-bundle ubuntu@newkl-was01:~/

# 배포 실행
ssh ubuntu@newkl-was01 \
  "sudo bash ~/ncp-was-bundle/deploy-was.sh ~/ncp-was-bundle"
```

### WEB 서버

```bash
# Nginx 설정 전송
scp -r nginx-conf ubuntu@newkl-web01:~/

# 배포 실행
ssh ubuntu@newkl-web01 \
  "sudo bash ~/nginx-conf/deploy-web.sh ~/nginx-conf example.com admin@example.com"
```

## 검증

```bash
# 1. WAS API 헬스 체크
curl http://192.168.2.6:8081/actuator/health

# 2. WEB E2E (Nginx → WAS)
curl http://<WEB_PUBLIC_IP>/actuator/health
curl http://<WEB_PUBLIC_IP>/

# 3. 로그인 세션 (JSESSIONID 쿠키 전달 확인)
curl -v http://<WEB_PUBLIC_IP>/login.jsp

# 4. DB 연결 (Spring Boot → Cloud DB)
curl http://192.168.2.6:8081/actuator/health | jq .

# 5. Qdrant 벡터 검색
curl http://<WEB_PUBLIC_IP>/reco-contents/
```
