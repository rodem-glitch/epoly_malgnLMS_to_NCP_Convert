# RPG-라이트: 기능 흐름 (`flows.md`)

최근 갱신: 2026-02-11

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-11 11:02

- Resin root-directory: resin/resin.xml → public_html
- React 빌드 산출물: project/vite.config.ts → public_html/tutor_lms/app
- Spring Boot API: polytech-lms-api/build.gradle (Boot 3.2.5, Java 17)
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

### FLOW-1003: 비로그인 권한 진입 시 신규 메인 로그인 모달 게이트
- 사용자 동작(의도): 로그인 없이 권한 필요한 URL(예: 게시글 작성, 수강 기능)에 진입
- 진입점:
  - 0차 마이페이지 공통 가드: `public_html/mypage/init.jsp`
  - 1차 로그인 엔드포인트: `public_html/member/login.jsp` (GET)
  - 모달 랜딩 페이지: `public_html/mypage/new_main/index.jsp`
- 처리(핵심):
  - `mypage/init.jsp`에서 비로그인(`userId==0`)이면 `auth.loginForm()` 대신 `/mypage/new_main/?login_required=Y&returl=...`로 즉시 리다이렉트
  - `member/login.jsp`에서 `GET + (access_token/ek 없음)`이면 로그인 화면을 직접 렌더링하지 않고 `/mypage/new_main/?login_required=Y&returl=...`로 리다이렉트
  - returl은 쿼리 원문에서 재파싱해(레거시 `Auth.loginForm()` 비인코딩 대응) 쿼리 손실을 줄이고, 외부 도메인은 `/mypage/new_main/`으로 차단
  - `mypage/new_main/index.jsp`에서 `login_required`, `returl`, `udid`를 세팅하고 로그(`login_modal_request_*`)를 남김
  - `html/mypage/new_main_full.html`에서 `login_required_block`일 때 모달을 자동 오픈하고, 로그인 POST 시 `returl/udid`를 hidden으로 전달
  - 테스트 기간에는 `public_html/WEB-INF/tmp/dev-login.properties`가 있을 때만 빠른 로그인 UI를 노출하며, `id_prefix + 숫자 suffix` 조합으로 즉시 로그인 POST를 보냄
- DB: 없음(세션/리다이렉트 제어만 수행)
- 출력:
  - 신규 메인 페이지 내 로그인 모달(`public_html/html/mypage/new_main_full.html`)
  - 로그인 처리 엔드포인트는 기존과 동일하게 `POST /member/login.jsp`
- 확인(근거):
  - 코드 경로 확인: `public_html/mypage/init.jsp`, `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp`, `public_html/html/mypage/new_main_full.html`
  - 시나리오 점검: `권한 페이지(/mypage/*) -> /mypage/new_main/?login_required=Y&returl=... -> 모달 POST`
  - 로컬 검증: `/mypage/new_main/index.jsp` 응답 HTML에 `테스트 빠른 로그인` 영역 노출, `POST /member/login.jsp(id=haksa_pf26_01)` 응답이 `/tutor_lms/index.jsp` 복귀 스크립트 반환
- 최근 갱신: 2026-02-10

### FLOW-1004: 신규 메인 헤더 `나의강의실` 클릭 시 로그인 모달 분기
- 사용자 동작(의도): 비로그인 사용자가 신규 메인 상단의 `나의강의실` 메뉴를 클릭
- 진입점:
  - 레이아웃 템플릿: `public_html/html/layout/layout_new_main.html`
  - 단독 템플릿: `public_html/html/mypage/new_main_full.html`, `public_html/html/mypage/new_main_manual.html`
  - 코스 레이아웃 템플릿: `public_html/html/layout/layout_course.html`, `public_html/html/layout/layout_course135.html`, `public_html/html/layout/layout_course173.html`
- 처리(핵심):
  - `login_block`일 때만 기존 경로(`/mypage/index.jsp`)로 이동
  - `nif(login_block)`일 때:
    - 신규 메인 템플릿(`layout_new_main`, `new_main_full`, `new_main_manual`)은 `openLoginModal()` 직접 호출
    - 코스 레이아웃(`layout_course*`)은 `/mypage/new_main/?login_required=Y&returl=%2Fmypage%2Findex.jsp`로 이동
  - 어떤 경로에서든 최종적으로 신규 메인 로그인 모달로 수렴되게 통일
- DB: 없음(템플릿 분기/클라이언트 동작 제어)
- 출력:
  - 비로그인: 신규 메인 로그인 모달(`nm-login-modal`) 표시
  - 로그인: 기존 `나의강의실` 경로 유지(`/mypage/index.jsp`)
- 확인(근거):
  - 템플릿 분기 코드 확인: `public_html/html/layout/layout_new_main.html`, `public_html/html/mypage/new_main_full.html`, `public_html/html/mypage/new_main_manual.html`, `public_html/html/layout/layout_course.html`, `public_html/html/layout/layout_course135.html`, `public_html/html/layout/layout_course173.html`
  - 시나리오 점검(정적): `비로그인 상태 -> 헤더 나의강의실 클릭 -> 신규 메인 모달 오픈(openLoginModal 또는 login_required 리다이렉트)`
- 최근 갱신: 2026-02-10

### FLOW-1005: 로컬 Resin 실행 시 `/mypage/*` 흰화면(200 + 빈 본문) 복구
- 사용자 동작(의도): 로컬 IntelliJ Resin에서 `나의강의실` 또는 로그인 URL 진입 시 정상 화면 렌더링
- 진입점:
  - `public_html/init.jsp`
  - `public_html/WEB-INF/resin-web.xml`
  - `C:\Users\newkl\Desktop\resin-4.0.67\resin-4.0.67\conf\resin.xml`
- 처리(핵심):
  - 원인: `jdbc/malgn` JNDI DataSource 미설정으로 DB 연결 실패(`ds is null`) → `init.jsp`의 siteinfo/doc_root 검증 구간에서 조기 종료되어 빈 본문 반환
  - 조치:
    - `public_html/WEB-INF/resin-web.xml` 추가
    - Resin class-loader에 `compiling-loader` 설정(`source=C:/Users/newkl/Desktop/polytech-lms/src`)
    - JNDI DB 2종 정의(`jdbc/malgn`, `jdbc/lms`)
    - `public_html/init.jsp`에 `siteinfo_invalid` 진단 로그 추가
  - 실행 설정은 기존 IntelliJ Resin conf(`...resin-4.0.67/conf/resin.xml`)를 유지하고, 외부 conf의 ROOT 웹앱 경로를 `C:\Users\newkl\Desktop\polytech-lms\public_html`로 사용
- DB:
  - JNDI: `jdbc/malgn`, `jdbc/lms`
  - URL: `jdbc:mysql://192.168.0.55:3306/lms?useSSL=false&allowPublicKeyRetrieval=true`
- 출력:
  - `/mypage/new_main/index.jsp` 정상 HTML 렌더링
  - 비로그인 `/mypage/index.jsp`, `/member/login.jsp?returl=/mypage/index.jsp`는 `/mypage/new_main/?login_required=Y...`로 302 이동
- 확인(근거):
  - 수정 전: `curl -i /mypage/new_main/index.jsp` → `200`, `Content-Length: 12`(빈 본문), `/mypage/index.jsp` `Content-Length: 0`
  - 수정 후: `curl -i /mypage/new_main/index.jsp` → HTML 본문 반환(로그인 모달 스크립트 포함), `/mypage/index.jsp`/`/member/login.jsp?...` → `302` 정상
- 최근 갱신: 2026-02-10

### FLOW-1006: 신규 메인 매뉴얼(학생/교직원) 페이지 노출
- 사용자 동작(의도): `/mypage/new_main/manual.jsp`에서 학생/교직원 매뉴얼을 열어 사용 방법을 확인
- 진입점:
  - 매뉴얼 진입: `public_html/mypage/new_main/manual.jsp`
  - 매뉴얼 선택 화면(템플릿): `public_html/html/mypage/new_main_manual.html`
  - 학생 매뉴얼(정적): `public_html/mypage/new_main/student_manual.html`
  - 교직원 매뉴얼(정적): `public_html/mypage/new_main/tutor_manual.html`
- 처리(핵심):
  - `manual.jsp`에서 `p.setLayout("blank")`, `p.setBody("mypage.new_main_manual")`로 매뉴얼 선택 화면을 렌더링
  - `login_block` 값에 따라 헤더 메뉴/우측 로그인 UI가 분기됨(신규 메인 패턴)
  - 상세 매뉴얼은 정적 HTML을 새 탭으로 오픈하며, 스크린샷 이미지는 같은 폴더의 파일을 상대경로로 참조
- DB:
  - 사용자 표시용 조회: `src/dao/UserDao.java` → `TB_USER` (`id`, `status`)
- 출력:
  - 선택 화면: `public_html/html/mypage/new_main_manual.html`
  - 상세 화면: `public_html/mypage/new_main/student_manual.html`, `public_html/mypage/new_main/tutor_manual.html`
- 확인(근거):
  - 링크/경로 확인: `public_html/html/mypage/new_main_manual.html`의 `/mypage/new_main/*_manual.html` 링크
  - 정적 검증: `*_manual.html`에서 참조하는 `src` 이미지 파일이 `public_html/mypage/new_main/`에 모두 존재함(Test-Path로 확인)
- 최근 갱신: 2026-02-10

### FLOW-1007: 학생 자연어 검색 결과/더보기 추천영상 요약·키워드 노출
- 사용자 동작(의도): 학생이 통합검색에서 자연어 검색 후, 강의 영역 카드와 `더보기` 화면에서 각 추천 영상의 요약/키워드를 확인
- 진입점:
  - 통합검색 미리보기: `public_html/main/search.jsp`
  - 강의 더보기(벡터 모드): `public_html/main/search_detail.jsp` (`subject=course&mode=vector`)
- 처리(핵심):
  - 두 JSP 모두 `POST /student/content-recommend/search` 응답의 `summary`를 읽어 `subtitle_conv`로 가공(`m.stripTags` + `m.nl2br`)해 템플릿으로 전달
  - 같은 응답의 `keywords`를 `keywords_conv`로 내려 요약 아래 줄에 출력
  - 키워드 라벨(`키워드`)은 제거하고, 키워드 칩만 노출해 모바일 잘림 이슈를 방지
  - 추천영상(`is_reco_video`) 전용 타이틀 영역에서 회색 카테고리 라벨(`category_nm`)은 노출하지 않음
  - 운영 추적을 위해 `search_vector` 로그에 `mode(preview/detail)`, `qlen`, `reco_total`, `summary_total`, `keyword_total`을 남겨 검색/더보기 불일치 여부를 즉시 확인 가능하게 유지
- DB:
  - 직접 조회 없음(JSP 기준)
  - 요약 원천은 추천 API의 `StudentVideoRecommendResponse.summary` (`polytech-lms-api` 기준 `TB_RECO_CONTENT.summary`)
- 출력:
  - 통합검색 강의 목록: `public_html/html/main/search.html`에서 추천영상 행(`tr.reco-video-row`)에 `{{courses.subtitle_conv}}`를 요약 영역(`.reco-summary`)으로, `{{courses.keywords_conv}}`를 키워드 칩(`.reco-keyword-chip`)으로 렌더링
  - 강의 더보기 목록: `public_html/html/main/search_detail.html`도 동일하게 추천영상 행(`tr.reco-video-row`)에서 `{{list.subtitle_conv}}`, `{{list.keywords_conv}}`를 요약/키워드 칩 UI로 렌더링
- 확인(근거):
  - 코드 반영 확인: `public_html/main/search.jsp`, `public_html/main/search_detail.jsp`에서 `recoRows.s("summary")/recoRows.s("keywords")` → `subtitle_conv/keywords_conv` 매핑 및 `search_vector` 로그 필드 확장 확인
  - 출력 경로 확인: `public_html/html/main/search.html`, `public_html/html/main/search_detail.html`에서 추천영상 타이틀 카테고리 라벨 제거 + `course_block` 행의 `reco-video-row` 클래스 적용 + 요약/키워드 칩 슬롯 확인
- 최근 갱신: 2026-02-11

### FLOW-2001: 교수자 LMS(React) 진입/라우팅 및 UI 톤 적용
- 사용자 동작(의도): 교수자가 `/tutor_lms/`로 접속하여 교수자 기능을 사용
- 진입점: `public_html/tutor_lms/index.jsp`
- 처리(핵심):
  - 미로그인(`userId == 0`)이면 `returl=/tutor_lms/index.jsp(현재 URI)`를 붙여 `/member/login.jsp`로 이동
  - `/member/login.jsp` GET 게이트가 신규 메인 로그인 모달(`/mypage/new_main/?login_required=Y&returl=...`)로 넘기고, 로그인 성공 시 다시 `/tutor_lms/index.jsp`로 복귀
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
  - 코드 분기 확인: `public_html/tutor_lms/index.jsp`, `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp`
  - 로컬 응답 확인: `curl /tutor_lms/index.jsp`가 `top.location.replace('/member/login.jsp?returl=...')`를 반환하고, `curl /member/login.jsp?returl=%2Ftutor_lms%2Findex.jsp`가 `302 -> /mypage/new_main/?login_required=Y&returl=%2Ftutor_lms%2Findex.jsp`를 반환함
- 최근 갱신: 2026-02-10

### FLOW-3001: 교수자 통계 > 산업별 통계(산업분포 분석)
- 사용자 동작(의도): 교수자 통계 화면에서 캠퍼스/행정구역/연도를 선택해 “행정구역(종사자) vs 캠퍼스(학생)” 산업 분포를 비교
- 진입점:
  - 화면: `polytech-lms-api/src/main/resources/static/statistics/dashboard.html` (`/statistics`, `/statistics/dashboard` → `/statistics/dashboard.html`)
  - API: `GET /statistics/api/industry/analysis?campus=...&admCd=...&admNm=...&statsYear=...`
- 처리(핵심):
  - `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/controller/StatisticsDashboardApiController.java`에서 요청 로그 후 서비스 호출
  - `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/service/IndustryAnalysisService.java`
    - 캠퍼스 파라미터가 비어 있으면 `campus=null`로 간주(전체 캠퍼스)
    - 행정구역 코드는 `SgisAdministrativeCodeService`로 SGIS 코드로 변환(전국 전체는 `admCd=00`)
    - 요청 연도부터 최대 5년 범위를 역탐색하고, 최소 연도(2000년) 아래로는 내려가지 않도록 보정
    - 전국(`admCd=00`)은 1) SGIS 시도코드 합산 → 2) 로컬 시도코드 합산 → 3) `adm_cd` 미전달(non) 전국 시도 리스트 합산 순으로 계산
- DB/외부:
  - 외부(SGIS): `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/sgis/client/SgisClient.java`
  - 캐시(DB): `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/sgis/service/SgisCompanyCacheService.java` → `SgisCompanyRepository` (시도/전국 코드의 null 캐시 + 0,0 캐시 1회 재조회)
  - 내부 매핑/입학정원: `MajorIndustryMappingService`, `CampusStudentQuotaExcelService`
- 출력: JSON(카테고리별 지역 종사자/캠퍼스 학생 인원 및 비율, GAP)
- 확인(근거):
  - 실제 호출/파라미터는 `dashboard.html`의 fetch 코드에서 확인(산업 탭 필터)
  - 로컬 컴파일/테스트: `polytech-lms-api`에서 `GRADLE_USER_HOME`를 작업 폴더로 지정 후 `gradlew test` 성공
- 최근 갱신: 2026-02-06

### FLOW-3002: 교수자 통계 > 인구별 통계(표/차트 동기화)
- 사용자 동작(의도): 캠퍼스/행정구역/연도 필터를 변경하면 차트와 표가 같은 응답으로 즉시 갱신
- 진입점:
  - 화면: `polytech-lms-api/src/main/resources/static/statistics/dashboard.html`
  - API: `GET /statistics/api/population/compare?campus=...&admCd=...&admNm=...&populationYear=...`
- 처리(핵심):
  - `refreshPopulation()`에서 필터 변경마다 API 호출
  - 새 요청 시작 시 이전 인구 API 요청을 `AbortController`로 중단해 지연 응답 누적을 방지
  - 응답 반영 시 `populationTableData`, `genderTableData` 저장 직후 `renderPopulationTable()`, `renderGenderTable()`를 즉시 호출해 표를 함께 갱신
  - `populationRequestSeq`로 최신 요청만 반영하여, 느린 이전 응답이 화면을 덮지 않도록 차단
- 출력:
  - 차트(`populationChart`, `populationGenderChart`)와 표(`populationTableBody`, `genderTableBody`)가 동일 시점 데이터로 동기화
- 확인(근거):
  - `dashboard.html`의 `refreshPopulation()` 내부에서 최신 요청 체크 및 표 즉시 렌더 호출 코드 확인
- 최근 갱신: 2026-02-06

### FLOW-3003: 교수자 통계 > 인구별 통계(학번 기반 연도·캠퍼스 인구)
- 사용자 동작(의도): 인구 탭에서 캠퍼스/연도 필터를 바꾸면, 기존 비교 그래프 아래에 학번 기반 연도별·캠퍼스별 인구 그래프/표를 즉시 확인
- 진입점:
  - 화면: `polytech-lms-api/src/main/resources/static/statistics/dashboard.html`
  - API: `GET /statistics/api/population/member-key-campus?campus=...`
- 처리(핵심):
  - `StatisticsDashboardApiController.populationMemberKeyCampus()`가 캠퍼스만 로그로 남기고 서비스 호출
  - `MemberKeyPopulationService.summarizeByYearAndCampus()`에서 캠퍼스 필터 정규화(전체/null 처리) 후 시리즈/표 형태로 가공
  - `MemberKeyPopulationJdbcRepository.findYearCampusCounts()`에서 `LMS_MEMBER_VIEW`를 대상으로 `MEMBER_KEY` 앞 2자리(년도), `CAMPUS_CODE`, `CAMPUS_NAME` 기준 집계 (방언 충돌을 줄이기 위해 `LENGTH/SUBSTRING/CONCAT` 기반 SQL 사용)
  - 저장소 레벨에서 SQL 시작/성공/실패 로그를 남겨, 뷰테이블 호출 여부와 실패 원인을 로그만으로 확인 가능
  - 프론트 `refreshPopulation()`에서 `refreshMemberKeyPopulation()` 호출 시 캠퍼스만 전달하여, 행정구역/연도 변경은 이 그래프 집계값에 영향이 없도록 유지
- DB:
  - `LM_POLY_MEMBER` (`MEMBER_KEY`, `CAMPUS_CODE`, `CAMPUS_NAME`) - 학사 원천 `COM.LMS_MEMBER_VIEW` 동기화 테이블
  - `MEMBER_KEY`는 숫자 10자리(`년도2+캠퍼스2+과정2+일련4`) 형식만 집계 대상
- 출력:
  - JSON: `years`, `campusSeries`, `rows`, `totalMembers`
  - 화면: `memberKeyPopulationChart`(stacked bar), `memberKeyPopulationTableBody`(연도/캠퍼스/인원 표)
  - 임시 상태(2026-02-06): `dashboard.html`에서 카드(`memberKeyPopulationCard`)를 숨기고, `enableMemberKeyPopulation=false`로 API 호출을 중지
- 확인(근거):
  - 백엔드 컴파일: `cd polytech-lms-api && .\\gradlew.bat compileJava` 성공
  - 프론트 코드 경로 확인: `dashboard.html`의 `refreshMemberKeyPopulation()`, `renderMemberKeyPopulationTable()`, `memberKeyPopulationChart` 추가
- 최근 갱신: 2026-02-06

### FLOW-4001: 교수자 LMS > 과제 > 피드백 관리(학생 제출물 모달 확인)
- 사용자 동작(의도): 교수자가 “피드백 관리”에서 학생을 선택한 뒤, 학생이 제출한 과제 내용/첨부파일을 모달로 확인
- 진입점:
  - 화면(React): `project/components/CourseManagement.tsx` (과제 > 피드백 관리 탭)
  - API(JSP): `public_html/tutor_lms/api/homework_user_submission.jsp`
- 처리(핵심):
  - API는 `tutor_lms/api/init.jsp`에서 로그인/교수자 권한을 먼저 검사
  - 과목 권한: 관리자가 아니면 `LM_COURSE_TUTOR(type='major')`(주강사)만 조회 허용
  - 과제가 과목에 배치된 건인지 `LM_COURSE_MODULE(module='homework')`로 확인
  - 제출 본문: `LM_HOMEWORK_USER(subject/content/submit_yn/reg_date)` 조회(레코드가 없으면 빈값 반환)
  - 첨부파일: `CL_FILE(module='homework_{homework_id}', module_id={course_user_id})` 목록을 배열로 내려줌
- DB:
  - 과제 배치: `src/dao/CourseModuleDao.java` → `LM_COURSE_MODULE` (`course_id`, `module`, `module_id`, `status`)
  - 제출 본문: `src/dao/HomeworkUserDao.java` → `LM_HOMEWORK_USER` (`homework_id`, `course_user_id`, `subject`, `content`, `submit_yn`, `reg_date`, `status`)
  - 첨부파일: `src/dao/ClFileDao.java` → `CL_FILE` (`module`, `module_id`, `filename`, `status`)
- 출력:
  - JSON: 제출 제목/내용 + 파일 목록(`download_url=/classroom/download_cl.jsp?id=...&ek=...`)
  - React 모달: `project/components/HomeworkSubmissionDetailModal.tsx` (Dialog/ScrollArea)
  - 표시 규칙: 제출 제목/내용은 HTML 태그가 있으면 제거 후 “텍스트만” 표시(`<p>` 등 태그가 화면에 노출되지 않도록)
- 확인(근거):
  - React에서 “제출물 보기” 버튼 클릭 시 API 호출 및 모달 렌더링 코드 확인(`CourseManagement.tsx`)
  - 로컬 빌드: `cd project && npm run build` 성공(산출물 `public_html/tutor_lms/app/assets/*` 갱신)
- 최근 갱신: 2026-02-10

### FLOW-4002: 교수자 LMS > 차시관리 > 추천 탭 동영상 추가 시 시간/인정시간 자동 세팅
- 사용자 동작(의도): 교수자가 차시관리의 콘텐츠 라이브러리 `추천` 탭에서 동영상을 추가할 때, 목록 시간 표시와 인정시간 기본값이 자동으로 들어가야 함
- 진입점:
  - 추천 목록 API: `public_html/tutor_lms/api/content_recommend.jsp`
  - 레슨 업서트 API: `public_html/tutor_lms/api/kollus_lesson_upsert.jsp`
  - 프론트 매핑: `project/components/ContentLibraryModal.tsx` (`row.total_time` → `content.totalTime`)
- 처리(핵심):
  - 추천 응답의 `lessonId`는 데이터에 따라 `LM_LESSON.id`(숫자) 또는 콜러스 `media_content_key`(문자열)일 수 있음
  - `content_recommend.jsp`에서 숫자 `lessonId`는 `LM_LESSON.id` 조회 후 `start_url(media key)`로 정규화하고, `total_time/content_width/content_height`를 보완
  - 정규화 후 `TB_KOLLUS_MEDIA(media_content_key)`를 조회해 메타(시간/해상도/파일명)를 최종 보강
  - 그래도 시간이 비는 항목은 `TB_KOLLUS_TRANSCRIPT.duration_seconds`(초)를 분 단위(`ceil(seconds/60)`)로 변환해 `total_time`을 채움
  - `kollus_lesson_upsert.jsp`에서 기존 레슨 재사용 시에도 비어 있는 `total_time/complete_time/content_width/content_height`를 입력값으로 최소 보정
  - `kollus_lesson_upsert.jsp`에서 요청 `total_time`이 0이어도 `TB_KOLLUS_TRANSCRIPT`로 1회 보강해 신규/기존 레슨의 인정시간 자동세팅을 보장
  - 두 API 모두 디버깅 로그(`content_recommend`, `kollus_lesson_upsert`)를 남겨 누락 원인을 추적 가능하게 유지
- DB:
  - 추천 원본: `polytech-lms-api`의 `TB_RECO_CONTENT.lesson_id`
  - 전사 시간 소스: `TB_KOLLUS_TRANSCRIPT` (`media_content_key`, `duration_seconds`)
  - 레거시 레슨: `src/dao/LessonDao.java` → `LM_LESSON` (`id`, `start_url`, `lesson_type`, `total_time`, `complete_time`)
  - 콜러스 메타: `src/dao/KollusMediaDao.java` → `TB_KOLLUS_MEDIA` (`media_content_key`, `total_time`, `content_width`, `content_height`)
- 출력:
  - 추천 탭 시간 컬럼: `ContentLibraryModal`에서 `content.totalTime`이 분 단위로 표시됨
  - 차시 추가 직후 인정시간: 동영상 콘텐츠의 `completeTime` 기본값이 `totalTime`으로 자동 세팅됨
- 확인(근거):
  - 코드 경로 확인: `public_html/tutor_lms/api/content_recommend.jsp`, `public_html/tutor_lms/api/kollus_lesson_upsert.jsp`
  - API 호출 검증(로컬): 로그인 세션으로 `POST /tutor_lms/api/content_recommend.jsp(top_k=50)` 실행 시 `zero_total_time=0` 확인
  - API 호출 검증(키워드별): 빈값/NCS/메타버스/인터넷/영어/시험 키워드 모두 `rows=50, zero=0` 확인
  - 업서트 검증(로컬): `POST /tutor_lms/api/kollus_lesson_upsert.jsp(media_content_key=1nzZRwiX, total_time 미전달)` 호출 시 `LM_LESSON.total_time=11` 자동 보강 확인 후 테스트 데이터 상태복구
- 최근 갱신: 2026-02-11

### FLOW-4003: 교수자 LMS > 콘텐츠 라이브러리 추천 탭 자연어 검색 정렬(학생 검색형)
- 사용자 동작(의도): 교수자가 강의명/차시명/차시 설명을 입력하면, 학생 자연어 검색처럼 제목이 맞는 영상이 먼저 추천되어야 함
- 진입점:
  - JSP 프록시: `public_html/tutor_lms/api/content_recommend.jsp` → Spring API `POST /tutor/content-recommend/lessons`
  - 핵심 서비스: `polytech-lms-api/src/main/java/kr/polytech/lms/tutorcontentrecommend/service/TutorContentRecommendService.java`
- 처리(핵심):
  - 쿼리 구성은 `courseName`만 사용하고, `lessonTitle/lessonDescription/keywords/courseIntro/courseDetail`은 추천 질의에서 제외
  - 차시명이 `1차시/2차시`처럼 일반값일 때 품질 저하가 커서, 교수자 추천은 과목명 중심으로 고정
  - 1차 후보: `TB_RECO_CONTENT`에 대해 `title/keywords/summary LIKE` 키워드 검색(`RecoContentRepository.searchByKeyword`)
  - 2차 후보: `VectorQueryService.similaritySearchWithQueryTaskType()`로 `RETRIEVAL_QUERY` 벡터 검색 수행
  - 병합 정렬: 학생 검색과 동일하게 `제목 exact/contains/token` 우선으로 재정렬 후 벡터 점수로 tie-break
  - 프론트 전달 경로: `CurriculumTab`(학사/비정규 모두) → `WeeklyContentModal`/`EditContentModal`/`CurriculumEditor` → `ContentLibraryModal.recommendContext.courseName`으로 과목명을 항상 전달
  - 프록시 로그: `content_recommend.jsp`에 `request_context(course_name_len/lesson_title_len/context_fields)` 로그를 추가해 컨텍스트 누락 여부를 운영 로그에서 즉시 판별
  - 결과 제한: 최종 응답은 요청 `topK`까지만 반환(기존 응답 포맷 유지)
- DB:
  - `polytech-lms-api` `TB_RECO_CONTENT` (`title`, `summary`, `keywords`, `lesson_id`)
  - 벡터 메타 필터: `source == 'tb_reco_content'`
- 출력:
  - `content_recommend.jsp` 응답 `rst_data`는 기존과 동일(`lesson_id`, `title`, `summary`, `keywords`, `score`)
  - 정렬 체감만 학생 자연어 검색형으로 변경
- 확인(근거):
  - 정적 경로 확인: `TutorContentRecommendService`에 `keywordSearchFromDatabase`, `rerankByTitleMatch`, `mergeAndDedupeResults` 추가
  - 컴파일 확인: `cd polytech-lms-api && .\\gradlew.bat compileJava` 성공
  - 동작 검증(변경 반영 인스턴스): `POST http://localhost:18081/tutor/content-recommend/lessons`에서 같은 과목명(`전기전자기초`) + 다른 차시명(`1차시/반도체 공정/영어 회화`) 호출 시 상위 결과가 동일
  - API 검증(실호출): `POST http://localhost:8081/tutor/content-recommend/lessons`에 과목명(`전기전자기초/반도체 공정 실무/영어 커뮤니케이션/스마트팩토리 데이터분석`)별 호출 시 상위 결과 제목군이 서로 다름을 확인
  - API 검증(빈 컨텍스트): `courseName/lessonTitle/lessonDescription/keywords` 모두 빈값이면 `NCS기반교육과정개발...`, `영어...`, `OTT...` 등 고정 패턴이 재현됨(입력 누락 시 동일 추천 원인)
- 최근 갱신: 2026-02-11
