# RPG-라이트: 기능 흐름 (`flows.md`)

최근 갱신: 2026-02-04

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-04 11:25

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
  - 동의 문구: `TB_WEBPAGE.code='consent_sso_20260120'` (운영에서 등록 필요)
- 출력:
  - 동의 화면: `public_html/member/privacy_agree.jsp` (파라미터 `ag=sso`, `returl=...`)
  - 템플릿: `public_html/html/member/privacy_agree.html` (`consent_mode` 분기)
- 확인(근거): 리다이렉트/저장/출력 분기 코드를 파일에서 확인(`public_html/mypage/new_main/index.jsp`, `public_html/member/privacy_agree.jsp`, `public_html/html/member/privacy_agree.html`)
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
  - 동의 문구: `TB_WEBPAGE.code='consent_cert_20260120'` (운영에서 등록 필요)
- 출력:
  - 동의 화면: `public_html/member/privacy_agree.jsp` (파라미터 `ag=cert`, `mid=cuid`, `returl=...`)
  - 템플릿: `public_html/html/member/privacy_agree.html` (`consent_mode` 분기)
- 확인(근거): 발급 진입점과 동의 게이트 분기 코드를 파일에서 확인(`public_html/mypage/certificate*.jsp`, `public_html/member/privacy_agree.jsp`)
- 최근 갱신: 2026-02-04
