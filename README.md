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



## 🔐 보안 준수사항

- 행정안전부 시큐어코딩 가이드라인 준수
- SQL Injection 방지: MyBatis `#{}` 바인딩
- XSS 방지: 입력값 검증 및 HTML 이스케이프
- 민감정보 로그 출력 금지



## 📄 라이선스

이 프로젝트는 한국폴리텍대학 내부 사용 목적으로 개발되었습니다.

---

**© 2026 NEWKL - AI 기반 교육 솔루션**
