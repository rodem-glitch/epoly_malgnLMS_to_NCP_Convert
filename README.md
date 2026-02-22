# epoly_malgnLMS_to_NCP_Convert

> MalgnLMS(GrowAILMS) GCP Cloud Run 서비스를 NCP(Naver Cloud Platform)로 전환하기 위한 프로젝트

## 원본 저장소

- **Source**: [sh-jang-code/MalgnLMS (dev branch)](https://github.com/sh-jang-code/MalgnLMS/tree/dev)
- **CI/CD**: [GitHub Actions](https://github.com/sh-jang-code/MalgnLMS/actions)

---

## GCP Cloud Run 현황 (as-is)

### 서비스 구성 (리전: `asia-northeast1`)

| 서비스 | 워크플로우 | 트리거 경로 | 트리거 브랜치 | 스택 | 리소스 |
|--------|-----------|------------|-------------|------|--------|
| **growailms-frontend** | `frontend-deploy.yml` | `project/**` | main, feature/securecoding_backend | React 18 + Vite + Nginx | CPU 1 / Mem 512Mi / max 5 |
| **growailms-api** | `gcp-deploy.yml` | `growailms-api/**` | main, feature/securecoding_backend | Spring Boot 3.2 (Java 17) | CPU 1 / Mem 1Gi / max 10 |
| **growailms-backend** (Legacy) | `backend-deploy.yml` | `growailms-backend/**` | main, develop | eGovFrame (Java 8) + Tomcat 9 | CPU 2 / Mem 2Gi / max 10 |

### 배포 파이프라인 (공통)

```
Code Push → Build & Test → Security Scan (Trivy) → Docker Build
→ Artifact Registry Push → Cloud Run Deploy → Health Check → Notify
```

- 이미지 저장소: `asia-northeast1-docker.pkg.dev/{PROJECT_ID}/growailms/`
- 인증: GitHub Secrets (`GCP_SA_KEY`, `GCP_PROJECT_ID`)
- 모든 서비스 `--allow-unauthenticated`, `gen2` 실행 환경

### 도메인 매핑

| 도메인 | 서비스 |
|--------|--------|
| growai.co.kr / www.growai.co.kr | growailms-frontend |
| api.growai.co.kr | growailms-api |
| legacy.growai.co.kr (선택) | growailms-legacy |

### 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    GrowAILMS System (GCP)                    │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Legacy     │  │  Backend API │  │  Frontend    │      │
│  │  (구 시스템)  │  │  (신규 API)   │  │ (교수자 LMS)  │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                  │              │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌──────▼───────┐      │
│  │ Cloud Run    │  │ Cloud Run    │  │ Cloud Run    │      │
│  │ (Tomcat 9)   │  │ (Spring Boot)│  │ (Nginx/React)│      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         └─────────────────┼──────────────────┘              │
│                           ▼                                 │
│                  ┌─────────────────┐                        │
│                  │   Cloud SQL     │                        │
│                  │    (MySQL)      │                        │
│                  └─────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 워크플로우 관련 주요 커밋 이력

| 커밋 | 내용 |
|------|------|
| `8443acf` | Phase 1~4: Legacy migration complete |
| `4fc0a75` | MalgnLMS → GrowAILMS 리브랜딩 완료 |
| `64669c2` | Legacy 서비스 배포 파이프라인 추가 |
| `1109423` | CI 환경 테스트 단계 임시 비활성화 |
| `f99da21` | 배포 리전을 `asia-northeast1`로 변경 (도메인 매핑 지원) |
| `e23d150` | 프론트엔드 Cloud Run 배포 설정 추가 |
| `a89aa42` | GCP Cloud Run CI/CD 파이프라인 최초 추가 |

### 월 예상 비용 (GCP)

| 서비스 | CPU | Memory | 월 비용 (예상) |
|--------|-----|--------|---------------|
| growailms-legacy | 2 core | 2 Gi | $30-50 |
| growailms-api | 1 core | 1 Gi | $15-25 |
| growailms-frontend | 1 core | 512 Mi | $10-15 |
| Artifact Registry + LB + etc | - | - | ~$25 |
| **합계** | - | - | **$80-120** |

---

## Firebase Hosting 현황 (as-is) - polytech-lms

### 서비스 기본 정보

| 항목 | 내용 |
|------|------|
| **URL** | https://epoly-kopo.web.app |
| **호스팅** | Firebase Hosting |
| **서비스명** | 한국폴리텍대학 학습관리시스템 |
| **브랜드** | PLISM - 미래형 직업교육 플랫폼 |
| **원본 저장소** | [sh-jang-code/polytech-lms](https://github.com/sh-jang-code/polytech-lms) |
| **배포 워크플로우** | `deploy-lms-gcp.yml` |
| **상태** | 정상 운영 중 |

### 기술 스택

| 구분 | 내용 |
|------|------|
| **프론트엔드** | 순수 JavaScript + JSP (SPA 프레임워크 미사용) |
| **UI** | Bootstrap Icons, CSS Grid/Flexbox |
| **반응형** | 992px / 576px 브레이크포인트 |
| **영상 플랫폼** | Kollus 비디오 플레이어 + YouTube Shorts 연동 |
| **외부 연동** | 교보문고 / YES24 전자도서관 |

### 주요 기능

| 기능 | 설명 |
|------|------|
| 추천 동영상 | 중독예방, 성폭력예방 등 교양 영상 |
| 강의 | 재료공학, 모션그래픽, 패션테크, 3D모델링 등 |
| 숏폼 | YouTube 통합 영상 콘텐츠 |
| 계속 학습하기 | 진행 중인 과정 추적 |
| AI 추천 프롬프트 | 개인화 추천 영상 기능 |

### 주요 API 엔드포인트

```
/api/youtube_shorts.jsp?maxResults=6
/mypage/new_main/reco_prompt.jsp (GET/POST)
/mypage/new_main/reco_video_list.jsp
/kollus/preview.jsp
```

### 인증

- 모달 기반 로그인 (아이디/비밀번호)
- 테스트 계정: `kopo_st01` / `Growai!2026`

### 배포 워크플로우 상세 (deploy-lms-gcp.yml)

| 항목 | 값 |
|------|-----|
| **워크플로우명** | Deploy LMS to GCP + Firebase |
| **트리거** | `main` 브랜치 push + workflow_dispatch (수동) |
| **타임아웃** | 90분 |
| **VM 이름** | `polytech-lms-vm` |
| **VM Zone** | `asia-northeast3-a` (서울) |
| **Region** | `asia-northeast3` |
| **고정 IP** | `polytech-lms-vm-ip` |
| **Firebase Site** | `epoly-kopo` |
| **Firebase URL** | https://epoly-kopo.web.app |
| **API 도메인** | `api.example.com` |

### 필수 GitHub Secrets

| 시크릿 | 용도 |
|--------|------|
| `GCP_SA_KEY` | GCP 서비스 계정 키 |
| `GCP_PROJECT_ID` | GCP 프로젝트 ID |
| `FIREBASE_TOKEN` | Firebase 배포 토큰 (없으면 ADC 사용) |
| `LMS_DB_PASSWORD` | DB 비밀번호 |
| `LMS_DB_ROOT_PASSWORD` | DB root 비밀번호 |
| `QDRANT_API_KEY` | Qdrant 벡터DB 키 |
| `GOOGLE_API_KEY` | Google API 키 |
| `GEMINI_API_KEY` | Gemini AI 키 |
| `GCP_VM_SSH_USER` | VM SSH 사용자 (선택) |
| `LETSENCRYPT_EMAIL` | SSL 인증서 이메일 |

### 배포 파이프라인 흐름

```
1. 체크아웃 + 시크릿 사전 점검
2. Node 20 + Java 17 설정
3. GCP 인증 (SA Key)
4. VM SSH 권한 사전 점검 (후보: secret → gcloud계정 → newkl → ubuntu → root)
5. 원클릭 배포 실행 (one-click-setup.ps1 → VM에 Docker 스택 배포)
6. 실패 시 진단 (JAR 존재, SSH/sudo 재확인, 원격 스크립트 존재)
7. VM IP 동기화 → Firebase proxy 함수의 TARGET URL 갱신
8. Firebase vmproxy 함수 + Hosting 배포
9. 스모크 테스트 (https://epoly-kopo.web.app/actuator/health + /)
```

### 아키텍처 (VM + Firebase Proxy)

```
사용자 → epoly-kopo.web.app (Firebase Hosting)
         → Firebase Functions (vmproxy)
           → GCP VM (polytech-lms-vm, asia-northeast3-a)
             → Docker 스택 (Spring Boot API + DB + Qdrant)
```

---

## NCP 전환 환경 (to-be)

### GCP → NCP 서비스 매핑

| GCP 서비스 | NCP 대응 서비스 | 비고 |
|-----------|----------------|------|
| Cloud Run (Frontend) | NCP Server + Nginx | React 정적 파일 서빙 |
| Cloud Run (API) | NCP Server + Docker | Spring Boot 컨테이너 |
| Cloud Run (Legacy) | NCP Server + Tomcat | eGovFrame JSP 서비스 |
| Artifact Registry | NCP Container Registry | Docker 이미지 저장소 |
| Cloud SQL (MySQL) | NCP Cloud DB for MySQL | 관리형 MySQL |
| Cloud Logging | NCP Cloud Log Analytics | 로그 수집/분석 |
| Google-managed SSL | NCP Certificate Manager | SSL/TLS 인증서 |
| Firebase Hosting | NCP Object Storage + CDN+ | 정적 호스팅 + CDN |
| Firebase Functions (vmproxy) | NCP API Gateway | 리버스 프록시 |
| GCP Compute Engine (VM) | NCP Server (VPC) | Docker 스택 호스팅 |
| Qdrant (Docker on VM) | NCP Server 내 Docker Qdrant | 벡터 DB |
| GitHub Actions (CI/CD) | GitHub Actions + NCP API | 배포 파이프라인 유지 |

### NCP 인프라 구성 (예정)

#### MalgnLMS (GrowAILMS) - Cloud Run 대체

| 서비스 | NCP 리소스 | 스펙 | 용도 |
|--------|-----------|------|------|
| **growailms-frontend** | NCP Server (Standard) | vCPU 2 / Mem 4GB | React 18 + Nginx |
| **growailms-api** | NCP Server (Standard) | vCPU 2 / Mem 4GB | Spring Boot 3.2 (Java 17) |
| **growailms-backend** | NCP Server (Standard) | vCPU 4 / Mem 8GB | eGovFrame + Tomcat 9 |
| **DB** | Cloud DB for MySQL | High Availability | MySQL 8.0 |

#### polytech-lms (PLISM) - Firebase + VM 대체

| 서비스 | NCP 리소스 | 스펙 | 용도 |
|--------|-----------|------|------|
| **Web/WAS** | NCP Server (Standard) | vCPU 2 / Mem 4GB | JSP + Spring Boot API |
| **Qdrant** | NCP Server 내 Docker | vCPU 2 / Mem 4GB | 벡터 DB |
| **Static Hosting** | Object Storage + CDN+ | - | 정적 파일 서빙 |
| **DB** | Cloud DB for MySQL | High Availability | MySQL 8.0 (공유 가능) |

### NCP 네트워크 구성

```
┌─────────────────────────────────────────────────────────────────┐
│                      NCP VPC (10.0.0.0/16)                      │
│                                                                 │
│  ┌─── Public Subnet (10.0.1.0/24) ──────────────────────────┐  │
│  │                                                           │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │  │
│  │  │  Frontend   │  │  API Server │  │  Legacy     │      │  │
│  │  │  (Nginx)    │  │ (Spring Boot)│  │  (Tomcat)   │      │  │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘      │  │
│  │         │                │                 │             │  │
│  └─────────┼────────────────┼─────────────────┼─────────────┘  │
│            │                │                 │                 │
│  ┌─── Private Subnet (10.0.2.0/24) ─────────────────────────┐  │
│  │         │                │                 │              │  │
│  │  ┌──────▼──────────────────────────────────▼──────┐       │  │
│  │  │            Cloud DB for MySQL (HA)             │       │  │
│  │  └────────────────────────────────────────────────┘       │  │
│  │                                                           │  │
│  │  ┌─────────────┐  ┌─────────────┐                        │  │
│  │  │ PLISM WAS   │  │  Qdrant     │                        │  │
│  │  │ (JSP+API)   │  │  (Docker)   │                        │  │
│  │  └─────────────┘  └─────────────┘                        │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─── Load Balancer ────────────────────────────────────────┐   │
│  │  growai.co.kr         → Frontend Server                  │   │
│  │  api.growai.co.kr     → API Server                       │   │
│  │  legacy.growai.co.kr  → Legacy Server                    │   │
│  │  epoly-kopo.ncloud.com → PLISM WAS Server                │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─── CDN+ ─────────────────────────────────────────────────┐   │
│  │  Object Storage → 정적 파일 (JS/CSS/이미지)               │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─── Certificate Manager ──────────────────────────────────┐   │
│  │  *.growai.co.kr / epoly-kopo 도메인 SSL 인증서            │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### NCP 배포 파이프라인 (예정)

```
1. Code Push → GitHub
2. GitHub Actions 트리거
3. Build & Test (Gradle / npm)
4. Docker Build → NCP Container Registry Push
5. NCP Server 접속 (SSH / NCP API)
6. Docker Pull & 컨테이너 재시작
7. Health Check
8. CDN+ 캐시 퍼지 (정적 파일 변경 시)
```

### NCP 필수 설정 (예정)

| 항목 | 값 |
|------|-----|
| **Region** | KR (한국) |
| **Zone** | KR-1 또는 KR-2 |
| **VPC** | epoly-vpc |
| **Subnet (Public)** | 10.0.1.0/24 |
| **Subnet (Private)** | 10.0.2.0/24 |
| **ACG (방화벽)** | 80, 443, 8080, 22 (관리용) |
| **Cloud DB** | MySQL 8.0, High Availability |
| **Object Storage** | epoly-static (정적 파일) |
| **CDN+** | Object Storage 연동 |
| **Container Registry** | epoly-registry |

### GitHub Secrets (NCP용 추가 예정)

| 시크릿 | 용도 |
|--------|------|
| `NCP_ACCESS_KEY` | NCP API 인증 Access Key |
| `NCP_SECRET_KEY` | NCP API 인증 Secret Key |
| `NCP_SERVER_IP` | 배포 대상 서버 IP |
| `NCP_SSH_KEY` | 서버 접속 SSH 프라이빗 키 |
| `NCP_CR_ENDPOINT` | Container Registry 엔드포인트 |
| `NCP_DB_HOST` | Cloud DB for MySQL 엔드포인트 |
| `NCP_DB_PASSWORD` | Cloud DB 비밀번호 |

### 월 예상 비용 (NCP)

| 서비스 | 스펙 | 월 비용 (예상) |
|--------|------|---------------|
| NCP Server x3 (Frontend/API/Legacy) | Standard vCPU 2~4 | ~150,000원 |
| NCP Server x1 (PLISM WAS + Qdrant) | Standard vCPU 2 | ~50,000원 |
| Cloud DB for MySQL (HA) | 2vCPU / 4GB | ~100,000원 |
| Object Storage + CDN+ | 트래픽 기반 | ~20,000원 |
| Container Registry | 저장 용량 기반 | ~5,000원 |
| Load Balancer | 트래픽 기반 | ~30,000원 |
| SSL 인증서 | Certificate Manager | 무료 |
| **합계** | | **~355,000원 ($250~270)** |

---

## 프로젝트 구조

### 브랜치 구성 (epoly_malgnLMS_to_NCP_Convert)

| 브랜치 | 원본 | 용도 |
|--------|------|------|
| `feature/init_clone` | sh-jang-code/MalgnLMS (dev) | MalgnLMS 소스 + README |
| `feature/polytech-lms_to_NCP_Web_WAS` | sh-jang-code/polytech-lms (dev) | polytech-lms 소스 |

### 디렉토리 구조 (MalgnLMS)

```
.github/workflows/
├── frontend-deploy.yml     → growailms-frontend (GCP Cloud Run)
├── gcp-deploy.yml          → growailms-api (GCP Cloud Run)
└── backend-deploy.yml      → growailms-backend (GCP Cloud Run)

growailms-api/              → Spring Boot 3.2 백엔드 API
project/                    → React 18 프론트엔드
src/ + public_html/         → Legacy (JSP + eGovFrame)
```

### 디렉토리 구조 (polytech-lms)

```
.github/workflows/
└── deploy-lms-gcp.yml      → Firebase Hosting + GCP VM 배포

polytech-lms-api/           → Spring Boot API
tools/gcp/
├── one-click-setup.ps1     → VM 원클릭 배포 스크립트
└── firebase-proxy-deploy/  → Firebase vmproxy 함수 + Hosting
src/ + public_html/         → Legacy (JSP)
```

---

**© 2026 NEWKL - epoly MalgnLMS to NCP Convert**
