# RPG-라이트: 기능 흐름 (`flows.md`)

최근 갱신: 2026-02-06

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-06 17:49

- React 빌드 산출물: project/vite.config.ts → public_html/tutor_lms/app
- Spring Boot API: growailms-api/build.gradle (Boot 3.2.5, Java 17)
<!-- @generated:end -->

## 자동 생성(권장)
- 아래 명령을 실행하면 “자동 요약”이 갱신됩니다.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File tools/rpg/generate.ps1`

## 목적
- 기능을 “사용자 의도(입력) → 서버 처리 → 출력”으로 압축해 두는 문서입니다.
- 에이전트는 작업 시작 시 이 문서를 먼저 보고, 작업 끝에 반드시 갱신합니다.

## 작성 규칙(강제, 최소)
- 아래 템플릿을 복사해서 항목을 추가/갱신합니다.
- “대충 이럴 것 같다”는 추측은 쓰지 말고, **실제 확인한 근거(경로/파일)** 를 포함합니다.
- 한 번의 작업(요청)에서 영향을 준 흐름이 있으면 최소 1개 항목은 반드시 갱신합니다.

---

## FLOW 템플릿
### FLOW-XXXX: (기능 이름)
- 사용자 동작(의도): (예: 관리자에서 과정 검색)
- 진입점: (JSP/API 경로)
- 처리(핵심): (주요 분기/검증/권한/세션 체크 등)
- DB: (DAO 클래스, 주요 테이블/컬럼, site_id/status 관례 포함 여부)
- 출력: (템플릿/JSON/파일 다운로드 등)
- 확인(근거): (어떤 화면/URL/테스트로 확인했는지)
- 최근 갱신: (YYYY-MM-DD)

---

### FLOW-1001: SSO 첫 방문 동의(신규 메인)
- 사용자 동작(의도): SSO 로그인 후 `/mypage/new_main/index.jsp` 첫 방문
- 진입점: `public_html/mypage/new_main/index.jsp`
- 처리(핵심):
  - 로그인 상태(`userId > 0`) + SSO 사용(`siteinfo.sso_yn=Y`)일 때만 동작
  - `TB_AGREEMENT_LOG`에 `type='sso'`, `module='sso_20260120'` 동의 이력이 없으면 동의 화면으로 리다이렉트
  - 동의 화면에서 동의 후 `returl`로 복귀
- DB:
  - 동의 이력: `src/dao/AgreementLogDao.java` → `TB_AGREEMENT_LOG` (`type=sso`, `module=sso_20260120`, `agreement_yn=Y`, `site_id`, `user_id`)
  - 동의 문구: 이미지 파일(`/common/images/consent/consent_sso_1.png` 또는 `/common/images/consent/consent_sso_2.png`) (둘 다 없으면 화면 차단)
- 출력:
  - 동의 화면: `public_html/member/privacy_agree.jsp` (파라미터 `ag=sso`, `returl=...`)
  - 템플릿: `public_html/html/member/privacy_agree.html` (`consent_mode` 분기)
    - 화면 제목: `consent_mode`에서는 상단 "회원가입" 제목을 숨김(동의 화면 전용)
    - 팝업 UI: `consent_mode`에서는 헤더/푸터/서브 배너를 숨김(동의만 집중)
- 확인(근거):
  - 리다이렉트/저장/출력 분기 코드를 파일에서 확인(`public_html/mypage/new_main/index.jsp`, `public_html/member/privacy_agree.jsp`, `public_html/html/member/privacy_agree.html`)
  - 로컬 테스트 진입점: `public_html/mypage/new_main/sso_consent_test.jsp`
- 최근 갱신: 2026-02-04

### FLOW-1002: 증명서(수료증/합격증) 발급 동의
- 사용자 동작(의도): 마이페이지에서 증명서 발급 버튼 클릭
- 진입점:
  - 발급 목록: `public_html/mypage/certificate_list.jsp`
  - 실제 발급: `public_html/mypage/certificate.jsp`, `public_html/mypage/certificate_course.jsp`, `public_html/mypage/certificate_template.jsp`
- 처리(핵심):
  - 발급 JSP 진입 시 `TB_AGREEMENT_LOG`에 `type='cert'`, `module='cert_20260120'` 동의 이력이 없으면 동의 화면으로 리다이렉트
  - 동의 화면에서 동의 후 원래 발급 URL(`returl`)로 복귀하여 발급 진행
- DB:
  - 동의 이력: `src/dao/AgreementLogDao.java` → `TB_AGREEMENT_LOG` (`type=cert`, `module=cert_20260120`, `agreement_yn=Y`, `module_id=cuid(가능한 경우)`)
  - 동의 문구: 이미지 파일(`/common/images/consent/consent_cert_1.png` 또는 `/common/images/consent/consent_cert_2.png`) (둘 다 없으면 화면 차단)
- 출력:
  - 동의 화면: `public_html/member/privacy_agree.jsp` (파라미터 `ag=cert`, `mid=cuid`, `returl=...`)
  - 템플릿: `public_html/html/member/privacy_agree.html` (`consent_mode` 분기)
- 확인(근거): 발급 진입점과 동의 게이트 분기 코드를 파일에서 확인(`public_html/mypage/certificate*.jsp`, `public_html/member/privacy_agree.jsp`)
- 최근 갱신: 2026-02-04

### FLOW-2001: 교수자 LMS(React) 진입/라우팅 및 UI 톤 적용
- 사용자 동작(의도): 교수자가 `/tutor_lms/`로 접속하여 교수자 기능을 사용
- 진입점: `public_html/tutor_lms/index.jsp`
- 처리(핵심):
  - 미로그인(`userId == 0`)이면 로그인 화면으로 이동
  - 운영자(S/A)는 통과, 그 외는 `TB_USER.tutor_yn='Y'` 교수자만 통과
  - `public_html/tutor_lms/app/index.html` 정적 빌드 파일이 없으면 “빌드 필요” 안내 페이지를 출력
  - 정상일 때 `app/index.html`로 리다이렉트(React SPA)
- 클라이언트(React):
  - 진입 파일: `public_html/tutor_lms/app/index.html`
  - 라우팅: 해시 라우팅(`#/<menuId>...`)을 `project/App.tsx`가 파싱하여 메뉴별 화면을 조건 렌더링
  - 메뉴 ID: `dashboard`, `explore`, `courses`, `assignment-manage`, `qna-manage`, `create-course`, `content-all`, `content-favorites`, `exam-categories`, `exam-questions`, `exam-management`, `subject-create`, `statistics`
- UI(학생 메인 톤):
  - 토큰/팔레트: `project/styles/globals.css` (배경 #f9fafb, 포인트 #2b58e6, 보더 #e5e7eb)
  - 레이아웃: `project/App.tsx` (헤더/사이드바/콘텐츠 컨테이너를 카드 기반으로 정리)
- 출력:
  - 빌드 산출물: `public_html/tutor_lms/app/assets/*`
- 확인(근거):
  - 권한/리다이렉트/빌드 안내 분기 코드를 파일에서 확인(`public_html/tutor_lms/index.jsp`)
  - 로컬 빌드로 산출물 갱신 확인(`cd project && npm run build`)
- 최근 갱신: 2026-02-05

### FLOW-3001: 통계 대시보드 인구 탭(학번 기반 연도·캠퍼스 그래프) 노출 복구
- 사용자 동작(의도): 교수자 LMS 통계 > 인구별 통계에서 학번 기반 연도·캠퍼스 인구 그래프를 다시 확인
- 진입점: `polytech-lms-api/src/main/resources/static/statistics/dashboard.html`
- 처리(핵심):
  - 학번 기반 카드 DOM(`memberKeyPopulationCard`)을 기본 노출 상태로 유지
  - 비활성 플래그(`enableMemberKeyPopulation`)를 `true`로 원복해 API 호출 재개
  - 학번 기반 통계는 캠퍼스 기준 통계이므로 캠퍼스 값만 전달
  - 학번 연도(2자리) 확장값이 현재 연도보다 크면 `19YY`로 보정해 표/그래프 연도 불일치 방지
  - 학번의 과정구분(5~6자리) 기준 집계를 추가해 첫 번째 그래프 막대를 과정구분 스택으로 세분화
- DB:
  - 프런트는 `/statistics/api/population/member-key-campus` 엔드포인트만 호출
  - 백엔드는 `StatisticsDashboardApiController` → `MemberKeyPopulationService` → `MemberKeyPopulationJdbcRepository` 흐름으로 처리
  - 집계 기준 테이블: `LM_POLY_MEMBER` (`MEMBER_KEY`, `CAMPUS_CODE`, `CAMPUS_NAME`)
  - SQL 집계 단위: `연도 + 캠퍼스 + 과정구분` (과정구분=`SUBSTRING(MEMBER_KEY, 5, 2)`)
- 출력:
  - 차트: `memberKeyPopulationChart`
  - 표: `memberKeyPopulationTableBody`
  - 상태 문구: `memberKeyPopulationStatus`
- 확인(근거):
  - `src` 파일에서 카드 숨김 스타일 제거, 플래그 `true` 반영 확인
  - `build/resources` 파일도 동일하게 반영해 실행 중 화면과 소스 불일치 제거
  - `refreshMemberKeyPopulation()`에서 응답 행 기준으로 연도 보정 후 차트/표 재구성 확인
  - `./gradlew.bat compileJava`로 API 엔드포인트/서비스/저장소 컴파일 성공 확인
- 최근 갱신: 2026-02-06

### FLOW-3002: 산업분포 분석 표 내 엑셀 버튼 제거
- 사용자 동작(의도): 교수자 LMS 통계 > 산업별 통계에서 표 카드 헤더의 엑셀 버튼을 숨기고 싶음
- 진입점: `public_html/tutor_lms/index.jsp` → `project/components/StatisticsPage.tsx`(iframe) → `polytech-lms-api/src/main/resources/static/statistics/dashboard.html`
- 처리(핵심):
  - 산업분포 분석 카드 헤더의 `downloadIndustryCsv` 버튼 마크업 제거
  - `refreshIndustry()` 내부의 `downloadIndustryCsv` 클릭 바인딩 제거(없는 DOM 참조 에러 방지)
  - 상단 다운로드 카드의 `downloadIndustryCsvTop` 버튼/바인딩은 유지
- DB: 없음(UI 레벨 변경, API/DAO 쿼리 변경 없음)
- 출력:
  - 산업분포 분석 표 카드 헤더: 다운로드 버튼 미노출
  - 상단 "산업분포 통합 데이터" 다운로드 버튼: 기존대로 동작
- 확인(근거):
  - 코드 확인: `dashboard.html`에서 `downloadIndustryCsv` 버튼/onclick 제거 확인
  - 정적 검증: `rg -n "downloadIndustryCsv(\\W|$)|downloadIndustryCsvTop" .../dashboard.html` 결과에서 `downloadIndustryCsvTop`만 남았는지 확인
- 최근 갱신: 2026-02-06

### FLOW-3003: 산업/인구 비교 결과 DB 캐시 기반 재사용
- 사용자 동작(의도): 통계 탭에서 같은 필터(캠퍼스/행정구역/연도)로 반복 조회해도 빠르게 결과를 보고 싶음
- 진입점:
  - 산업: `/statistics/api/industry/analysis`
  - 인구: `/statistics/api/population/compare`
- 처리(핵심):
  - 서비스 시작 시 `statistics_dashboard_cache` 테이블을 보장 생성
  - `IndustryAnalysisService`, `PopulationComparisonService`에서 계산 전에 캐시 조회(HIT 시 즉시 반환)
  - MISS일 때만 기존 계산 수행 후 최종 응답 JSON을 DB에 upsert 저장
  - 캐시 키: `cacheType + campus + admCd + admNm + year`를 SHA-256으로 해시해 고정 길이 PK 사용
  - 운영 추적을 위해 캐시 HIT/MISS 로그를 남김
- DB:
  - 테이블: `statistics_dashboard_cache`
  - 컬럼: `cache_key(PK)`, `cache_type`, `campus`, `adm_cd`, `adm_nm`, `stats_year`, `payload_json`, `hit_count`, `created_at`, `updated_at`
- 출력:
  - 기존 API 응답 포맷 유지(프론트 변경 없음)
  - 동일 조건 재조회 시 계산 결과를 DB에서 직접 반환해 응답 지연 감소
- 확인(근거):
  - 코드 반영: 캐시 저장소/서비스 추가 및 산업·인구 서비스에 캐시 분기 연결 확인
  - 빌드 검증: `cd polytech-lms-api && ./gradlew.bat compileJava` 성공
- 최근 갱신: 2026-02-06
