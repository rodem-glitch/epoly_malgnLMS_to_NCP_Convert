# RPG-라이트: 저장소 지도 (`map.md`)

최근 갱신: 2026-02-06

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-06 14:05

- JSP 총합(전체): 1226
- JSP(public_html): 1225 (sysop: 715, api: 18)
- 템플릿 HTML(public_html/**/html): 928
- DAO(src/dao): 178
- React(Vite) 프로젝트 파일 수(project, node_modules 제외): 109
- growailms-api(Java/Spring Boot) Java 파일 수: 160

생성된 인덱스:
- docs/rpg/generated/jsp_setBody_index.tsv
- docs/rpg/generated/jsp_newDao_index.tsv
- docs/rpg/generated/dao_table_index.tsv
- docs/rpg/generated/polytech_controller_mapping_candidates.tsv
<!-- @generated:end -->

## 자동 생성(권장)
- 아래 명령을 실행하면 `docs/rpg/generated/*` 인덱스와 위 “자동 요약”이 갱신됩니다.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File tools/rpg/generate.ps1`

## 목적
- “어디를 고쳐야 하는지”를 빨리 찾기 위한 최소 지도입니다.
- 완벽하게 만들려고 하지 말고, **지금 작업에 필요한 만큼만** 계속 갱신합니다(오버엔지니어링 금지).

## 작성 규칙(최소)
- 새로운 기능/버그를 다룰 때, **진입점(JSP/API)** 과 **연결된 DAO/템플릿**을 한 묶음으로 기록합니다.
- 파일 이동/추가/삭제가 있으면 이 문서도 같이 갱신합니다.

## 자주 보는 진입점
- 프론트 공통 초기화: `public_html/init.jsp`
- 관리자 공통 초기화: `public_html/sysop/init.jsp`
- React UI 빌드 설정: `project/vite.config.ts` (outDir=`public_html/tutor_lms/app`)
- Spring Boot API 빌드 설정: `growailms-api/build.gradle`

## 모듈 지도(예시 형식)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| (예: 관리자 과정 목록) | `public_html/sysop/course/course_list.jsp` | `src/dao/CourseDao.java` | `public_html/sysop/html/course/course_list.html` |  |

## 최근 작업(동의 게이트)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| SSO 첫 방문 동의(신규 메인) | `public_html/mypage/new_main/index.jsp` → `public_html/member/privacy_agree.jsp` | `src/dao/AgreementLogDao.java` / `TB_AGREEMENT_LOG` | `public_html/html/member/privacy_agree.html` | 동의서 이미지(`/common/images/consent/consent_sso_1.png` 또는 `/common/images/consent/consent_sso_2.png`) 필요(둘 다 없으면 차단), `ag=sso`, `returl` 필수 |
| 증명서(수료증/합격증) 발급 동의 | `public_html/mypage/certificate*.jsp` → `public_html/member/privacy_agree.jsp` | `src/dao/AgreementLogDao.java` / `TB_AGREEMENT_LOG` | `public_html/html/member/privacy_agree.html` | 동의서 이미지(`/common/images/consent/consent_cert_1.png` 또는 `/common/images/consent/consent_cert_2.png`) 필요(둘 다 없으면 차단), `ag=cert`, `mid=cuid`(선택), `returl` 필수 |
| (로컬 테스트) SSO 동의 화면 확인 | `public_html/mypage/new_main/sso_consent_test.jsp` → `public_html/member/privacy_agree.jsp` | `src/dao/AgreementLogDao.java` / `TB_AGREEMENT_LOG` | `public_html/html/member/privacy_agree.html` | localhost에서만 접근(운영 노출 방지), `force=Y`로 재확인 |

## 최근 작업(교수자 UI 톤 맞춤)
| 기능/화면 | 진입점(JSP/API) | 관련 소스(React) | 산출물 | 비고 |
|---|---|---|---|---|
| 교수자 LMS UI를 학생 메인 톤으로 통일 | `public_html/tutor_lms/index.jsp` → `public_html/tutor_lms/app/index.html` | `project/styles/globals.css`, `project/App.tsx` | `public_html/tutor_lms/app/assets/*` | 학생 메인(/mypage/new_main) 팔레트(#f9fafb, #2b58e6, #e5e7eb)로 토큰/레이아웃 정리 후 `cd project && npm run build`로 반영 |
| (UI 미세조정) 좌측 메뉴 폰트 1단계 축소 | `public_html/tutor_lms/index.jsp` → `public_html/tutor_lms/app/index.html` | `project/App.tsx` | `public_html/tutor_lms/app/assets/*` | 사이드바 메뉴 영역에 `text-sm` 적용(메뉴만 한 단계 작게) |

## 최근 작업(교수자 통계/산업별 통계)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 산출물 | 비고 |
|---|---|---|---|---|
| 산업별 통계: 전체 캠퍼스/전국 전체 선택 시 “행정구역(종사자) 인원”이 0으로 뜨는 문제 수정 | `/statistics` → `GET /statistics/api/industry/analysis` | `growailms-api/src/main/resources/static/statistics/dashboard.html`, `growailms-api/src/main/java/kr/go/growailms/statistics/dashboard/controller/StatisticsDashboardApiController.java`, `growailms-api/src/main/java/kr/go/growailms/statistics/dashboard/service/IndustryAnalysisService.java`, `growailms-api/src/main/java/kr/go/growailms/statistics/sgis/service/SgisCompanyCacheService.java`, `growailms-api/src/main/java/kr/go/growailms/statistics/sgis/client/SgisClient.java` | JSON(산업분포 분석) | 전국(`admCd=00`)은 SGIS 시도코드→로컬 시도코드→`adm_cd` non 리스트 순으로 합산. 시도/전국 코드의 null/0,0 캐시는 1회 재조회 |
| 인구별 통계: 학번(MEMBER_KEY) 기반 연도별·캠퍼스별 인구 그래프/표 추가 | `/statistics` → `GET /statistics/api/population/member-key-campus` | `growailms-api/src/main/resources/static/statistics/dashboard.html`, `growailms-api/src/main/java/kr/go/growailms/statistics/dashboard/controller/StatisticsDashboardApiController.java`, `growailms-api/src/main/java/kr/go/growailms/statistics/dashboard/service/MemberKeyPopulationService.java`, `growailms-api/src/main/java/kr/go/growailms/statistics/dashboard/persistence/MemberKeyPopulationJdbcRepository.java` | JSON(학번 기반 인구 시계열) + 인구 탭 하단 그래프/표 | `LM_POLY_MEMBER`(학사 원천 `COM.LMS_MEMBER_VIEW` 동기화본)의 `MEMBER_KEY/CAMPUS_CODE/CAMPUS_NAME` 집계. **캠퍼스 필터만 반영**하고 행정구역/연도 필터는 집계에서 제외. 2026-02-06 기준 화면/호출 임시 숨김(`memberKeyPopulationCard`, `enableMemberKeyPopulation=false`) |
