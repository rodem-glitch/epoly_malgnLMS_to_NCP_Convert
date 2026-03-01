# 에폴리 LMS (MalgnLMS) 운영현황 및 운영가이드

> **최종 갱신**: 2026-03-02
> **대상 저장소**: `rodem-glitch/epoly_malgnLMS_to_NCP_Convert`
> **현재 브랜치 전략**: 1-repo 패턴 (feature/* → dev → stag → prod)

---

## 목차

1. [시스템 개요](#1-시스템-개요)
2. [전체 아키텍처 (GCP vs NCP)](#2-전체-아키텍처-gcp-vs-ncp)
3. [저장소 구조 및 Git 전략](#3-저장소-구조-및-git-전략)
4. [CI/CD 파이프라인](#4-cicd-파이프라인)
5. [인프라 상세 — GCP (기존)](#5-인프라-상세--gcp-기존)
6. [인프라 상세 — NCP (신규/운영)](#6-인프라-상세--ncp-신규운영)
7. [Database 구성](#7-database-구성)
8. [Vector DB (Qdrant)](#8-vector-db-qdrant)
9. [외부 API 연동](#9-외부-api-연동)
10. [엔드포인트 라우팅 맵](#10-엔드포인트-라우팅-맵)
11. [환경변수 전체 목록](#11-환경변수-전체-목록)
12. [빌드 및 배포 명령어](#12-빌드-및-배포-명령어)
13. [모니터링 및 헬스체크](#13-모니터링-및-헬스체크)
14. [학사 연동 (PolySync)](#14-학사-연동-polysync)
15. [롤백 및 백업](#15-롤백-및-백업)
16. [트러블슈팅 가이드](#16-트러블슈팅-가이드)
17. [Appendix A: 디렉토리 트리 상세](#appendix-a-디렉토리-트리-상세)
18. [Appendix B: 설정 파일 경로 색인](#appendix-b-설정-파일-경로-색인)
19. [Appendix C: GitHub Secrets 전체 목록](#appendix-c-github-secrets-전체-목록)
20. [Appendix D: Docker Compose 서비스 명세](#appendix-d-docker-compose-서비스-명세)
21. [Appendix E: Nginx 라우팅 룰 전문](#appendix-e-nginx-라우팅-룰-전문)
22. [Appendix F: Spring Boot API 컨트롤러 전체 목록](#appendix-f-spring-boot-api-컨트롤러-전체-목록)

---

## 1. 시스템 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | 에폴리 학습관리시스템 (한국폴리텍대학 LMS) |
| **GCP URL** | `https://epoly-kopo.web.app` |
| **NCP URL** | `http://{WEB_DOMAIN}` (27.96.144.249:80) |
| **기술 스택** | Spring Boot 3.2 + Resin 4.0 (JSP/DAO Legacy) + React 18 (Vite) |
| **DB** | MySQL 8.0 (Cloud DB) + Oracle (학사, 읽기전용) |
| **AI/ML** | Google Gemini + Qdrant Vector DB |
| **현재 상태** | GCP → NCP 이관 완료 (2026-02 기준), GCP는 프록시 유지 |

### 기술 스택 요약

```
┌─ Frontend ──────────────────────────────────────────────┐
│  React 18 + TypeScript + Vite + TailwindCSS + Radix UI  │
│  빌드 산출물 → public_html/tutor_lms/app/               │
└─────────────────────────────────────────────────────────┘
┌─ Backend (신규 API) ────────────────────────────────────┐
│  Spring Boot 3.2.5 / Java 17 / Gradle 8.7              │
│  Spring Data JPA + Spring AI (Qdrant + GenAI)           │
│  포트: 8081                                              │
└─────────────────────────────────────────────────────────┘
┌─ Backend (레거시) ──────────────────────────────────────┐
│  Resin WAS 4.0 / Java 8 / JSP + DAO 패턴               │
│  JNDI DataSource + 동적 컴파일 (src → WEB-INF/classes)  │
│  포트: 8080                                              │
└─────────────────────────────────────────────────────────┘
┌─ Data Layer ────────────────────────────────────────────┐
│  MySQL 8.0 (NCP Cloud DB, 186 테이블)                   │
│  Oracle 11g (학사 연동, VPN, 읽기전용 8개 뷰)            │
│  Qdrant v1.15.3 (벡터 DB, 768차원, gRPC 6334)          │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 전체 아키텍처 (GCP vs NCP)

### 2-1. GCP 구성 (기존 — 프록시 유지)

```
사용자
  │
  ▼
Firebase Hosting (epoly-kopo.web.app)
  │  rewrites: /** → vmproxy 함수
  ▼
Cloud Functions (vmproxy, asia-northeast3)
  │  역방향 프록시 (쿠키 브릿지: __session ↔ JSESSIONID)
  ▼
GCE VM (34.64.207.10)
  ├── Docker MySQL (로컬)
  ├── Docker Qdrant (벡터DB)
  ├── Docker Spring Boot API (:8081)
  ├── Docker Resin JSP (:8080)
  └── Nginx (역방향 프록시)
```

**Firebase 프록시 동작 원리**:
- Firebase Hosting은 모든 요청(`**`)을 `vmproxy` Cloud Function으로 라우팅
- `vmproxy`는 GCE VM(34.64.207.10)으로 HTTP 프록시
- Firebase는 `__session` 쿠키만 허용하므로, JSESSIONID/MLMS 쿠키를 `__session`에 Base64URL 번들로 묶어 브릿지
- 응답의 `charset=us-ascii`를 `charset=utf-8`로 변환 (한글 깨짐 방지)

### 2-2. NCP 구성 (신규 — 운영)

```
사용자
  │
  ▼
Load Balancer (27.96.144.249:80)
  │
  ▼
┌──────────────────────────────────────────────────┐
│  newkl-web01 (WEB) — 192.168.1.6               │
│  2Core / 4GB RAM / Ubuntu 24.04                  │
│  ┌────────────────────────────────────────────┐  │
│  │  Nginx                                     │  │
│  │  • 역방향 프록시 (API/JSP 분기)            │  │
│  │  • Rate Limiting (login 5r/s, api 30r/s)  │  │
│  │  • 보안 헤더 + gzip 압축                   │  │
│  │  • 정적 캐시 (common 7d, assets 30d)      │  │
│  │  • (선택) Let's Encrypt SSL               │  │
│  └────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────┘
                 │  사설망 (192.168.x.x)
                 ▼
┌──────────────────────────────────────────────────┐
│  newkl-was01 (WAS) — 192.168.2.6               │
│  4Core / 16GB RAM / Ubuntu 24.04                 │
│  ┌────────────────────────────────────────────┐  │
│  │  Docker Compose                            │  │
│  │  ┌──────────────┐  ┌──────────────────┐   │  │
│  │  │ lms-qdrant   │  │ lms-api          │   │  │
│  │  │ :6333/:6334  │  │ Spring Boot      │   │  │
│  │  │ 2GB / 1CPU   │  │ :8081            │   │  │
│  │  └──────────────┘  │ 6GB / 2CPU       │   │  │
│  │                     └──────────────────┘   │  │
│  │  ┌──────────────────────────────────────┐  │  │
│  │  │ lms-resin (Legacy JSP/DAO)           │  │  │
│  │  │ :8080 / 4GB / 2CPU                   │  │  │
│  │  │ Java 8 동적컴파일 + JNDI DataSource  │  │  │
│  │  └──────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────┘
                 │  사설망
                 ▼
┌──────────────────────────────────────────────────┐
│  NCP Cloud DB for MySQL                          │
│  growai-db.vpc-cdb.ntruss.com:3306               │
│  MySQL 8.0.42 / 186 테이블 / 10GB SSD           │
│  30일 자동 백업 / utf8mb4_unicode_ci             │
└──────────────────────────────────────────────────┘
                 │  VPN 터널 (IPSec)
                 ▼
┌──────────────────────────────────────────────────┐
│  폴리텍 학사 Oracle DB (읽기전용)                 │
│  172.28.5.34:61521/KOPO                          │
│  8개 뷰테이블 (학생/과목/교수/강의계획 등)        │
└──────────────────────────────────────────────────┘
```

### 2-3. GCP ↔ NCP 비교표

| 항목 | GCP | NCP |
|------|-----|-----|
| **진입점** | `epoly-kopo.web.app` | `27.96.144.249` (도메인 예정) |
| **프록시** | Firebase Functions (vmproxy) | Nginx (WEB 서버) |
| **WAS** | 단일 VM (34.64.207.10) | WEB/WAS 분리 |
| **DB** | Docker MySQL (VM 내장) | Cloud DB for MySQL (관리형) |
| **Oracle 연동** | 미적용 | VPN 터널 → 학사 Oracle |
| **Vector DB** | Qdrant (VM Docker) | Qdrant (WAS Docker) |
| **배포** | GH Actions → VM SSH | GH Actions → WAS/WEB SSH |
| **SSL** | Firebase 자동 | Let's Encrypt (선택) |
| **상태** | 프록시 유지 (→ NCP VM 전환 예정) | **운영** |

---

## 3. 저장소 구조 및 Git 전략

### 3-1. 저장소 정보

| 항목 | 값 |
|------|-----|
| **GitHub** | `git@github.com:rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git` |
| **패턴** | 1-repo (앱 소스 + 배포 스크립트 + 문서 통합) |
| **기본 브랜치** | `main` |

### 3-2. 브랜치 전략

```
feature/* ──→ dev ──→ stag ──→ prod
              │               │
              CI 검증          자동 배포 (NCP)
              (빌드만)         (WAS + WEB)
```

| 브랜치 | 목적 | CI/CD | 보호 규칙 |
|--------|------|-------|-----------|
| `prod` | 운영 배포 | push 시 NCP 자동 배포 | PR 필수 (권장) |
| `stag` | 스테이징 검증 | CI 빌드 검증 | PR 권장 |
| `dev` | 개발 통합 | CI 빌드 검증 | 자유 push |
| `feature/*` | 기능 개발 | 로컬 빌드만 | — |
| `main` | 기본 브랜치 (GCP 배포용) | — | — |

### 3-3. 주요 feature 브랜치 이력

| 브랜치 | 설명 |
|--------|------|
| `feature/opti` | NCP 최적화 + 학사 연동 |
| `feature/polytech-lms_to_NCP_Web_WAS` | NCP WEB/WAS 이관 |
| `feature/ncp-migration` | NCP 마이그레이션 메인 |
| `feature/backend` | Spring Boot API 개발 |
| `feature/frontend` | React UI 개발 |
| `feature/lms-agent-2nd-tuning` | AI 추천 2차 튜닝 |

---

## 4. CI/CD 파이프라인

### 4-1. NCP 배포 (`deploy-lms-ncp.yml`)

**파일**: `.github/workflows/deploy-lms-ncp.yml`
**트리거**: `prod` 브랜치 push 또는 수동(workflow_dispatch)
**타임아웃**: 30분

```
┌─ GitHub Actions Runner ────────────────────────────────────────┐
│                                                                │
│  1. Checkout                                                   │
│  2. Validate Secrets (11개 필수)                               │
│  3. Setup Java 17 (Temurin)                                    │
│  4. Gradle bootJar → polytech-lms-api.jar (~131MB)            │
│  5. Setup SSH Keys (WAS/WEB)                                   │
│                                                                │
│  ┌─ WAS 배포 ───────────────────────────────────┐             │
│  │  6. WAS 번들 생성 (tar.gz):                   │             │
│  │     • JAR + docker-compose + .env             │             │
│  │     • legacy (public_html + src)              │             │
│  │     • 통계 데이터 Excel                        │             │
│  │     • resin-web.xml (Cloud DB 주입)           │             │
│  │     • 학사 DDL + poly_sync 스크립트            │             │
│  │  7. SCP → WAS 서버                             │             │
│  │  8. deploy-was.sh 실행 (300s 타임아웃)        │             │
│  └────────────────────────────────────────────────┘             │
│                                                                │
│  ┌─ WEB 배포 ───────────────────────────────────┐             │
│  │  9. Nginx 설정 렌더링 (WAS IP 주입)           │             │
│  │  10. SCP → WEB 서버                            │             │
│  │  11. deploy-web.sh 실행 (60s 타임아웃)        │             │
│  └────────────────────────────────────────────────┘             │
│                                                                │
│  12. Smoke Test: curl /actuator/health (60s 대기)              │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 4-2. CI 검증 (`ci-validate.yml`)

**파일**: `.github/workflows/ci-validate.yml`
**트리거**: `dev`/`stag` push, `dev`/`stag`/`prod` PR

```
1. Checkout
2. Setup Java 17
3. Gradle bootJar -x test (빌드 검증만, 테스트 제외)
```

### 4-3. GCP 배포 (기존)

**Firebase Hosting + Functions 배포**:
```bash
cd tools/gcp/firebase-proxy-deploy
firebase deploy --project gen-lang-client-0343478566 --only functions,hosting
```

**GCE VM 배포** (원클릭):
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gcp/one-click-setup.ps1 `
  -ProjectId gen-lang-client-0343478566 `
  -FirebaseSite epoly-kopo `
  ...
```

### 4-4. Pull 방식 배포 (서버 수동)

GitHub Actions가 불가능하거나 긴급 수동 배포 시 서버에서 직접 실행:

```bash
# WAS 서버에서
sudo REPO_BRANCH=prod bash /opt/deploy-workspace/repo/tools/ncp/pull-deploy-was.sh

# WEB 서버에서
sudo REPO_BRANCH=prod bash /opt/deploy-workspace/repo/tools/ncp/pull-deploy-web.sh
```

---

## 5. 인프라 상세 — GCP (기존)

### 5-1. Firebase Hosting

| 항목 | 값 |
|------|-----|
| **프로젝트** | `gen-lang-client-0343478566` |
| **사이트** | `epoly-kopo` |
| **URL** | `https://epoly-kopo.web.app` |
| **리전** | `asia-northeast3` (서울) |
| **라우팅** | `**` → `vmproxy` Cloud Function |

**설정 파일**: `tools/gcp/firebase-proxy-deploy/firebase.json`
```json
{
  "hosting": {
    "site": "epoly-kopo",
    "rewrites": [{ "source": "**", "function": { "functionId": "vmproxy", "region": "asia-northeast3" } }]
  }
}
```

### 5-2. Cloud Functions (vmproxy)

| 항목 | 값 |
|------|-----|
| **함수명** | `vmproxy` |
| **리전** | `asia-northeast3` |
| **타임아웃** | 540초 |
| **메모리** | 512MiB |
| **타겟** | `http://34.64.207.10` (GCE VM) |

**핵심 기능**:
- HTTP 역방향 프록시 (모든 메서드/경로)
- Firebase `__session` ↔ JSESSIONID/MLMS 쿠키 브릿지 (Base64URL 인코딩)
- `charset=us-ascii` → `charset=utf-8` 자동 변환
- `Location` 헤더의 VM IP → `epoly-kopo.web.app` 치환
- 업스트림 타임아웃: 120초

**소스**: `tools/gcp/firebase-proxy-deploy/functions/index.js`

### 5-3. GCE VM

| 항목 | 값 |
|------|-----|
| **외부 IP** | `34.64.207.10` |
| **구성** | Docker Compose (MySQL + Qdrant + API + Resin) |
| **DB** | Docker MySQL (VM 내부, `lower_case_table_names=1`) |

### 5-4. GCP 배포 파이프라인

```
main 브랜치 push
      │
      ▼
GitHub Actions (deploy-lms-gcp.yml)
      │
      ├─ one-click-setup.ps1 실행
      │  └─ VM SSH → Docker 스택 배포
      │
      └─ firebase deploy
         └─ vmproxy 함수 + Hosting 배포
```

**필수 GCP Secrets**: `GCP_SA_KEY`, `GCP_PROJECT_ID`, `GCP_VM_SSH_USER`, `FIREBASE_TOKEN`, `LMS_DB_PASSWORD`, `LMS_DB_ROOT_PASSWORD`, `QDRANT_API_KEY`, `GOOGLE_API_KEY`, `GEMINI_API_KEY`

---

## 6. 인프라 상세 — NCP (신규/운영)

### 6-1. 서버 구성

| 서버 | 호스트 | 스펙 | OS | 역할 |
|------|--------|------|-----|------|
| WEB | 192.168.1.6 (공인 27.96.144.249) | 2Core / 4GB | Ubuntu 24.04 | Nginx 역방향 프록시 |
| WAS | 192.168.2.6 | 4Core / 16GB | Ubuntu 24.04 | Docker (API + Resin + Qdrant) |
| DB | growai-db.vpc-cdb.ntruss.com | 관리형 | MySQL 8.0.42 | Cloud DB |

### 6-2. WAS 메모리 배분 (16GB 기준)

| 컨테이너 | 메모리 | CPU | JVM 옵션 |
|----------|--------|-----|----------|
| lms-qdrant | 2GB | 1.0 | — |
| lms-api | 6GB (limit) | 2.0 | `-Xms2g -Xmx4g -XX:+UseG1GC` |
| lms-resin | 4GB (limit) | 2.0 | `-Xmx2g -Xms1g` (배포 시 자동 설정) |
| OS 예약 | ~4GB | — | — |

### 6-3. WAS deploy-was.sh 상세 동작

```
1. Docker 설치/확인
2. 이전 버전 백업 (/opt/polytech-lms-backups/{timestamp}/)
3. rsync 동기화 (exclude: data/file, data/log, WEB-INF/work)
4. 레거시 디렉토리 권한 설정 (WEB-INF/work, classes, data → 777)
5. docker compose up -d
6. Resin JVM 최적화 (256MB → 2GB)
7. Kollus TLS 인증서 고정 (Java keystore import)
8. 학사 DDL 적용 (CREATE TABLE IF NOT EXISTS)
9. 학사 동기화 cron 등록
10. 7일 초과 백업 자동 삭제
```

### 6-4. WEB deploy-web.sh 상세 동작

```
1. Nginx + certbot 설치
2. Nginx 설정 → /etc/nginx/sites-available/lms-web.conf
3. 심링크 → sites-enabled, 기본 사이트 제거
4. nginx -t → systemctl reload nginx
5. (선택) DNS 확인 후 Let's Encrypt SSL 발급
```

---

## 7. Database 구성

### 7-1. MySQL (Primary — NCP Cloud DB)

| 항목 | 값 |
|------|-----|
| **호스트** | `growai-db.vpc-cdb.ntruss.com:3306` |
| **데이터베이스** | `lms` |
| **사용자** | `lms` |
| **문자셋** | `utf8mb4` / `utf8mb4_unicode_ci` |
| **테이블 수** | 186개 |
| **대소문자** | `lower_case_table_names=0` (Linux, 대소문자 구분) |
| **테이블명** | 모두 UPPERCASE (`LM_POLY_COURSE`, `TB_SITE` 등) |
| **자동백업** | 30일 보관 (NCP Cloud DB 기본) |
| **드라이버** | `com.mysql.cj.jdbc.Driver` |

**커넥션 풀 (Spring Boot — HikariCP)**:
```yaml
spring.datasource:
  url: jdbc:mysql://${NCP_DB_HOST}:3306/lms?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8mb4
  hikari:
    maximum-pool-size: 10 (기본값)
```

**커넥션 풀 (Resin — JNDI)**:
```xml
<jndi-name>jdbc/malgn</jndi-name>  <!-- 또는 jdbc/lms -->
<max-connections>16</max-connections>
<max-idle-time>30s</max-idle-time>
<prepared-statement-cache-size>8</prepared-statement-cache-size>
```

**주요 설정 주의사항**:
- `sql_require_primary_key=OFF` (NCP Cloud DB 기본: ON → 레거시 호환 위해 OFF)
- `PhysicalNamingStrategyStandardImpl` 사용 (Hibernate 자동 snake_case 변환 방지)
- DDL auto: `none` (스키마 자동 생성 비활성화)

### 7-2. MySQL (Local — Docker)

```yaml
# docker-compose.local.yml
mysql:
  image: mysql:8.0
  ports: 127.0.0.1:3306:3306
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-localroot}
    MYSQL_DATABASE: lms
    MYSQL_USER: ${MYSQL_USER:-lms}
    MYSQL_PASSWORD: ${MYSQL_PASSWORD:-localpass}
```

### 7-3. Oracle (학사 연동 — 읽기전용)

| 항목 | 값 |
|------|-----|
| **호스트** | `172.28.5.34:61521` (VPN 터널 경유) |
| **SID** | `KOPO` |
| **사용자** | `KOPO_LMS` |
| **드라이버** | `com.oracle.database.jdbc:ojdbc11:23.5.0.24.07` |
| **접근 방식** | NCP WAS → IPSec VPN → 폴리텍 IDC |

**커넥션 풀 (HikariCP)**:
```yaml
haksa.oracle:
  pool-size: 5
  pool-min-idle: 2
  connection-timeout: 30000
  max-lifetime: 1800000
  keepalive-time: 300000
  validation-query: SELECT 1 FROM DUAL
  read-only: true
```

**8개 뷰테이블**:

| # | 뷰 | 컬럼 수 | 설명 |
|---|-----|---------|------|
| 1 | `COM.LMS_MEMBER_VIEW` | 22 | 회원 정보 |
| 2 | `COM.LMS_COURSE_VIEW` | 28 | 과목 정보 |
| 3 | `COM.LMS_LECTPLAN_VIEW` | 17 | 강의계획 |
| 4 | `COM.LMS_LECTPLAN_NCS_VIEW` | 22 | NCS 강의계획 |
| 5 | `COM.LMS_STUDENT_VIEW` | 10 | 수강생 |
| 6 | `COM.LMS_PROFESSOR_VIEW` | 11 | 교수자 |
| 7 | `COM.COURSE_INFO_VIEW` | 25 | 과정 정보 |
| 8 | `COM.Job_Postings_VIEW` | 27 | 채용 공고 |

### 7-4. MySQL (GCP — Docker)

| 항목 | 값 |
|------|-----|
| **위치** | GCE VM (34.64.207.10) 내부 Docker |
| **대소문자** | `lower_case_table_names=1` (대소문자 무시) |
| **비고** | GCP 환경에서만 사용, NCP 이관 시 UPPERCASE RENAME 필요 |

---

## 8. Vector DB (Qdrant)

| 항목 | 값 |
|------|-----|
| **이미지** | `qdrant/qdrant:v1.15.3` |
| **REST 포트** | 6333 |
| **gRPC 포트** | 6334 |
| **컬렉션** | `video_summary_vectors_gemini` |
| **차원** | 768 (Gemini Embedding) |
| **인증** | API Key (`QDRANT_API_KEY`) |
| **메모리** | 2GB (NCP WAS) |

**임베딩 모델**: Google GenAI `gemini-embedding-001`

**Spring AI 설정**:
```yaml
spring.ai:
  vectorstore.qdrant:
    host: ${QDRANT_HOST:localhost}
    port: ${QDRANT_GRPC_PORT:6334}
    api-key: ${QDRANT_API_KEY:}
    collection-name: ${QDRANT_COLLECTION:video_summary_vectors_gemini}
    use-tls: ${QDRANT_USE_TLS:false}
    initialize-schema: ${QDRANT_INIT_SCHEMA:true}
  google.genai:
    embedding:
      model: ${GOOGLE_EMBEDDING_MODEL:gemini-embedding-001}
      options.task-type: ${GOOGLE_EMBEDDING_TASK:RETRIEVAL_DOCUMENT}
```

**용도**:
- 교수자 콘텐츠 추천: 강의 제목/소개/키워드 → 유사 강의 검색
- 학생 홈 추천: 수강 이력 기반 콘텐츠 추천
- 자연어 검색: 채용 공고 시맨틱 검색

---

## 9. 외부 API 연동

### 9-1. Google Gemini (AI 분석/요약)

| 항목 | 값 |
|------|-----|
| **Base URL** | `https://generativelanguage.googleapis.com/v1beta` |
| **모델** | `gemini-3-flash-preview` (변경 가능) |
| **용도** | 동영상 요약, 키워드 추출, 통계 AI 분석, 채용 분류 |
| **Temperature** | 0.2 |
| **Max Output** | 4096 토큰 |
| **환경변수** | `GEMINI_API_KEY` (없으면 `GOOGLE_API_KEY` 사용) |

### 9-2. Kollus (동영상 플랫폼)

| 항목 | 값 |
|------|-----|
| **Base URL** | `https://api.kr.kollus.com` |
| **플레이어** | `https://v.kr.kollus.com` |
| **용도** | 동영상 다운로드, 트랜스크립션, 채널 관리 |
| **Webhook** | `POST /contentsummary/webhooks/kollus` |
| **환경변수** | `KOLLUS_ACCESS_TOKEN`, `KOLLUS_SECURITY_KEY`, `KOLLUS_CHANNEL_KEY` |

### 9-3. KOSIS / SGIS (통계청)

| 항목 | 값 |
|------|-----|
| **인증 URL** | `https://sgisapi.mods.go.kr/OpenAPI3/auth/authentication.json` |
| **인구 통계** | `https://sgisapi.mods.go.kr/OpenAPI3/stats/searchpopulation.json` |
| **산업 코드** | `https://sgisapi.mods.go.kr/OpenAPI3/stats/industrycode.json` |
| **사업체 통계** | `https://sgisapi.mods.go.kr/OpenAPI3/stats/company.json` |
| **환경변수** | `KOSIS_CONSUMER_KEY`, `KOSIS_CONSUMER_SECRET` |

### 9-4. Work24 (워크넷)

| 항목 | 값 |
|------|-----|
| **채용 API** | `https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo210L01.do` |
| **코드 API** | `https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo21L01.do` |
| **응답 형식** | XML |
| **캐시** | 기본 활성 (TTL: 1440분 = 24시간) |
| **환경변수** | `WORK24_AUTH_KEY` |

### 9-5. JobKorea (잡코리아)

| 항목 | 값 |
|------|-----|
| **API URL** | `http://www.jobkorea.co.kr/Service_JK/Data/JK_GI_XML_List.asp` |
| **응답 형식** | XML |
| **캐시** | 기본 활성 (TTL: 1440분) |
| **환경변수** | `JOBKOREA_API_KEY`, `JOBKOREA_OEM_CODE` |

### 9-6. Google Speech-to-Text v2

| 항목 | 값 |
|------|-----|
| **용도** | Kollus 동영상 → 음성 → 텍스트 변환 |
| **인증** | Google OAuth (서비스 계정) |
| **저장소** | Google Cloud Storage (중간 결과물) |

---

## 10. 엔드포인트 라우팅 맵

### 10-1. Nginx → 백엔드 라우팅 (NCP WEB)

```
┌─────────────────────────────────┬──────────────────┬──────────────────┐
│ URL 패턴                        │ 대상              │ Rate Limit       │
├─────────────────────────────────┼──────────────────┼──────────────────┤
│ /statistics/*                   │ lms-api:8081     │ 30r/s burst 20   │
│ /job/*                          │ lms-api:8081     │ 30r/s burst 20   │
│ /student/*                      │ lms-api:8081     │ 30r/s burst 20   │
│ /tutor/*                        │ lms-api:8081     │ 30r/s burst 20   │
│ /contentsummary/*               │ lms-api:8081     │ 30r/s burst 20   │
│ /reco-contents/*                │ lms-api:8081     │ 30r/s burst 20   │
│ /global/*                       │ lms-api:8081     │ 30r/s burst 20   │
│ /antifraud/*                    │ lms-api:8081     │ 30r/s burst 20   │
│ /livesession/*                  │ lms-api:8081     │ WebSocket 업그레이드│
│ /actuator/*                     │ lms-api:8081     │ 내부망만          │
│ /login*.jsp                     │ lms-resin:8080   │ 5r/s burst 10    │
│ / (기본)                        │ lms-resin:8080   │ —                │
├─────────────────────────────────┼──────────────────┼──────────────────┤
│ /common/* (정적)                │ lms-resin:8080   │ 캐시 7일         │
│ /tutor_lms/app/assets/* (정적)  │ lms-resin:8080   │ 캐시 30일        │
└─────────────────────────────────┴──────────────────┴──────────────────┘
```

### 10-2. JSP 프록시 → Spring API

JSP 레거시 화면에서 신규 Spring API를 호출할 때 사용하는 JSP 프록시:

| JSP 프록시 | 대상 API |
|-----------|---------|
| `/tutor_lms/api/content_recommend.jsp` | `POST /tutor/content-recommend/lessons` |
| `/tutor_lms/api/statistics_proxy.jsp` | `/statistics/*` (경로 투과) |
| `/api/job_proxy.jsp` | `/job/*` (경로 투과) |
| `/mypage/new_main/index.jsp` | `POST /student/content-recommend/home` |
| `/mypage/new_main/reco_video_list.jsp` | `POST /student/content-recommend/search`, `/home/more` |

환경변수 `POLYTECH_LMS_API_BASE` (기본: `http://localhost:8081`)로 Spring API 주소 결정.

### 10-3. 로컬/GCP/NCP 엔드포인트 비교

| 서비스 | 로컬 | GCP | NCP |
|--------|------|-----|-----|
| **Spring API** | `localhost:8081` | `34.64.207.10:8081` | `192.168.2.6:8081` |
| **Resin JSP** | `localhost:8080` | `34.64.207.10:8080` | `192.168.2.6:8080` |
| **MySQL** | `localhost:3306` | `34.64.207.10:3306` | `growai-db.vpc-cdb.ntruss.com:3306` |
| **Qdrant REST** | `localhost:6333` | `34.64.207.10:6333` | `192.168.2.6:6333` (Docker 내부) |
| **Qdrant gRPC** | `localhost:6334` | `34.64.207.10:6334` | `192.168.2.6:6334` (Docker 내부) |
| **Oracle** | — | — | `172.28.5.34:61521` (VPN) |
| **사용자 진입** | `localhost:8080` | `epoly-kopo.web.app` | `27.96.144.249` |
| **Vite Dev** | `localhost:5173` | — | — |

---

## 11. 환경변수 전체 목록

### 11-1. Database

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `DB_URL` | `jdbc:mysql://localhost:3306/lms?...` | MySQL JDBC URL |
| `DB_USERNAME` | `lms` | MySQL 사용자 |
| `DB_PASSWORD` | (필수) | MySQL 비밀번호 |
| `HAKSA_ORACLE_URL` | — | Oracle JDBC URL |
| `HAKSA_ORACLE_USERNAME` | `KOPO_LMS` | Oracle 사용자 |
| `HAKSA_ORACLE_PASSWORD` | — | Oracle 비밀번호 |
| `HAKSA_ORACLE_POOL_SIZE` | `5` | Oracle 커넥션 풀 최대 |
| `HAKSA_ORACLE_POOL_MIN` | `2` | Oracle 커넥션 풀 최소 |
| `HAKSA_ORACLE_CONN_TIMEOUT` | `30000` | Oracle 커넥션 타임아웃 (ms) |

### 11-2. Vector DB (Qdrant)

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `QDRANT_HOST` | `localhost` | Qdrant 호스트 |
| `QDRANT_GRPC_PORT` | `6334` | gRPC 포트 |
| `QDRANT_API_KEY` | — | API 키 |
| `QDRANT_COLLECTION` | `video_summary_vectors_gemini` | 컬렉션명 |
| `QDRANT_USE_TLS` | `false` | TLS 사용 |
| `QDRANT_INIT_SCHEMA` | `true` | 스키마 자동 초기화 |

### 11-3. AI / LLM

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `GOOGLE_API_KEY` | — | Google GenAI API 키 |
| `GEMINI_API_KEY` | (GOOGLE_API_KEY) | Gemini API 키 |
| `GOOGLE_EMBEDDING_MODEL` | `gemini-embedding-001` | 임베딩 모델 |
| `GOOGLE_EMBEDDING_TASK` | `RETRIEVAL_DOCUMENT` | 임베딩 작업 유형 |
| `GEMINI_MODEL` | `gemini-3-flash-preview` | Gemini 모델 |
| `GEMINI_TEMPERATURE` | `0.2` | 생성 온도 |
| `GEMINI_MAX_OUTPUT_TOKENS` | `4096` | 최대 출력 토큰 |
| `GEMINI_HTTP_TIMEOUT` | `PT60S` | HTTP 타임아웃 |

### 11-4. Kollus (동영상)

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `KOLLUS_API_BASE_URL` | `https://api.kr.kollus.com` | API 기본 URL |
| `KOLLUS_ACCESS_TOKEN` | — | 접근 토큰 |
| `KOLLUS_SECURITY_KEY` | — | 보안 키 |
| `KOLLUS_CHANNEL_KEY` | — | 채널 키 |
| `KOLLUS_CLIENT_USER_ID` | `contentsummary` | 클라이언트 ID |
| `KOLLUS_PLAYER_BASE_URL` | `https://v.kr.kollus.com` | 플레이어 URL |
| `KOLLUS_HTTP_TIMEOUT` | `PT60S` | HTTP 타임아웃 |
| `KOLLUS_MEDIA_TOKEN_EXPIRE` | `PT5M` | 미디어 토큰 만료 |
| `KOLLUS_WEBHOOK_TOKEN` | — | 웹훅 인증 토큰 |

### 11-5. 콘텐츠 요약/트랜스크립션

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `CONTENTSUMMARY_WORKER_ENABLED` | `false` | 워커 활성화 |
| `CONTENTSUMMARY_WORKER_POLL_DELAY_MS` | `30000` | 폴링 간격 |
| `CONTENTSUMMARY_WORKER_BATCH_SIZE` | `3` | 배치 크기 |
| `CONTENTSUMMARY_WORKER_MAX_RETRIES` | `5` | 최대 재시도 |
| `CONTENTSUMMARY_WORKER_RETRY_DELAY_SECONDS` | `300` | 재시도 간격 |
| `CONTENTSUMMARY_WORKER_PROCESSING_TIMEOUT_SECONDS` | `7200` | 처리 타임아웃 |
| `CONTENTSUMMARY_ADMIN_TOKEN` | — | 관리자 토큰 |

### 11-6. 학사 동기화 (PolySync)

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `POLYSYNC_SCHEDULER_ENABLED` | `true` | 스케줄러 활성화 |
| `POLYSYNC_RESIN_BASE_URL` | `http://resin:8080` | Resin 기본 URL |
| `POLY_SYNC_TOKEN` | — | 동기화 토큰 |
| `POLYSYNC_FULL_SYNC_DELAY_MS` | `1800000` | 전체 동기화 간격 (30분) |
| `POLYSYNC_STUDENT_SYNC_DELAY_MS` | `300000` | 수강생 동기화 간격 (5분) |
| `POLYSYNC_CONNECT_TIMEOUT` | `10` | 연결 타임아웃 (초) |
| `POLYSYNC_READ_TIMEOUT` | `300` | 읽기 타임아웃 (초) |
| `POLYSYNC_START_YEAR_OFFSET` | `0` | 시작 연도 오프셋 |
| `POLYSYNC_END_YEAR_OFFSET` | `1` | 종료 연도 오프셋 |

### 11-7. 통계

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `STATISTICS_AI_ENABLED` | `true` | 통계 AI 활성화 |
| `STATISTICS_AI_API_KEY` | (GOOGLE_API_KEY) | AI API 키 |
| `STATISTICS_AI_MODEL` | `gemini-3-flash-preview` | AI 모델 |
| `KOSIS_CONSUMER_KEY` | — | KOSIS API 키 |
| `KOSIS_CONSUMER_SECRET` | — | KOSIS API 시크릿 |

### 11-8. 채용 연동

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `WORK24_AUTH_KEY` | — | Work24 인증 키 |
| `WORK24_CACHE_ENABLED` | `true` | 캐시 활성화 |
| `WORK24_CACHE_TTL_MINUTES` | `1440` | 캐시 TTL (24시간) |
| `JOBKOREA_API_KEY` | — | JobKorea API 키 |
| `JOBKOREA_OEM_CODE` | — | JobKorea OEM 코드 |
| `JOBKOREA_ENABLED` | `true` | JobKorea 활성화 |
| `JOBKOREA_CACHE_ENABLED` | `true` | 캐시 활성화 |
| `JOBKOREA_CACHE_TTL_MINUTES` | `1440` | 캐시 TTL |

### 11-9. 기타

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `POLYTECH_LMS_API_BASE` | `http://localhost:8081` | JSP → Spring API 기본 URL |
| `JOB_ADMIN_TOKEN` | — | 채용 API 관리자 토큰 |
| `VECTOR_SEARCH_MAX_TOP_K` | `300` | 벡터 검색 최대 결과 |
| `LOG_LEVEL_SQL` | `INFO` | SQL 로그 레벨 |

---

## 12. 빌드 및 배포 명령어

### 12-1. 로컬 개발 환경 구축

```bash
# 1. 인프라 (MySQL + Qdrant + Resin)
cp .env.local.example .env.local
# .env.local에 비밀번호 등 설정
docker compose -f docker-compose.local.yml up -d

# 2. Spring Boot API (IDE 또는 CLI)
cd polytech-lms-api
./gradlew bootRun
# → http://localhost:8081

# 3. React 개발 서버
cd project
npm install
npm run dev
# → http://localhost:5173 (API 프록시: localhost:8080)

# 4. React 빌드 (public_html/tutor_lms/app/ 으로 출력)
cd project
npm run build
```

### 12-2. NCP 배포 (자동 — GitHub Actions)

```bash
# dev/stag에서 개발 후 prod로 머지
git checkout prod
git merge stag    # 또는 PR 생성
git push origin prod
# → deploy-lms-ncp.yml 자동 실행
```

### 12-3. NCP 배포 (수동 — 서버 Pull)

```bash
# WAS 서버 SSH 접속
ssh ubuntu@192.168.2.6

# WAS 배포
sudo REPO_BRANCH=prod \
  NCP_DB_HOST=growai-db.vpc-cdb.ntruss.com \
  LMS_DB_PASSWORD='비밀번호' \
  QDRANT_API_KEY='키' \
  GOOGLE_API_KEY='키' \
  bash /opt/deploy-workspace/repo/tools/ncp/pull-deploy-was.sh

# WEB 서버 SSH 접속
ssh ubuntu@192.168.1.6

# WEB 배포
sudo REPO_BRANCH=prod \
  WAS_IP=192.168.2.6 \
  bash /opt/deploy-workspace/repo/tools/ncp/pull-deploy-web.sh
```

### 12-4. GCP/Firebase 배포

```bash
# Firebase 프록시 함수 + Hosting 배포
cd tools/gcp/firebase-proxy-deploy
npm --prefix functions install
firebase deploy --project gen-lang-client-0343478566 --only functions,hosting

# GCP VM 원클릭 배포 (Windows)
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gcp/one-click-setup.ps1 `
  -ProjectId gen-lang-client-0343478566 `
  -FirebaseSite epoly-kopo `
  -DbPassword "비밀번호" `
  -GoogleApiKey "API키"
```

### 12-5. DB 마이그레이션

```bash
# GCP → NCP Cloud DB 초기 이관
bash tools/ncp/migrate-db.sh \
  growai-db.vpc-cdb.ntruss.com \
  lms lms '비밀번호' \
  ./lms-dump.sql

# 테이블명 UPPERCASE 변환 (NCP Cloud DB에서)
mysql -h growai-db.vpc-cdb.ntruss.com -u lms -p lms -e "
  SELECT CONCAT('RENAME TABLE lms.\`', table_name, '\` TO lms.\`', UPPER(table_name), '\`;')
  FROM information_schema.tables
  WHERE table_schema='lms' AND table_name != UPPER(table_name);
"
```

### 12-6. React 빌드 (필수)

> **CLAUDE.md 규칙**: `project/` 코드 변경 시 반드시 빌드 실행

```bash
cd project && npm run build
# 빌드 결과: public_html/tutor_lms/app/
#   ├── index.html
#   └── assets/ (JS/CSS, 해시 파일명)

# 빌드 확인
git status  # public_html/tutor_lms/app/ 변경 확인
```

---

## 13. 모니터링 및 헬스체크

### 13-1. Spring Boot Actuator

| 엔드포인트 | 설명 | 접근 제한 |
|-----------|------|----------|
| `/actuator/health` | 서비스 상태 (DB, Qdrant, Disk) | Nginx: 192.168.0.0/16 only |
| `/actuator/info` | 앱 정보 | Nginx: 192.168.0.0/16 only |
| `/actuator/metrics` | 메트릭 (JVM, HTTP 등) | Nginx: 192.168.0.0/16 only |

**헬스체크 응답 예시**:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL" } },
    "qdrant": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

### 13-2. Docker 컨테이너 헬스체크

| 컨테이너 | 방식 | 간격 | 시작 대기 |
|----------|------|------|-----------|
| lms-qdrant | `HTTP GET :6333/healthz` | 30초 | 15초 |
| lms-api | `curl :8081/actuator/health` | 30초 | 60초 |
| lms-resin | `curl :8080/` | 30초 | 30초 |

### 13-3. 수동 헬스체크 명령어

```bash
# WAS 서버에서
docker ps                                              # 컨테이너 상태
docker logs lms-api --tail 100                         # API 로그
docker logs lms-resin --tail 100                       # Resin 로그
curl -s http://localhost:8081/actuator/health | jq .   # API 헬스
curl -s http://localhost:6333/healthz                  # Qdrant 헬스
curl -sI http://localhost:8080/                        # Resin 응답

# WEB 서버에서
nginx -t                                               # Nginx 설정 검증
curl -sI http://localhost/                             # Nginx → Resin
curl -sI http://localhost/actuator/health              # Nginx → API

# DB 연결
mysql -h growai-db.vpc-cdb.ntruss.com -u lms -p -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lms'"
```

### 13-4. 로그 경로

| 서비스 | 경로 | 형식 |
|--------|------|------|
| Spring Boot API | `/app/logs/polytech-lms-api.log` (컨테이너) | 100MB/파일, 7일, 1GB 총합 |
| Resin | `/opt/polytech-lms/logs/resin/` (호스트) | — |
| Poly Sync | `/var/log/malgnlms/poly_sync.log` (호스트) | — |
| Poly Sync (cron) | `/var/log/malgnlms/poly_sync_cron.log` | — |
| Nginx | `/var/log/nginx/access.log`, `error.log` | — |
| Docker | `docker logs <컨테이너>` | JSON 파일, 50MB/5개 |

---

## 14. 학사 연동 (PolySync)

### 14-1. 아키텍처

```
┌── Spring Boot Scheduler ───────────────────┐
│  (POLYSYNC_SCHEDULER_ENABLED=true)         │
│  • 전체 동기화: 30분 간격                    │
│  • 수강생 동기화: 5분 간격                   │
└─────────────┬──────────────────────────────┘
              │ HTTP POST
              ▼
┌── Resin JSP (/main/poly_sync.jsp) ─────────┐
│  Oracle VPN → 뷰테이블 조회                  │
│  MySQL → 미러 테이블 UPSERT/DELETE           │
│  응답: { rst_code: "0000", ... }             │
└──────────────────────────────────────────────┘
```

### 14-2. 미러 테이블 (MySQL)

| 테이블 | 소스 뷰 | 설명 |
|--------|---------|------|
| `LM_POLY_COURSE` | `LMS_COURSE_VIEW` | 과목 |
| `LM_POLY_STUDENT` | `LMS_STUDENT_VIEW` | 수강생 |
| `LM_POLY_MEMBER` | `LMS_MEMBER_VIEW` | 회원 |
| `LM_POLY_LECTPLAN` | `LMS_LECTPLAN_VIEW` | 강의계획 |
| `LM_POLY_LECTPLAN_NCS` | `LMS_LECTPLAN_NCS_VIEW` | NCS 강의계획 |
| `LM_POLY_PROFESSOR` | `LMS_PROFESSOR_VIEW` | 교수자 |
| `LM_POLY_SYNC_LOG` | — | 동기화 이력 |

### 14-3. cron 스케줄 (WAS 서버)

```bash
# 매일 02:10 — 전체 동기화 (모든 년도)
10 2 * * * flock -n /tmp/poly_sync.lock \
  POLY_SYNC_BASE_URL=http://127.0.0.1:8080 \
  /opt/polytech-lms/tools/poly_sync/run_poly_sync.sh \
  >> /var/log/malgnlms/poly_sync_cron.log 2>&1

# 평일 03:10 — 수강생만 빠른 동기화 (최근 2년)
10 3 * * 1-5 flock -n /tmp/poly_sync_student.lock \
  POLY_SYNC_BASE_URL=http://127.0.0.1:8080 \
  POLY_SYNC_LOG_FILE=/var/log/malgnlms/poly_sync_student.log \
  /opt/polytech-lms/tools/poly_sync/run_poly_sync.sh \
  --mode student_only --start-year 2025 --end-year 2026 \
  >> /var/log/malgnlms/poly_sync_student_cron.log 2>&1
```

### 14-4. 수동 동기화 실행

```bash
# WAS 서버에서
cd /opt/polytech-lms
POLY_SYNC_BASE_URL=http://127.0.0.1:8080 \
POLY_SYNC_LOG_FILE=/var/log/malgnlms/poly_sync_manual.log \
tools/poly_sync/run_poly_sync.sh \
  --start-year 2025 --end-year 2026

# 수강생만
tools/poly_sync/run_poly_sync.sh \
  --mode student_only --start-year 2025 --end-year 2026

# 드라이런 (삭제 시뮬레이션)
tools/poly_sync/run_poly_sync.sh --dry-run-user-delete Y
```

### 14-5. 학사 VPN 검증

```bash
# 8개 뷰테이블 전수 검증
bash tools/ncp/test-haksa-views.sh [--sqlplus] [--verbose]

# Phase 0: VPN 네트워크 (ping, TCP, HTTPS)
# Phase 1: vpn_test.jsp 8개 뷰 조회
# Phase 2: sqlplus 직접 접속 (--sqlplus 옵션)
# Phase 3: Docker 컨테이너 내부 연결
```

---

## 15. 롤백 및 백업

### 15-1. WAS 롤백

```bash
# 가장 최근 백업으로 복구
sudo bash /opt/polytech-lms/tools/ncp/rollback-was.sh

# 특정 시점 지정
sudo bash /opt/polytech-lms/tools/ncp/rollback-was.sh 20260224-153000

# 백업 목록 확인
ls -la /opt/polytech-lms-backups/
```

**백업 구조**:
```
/opt/polytech-lms-backups/{YYYYMMDD-HHMMSS}/
├── polytech-lms-api.jar
├── docker-compose.yml
└── .env
```
- 배포마다 자동 백업 생성
- 7일 초과 백업 자동 삭제

### 15-2. DB 백업

```bash
# 수동 백업
bash tools/ncp/backup-db.sh

# 백업 구조
/opt/polytech-lms/db-backups/
├── daily/lms-{YYYYMMDD}.sql.gz      # 7일 보관
└── weekly/lms-weekly-{YYYYMMDD}.sql.gz  # 28일 보관
```

**mysqldump 옵션**:
```bash
--single-transaction   # InnoDB 일관성 스냅샷
--routines             # 저장 프로시저/함수 포함
--triggers             # 트리거 포함
--set-gtid-purged=OFF  # NCP Cloud DB 호환
```

### 15-3. NCP Cloud DB 자동 백업
- 30일 보관 (NCP Cloud DB 관리형 기능)
- NCP 콘솔에서 복원 가능

---

## 16. 트러블슈팅 가이드

### 16-1. Cloud DB 연결 실패

**증상**: `Communications link failure`
**원인**: DNS 해석 실패
**해결**:
```bash
# 호스트명 대신 IP 사용
# growai-db.vpc-cdb.ntruss.com → 192.168.3.6
# application.yml의 DB_URL 변경 또는 /etc/hosts 추가
echo "192.168.3.6 growai-db.vpc-cdb.ntruss.com" >> /etc/hosts
```

### 16-2. GitHub HTTPS 인증 실패

**증상**: `remote: Support for password authentication was removed`
**해결**: Classic Personal Access Token (PAT) 사용
```bash
git remote set-url origin https://{PAT}@github.com/rodem-glitch/epoly_malgnLMS_to_NCP_Convert.git
```

### 16-3. resin-web.xml sed & 이스케이프

**증상**: `&` 문자가 sed에서 깨짐
**해결**: 템플릿에서 `\&amp;` 사용
```bash
# Bad:  &amp;
# Good: sed에서 & → \&amp; 이스케이프 필요
```

### 16-4. Nginx IPv6 소켓 에러

**증상**: `[emerg] socket() [::]:80 failed`
**해결**: `listen [::]:80` 주석 처리
```nginx
# listen [::]:80;
listen 80;
```

### 16-5. about:blank 리다이렉트

**증상**: 로그인 후 빈 페이지
**원인**: `TB_SITE.DOMAIN` 값 불일치
**해결**: DB에서 도메인 업데이트
```sql
UPDATE TB_SITE SET DOMAIN = '{새_도메인}' WHERE SITE_ID = '{사이트_ID}';
```

### 16-6. sql_require_primary_key 에러

**증상**: `ERROR 3750: Unable to create or change a table without a primary key`
**해결**: NCP Cloud DB 설정에서 OFF
```sql
SET GLOBAL sql_require_primary_key = OFF;
```

### 16-7. 테이블명 대소문자 불일치

**증상**: `Table 'lms.lm_poly_course' doesn't exist` (소문자)
**해결**: 모든 테이블을 UPPERCASE로 RENAME
```sql
RENAME TABLE lms.`lm_poly_course` TO lms.`LM_POLY_COURSE`;
-- 전체 테이블 자동 생성 쿼리는 §12-5 참조
```

### 16-8. Docker 권한 오류

**증상**: `permission denied while trying to connect to the Docker daemon socket`
**해결**: sudo 사용 또는 사용자를 docker 그룹에 추가
```bash
sudo usermod -aG docker $USER
# 재로그인 필요
```

### 16-9. Resin OOM (OutOfMemory)

**증상**: poly_sync.jsp 실행 시 JVM 크래시
**원인**: Resin 기본 JVM 256MB
**해결**: deploy-was.sh에서 자동 적용 (수동 시):
```bash
docker exec lms-resin sh -c \
  "sed -i 's/^# jvm_args.*/jvm_args  : -Xmx2g -Xms1g/' /etc/resin/resin.properties"
docker restart lms-resin
```

### 16-10. Kollus TLS 인증서 에러

**증상**: `PKIX path building failed`
**해결**: deploy-was.sh에서 자동 처리 (수동 시):
```bash
# 인증서 체인 추출
openssl s_client -connect api.kr.kollus.com:443 -showcerts </dev/null 2>/dev/null \
  | sed -n '/BEGIN CERT/,/END CERT/p' > /tmp/kollus_chain.pem

# Java keystore에 import
docker exec lms-resin keytool -importcert -trustcacerts \
  -keystore $JAVA_HOME/lib/security/cacerts \
  -storepass changeit -noprompt -alias kollus \
  -file /tmp/kollus_chain.pem
```

---

## Appendix A: 디렉토리 트리 상세

### A-1. 프로젝트 루트

```
polytech-lms/
├── .claude/                          # Claude 에이전트 설정
├── .github/
│   └── workflows/
│       ├── deploy-lms-ncp.yml        # NCP 자동 배포
│       └── ci-validate.yml           # CI 빌드 검증
├── .githooks/                        # Git 훅
├── .vscode/                          # VS Code 설정
│
├── AGENTS.md                         # 에이전트 최상위 규칙
├── CLAUDE.md                         # 에이전트 강제 규칙
├── README.md                         # 프로젝트 개요
├── .gitignore
├── .env.local.example                # 로컬 환경변수 템플릿
├── docker-compose.local.yml          # 로컬 개발 Docker Compose
│
├── polytech-lms-api/                 # Spring Boot API (Java 17)
├── project/                          # React SPA (TypeScript/Vite)
├── public_html/                      # JSP 웹루트 (레거시)
├── src/                              # DAO 클래스 (레거시 Java 8)
├── resin/                            # Resin WAS 설정
│
├── tools/
│   ├── gcp/                          # GCP 배포 도구
│   ├── ncp/                          # NCP 배포 도구
│   ├── poly_sync/                    # 학사 동기화 배치
│   └── rpg/                          # RPG 자동화
│
├── docs/                             # 문서
│   ├── ncp-deployment-guide.md
│   ├── ncp-haksa-sync-guide.md
│   ├── ncp-infra-request-mail.md
│   ├── ncp-project-tree-spec.md
│   ├── rpg/
│   └── sql/
│
├── claude/                           # 에이전트 규칙 세트
│   ├── README.md
│   └── rules/
│       ├── 00-core.md
│       ├── security.md
│       ├── git-workflow.md
│       ├── testing.md
│       ├── coding-style.md
│       └── patterns.md
│
├── var/                              # 런타임 데이터
├── web/                              # 웹 설정
├── 통계/                             # 통계 Excel 데이터 파일
├── EgovMainView.jsp                  # 전자정부 메인 참고 파일
├── Jobkorea_API_Guide_v4_1.pdf       # JobKorea API 가이드
├── 한국고용정보원_직종코드_*.csv      # 직종 코드 CSV
├── 정리_폴리텍 API.xlsx              # API 정리 문서
├── 채용 매핑.txt / 채용 화면.pptx    # 채용 화면 기획
└── 콘텐츠 추천 테스트 요약 샘플.txt   # 추천 테스트 샘플
```

### A-2. polytech-lms-api/ (Spring Boot)

```
polytech-lms-api/
├── build.gradle                      # Gradle 빌드 (Spring Boot 3.2.5, Java 17)
├── gradlew / gradlew.bat
├── settings.gradle
├── src/main/
│   ├── java/kr/polytech/lms/
│   │   ├── PolytechLmsApiApplication.java  # 진입점
│   │   ├── antifraud/                # 부정행위 방지
│   │   │   └── controller/AntiFraudController.java
│   │   ├── contentsummary/           # 콘텐츠 요약 (Gemini + Kollus)
│   │   │   ├── controller/ContentSummaryController.java
│   │   │   ├── service/
│   │   │   ├── client/ (Gemini, Kollus, GoogleSTT)
│   │   │   └── config/
│   │   ├── global/                   # 공통 (헬스, 벡터인덱스)
│   │   │   └── controller/GlobalHealthController.java
│   │   │   └── controller/VectorIndexController.java
│   │   ├── haksa/                    # 학사 Oracle 연동
│   │   │   ├── config/HaksaOracleDataSourceConfig.java
│   │   │   ├── config/HaksaOracleProperties.java
│   │   │   └── controller/HaksaHealthController.java
│   │   ├── job/                      # 채용 (JobKorea + Work24)
│   │   │   ├── controller/JobController.java
│   │   │   └── client/ (JobKorea, Work24)
│   │   ├── livesession/              # 라이브 세션 (WebSocket)
│   │   │   └── controller/LiveSessionController.java
│   │   ├── recocontent/              # 콘텐츠 추천 관리
│   │   │   └── controller/RecoContentAdminController.java
│   │   ├── statistics/               # 통계 대시보드
│   │   │   ├── dashboard/controller/StatisticsDashboardApiController.java
│   │   │   ├── dashboard/controller/StatisticsDashboardPageController.java
│   │   │   ├── ai/v2/StatisticsAiV2ApiController.java
│   │   │   ├── controller/KosisStatisticsController.java
│   │   │   └── client/ (KOSIS, SGIS)
│   │   ├── studentcontentrecommend/  # 학생 콘텐츠 추천
│   │   │   └── controller/StudentContentRecommendController.java
│   │   └── tutorcontentrecommend/    # 교수자 콘텐츠 추천
│   │       └── controller/TutorContentRecommendController.java
│   └── resources/
│       ├── application.yml           # 메인 설정
│       ├── data-catalog.yml          # 통계 AI 카탈로그
│       ├── static/statistics/        # 통계 대시보드 HTML
│       └── jobkorea/                 # 직종 코드 CSV
└── build/libs/polytech-lms-api.jar   # 빌드 산출물 (~131MB)
```

### A-3. project/ (React SPA)

```
project/
├── package.json                      # React 18, Vite 6, TailwindCSS 4
├── vite.config.ts                    # 빌드 설정 (→ ../public_html/tutor_lms/app/)
├── tsconfig.json
├── index.html
├── src/
│   ├── App.tsx
│   ├── main.tsx
│   ├── components/                   # Radix UI 기반 컴포넌트
│   │   └── ui/ (button, card, dialog, table, ...)
│   ├── pages/                        # 페이지 컴포넌트
│   ├── hooks/                        # 커스텀 훅
│   ├── lib/                          # 유틸리티
│   └── styles/
│       └── globals.css               # TailwindCSS
└── node_modules/
```

### A-4. public_html/ (JSP 웹루트)

```
public_html/
├── WEB-INF/
│   ├── web.xml                       # 서블릿 설정
│   ├── resin-web.xml                 # 배포 시 생성 (JNDI, 컴파일러)
│   ├── lib/                          # JAR 라이브러리
│   ├── classes/                      # 컴파일 출력 (동적)
│   ├── message/                      # I18n 메시지
│   └── work/                         # JSP 컴파일 캐시
│
├── tutor_lms/
│   ├── app/                          # React 빌드 산출물
│   │   ├── index.html
│   │   └── assets/
│   └── api/                          # JSP 프록시
│       ├── content_recommend.jsp
│       ├── statistics_proxy.jsp
│       ├── kollus_list.jsp
│       └── ...
│
├── main/                             # 메인 화면
│   ├── poly_sync.jsp                 # 학사 동기화 JSP
│   └── ...
├── mypage/                           # 마이페이지
│   └── new_main/
│       ├── index.jsp                 # 학생 홈 추천
│       └── reco_video_list.jsp       # 추천 검색
├── auth/                             # 인증/로그인
├── member/                           # 회원관리
├── course/                           # 수업관리
├── board/                            # 게시판
├── sysop/                            # 시스템 관리자
├── common/                           # 공통 CSS/JS/이미지
├── api/                              # API 프록시 (채용 등)
├── data/                             # 런타임 데이터 (배포 제외)
│   ├── file/                         # 업로드 파일
│   ├── log/                          # 로그
│   └── tmp/                          # 임시
└── [기타 30+ 디렉토리]
```

### A-5. tools/ (배포/운영)

```
tools/
├── gcp/                              # GCP 배포 도구
│   ├── README.md                     # GCP 원클릭 배포 가이드
│   ├── one-click-setup.ps1           # PowerShell 자동 셋업
│   ├── start-one-click.bat           # Windows 배치 런처
│   ├── firebase-proxy-deploy/        # Firebase Hosting + Functions
│   │   ├── .firebaserc              # 프로젝트: gen-lang-client-0343478566
│   │   ├── firebase.json            # 사이트: epoly-kopo, vmproxy 리라이트
│   │   ├── functions/
│   │   │   ├── index.js             # vmproxy 프록시 함수
│   │   │   └── package.json
│   │   └── public/
│   └── templates/                    # GCP Docker 스택 템플릿
│       ├── docker-compose.yml.tpl
│       ├── nginx-api.conf.tpl
│       ├── deploy-stack.sh.tpl
│       └── resin-web.xml.tpl
│
├── ncp/                              # NCP 배포 도구
│   ├── README.md                     # NCP 배포 가이드
│   ├── pull-deploy-was.sh            # WAS Pull 배포 (1-repo)
│   ├── pull-deploy-web.sh            # WEB Pull 배포
│   ├── migrate-db.sh                 # DB 초기 마이그레이션
│   ├── backup-db.sh                  # DB 자동 백업
│   ├── rollback-was.sh               # WAS 롤백
│   ├── setup-haksa-sync.sh           # 학사 연동 초기화
│   ├── test-haksa-views.sh           # 학사 뷰테이블 검증
│   └── templates/                    # NCP 배포 템플릿
│       ├── docker-compose-was.yml.tpl # Docker Compose (API+Resin+Qdrant)
│       ├── nginx-web.conf.tpl        # Nginx 역방향 프록시
│       ├── deploy-was.sh.tpl         # WAS 배포 스크립트
│       ├── deploy-web.sh.tpl         # WEB 배포 스크립트
│       └── resin-web.xml.tpl         # Resin JNDI 설정
│
├── poly_sync/                        # 학사 동기화 배치
│   ├── README.md
│   ├── run_poly_sync.py              # Python 동기화 클라이언트
│   └── run_poly_sync.sh              # Shell 동기화 래퍼
│
└── rpg/                              # RPG 자동화
    ├── setup-githooks.ps1
    ├── generate.ps1
    └── finish.ps1
```

---

## Appendix B: 설정 파일 경로 색인

| 설정 | 파일 경로 | 비고 |
|------|----------|------|
| Spring Boot 메인 | `polytech-lms-api/src/main/resources/application.yml` | DB, AI, PolySync, 로깅 전체 |
| Gradle 빌드 | `polytech-lms-api/build.gradle` | Spring Boot 3.2.5, Java 17, 의존성 |
| React 빌드 | `project/vite.config.ts` | 빌드 → public_html/tutor_lms/app/ |
| React 패키지 | `project/package.json` | React 18, Vite 6, TailwindCSS |
| 로컬 Docker | `docker-compose.local.yml` | MySQL + Qdrant + Resin |
| 로컬 환경변수 | `.env.local.example` | 템플릿 |
| NCP Docker 템플릿 | `tools/ncp/templates/docker-compose-was.yml.tpl` | API + Resin + Qdrant |
| NCP Nginx 템플릿 | `tools/ncp/templates/nginx-web.conf.tpl` | 라우팅, Rate Limit, 캐시 |
| NCP Resin 템플릿 | `tools/ncp/templates/resin-web.xml.tpl` | JNDI, 컴파일러, DB |
| NCP WAS 배포 | `tools/ncp/templates/deploy-was.sh.tpl` | Docker, JVM, 학사, cron |
| NCP WEB 배포 | `tools/ncp/templates/deploy-web.sh.tpl` | Nginx, SSL |
| NCP CI/CD | `.github/workflows/deploy-lms-ncp.yml` | prod push → 자동 배포 |
| CI 검증 | `.github/workflows/ci-validate.yml` | dev/stag 빌드 검증 |
| Firebase 설정 | `tools/gcp/firebase-proxy-deploy/firebase.json` | epoly-kopo, vmproxy |
| Firebase 프록시 | `tools/gcp/firebase-proxy-deploy/functions/index.js` | 쿠키 브릿지 |
| GCP 원클릭 | `tools/gcp/one-click-setup.ps1` | VM + Firebase 자동 배포 |
| Resin (로컬) | `resin/resin.xml` | 로컬 테스트용 |
| Git 무시 | `.gitignore` | node_modules, .env, data 등 |
| 에이전트 규칙 | `AGENTS.md`, `CLAUDE.md`, `claude/` | 개발 에이전트 강제 규칙 |

---

## Appendix C: GitHub Secrets 전체 목록

### C-1. NCP 배포 (deploy-lms-ncp.yml)

| Secret | 필수 | 설명 |
|--------|------|------|
| `NCP_WAS_SSH_KEY` | O | WAS 서버 SSH 프라이빗 키 |
| `NCP_WEB_SSH_KEY` | O | WEB 서버 SSH 프라이빗 키 |
| `NCP_WAS_SERVER_IP` | O | WAS IP (192.168.2.6) |
| `NCP_WEB_SERVER_IP` | O | WEB IP (192.168.1.6) |
| `NCP_DB_HOST` | O | Cloud DB 호스트명 |
| `NCP_DB_PASSWORD` | O | Cloud DB root 비밀번호 |
| `LMS_DB_PASSWORD` | O | 앱 DB 사용자 비밀번호 |
| `QDRANT_API_KEY` | O | Qdrant 벡터DB API 키 |
| `QDRANT_COLLECTION` | △ | 컬렉션명 (기본: video_summary_vectors_gemini) |
| `GOOGLE_API_KEY` | O | Google GenAI API 키 |
| `GEMINI_API_KEY` | O | Gemini AI API 키 |
| `WEB_DOMAIN` | △ | 웹 도메인 (예: growai.co.kr) |
| `LETSENCRYPT_EMAIL` | △ | SSL 인증서 발급 이메일 |
| `KOSIS_CONSUMER_KEY` | △ | KOSIS 통계 API |
| `KOSIS_CONSUMER_SECRET` | △ | KOSIS 통계 API |
| `WORK24_AUTH_KEY` | △ | Work24 채용 API |
| `JOBKOREA_API_KEY` | △ | JobKorea API |
| `JOBKOREA_OEM_CODE` | △ | JobKorea OEM |
| `STATISTICS_AI_API_KEY` | △ | 통계 AI API |
| `KOLLUS_ACCESS_TOKEN` | △ | Kollus 동영상 |
| `KOLLUS_SECURITY_KEY` | △ | Kollus 보안키 |
| `KOLLUS_CHANNEL_KEY` | △ | Kollus 채널키 |
| `KOLLUS_CLIENT_USER_ID` | △ | Kollus 클라이언트 (기본: contentsummary) |
| `HAKSA_ORACLE_URL` | △ | Oracle JDBC URL |
| `HAKSA_ORACLE_USERNAME` | △ | Oracle 사용자 |
| `HAKSA_ORACLE_PASSWORD` | △ | Oracle 비밀번호 |

> O = 필수, △ = 선택 (없으면 해당 기능 비활성)

### C-2. GCP 배포 (deploy-lms-gcp.yml)

| Secret | 설명 |
|--------|------|
| `GCP_SA_KEY` | GCP 서비스 계정 JSON |
| `GCP_PROJECT_ID` | GCP 프로젝트 ID |
| `GCP_VM_SSH_USER` | VM SSH 사용자 (기본: newkl) |
| `FIREBASE_TOKEN` | `firebase login:ci` 토큰 |
| `LMS_DB_PASSWORD` | DB 사용자 비밀번호 |
| `LMS_DB_ROOT_PASSWORD` | DB root 비밀번호 |
| `QDRANT_API_KEY` | Qdrant API 키 |
| `GOOGLE_API_KEY` | Google API 키 |
| `GEMINI_API_KEY` | Gemini API 키 |
| `LETSENCRYPT_EMAIL` | SSL 인증서 이메일 (선택) |

---

## Appendix D: Docker Compose 서비스 명세

### D-1. NCP 운영 (docker-compose-was.yml.tpl)

```yaml
networks:
  lms_net:
    driver: bridge

services:
  qdrant:
    image: qdrant/qdrant:v1.15.3
    container_name: lms-qdrant
    restart: unless-stopped
    environment:
      QDRANT__SERVICE__API_KEY: ${QDRANT_API_KEY}
      QDRANT__SERVICE__GRPC_PORT: 6334
      QDRANT__SERVICE__HTTP_PORT: 6333
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:6333/healthz || exit 1"]
      interval: 30s / timeout: 10s / retries: 3 / start_period: 15s
    deploy:
      resources:
        limits: { memory: 2g, cpus: "1.0" }

  api:
    image: eclipse-temurin:17-jre
    container_name: lms-api
    restart: unless-stopped
    depends_on: { qdrant: { condition: service_healthy } }
    ports: ["0.0.0.0:8081:8081"]
    command: java -Xms2g -Xmx4g -XX:+UseG1GC -jar /app/polytech-lms-api.jar
    env_file: .env
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8081/actuator/health || exit 1"]
      interval: 30s / timeout: 10s / retries: 5 / start_period: 60s
    deploy:
      resources:
        limits: { memory: 6g, cpus: "2.0" }

  resin:
    image: expertsystems/resin:latest
    container_name: lms-resin
    restart: unless-stopped
    depends_on: { api: { condition: service_healthy } }
    ports: ["0.0.0.0:8080:8080"]
    environment:
      POLYTECH_LMS_API_BASE: http://api:8081
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8080/ || exit 1"]
      interval: 30s / timeout: 10s / retries: 5 / start_period: 30s
    deploy:
      resources:
        limits: { memory: 4g, cpus: "2.0" }
```

### D-2. 로컬 개발 (docker-compose.local.yml)

```yaml
services:
  mysql:
    image: mysql:8.0
    ports: ["127.0.0.1:3306:3306"]
    # lower_case_table_names=1 (로컬 호환)

  qdrant:
    image: qdrant/qdrant:v1.15.3
    ports: ["127.0.0.1:6333:6333", "127.0.0.1:6334:6334"]

  resin:
    image: expertsystems/resin:latest
    ports: ["127.0.0.1:8080:8080"]
    volumes:
      - ./public_html:/var/resin/webapps/ROOT
      - ./src:/opt/polytech-lms/legacy/src:ro
```

---

## Appendix E: Nginx 라우팅 룰 전문

**파일**: `tools/ncp/templates/nginx-web.conf.tpl`

### Rate Limiting
```nginx
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=5r/s;
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=30r/s;
```

### Upstream 정의
```nginx
upstream lms_api  { server __WAS_IP__:8081; }
upstream lms_resin { server __WAS_IP__:8080; }
```

### 보안 헤더
```nginx
add_header X-Frame-Options        "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy        "strict-origin-when-cross-origin" always;
add_header X-XSS-Protection       "1; mode=block" always;
```

### 정적 파일 캐시
```nginx
location /common/    { proxy_pass http://lms_resin; expires 7d; }
location /tutor_lms/app/assets/ { proxy_pass http://lms_resin; expires 30d; }
```

### WebSocket (livesession)
```nginx
location /livesession/ {
    proxy_pass http://lms_api;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

### Actuator 접근 제한
```nginx
location /actuator/ {
    allow 192.168.0.0/16;
    allow 127.0.0.1;
    deny all;
    proxy_pass http://lms_api;
}
```

---

## Appendix F: Spring Boot API 컨트롤러 전체 목록

| # | 컨트롤러 | 경로 | 주요 엔드포인트 |
|---|---------|------|----------------|
| 1 | `TutorContentRecommendController` | `/tutor/content-recommend` | `POST /lessons` |
| 2 | `StudentContentRecommendController` | `/student/content-recommend` | `POST /home`, `POST /search`, `POST /home/more` |
| 3 | `JobController` | `/job` | `GET /recruits`, `GET /recruits/nl`, `POST /recruits/refresh` |
| 4 | `ContentSummaryController` | `/contentsummary` | `POST /admin/transcribe`, `POST /webhooks/kollus` |
| 5 | `StatisticsDashboardApiController` | `/statistics/api` | `GET /meta/*`, `GET /industry/*`, `GET /population/*` |
| 6 | `StatisticsDashboardPageController` | `/statistics` | `GET /dashboard` (HTML) |
| 7 | `KosisStatisticsController` | `/statistics/kosis` | `GET /population` |
| 8 | `StatisticsAiV2ApiController` | `/statistics/api/ai/v2` | `GET /catalog` |
| 9 | `StatisticsAiV2DataStoreApiController` | `/statistics/api/ai/v2/datastore` | 데이터 스토어 |
| 10 | `GlobalHealthController` | `/global` | 헬스 체크 |
| 11 | `VectorIndexController` | `/global/vector/index` | `POST /lessons` |
| 12 | `HaksaHealthController` | `/haksa/health` | `GET /` (Oracle 뷰 카운트) |
| 13 | `LiveSessionController` | `/livesession` | WebSocket |
| 14 | `AntiFraudController` | `/antifraud` | 부정행위 방지 |
| 15 | `RecoContentAdminController` | `/reco-contents` | 추천 관리 |

### 관리자 토큰 인증 엔드포인트

| 엔드포인트 | 헤더 | 환경변수 |
|-----------|------|---------|
| `POST /job/recruits/refresh` | `X-Job-Admin-Token` | `JOB_ADMIN_TOKEN` |
| `POST /job/codes/refresh` | `X-Job-Admin-Token` | `JOB_ADMIN_TOKEN` |
| `POST /contentsummary/admin/transcribe` | `X-ContentSummary-Admin-Token` | `CONTENTSUMMARY_ADMIN_TOKEN` |
| `POST /contentsummary/admin/enqueue/backfill` | `X-ContentSummary-Admin-Token` | `CONTENTSUMMARY_ADMIN_TOKEN` |

---

> **문서 끝**
> 본 문서는 `polytech-lms` 프로젝트의 전체 시스템 구성, 배포 파이프라인, 운영 절차를 포함합니다.
> 인수인계 시 이 문서와 함께 GitHub Secrets, SSH 키, API 키를 별도로 전달해야 합니다.
