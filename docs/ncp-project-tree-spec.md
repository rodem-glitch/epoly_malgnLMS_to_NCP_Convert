# NCP 이관 프로젝트 트리구조 명세서

> 작성일: 2026-02-22
> 대상: GCP 단일 VM → NCP WEB/WAS 분리 구조 이관

---

## 1. NCP 인프라 구성

```
┌──────────────────────────────────────────────────────────────────────┐
│  NCP VPC                                                             │
│                                                                      │
│  ┌──────────────────────┐         ┌──────────────────────┐          │
│  │ newkl-web01          │         │ newkl-was01          │          │
│  │ 192.168.1.6          │  HTTP   │ 192.168.2.6          │          │
│  │                      │ ──────► │                      │          │
│  │  Nginx (80/443)      │         │  Docker Compose      │          │
│  │  - reverse proxy     │         │  ├ lms-api    :8081  │          │
│  │  - SSL termination   │         │  ├ lms-resin  :8080  │          │
│  │                      │         │  └ lms-qdrant :6333  │          │
│  └──────────────────────┘         └─────────┬────────────┘          │
│                                             │                        │
│                                   ┌─────────▼────────────┐          │
│                                   │ Cloud DB for MySQL   │          │
│                                   │ growai-db.vpc-cdb    │          │
│                                   │ .ntruss.com:3306     │          │
│                                   │ (MySQL 8.0.42)       │          │
│                                   └──────────────────────┘          │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. 소스 프로젝트 루트 구조

```
polytech-lms/                          ← Git 저장소 루트
├── polytech-lms-api/                  ← Spring Boot 3.2 API (Java 17)
├── project/                           ← React/TypeScript SPA (교수자 LMS)
├── public_html/                       ← 레거시 JSP/Malgn 템플릿 웹루트
├── src/                               ← 레거시 Java 소스 (DAO/비즈니스 로직)
├── resin/                             ← Resin WAS 설정
├── tools/                             ← 배포 도구/템플릿
│   ├── ncp/                           ← NCP 전용 배포 스크립트
│   └── gcp/                           ← GCP 전용 배포 스크립트
├── 통계/                              ← 통계 엑셀 원본 데이터
├── docs/                              ← 프로젝트 문서
├── claude/                            ← 에이전트 규칙 문서
├── AGENTS.md
├── CLAUDE.md
└── README.md
```

---

## 3. 상세 디렉터리별 설명

### 3-1. `polytech-lms-api/` — Spring Boot API

```
polytech-lms-api/
├── build.gradle
├── gradlew
├── src/main/
│   ├── java/kr/polytech/lms/
│   │   ├── antifraud/              ← 부정행위 탐지 API
│   │   │   ├── controller/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── contentsummary/         ← 영상 콘텐츠 요약 (Gemini AI)
│   │   │   ├── client/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── global/                 ← 공통/전역 엔드포인트
│   │   │   ├── controller/
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   └── vector/            ← Qdrant 벡터 스토어
│   │   │       ├── config/
│   │   │       └── service/
│   │   ├── job/                    ← 채용 추천 (잡코리아/워크넷)
│   │   │   ├── classify/
│   │   │   ├── client/
│   │   │   ├── code/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── livesession/            ← 실시간 수업 (WebSocket)
│   │   │   ├── controller/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── recocontent/            ← 콘텐츠 추천
│   │   │   ├── controller/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   └── service/
│   │   ├── statistics/             ← 통계 대시보드
│   │   │   ├── ai/                ← 통계 AI 분석
│   │   │   ├── controller/
│   │   │   ├── dashboard/         ← 대시보드 전용
│   │   │   ├── internalstats/
│   │   │   ├── kosis/             ← KOSIS 공공데이터 연계
│   │   │   ├── mapping/
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   ├── sgis/              ← SGIS 통계지리정보 연계
│   │   │   ├── student/           ← 재학생 통계
│   │   │   └── util/
│   │   ├── studentcontentrecommend/ ← 학생용 콘텐츠 추천
│   │   │   ├── controller/
│   │   │   └── service/
│   │   └── tutorcontentrecommend/   ← 교수자용 콘텐츠 추천
│   │       ├── controller/
│   │       └── service/
│   └── resources/
│       ├── application.yml         ← Spring 설정
│       ├── static/statistics/      ← 통계 정적 리소스
│       ├── statistics/ai/          ← AI 프롬프트 템플릿
│       ├── sql/                    ← SQL 쿼리 파일
│       └── jobkorea/               ← 잡코리아 연동 데이터
└── src/test/                       ← 테스트 코드
```

**배포 대상**: WAS 서버 → `bootJar` 빌드 후 `polytech-lms-api.jar`로 Docker 컨테이너 실행

---

### 3-2. `project/` — React SPA (교수자 LMS)

```
project/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── main.tsx
├── App.tsx
├── api/                            ← API 호출 모듈
├── components/                     ← React 컴포넌트
├── styles/                         ← CSS/스타일
├── utils/                          ← 유틸리티 함수
└── imports/                        ← 공통 임포트
```

**빌드 산출물**: `npm run build` → `public_html/tutor_lms/app/` 에 출력
**배포 대상**: WAS 서버 → Resin 컨테이너의 웹루트에 포함

---

### 3-3. `public_html/` — 레거시 웹루트 (JSP/Malgn 템플릿)

```
public_html/
├── WEB-INF/
│   ├── web.xml                     ← 서블릿 설정
│   ├── resin-web.xml               ← 배포 시 렌더링 (DB 접속 정보)
│   ├── lib/                        ← JAR 라이브러리
│   ├── message/                    ← 다국어 메시지
│   └── key.dat                     ← 암호화 키
├── tutor_lms/                      ← 교수자 LMS 관련
│   ├── app/                        ← React 빌드 산출물
│   ├── api/                        ← 교수자 API JSP
│   ├── index.jsp
│   └── certificate*.jsp
├── sysop/                          ← 관리자(시삽) 화면
├── classroom/                      ← 수강실 화면
├── main/                           ← 메인 화면
├── auth/                           ← 인증/로그인
├── member/                         ← 회원 관리
├── board/                          ← 게시판
├── course/                         ← 과정 관리
├── ai/                             ← AI 기능 JSP
├── api/                            ← API 엔드포인트 JSP
├── schedule/                       ← 스케줄/캘린더
├── mypage/                         ← 마이페이지
├── comment/                        ← 댓글
├── book/                           ← 교재
├── webtv/                          ← 웹TV/동영상
├── player/                         ← 플레이어
├── kollus/                         ← 콜러스 VOD 연동
├── order/                          ← 주문/결제
├── mobile/                         ← 모바일 화면
├── minitalk/                       ← 미니톡 채팅
├── exsignon/                       ← SSO 연동
├── allat/                          ← 올앳 결제
├── html/                           ← 정적 HTML
├── common/                         ← 공통 리소스 (CSS/JS/이미지)
├── inc/                            ← 공통 include JSP
├── data/                           ← 런타임 데이터 (배포 제외)
│   ├── file/                       ← 업로드 파일 저장소
│   ├── log/                        ← 애플리케이션 로그
│   ├── tmp/                        ← 업로드 임시 파일
│   └── qrcode/                     ← QR코드 생성 데이터
├── _html_v5/                       ← HTML5 퍼블리싱 원본
├── index.jsp                       ← 루트 진입점
└── server_host.jsp                 ← 호스트 설정 JSP
```

**배포 대상**: WAS 서버 → Resin 컨테이너 `/var/resin/webapps/ROOT`

---

### 3-4. `src/` — 레거시 Java 소스

```
src/
├── dao/                            ← DAO 클래스 (DB 접근 계층)
│   ├── ActionLogDao.java
│   ├── BoardDao.java
│   ├── BookDao.java
│   ├── CategoryDao.java
│   ├── CodeDao.java
│   ├── CourseDao.java              ← 과정 DAO
│   ├── MemberDao.java              ← 회원 DAO
│   └── ... (약 50+ DAO 파일)
└── malgnsoft/
    └── util/                       ← Malgn 프레임워크 유틸리티
```

**배포 대상**: WAS 서버 → Resin 컨테이너 `/opt/polytech-lms/legacy/src` (읽기전용)

---

### 3-5. `통계/` — 통계 엑셀 데이터

```
통계/
├── 통계 기능 관련 학과 정보 매칭.xlsx
├── 2024.02_학위과정 졸업자 취업률_집계배포_251204.xlsx
├── 입시율관리.xlsx
├── 재학생_인구_가데이터_20260120.xlsx
├── 2022_취업률_Dummy.xlsx
├── 2023_취업률_Dummy.xlsx
├── 2025_취업률_Dummy.xlsx
├── 캠퍼스 소재지.xlsx
├── LXP 성과지표 항목.xlsx
├── 입시 양성 취업.xlsx
└── 2025년 9월 기준 2년제학위과정 월보 양식.xls
```

**배포 대상**: WAS 서버 → API 컨테이너 `/data/statistics` (읽기전용 마운트)

---

### 3-6. `tools/ncp/` — NCP 배포 도구

```
tools/ncp/
├── templates/
│   ├── docker-compose-was.yml.tpl  ← WAS Docker Compose (3 서비스)
│   ├── deploy-was.sh.tpl           ← WAS 배포 스크립트
│   ├── deploy-web.sh.tpl           ← WEB 배포 스크립트
│   ├── nginx-web.conf.tpl          ← Nginx 리버스 프록시 설정
│   └── resin-web.xml.tpl           ← JNDI 데이터소스 설정
├── migrate-db.sh                   ← Cloud DB 마이그레이션 스크립트
└── README.md                       ← NCP 이관 가이드
```

---

## 4. WAS 서버 배포 번들 구조

CI/CD에서 WAS 서버로 전송되는 번들(`ncp-was-bundle/`):

```
ncp-was-bundle/                        ← GitHub Actions에서 생성
├── .env                               ← 환경변수 (DB/API 키)
├── docker-compose.yml                 ← docker-compose-was.yml.tpl 사본
├── deploy-was.sh                      ← deploy-was.sh.tpl 사본
├── app/
│   └── polytech-lms-api.jar           ← Spring Boot JAR (bootJar 빌드 산출물)
├── legacy/
│   ├── public_html/                   ← public_html/ 전체 사본
│   │   └── WEB-INF/
│   │       └── resin-web.xml          ← 렌더링 완료된 DB 접속 설정
│   └── src/                           ← src/ 전체 사본
└── statistics_data/                   ← 통계/ 엑셀 파일 사본
```

### WAS 서버 최종 배치 경로

```
/opt/polytech-lms/                     ← STACK_TARGET (rsync 대상)
├── .env
├── docker-compose.yml
├── app/
│   └── polytech-lms-api.jar
├── legacy/
│   ├── public_html/                   ← Resin 웹루트 마운트
│   │   ├── WEB-INF/
│   │   │   ├── resin-web.xml          ← Cloud DB JNDI 설정
│   │   │   ├── work/                  ← JSP 컴파일 캐시 (런타임 생성)
│   │   │   └── classes/               ← 컴파일 산출물 (런타임 생성)
│   │   └── data/
│   │       ├── file/                  ← 업로드 파일 (rsync 보호)
│   │       ├── log/                   ← 로그 (rsync 보호)
│   │       └── tmp/                   ← 임시 파일 (rsync 보호)
│   └── src/                           ← 레거시 Java 소스 (읽기전용)
└── statistics_data/                   ← 통계 엑셀 (읽기전용)
```

---

## 5. WEB 서버 배포 번들 구조

CI/CD에서 WEB 서버로 전송되는 번들(`ncp-nginx-conf/`):

```
ncp-nginx-conf/                        ← GitHub Actions에서 생성
├── lms-web.conf                       ← nginx-web.conf.tpl 렌더링 완료
└── deploy-web.sh                      ← deploy-web.sh.tpl 사본
```

### WEB 서버 최종 배치 경로

```
/etc/nginx/
├── sites-available/
│   └── lms-web.conf                   ← Nginx 리버스 프록시 설정
└── sites-enabled/
    └── lms-web.conf → ../sites-available/lms-web.conf
```

---

## 6. Docker 컨테이너 내부 경로 매핑

### lms-api (Spring Boot, eclipse-temurin:17-jre)

| 호스트 경로 | 컨테이너 경로 | 용도 |
|---|---|---|
| `/opt/polytech-lms/app/polytech-lms-api.jar` | `/app/polytech-lms-api.jar` (ro) | Spring Boot JAR |
| `/opt/polytech-lms/statistics_data/` | `/data/statistics/` (ro) | 통계 엑셀 데이터 |

### lms-resin (Resin WAS, expertsystems/resin:latest)

| 호스트 경로 | 컨테이너 경로 | 용도 |
|---|---|---|
| `/opt/polytech-lms/legacy/public_html/` | `/var/resin/webapps/ROOT` | 웹루트 (JSP/HTML/CSS/JS) |
| `/opt/polytech-lms/legacy/src/` | `/opt/polytech-lms/legacy/src` (ro) | 레거시 DAO/유틸 소스 |

### lms-qdrant (Qdrant v1.15.3)

| 호스트 경로 | 컨테이너 경로 | 용도 |
|---|---|---|
| Docker volume `qdrant_data` | `/qdrant/storage` | 벡터 DB 영구 스토리지 |

---

## 7. Nginx 라우팅 규칙 (WEB → WAS)

| URL 패턴 | 대상 | 포트 | 비고 |
|---|---|---|---|
| `/statistics/`, `/statistics` | was_api (lms-api) | 8081 | 통계 대시보드 |
| `/job/` | was_api | 8081 | 채용 추천 |
| `/student/` | was_api | 8081 | 학생 콘텐츠 추천 |
| `/tutor/` | was_api | 8081 | 교수자 콘텐츠 추천 |
| `/contentsummary/` | was_api | 8081 | 영상 요약 |
| `/reco-contents/` | was_api | 8081 | 콘텐츠 추천 |
| `/global/` | was_api | 8081 | 전역 API |
| `/antifraud/` | was_api | 8081 | 부정행위 탐지 |
| `/livesession/` | was_api | 8081 | 실시간 수업 (WebSocket) |
| `/actuator/` | was_api | 8081 | 헬스 체크 |
| `/tutor_lms/app/index.html` | was_resin (lms-resin) | 8080 | 교수자 SPA (UTF-8 강제) |
| `/` (기본) | was_resin | 8080 | 레거시 JSP/템플릿 전체 |

---

## 8. 파일-서버 매핑 요약

| 소스 경로 | 서버 | 최종 위치 | 비고 |
|---|---|---|---|
| `polytech-lms-api/build/libs/*.jar` | WAS | `/opt/polytech-lms/app/polytech-lms-api.jar` | bootJar 빌드 |
| `public_html/` | WAS | `/opt/polytech-lms/legacy/public_html/` | Resin 웹루트 |
| `src/` | WAS | `/opt/polytech-lms/legacy/src/` | 읽기전용 |
| `통계/` | WAS | `/opt/polytech-lms/statistics_data/` | 읽기전용 |
| `tools/ncp/templates/resin-web.xml.tpl` | WAS | `legacy/public_html/WEB-INF/resin-web.xml` | sed 렌더링 |
| `tools/ncp/templates/docker-compose-was.yml.tpl` | WAS | `/opt/polytech-lms/docker-compose.yml` | 직접 복사 |
| `tools/ncp/templates/deploy-was.sh.tpl` | WAS | 번들 내 실행 후 삭제 | 배포 스크립트 |
| `tools/ncp/templates/nginx-web.conf.tpl` | WEB | `/etc/nginx/sites-available/lms-web.conf` | sed 렌더링 |
| `tools/ncp/templates/deploy-web.sh.tpl` | WEB | 번들 내 실행 후 삭제 | 배포 스크립트 |

---

## 9. 런타임 데이터 보호 영역

배포 시 `rsync --delete`에서 제외되는 디렉터리:

| 경로 | 용도 | 보호 이유 |
|---|---|---|
| `legacy/public_html/data/file/` | 사용자 업로드 파일 | 배포마다 삭제 시 파일 유실 |
| `legacy/public_html/data/log/` | 애플리케이션 로그 | 운영 로그 보존 |
| `legacy/public_html/data/tmp/` | 업로드 임시 파일 | 진행 중 업로드 보호 |
| `legacy/public_html/WEB-INF/work/` | JSP 컴파일 캐시 | Resin 런타임 생성 |

---

## 10. 환경변수 (.env) 항목

WAS 서버의 `/opt/polytech-lms/.env`에 포함되는 변수:

| 변수명 | 용도 | GitHub Secret |
|---|---|---|
| `MYSQL_USER` | Cloud DB 사용자 | 하드코딩 (`lms`) |
| `MYSQL_PASSWORD` | Cloud DB 비밀번호 | `LMS_DB_PASSWORD` |
| `APP_DB_URL` | JDBC 접속 URL | `NCP_DB_HOST`에서 생성 |
| `QDRANT_API_KEY` | Qdrant 벡터 DB 키 | `QDRANT_API_KEY` |
| `QDRANT_COLLECTION` | 벡터 컬렉션명 | `QDRANT_COLLECTION` |
| `GOOGLE_API_KEY` | Google API 키 | `GOOGLE_API_KEY` |
| `GEMINI_API_KEY` | Gemini AI 키 | `GEMINI_API_KEY` |
| `KOSIS_CONSUMER_KEY` | KOSIS 공공데이터 키 | `KOSIS_CONSUMER_KEY` |
| `KOSIS_CONSUMER_SECRET` | KOSIS 시크릿 | `KOSIS_CONSUMER_SECRET` |
| `WORK24_AUTH_KEY` | 워크넷24 인증 키 | `WORK24_AUTH_KEY` |
| `JOBKOREA_API_KEY` | 잡코리아 API 키 | `JOBKOREA_API_KEY` |
| `JOBKOREA_OEM_CODE` | 잡코리아 OEM 코드 | `JOBKOREA_OEM_CODE` |
| `STATISTICS_AI_API_KEY` | 통계 AI API 키 | `STATISTICS_AI_API_KEY` |
| `KOLLUS_ACCESS_TOKEN` | 콜러스 VOD 토큰 | `KOLLUS_ACCESS_TOKEN` |
| `KOLLUS_SECURITY_KEY` | 콜러스 보안 키 | `KOLLUS_SECURITY_KEY` |
| `KOLLUS_CHANNEL_KEY` | 콜러스 채널 키 | `KOLLUS_CHANNEL_KEY` |
| `KOLLUS_CLIENT_USER_ID` | 콜러스 사용자 ID | `KOLLUS_CLIENT_USER_ID` |
