# GrowAILMS Legacy 배포 가이드

## 개요

Legacy 시스템(구 e-poly LMS)을 GCP Cloud Run에 배포하기 위한 가이드입니다.

## 기술 스택

- **Backend**: Java 8, JSP, Servlet 2.4
- **Framework**: Malgnsoft 1.13.0
- **WAS**: Apache Tomcat 9
- **Database**: (기존 DB 연결 유지)
- **Infrastructure**: Docker, GCP Cloud Run, GitHub Actions

## 디렉토리 구조

```
GrowAILMS/
├── src/                        # Legacy Java 소스 코드
│   ├── dao/                    # DAO 클래스 (180개)
│   └── malgnsoft/              # Malgnsoft 프레임워크
├── public_html/                # Legacy 웹 루트
│   ├── WEB-INF/
│   │   ├── web.xml            # 서블릿 설정
│   │   └── lib/               # JAR 라이브러리 (45개)
│   │       └── malgn-1.13.0.jar
│   ├── init.jsp               # 프론트 초기화
│   ├── sysop/                 # 관리자 페이지
│   ├── html/                  # HTML 템플릿
│   └── api/                   # API JSP
├── Dockerfile.legacy           # Docker 빌드 파일
└── .github/workflows/
    └── legacy-deploy.yml      # CI/CD 파이프라인
```

## 배포 파일 구성

### 1. Dockerfile.legacy

Multi-stage 빌드 구조:
- **Stage 1 (Build)**: Java 소스 컴파일
  - Maven 3.8 + OpenJDK 8
  - 180개 Java 파일 → .class 파일 생성
  - 출력: WEB-INF/classes/

- **Stage 2 (Runtime)**: Tomcat 서버
  - Tomcat 9 + JDK 8
  - public_html → webapps/ROOT
  - 컴파일된 클래스 복사
  - 포트: 8080

### 2. GitHub Actions 워크플로우

`.github/workflows/legacy-deploy.yml`:

| Job | 설명 | 주요 작업 |
|-----|------|----------|
| build | 빌드 및 테스트 | Docker 이미지 빌드, 로컬 테스트 |
| security-scan | 보안 스캔 | Trivy 취약점 검사 |
| deploy | Cloud Run 배포 | Artifact Registry 푸시, Cloud Run 배포 |
| notify | 결과 알림 | 배포 성공/실패 알림 |

**트리거 조건**:
- `src/**`, `public_html/**` 변경 시
- `Dockerfile.legacy` 변경 시
- main, dev 브랜치 push
- 수동 실행 (workflow_dispatch)

### 3. Cloud Run 설정

| 설정 | 값 | 설명 |
|------|---|------|
| Service Name | growailms-legacy | Legacy 서비스명 |
| Region | asia-northeast1 | 도쿄 리전 (도메인 매핑 지원) |
| CPU | 2 | Legacy는 리소스 많이 사용 |
| Memory | 2Gi | JSP 컴파일, 세션 관리 |
| Min Instances | 0 | 비용 절감 (콜드 스타트 허용) |
| Max Instances | 10 | 오토스케일링 |
| Concurrency | 80 | 동시 요청 처리 |
| Timeout | 300s | 5분 타임아웃 |

## 배포 방법

### 방법 1: GitHub Actions (자동 배포)

1. 코드 변경 후 커밋:
```bash
cd D:\Dev\GrowAILMS
git add src/ public_html/
git commit -m "feat: update legacy service"
git push origin main
```

2. GitHub Actions 자동 실행:
   - https://github.com/sh-jang-code/GrowAILMS/actions
   - "Legacy Cloud Run Deploy" 워크플로우 확인

### 방법 2: 수동 실행

1. GitHub Actions 페이지 이동
2. "Legacy Cloud Run Deploy" 선택
3. "Run workflow" 버튼 클릭
4. 브랜치 선택 (main 또는 dev)
5. Environment 선택 (staging/production)
6. "Run workflow" 실행

### 방법 3: 로컬 Docker 빌드

```bash
# 1. Docker 이미지 빌드
cd D:\Dev\GrowAILMS
docker build -f Dockerfile.legacy -t growailms-legacy:local .

# 2. 로컬 실행
docker run -d -p 8080:8080 --name legacy-test growailms-legacy:local

# 3. 테스트
curl http://localhost:8080/

# 4. 로그 확인
docker logs legacy-test

# 5. 종료
docker stop legacy-test
docker rm legacy-test
```

## 서비스 구분

| 서비스 | 경로 | 기술 스택 | 용도 | Cloud Run Service |
|--------|------|----------|------|-------------------|
| **Legacy** | `src/`, `public_html/` | Java 8, JSP, Malgnsoft | 구 e-poly LMS | growailms-legacy |
| **Backend API** | `growailms-api/` | Java 17, Spring Boot | 신규 API | growailms-api |
| **Frontend** | `project/` | React, TypeScript | 교수자 LMS UI | growailms-frontend |

## 도메인 매핑 (선택 사항)

Legacy 서비스에도 커스텀 도메인을 연결할 수 있습니다:

```bash
# Legacy 도메인 매핑 생성
gcloud run domain-mappings create \
  --service=growailms-legacy \
  --domain=legacy.growai.co.kr \
  --region=asia-northeast1

# DNS 설정 (CNAME)
# legacy.growai.co.kr → ghs.googlehosted.com
```

## 배포 확인

### 1. GitHub Actions 로그 확인
```
✅ Build Legacy - 성공
✅ Security Scan - 성공
✅ Deploy to Cloud Run - 성공
✅ Notify Result - 배포 완료
```

### 2. Cloud Run 서비스 확인
```bash
gcloud run services describe growailms-legacy --region=asia-northeast1
```

### 3. 서비스 접속 테스트
```bash
# Cloud Run URL 확인
gcloud run services list --region=asia-northeast1

# 헬스체크
curl https://growailms-legacy-xxxxx-an.a.run.app/
```

### 4. 로그 확인
```bash
# 최근 로그 조회
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=growailms-legacy" --limit=50

# 실시간 로그 스트리밍
gcloud alpha run services logs tail growailms-legacy --region=asia-northeast1
```

## 트러블슈팅

### 문제 1: Java 컴파일 실패
**증상**: Docker 빌드 중 "cannot find symbol" 에러

**원인**:
- Malgnsoft JAR가 누락됨
- 소스 코드에 오류가 있음

**해결**:
```bash
# WEB-INF/lib 확인
ls -la public_html/WEB-INF/lib/malgn-1.13.0.jar

# Dockerfile.legacy에서 라이브러리 경로 확인
```

### 문제 2: Tomcat 시작 실패
**증상**: Container가 시작 직후 종료됨

**원인**:
- web.xml 설정 오류
- 필수 라이브러리 누락
- 메모리 부족

**해결**:
```bash
# 로컬에서 테스트
docker run -it growailms-legacy:test bash
cd /usr/local/tomcat/webapps/ROOT
ls -la WEB-INF/

# Tomcat 로그 확인
docker logs legacy-test
```

### 문제 3: JSP 페이지 404 에러
**증상**: JSP 파일을 찾을 수 없음

**원인**:
- public_html이 제대로 복사되지 않음
- Tomcat webapps 경로 문제

**해결**:
```bash
# Dockerfile.legacy 확인
COPY public_html /usr/local/tomcat/webapps/ROOT/

# 컨테이너 내부 확인
docker exec -it legacy-test ls -la /usr/local/tomcat/webapps/ROOT/
```

### 문제 4: 데이터베이스 연결 실패
**증상**: DB 관련 오류

**원인**:
- DB 연결 정보가 환경변수로 전달되지 않음
- Cloud SQL 연결 설정 필요

**해결**:
1. Cloud SQL Proxy 설정
2. 환경변수 추가:
```yaml
# legacy-deploy.yml에 추가
env:
  - name: DB_HOST
    value: ${{ secrets.DB_HOST }}
  - name: DB_USER
    value: ${{ secrets.DB_USER }}
  - name: DB_PASS
    value: ${{ secrets.DB_PASS }}
```

## 비용 최적화

### 1. 최소 인스턴스 0으로 설정
- 트래픽 없을 때 자동 종료
- 콜드 스타트 허용 (첫 요청 느림)

### 2. CPU/메모리 조정
- 현재: CPU 2, Memory 2Gi
- 트래픽 적을 경우: CPU 1, Memory 1Gi로 축소 가능

### 3. 타임아웃 조정
- 현재: 300s (5분)
- 일반 요청: 60s로 축소 가능

### 4. Artifact Registry 이미지 정리
```bash
# 30일 이상 오래된 이미지 삭제
gcloud artifacts docker images list \
  asia-northeast1-docker.pkg.dev/gen-lang-client-0725900816/growailms/growailms-legacy

gcloud artifacts docker images delete \
  asia-northeast1-docker.pkg.dev/gen-lang-client-0725900816/growailms/growailms-legacy:OLD_SHA
```

## 성능 모니터링

### Cloud Run 메트릭
- Request count: 요청 수
- Request latency: 응답 시간
- Container CPU utilization: CPU 사용률
- Container memory utilization: 메모리 사용률
- Billable container instance time: 과금 시간

### 최적화 체크리스트
- [ ] CPU/메모리 사용률 70% 이하 유지
- [ ] P95 latency 2초 이하
- [ ] 콜드 스타트 5초 이하
- [ ] 에러율 1% 이하

## 참고 자료

- [Google Cloud Run 문서](https://cloud.google.com/run/docs)
- [Tomcat 9 문서](https://tomcat.apache.org/tomcat-9.0-doc/)
- [Dockerfile Best Practices](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [GitHub Actions 문서](https://docs.github.com/en/actions)
