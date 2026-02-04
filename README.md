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

## 📖 문서

- [개발 가이드](./docs/MalgnLMS_Development_Guide.md)
- [CI/CD 배포 가이드](./docs/DEPLOYMENT.md)

## 🔐 보안 준수사항

- 행정안전부 시큐어코딩 가이드라인 준수
- SQL Injection 방지: MyBatis `#{}` 바인딩
- XSS 방지: 입력값 검증 및 HTML 이스케이프
- 민감정보 로그 출력 금지

================================================================================
GrowAI Load Balancer 엔드포인트 점검 로그
점검일시: 2026-02-05 16:12 KST
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
- 인증서 체인: GTS Root R1 → WR3 → growai.co.kr
- 결과: ✓ 정상

[www 서브도메인]
--------------------------------------------------------------------------------
- www.growai.co.kr: 인증서 미포함
- 사용 여부: 사용 안 함 (확인됨)
- 결과: ✓ 의도된 설정

================================================================================
[경로 규칙 점검 결과]
================================================================================

1. 기본 경로 (/)
   - URL: https://growai.co.kr/
   - 백엔드: malgnlms-frontend-backend
   - HTTP 상태: 200 OK
   - Content-Type: text/html
   - Content-Length: 1162
   - 보안 헤더: 적용됨 (X-Content-Type-Options, X-Frame-Options, X-XSS-Protection, CSP)
   - 결과: ✓ 정상

2. /lms/* 경로
   - URL: https://growai.co.kr/lms/
   - 백엔드: malgnlms-legacy-backend
   - HTTP 상태: 404 Not Found
   - Content-Type: text/html
   - Content-Length: 1647
   - 결과: ✗ 오류 - 페이지를 찾을 수 없음

3. /tutor/* 경로
   - URL: https://growai.co.kr/tutor/
   - 백엔드: malgnlms-frontend-backend
   - HTTP 상태: 200 OK
   - Content-Type: text/html
   - Content-Length: 1162
   - 보안 헤더: 적용됨
   - 결과: ✓ 정상

4. /api/lms/* 경로
   - URL: https://growai.co.kr/api/lms/
   - 백엔드: malgnlms-api-backend
   - HTTP 상태: 500 Internal Server Error
   - Content-Type: application/json
   - 결과: ✗ 오류 - 서버 내부 오류

================================================================================
[Cloud Run 서비스 직접 접속 점검]
================================================================================

1. malgnlms-legacy (Cloud Run)
   - URL: https://malgnlms-legacy-212772069233.asia-northeast1.run.app
   - 매핑 경로: /lms/*
   - 상태: ✗ 오류 (HTTP 500 Server Error)
   - 비고: Cloud Run 서비스 자체에서 500 에러 반환

================================================================================
[백엔드 서비스 구성]
================================================================================

1. malgnlms-api-backend
   - 프로토콜: HTTP
   - 리전: asia-northeast1
   - Cloud CDN: 사용 중지됨
   - 로깅: 사용 중지됨

2. malgnlms-frontend-backend
   - 프로토콜: HTTP
   - 리전: asia-northeast1
   - Cloud CDN: 사용 중지됨
   - 로깅: 사용 중지됨

3. malgnlms-legacy-backend
   - 프로토콜: HTTP
   - 리전: asia-northeast1
   - Cloud CDN: 사용 중지됨
   - 로깅: 사용 중지됨

================================================================================
[점검 요약]
================================================================================

| 경로          | 상태코드 | 결과   |
|---------------|----------|--------|
| /             | 200      | ✓ 정상 |
| /lms/*        | 404      | ✗ 오류 |
| /tutor/*      | 200      | ✓ 정상 |
| /api/lms/*    | 500      | ✗ 오류 |

- 정상: 2개
- 오류: 2개

================================================================================
[조치 필요 사항]
================================================================================

1. /lms/* 경로 (404 Not Found)
   - malgnlms-legacy-backend 서비스 확인 필요
   - Cloud Run 서비스가 해당 경로를 처리하지 못하고 있음
   - Cloud Run 로그 확인 권장

2. /api/lms/* 경로 (500 Internal Server Error)
   - malgnlms-api-backend 서비스 확인 필요
   - 백엔드 애플리케이션 오류 발생 중
   - Cloud Run 로그에서 상세 에러 확인 권장

================================================================================

================================================================================
[오류 상세 분석]
================================================================================

1. malgnlms-legacy 서비스 오류 분석
--------------------------------------------------------------------------------
   - 오류 위치: /index.jsp (Line 19)
   - 오류 유형: java.lang.NoClassDefFoundError
   
   [근본 원인]
   java.lang.ClassNotFoundException: javax.mail.Address
   
   [원인 분석]
   JavaMail 라이브러리(javax.mail)가 누락됨
   - Malgn 클래스 초기화 시 javax.mail.Address 클래스를 찾지 못함
   - WEB-INF/lib에 mail.jar 또는 javax.mail.jar 파일이 없음
   
   [해결 방법]
   1. JavaMail 라이브러리 추가
      - Maven 사용 시:
        <dependency>
            <groupId>com.sun.mail</groupId>
            <artifactId>javax.mail</artifactId>
            <version>1.6.2</version>
        </dependency>
      
      - 또는 직접 JAR 추가:
        WEB-INF/lib/javax.mail-1.6.2.jar
   
   2. Jakarta Mail (Java EE 9+) 사용 시:
      <dependency>
          <groupId>com.sun.mail</groupId>
          <artifactId>jakarta.mail</artifactId>
          <version>2.0.1</version>
      </dependency>
   
   3. Docker 이미지 재빌드 후 Cloud Run 재배포

================================================================================


## 📄 라이선스

이 프로젝트는 한국폴리텍대학 내부 사용 목적으로 개발되었습니다.

---

**© 2025 NEWKL - AI 기반 교육 솔루션**
