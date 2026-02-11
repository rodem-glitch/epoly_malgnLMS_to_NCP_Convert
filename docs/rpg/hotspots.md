# RPG-라이트: 핫스팟/주의사항 (`hotspots.md`)

최근 갱신: 2026-02-11

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-11 18:02

- Resin 설정: resin/resin.xml (root-directory=public_html)
- React 배포: public_html/tutor_lms/app (project 빌드 산출물)
- Spring Boot 설정: polytech-lms-api/src/main/resources/application.yml, application-local.yml
- Spring Boot DB/외부연동: polytech-lms-api/build.gradle 의존성(JPA/MySQL/Google/Spring AI 등)
<!-- @generated:end -->

## 자동 생성(권장)
- 아래 명령을 실행하면 “자동 요약”이 갱신됩니다.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File tools/rpg/generate.ps1`

## 목적
- “여기 건드리면 운영에 영향이 큰 곳”을 모아두는 체크리스트입니다.
- 에이전트가 수정 범위를 줄이고, 검증을 빠뜨리지 않게 돕습니다.

## 기본 핫스팟(자주 터짐)
- 권한/세션 공통: `public_html/init.jsp`, `public_html/sysop/init.jsp`
- 멀티사이트 범위: `site_id` 누락 여부(조회/수정 모두)
- 상태값 관례: `status`의 의미(테이블마다 다를 수 있으니 항상 확인)
- 템플릿 렌더링: JSP의 `p.setVar()/p.setLoop()` ↔ 템플릿 `.html` 변수/루프 매칭
- 파일 업로드/경로: `public_html/data/` 및 저장 경로/권한
- 교수자 LMS 과제 제출물 첨부파일(주의):
  - 교수자 과제 제출 첨부는 `TB_FILE`이 아니라 `CL_FILE`에 저장되는 흐름이 있습니다(과제 모듈).
  - 다운로드 링크는 `/classroom/download_cl.jsp?id=...&ek=...`를 사용하며, `ek`는 보통 `m.encrypt(id)` 또는 `m.encrypt(id + yyyyMMdd)` 패턴입니다.
  - 관련 코드: `public_html/tutor_lms/api/homework_user_submission.jsp`, `public_html/tutor_lms/api/homework_submit_cancel.jsp`, `public_html/classroom/download_cl.jsp`
- Resin 실행 conf 경로:
  - IntelliJ 실행 기준은 `.idea/runConfigurations/Resin.xml`의 `SCRIPT_OPTIONS`입니다.
  - 현재 기준값은 `console --conf C:\Users\newkl\Desktop\resin-4.0.67\resin-4.0.67\conf\resin.xml`입니다.
  - 외부 conf(`resin-4.0.67/conf/resin.xml`)의 ROOT 웹앱 경로가 `C:\Users\newkl\Desktop\polytech-lms\public_html`인지 반드시 확인합니다.
  - ROOT 웹앱 경로가 다른 저장소(예: 구 `MalgnLMS`)를 가리키면 `/mypage/*`가 구 로그인으로 빠지거나 404가 발생합니다.
- Resin 클래스패스/컴파일:
  - `public_html/WEB-INF/classes`가 비어 있으면 JSP 컴파일 시 `package dao does not exist`로 `/mypage/*`가 500이 납니다.
  - 로컬 실행은 `public_html/WEB-INF/resin-web.xml`의 `compiling-loader(source=C:/Users/newkl/Desktop/polytech-lms/src)`를 기준으로 유지합니다.
  - 수동 컴파일이 필요하면 Resin lib를 classpath에 포함해 `src/dao`, `src/malgnsoft`를 `public_html/WEB-INF/classes`로 컴파일합니다.
  - 확인 근거: `curl -s /mypage/new_main/index.jsp` 응답에 `import dao.*` 컴파일 에러가 출력되면 클래스 누락 상태입니다.
- Resin JNDI(DB) 누락:
  - 증상: `/mypage/new_main/index.jsp`가 `200`인데 본문이 비거나(`Content-Length: 12/0`) 흰 화면으로 보입니다.
  - 원인: `jdbc/malgn` DataSource가 null이면 `public_html/init.jsp` siteinfo 조회가 실패하고 조기 종료됩니다.
  - 기준 설정: `public_html/WEB-INF/resin-web.xml`에 `jdbc/malgn`, `jdbc/lms`를 모두 정의해 둡니다.
  - 확인 근거: `public_html/data/log/error_YYYYMMDD.log`에 `DataSource.jndi jdbc/malgn` + `ds is null` 로그가 보이면 JNDI 누락입니다.
- 개인정보 동의(게이트/버전):
  - 동의 화면 재사용: `public_html/member/privacy_agree.jsp` (`ag=sso|cert`)
  - 리다이렉트 안전: `returl`은 외부 URL 차단/검증 필수(오픈 리다이렉트 방지)
  - 운영 준비: 이미지가 없으면 화면이 차단됨(폴백 금지 정책)
    - SSO: `/common/images/consent/consent_sso_1.png` 또는 `/common/images/consent/consent_sso_2.png` (둘 다 없으면 차단)
    - 증명서: `/common/images/consent/consent_cert_1.png` 또는 `/common/images/consent/consent_cert_2.png` (둘 다 없으면 차단)
  - 이력: `TB_AGREEMENT_LOG`에 `type/module` 조합으로 버전 관리(`sso_20260120`, `cert_20260120`)
- 로그인 게이트/모달(권한·세션):
  - `/mypage/*` 공통 진입은 `public_html/mypage/init.jsp`에서 먼저 가드됩니다. 이 지점이 `auth.loginForm()`로 되돌아가면 다시 구 로그인 화면으로 빠질 수 있습니다.
  - 구 로그인 페이지 렌더 대신 `public_html/member/login.jsp` GET에서 `/mypage/new_main/?login_required=Y&returl=...`로 우회합니다.
  - `public_html/tutor_lms/index.jsp`는 미로그인 시 `returl`을 반드시 포함해 `/member/login.jsp`로 보내야 합니다. 이 값이 빠지면 로그인 모달 완료 후 기본값(`/mypage/new_main/`)으로 이동해 교수자 진입이 끊깁니다.
  - 예외 분기(`access_token`, `ek`, SSO)는 기존 로그인 처리 경로를 유지해야 합니다. 이 분기를 건드리면 SSL 토큰 로그인/외부 SSO가 깨질 수 있습니다.
  - `returl`은 레거시 비인코딩 케이스가 있어 쿼리 원문 재파싱을 같이 유지해야 하며, 외부 도메인 차단 검사도 함께 유지해야 합니다.
  - 모달 POST 값(`returl`, `udid`)은 `public_html/html/mypage/new_main_full.html` hidden 필드와 `public_html/mypage/new_main/index.jsp` 변수 세팅이 한 쌍입니다.
  - 현재 로그인 UX는 “빠른 로그인 카드”가 아니라 입력칸 기본값 방식입니다. 하위 메뉴 공통 모달(`layout_new_main`)까지 동일하게 맞추지 않으면 메뉴별 동작이 달라 보일 수 있습니다.
  - 2026-02-11 임시 운영 변경으로 신규메인/공통 레이아웃 로그인 모달 기본값이 하드코딩(`haksa_st26_01`, `Growai!2026`)되어 있습니다.
  - 교수자 기본값(`haksa_pf26_01`)은 화면 하드코딩이 아니라 `returl` 분기(`member/login.jsp`, `mypage/new_main/index.jsp`)에서 주입됩니다.
  - 운영 전환 시에는 하드코딩 제거(또는 서버 설정값 외부화)를 우선 점검해야 하며, 템플릿 위치는 `public_html/html/mypage/new_main_full.html`, `public_html/html/layout/layout_new_main.html`입니다.
- React 배포 산출물: `public_html/tutor_lms/app` (빌드 누락/정적파일 캐시 이슈)
- 교수자 차시관리 추천 동영상(시간/인정시간) 주의:
  - `public_html/tutor_lms/api/content_recommend.jsp`의 `lessonId`는 데이터셋에 따라 `LM_LESSON.id`(숫자) 또는 콜러스 `media_content_key`(문자열)일 수 있습니다.
  - 숫자 `lessonId`를 media key로 그대로 사용하면 추천 탭 시간 표시(`-`)와 인정시간 자동세팅(0분)이 동시에 깨질 수 있으므로, `LM_LESSON.start_url` 정규화 경로를 유지해야 합니다.
  - `TB_RECO_CONTENT.lesson_id`가 `LM_LESSON/TB_KOLLUS_MEDIA`에 없는 케이스가 많으므로, `TB_KOLLUS_TRANSCRIPT.duration_seconds`를 시간 보강의 기준 소스로 함께 유지해야 합니다.
  - `public_html/tutor_lms/api/kollus_lesson_upsert.jsp`는 기존 레슨 재사용 시 `total_time/complete_time/content_width/content_height`가 비어 있으면 최소 보정 업데이트가 필요합니다(과거 0분 데이터 고착 방지).
  - `kollus_lesson_upsert.jsp` 요청값 `total_time=0`이 들어올 수 있으므로, 업서트 내부에서 transcript 기반 보강을 빼면 다시 누락이 재발합니다.
  - `polytech-lms-api` 교수자 추천(`TutorContentRecommendService`)은 2026-02-11부터 학생 검색형 하이브리드(키워드 DB + RETRIEVAL_QUERY 벡터 + 제목 매칭 재정렬)입니다. 세 단계 중 하나라도 빠지면 강의명/차시명 매칭 체감이 급격히 떨어질 수 있습니다.
  - 추천 질의는 현재 `courseName`만 사용합니다. `lessonTitle/lessonDescription`은 전달돼도 검색 쿼리에서 무시됩니다(차시명 일반값 노이즈 방지).
  - 추천 품질은 `recommendContext.courseName` 입력 품질에 직접 의존합니다. `courseName` 전달이 빠지면 기본 문장 검색으로 내려가 결과가 퍼질 수 있습니다.
  - 특히 `project/components/courseManagement/CurriculumTab.tsx`의 비정규 경로(`CurriculumEditor`)에서 `courseName` 전달이 누락되면 과목이 달라도 추천이 유사하게 고정될 수 있습니다.
  - `public_html/tutor_lms/api/content_recommend.jsp`의 `content_recommend request_context` 로그(`course_name_len`, `lesson_title_len`, `context_fields`)를 먼저 확인하면 입력 누락과 추천엔진 문제를 빠르게 분리할 수 있습니다.
  - 제목 매칭 보정은 `TB_RECO_CONTENT.title/keywords/summary` LIKE 조회를 사용합니다. 대량 데이터에서 성능 이슈가 보이면 무작정 로직을 제거하지 말고 인덱스/쿼리 계획부터 확인해야 합니다.
- 교수자(React) CSP/외부 리소스:
  - `project/index.html`에 CSP 메타가 있어, 기본적으로 외부 CSS/폰트 로드가 막힙니다(보안상 장점).
  - 학생 메인(`public_html/html/css/custom.css`)은 Pretendard를 CDN으로 불러오지만, 교수자 앱에서 같은 방식으로 적용하려면 CSP 완화 또는 폰트 파일 자체 호스팅이 필요합니다(보안/배포 영향).
- Spring Boot 설정/시크릿: `polytech-lms-api/src/main/resources/application.yml` (키/토큰/DB정보 노출 금지)
- GCP/Firebase 원클릭 스크립트(신규) 주의:
  - 실행 진입점은 `tools/gcp/start-one-click.bat` / `tools/gcp/one-click-setup.ps1`입니다.
  - 기본 원클릭은 Firebase를 `epoly-kopo.web.app -> VM` 302 리다이렉트로 배포합니다. 이 모드에서는 주소창이 VM 주소로 바뀝니다.
  - 주소 유지가 필요하면 `tools/gcp/firebase-proxy-deploy`(Hosting rewrite + Functions `vmproxy`)를 사용해야 합니다.
  - 프록시 모드에서 로그인 리다이렉트 `Location` 헤더가 IP로 내려오면 프론트 주소가 다시 깨지므로, 함수에서 `Location: http://34.64.207.10/...`을 `https://epoly-kopo.web.app/...`로 치환하는 로직을 유지해야 합니다.
  - Firebase Hosting 경유에서는 일반 쿠키가 안정적으로 전달되지 않을 수 있어, 레거시 로그인 쿠키(`MLMS*`, `JSESSIONID`)를 `__session` 번들로 브리지하는 로직(`tools/gcp/firebase-proxy-deploy/functions/index.js`)을 유지해야 합니다.
  - `tools/gcp/firebase-proxy-deploy/functions/index.js`에서 프록시 구현을 `fetch`로 되돌리면 쿠키 전달이 다시 누락될 수 있습니다. 저수준 HTTP 프록시 + `__session` 복원 경로를 기본으로 유지합니다.
  - 신규메인 추천 JSP(`public_html/mypage/new_main/reco_video_list.jsp`)는 `POLYTECH_LMS_API_BASE` 미설정 시 침묵 실패(빈 목록)로 보이기 쉽습니다. 운영 `lms-resin`에 `POLYTECH_LMS_API_BASE=http://api:8081` 주입 여부를 먼저 확인해야 합니다.
  - 실제 실행 시 민감정보(DB 비밀번호, Qdrant API 키)가 `tools/gcp/generated/setup-summary.txt`와 `tools/gcp/generated/stack/.env`에 기록됩니다.
  - `tools/gcp/generated/`는 `.gitignore` 처리되어 있으므로, 스크립트 실행 전후에 `git status`로 민감파일이 추적되지 않는지 확인합니다.
  - `tools/gcp/templates/deploy-stack.sh.tpl`은 VM에서 Docker/Nginx/Certbot을 한 번에 설치하므로, 기존 운영 VM에 재실행하면 설정이 덮어써질 수 있습니다(신규 VM 기준 사용 권장).
  - GitHub Actions 배포에서 VM SSH 계정이 다르면 `gcloud compute scp/ssh` 단계가 즉시 실패합니다. 워크플로 시크릿 `GCP_VM_SSH_USER`를 실제 sudo 가능한 계정으로 맞춰야 합니다.
  - VM Resin은 `src` 런타임 컴파일을 쓰지 않고 `public_html/WEB-INF/classes`를 사용합니다. VM용 `resin-web.xml.tpl`에 `source=/opt/polytech-lms/legacy/src`를 다시 넣으면 `CourseSectionDao` 컴파일 오류로 첫 화면 500이 재발할 수 있습니다.
  - Resin 첫 요청 시 `WEB-INF/work`에 JSP 컴파일 파일을 쓰므로, 배포 스크립트의 `prepare_legacy_permissions` 권한 보정 단계를 제거하면 `Permission denied`로 500이 재발할 수 있습니다.
  - 배포 번들을 `stack-타임스탬프`로 생성할 때는 원격 실행 경로도 같은 폴더(`~/stack-...`)를 써야 합니다. 고정 `~/stack`을 실행하면 이전 dump가 재사용될 수 있습니다.
  - 레거시 dump는 `LM_COURSE`/`LM_COURSE_USER` 컬럼 수 mismatch와 `DEFINER` 구문으로 import 실패가 날 수 있어, 보정 로직(`Normalize-DbDumpIfNeeded`)을 우회하지 않아야 합니다.
  - MySQL 함수 생성 정책 오류(1418)는 `lms` 계정으로는 해결되지 않습니다. `log_bin_trust_function_creators`는 반드시 root 계정으로 설정해야 합니다.
  - SSL 자동 발급은 `API_DOMAIN` DNS가 VM 공인 IP와 일치할 때만 시도됩니다. DNS 전파 전에는 HTTP만 동작할 수 있습니다.
  - DB 이관(`-EnableDbMigration`)을 켜면 배포 시점에 `migration/source.sql`이 대상 MySQL로 import 됩니다. 대상 DB가 비어있지 않으면 데이터 충돌/중복 위험이 있으니 사전 백업이 필수입니다.
  - 소스 DB 자동 dump 방식(`mysqldump`)은 로컬 PC에서 실행되므로, 소스 DB 네트워크 접근 권한과 클라이언트 설치 여부를 먼저 확인해야 합니다.
  - API 로그에 `Qdrant client version 1.13.0 vs server 1.15.3` 경고가 출력될 수 있습니다. 기능은 동작해도 장기적으로 버전 정합(클라이언트/서버)을 맞추는 것이 안전합니다.
- Spring Boot 통계(SGIS) 전국 코드 주의:
  - 산업별 통계에서 전국 전체(`admCd=00`)는 응답 시점에 따라 값이 비는 경우가 있어, 시도 코드 합산 경로를 유지해야 합니다.
  - 시도코드 체계(SGIS/로컬) 불일치 가능성이 있어, 합계가 0이면 대체 코드 체계로 재합산하는 방어가 필요합니다.
  - 사업체통계는 문서상 `adm_cd` 미전달(non) 조회가 가능(전국 시도 리스트)하므로, 전국 0건 시 최종 보정 경로로 활용합니다.
  - 사업체통계는 요청 연도부터 최대 5년까지만 역탐색하고, 최소 연도(2000년) 아래로 내려가지 않도록 보정합니다.
  - `sgis_company`에 null 또는 0,0이 저장된 항목은 실제 데이터가 뒤늦게 열릴 수 있으므로(특히 시도/전국 코드), 1회 재조회 정책을 확인해야 합니다.
  - 관련 코드: `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/service/IndustryAnalysisService.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/sgis/service/SgisCompanyCacheService.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/sgis/client/SgisClient.java`
- 통계 대시보드(인구 탭) 비동기 UI 주의:
  - 필터 연속 변경 시 이전 요청 응답이 늦게 도착하면 차트/표가 서로 다른 조건 값으로 보일 수 있습니다.
  - `dashboard.html`의 `refreshPopulation()`에서 이전 요청 중단(`populationAbortController`), 최신 요청 순번(`populationRequestSeq`) 검증, 표 즉시 렌더(`renderPopulationTable`, `renderGenderTable`)를 함께 유지해야 합니다.
- 통계 대시보드(학번 기반 인구) 집계 주의:
  - 현재 운영 DB에서는 `LMS_MEMBER_VIEW`가 직접 노출되지 않을 수 있으므로, 동기화 테이블 `LM_POLY_MEMBER` 기준으로 조회해야 합니다.
  - `LM_POLY_MEMBER.MEMBER_KEY`는 10자리 숫자 형식(년도2+캠퍼스2+과정2+일련4) 가정이 깨지면 연도 추출이 어긋날 수 있으므로, 숫자/길이 필터를 유지해야 합니다.
  - 캠퍼스 필터는 화면값(`서울`)과 DB값(`서울캠퍼스`)이 다를 수 있어 `REPLACE(CAMPUS_NAME, '캠퍼스', '')` 비교를 같이 유지해야 합니다.
  - 사용자 요구상 학번 기반 그래프/표는 캠퍼스 전용 통계이므로, 행정구역/연도 필터 파라미터를 새로 연결하지 않도록 유지해야 합니다.
  - DB 방언 차이(MySQL/H2)로 SQL 문법 오류가 날 수 있으므로, `REGEXP`, `UNSIGNED`, 별칭 `HAVING` 같은 방언 의존 문법은 피하고 `LENGTH/SUBSTRING/CONCAT` 기준으로 유지해야 합니다.
  - 임시 숨김 상태에서는 `dashboard.html`의 `enableMemberKeyPopulation=false`를 유지해 API 호출까지 중지해야 불필요한 오류 로그 누적을 막을 수 있습니다.
  - 운영 확인 시 `MemberKeyPopulationJdbcRepository`의 `학번 기반 인구 SQL 시작/성공/실패` 로그를 먼저 확인하면, 뷰테이블 조회 자체 문제인지 후처리 문제인지 빠르게 분리할 수 있습니다.
  - 인구 탭 필터 연속 변경 시 학번 그래프도 비동기 충돌이 날 수 있으므로 `memberKeyPopulationAbortController`와 요청 순번 검증(`requestSeq`)을 함께 유지해야 합니다.

## 갱신 기준(강제)
- 권한/세션/결제/수료/통계/업로드처럼 “운영 영향이 큰” 부분을 수정했으면,
  무엇이 위험했고 무엇을 확인했는지(근거)를 1~2줄로 추가합니다.
