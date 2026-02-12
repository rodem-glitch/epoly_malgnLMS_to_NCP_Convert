# GCP/Firebase 원클릭 자동 셋업

왜 필요한가요?
- 사용자님이 콘솔에서 수십 개 설정을 직접 만지면, 실수 확률이 높고 시간이 오래 걸립니다.
- 그래서 이 스크립트는 `Firebase 짧은 링크` + `VM(Resin JSP + Spring API + MySQL + Qdrant)` 구조를 한 번에 자동으로 만듭니다.

## 실행 방법(클릭 중심)
1. `tools/gcp/start-one-click.bat` 더블클릭
2. 브라우저 로그인 창이 뜨면 `gcloud`/`firebase` 승인
3. 끝나면 `tools/gcp/generated/setup-summary.txt` 확인

## 이 스크립트가 자동으로 하는 일
- GCP 프로젝트 확인/생성 + 결제연결(가능한 경우)
- Compute Engine VM 생성 + 고정 IP 할당
- 웹 방화벽 규칙(80/443) 생성
- `polytech-lms-api` JAR 빌드
- VM에 Docker 스택(MySQL/Qdrant/Spring API/Resin JSP) 배포
- 레거시 `public_html` + `src`를 VM 번들에 포함하고, VM용 `resin-web.xml`을 자동 생성
- 저장소 루트 `통계/` 폴더를 VM 번들에 포함하고 API 컨테이너 `/data/statistics`로 마운트
- (옵션) 기존 DB dump 자동 import
- Nginx 리버스 프록시 설정
  - 기본 화면(`/`, `/mypage/*`, `/member/*`, `/tutor_lms/*`)은 Resin
  - 통계/추천/채용 API 경로(`/statistics/*`, `/student/*`, `/tutor/*`, `/job/*` 등)는 Spring API
- Firebase Hosting 사이트 생성/배포
  - 기본값은 `*.web.app -> VM` 302 리다이렉트(초기 진입 링크 용도)

## 사용자님이 마지막으로 클릭할 것
- DNS 연결
  - `api` 도메인 A 레코드 -> VM 고정 IP
  - `www` 도메인(선택) -> Firebase Hosting 도메인 연결 안내값

## 옵션 예시
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gcp/one-click-setup.ps1 `
  -ProjectId my-polytech-prod `
  -ApiDomain api.my-domain.com `
  -WwwDomain epoly-kopo.web.app `
  -FirebaseSite epoly-kopo `
  -VmName polytech-lms-prod `
  -GoogleApiKey "YOUR_GOOGLE_API_KEY"
```

## Firebase를 짧은 링크로만 쓰는 권장 예시
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gcp/one-click-setup.ps1 `
  -ProjectId gen-lang-client-0343478566 `
  -ApiDomain api.example.com `
  -WwwDomain epoly-kopo.web.app `
  -FirebaseSite epoly-kopo `
  -DbPassword "YOUR_DB_PASSWORD" `
  -DbRootPassword "YOUR_DB_ROOT_PASSWORD" `
  -QdrantApiKey "YOUR_QDRANT_API_KEY" `
  -GoogleApiKey "YOUR_GOOGLE_API_KEY"
```

## Firebase URL을 그대로 유지하는 모드(권장)
왜 필요한가요?
- 302 리다이렉트 방식은 주소창이 `34.64.x.x`로 바뀝니다.
- `https://epoly-kopo.web.app/mypage/...` 형태를 그대로 유지하려면, Hosting rewrite + Functions 프록시가 필요합니다.

현재 적용 경로:
- 배포 폴더: `tools/gcp/firebase-proxy-deploy`
- 함수명: `vmproxy` (region: `asia-northeast3`)
- Hosting 사이트: `epoly-kopo`

배포 명령:
```powershell
cd tools/gcp/firebase-proxy-deploy
firebase deploy --project gen-lang-client-0343478566 --only functions,hosting
```

## DB 이관까지 원클릭으로 실행하는 방법
1. 기존 dump 파일이 이미 있으면(권장)
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gcp/one-click-setup.ps1 `
  -ProjectId my-polytech-prod `
  -ApiDomain api.example.com `
  -EnableDbMigration `
  -SourceDbDumpPath "C:\backup\lms.sql" `
  -GoogleApiKey "YOUR_GOOGLE_API_KEY"
```

2. dump 파일이 없으면, 소스 DB에서 자동 덤프 생성 후 이관
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gcp/one-click-setup.ps1 `
  -ProjectId my-polytech-prod `
  -ApiDomain api.example.com `
  -EnableDbMigration `
  -SourceDbHost "192.168.0.55" `
  -SourceDbPort 3306 `
  -SourceDbName "lms" `
  -SourceDbUser "lms" `
  -SourceDbPassword "비밀번호" `
  -GoogleApiKey "YOUR_GOOGLE_API_KEY"
```

참고:
- 자동 덤프 방식은 로컬에 `mysqldump`가 설치되어 있어야 합니다.
- DB import는 `DB_IMPORT_ON_DEPLOY=true`로 VM 배포 단계에서 자동 실행됩니다.
- 소스 DB 계정에 `PROCESS` 권한이 없으면 `mysqldump --no-tablespaces`가 필요합니다(스크립트 반영됨).
- CI(GitHub Actions)에서 결제/프로젝트 API 권한이 부족하면 `-SkipProjectBootstrap` 옵션을 사용합니다.

## GitHub Actions 자동 연동
- 워크플로우 파일: `.github/workflows/deploy-lms-gcp.yml`
- 트리거: `main` 브랜치 push 또는 수동 실행
- 동작:
  - 원클릭 스크립트 실행으로 VM(Resin+API+MySQL+Qdrant) 배포
  - Firebase `vmproxy` 함수 + Hosting 동시 배포
  - 배포 후 `epoly-kopo.web.app` 스모크 테스트 수행

필수 GitHub Secrets:
- `GCP_SA_KEY` : GCP 서비스계정 JSON
- `GCP_PROJECT_ID` : 프로젝트 ID
- `GCP_VM_SSH_USER` : VM SSH 사용자(미설정 시 기본값 `newkl`)
- `FIREBASE_TOKEN` : `firebase login:ci` 토큰
- `LMS_DB_PASSWORD` : DB 사용자 비밀번호
- `LMS_DB_ROOT_PASSWORD` : DB root 비밀번호
- `QDRANT_API_KEY` : Qdrant API 키
- `GOOGLE_API_KEY` : Gemini/임베딩 API 키
- `GEMINI_API_KEY` : Gemini API 키(없으면 Google API 키와 동일 값 권장)
- `LETSENCRYPT_EMAIL` : SSL 인증서 발급 이메일(선택)

## 주의
- `tools/gcp/generated/`에는 민감정보가 저장됩니다. 외부 공유 금지입니다.
- DB 이관을 켜면 대상 MySQL에 데이터가 반영되므로, 빈 DB 또는 테스트 VM에서 먼저 검증해 주세요.
- 최초 실행은 VM 생성/빌드 때문에 시간이 걸릴 수 있습니다.
- VM Resin은 `public_html/WEB-INF/classes`를 기본으로 사용하되, 누락 클래스는 `src`를 기준으로 동적 컴파일합니다.
- MySQL은 Linux 대소문자 이슈를 피하려고 `--lower_case_table_names=1`로 동작합니다(소스 DB와 테이블명 호환).
