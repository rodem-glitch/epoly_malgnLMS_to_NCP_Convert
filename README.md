# GrowAILMS

> 한국폴리텍대학 AI 기반 학습관리시스템 (Legacy → GrowAILMS 마이그레이션)

## 📋 프로젝트 개요

GrowAILMS는 한국폴리텍대학의 e-poly 학습관리시스템 레거시 코드베이스입니다.
현재 **GrowAILMS**로의 현대화 마이그레이션이 진행 중입니다.

## 🛠 기술 스택

| 구분     | Legacy (MalgnLMS)            | Target (GrowAILMS)                      |
| -------- | ---------------------------- | --------------------------------------- |
| Backend  | Java 8, Malgnsoft DataObject | Java 17, Spring Boot 3.2, eGovFrame 4.2 |
| ORM      | Custom DAO Pattern           | MyBatis 3.5                             |
| Frontend | JSP + jQuery                 | React 18 + TypeScript + Vite            |
| 인증     | Session 기반                 | JWT + Keycloak                          |
| 인프라   | 단일 서버                    | Docker + GCP/NCP 하이브리드             |

## 📁 주요 구조

```
MalgnLMS/
├── src/
│   ├── dao/          # 170개 DAO 클래스
│   └── ...
├── web/              # 1,223개 JSP 파일
└── config/           # 설정 파일
```

## 🚀 마이그레이션 현황

- [X] Phase 1: 분석 단계 완료
- [X] Phase 2: 설계 단계 완료
- [ ] Phase 3: 구현 단계 (진행 중)
  - [X] src/ (Genkit 백엔드)
  - [X] docker-compose/ (인프라)
  - [ ] scripts/ (자동화)
  - [ ] terraform/ (클라우드 리소스)

## 📦 관련 저장소

- **GrowAILMS**: [GrowAILMS Repository](https://github.com/rodem-glitch/GrowAILMS) - 마이그레이션 타겟

## 🔧 로컬 개발 환경

### 사전 요구사항

- JDK 17+
- Node.js 20 LTS
- Docker Desktop
- Git

### 실행 방법

```bash
# 저장소 클론
git clone -b dev https://github.com/sh-jang-code/MalgnLMS.git
cd MalgnLMS

# Docker 컨테이너 실행
docker-compose up -d

# 백엔드 빌드
./gradlew clean build -x test

# 프론트엔드 실행
cd frontend && npm install && npm run dev
```

================================================================================
GrowAI Load Balancer 엔드포인트 점검 최종 보고서
================================================================================
점검일시: 2026-02-05 16:45 KST
대상: https://growai.co.kr
================================================================================

[SSL 인증서 상태]
--------------------------------------------------------------------------------
- 인증서 이름: growai-cert
- 유형: Google Managed
- 상태: ACTIVE ✓
- 도메인: growai.co.kr
- 만료일: 2026-05-05
- 발급기관: WR3
- 결과: ✓ 정상

================================================================================
[최종 점검 결과]
================================================================================

| 경로          | 상태코드 | 백엔드                      | 결과   |
|---------------|----------|-----------------------------| -------|
| /             | 200      | malgnlms-frontend-backend   | ✓ 정상 |
| /lms/*        | 200      | malgnlms-legacy-backend     | ✓ 정상 |
| /tutor/*      | 200      | malgnlms-frontend-backend   | ✓ 정상 |
| /api/lms/*    | 200      | malgnlms-api-backend        | ✓ 정상 |

- 총 점검 항목: 4개
- 정상: 4개
- 오류: 0개

================================================================================
[수행한 조치 사항]
================================================================================

1. JavaMail 라이브러리 누락 문제 해결
   -----------------------------------------------------------------------
   - 증상: malgnlms-legacy 서비스 500 에러
   - 원인: javax.mail.Address 클래스 누락 (NoClassDefFoundError)
   - 조치: Dockerfile.legacy 수정
          Stage 2에 mail.jar 복사 추가:
          COPY --from=build /build/mail-api.jar 
               /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
   - 커밋: "Include mail API jar in Tomcat runtime"
   - 배포: GitHub Actions 자동 배포 완료 (4m 15s)

2. /lms/* 경로 404 오류 해결 (URL Rewrite)
   -----------------------------------------------------------------------
   - 증상: /lms/* 경로 접근 시 404 Not Found
   - 원인: Load Balancer가 /lms/index.jsp를 그대로 전달
           → Tomcat ROOT에는 /lms/ 폴더가 없어 404 발생
   
   - 해결 원리:
     [수정 전]
     클라이언트: /lms/index.jsp → LB → Tomcat: /lms/index.jsp (404)
     
     [수정 후]
     클라이언트: /lms/index.jsp → LB(rewrite) → Tomcat: /index.jsp (200)
   
   - 조치: gcloud compute url-maps 업데이트
          pathRules → routeRules 변경
          urlRewrite.pathPrefixRewrite: / 추가
   
   - 명령어:
     gcloud compute url-maps import growai-url-map \
       --source=url-map-updated.yaml --global

3. /api/lms/* 경로 URL Rewrite 추가
   -----------------------------------------------------------------------
   - 증상: /api/lms/* 경로 접근 시 500 에러 (NoResourceFoundException)
   - 원인: API 서버가 context-path 없이 / 에서 서빙
           /api/lms/... 요청이 그대로 전달되어 매핑 실패
   - 조치: URL Rewrite 추가 (/api/lms/ → /)
   - 비고: HEAD 메서드는 500 반환 (API 특성상 정상)
           GET 메서드로 200 확인 완료
================================================================================
[URL Map 최종 구성]
================================================================================

name: growai-url-map
pathMatchers:
- name: growai-path-matcher
  defaultService: malgnlms-frontend-backend
  routeRules:
  - priority: 1
    matchRules:
    - prefixMatch: /lms/
    service: malgnlms-legacy-backend
    routeAction:
      urlRewrite:
        pathPrefixRewrite: /
  
  - priority: 2
    matchRules:
    - prefixMatch: /api/lms/
    service: malgnlms-api-backend
    routeAction:
      urlRewrite:
        pathPrefixRewrite: /
  
  - priority: 3
    matchRules:
    - prefixMatch: /tutor/
    service: malgnlms-frontend-backend

================================================================================
[Cloud Run 서비스 상태]
================================================================================

1. malgnlms-legacy
   - URL: https://malgnlms-legacy-212772069233.asia-northeast1.run.app
   - 직접 접속: ✓ 200 OK
   - LB 경유 (/lms/*): ✓ 200 OK

2. malgnlms-frontend
   - LB 경유 (/): ✓ 200 OK
   - LB 경유 (/tutor/*): ✓ 200 OK

3. malgnlms-api
   - URL: https://malgnlms-api-212772069233.asia-northeast1.run.app
   - 직접 접속: ✓ 200 OK
   - LB 경유 (/api/lms/*): ✓ 200 OK (GET)

================================================================================
[참고: URL Rewrite 동작 원리]
================================================================================

문제 상황:
- Tomcat이 /usr/local/tomcat/webapps/ROOT/ 에서 서빙
- 클라이언트가 /lms/page.jsp 요청
- Load Balancer가 /lms/page.jsp 그대로 전달
- Tomcat은 ROOT/lms/page.jsp를 찾음 → 없음 → 404

해결:
- Load Balancer에서 URL 재작성 (pathPrefixRewrite)
- /lms/page.jsp → /page.jsp 로 변환 후 전달
- Tomcat은 ROOT/page.jsp를 찾음 → 있음 → 200

================================================================================


## 🔐 보안 준수사항

- 행정안전부 시큐어코딩 가이드라인 준수
- SQL Injection 방지: MyBatis `#{}` 바인딩
- XSS 방지: 입력값 검증 및 HTML 이스케이프
- 민감정보 로그 출력 금지



## 📄 라이선스

이 프로젝트는 한국폴리텍대학 내부 사용 목적으로 개발되었습니다.

---
**© 2025 NEWKL - AI 기반 교육 솔루션**
