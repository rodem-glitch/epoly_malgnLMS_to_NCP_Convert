# RPG-라이트: 핫스팟/주의사항 (`hotspots.md`)

최근 갱신: 2026-02-06

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-10 10:03

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
- 개인정보 동의(게이트/버전):
  - 동의 화면 재사용: `public_html/member/privacy_agree.jsp` (`ag=sso|cert`)
  - 리다이렉트 안전: `returl`은 외부 URL 차단/검증 필수(오픈 리다이렉트 방지)
  - 운영 준비: 이미지가 없으면 화면이 차단됨(폴백 금지 정책)
    - SSO: `/common/images/consent/consent_sso_1.png` 또는 `/common/images/consent/consent_sso_2.png` (둘 다 없으면 차단)
    - 증명서: `/common/images/consent/consent_cert_1.png` 또는 `/common/images/consent/consent_cert_2.png` (둘 다 없으면 차단)
  - 이력: `TB_AGREEMENT_LOG`에 `type/module` 조합으로 버전 관리(`sso_20260120`, `cert_20260120`)
- React 배포 산출물: `public_html/tutor_lms/app` (빌드 누락/정적파일 캐시 이슈)
- 교수자(React) CSP/외부 리소스:
  - `project/index.html`에 CSP 메타가 있어, 기본적으로 외부 CSS/폰트 로드가 막힙니다(보안상 장점).
  - 학생 메인(`public_html/html/css/custom.css`)은 Pretendard를 CDN으로 불러오지만, 교수자 앱에서 같은 방식으로 적용하려면 CSP 완화 또는 폰트 파일 자체 호스팅이 필요합니다(보안/배포 영향).
- Spring Boot 설정/시크릿: `polytech-lms-api/src/main/resources/application.yml` (키/토큰/DB정보 노출 금지)
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
