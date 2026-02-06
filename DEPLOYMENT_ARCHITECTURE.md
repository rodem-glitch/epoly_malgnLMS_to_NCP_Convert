# GrowAILMS 배포 아키텍처

## 전체 시스템 구성

### 3개 서비스 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    GrowAILMS System                          │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Legacy     │  │  Backend API │  │  Frontend    │    │
│  │  (구 시스템)  │  │  (신규 API)   │  │ (교수자 LMS)  │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│        │                  │                 │              │
│        ▼                  ▼                 ▼              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Cloud Run    │  │ Cloud Run    │  │ Cloud Run    │    │
│  │ (Tomcat 9)   │  │ (Spring Boot)│  │ (Nginx/React)│    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
│        │                  │                 │              │
│        └──────────────────┴─────────────────┘              │
│                           │                                 │
│                           ▼                                 │
│                  ┌─────────────────┐                       │
│                  │   Cloud SQL     │                       │
│                  │    (MySQL)      │                       │
│                  └─────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

## 서비스 비교

| 항목 | Legacy | Backend API | Frontend |
|------|--------|-------------|----------|
| **경로** | `src/`, `public_html/` | `growailms-api/` | `project/` |
| **언어** | Java 8 | Java 17 | TypeScript |
| **프레임워크** | Malgnsoft, JSP | Spring Boot 3.2 | React 18, Vite |
| **WAS/서버** | Tomcat 9 | Embedded Tomcat | Nginx |
| **ORM** | Custom DataObject | MyBatis, JPA | - |
| **인증** | Session 기반 | JWT | JWT |
| **포트** | 8080 | 8080 | 8080 |
| **CPU** | 2 core | 1 core | 1 core |
| **Memory** | 2 Gi | 1 Gi | 512 Mi |
| **Min Instances** | 0 | 0 | 0 |
| **Max Instances** | 10 | 10 | 5 |
| **상태** | 운영 중 (구 시스템) | 개발 중 (마이그레이션) | 개발 중 (신규 UI) |

## Cloud Run 서비스 목록

### 1. growailms-legacy
- **URL**: https://growailms-legacy-xxxxx-an.a.run.app
- **Domain**: legacy.growai.co.kr (선택 사항)
- **용도**: 구 e-poly LMS 전체 기능
- **사용자**: 모든 사용자 (관리자, 교수, 학생)

### 2. growailms-api
- **URL**: https://growailms-api-xxxxx-an.a.run.app
- **Domain**: api.growai.co.kr
- **용도**: 신규 RESTful API
- **사용자**: Frontend, 외부 시스템

### 3. growailms-frontend
- **URL**: https://growailms-frontend-xxxxx-an.a.run.app
- **Domain**: growai.co.kr, www.growai.co.kr
- **용도**: 교수자 LMS 신규 UI
- **사용자**: 교수자

## 배포 파이프라인

### GitHub Actions Workflows

```
.github/workflows/
├── legacy-deploy.yml       → growailms-legacy
├── gcp-deploy.yml          → growailms-api
└── frontend-deploy.yml     → growailms-frontend
```

### 트리거 조건

| 워크플로우 | 트리거 경로 | 브랜치 |
|-----------|------------|--------|
| **legacy-deploy.yml** | `src/**`, `public_html/**` | main, dev |
| **gcp-deploy.yml** | `growailms-api/**` | main, feature/securecoding_backend |
| **frontend-deploy.yml** | `project/**` | main, feature/securecoding_backend |

### 배포 순서

각 워크플로우는 독립적으로 실행됨:

```
1. Code Push → GitHub
   ↓
2. GitHub Actions 트리거
   ↓
3. Build & Test
   - Legacy: Java 컴파일 → Docker 이미지
   - Backend: Gradle build → Docker 이미지
   - Frontend: npm build → Docker 이미지
   ↓
4. Security Scan (Trivy)
   ↓
5. Docker Push → Artifact Registry
   ↓
6. Deploy → Cloud Run
   ↓
7. Health Check
   ↓
8. Notify Result
```

## 도메인 매핑

### DNS 설정

| 도메인 | 타입 | 값 | 연결 서비스 |
|--------|------|---|-------------|
| growai.co.kr | A | 216.239.32.21, 216.239.34.21, ... | growailms-frontend |
| www.growai.co.kr | CNAME | ghs.googlehosted.com | growailms-frontend |
| api.growai.co.kr | CNAME | ghs.googlehosted.com | growailms-api |
| legacy.growai.co.kr | CNAME | ghs.googlehosted.com | growailms-legacy (선택) |

### 도메인 매핑 생성

```bash
# Frontend (이미 생성됨)
gcloud run domain-mappings create --service=growailms-frontend --domain=growai.co.kr --region=asia-northeast1
gcloud run domain-mappings create --service=growailms-frontend --domain=www.growai.co.kr --region=asia-northeast1

# Backend API (이미 생성됨)
gcloud run domain-mappings create --service=growailms-api --domain=api.growai.co.kr --region=asia-northeast1

# Legacy (선택 사항)
gcloud run domain-mappings create --service=growailms-legacy --domain=legacy.growai.co.kr --region=asia-northeast1
```

## 마이그레이션 전략

### Phase 1: 병렬 운영 (현재)
- Legacy: 모든 기능 제공 (100%)
- Backend API: 일부 기능 마이그레이션 (30%)
- Frontend: 교수자 LMS UI 개발 (50%)

### Phase 2: 점진적 전환
- Legacy: 주요 기능 유지 (80%)
- Backend API: 추가 기능 마이그레이션 (60%)
- Frontend: 교수자 LMS 완성 (80%)
- **사용자**: Frontend → Backend API 사용 시작

### Phase 3: Legacy 축소
- Legacy: 핵심 기능만 유지 (50%)
- Backend API: 대부분 기능 완료 (90%)
- Frontend: 전체 기능 완성 (100%)
- **사용자**: 대부분 Frontend 사용

### Phase 4: Legacy 종료
- Legacy: 종료 (0%)
- Backend API: 전체 기능 완료 (100%)
- Frontend: 안정화 (100%)
- **사용자**: 완전 마이그레이션

## 비용 구조

### 월간 예상 비용 (asia-northeast1)

| 서비스 | CPU | Memory | 평균 인스턴스 | 월 비용 (예상) |
|--------|-----|--------|--------------|---------------|
| growailms-legacy | 2 core | 2 Gi | 1 | $30-50 |
| growailms-api | 1 core | 1 Gi | 0.5 | $15-25 |
| growailms-frontend | 1 core | 512 Mi | 0.5 | $10-15 |
| **합계** | - | - | - | **$55-90** |

**추가 비용**:
- Artifact Registry: ~$5/월
- Cloud SQL (별도): 인스턴스 타입에 따라
- 로드밸런서 (도메인 매핑): ~$18/월
- 네트워크 송신: 트래픽에 따라

**총 예상 비용**: **$80-120/월**

### 비용 절감 팁

1. **최소 인스턴스 0 유지** (콜드 스타트 허용)
2. **트래픽 적은 서비스**: CPU/Memory 축소
3. **오래된 Docker 이미지 삭제** (Artifact Registry)
4. **로그 보관 기간 단축** (Cloud Logging: 30일 → 7일)
5. **개발/스테이징 환경 분리** (필요시만 실행)

## 모니터링 & 알림

### Cloud Run 메트릭

| 메트릭 | 임계값 | 알림 |
|--------|--------|------|
| Request latency (P95) | > 2s | Slack 알림 |
| Error rate | > 5% | Email 알림 |
| CPU utilization | > 80% | 스케일 업 |
| Memory utilization | > 80% | 스케일 업 |

### 로그 확인

```bash
# Legacy 로그
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=growailms-legacy" --limit=50

# Backend API 로그
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=growailms-api" --limit=50

# Frontend 로그
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=growailms-frontend" --limit=50
```

## 보안

### 인증/인가

| 서비스 | 인증 방식 | 세션 관리 | API 보안 |
|--------|----------|-----------|----------|
| Legacy | Session Cookie | 서버 세션 (120분) | 세션 기반 |
| Backend API | JWT | Stateless | Bearer Token |
| Frontend | JWT | LocalStorage | Bearer Token |

### 네트워크 보안

- **Cloud Run**: `--allow-unauthenticated` (공개)
- **Cloud SQL**: Private IP, VPC 연결
- **API 키**: GitHub Secrets 관리
- **SSL/TLS**: Google 관리 인증서 (자동)

### 취약점 스캔

- **Trivy**: 모든 워크플로우에서 자동 스캔
- **SARIF 업로드**: GitHub Security 탭에서 확인
- **심각도**: CRITICAL, HIGH만 체크

## 백업 & 복구

### Docker 이미지 버전 관리

```bash
# 특정 버전으로 롤백
gcloud run services update growailms-legacy \
  --image=asia-northeast1-docker.pkg.dev/gen-lang-client-0725900816/growailms/growailms-legacy:PREVIOUS_SHA \
  --region=asia-northeast1
```

### 데이터베이스 백업

```bash
# Cloud SQL 자동 백업 설정 (7일 보관)
gcloud sql instances patch INSTANCE_NAME --backup-start-time=03:00
```

### 재배포

```bash
# GitHub Actions에서 재실행
# 또는 수동 배포
gcloud run deploy growailms-legacy \
  --image=asia-northeast1-docker.pkg.dev/gen-lang-client-0725900816/growailms/growailms-legacy:latest \
  --region=asia-northeast1
```

## 체크리스트

### 배포 전
- [ ] GitHub Secrets 설정 (GCP_PROJECT_ID, GCP_SA_KEY, GCP_REGION)
- [ ] Artifact Registry 저장소 생성 (`growailms`)
- [ ] Cloud SQL 인스턴스 준비
- [ ] DNS 레코드 설정
- [ ] 도메인 소유권 인증

### 배포 후
- [ ] 3개 서비스 모두 정상 실행 확인
- [ ] Health check 통과 확인
- [ ] 도메인 접속 테스트
- [ ] SSL 인증서 발급 확인 (15-60분)
- [ ] 로그 모니터링 설정
- [ ] 비용 알림 설정

### 운영 중
- [ ] 주간 로그 리뷰
- [ ] 월간 비용 리뷰
- [ ] 분기별 보안 스캔
- [ ] 연간 인프라 최적화

## 참고 문서

- [LEGACY_DEPLOYMENT.md](./LEGACY_DEPLOYMENT.md) - Legacy 배포 상세 가이드
- [growailms-api/README.md](./growailms-api/README.md) - Backend API 문서
- [project/README.md](./project/README.md) - Frontend 문서
- [CLEANUP_COMMANDS.txt](D:\WorkSpace\GrowAI-MAP\CLEANUP_COMMANDS.txt) - 리소스 정리 가이드
