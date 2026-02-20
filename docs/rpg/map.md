# RPG-라이트: 저장소 지도 (`map.md`)

최근 갱신: 2026-02-20

## 자동 요약(전체 스캔)
<!-- @generated:start -->

최근 자동 갱신: 2026-02-19 16:55

- JSP 총합(전체): 1245
- JSP(public_html): 1244 (sysop: 715, api: 18)
- 템플릿 HTML(public_html/**/html): 928
- DAO(src/dao): 181
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

## 최근 작업(강의실 학사 커리큘럼)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 학사 커리큘럼 과제 `보기` 바로가기(차시 기간 예외) | `public_html/classroom/index.jsp`(렌더) → `public_html/classroom/haksa_module.jsp`(리다이렉트) → `public_html/classroom/homework_view.jsp` | `src/dao/PolyCourseSettingDao.java` (커리큘럼 JSON) | `public_html/html/classroom/index.html` | 과제는 차시 수강기간 밖이어도 `보기` 이동 허용(시험/동영상은 기존 차단 유지) |

## 최근 작업(로그인 모달 게이트)
| 기능/화면 | 진입점(JSP/API) | 관련 소스 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 비로그인 권한 진입 시 신규 메인 모달로 로그인 유도 | `public_html/mypage/init.jsp`(0차 게이트), `public_html/member/login.jsp`(GET 게이트) → `public_html/mypage/new_main/index.jsp` | `public_html/mypage/init.jsp`, `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp` | `public_html/html/mypage/new_main_full.html` | 구 로그인 페이지 직접 렌더 대신 `login_required=Y`로 모달 자동 오픈, `returl`/`udid` hidden 전달, `access_token`/`ek`(SSL 토큰 로그인)은 기존 분기 유지 |
| 공통 로그인 기본값 분기(학생/교수자) | `/member/login.jsp` → `/mypage/new_main/?login_required=Y...` | `public_html/member/login.jsp`, `public_html/mypage/new_main/index.jsp`, `public_html/init.jsp` | `public_html/html/member/login.html`, `public_html/_html_v5/member/login.html`, `public_html/html/mypage/new_main_full.html`, `public_html/html/layout/layout_new_main.html` | `returl`(또는 현재 경로)에 `/tutor_lms/` 포함 시 교수자(`kopo_pr01`) 기본값 주입, 그 외는 학생(`kopo_st01`) 기본값 주입. 비밀번호 `Growai!2026` 공통 |

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
| 과제 > 피드백 관리: 교수자 피드백 템플릿(조회/저장/삭제) 백엔드 추가 | `GET public_html/tutor_lms/api/homework_feedback_template_list.jsp`, `POST public_html/tutor_lms/api/homework_feedback_template_save.jsp`, `POST public_html/tutor_lms/api/homework_feedback_template_delete.jsp` | `src/dao/HomeworkFeedbackTemplateDao.java` (`LM_HOMEWORK_FEEDBACK_TEMPLATE`) | `public_html/ddl_homework_feedback_template.sql` | 과목/교수자별 템플릿 CRUD 제공. 서버 개수 제한 없음(프론트에서 필요한 개수만 조회) |
| 과제 > 피드백 관리: 교수자 피드백 첨부파일(업로드/목록/삭제) 백엔드 추가 | `POST public_html/tutor_lms/api/homework_feedback_file_upload.jsp`, `GET public_html/tutor_lms/api/homework_feedback_file_list.jsp`, `POST public_html/tutor_lms/api/homework_feedback_file_delete.jsp`, `GET public_html/tutor_lms/api/homework_user_submission.jsp` | `src/dao/ClFileDao.java`(`CL_FILE`), `src/dao/CourseTutorDao.java`, `src/dao/CourseModuleDao.java`, `src/dao/HomeworkDao.java`, `src/dao/CourseUserDao.java` | (React API 응답 JSON) | 피드백 파일은 `CL_FILE(module='homework_feedback_{homework_id}', module_id=course_user_id)`로 저장. 제출 상세 API에 `feedback_files` 배열을 포함해 정규/비정규 공통 조회 |
| 과제 > 피드백 관리: 제출물 일치율 분석(수동/자동) 백엔드 추가 | `POST public_html/tutor_lms/api/homework_similarity_run.jsp`, `GET public_html/tutor_lms/api/homework_similarity_list.jsp`, `GET public_html/tutor_lms/api/homework_similarity_detail.jsp`, `POST public_html/classroom/homework_view.jsp`, `POST public_html/classroom/file_upload.jsp`, `POST public_html/tutor_lms/api/homework_submit_cancel.jsp` | `src/dao/HomeworkSimilarityRunDao.java`(`LM_HOMEWORK_SIMILARITY_RUN`), `src/dao/HomeworkSimilarityResultDao.java`(`LM_HOMEWORK_SIMILARITY_RESULT`) | `public_html/ddl_homework_similarity.sql` | 제목/본문/첨부 기반 일치율 저장. 제출/취소/첨부변경 시 자동 증분 갱신, 수동 실행은 전체 재계산 |
| 담당과목 > 과제관리: 교수자 첨부파일 확인/다운로드/삭제/재업로드 백엔드 보강 | `GET public_html/tutor_lms/api/homework_list.jsp`, `POST public_html/tutor_lms/api/homework_modify.jsp`, `POST public_html/tutor_lms/api/homework_delete.jsp`, `GET public_html/main/download_file.jsp` | `src/dao/HomeworkDao.java`(`LM_HOMEWORK.homework_file`), `src/dao/CourseModuleDao.java` | (React API 응답 JSON) | 목록 API에 `homework_file_*`(conv/ek/download_url) 추가, 수정 API에 `delete_homework_file_yn` 지원, 과제 최종 삭제 시 물리 파일 정리 |
| 담당과목 > 과제관리: 동일 과제 다중 강의 동시 등록 | `POST public_html/tutor_lms/api/homework_insert.jsp` | `src/dao/HomeworkDao.java`(`LM_HOMEWORK`), `src/dao/CourseModuleDao.java`(`LM_COURSE_MODULE`) | (React API 응답 JSON) | `course_id`(단일) + `course_ids`(복수, 쉼표) 동시 지원. 과목별 권한/존재 검증 후 가능한 강의에만 배치하고 실패 과목 목록(`rst_failed_courses`) 반환 |
| 담당과목 > 과제관리: 과제별 제출첨부 허용 파일형식 옵션 | `POST public_html/tutor_lms/api/homework_insert.jsp`, `POST public_html/tutor_lms/api/homework_modify.jsp`, `GET public_html/tutor_lms/api/homework_list.jsp`, `POST public_html/classroom/file_upload.jsp` | `src/dao/HomeworkDao.java`(`LM_HOMEWORK.submit_file_ext_mode/submit_file_exts`) | `public_html/ddl_homework_submit_file_ext.sql` | 과제별 프리셋/직접입력 확장자 저장 후 학생 제출 업로드에서 서버 강제 검증(우회 업로드 차단) |

## 최근 작업(교수자 출석 자동 판정)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 결석 n회 이상 자동 판정(F/미수료) + 출석 통합 요약 API | `POST public_html/tutor_lms/api/course_evaluation_update.jsp`, `GET public_html/tutor_lms/api/attendance_course_summary.jsp`, `POST public_html/tutor_lms/api/attendance_absence_apply.jsp`, `GET public_html/tutor_lms/api/progress_students.jsp`, `GET public_html/tutor_lms/api/completion_list.jsp` | `src/dao/CourseUserDao.java`(`LM_COURSE_USER.complete_status/fail_reason`, `LM_COURSE.limit_absence_yn/limit_absence_cnt`), `src/dao/CourseProgressDao.java`(`LM_COURSE_PROGRESS.complete_yn`), `LM_COURSE_LESSON.progress_yn` | (React API 응답 JSON) | 정규(`course_type='R'`) + 결석 초과는 상태 라벨 `F`, 비정규는 `미수료`로 표시. 수동 출석 변경 시 즉시 자동 판정 재계산 |
| 출석 탭 부분 출결(다중 차시 일괄저장 + 주차 그룹 + 학생×차시 매트릭스) API 추가 | `POST public_html/tutor_lms/api/attendance_batch_update.jsp`, `GET public_html/tutor_lms/api/attendance_week_lessons.jsp`, `GET public_html/tutor_lms/api/attendance_student_matrix.jsp` | `src/dao/CourseProgressDao.java`(`LM_COURSE_PROGRESS.complete_yn`), `src/dao/CourseLessonDao.java`(`LM_COURSE_LESSON.section_id/chapter`), `src/dao/CourseUserDao.java`(`LM_COURSE_USER`), `src/dao/CourseSectionDao.java`(`LM_COURSE_SECTION`) | (React API 응답 JSON) | 한 번 요청으로 `여러 차시 × 여러 학생` 상태를 저장하고, 주차별 차시/매트릭스 조회를 같이 제공해 교수자 출결 입력 클릭 수를 줄임 |

## 최근 작업(교수자 수강생 상세 조회)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 담당과목/수강생추가 화면용 학생 상세 조회(API 단일 책임) | `public_html/tutor_lms/api/student_detail.jsp` | `src/dao/UserDao.java`(`TB_USER`), `src/dao/UserDeptDao.java`(`TB_USER_DEPT`), `src/dao/CourseUserDao.java`(`LM_COURSE_USER`) | (React API 응답 JSON) | 상세 API는 데이터 조회만 수행. 개인정보 로그는 기존 `public_html/tutor_lms/api/privacy_log.jsp`(가려진 정보 보기) 경로를 그대로 사용 |

## 최근 작업(교수자 문제은행 공개/비공개)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 문제은행 공개/비공개 저장 + 조회/출제 권한 통일 | `public_html/tutor_lms/api/question_bank_list.jsp`, `public_html/tutor_lms/api/question_bank_insert.jsp`, `public_html/tutor_lms/api/question_bank_modify.jsp`, `public_html/tutor_lms/api/question_bank_delete.jsp`, `public_html/tutor_lms/api/exam_template_insert.jsp`, `public_html/tutor_lms/api/exam_template_modify.jsp` | `src/dao/QuestionDao.java`(`LM_QUESTION.open_yn/manager_id/site_id/status`) | (React API 응답 JSON) | 비관리자는 `내 문제 OR 공개문제`만 조회/출제 가능, 수정/삭제는 작성자만 허용. DDL: `public_html/ddl_question_open_yn.sql` |

## 최근 작업(학사 성적 연동/다운로드)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 템플릿(HTML) | 비고 |
|---|---|---|---|---|
| 학사 평가기준 저장 시 성적결과 즉시 반영 | `public_html/tutor_lms/api/haksa_course_eval_update.jsp`, `public_html/tutor_lms/api/haksa_grade_list.jsp`, `public_html/tutor_lms/api/haksa_grade_update.jsp` | `src/dao/PolyCourseSettingDao.java`(`LM_POLY_COURSE_SETTING.eval_json`), `src/dao/PolyCourseGradeDao.java`(`LM_POLY_COURSE_GRADE.score/grade`) | (React API 응답 JSON) | `weights(attendance/midterm/final/assignment/etc/participation)` 합계 100 검증 후 저장, 평가 저장 직후 등급 재계산 + 조회/저장 시 서버 컷오프 기준 재판정 |
| 학사 성적 CSV 다운로드 API 추가 | `public_html/tutor_lms/api/haksa_grade_export.jsp` | `src/dao/PolyCourseGradeDao.java`, `src/dao/PolyCourseSettingDao.java`, `src/dao/UserDao.java` | (CSV 첨부 다운로드) | 연동 상태와 무관하게 `No/학번/이름/점수/등급` CSV 즉시 다운로드 제공. `login_id/member_key` 조인 컬레이션 충돌 방지를 위해 비교 컬레이션 명시 |

## 최근 작업(교수자 차시 추천 동영상 시간 동기화)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 관련 소스 | 비고 |
|---|---|---|---|---|
| 차시관리 > 콘텐츠 라이브러리 추천 탭에서 동영상 시간/인정시간 자동세팅 복구 | `POST public_html/tutor_lms/api/content_recommend.jsp`, `POST public_html/tutor_lms/api/kollus_lesson_upsert.jsp` | `src/dao/LessonDao.java` / `LM_LESSON`, `src/dao/KollusMediaDao.java` / `TB_KOLLUS_MEDIA`, `TB_KOLLUS_TRANSCRIPT(duration_seconds)` | `project/components/ContentLibraryModal.tsx` | `lessonId`가 숫자(`LM_LESSON.id`)로 들어오는 추천 데이터는 `start_url(media key)`로 정규화하고, DB 메타가 비면 `TB_KOLLUS_TRANSCRIPT` 시간을 분 단위로 보강. 업서트 단계에서도 `total_time` 미전달 시 전사시간으로 1회 보강해 인정시간 기본값 누락을 방지 |
| 콘텐츠 라이브러리 추천 탭 자연어 검색 정렬(학생 검색형) | `POST public_html/tutor_lms/api/content_recommend.jsp` → `POST /tutor/content-recommend/lessons` | `TB_RECO_CONTENT` (`title`, `summary`, `keywords`, `lesson_id`) | `polytech-lms-api/src/main/java/kr/polytech/lms/tutorcontentrecommend/service/TutorContentRecommendService.java`, `project/components/courseManagement/CurriculumTab.tsx`, `project/components/CurriculumEditor.tsx`, `project/components/courseManagement/WeeklyContentModal.tsx`, `project/components/courseManagement/EditContentModal.tsx` | 교수자 추천은 `courseName`만 질의로 사용(차시명/설명 제외)하고, 내부는 `키워드 DB 검색 + RETRIEVAL_QUERY 벡터검색 + 제목 매칭 재정렬` 구조를 유지. 학사/비정규 경로 모두 `recommendContext.courseName` 전달 강제, 프록시(`content_recommend.jsp`)는 `request_context` 로그로 유입 확인 |

## 최근 작업(교수자 차시관리 일괄등록/학사영상관리)
| 기능/화면 | 진입점(JSP/API) | 관련 DAO/테이블 | 관련 소스 | 비고 |
|---|---|---|---|---|
| 비정규 차시 일괄등록 + 더블클릭/중복요청 안정화 + 수정/삭제 키 보강 | `POST public_html/tutor_lms/api/curriculum_lesson_bulk_add.jsp`, `POST public_html/tutor_lms/api/curriculum_lesson_add.jsp`, `POST public_html/tutor_lms/api/curriculum_lesson_update.jsp`, `POST public_html/tutor_lms/api/curriculum_lesson_delete.jsp` | `src/dao/CourseLessonDao.java` / `LM_COURSE_LESSON`, `src/dao/LessonDao.java` / `LM_LESSON` | `project/api/tutorLmsApi.ts` | 대량 등록은 `lessons_json` 배열로 insert/update를 함께 처리(재실행 안전). 단건 추가는 중복/재활성화를 성공 응답으로 통일해 더블클릭 시 실패 오인 방지 |
| 비정규 수강생 자동승인 + 학사 영상 검토/수정/삭제 API | `GET public_html/tutor_lms/api/course_students_list.jsp`, `POST public_html/tutor_lms/api/course_students_auto_approve.jsp`, `GET public_html/tutor_lms/api/haksa_video_list.jsp`, `POST public_html/tutor_lms/api/haksa_video_update.jsp`, `POST public_html/tutor_lms/api/haksa_video_delete.jsp`, `POST public_html/tutor_lms/api/haksa_curriculum_update.jsp` | `src/dao/CourseUserDao.java` / `LM_COURSE_USER`, `src/dao/PolyCourseSettingDao.java` / `LM_POLY_COURSE_SETTING`, `src/dao/CourseLessonDao.java` / `LM_COURSE_LESSON`, `src/dao/LessonDao.java` / `LM_LESSON` | `project/api/tutorLmsApi.ts` | 학사 업데이트는 `sessionNo`(주차 내 차시)와 DB `chapter`(전체 순번)를 분리해 "차시 몰림" 회귀를 차단. 확인 근거: `cd project && npm run build` 성공 |

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
| GitHub Actions Firebase 배포 실패 복구 | `Deploy LMS to GCP + Firebase` 워크플로의 `Firebase vmproxy + Hosting 배포` 단계 | `tools/gcp/firebase-proxy-deploy/public/vmproxy-placeholder.txt`, `.github/workflows/deploy-lms-gcp.yml` | CI 재실행 시 Hosting public 경로 검증 통과 + rewrite 우선 적용 기반 확보 | 확인 근거: Run #7 로그에서 `Directory 'public' for Hosting does not exist` 원인 확인 후 추적 파일 추가, 루트 placeholder 노출 재발을 막기 위해 `index.html` 대신 non-index placeholder 유지 |
| GitHub Actions VM 배포 단계 무대기 보강 | `Deploy LMS to GCP + Firebase` 워크플로의 `원클릭 배포 실행 (VM 스택)` 단계 | `tools/gcp/one-click-setup.ps1` | CI에서 sudo/ssh 대기 프롬프트 제거 | 확인 근거: `gcloud compute scp/ssh`에 `BatchMode` 적용, 원격 실행 `sudo -n`으로 강제해 프롬프트 대기를 즉시 실패로 노출 |
| GitHub Actions 시크릿/프록시 안정화 | `Deploy LMS to GCP + Firebase` 워크플로의 사전 점검 + `vmproxy` 배포 단계 | `.github/workflows/deploy-lms-gcp.yml` | 시크릿 누락 즉시 실패 + VM 고정 IP 자동 동기화 + Firebase 인증 분기(토큰/서비스계정) | 확인 근거: `npx -y js-yaml .github/workflows/deploy-lms-gcp.yml` 파싱 성공, `node --check tools/gcp/firebase-proxy-deploy/functions/index.js` 문법 확인, `git diff`로 `TARGET` 치환/인증 분기 반영 확인 |
| GitHub Actions VM SSH 계정 불일치 복구 | `Deploy LMS to GCP + Firebase` 워크플로의 `원클릭 배포 실행 (VM 스택)` 단계 | `tools/gcp/one-click-setup.ps1`, `.github/workflows/deploy-lms-gcp.yml` | SSH 계정 자동 후보 탐색 + one-click 재시도(최대 2회) + 인자 바인딩 오류 제거 + root/sudo 경로 명시화 | 확인 근거: 최근 실패 run(#11, #12)이 동일하게 VM 스택 단계에서 중단된 이력 확인 후 `Deploy-StackToVm`에 `지정계정→메타 ssh-keys 계정→gcloud계정→newkl→ubuntu→root→기본호스트` 순차 시도 추가, run(#13,#14)에서 재현된 `SourceDbPort` 바인딩 오류를 원인으로 확인해 워크플로 원클릭 호출을 배열 스플랫(`@args`) 방식에서 명시적 파라미터 호출로 복구, 워크플로 `VM SSH 권한 사전 점검` 단계를 추가해 `sudo -n` 가능한 계정을 먼저 선별하고 SSH 타임아웃(`ConnectTimeout=15`)으로 무대기 보강, one-click 실패 시 진단 step(`로컬 JAR/VM SSH/VM sudo/원격 스크립트`)을 자동 실행해 로그 접근 없이도 원인 범위를 단계별로 추적 가능하게 개선, `VmSshUser`가 명시된 경우 해당 계정만 사용해 실패 원인 마스킹을 줄이도록 보강 |
| GitHub Actions Linux JAR 경로 호환 | `Deploy LMS to GCP + Firebase` 워크플로의 `원클릭 배포 실행 (VM 스택)` 단계 | `tools/gcp/one-click-setup.ps1` | ubuntu 러너에서 JAR 산출물 탐색 경로를 `build/libs/*.jar`로 통일 | 확인 근거: Linux에서 `build\\libs\\*.jar` 경로가 JAR 탐색 실패로 이어질 수 있어 `Build-ApiJar`를 경로 중립적으로 수정 |
| GitHub Actions 통계 폴더 누락 복구 | `Deploy LMS to GCP + Firebase` 워크플로의 `원클릭 배포 실행 (VM 스택)` 단계 | `.gitignore`, `통계/*.xlsx` | CI 체크아웃에 통계 원본 포함(원클릭 번들 생성 실패 방지) | 확인 근거: run(#26) `원클릭 배포 실행` 로그에서 `통계 폴더를 찾지 못했습니다` 오류 확인 후 `.gitignore`의 `통계` 제외 규칙 제거, 통계 엑셀 파일을 Git 추적 대상으로 반영 |
| GitHub Actions 원클릭 장기대기 차단 | `Deploy LMS to GCP + Firebase` 워크플로의 `원클릭 배포 실행 (VM 스택)` 단계 | `.github/workflows/deploy-lms-gcp.yml`, `tools/gcp/one-click-setup.ps1` | 워크플로 step 타임아웃(6분) + CI 재시도 1회 + 원격 `deploy-stack.sh` 실행 360초 상한 | 확인 근거: run(#27)에서 `원클릭 배포 실행`이 13분 이상 `in_progress`로 유지되는 상태 확인 후 fail-fast 정책을 더 단축 적용 |
| GitHub Actions 원격 배포 속도 최적화 | `Deploy LMS to GCP + Firebase` 워크플로의 VM 원격 배포 구간 | `tools/gcp/templates/deploy-stack.sh.tpl` | 설치 완료 VM에서 apt 재설치/이미지 pull 기본 생략(필요 시 `DEPLOY_FORCE_PULL=true`) | 확인 근거: run(#28)에서 6분 타임아웃 발생 로그 확인 후 `install_base_packages`/`start_stack`/`start_stack_for_import`를 속도 우선 경로로 보강 |
| GitHub Actions VM 업로드 시간 단축 | `Deploy LMS to GCP + Firebase` 워크플로의 VM 원격 배포 구간 | `tools/gcp/one-click-setup.ps1` | 배포 번들을 tar.gz 단일 파일로 전송 후 원격 압축해제(`scp --recurse` 제거) | 확인 근거: run(#29)에서 VM 배포 직후 6분 타임아웃 재발 확인 후 대량 파일 재귀 전송 병목을 해소하도록 업로드 방식을 변경 |
| 운영 장애 즉시 복구(web.app placeholder + Resin classes 권한) | `https://epoly-kopo.web.app/`, `https://epoly-kopo.web.app/tutor_lms/` | `tools/gcp/firebase-proxy-deploy/public/vmproxy-placeholder.txt`, `tools/gcp/templates/deploy-stack.sh.tpl` | 루트는 vmproxy rewrite 경유로 복원, Resin `WEB-INF/classes` 쓰기권한 보정으로 500 복구 | 확인 근거: 운영에서 root가 `vmproxy hosting placeholder`, `/tutor_lms/`가 `Cannot create directory: /var/resin/webapps/ROOT/WEB-INF/classes` 오류를 반환한 이력 기준으로 즉시 보정 |
| 운영 500 즉시 복구(Resin 런타임 컴파일 에러) | `https://epoly-kopo.web.app/`, `https://epoly-kopo.web.app/tutor_lms/` | `src/dao/CourseSectionDao.java` | `item(String,Object/int)` 오버로드 충돌 제거로 Resin 컴파일 에러 해소 | 확인 근거: 운영 500 응답에 `CourseSectionDao.java:36 reference to item is ambiguous`가 포함되어 있어 `section_id` 값을 int로 명시 변환 |
