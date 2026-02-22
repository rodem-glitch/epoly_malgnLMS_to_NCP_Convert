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
