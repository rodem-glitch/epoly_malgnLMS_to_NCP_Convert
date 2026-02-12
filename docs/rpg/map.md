# RPG-라이트: 저장소 지도 (`map.md`)

최근 갱신: 2026-02-11

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-12 12:28

- JSP 총합(전체): 1227
- JSP(public_html): 1226 (sysop: 715, api: 18)
- 템플릿 HTML(public_html/**/html): 928
- DAO(src/dao): 178
- React(Vite) 프로젝트 파일 수(project, node_modules 제외): 106
- polytech-lms-api(Java/Spring Boot) Java 파일 수: 147

생성된 인덱스:
- docs/rpg/generated/jsp_setBody_index.tsv (SummaryOnly에서는 갱신하지 않음)
- docs/rpg/generated/jsp_newDao_index.tsv (SummaryOnly에서는 갱신하지 않음)
- docs/rpg/generated/dao_table_index.tsv (SummaryOnly에서는 갱신하지 않음)
- docs/rpg/generated/polytech_controller_mapping_candidates.tsv (SummaryOnly에서는 갱신하지 않음)
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
- Resin 웹앱 런타임 설정: `public_html/WEB-INF/resin-web.xml` (JNDI/클래스 로더)
- React UI 빌드 설정: `project/vite.config.ts` (outDir=`public_html/tutor_lms/app`)
- Spring Boot API 빌드 설정: `polytech-lms-api/build.gradle`

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

## 최근 작업(로그인 모달 게이트)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 비로그인 권한 진입 시 신규 메인 모달로 로그인 유도 | `public_html/mypage/init.jsp`(0차 게이트), `public_html/member/login.jsp`(GET 게이트) → `public_html/mypage/new_main/index.jsp` | `public_html/mypage/init.jsp`, `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp` | `public_html/html/mypage/new_main_full.html` | 구 로그인 페이지 직접 렌더 대신 `login_required=Y`로 모달 자동 오픈, `returl`/`udid` hidden 전달, `access_token`/`ek`(SSL 토큰 로그인)은 기존 분기 유지 |
| 공통 로그인 기본값 분기(학생/교수자) | `/member/login.jsp` → `/mypage/new_main/?login_required=Y...` | `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp` | `public_html/html/member/login.html`, `public_html/_html_v5/member/login.html`, `public_html/html/mypage/new_main_full.html`, `public_html/html/layout/layout_new_main.html` | `returl`에 `/tutor_lms/` 포함 시 교수자(`haksa_pf26_01`) 기본값 주입, 그 외(이러닝/채용 하위 포함)는 학생(`haksa_st26_01`) 기본값 주입. 비밀번호 `Growai!2026` 공통 |

## 최근 작업(매뉴얼)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 정적 파일(HTML/이미지) | 비고 |
|---|---|---|---|---|
| 신규 메인 매뉴얼(학생/교직원) 스크린샷/스타일 갱신 | `public_html/mypage/new_main/manual.jsp` | `public_html/html/mypage/new_main_manual.html` | `public_html/mypage/new_main/student_manual.html`, `public_html/mypage/new_main/tutor_manual.html`, `public_html/mypage/new_main/*.png` | 학생 매뉴얼: 표 헤더 색상 통일 + 하위 항목 스크린샷 추가. 교수자 매뉴얼: 스크린샷 캡션/레이아웃 통일 |

## 최근 작업(교수자 UI 톤 맞춤)
| 기능/화면 | 진입점(JSP/API) | 관련 소스(React) | 산출물 | 비고 |
|---|---|---|---|---|
| 교수자 LMS UI를 학생 메인 톤으로 통일 | `public_html/tutor_lms/index.jsp` → `public_html/tutor_lms/app/index.html` | `project/styles/globals.css`, `project/App.tsx` | `public_html/tutor_lms/app/assets/*` | 학생 메인(/mypage/new_main) 팔레트(#f9fafb, #2b58e6, #e5e7eb)로 토큰/레이아웃 정리 후 `cd project && npm run build`로 반영 |
| (UI 미세조정) 좌측 메뉴 폰트 1단계 축소 | `public_html/tutor_lms/index.jsp` → `public_html/tutor_lms/app/index.html` | `project/App.tsx` | `public_html/tutor_lms/app/assets/*` | 사이드바 메뉴 영역에 `text-sm` 적용(메뉴만 한 단계 작게) |
| 과제 > 피드백 관리: 학생 제출물(제목/내용/첨부) 모달 확인 추가 | `project/components/CourseManagement.tsx` → `GET public_html/tutor_lms/api/homework_user_submission.jsp` | `project/components/HomeworkSubmissionDetailModal.tsx`, `project/api/tutorLmsApi.ts` | `public_html/tutor_lms/app/assets/*` | “제출물 보기” 버튼으로 제출 본문/첨부파일을 모달에서 확인(파일은 `CL_FILE(module='homework_{homework_id}', module_id=course_user_id)` 기반) |

## 최근 작업(교수자 차시 추천 동영상 시간 동기화)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 관련 소스 | 비고 |
|---|---|---|---|---|
| 차시관리 > 콘텐츠 라이브러리 추천 탭에서 동영상 시간/인정시간 자동세팅 복구 | `POST public_html/tutor_lms/api/content_recommend.jsp`, `POST public_html/tutor_lms/api/kollus_lesson_upsert.jsp` | `src/dao/LessonDao.java` / `LM_LESSON`, `src/dao/KollusMediaDao.java` / `TB_KOLLUS_MEDIA`, `TB_KOLLUS_TRANSCRIPT(duration_seconds)` | `project/components/ContentLibraryModal.tsx` | `lessonId`가 숫자(`LM_LESSON.id`)로 들어오는 추천 데이터는 `start_url(media key)`로 정규화하고, DB 메타가 비면 `TB_KOLLUS_TRANSCRIPT` 시간을 분 단위로 보강. 업서트 단계에서도 `total_time` 미전달 시 전사시간으로 1회 보강해 인정시간 기본값 누락을 방지 |
| 콘텐츠 라이브러리 추천 탭 자연어 검색 정렬(학생 검색형) | `POST public_html/tutor_lms/api/content_recommend.jsp` → `POST /tutor/content-recommend/lessons` | `TB_RECO_CONTENT` (`title`, `summary`, `keywords`, `lesson_id`) | `polytech-lms-api/src/main/java/kr/polytech/lms/tutorcontentrecommend/service/TutorContentRecommendService.java`, `project/components/courseManagement/CurriculumTab.tsx`, `project/components/CurriculumEditor.tsx`, `project/components/courseManagement/WeeklyContentModal.tsx`, `project/components/courseManagement/EditContentModal.tsx` | 교수자 추천은 `courseName`만 질의로 사용(차시명/설명 제외)하고, 내부는 `키워드 DB 검색 + RETRIEVAL_QUERY 벡터검색 + 제목 매칭 재정렬` 구조를 유지. 학사/비정규 경로 모두 `recommendContext.courseName` 전달 강제, 프록시(`content_recommend.jsp`)는 `request_context` 로그로 유입 확인 |

## 최근 작업(교수자 통계/산업별 통계)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 산출물 | 비고 |
|---|---|---|---|---|
| 산업별 통계: 전체 캠퍼스/전국 전체 선택 시 “행정구역(종사자) 인원”이 0으로 뜨는 문제 수정 | `/statistics` → `GET /statistics/api/industry/analysis` | `polytech-lms-api/src/main/resources/static/statistics/dashboard.html`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/controller/StatisticsDashboardApiController.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/service/IndustryAnalysisService.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/sgis/service/SgisCompanyCacheService.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/sgis/client/SgisClient.java` | JSON(산업분포 분석) | 전국(`admCd=00`)은 SGIS 시도코드→로컬 시도코드→`adm_cd` non 리스트 순으로 합산. 시도/전국 코드의 null/0,0 캐시는 1회 재조회 |
| 인구별 통계: 학번(MEMBER_KEY) 기반 연도별·캠퍼스별 인구 그래프/표 추가 | `/statistics` → `GET /statistics/api/population/member-key-campus` | `polytech-lms-api/src/main/resources/static/statistics/dashboard.html`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/controller/StatisticsDashboardApiController.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/service/MemberKeyPopulationService.java`, `polytech-lms-api/src/main/java/kr/polytech/lms/statistics/dashboard/persistence/MemberKeyPopulationJdbcRepository.java` | JSON(학번 기반 인구 시계열) + 인구 탭 하단 그래프/표 | `LM_POLY_MEMBER`(학사 원천 `COM.LMS_MEMBER_VIEW` 동기화본)의 `MEMBER_KEY/CAMPUS_CODE/CAMPUS_NAME` 집계. **캠퍼스 필터만 반영**하고 행정구역/연도 필터는 집계에서 제외. 2026-02-06 기준 화면/호출 임시 숨김(`memberKeyPopulationCard`, `enableMemberKeyPopulation=false`) |

## 최근 작업(에이전트 운영 규칙)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 산출물 | 비고 |
|---|---|---|---|---|
| 팀 에이전트 기본 모드 강제 | (문서 규칙) | `AGENTS.md` | 세션 공통 작업 절차 규칙 | 새 세션 첫 작업 지시에서도 Planner/Explorer/Builder/Reviewer/Reporter 및 병렬 탐색 기본 적용 근거를 문서화 |

## 최근 작업(개발환경 실행설정)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 산출물 | 비고 |
|---|---|---|---|---|
| IntelliJ Resin/Polytech API 실행설정 이관 | IntelliJ Run/Debug (`Resin`, `PolytechLmsApiApplication`) | `.idea/runConfigurations/Resin.xml`, `.idea/runConfigurations/PolytechLmsApiApplication.xml` | 프로젝트 공유 실행설정 2종 | `C:\Users\newkl\Desktop\MalgnLMS\.idea\workspace.xml`의 설정을 기준으로 `C:\Users\newkl\Desktop\polytech-lms`에 이관. 확인 근거: `Get-ChildItem .idea/runConfigurations`로 2개 파일 생성 확인 |

## 최근 작업(로컬 Resin 흰화면 복구)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 산출물 | 비고 |
|---|---|---|---|---|
| `/mypage/*` 흰화면(200 + 빈 본문) 복구 | `public_html/init.jsp`, `public_html/mypage/new_main/index.jsp`, `public_html/member/login.jsp` | `public_html/WEB-INF/resin-web.xml`, `public_html/init.jsp`, `C:\Users\newkl\Desktop\resin-4.0.67\resin-4.0.67\conf\resin.xml` | 로컬 Resin JNDI(`jdbc/malgn`, `jdbc/lms`) + src 자동 컴파일 | 확인 근거: `curl -i /mypage/new_main/index.jsp`가 `Content-Length: 12/0` 빈 응답에서 HTML 본문 응답으로 변경, `/mypage/index.jsp`와 `/member/login.jsp`가 `302 -> /mypage/new_main/?login_required=Y...` 정상 확인 |

## 최근 작업(GCP/Firebase 원클릭 자동화)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 산출물 | 비고 |
|---|---|---|---|---|
| GCP Linux VM + Firebase Hosting + DB/Qdrant 전량 이관 운영 검증 | 실행: `tools/gcp/start-one-click.bat` → `tools/gcp/one-click-setup.ps1` + `tools/gcp/firebase-proxy-deploy` | `tools/gcp/one-click-setup.ps1`, `tools/gcp/templates/docker-compose.yml.tpl`, `tools/gcp/templates/deploy-stack.sh.tpl`, `tools/gcp/templates/nginx-api.conf.tpl`, `tools/gcp/templates/resin-web.xml.tpl`, `tools/gcp/firebase-proxy-deploy/*`, `tools/gcp/README.md`, `.github/workflows/deploy-lms-gcp.yml` | `tools/gcp/generated/*`(실행 시 생성) | 확인 근거: 소스/타깃 MySQL row count 일치(`tb_user=1064`, `lm_course=165`, `tb_reco_content=3000`, `tb_kollus_transcript=3000`), Qdrant point count 일치(`video_summary_vectors_gemini=3014`, `video_summary_vectors=131`), `curl -I https://epoly-kopo.web.app/mypage/new_main/index.jsp` 200, 추천 API(`POST /tutor/content-recommend/lessons`) 200 확인, CI 경로는 `one-click -SkipFirebaseDeploy` 후 `vmproxy` 별도 배포로 검증 |
| Firebase `web.app` 로그인 세션/추천영상 복구 | Hosting rewrite + Functions 프록시 + 신규메인 추천 JSP | `tools/gcp/firebase-proxy-deploy/functions/index.js`, `tools/gcp/firebase-proxy-deploy/firebase.json`, `public_html/mypage/new_main/reco_video_list.jsp`, `tools/gcp/templates/docker-compose.yml.tpl`, `tools/gcp/templates/nginx-api.conf.tpl`, `tools/gcp/templates/deploy-stack.sh.tpl` | 운영 반영: `vmproxy` 재배포 + `lms-resin` 재생성 + nginx reload | 확인 근거: `Cookie: __session=...`로 `GET /mypage/new_main/reco_prompt.jsp`가 `{\"ok\":true}` 응답, `GET /mypage/new_main/reco_video_list.jsp` 추천 타이틀 4건 반환, `lms-resin` env에 `POLYTECH_LMS_API_BASE=http://api:8081` 확인, `curl -I /tutor_lms/app/index.html` charset UTF-8 확인, `virtualclass-2ee22`의 Generative Language API 활성화 후 `/student/content-recommend/home` 200 확인 |
| GCP 통계 폴더(`통계/`) 운영 반영 | 통계 API: `/statistics/api/internal/*` | `tools/gcp/one-click-setup.ps1`, `tools/gcp/templates/docker-compose.yml.tpl`, `tools/gcp/README.md` | VM 경로 `/opt/polytech-lms/statistics_data` + API 컨테이너 `/data/statistics` | 확인 근거: 반영 전 `GET /statistics/api/internal/employment/top` 500(`통계 파일을 찾을 수 없습니다`), 반영 후 `GET /statistics/api/internal/employment/top?top=3` 200 및 JSON 응답 확인 |
| GitHub Actions Firebase 배포 실패 복구 | `Deploy LMS to GCP + Firebase` 워크플로의 `Firebase vmproxy + Hosting 배포` 단계 | `tools/gcp/firebase-proxy-deploy/public/index.html`, `.github/workflows/deploy-lms-gcp.yml` | CI 재실행 시 Hosting public 경로 검증 통과 기반 확보 | 확인 근거: Run #7 로그에서 `Directory 'public' for Hosting does not exist` 원인 확인 후 추적 파일 추가 |
| GitHub Actions VM 배포 단계 무대기 보강 | `Deploy LMS to GCP + Firebase` 워크플로의 `원클릭 배포 실행 (VM 스택)` 단계 | `tools/gcp/one-click-setup.ps1` | CI에서 sudo/ssh 대기 프롬프트 제거 | 확인 근거: `gcloud compute scp/ssh`에 `BatchMode` 적용, 원격 실행 `sudo -n`으로 강제해 프롬프트 대기를 즉시 실패로 노출 |
| GitHub Actions 시크릿/프록시 안정화 | `Deploy LMS to GCP + Firebase` 워크플로의 사전 점검 + `vmproxy` 배포 단계 | `.github/workflows/deploy-lms-gcp.yml` | 시크릿 누락 즉시 실패 + VM 고정 IP 자동 동기화 + Firebase 인증 분기(토큰/서비스계정) | 확인 근거: `npx -y js-yaml .github/workflows/deploy-lms-gcp.yml` 파싱 성공, `node --check tools/gcp/firebase-proxy-deploy/functions/index.js` 문법 확인, `git diff`로 `TARGET` 치환/인증 분기 반영 확인 |
