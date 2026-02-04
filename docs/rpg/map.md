# RPG-라이트: 저장소 지도 (`map.md`)

최근 갱신: 2026-02-04

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-04 11:25

- JSP 총합(전체): 1224
- JSP(public_html): 1223 (sysop: 715, api: 18)
- 템플릿 HTML(public_html/**/html): 927
- DAO(src/dao): 178
- React(Vite) 프로젝트 파일 수(project, node_modules 제외): 105
- polytech-lms-api(Java/Spring Boot) Java 파일 수: 143

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
- Resin 루트 설정: `resin/resin.xml` (root-directory=`public_html`)
- React UI 빌드 설정: `project/vite.config.ts` (outDir=`public_html/tutor_lms/app`)
- Spring Boot API 빌드 설정: `polytech-lms-api/build.gradle`

## 모듈 지도(예시 형식)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| (예: 관리자 과정 목록) | `public_html/sysop/course/course_list.jsp` | `src/dao/CourseDao.java` | `public_html/sysop/html/course/course_list.html` |  |

