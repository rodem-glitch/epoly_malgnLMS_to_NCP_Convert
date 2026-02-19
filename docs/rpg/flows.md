# RPG-라이트: 기능 흐름 (`flows.md`)

최근 갱신: 2026-02-11

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-19 13:59

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
  - 로컬 검증: `/mypage/new_main/index.jsp` 응답 HTML에 `테스트 빠른 로그인` 영역 노출, `POST /member/login.jsp(id=kopo_pr01)` 응답이 `/tutor_lms/index.jsp` 복귀 스크립트 반환
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

### FLOW-1008: 강의실(학사 커리큘럼) 과제 `보기`는 차시 수강기간과 무관하게 이동
- 사용자 동작(의도): 강의실의 학사(정규) 커리큘럼에서 과제 항목 `보기`를 눌러 과제 글로 바로 이동
- 진입점:
  - 목록 렌더/클릭 처리(템플릿 JS): `public_html/html/classroom/index.html`
  - 서버 게이트(리다이렉트): `public_html/classroom/haksa_module.jsp` (`type=assignment`, `module_id`, `session_id`)
- 처리(핵심):
  - 차시 수강기간(`session.startDate~endDate`)이 현재 시간 밖이어도 과제는 `보기` 클릭 시 이동을 차단하지 않음
  - 시험(`type=exam`)과 동영상은 기존대로 차시 수강기간 밖이면 차단(동영상은 `haksa_video.jsp`, 시험은 `haksa_module.jsp`)
  - 서버에서도 동일 규칙을 한 번 더 적용: 시험은 차단 유지, 과제는 차단 대신 로그(`haksa_module [bypass] out_of_period ...`)만 남김
- DB:
  - 차시 기간 원천: `PolyCourseSettingDao`의 `curriculum_json` (DB 테이블/컬럼은 DAO 설정 기준)
  - 과제 화면 자체의 열람/제출 기간/권한은 `homework_view.jsp` 내부 로직(별도)에서 최종 제어됨
- 출력:
  - 과제 보기: `public_html/classroom/homework_view.jsp?id={module_id}`로 리다이렉트
- 확인(근거):
  - 기간 차단 제거/예외 처리 코드 확인: `public_html/html/classroom/index.html`, `public_html/classroom/haksa_module.jsp`
- 최근 갱신: 2026-02-12

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
  - 개발 모드(로컬): `npm run dev`/`npm run preview`로 Vite 서버를 띄운 경우에도 API(`/tutor_lms/api/*`)는 Resin(8080)으로 가야 합니다.
  - 설정: `project/vite.config.ts`의 `server.proxy`/`preview.proxy`에서 `/tutor_lms/api -> http://localhost:8080`
- UI(학생 메인 톤):
  - 토큰/팔레트: `project/styles/globals.css` (배경 #f9fafb, 포인트 #2b58e6, 보더 #e5e7eb)
  - 레이아웃: `project/App.tsx` (헤더/사이드바/콘텐츠 컨테이너를 카드 기반으로 정리)
- 출력:
  - 빌드 산출물: `public_html/tutor_lms/app/assets/*`
- 확인(근거):
  - 코드 분기 확인: `public_html/tutor_lms/index.jsp`, `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp`
  - 로컬 응답 확인: `curl /tutor_lms/index.jsp`가 `top.location.replace('/member/login.jsp?returl=...')`를 반환하고, `curl /member/login.jsp?returl=%2Ftutor_lms%2Findex.jsp`가 `302 -> /mypage/new_main/?login_required=Y&returl=%2Ftutor_lms%2Findex.jsp`를 반환함
- 최근 갱신: 2026-02-10

### FLOW-2002: 교수자 담당과목(학사 탭) 학기 코드 표시 변환
- 사용자 동작(의도): 교수자 `담당과목` 학사 탭에서 `10학기/20학기` 같은 코드형 학기 대신 사람이 읽는 학기명으로 확인
- 진입점:
  - 목록 API: `public_html/tutor_lms/api/course_list_combined.jsp`
  - 단건 조회 API(딥링크/직접열기): `public_html/tutor_lms/api/course_resolve.jsp`
- 처리(핵심):
  - `open_term` 원본 코드는 저장/조회 키(`course_code/open_year/open_term/bunban_code/group_code`)로 계속 유지
  - 표시 전용 변환 함수(`getHaksaOpenTermLabel`)를 추가해 `period_conv`만 학기명으로 변환
  - 매핑 기준: `10/1=1학기`, `11=여름학기`, `20/2=2학기`, `21=겨울학기`, `30=기타`
  - 미정의 코드는 숨기지 않고 원본값 그대로 노출해 데이터 이상을 확인 가능하게 유지
- DB:
  - 기존 동일: `LM_POLY_COURSE`(`open_year`, `open_term` 등) 및 연계 키 컬럼
  - `open_term` DB 원본값은 변경하지 않음
- 출력:
  - 목록/단건 JSON의 `period_conv`가 `YYYY-학기명` 형식으로 반환
  - 보조 표시 필드 `haksa_open_term_conv` 추가
- 확인(근거):
  - 코드 반영 위치 확인: `public_html/tutor_lms/api/course_list_combined.jsp`, `public_html/tutor_lms/api/course_resolve.jsp`
  - 로컬 확인 경로: `http://localhost:8080/tutor_lms` 담당과목 > 학사 탭, 그리고 `/tutor_lms/api/course_list_combined.jsp?tab=haksa`
- 최근 갱신: 2026-02-19

### FLOW-2003: 교수자 LMS 수강생/학습자 상세 조회(API 단일 책임)
- 사용자 동작(의도): 담당과목(수강생 탭) 또는 개설 단계(수강생 추가)에서 학생 1명을 눌렀을 때 상세 정보를 확인
- 진입점: `public_html/tutor_lms/api/student_detail.jsp`
- 처리(핵심):
  - 입력값 검증: `user_id` 필수
  - `course_id`가 있으면 과목 존재 여부 확인 후 권한을 수강생 목록 API와 동일 기준으로 검증
    - 주강사(`LM_COURSE_TUTOR.type='major'`) 또는
    - 과정담당자(`LM_COURSE_MANAGER`) 또는
    - 개설자(`LM_COURSE.manager_id`) 또는
    - 관리자(S/A)
  - 상세 데이터는 `TB_USER` + `TB_USER_DEPT` + `LM_COURSE_USER(선택 조인)`로 구성
  - 부서 경로(`dept_path`)를 `UserDeptDao.getTreeNames`로 계산
  - 상세 API는 데이터 조회만 담당하고, 개인정보 로그는 기존 `privacy_log.jsp` 경로에서 별도로 기록
- DB:
  - 학습자 기본정보: `src/dao/UserDao.java` → `TB_USER`
  - 부서/학과 정보: `src/dao/UserDeptDao.java` → `TB_USER_DEPT`
  - 과목 수강 상태: `src/dao/CourseUserDao.java` → `LM_COURSE_USER` (`status NOT IN (-1, -4)`)
- 출력:
  - JSON: `rst_data`(학생 상세)
- 확인(근거):
  - 정적 확인: `public_html/tutor_lms/api/student_detail.jsp`에서 입력검증/권한검증/조회 순서 확인
  - 호출 경로 기준: `/tutor_lms/api/student_detail.jsp?course_id={courseId}&user_id={userId}`
- 최근 갱신: 2026-02-19

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

### FLOW-4101: Firebase `web.app` 경유 로그인 세션 + 신규메인 추천영상 복구
- 사용자 동작(의도): `https://epoly-kopo.web.app`로 접속해 로그인 후 신규메인 추천(프롬프트/영상)을 정상 사용
- 진입점:
  - Hosting rewrite: `tools/gcp/firebase-proxy-deploy/firebase.json`
  - 프록시 함수: `tools/gcp/firebase-proxy-deploy/functions/index.js`
  - 추천 영상 API 브리지: `public_html/mypage/new_main/reco_video_list.jsp`
  - 운영 스택 템플릿: `tools/gcp/templates/docker-compose.yml.tpl`
- 처리(핵심):
  - Functions 프록시를 `fetch` 기반에서 저수준 HTTP 프록시로 변경해 쿠키 헤더 전달을 제어
  - `MLMS*`, `JSESSIONID`를 `__session` 쿠키에 번들링/복원해 Firebase Hosting 경유 시에도 레거시 로그인 세션 유지
  - 프록시 응답 헤더에서 `Content-Type: text/html; charset=US-ASCII`가 내려오면 `charset=utf-8`으로 교정해 `교수자 LMS` 탭 제목 한글 깨짐을 방지
  - `reco_video_list.jsp`는 `POLYTECH_LMS_API_BASE` 누락 시 즉시 로그를 남기고 빈 결과 반환(침묵 실패 방지)
  - Resin 컨테이너에 `POLYTECH_LMS_API_BASE=http://api:8081` 주입해 JSP->Spring API 내부 호출 고정
  - Resin 런타임 로그 경로(`/data/log`)가 없으면 예외 기록 시 추가 예외가 터지므로 배포 단계에서 `public_html/data/log`를 생성/권한 보정
- DB:
  - 추천 조회는 내부 Spring API(`/student/content-recommend/home`)가 MySQL(`TB_RECO_CONTENT` 등) + Qdrant를 사용
  - JSP 쪽 직접 DB 업데이트 없음(프롬프트 저장 제외)
- 출력:
  - 로그인 후 `GET /mypage/new_main/reco_prompt.jsp` -> `{"ok":true,...}`
  - `GET /mypage/new_main/reco_video_list.jsp` -> 추천 `items[]` 반환
- 확인(근거):
  - Functions 배포: `firebase deploy --only functions:vmproxy`
  - 헤더 확인: `curl -I https://epoly-kopo.web.app/tutor_lms/app/index.html`에서 `Content-Type: text/html; charset=utf-8` 확인
  - 운영 반영 확인: `Set-Cookie: __session=...` 응답 확인, `Cookie: __session=...`로 `reco_prompt.jsp` 정상 응답 확인
  - 추천 장애 원인 확인: `/student/content-recommend/home` 500 시 `lms-api` 로그에 `Generative Language API ... disabled (project 351209535185)` 확인
  - 복구 확인: `virtualclass-2ee22` 프로젝트에서 `generativelanguage.googleapis.com` 활성화 후 `/student/content-recommend/home` 200, `reco_video_list.jsp` 추천 타이틀 4건 확인
- 최근 갱신: 2026-02-12

### FLOW-4102: 신규메인/공통 레이아웃 로그인 모달 기본값 통일
- 사용자 동작(의도): 이러닝/채용 등 하위 메뉴 로그인 모달에서도 아이디/비밀번호 기본값이 동일하게 보이도록 통일
- 진입점:
  - 학생 신규메인: `public_html/html/mypage/new_main_full.html`
  - 공통 레이아웃: `public_html/html/layout/layout_new_main.html`
  - 교수자 진입 게이트: `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp` (`returl=/tutor_lms/...` 분기)
- 처리(핵심):
  - 학생 모달 기본값: `id=kopo_st01`, `passwd=Growai!2026`
  - 공통 레이아웃 로그인 모달(`layout_new_main`)은 화면 하드코딩이 아니라 서버 변수(`login_returl`, `login_id_preset`, `login_passwd_preset`)를 사용
  - `openLoginModal()/closeLoginModal()`에서 `applyDefaultLoginPreset()`로 서버 프리셋을 재적용해, 재오픈 시에도 값이 일관되게 유지
  - 교수자(`tutor_lms` 등)는 `returl` 분기에서 기본 아이디를 `kopo_pr01`로 서버 주입
- 출력:
  - 신규메인/공통 레이아웃 로그인 모달에서 기본 계정 자동 입력
  - 사용자는 아이디 끝 숫자만 수정해 바로 로그인 가능
- 확인(근거):
  - `public_html/init.jsp`에서 `login_returl`, `login_id_preset`, `login_passwd_preset` 기본값 주입 확인
  - `public_html/html/mypage/new_main_full.html`이 `{{login_id_preset}}`, `{{login_passwd_preset}}` 기반으로 입력값을 세팅(`applyDefaultLoginPreset`)하는지 확인
  - `public_html/html/layout/layout_new_main.html`이 `{{login_returl}}`, `{{login_id_preset}}`, `{{login_passwd_preset}}` 기반으로 입력값을 세팅(`applyDefaultLoginPreset`)하는지 확인
  - `public_html/member/login.jsp` / `public_html/mypage/new_main/index.jsp`에서 `returl`에 `/tutor_lms/` 포함 시 `kopo_pr01` 주입 확인
- 최근 갱신: 2026-02-12

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

### FLOW-4004: 교수자 LMS > 과제 관리(교수자 첨부파일 확인/다운로드/삭제/재업로드)
- 사용자 동작(의도): 과제 부여 시 교수자가 올린 첨부파일을 과제 관리에서 확인하고, 다운로드/파일삭제/재업로드까지 수행
- 진입점:
  - 목록 API: `public_html/tutor_lms/api/homework_list.jsp`
  - 수정 API: `public_html/tutor_lms/api/homework_modify.jsp`
  - 삭제 API: `public_html/tutor_lms/api/homework_delete.jsp`
  - 다운로드 엔드포인트: `public_html/main/download_file.jsp`
- 처리(핵심):
  - `homework_list.jsp`에서 `LM_HOMEWORK.homework_file`을 함께 조회하고, 다운로드용 `homework_file_conv/homework_file_ek/homework_file_download_url`을 응답에 포함
  - `homework_modify.jsp`는 `delete_homework_file_yn=Y`를 받으면 첨부파일만 삭제(파일시스템+DB 컬럼 비움)
  - `homework_modify.jsp`에 새 파일(`homework_file`)이 오면 기존 파일을 정리하고 새 파일로 교체(재업로드)
  - `homework_delete.jsp`는 과제가 다른 과목에서 더 이상 참조되지 않을 때(`LM_COURSE_MODULE` 0건) 과제 상태 `-1` 처리와 함께 첨부파일 물리 삭제
  - 다운로드는 `download_file.jsp`의 기존 보안 규칙(`ek = encrypt(file + yyyyMMdd)`)을 그대로 사용
- DB:
  - 과제 본문/첨부: `src/dao/HomeworkDao.java` → `LM_HOMEWORK` (`homework_file`, `status`)
  - 과목 배치: `src/dao/CourseModuleDao.java` → `LM_COURSE_MODULE`
  - 제출내역 보호(삭제 차단): `src/dao/HomeworkUserDao.java` → `LM_HOMEWORK_USER`
- 출력:
  - `homework_list.jsp` JSON: 기존 과제 목록 + `homework_file_*` 다운로드 메타
  - `homework_modify.jsp` JSON: 기존 성공코드 유지(`0000`)
  - `homework_delete.jsp` JSON: 기존 성공코드 유지(`0000`)
- 확인(근거):
  - 코드 확인: `public_html/tutor_lms/api/homework_list.jsp`, `public_html/tutor_lms/api/homework_modify.jsp`, `public_html/tutor_lms/api/homework_delete.jsp`, `public_html/main/download_file.jsp`
  - 로컬 호출 확인: 비로그인 상태에서 각 API가 `4010` JSON을 반환(컴파일/라우팅 정상)
- 최근 갱신: 2026-02-19

### FLOW-4005: 교수자 LMS > 과제 관리(동일 과제 다중 강의 동시 등록)
- 사용자 동작(의도): 교수자가 동일한 과제를 여러 강의에 한 번에 등록
- 진입점: `public_html/tutor_lms/api/homework_insert.jsp`
- 처리(핵심):
  - 입력: 단일 `course_id`와 복수 `course_ids`(쉼표 구분)를 함께 지원
  - `course_id/course_ids`를 합쳐 중복 제거 후, 과목별로 권한/존재 여부를 개별 검증
  - 권한/존재 검증 통과 과목만 `validCourseIds`로 분리
  - 과제 본문(`LM_HOMEWORK`)은 1건 생성 후, 과목 배치(`LM_COURSE_MODULE`)를 유효 과목 수만큼 반복 생성
  - 일부 과목 실패 시에도 성공 과목은 반영하고, 실패 과목은 `rst_failed_courses`로 반환(부분 성공)
  - 전 과목 배치 실패면 과제 상태를 `-1`로 되돌리고 업로드 파일도 정리
- DB:
  - 과제 본문: `src/dao/HomeworkDao.java` → `LM_HOMEWORK`
  - 과목 배치: `src/dao/CourseModuleDao.java` → `LM_COURSE_MODULE`
  - 권한 체크: `src/dao/CourseTutorDao.java` → `LM_COURSE_TUTOR` (`type='major'`)
- 출력:
  - JSON: `rst_data`(homework_id), `rst_inserted_course_count`, `rst_success_courses`, `rst_failed_courses`, `rst_invalid_tokens`
- 확인(근거):
  - 코드 확인: `public_html/tutor_lms/api/homework_insert.jsp` (course_ids 파싱/과목별 검증/부분성공 응답)
  - 로컬 호출 확인: 비로그인 상태에서 `4010` JSON 반환(컴파일/라우팅 정상)
- 최근 갱신: 2026-02-19

### FLOW-4006: 교수자 LMS > 과제 관리(제출첨부 허용 파일형식 옵션)
- 사용자 동작(의도): 교수자가 과제별로 학생 제출 첨부파일 허용 형식(프리셋/직접입력)을 선택
- 진입점:
  - 교수자 등록/수정/조회 API: `public_html/tutor_lms/api/homework_insert.jsp`, `public_html/tutor_lms/api/homework_modify.jsp`, `public_html/tutor_lms/api/homework_list.jsp`
  - 학생 실제 업로드 저장 API: `public_html/classroom/file_upload.jsp`
- 처리(핵심):
  - 과제 저장 시 `submit_file_ext_mode`(ALL/DOC/IMAGE/ARCHIVE/AUDIO/CUSTOM), `submit_file_exts`를 검증/정규화해 `LM_HOMEWORK`에 저장
  - `CUSTOM` 모드는 기본 화이트리스트(레거시 업로드 허용 확장자 집합) 내부 값만 저장
  - 과제 목록 조회에서 허용 모드/확장자(`submit_file_*`)를 함께 반환해 수정 모달 초기값으로 사용
  - 학생 업로드(`file_upload.jsp`)는 `md=homework_{id}` / `md=homework_task_{tid}`를 해석해 과제 설정을 조회하고, 서버 `f.addElement(... allow:'...')`로 최종 차단
  - 설정이 비정상(잘못된 모드/빈 custom)인 경우 업로드를 성공 처리하지 않고 즉시 오류 반환
- DB:
  - `src/dao/HomeworkDao.java` → `LM_HOMEWORK.submit_file_ext_mode`, `LM_HOMEWORK.submit_file_exts`
  - DDL: `public_html/ddl_homework_submit_file_ext.sql`
- 출력:
  - 교수자 API JSON: `submit_file_ext_mode`, `submit_file_exts`, `submit_file_allow_ext`, `submit_file_allow_ext_conv`
  - 업로드 API JSON: 기존 `{"success":true/false}` 포맷 유지
- 확인(근거):
  - 코드 확인: `src/dao/HomeworkDao.java`, `public_html/tutor_lms/api/homework_insert.jsp`, `public_html/tutor_lms/api/homework_modify.jsp`, `public_html/tutor_lms/api/homework_list.jsp`, `public_html/classroom/file_upload.jsp`
  - 로컬 호출 확인: 비로그인 상태에서 `homework_insert.jsp`가 `4010` JSON 반환(라우팅/컴파일 정상)
- 최근 갱신: 2026-02-19

### FLOW-4007: 교수자 LMS > 과제 > 피드백 템플릿(조회/저장/삭제)
- 사용자 동작(의도): 교수자가 과제 피드백에서 자주 쓰는 문구를 템플릿으로 저장하고, 필요할 때 빠르게 불러와 재사용
- 진입점:
  - 조회 API: `public_html/tutor_lms/api/homework_feedback_template_list.jsp`
  - 저장 API: `public_html/tutor_lms/api/homework_feedback_template_save.jsp`
  - 삭제 API: `public_html/tutor_lms/api/homework_feedback_template_delete.jsp`
- 처리(핵심):
  - 세 API 모두 `tutor_lms/api/init.jsp`를 통해 로그인/교수자 권한을 먼저 검사
  - 과목 존재(`LM_COURSE`) + 담당교수(`LM_COURSE_TUTOR.type='major'`) 권한을 재검증해 타 과목 접근을 차단
  - 템플릿 저장은 단건 생성/수정 방식(`id` 유무)으로 처리하며, 서버에서 개수 제한은 두지 않음
  - 템플릿 본문은 필수/길이 제한(2000자)과 base64 이미지 차단 검증을 수행
  - 삭제는 물리 삭제가 아니라 `status=-1` 소프트 삭제
  - 운영 추적을 위해 `tutor_homework_feedback_template` 로그에 시작/권한실패/성공 이벤트를 남김
- DB:
  - DAO: `src/dao/HomeworkFeedbackTemplateDao.java`
  - 테이블: `LM_HOMEWORK_FEEDBACK_TEMPLATE` (`site_id`, `course_id`, `manager_id`, `sort`, `content`, `status`, `reg_date`, `mod_date`)
  - DDL: `public_html/ddl_homework_feedback_template.sql`
- 출력:
  - 조회: `rst_data` 템플릿 목록(`id/sort/content/content_preview/...`)
  - 저장: `rst_data` 저장된 템플릿 ID
  - 삭제: `rst_data` 삭제된 템플릿 ID
- 확인(근거):
  - 코드 확인: `public_html/tutor_lms/api/homework_feedback_template_list.jsp`, `public_html/tutor_lms/api/homework_feedback_template_save.jsp`, `public_html/tutor_lms/api/homework_feedback_template_delete.jsp`, `src/dao/HomeworkFeedbackTemplateDao.java`
  - 정적 흐름 확인: 기존 과제 피드백 저장 API(`homework_feedback_update.jsp`)와 분리되어 템플릿 CRUD만 담당함
- 최근 갱신: 2026-02-19

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

### FLOW-5001: GCP Linux VM + Firebase Hosting 원클릭 자동 셋업
- 사용자 동작(의도): 사용자가 스크립트 1회 실행으로 `www(Firebase 짧은 링크)`와 `VM(Resin JSP + Spring API + MySQL + Qdrant)`을 배포하고, 필요 시 기존 DB까지 자동 이관
- 진입점:
  - 윈도우 실행: `tools/gcp/start-one-click.bat`
  - 메인 자동화: `tools/gcp/one-click-setup.ps1`
- 처리(핵심):
  - `gcloud`/`firebase` 로그인(브라우저 승인) 후 프로젝트 설정
  - GCP API 활성화(Compute/Firebase Hosting/DNS/IAM)
  - 고정 IP(`{vmName}-ip`) 확인/생성 + VM 생성(Ubuntu 22.04)
  - `polytech-lms-api`를 `bootJar`로 빌드해 VM 배포 번들 생성
  - 배포 번들은 실행마다 `tools/gcp/generated/stack-YYYYMMDD-HHmmss`로 고유 폴더를 생성하고, 원격 실행도 같은 폴더를 직접 지정해 이전 `~/stack` 잔여물 오배포를 방지
  - VM 전송은 디렉터리 재귀 SCP 대신 `stack-*.tar.gz` 단일 아카이브를 업로드한 뒤 원격에서 압축 해제해, 대량 파일 전송 시간을 단축
  - VM에 Docker 스택(`MySQL + Qdrant + Spring API + Resin`)과 Nginx 리버스 프록시 배포
  - 원격 배포 스크립트는 이미 패키지가 설치된 VM이면 `apt update/install`을 건너뛰고, Docker 이미지는 기본값으로 `pull` 없이 즉시 재기동(필요 시 `DEPLOY_FORCE_PULL=true`로 강제 pull)
  - Nginx 경로 분기:
    - 기본 화면(`/`, `/mypage/*`, `/member/*`, `/tutor_lms/*`)은 Resin(8080)
    - API 경로(`/statistics/*`, `/student/*`, `/tutor/*`, `/job/*`, `/actuator/*`)는 Spring API(8081)
  - VM Resin은 `public_html` + `WEB-INF/classes`를 기본으로 사용하고, 필요한 클래스가 없으면 `legacy/src`를 기준으로 런타임 컴파일
  - 런타임 컴파일 대상 `src/dao/CourseSectionDao.java`의 `item("section_id", ...)`는 `Integer/int` 혼합 삼항식이면 오버로드 충돌로 컴파일 실패할 수 있어 `int`로 먼저 확정
  - API 컨테이너는 `SPRING_DATASOURCE_*`, `SPRING_AI_VECTORSTORE_QDRANT_*` 환경변수로 운영값을 강제 주입해 JAR 내부 `application-local.yml` 오버라이드를 방지
  - 통계 기능 엑셀 원본(`통계/`)을 배포 번들에 포함하고 API 컨테이너 `/data/statistics`로 마운트
  - API에 `STATISTICS_MAJOR_INDUSTRY_FILE`, `STATISTICS_EMPLOYMENT_FILE`, `STATISTICS_ADMISSION_FILE`, `STATISTICS_STUDENT_POPULATION_FILE`을 절대경로(`/data/statistics/*.xlsx`)로 주입
  - GitHub Actions 체크아웃에서 `통계/`가 제외되면 one-click이 `통계 폴더를 찾지 못했습니다`로 즉시 실패하므로, `.gitignore`에서 `통계` 제외 규칙을 두지 않고 `통계/*.xlsx`를 추적 상태로 유지
  - `-EnableDbMigration` 사용 시:
    - `-SourceDbDumpPath`가 있으면 해당 dump를 사용
    - 없으면 `mysqldump`로 소스 DB를 로컬에서 dump 생성
    - import 전 dump를 자동 보정(`LM_COURSE`/`LM_COURSE_USER` 컬럼 수 보정, `DEFINER=` 제거)
    - 소스 계정 `PROCESS` 권한이 없는 환경을 위해 `mysqldump --no-tablespaces` 적용
    - MySQL 함수 생성 오류(1418) 방지를 위해 import 직전 `log_bin_trust_function_creators=1`을 root 계정으로 설정
    - 배포 번들 `migration/source.sql`로 전송 후, VM 배포 단계에서 자동 import(`DB_IMPORT_ON_DEPLOY=true`)
  - 초기에는 Firebase Hosting을 `302 리다이렉트`로 배포하되, 주소 유지가 필요하면 `tools/gcp/firebase-proxy-deploy`(Hosting rewrite + Functions vmproxy)로 전환
  - `tools/gcp/firebase-proxy-deploy/public`은 Git 추적용 파일이 필요하지만, `index.html`을 두면 루트(`/`)가 정적 파일로 먼저 매칭되어 rewrite가 우회되므로 non-index placeholder만 유지
  - GitHub Actions(`.github/workflows/deploy-lms-gcp.yml`)는
    - 사전 점검 단계에서 `GCP_SA_KEY/GCP_PROJECT_ID/GCP_VM_SSH_USER/LMS_DB_PASSWORD/LMS_DB_ROOT_PASSWORD/QDRANT_API_KEY/GOOGLE_API_KEY/GEMINI_API_KEY` 누락 시 즉시 실패
    - VM 배포: `one-click-setup.ps1 -SkipProjectBootstrap -SkipFirebaseDeploy -VmSshUser`
    - VM 배포 뒤 `gcloud compute addresses describe polytech-lms-vm-ip`로 현재 고정 IP를 조회해 `tools/gcp/firebase-proxy-deploy/functions/index.js`의 `TARGET`을 자동 치환
    - Firebase 배포: `tools/gcp/firebase-proxy-deploy`의 `functions:vmproxy,hosting` 별도 배포
    - Firebase 인증은 `FIREBASE_TOKEN`이 있으면 토큰, 없으면 `google-github-actions/auth`가 내보낸 서비스 계정(ADC)으로 진행
    순서로 실행해 Hosting 덮어쓰기 충돌을 방지
  - CI 무대기 보강:
    - `gcloud compute scp/ssh`에 `BatchMode` 플래그 적용
    - 원격 배포 실행을 `sudo -n`으로 강제해 비밀번호 프롬프트 대기를 실패로 즉시 노출
  - CI SSH 계정 보강:
    - 워크플로우에 `VM SSH 권한 사전 점검` 단계를 두어 `id -u` 또는 `sudo -n true`가 통과하는 계정을 먼저 탐색하고(`secret -> gcloud account -> newkl -> ubuntu -> root`), 성공 계정을 one-click에 주입
    - SSH 시도에는 `ConnectTimeout=15`를 명시해 네트워크 단절/방화벽 상황에서 장시간 멈추지 않고 빠르게 다음 후보로 진행
    - one-click에 `-VmSshUser`가 명시되면 해당 계정만 사용해 배포하고, 후보 순회는 건너뜁니다(실패 원인 마스킹 방지).
    - `Deploy-StackToVm`은 VM 접속 계정을 단일값으로 고정하지 않고 `-VmSshUser`(지정값) → `gcloud config account` 사용자 → `newkl` → `ubuntu` → 인스턴스 기본호스트 순으로 명시적 시도
    - 모든 후보 실패 시 시도한 대상 목록과 마지막 오류를 함께 던져 재현 가능한 실패 로그를 남김
    - 인스턴스 메타데이터 `ssh-keys`에 등록된 사용자도 후보 목록에 포함해 커스텀 운영 계정 누락을 줄임
    - 원격 실행은 `id -u`/`sudo -n true`를 먼저 점검해 `root 직접 실행` 또는 `무비밀번호 sudo 실행` 경로만 허용
  - CI 재시도/진단 보강:
    - `원클릭 배포 실행 (VM 스택)` 단계는 one-click 호출을 1회만 실행하고, step 타임아웃(`timeout-minutes: 6`)으로 장기대기를 강제 종료
    - one-click 단계를 `continue-on-error`로 실행하고 실패 메시지 첫 줄(`error_head`)을 step output으로 기록
    - `Deploy-StackToVm`의 원격 `deploy-stack.sh` 실행은 `timeout 360`으로 감싸 6분 이상 지연 시 즉시 실패 처리
    - one-click 실패 시 후속 진단 단계(`로컬 JAR 존재`, `VM SSH 재확인`, `VM sudo 재확인`, `원격 배포 스크립트 존재`)를 실행해 API step 상태만으로도 실패 지점을 좁힌 뒤, 마지막 `원클릭 실패 종료` 단계에서 명시적으로 실패 처리
  - CI 인자 바인딩 보강:
    - one-click 호출은 배열 스플랫(`@args`) 방식에서 스위치 파라미터(`-SkipProjectBootstrap`, `-SkipFirebaseDeploy`)가 밀려 `SourceDbPort` 바인딩 오류를 낼 수 있어, 명시적 파라미터 호출 형태를 유지
  - `Build-ApiJar`는 Linux 러너에서 `bash ./gradlew bootJar -x test`를 사용해 실행권한 비트 누락(`chmod +x` 미반영)에도 빌드가 진행되도록 보강
  - `Build-ApiJar`는 빌드 산출물 검색을 `build/libs/*.jar` 경로로 통일해(백슬래시 경로 의존 제거) Windows/ubuntu 모두 동일하게 JAR을 찾습니다.
  - 실행 결과를 `tools/gcp/generated/setup-summary.txt`에 저장(도메인 A레코드 값/민감정보 포함)
- DB:
  - MySQL 컨테이너(`mysql:8.4`) + Qdrant 컨테이너(`qdrant/qdrant:v1.15.3`)
  - Spring API는 `.env`로 `DB_URL/DB_USERNAME/DB_PASSWORD/QDRANT_*` 값을 주입받아 실행
- 출력:
  - 배포 스크립트/템플릿: `tools/gcp/templates/*.tpl`
  - 실행 산출물(민감정보): `tools/gcp/generated/*`
- 확인(근거):
  - 실실행: `powershell -File tools/gcp/one-click-setup.ps1 -ProjectId gen-lang-client-0343478566 -FirebaseSite epoly-kopo -WwwDomain epoly-kopo.web.app -SkipProjectBootstrap`
  - 배포 로그 확인: `Container lms-resin Started`, `Container lms-api Started`, `Deploy complete! Hosting URL: https://epoly-kopo.web.app` 확인
  - 원격 상태 확인: `docker ps`에서 `lms-resin/lms-api/lms-mysql/lms-qdrant` 모두 `Up` 확인
  - 전량 이관 검증(소스 192.168.0.55 vs 타깃 VM):
    - MySQL row count 일치: `tb_user=1064`, `lm_course=165`, `tb_reco_content=3000`, `tb_kollus_transcript=3000`
    - Qdrant point count 일치: `video_summary_vectors_gemini=3014`, `video_summary_vectors=131`
  - URL 유지 모드 검증:
    - `firebase deploy --only functions,hosting` (`tools/gcp/firebase-proxy-deploy`) 후 `curl -I https://epoly-kopo.web.app/` -> `301 Location: /mypage/new_main/index.jsp`
    - `curl -I https://epoly-kopo.web.app/mypage/index.jsp` -> `302 Location: https://epoly-kopo.web.app/mypage/new_main/?login_required=Y...` (IP로 변경되지 않음)
    - `POST https://epoly-kopo.web.app/tutor/content-recommend/lessons` -> `200` + 추천 JSON 응답 확인
    - `GET https://epoly-kopo.web.app/actuator/health` -> `200`, `{\"status\":\"UP\"}`
  - 통계 파일 반영 검증:
    - 반영 전 `GET https://epoly-kopo.web.app/statistics/api/internal/employment/top?top=3` -> `500` (`통계 파일을 찾을 수 없습니다`)
    - 반영 후 동일 호출 -> `200` + 학과별 취업률 JSON 응답
  - CI 복구 검증:
    - GitHub Actions `Firebase vmproxy + Hosting 배포` 실패 원인(`Directory 'public' for Hosting does not exist`) 확인
    - `tools/gcp/firebase-proxy-deploy/public/vmproxy-placeholder.txt` 추적으로 워크플로 재실행 기준 `public` 디렉터리 누락 재발 방지(`index.html`은 rewrite 우회 이슈로 미사용)
    - 워크플로 문법 검증: `npx -y js-yaml .github/workflows/deploy-lms-gcp.yml` 성공
    - vmproxy 함수 문법 검증: `node --check tools/gcp/firebase-proxy-deploy/functions/index.js` 성공
    - API JAR 빌드 검증: `cd polytech-lms-api && .\\gradlew.bat bootJar -x test` 성공
    - one-click DryRun 검증: `pwsh -NoLogo -NoProfile -Command ". ./tools/gcp/one-click-setup.ps1 -DryRun -SkipProjectBootstrap -SkipVmProvision -SkipFirebaseDeploy -ProjectId test-polytech"` 실행 완료
    - 워크플로 호출 재현 검증: 배열 스플랫(`@args`) 호출 시 `SourceDbPort` 변환 오류 재현, 명시적 파라미터 호출(`-DryRun`)로 정상 동작 확인
    - run(#26) 로그 검증: `원클릭 배포 실행` 단계에서 `통계 폴더를 찾지 못했습니다: /home/runner/work/polytech-lms/polytech-lms/통계` 확인 후 `.gitignore`/`통계/*.xlsx` 반영
  - 장애 복구 확인:
    - 초기에 Resin이 `WEB-INF/work` 쓰기권한 부족으로 500 발생
    - `deploy-stack.sh.tpl`에 `WEB-INF/work` 권한 보정 추가 후 정상화 확인
    - `resin-web.xml.tpl`에서 VM `src` 소스 컴파일 제거 후 `CourseSectionDao` 컴파일 오류 재발 방지
    - MySQL을 `--lower_case_table_names=1`로 재기동해 Linux 대소문자 충돌(`TB_RECO_CONTENT` 미인식) 재발 방지
- 최근 갱신: 2026-02-11
