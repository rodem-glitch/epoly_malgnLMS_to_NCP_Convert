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

## NCP 전환 계획 (to-be)

> TODO: NCP 전환 설계 및 구현 내용 추가 예정

### 전환 대상 매핑 (예정)

| GCP 서비스 | NCP 대응 서비스 |
|-----------|----------------|
| Cloud Run | NCP Container Registry + NKS 또는 Cloud Functions |
| Artifact Registry | NCP Container Registry |
| Cloud SQL (MySQL) | NCP Cloud DB for MySQL |
| Cloud Logging | NCP Cloud Log Analytics |
| Google-managed SSL | NCP Certificate Manager |

---

## 프로젝트 구조

```
.github/workflows/
├── frontend-deploy.yml     → growailms-frontend (GCP)
├── gcp-deploy.yml          → growailms-api (GCP)
└── backend-deploy.yml      → growailms-backend (GCP)

growailms-api/              → Spring Boot 3.2 백엔드 API
project/                    → React 18 프론트엔드
src/ + public_html/         → Legacy (JSP + eGovFrame)
```

---

**© 2026 NEWKL - epoly MalgnLMS to NCP Convert**
