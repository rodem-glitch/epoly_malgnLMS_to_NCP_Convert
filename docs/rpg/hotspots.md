# RPG-라이트: 핫스팟/주의사항 (`hotspots.md`)

최근 갱신: 2026-02-04

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-04 11:25

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
- React 배포 산출물: `public_html/tutor_lms/app` (빌드 누락/정적파일 캐시 이슈)
- Spring Boot 설정/시크릿: `polytech-lms-api/src/main/resources/application.yml` (키/토큰/DB정보 노출 금지)

## 갱신 기준(강제)
- 권한/세션/결제/수료/통계/업로드처럼 “운영 영향이 큰” 부분을 수정했으면,
  무엇이 위험했고 무엇을 확인했는지(근거)를 1~2줄로 추가합니다.

