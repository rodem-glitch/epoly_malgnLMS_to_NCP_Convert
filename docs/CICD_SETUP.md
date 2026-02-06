# GrowAILMS GCP CI/CD 설정 가이드

## 개요

이 문서는 GrowAILMS API를 GCP Cloud Run에 자동 배포하기 위한 CI/CD 파이프라인 설정 방법을 설명합니다.

## 아키텍처

```
GitHub Repository
       │
       ▼
GitHub Actions (CI/CD)
       │
       ├── Build (Gradle)
       ├── Test
       ├── Security Scan (Trivy)
       │
       ▼
GCP Artifact Registry
       │
       ▼
GCP Cloud Run (배포)
```

## 사전 요구사항

### 1. GCP 프로젝트 설정

```bash
# GCP 프로젝트 ID 설정
export PROJECT_ID="your-project-id"
export REGION="asia-northeast3"

# 필요한 API 활성화
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  secretmanager.googleapis.com
```

### 2. Artifact Registry 저장소 생성

```bash
# Docker 저장소 생성
gcloud artifacts repositories create growailms \
  --repository-format=docker \
  --location=$REGION \
  --description="GrowAILMS Docker images"
```

### 3. 서비스 계정 생성 및 권한 설정

```bash
# 서비스 계정 생성
gcloud iam service-accounts create github-actions-sa \
  --display-name="GitHub Actions Service Account"

# 필요한 역할 부여
SA_EMAIL="github-actions-sa@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/iam.serviceAccountUser"

# 서비스 계정 키 생성
gcloud iam service-accounts keys create gcp-sa-key.json \
  --iam-account=$SA_EMAIL
```

### 4. GitHub Secrets 설정

GitHub Repository > Settings > Secrets and variables > Actions에서 다음 시크릿 추가:

| Secret Name | 설명 | 값 |
|-------------|------|-----|
| `GCP_PROJECT_ID` | GCP 프로젝트 ID | `your-project-id` |
| `GCP_SA_KEY` | 서비스 계정 JSON 키 | `gcp-sa-key.json` 파일 내용 |
| `GCP_REGION` | 배포 리전 | `asia-northeast3` |

```bash
# JSON 키를 GitHub Secret에 설정할 값으로 변환
cat gcp-sa-key.json | base64
```

### 5. GitHub Variables 설정 (선택)

| Variable Name | 설명 | 기본값 |
|---------------|------|--------|
| `CLOUD_RUN_SERVICE` | Cloud Run 서비스 이름 | `growailms-api` |

## 배포 트리거

### 자동 배포
- `main` 브랜치에 push
- `feature/securecoding_backend` 브랜치에 push
- `growailms-api/` 경로의 파일 변경 시

### 수동 배포
GitHub Actions > GCP Cloud Run Deploy > Run workflow

## 파일 구조

```
GrowAILMS/
├── .github/
│   └── workflows/
│       └── gcp-deploy.yml      # GitHub Actions 워크플로우
├── growailms-api/
│   ├── Dockerfile              # 컨테이너 빌드 파일
│   ├── .dockerignore           # Docker 빌드 제외 파일
│   ├── cloudbuild.yaml         # Cloud Build 설정 (대안)
│   └── ...
└── docs/
    └── CICD_SETUP.md           # 이 문서
```

## 환경별 배포

### Staging 환경
```bash
# 수동 배포 시 staging 선택
gh workflow run gcp-deploy.yml -f environment=staging
```

### Production 환경
```bash
# 수동 배포 시 production 선택
gh workflow run gcp-deploy.yml -f environment=production
```

## Cloud Build 사용 (대안)

GitHub Actions 대신 GCP Cloud Build를 사용할 경우:

```bash
# Cloud Build 트리거 생성
gcloud builds triggers create github \
  --repo-name=GrowAILMS \
  --repo-owner=sh-jang-code \
  --branch-pattern="^main$" \
  --build-config=growailms-api/cloudbuild.yaml

# 수동 빌드 실행
cd growailms-api
gcloud builds submit --config=cloudbuild.yaml
```

## 모니터링

### Cloud Run 로그 확인
```bash
gcloud run services logs read growailms-api --region=$REGION --limit=100
```

### 서비스 상태 확인
```bash
gcloud run services describe growailms-api --region=$REGION
```

## 롤백

### 이전 리비전으로 롤백
```bash
# 리비전 목록 확인
gcloud run revisions list --service=growailms-api --region=$REGION

# 특정 리비전으로 트래픽 전환
gcloud run services update-traffic growailms-api \
  --region=$REGION \
  --to-revisions=growailms-api-00001-abc=100
```

## 비용 최적화

현재 설정된 Cloud Run 옵션:
- `min-instances=0`: 트래픽이 없을 때 비용 절감
- `max-instances=10`: 최대 인스턴스 제한
- `cpu-throttling`: CPU 비사용 시 스로틀링

## 트러블슈팅

### 빌드 실패
1. Gradle 버전 확인
2. JDK 버전 확인 (17 필요)
3. 의존성 충돌 확인

### 배포 실패
1. 서비스 계정 권한 확인
2. Artifact Registry 접근 권한 확인
3. Cloud Run 할당량 확인

### 컨테이너 시작 실패
1. 환경 변수 설정 확인
2. 메모리 할당 확인 (최소 512Mi 권장)
3. 포트 설정 확인 (8080)

## 보안 권장사항

1. **Secrets 관리**: GCP Secret Manager 사용 권장
2. **네트워크**: VPC Connector로 내부 서비스 접근
3. **인증**: Cloud IAM 또는 Identity-Aware Proxy 사용
4. **스캐닝**: Container Analysis API로 이미지 취약점 스캔

## 참고 자료

- [Cloud Run 문서](https://cloud.google.com/run/docs)
- [GitHub Actions for GCP](https://github.com/google-github-actions)
- [Artifact Registry 문서](https://cloud.google.com/artifact-registry/docs)
