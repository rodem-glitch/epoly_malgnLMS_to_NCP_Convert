제공해주신 프로젝트 개요와 최종 점검 보고서를 바탕으로, 사내 위키나 GitHub README.md에 바로 복사해서 사용할 수 있도록 가독성 높고 깔끔한 마크다운 형식으로 재구성했습니다.🚀 GrowAILMS (AI 기반 학습관리시스템)한국폴리텍대학 e-poly 현대화 프로젝트레거시 시스템(MalgnLMS)에서 차세대 AI LMS(GrowAILMS)로의 마이그레이션 및 클라우드 네이티브 전환📋 프로젝트 개요본 프로젝트는 한국폴리텍대학의 기존 e-poly(MalgnLMS) 시스템을 현대적인 기술 스택으로 전환하여 확장성과 유지보수성을 확보하고, AI 기반 학습 기능을 통합하는 것을 목표로 합니다.🛠 기술 스택 변천사구분Legacy (MalgnLMS)Target (GrowAILMS)BackendJava 8, Malgnsoft DataObjectJava 17, Spring Boot 3.2, eGov 4.2ORMCustom DAO PatternMyBatis 3.5FrontendJSP + jQueryReact 18 + TypeScript + Vite인증Session 기반JWT + Keycloak (SSO)인프라단일 온프레미스 서버Docker + GCP/NCP Hybrid Cloud📂 시스템 구조 및 현황1. 소스 코드 통계Legacy DAO: 약 170여 개의 클래스 기반 데이터 접근Frontend UI: 1,223개의 JSP 파일로 구성된 대규모 레거시 뷰Modernization: 3단계 마이그레이션 전략 수행 중2. 마이그레이션 로드맵[x] Phase 1: 분석 - 레거시 코드 정적 분석 및 종속성 파악 완료[x] Phase 2: 설계 - 클라우드 아키텍처 및 DB 스키마 재설계 완료[ ] Phase 3: 구현 (진행률 85%)[x] Backend Core (Genkit 엔진 포함)[x] Infrastructure (Docker Compose 설정)[ ] Automation (Deployment Scripts)[ ] IaC (Terraform 클라우드 프로비저닝)💻 로컬 개발 환경 구축사전 요구사항Runtime: JDK 17+, Node.js 20 LTSTooling: Docker Desktop, Git퀵 스타트Bash# 1. 저장소 클론 (dev 브랜치)
git clone -b dev https://github.com/sh-jang-code/MalgnLMS.git
cd MalgnLMS

# 2. 인프라 컨테이너 실행
docker-compose up -d

# 3. 백엔드 빌드 및 실행
./gradlew clean build -x test

# 4. 프론트엔드 개발 서버 실행
cd frontend && npm install && npm run dev
🔍 엔드포인트 점검 최종 보고 (2026-02-05)1. 인프라 상태 요약대상: https://growai.co.kr | SSL: ACTIVE (Google Managed)경로매핑된 백엔드 서비스상태/malgnlms-frontend-backend✅ 정상/lms/*malgnlms-legacy-backend✅ 정상 (Rewrite)/tutor/*malgnlms-frontend-backend✅ 정상/api/lms/*malgnlms-api-backend✅ 정상 (Rewrite)2. 주요 트러블슈팅 및 조치 사항✅ JavaMail 라이브러리 누락 해결현상: 레거시 서비스에서 NoClassDefFoundError (javax.mail.Address) 발생원인: Tomcat 런타임 이미지 내 Mail API 라이브러리 누락조치: Dockerfile.legacy 수정하여 mail-api.jar 라이브러리 강제 포함 및 재배포 완료✅ 경로 기반 라우팅 404 오류 해결현상: /lms/ 및 /api/lms/ 접근 시 404 Not Found 발생원인: Load Balancer가 접두사를 포함해 요청을 전달하여 Tomcat ROOT 내 경로 불일치해결: GCP URL Map 설정을 pathRules에서 routeRules로 변경하고 URL Rewrite(Prefix /) 처리 적용🔐 보안 및 준수사항시큐어 코딩: 행정안전부 가이드라인에 따른 정적 보안 분석 수행인젝션 방지: MyBatis #{} 파라미터 바인딩 원칙 준수데이터 보호: 민감 정보 마스킹 및 로그 출력 원칙적 금지© 2026 NEWKL - AI 기반 교육 솔루션이 문서는 한국폴리텍대학 GrowAILMS 프로젝트의 공식 기술 명세서입니다.
