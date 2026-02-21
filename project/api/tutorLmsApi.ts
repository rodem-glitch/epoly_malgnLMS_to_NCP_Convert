// 왜 필요한가:
// - `project` 화면(React)은 서버(JSP)에서 실제 데이터를 가져와서 즉시 보여줘야 합니다.
// - 그래서 화면마다 같은 fetch 코드를 복붙하지 않도록, 공통 API 호출 함수를 한 곳에 모읍니다.

export type TutorLmsApiResponse<T> = {
  rst_code: string;
  rst_message: string;
  rst_data?: T;
  rst_count?: number;
  rst_total?: number;
  rst_page?: number;
  rst_limit?: number;
  rst_channel?: string;
  rst_category?: string;
  rst_channels?: unknown;
  rst_categories?: unknown;
  rst_program_id?: number;
  rst_detached?: number;
  rst_skipped?: number;
  // 왜: 일부 API는 본문 외에 추가 정보를 같이 내려줍니다(exam/homework/course 등).
  rst_exam?: unknown;
  rst_homework?: unknown;
  rst_course?: unknown;
  // 왜: 대시보드처럼 여러 목록을 한 번에 내려주는 API가 있습니다.
  rst_courses?: unknown;
  rst_submissions?: unknown;
  rst_qna?: unknown;
  rst_summary?: unknown;
  rst_stat?: unknown;
  rst_survey?: unknown;
  rst_thread?: unknown;
  rst_question?: unknown;
  rst_responses?: unknown;
  rst_grade_data?: unknown;
};

function buildQuery(params: Record<string, string | number | undefined | null>) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    searchParams.set(key, String(value));
  });
  const qs = searchParams.toString();
  return qs ? `?${qs}` : '';
}

function isHtmlLike(text: string) {
  const t = text.trimStart().toLowerCase();
  if (!t) return false;
  return (
    t.startsWith('<!doctype html') ||
    t.startsWith('<html') ||
    t.startsWith('<head') ||
    t.startsWith('<body') ||
    t.startsWith('<script') ||
    t.startsWith('<div') ||
    t.startsWith('<span')
  );
}

function buildPreview(text: string, maxLen = 180) {
  const compact = text.replace(/\s+/g, ' ').trim();
  if (!compact) return '';
  if (compact.length <= maxLen) return compact;
  return `${compact.slice(0, maxLen)}…`;
}

async function requestJson<T>(url: string, options?: RequestInit): Promise<TutorLmsApiResponse<T>> {
  // 왜: 교수자 LMS API는 세션 기반이므로 쿠키가 항상 전달되어야 합니다.
  //     또한 프록시/리라이트 환경에서 "HTML"이 내려오는 문제를 빠르게 진단하려면,
  //     JSON을 기대한다는 헤더(Accept)를 기본으로 보내는 편이 안전합니다.
  const headers = new Headers(options?.headers || {});
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');

  const response = await fetch(url, {
    credentials: options?.credentials ?? 'include',
    ...options,
    headers,
  });
  const contentType = response.headers.get('content-type') || '';
  const rawText = await response.text();

  // 왜: 세션 만료/권한 문제일 때 HTML 페이지가 내려오면 JSON 파싱 오류가 발생합니다.
  //     JSON 여부를 먼저 확인해서 사용자에게 더 친절한 메시지를 보여줍니다.
  let parsed: TutorLmsApiResponse<T> | null = null;
  const trimmed = rawText.trim();
  const looksJson = contentType.includes('application/json') || trimmed.startsWith('{') || trimmed.startsWith('[');

  if (looksJson) {
    try {
      parsed = JSON.parse(rawText) as TutorLmsApiResponse<T>;
    } catch {
      parsed = null;
    }
  }

  if (!parsed || typeof parsed.rst_code !== 'string') {
    const lowerType = contentType.toLowerCase();
    const preview = buildPreview(trimmed);
    const htmlLike = lowerType.includes('text/html') || isHtmlLike(trimmed) || trimmed.startsWith('<');

    // 왜: 운영에서 “어느 API가 어떤 응답을 돌려줬는지”를 콘솔만 보고도 추적 가능해야 합니다.
    // 주의: 응답 본문 전체(개인정보 포함 가능)는 남기지 않고, 앞부분만 짧게 남깁니다.
    // eslint-disable-next-line no-console
    console.error('[tutorLmsApi] API 응답 파싱 실패', {
      requestUrl: url,
      responseUrl: response.url,
      redirected: response.redirected,
      status: response.status,
      ok: response.ok,
      contentType,
      preview,
    });

    const hint = (() => {
      if (htmlLike) {
        return response.ok
          ? 'API 응답이 HTML로 내려왔습니다. 로그인 상태/권한 또는 API 경로(프록시/리라이트 포함)를 확인해 주세요.'
          : `API 응답이 HTML로 내려왔습니다(HTTP ${response.status}). 로그인 상태/권한 또는 API 경로(프록시/리라이트 포함)를 확인해 주세요.`;
      }

      if (looksJson) {
        // 왜: Content-Type은 JSON인데 본문이 0바이트로 내려오는 경우가 있습니다.
        //     (예: 서버 공통 init.jsp에서 multipart 파싱 중 예외로 조기 종료)
        //     이때는 "rst_code 없음"보다 원인 힌트를 더 직접적으로 주는 편이 디버깅에 유리합니다.
        if (!trimmed) {
          return response.ok
            ? '서버가 JSON(Content-Type)로 응답했지만 본문이 비어 있습니다. 서버에서 요청 파싱(특히 FormData/multipart) 중 예외가 발생했을 가능성이 큽니다. 서버 로그와 업로드 임시폴더(data/tmp) 권한을 확인해 주세요.'
            : `서버 응답 오류(${response.status}). 서버가 JSON(Content-Type)로 응답했지만 본문이 비어 있습니다. 서버 로그와 업로드 임시폴더(data/tmp) 권한을 확인해 주세요.`;
        }

        // 왜: JSON은 맞는데 rst_code가 없으면(예: 다른 서버 응답) 화면에서 처리가 불가능합니다.
        return response.ok
          ? '서버 응답 JSON 형식이 예상과 다릅니다(rst_code 없음). 로그인 상태/권한 또는 API 경로를 확인해 주세요.'
          : `서버 응답 오류(${response.status}). 로그인 상태/권한 또는 API 경로를 확인해 주세요.`;
      }

      return response.ok
        ? '서버 응답이 JSON이 아닙니다. 로그인 상태/권한 또는 API 경로를 확인해 주세요.'
        : `서버 응답 오류(${response.status}). 로그인 상태/권한 또는 API 경로를 확인해 주세요.`;
    })();
    throw new Error(hint);
  }

  return parsed;
}

export type TutorProgramRow = {
  id: number;
  course_nm: string;
  start_date?: string;
  end_date?: string;
  training_period?: string;
  course_cnt?: number;
  plan_json?: string | null;
};

export type TutorCourseRow = {
  id: number;
  course_nm: string;
  mapped_course_id?: number;
  source_type?: 'prism' | 'haksa' | string;
  // 왜: course_list_combined.jsp에서는 화면에서 쓰기 편하게 변환값을 같이 내려줍니다.
  course_nm_conv?: string;
  course_cd?: string;
  course_id_conv?: string;
  subject_nm_conv?: string;
  course_type?: string;
  onoff_type?: string;
  course_type_conv?: string;
  onoff_type_conv?: string;
  year?: string;
  program_id?: number;
  program_nm?: string;
  program_nm_conv?: string;
  period_conv?: string;
  student_cnt?: number;
  status_label?: string;
  // ===== 학사 View 25개 필드 =====
  haksa_category?: string;        // 강좌형태
  haksa_dept_name?: string;       // 학과/전공 이름
  haksa_week?: string;            // 주차
  haksa_open_term?: string;       // 학기
  haksa_course_code?: string;     // 강좌코드
  haksa_visible?: string;         // 강좌 폐강 여부 (Y=정상, N=폐강)
  haksa_startdate?: string;       // 강좌시작일
  haksa_bunban_code?: string;     // 분반코드
  haksa_grade?: string;           // 학년
  haksa_grad_name?: string;       // 단과대학 이름
  haksa_day_cd?: string;          // 강의 요일
  haksa_classroom?: string;       // 강의실 정보
  haksa_curriculum_code?: string; // 과목구분 코드
  haksa_course_ename?: string;    // 강좌명(영문)
  haksa_type_syllabus?: string;   // 강의계획서 구분
  haksa_open_year?: string;       // 연도
  haksa_dept_code?: string;       // 학과/전공 코드
  haksa_course_name?: string;     // 강좌명(한글)
  haksa_group_code?: string;      // 학부/대학원 구분
  haksa_enddate?: string;         // 강좌종료일
  haksa_english?: string;         // 영문 강좌 여부
  haksa_hour1?: string;           // 강의 시간
  haksa_curriculum_name?: string; // 과목구분 이름
  haksa_grad_code?: string;       // 단과대학 코드
  haksa_is_syllabus?: string;     // 강의계획서 존재여부
};

export type TutorListRow = {
  user_id: number;
  tutor_nm: string;
};


// 통합 과목 타입 (API + 학사 View 모두 지원)
export type UnifiedCourseRow = {
  source: 'api' | 'poly';
  id: number | string;
  course_cd?: string;
  course_nm: string;
  course_id_conv?: string;
  subject_nm_conv?: string;
  year?: string;
  // API 전용 필드
  program_id?: number;
  program_nm_conv?: string;
  course_type?: string;
  onoff_type?: string;
  course_type_conv?: string;
  onoff_type_conv?: string;
  period_conv?: string;
  student_cnt?: number;
  status_label?: string;
  // 학사 View 전용 필드
  dept_code?: string;
  dept_name?: string;
  grad_code?: string;
  grad_name?: string;
  bunban_code?: string;
  curriculum_code?: string;
  curriculum_name?: string;
  grade?: string;
  open_term?: string;
  category?: string;
  visible?: string;
  course_ename?: string;
};

export type TutorCourseYearRow = {
  year: string;
};

export type TutorCourseCategoryRow = {
  id: number;
  category_nm: string;
  name_conv?: string;
  label?: string;
  parent_id?: number;
  depth?: number;
  display_yn?: 'Y' | 'N';
  status?: number;
};

export type TutorCourseInfoDetail = {
  id: number;
  course_nm: string;
  course_cd?: string;
  course_id_conv?: string;
  course_type?: string;
  onoff_type?: string;
  subject_id?: number;
  program_nm?: string;
  program_start_date?: string;
  program_end_date?: string;
  period_conv?: string;
  student_cnt?: number;

  content1_title?: string;
  content1?: string;
  content2_title?: string;
  content2?: string;

  // 평가/수료 기준
  assign_progress?: number;
  assign_exam?: number;
  assign_homework?: number;
  assign_forum?: number;
  assign_etc?: number;
  assign_survey_yn?: 'Y' | 'N';
  push_survey_yn?: 'Y' | 'N';
  pass_yn?: 'Y' | 'N';

  limit_total_score?: number;
  limit_progress?: number;
  complete_limit_progress?: number;
  complete_limit_total_score?: number;

  // 증명서
  cert_complete_yn?: 'Y' | 'N';
  cert_template_id?: number;
  pass_cert_template_id?: number;
  complete_no_yn?: 'Y' | 'N';
  complete_prefix?: string;
  postfix_cnt?: number;
  postfix_type?: 'R' | 'C';
  postfix_ord?: 'A' | 'D';
};

export type TutorCertificateTemplateRow = {
  id: number;
  template_nm?: string;
  template_cd?: string;
  template_type?: string;
  background_file?: string;
  reg_date?: string;
  status?: number;
};

// 문제 카테고리 타입 (교수자용)
export type TutorQuestionCategoryRow = {
  id: number;
  category_nm: string;
  parent_id: number;
  depth?: number;
  sort?: number;
  manager_id?: number;
  name_conv?: string; // 경로명 (예: "프로그래밍 > Java > 기초")
  label?: string; // 프론트엔드용 라벨
  status?: number;
};

// 문제은행 문제 타입 (교수자용)
export type TutorQuestionBankRow = {
  id: number;
  category_id?: number;
  category_nm?: string;
  question_type: number; // 1=단일선택, 2=다중선택, 3=단답형, 4=서술형
  question_type_conv?: string;
  question: string;
  question_text?: string;
  grade?: number; // 난이도 (1=A ~ 6=F)
  grade_conv?: string;
  item_cnt?: number;
  item1?: string;
  item2?: string;
  item3?: string;
  item4?: string;
  item5?: string;
  answer?: string;
  description?: string;
  score?: number; // 배점
  manager_id?: number;
  reg_date?: string;
  reg_date_conv?: string;
  status?: number;
  choices?: { id: string; text: string; file?: string }[];
};

// 시험 템플릿 타입 (교수자용)
export type TutorExamTemplateRow = {
  id: number;
  exam_nm: string;
  exam_time?: number;
  question_cnt?: number;
  shuffle_yn?: string;
  auto_complete_yn?: string;
  content?: string;
  range_idx?: string; // 선택된 문제 ID 목록 (쉼표 구분)
  assign1?: number; // 합격 점수로 활용
  total_points?: number; // 계산된 총점
  manager_id?: number;
  reg_date?: string;
  reg_date_conv?: string;
  status?: number;
};

export type TutorLearnerRow = {
  id: number;
  name?: string;
  student_id?: string;
  email?: string;
  dept_nm?: string;
  dept_path?: string;
};

export type TutorCourseImageUploadResult = {
  file_name: string;
  file_url: string;
  course_id?: number;
};

export type TutorProgramDetail = {
  id: number;
  site_id?: number;
  user_id?: number;
  course_nm: string;
  start_date?: string;
  end_date?: string;
  reg_date?: string;
  status?: number;
  plan_json?: string | null;
  course_nm_conv?: string;
  reg_date_conv?: string;
  start_date_conv?: string;
  end_date_conv?: string;
  training_period?: string;
};

export type TutorKollusRow = {
  id?: string;
  media_content_key: string;
  title: string;
  category_nm?: string;
  category_key?: string;
  duration?: string;
  total_time?: number;
  content_width?: number;
  content_height?: number;
  snapshot_url?: string;
  thumbnail?: string;
  original_file_name?: string;
  use_encryption_conv?: string;
  is_favorite?: boolean;
  media_id?: number;
};

export type TutorContentRecommendRow = TutorKollusRow & {
  // 왜: 추천 탭에서는 콜러스 영상 키값(문자열)이 있어야 바로 재생할 수 있습니다.
  lesson_id?: string;
  // 왜: 추천 점수는 디버깅/정렬/표시(선택)용입니다.
  score?: number;
  // 왜: TB_RECO_CONTENT의 요약/키워드를 표시하기 위함입니다.
  summary?: string;
  keywords?: string;
};

export type TutorKollusChannelRow = {
  key: string;
  name: string;
  count?: number;
};

export type TutorKollusCategoryRow = {
  key: string;
  name: string;
};

export type TutorCurriculumRow = {
  course_id: number;
  section_id: number;
  section_nm?: string;
  lesson_id: number;
  chapter: number;
  lesson_nm?: string;
  lesson_type?: string;
  total_time?: number;
  duration_conv?: string;
};

export type TutorCourseStudentRow = {
  course_user_id: number;
  user_id: number;
  course_id: number;
  student_id?: string;
  login_id?: string;
  name?: string;
  email?: string;
  progress?: number;
  progress_ratio?: number;
  total_score?: number;
  complete_yn?: string;
  complete_status?: string;
  complete_no?: string;
  start_date?: string;
  end_date?: string;
  status?: number;
};

export type HaksaCourseStudentRow = {
  student_id?: string;
  name?: string;
  email?: string;
  mobile?: string;
  visible?: string;
  course_code?: string;
  open_year?: string;
  open_term?: string;
  bunban_code?: string;
  group_code?: string;
  // LM_POLY_MEMBER 추가 필드
  user_type?: string;
  eng_name?: string;
  phone?: string;
  campus_code?: string;
  campus_name?: string;
  institution_code?: string;
  institution_name?: string;
  dept_code?: string;
  dept_name?: string;
  state?: string;
  use_yn?: string;
  gender?: string;
};

export type HaksaCourseKey = {
  courseCode: string;
  openYear: string;
  openTerm: string;
  bunbanCode: string;
  groupCode: string;
};

export type HaksaEvalSettings = {
  weights: {
    attendance: number;
    midterm: number;
    final: number;
    assignment: number;
    etc: number;
    participation: number;
  };
  cutoffs: {
    A: number;
    B: number;
    C: number;
    D: number;
    F: number;
  };
};

export type HaksaGradeRow = {
  student_id: string;
  grade?: string;
  score?: number;
};

export type HaksaResolveResult = {
  mapped_course_id: number;
  mapped_students?: number;
  skipped_students?: number;
  missing_students?: number;
};

export type HaksaVideoRow = {
  content_id: string;
  type: 'video' | string;
  title?: string;
  lesson_id?: number;
  media_key?: string;
  complete_time?: number;
  week_number?: number;
  week_title?: string;
  session_id?: string;
  session_name?: string;
  session_no?: number;
  chapter_no?: number;
  start_date?: string;
  start_time?: string;
  end_date?: string;
  end_time?: string;
};

export type TutorProgressSummaryRow = {
  chapter: number;
  section_id: number;
  section_nm?: string;
  lesson_id: number;
  lesson_nm?: string;
  lesson_type?: string;
  total_time?: number;
  duration_conv?: string;
  student_cnt?: number;
  complete_cnt?: number;
  complete_rate?: number;
  avg_ratio?: number;
  last_date_conv?: string;
};

export type TutorProgressStudentRow = {
  course_user_id: number;
  user_id: number;
  student_id?: string;
  name?: string;
  email?: string;
  ratio?: number;
  total_progress_ratio?: number;
  study_time?: number;
  study_time_conv?: string;
  complete_yn?: 'Y' | 'N';
  complete_date_conv?: string;
  last_date_conv?: string;
};

export type TutorProgressDetailRow = {
  course_user_id: number;
  user_id: number;
  student_id?: string;
  name?: string;
  email?: string;
  chapter?: number;
  lesson_id: number;
  lesson_nm?: string;
  lesson_type?: string;
  total_time?: number;
  complete_time?: number;
  ratio?: number;
  study_time?: number;
  study_time_conv?: string;
  curr_time?: number;
  last_time?: number;
  view_cnt?: number;
  curr_page?: string;
  study_page?: number;
  complete_yn?: 'Y' | 'N';
  complete_date_conv?: string;
  last_date_conv?: string;
};

// ----- 마감 운영(시험/과제/자료/Q&A/성적/수료/증명서) -----
export type TutorExamRow = {
  exam_id: number;
  exam_nm: string;
  exam_time?: number;
  question_cnt?: number;
  onoff_type?: string;
  assign_score?: number;
  start_date?: string;
  end_date?: string;
  start_date_conv?: string;
  end_date_conv?: string;
  total_cnt?: number;
  submitted_cnt?: number;
  confirmed_cnt?: number;
};

export type TutorExamUserRow = {
  course_user_id: number;
  user_id: number;
  login_id: string;
  user_nm: string;
  submitted?: boolean;
  submitted_at?: string;
  confirm?: boolean;
  confirm_at?: string;
  marking_score?: number;
  marking_score_conv?: string;
  score_conv?: string;
};

export type TutorHomeworkRow = {
  homework_id: number;
  homework_nm?: string;
  module_nm?: string;
  assign_score?: number;
  start_date?: string;
  end_date?: string;
  start_date_conv?: string;
  end_date_conv?: string;
  total_cnt?: number;
  submitted_cnt?: number;
  confirmed_cnt?: number;
  homework_file?: string;
};

export type TutorHomeworkUserRow = {
  course_user_id: number;
  user_id: number;
  login_id: string;
  user_nm: string;
  submitted?: boolean;
  submitted_at?: string;
  confirm?: boolean;
  confirm_at?: string;
  marking_score?: number;
  marking_score_conv?: string;
  score_conv?: string;
  feedback?: string;
  task_cnt?: number;
};

export type TutorHomeworkSubmissionFileRow = {
  id: number;
  filename: string;
  ek: string;
  download_url: string;
};

export type TutorHomeworkSubmissionDetail = {
  course_id: number;
  homework_id: number;
  course_user_id: number;
  submitted: boolean;
  submitted_at: string;
  subject: string;
  content: string;
  files: TutorHomeworkSubmissionFileRow[];
  feedback_files?: TutorHomeworkFeedbackFileRow[];
};

export type TutorHomeworkFeedbackFileRow = {
  id: number;
  filename: string;
  ek?: string;
  download_url?: string;
};

export type TutorHomeworkSimilarityRow = {
  id: number;
  run_id?: number;
  left_course_user_id: number;
  right_course_user_id: number;
  left_user_id?: number;
  right_user_id?: number;
  left_login_id?: string;
  left_user_nm?: string;
  right_login_id?: string;
  right_user_nm?: string;
  subject_score?: number;
  content_score?: number;
  file_score?: number;
  total_score?: number;
  subject_score_conv?: string;
  content_score_conv?: string;
  file_score_conv?: string;
  total_score_conv?: string;
};

export type TutorMaterialRow = {
  library_id: number;
  library_nm: string;
  content?: string;
  library_file?: string;
  library_link?: string;
  file_url?: string;
  file_size_conv?: string;
  upload_date_conv?: string;
};

export type TutorQnaRow = {
  id: number;
  subject: string;
  question_conv?: string;
  user_nm?: string;
  login_id?: string;
  reg_date_conv?: string;
  answered?: boolean;
  proc_status?: number;
};

export type TutorQnaDetail = {
  question_id: number;
  subject: string;
  question_content: string;
  question_user_nm: string;
  question_login_id: string;
  question_reg_date_conv?: string;
  answered?: boolean;
  answer_id?: number;
  answer_content?: string;
  answer_user_nm?: string;
  answer_login_id?: string;
  answer_reg_date_conv?: string;
};

export type TutorFaqNoticeRow = {
  faq_id: number;
  question: string;
  answer: string;
  reg_date?: string;
  mod_date?: string;
  reg_date_conv?: string;
  mod_date_conv?: string;
  display_yn?: 'Y' | 'N';
};

export type TutorHomeworkFeedbackTemplateRow = {
  id: number;
  course_id: number;
  manager_id: number;
  sort: number;
  content: string;
  content_preview?: string;
  reg_date?: string;
  mod_date?: string;
  reg_date_conv?: string;
  mod_date_conv?: string;
};

export type TutorSurveyRow = {
  survey_id: number;
  module_nm?: string;
  survey_nm?: string;
  item_cnt?: number;
  apply_type?: string;
  apply_type_conv?: string;
  apply_conv?: string;
  start_date?: string;
  end_date?: string;
  start_date_conv?: string;
  end_date_conv?: string;
  chapter?: number;
  total_cnt?: number;
  submitted_cnt?: number;
  survey_rate?: number | string;
  anonymous_yn?: 'Y' | 'N';
  anonymous_type_conv?: string;
};

export type TutorSurveyResultQuestionRow = {
  question_id: number;
  sort?: number;
  question_type?: string;
  question_type_conv?: string;
  question?: string;
  item_cnt?: number;
  item1?: string;
  item2?: string;
  item3?: string;
  item4?: string;
  item5?: string;
  item6?: string;
  item7?: string;
  item8?: string;
  item9?: string;
  item10?: string;
  item1_cnt?: number;
  item2_cnt?: number;
  item3_cnt?: number;
  item4_cnt?: number;
  item5_cnt?: number;
  item6_cnt?: number;
  item7_cnt?: number;
  item8_cnt?: number;
  item9_cnt?: number;
  item10_cnt?: number;
  response_cnt?: number;
};

export type TutorSurveyResponseRow = {
  course_user_id: number;
  answer?: string;
  answer_text?: string;
  answer_conv?: string;
  user_nm?: string;
  login_id?: string;
  reg_date?: string;
  reg_date_conv?: string;
};

export type TutorAttendanceLessonRow = {
  section_id: number;
  section_nm?: string;
  lesson_id: number;
  chapter: number;
  lesson_nm?: string;
  lesson_type?: string;
  total_time?: number;
  duration_conv?: string;
};

export type TutorAttendanceMatrixRow = {
  course_user_id: number;
  student_id?: string;
  name?: string;
  section_id?: number;
  section_nm?: string;
  lesson_id: number;
  chapter: number;
  attend_yn?: 'Y' | 'N';
  attend_label?: string;
  attend_date_conv?: string;
};

export type TutorAttendanceCourseSummaryRow = {
  id: number;
  course_nm?: string;
  course_id_conv?: string;
  program_nm_conv?: string;
  student_cnt?: number;
  lesson_cnt?: number;
  at_risk_cnt?: number;
  at_risk_cnt_conv?: string;
  absence_rule_yn?: 'Y' | 'N';
  absence_limit_cnt?: number;
  status_label?: string;
};

export type TutorScoreDistributionRow = {
  bucket_key: string;
  bucket_label?: string;
  min_score?: number;
  max_score?: number;
  student_count: number;
  ratio?: number;
};

export type TutorScoreDistributionSummary = {
  total_count: number;
  avg_score?: number;
  min_score?: number;
  max_score?: number;
  pass_count?: number;
  complete_count?: number;
  fail_count?: number;
};

export type TutorCourseChatRoomRow = {
  thread_id: number;
  question_id: number;
  student_user_id: number;
  student_user_nm?: string;
  student_login_id?: string;
  subject?: string;
  preview_conv?: string;
  waiting_answer?: boolean;
  question_reg_date_conv?: string;
  last_reg_date_conv?: string;
  last_sender_role?: 'student' | 'professor';
};

export type TutorCourseChatMessageRow = {
  id: number;
  thread: number;
  user_id: number;
  writer?: string;
  content?: string;
  sender_role?: 'student' | 'professor';
  mine?: boolean;
  reg_date?: string;
  reg_date_conv?: string;
};

export type TutorGradeRow = {
  course_user_id: number;
  user_id: number;
  login_id: string;
  user_nm: string;
  progress_ratio?: number;
  exam_score?: number;
  homework_score?: number;
  etc_score?: number;
  total_score?: number;
  status_label?: string;
};

export type TutorCompletionRow = {
  course_user_id: number;
  user_id: number;
  login_id: string;
  user_nm: string;
  progress_ratio?: number;
  total_score?: number;
  complete_status?: string;
  complete_yn?: string;
  complete_no?: string;
  complete_date_conv?: string;
  close_yn?: string;
  close_date_conv?: string;
  status_label?: string;
};


export type TutorDashboardStats = {
  active_course_cnt: number;
  pending_homework_cnt: number;
  unanswered_qna_cnt: number;
  today?: string;
};

export type TutorDashboardCourseRow = {
  id: number;
  course_cd?: string;
  course_nm: string;
  course_id_conv?: string;
  period_conv?: string;
  student_cnt?: number;
  avg_progress_ratio?: number;
  avg_progress_ratio_conv?: string;
  pending_homework_cnt?: number;
  unanswered_qna_cnt?: number;
  source_type?: 'prism' | 'haksa' | string;
};

export type TutorDashboardSubmissionRow = {
  course_id: number;
  course_id_conv?: string;
  course_nm: string;
  homework_id: number;
  homework_nm: string;
  course_user_id: number;
  user_nm?: string;
  login_id?: string;
  submit_date?: string;
  submitted_at?: string;
  confirm_yn?: string;
  confirmed?: boolean;
  source_type?: 'prism' | 'haksa' | string;
};

export type TutorHomeworkSubmissionRow = {
  course_id: number;
  course_nm: string;
  homework_id: number;
  homework_nm: string;
  course_user_id: number;
  user_nm?: string;
  login_id?: string;
  submit_date?: string;
  submitted_at?: string;
  confirm_yn?: string;
  confirmed?: boolean;
  source_type?: 'prism' | 'haksa' | string;
};

export type HaksaAttendanceRow = {
  course_user_id: number;
  student_id?: string;
  name?: string;
  attend_yn?: 'Y' | 'N';
  video_done_cnt?: number;
  video_total_cnt?: number;
  exam_done_cnt?: number;
  exam_total_cnt?: number;
  homework_done_cnt?: number;
  homework_total_cnt?: number;
};

// 과목별 통합 출력용 (과제 제출/피드백)
export type TutorHomeworkReportRow = {
  course_id: number;
  homework_id: number;
  homework_nm: string;
  course_user_id: number;
  user_nm?: string;
  login_id?: string;
  student_id?: string;
  submit_yn?: string;
  submitted?: boolean;
  submit_date?: string;
  submitted_at?: string;
  confirm_yn?: string;
  confirmed?: boolean;
  confirm_date?: string;
  confirm_at?: string;
  marking_score?: number;
  marking_score_conv?: string;
  score?: number;
  score_conv?: string;
  subject?: string;
  feedback?: string;
  task_cnt?: number;
  last_task_date?: string;
  last_task_date_conv?: string;
};

export type TutorDashboardQnaRow = {
  post_id: number;
  course_id: number;
  course_nm: string;
  subject: string;
  reg_date?: string;
  reg_date_conv?: string;
  proc_status?: number;
  answered?: boolean;
  user_nm?: string;
  login_id?: string;
  source_type?: 'prism' | 'haksa' | string;
};

export type TutorQnaManageRow = {
  post_id: number;
  course_id: number;
  course_nm: string;
  subject: string;
  reg_date?: string;
  reg_date_conv?: string;
  proc_status?: number;
  answered?: boolean;
  user_nm?: string;
  login_id?: string;
  source_type?: 'prism' | 'haksa' | string;
};

// 과목별 통합 출력용 (Q&A 질문/답변)
export type TutorQnaReportRow = {
  question_id: number;
  subject: string;
  question_content?: string;
  question_user_nm?: string;
  question_login_id?: string;
  question_reg_date?: string;
  question_reg_date_conv?: string;
  proc_status?: number;
  answered?: boolean;
  answer_id?: number;
  answer_content?: string;
  answer_user_nm?: string;
  answer_login_id?: string;
  answer_reg_date?: string;
  answer_reg_date_conv?: string;
};

export type TutorCourseResolveRow = {
  id?: number | string;
  mapped_course_id?: number;
  source_type?: 'prism' | 'haksa' | string;
  course_nm?: string;
  course_nm_conv?: string;
  course_cd?: string;
  course_id_conv?: string;
  course_type_conv?: string;
  onoff_type_conv?: string;
  program_nm_conv?: string;
  period_conv?: string;
  student_cnt?: number;
  status_label?: string;
  // 학사 필드 (있을 때만 내려옵니다)
  haksa_category?: string;
  haksa_dept_name?: string;
  haksa_week?: string;
  haksa_open_term?: string;
  haksa_course_code?: string;
  haksa_visible?: string;
  haksa_startdate?: string;
  haksa_bunban_code?: string;
  haksa_grade?: string;
  haksa_grad_name?: string;
  haksa_day_cd?: string;
  haksa_classroom?: string;
  haksa_curriculum_code?: string;
  haksa_course_ename?: string;
  haksa_type_syllabus?: string;
  haksa_open_year?: string;
  haksa_dept_code?: string;
  haksa_course_name?: string;
  haksa_group_code?: string;
  haksa_enddate?: string;
  haksa_english?: string;
  haksa_hour1?: string;
  haksa_curriculum_name?: string;
  haksa_grad_code?: string;
  haksa_is_syllabus?: string;
};

export const tutorLmsApi = {
  // =========================
  // 대시보드
  // =========================
  async getDashboard() {
    const url = `/tutor_lms/api/dashboard.jsp`;
    return requestJson<TutorDashboardStats>(url) as Promise<
      TutorLmsApiResponse<TutorDashboardStats> & {
        rst_courses?: TutorDashboardCourseRow[];
        rst_submissions?: TutorDashboardSubmissionRow[];
        rst_qna?: TutorDashboardQnaRow[];
      }
    >;
  },

  async getCourseResolve(params: { courseId: number; sourceType?: 'prism' | 'haksa' }) {
    const url = `/tutor_lms/api/course_resolve.jsp${buildQuery({ course_id: params.courseId, source_type: params.sourceType })}`;
    return requestJson<TutorCourseResolveRow>(url);
  },

  async getPrograms(params: { keyword?: string } = {}) {
    const url = `/tutor_lms/api/program_list.jsp${buildQuery({ s_keyword: params.keyword })}`;
    return requestJson<TutorProgramRow[]>(url);
  },

  async getHomeworkSubmissions(params: { keyword?: string; page?: number; pageSize?: number; startDate?: string; endDate?: string; status?: 'unconfirmed' | 'confirmed' } = {}) {
    const url = `/tutor_lms/api/homework_submissions.jsp${buildQuery({
      s_keyword: params.keyword,
      page: params.page,
      page_size: params.pageSize,
      start_date: params.startDate,
      end_date: params.endDate,
      status: params.status,
    })}`;
    return requestJson<TutorHomeworkSubmissionRow[]>(url);
  },

  async getQnaManageList(params: { keyword?: string; page?: number; pageSize?: number; startDate?: string; endDate?: string; status?: 'unanswered' | 'answered' } = {}) {
    const url = `/tutor_lms/api/qna_manage_list.jsp${buildQuery({
      s_keyword: params.keyword,
      page: params.page,
      page_size: params.pageSize,
      start_date: params.startDate,
      end_date: params.endDate,
      status: params.status,
    })}`;
    return requestJson<TutorQnaManageRow[]>(url);
  },

  // 왜: 과목별로 과제/피드백 + Q&A를 한 번에 출력해야 하므로 통합 데이터를 받습니다.
  async getCourseFeedbackReport(params: { courseId: number; startDate?: string; endDate?: string }) {
    const url = `/tutor_lms/api/course_feedback_report.jsp${buildQuery({
      course_id: params.courseId,
      start_date: params.startDate,
      end_date: params.endDate,
    })}`;
    return requestJson<unknown>(url) as Promise<
      TutorLmsApiResponse<unknown> & {
        rst_course?: unknown;
        rst_homework?: TutorHomeworkReportRow[];
        rst_qna?: TutorQnaReportRow[];
      }
    >;
  },

  async getProgram(id: number) {
    const url = `/tutor_lms/api/program_view.jsp${buildQuery({ id })}`;
    return requestJson<TutorProgramDetail>(url);
  },

  async createProgram(payload: { courseName: string; startDate: string; endDate: string; planJson: string }) {
    const body = new URLSearchParams();
    body.set('course_nm', payload.courseName);
    body.set('start_date', payload.startDate);
    body.set('end_date', payload.endDate);
    body.set('plan_json', payload.planJson);

    return requestJson<number>(`/tutor_lms/api/program_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getProgramCourses(params: { programId: number }) {
    const url = `/tutor_lms/api/program_course_list.jsp${buildQuery({ program_id: params.programId })}`;
    return requestJson<TutorCourseRow[]>(url);
  },

  async modifyProgram(payload: { id: number; courseName: string; startDate?: string; endDate?: string; planJson?: string }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));
    body.set('course_nm', payload.courseName);
    if (payload.startDate !== undefined) body.set('start_date', payload.startDate);
    if (payload.endDate !== undefined) body.set('end_date', payload.endDate);
    if (payload.planJson !== undefined) body.set('plan_json', payload.planJson);

    return requestJson<number>(`/tutor_lms/api/program_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteProgram(payload: { id: number }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));

    return requestJson<number>(`/tutor_lms/api/program_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getCourseYears(params: { tutorId?: number } = {}) {
    const url = `/tutor_lms/api/course_years.jsp${buildQuery({ tutor_id: params.tutorId })}`;
    return requestJson<TutorCourseYearRow[]>(url);
  },

  async getMyCourses(params: { year?: string; keyword?: string } = {}) {
    const url = `/tutor_lms/api/course_list.jsp${buildQuery({ year: params.year, s_keyword: params.keyword })}`;
    return requestJson<TutorCourseRow[]>(url);
  },

  // 왜: 학사 시스템(View) 과목을 별도 목록으로 조회할 때 사용합니다.
  async getPolyCourses(params: { year?: string } = {}) {
    const url = `/tutor_lms/api/course_list_poly.jsp${buildQuery({ year: params.year })}`;
    return requestJson<UnifiedCourseRow[]>(url);
  },

  // 왜: 담당과목 화면에서 학사/프리즘 탭을 분리하여 조회하기 위함
  async getMyCoursesCombined(params: {
    tab: 'prism' | 'haksa';
    year?: string;
    keyword?: string;
    page?: number;
    pageSize?: number;
    haksaCategory?: string;
    haksaGrad?: string;
    haksaCurriculum?: string;
    sortOrder?: 'asc' | 'desc';
  } = { tab: 'prism' }) {
    const url = `/tutor_lms/api/course_list_combined.jsp${buildQuery({
      tab: params.tab,
      year: params.year,
      s_keyword: params.keyword,
      page: params.page,
      page_size: params.pageSize,
      haksa_category: params.haksaCategory,
      haksa_grad: params.haksaGrad,
      haksa_curriculum: params.haksaCurriculum,
      sort_order: params.sortOrder,
    })}`;
    return requestJson<TutorCourseRow[]>(url);
  },

  // 왜: 과목 복사 시 담당 교수/강사 선택 목록을 만들기 위함
  async getTutors() {
    const url = `/tutor_lms/api/tutor_list.jsp`;
    return requestJson<TutorListRow[]>(url);
  },

  // 왜: 원본 과목을 복사해서 새 과목을 만들고 차시 편집으로 이어가기 위함
  async copyCourse(payload: { sourceCourseId: number; courseName: string; tutorId: number }) {
    const body = new URLSearchParams();
    body.set('source_course_id', String(payload.sourceCourseId));
    body.set('course_nm', payload.courseName);
    body.set('tutor_id', String(payload.tutorId));

    return requestJson<number>(`/tutor_lms/api/course_copy.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 담당과목 상세 상단 "삭제" 버튼에서 비정규 과목을 안전하게 비활성(status=-1) 처리합니다.
  async deleteCourse(payload: { courseId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));

    return requestJson<number>(`/tutor_lms/api/course_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async setCourseProgram(payload: { courseId: number; programId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('program_id', String(payload.programId));

    return requestJson<number>(`/tutor_lms/api/course_set_program.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // =========================
  // 과목정보(소개/목표/평가/증명서 설정)
  // =========================
  async getCourseInfo(params: { courseId: number }) {
    // 왜: 캐시 무효화를 위해 타임스탬프 추가 - 저장 후 최신 데이터를 보장합니다.
    const url = `/tutor_lms/api/course_info_get.jsp${buildQuery({ course_id: params.courseId, _t: Date.now() })}`;
    return requestJson<TutorCourseInfoDetail>(url);
  },

  async updateCourseInfo(payload: { courseId: number; content1: string; content2: string }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('content1', payload.content1);
    body.set('content2', payload.content2);

    return requestJson<number>(`/tutor_lms/api/course_info_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateCourseEvaluation(payload: {
    courseId: number;
    assignProgress: number;
    assignExam: number;
    assignFinal: number;
    assignHomework: number;
    assignForum: number;
    assignEtc: number;
    assignSurveyYn: 'Y' | 'N';
    pushSurveyYn: 'Y' | 'N';
    passYn: 'Y' | 'N';
    limitTotalScore: number;
    limitProgress: number;
    completeLimitProgress: number;
    completeLimitTotalScore: number;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));

    body.set('assign_progress', String(payload.assignProgress));
    body.set('assign_exam', String(payload.assignExam));
    // TODO: 백엔드에 assign_final 컬럼 추가 후 활성화
    body.set('assign_final', String(payload.assignFinal));
    body.set('assign_homework', String(payload.assignHomework));
    body.set('assign_forum', String(payload.assignForum));
    body.set('assign_etc', String(payload.assignEtc));

    body.set('assign_survey_yn', payload.assignSurveyYn);
    body.set('push_survey_yn', payload.pushSurveyYn);
    body.set('pass_yn', payload.passYn);

    body.set('limit_total_score', String(payload.limitTotalScore));
    body.set('limit_progress', String(payload.limitProgress));

    body.set('complete_limit_progress', String(payload.completeLimitProgress));
    body.set('complete_limit_total_score', String(payload.completeLimitTotalScore));

    return requestJson<number>(`/tutor_lms/api/course_evaluation_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateCourseCertificateSettings(payload: {
    courseId: number;
    certCompleteYn: 'Y' | 'N';
    certTemplateId: number;
    passCertTemplateId: number;
    completeNoYn: 'Y' | 'N';
    completePrefix: string;
    postfixCnt: number;
    postfixType: 'R' | 'C';
    postfixOrd: 'A' | 'D';
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('cert_complete_yn', payload.certCompleteYn);
    body.set('cert_template_id', String(payload.certTemplateId));
    body.set('pass_cert_template_id', String(payload.passCertTemplateId));

    body.set('complete_no_yn', payload.completeNoYn);
    body.set('complete_prefix', payload.completePrefix);
    body.set('postfix_cnt', String(payload.postfixCnt));
    body.set('postfix_type', payload.postfixType);
    body.set('postfix_ord', payload.postfixOrd);

    return requestJson<number>(`/tutor_lms/api/course_certificate_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // ----- 과목개설(CreateSubjectWizard) -----
  async createCourse(payload: {
    courseName: string;
    year: string;
    studyStartDate: string;
    studyEndDate: string;
    programId?: number;
    categoryId?: number;
    semester?: string;
    credit?: string | number;
    lessonDay?: string | number;
    lessonTime?: string | number;
    content1?: string;
    content2?: string;
    courseFile?: string;
  }) {
    const body = new URLSearchParams();
    body.set('course_nm', payload.courseName);
    body.set('year', payload.year);
    body.set('study_sdate', payload.studyStartDate);
    body.set('study_edate', payload.studyEndDate);
    if (payload.programId) body.set('program_id', String(payload.programId));
    if (payload.categoryId) body.set('category_id', String(payload.categoryId));
    if (payload.semester) body.set('semester', payload.semester);
    if (payload.credit !== undefined && payload.credit !== null && String(payload.credit) !== '') body.set('credit', String(payload.credit));
    if (payload.lessonDay !== undefined && payload.lessonDay !== null && String(payload.lessonDay) !== '') body.set('lesson_day', String(payload.lessonDay));
    if (payload.lessonTime !== undefined && payload.lessonTime !== null && String(payload.lessonTime) !== '') body.set('lesson_time', String(payload.lessonTime));
    if (payload.content1) body.set('content1', payload.content1);
    if (payload.content2) body.set('content2', payload.content2);
    if (payload.courseFile) body.set('course_file', payload.courseFile);

    return requestJson<number>(`/tutor_lms/api/course_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getCourseCategories() {
    // 왜: "과정 카테고리"를 관리자(sysop)와 같은 기준(LM_CATEGORY)으로 보여주기 위해 필요합니다.
    return requestJson<TutorCourseCategoryRow[]>(`/tutor_lms/api/course_categories.jsp`);
  },

  async getLearners(params: { keyword?: string; deptId?: number; deptKeyword?: string; page?: number; limit?: number } = {}) {
    const url = `/tutor_lms/api/learner_list.jsp${buildQuery({
      s_keyword: params.keyword,
      dept_id: params.deptId,
      s_dept: params.deptKeyword,
      page: params.page,
      limit: params.limit,
    })}`;
    return requestJson<TutorLearnerRow[]>(url);
  },

  async uploadCourseImage(payload: { file: File; courseId?: number }) {
    const formData = new FormData();
    formData.set('course_file', payload.file);
    if (payload.courseId) formData.set('course_id', String(payload.courseId));

    return requestJson<TutorCourseImageUploadResult>(`/tutor_lms/api/course_image_upload.jsp`, {
      method: 'POST',
      body: formData,
    });
  },

  // ----- 콘텐츠(콜러스 목록) -----
  async getKollusList(params: { keyword?: string; channelKey?: string; categoryKey?: string; page?: number; limit?: number; version?: number } = {}) {
    const url = `/tutor_lms/api/kollus_list.jsp${buildQuery({
      s_keyword: params.keyword,
      s_channel: params.channelKey,
      s_category: params.categoryKey,
      page: params.page,
      limit: params.limit,
      version: params.version,
    })}`;
    return requestJson<TutorKollusRow[]>(url);
  },

  async getContentRecommendations(payload: {
    courseName?: string;
    courseIntro?: string;
    courseDetail?: string;
    lessonTitle?: string;
    lessonDescription?: string;
    keywords?: string;
    topK?: number;
    similarityThreshold?: number;
  }) {
    // 왜: 프론트는 JSP API(`/tutor_lms/api/*`)만 호출하므로, 추천 API도 같은 패턴으로 제공해야 합니다.
    const body = new URLSearchParams();
    if (payload.courseName) body.set('course_name', payload.courseName);
    if (payload.courseIntro) body.set('course_intro', payload.courseIntro);
    if (payload.courseDetail) body.set('course_detail', payload.courseDetail);
    if (payload.lessonTitle) body.set('lesson_title', payload.lessonTitle);
    if (payload.lessonDescription) body.set('lesson_description', payload.lessonDescription);
    if (payload.keywords) body.set('keywords', payload.keywords);
    if (payload.topK !== undefined) body.set('top_k', String(payload.topK));
    if (payload.similarityThreshold !== undefined) body.set('similarity_threshold', String(payload.similarityThreshold));

    return requestJson<TutorContentRecommendRow[]>(`/tutor_lms/api/content_recommend.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getKollusWishlistList(params: { keyword?: string; page?: number; limit?: number } = {}) {
    const url = `/tutor_lms/api/kollus_wishlist_list.jsp${buildQuery({
      s_keyword: params.keyword,
      page: params.page,
      limit: params.limit,
    })}`;
    return requestJson<TutorKollusRow[]>(url);
  },

  async toggleKollusWishlist(payload: {
    mediaContentKey: string;
    title?: string;
    snapshotUrl?: string;
    categoryKey?: string;
    categoryName?: string;
    originalFileName?: string;
    totalTime?: number;
    contentWidth?: number;
    contentHeight?: number;
  }) {
    const body = new URLSearchParams();
    body.set('media_content_key', payload.mediaContentKey);
    if (payload.title) body.set('title', payload.title);
    if (payload.snapshotUrl) body.set('snapshot_url', payload.snapshotUrl);
    if (payload.categoryKey) body.set('category_key', payload.categoryKey);
    if (payload.categoryName) body.set('category_nm', payload.categoryName);
    if (payload.originalFileName) body.set('original_file_name', payload.originalFileName);
    if (payload.totalTime !== undefined) body.set('total_time', String(payload.totalTime));
    if (payload.contentWidth !== undefined) body.set('content_width', String(payload.contentWidth));
    if (payload.contentHeight !== undefined) body.set('content_height', String(payload.contentHeight));

    return requestJson<number>(`/tutor_lms/api/kollus_wishlist_toggle.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async upsertKollusLesson(payload: { mediaContentKey: string; title?: string; totalTime?: number; contentWidth?: number; contentHeight?: number }) {
    const body = new URLSearchParams();
    body.set('media_content_key', payload.mediaContentKey);
    if (payload.title) body.set('title', payload.title);
    if (payload.totalTime !== undefined) body.set('total_time', String(payload.totalTime));
    if (payload.contentWidth !== undefined) body.set('content_width', String(payload.contentWidth));
    if (payload.contentHeight !== undefined) body.set('content_height', String(payload.contentHeight));

    return requestJson<number>(`/tutor_lms/api/kollus_lesson_upsert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 교수 차시관리에서 외부링크(URL)를 직접 입력하여 레슨으로 등록하기 위함입니다.
  async upsertExternalLinkLesson(payload: { url: string; title: string; totalTime?: number }) {
    const body = new URLSearchParams();
    body.set('url', payload.url);
    body.set('title', payload.title);
    if (payload.totalTime !== undefined) body.set('total_time', String(payload.totalTime));

    return requestJson<number>(`/tutor_lms/api/external_link_lesson_upsert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },


  // ----- 강의목차(curriculum_*) -----
  async getCurriculum(params: { courseId: number }) {
    const url = `/tutor_lms/api/curriculum_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorCurriculumRow[]>(url);
  },


  async insertCurriculumSection(payload: { courseId: number; sectionName: string }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('section_nm', payload.sectionName);

    return requestJson<number>(`/tutor_lms/api/curriculum_section_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async modifyCurriculumSection(payload: { courseId: number; sectionId: number; sectionName: string }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('section_id', String(payload.sectionId));
    body.set('section_nm', payload.sectionName);

    return requestJson<number>(`/tutor_lms/api/curriculum_section_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteCurriculumSection(payload: { courseId: number; sectionId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('section_id', String(payload.sectionId));

    return requestJson<number>(`/tutor_lms/api/curriculum_section_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async addCurriculumLesson(payload: {
    courseId: number;
    sectionId: number;
    lessonId?: number;
    url?: string;
    title?: string;
    chapter?: number;
    startDate?: string;
    endDate?: string;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('section_id', String(payload.sectionId));
    if (payload.lessonId) body.set('lesson_id', String(payload.lessonId));
    if (payload.url) body.set('url', payload.url);
    if (payload.title) body.set('title', payload.title);
    if (payload.chapter !== undefined) body.set('chapter', String(payload.chapter));
    if (payload.startDate !== undefined) body.set('start_date', payload.startDate);
    if (payload.endDate !== undefined) body.set('end_date', payload.endDate);

    return requestJson<number>(`/tutor_lms/api/curriculum_lesson_add.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async addCurriculumLessonsBulk(payload: {
    courseId: number;
    lessons: Array<{
      sectionId?: number;
      lessonId?: number;
      url?: string;
      title?: string;
      chapter?: number;
      startDate?: string;
      endDate?: string;
      completeTime?: number;
    }>;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set(
      'lessons_json',
      JSON.stringify(
        (payload.lessons || []).map((item) => ({
          section_id: item.sectionId ?? 0,
          lesson_id: item.lessonId,
          url: item.url,
          title: item.title,
          chapter: item.chapter,
          start_date: item.startDate,
          end_date: item.endDate,
          complete_time: item.completeTime,
        }))
      )
    );

    return requestJson<number>(`/tutor_lms/api/curriculum_lesson_bulk_add.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteCurriculumLesson(payload: { courseId: number; lessonId: number; chapter?: number; sectionId?: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('lesson_id', String(payload.lessonId));
    if (payload.chapter !== undefined) body.set('chapter', String(payload.chapter));
    if (payload.sectionId !== undefined) body.set('section_id', String(payload.sectionId));

    return requestJson<number>(`/tutor_lms/api/curriculum_lesson_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateCurriculumLesson(payload: {
    courseId: number;
    lessonId: number;
    chapter?: number;
    sectionId?: number;
    completeTime?: number;
    tutorId?: number;
    startDate?: string;
    endDate?: string;
    sourceChapter?: number;
    sourceSectionId?: number;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('lesson_id', String(payload.lessonId));
    if (payload.chapter !== undefined) body.set('chapter', String(payload.chapter));
    if (payload.sectionId !== undefined) body.set('section_id', String(payload.sectionId));
    if (payload.completeTime !== undefined) body.set('complete_time', String(payload.completeTime));
    if (payload.tutorId !== undefined) body.set('tutor_id', String(payload.tutorId));
    if (payload.startDate !== undefined) body.set('start_date', payload.startDate);
    if (payload.endDate !== undefined) body.set('end_date', payload.endDate);
    if (payload.sourceChapter !== undefined) body.set('source_chapter', String(payload.sourceChapter));
    if (payload.sourceSectionId !== undefined) body.set('source_section_id', String(payload.sourceSectionId));

    return requestJson<number>(`/tutor_lms/api/curriculum_lesson_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // ----- 수강생(course_students_*) -----
  async getCourseStudents(params: { courseId: number; keyword?: string }) {
    const url = `/tutor_lms/api/course_students_list.jsp${buildQuery({ course_id: params.courseId, s_keyword: params.keyword })}`;
    return requestJson<TutorCourseStudentRow[]>(url);
  },

  async autoApproveCourseStudents(payload: { courseId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));

    return requestJson<number>(`/tutor_lms/api/course_students_auto_approve.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // ----- 학사 수강생(읽기 전용) -----
  async getHaksaCourseStudents(params: {
    courseCode: string;
    openYear?: string;
    openTerm?: string;
    bunbanCode?: string;
    groupCode?: string;
    keyword?: string;
  }) {
    const url = `/tutor_lms/api/haksa_students_list.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
      s_keyword: params.keyword,
    })}`;
    return requestJson<HaksaCourseStudentRow[]>(url);
  },

  // ----- 개인정보 로그 -----
  async logPrivacyAccess(payload: {
    logType: 'V' | 'E';
    purpose: string;
    pageName?: string;
    courseId?: number;
    userIds?: number[];
    userCnt?: number;
  }) {
    const body = new URLSearchParams();
    body.set('log_type', payload.logType);
    body.set('purpose', payload.purpose);
    if (payload.pageName) body.set('page_nm', payload.pageName);
    if (payload.courseId !== undefined) body.set('course_id', String(payload.courseId));
    if (payload.userIds && payload.userIds.length > 0) body.set('user_ids', payload.userIds.join(','));
    if (payload.userCnt !== undefined) body.set('user_cnt', String(payload.userCnt));

    return requestJson<number>(`/tutor_lms/api/privacy_log.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // =========================
  // 학사 과목: 평가/강의목차/시험/성적
  // =========================
  async getHaksaCourseEval(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_course_eval_get.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<{ eval_json?: string }>(url);
  },

  async updateHaksaCourseEval(params: HaksaCourseKey & { evalJson: string }) {
    const body = new URLSearchParams();
    body.set('course_code', params.courseCode);
    body.set('open_year', params.openYear);
    body.set('open_term', params.openTerm);
    body.set('bunban_code', params.bunbanCode);
    body.set('group_code', params.groupCode);
    body.set('eval_json', params.evalJson);

    return requestJson<number>(`/tutor_lms/api/haksa_course_eval_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHaksaCurriculum(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_curriculum_get.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<{ curriculum_json?: string }>(url);
  },

  async updateHaksaCurriculum(params: HaksaCourseKey & { curriculumJson: string }) {
    const body = new URLSearchParams();
    body.set('course_code', params.courseCode);
    body.set('open_year', params.openYear);
    body.set('open_term', params.openTerm);
    body.set('bunban_code', params.bunbanCode);
    body.set('group_code', params.groupCode);
    body.set('curriculum_json', params.curriculumJson);

    return requestJson<number>(`/tutor_lms/api/haksa_curriculum_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHaksaVideos(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_video_list.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<HaksaVideoRow[]>(url);
  },

  async updateHaksaVideo(params: HaksaCourseKey & {
    contentId: string;
    title?: string;
    mediaKey?: string;
    completeTime?: number;
  }) {
    const body = new URLSearchParams();
    body.set('course_code', params.courseCode);
    body.set('open_year', params.openYear);
    body.set('open_term', params.openTerm);
    body.set('bunban_code', params.bunbanCode);
    body.set('group_code', params.groupCode);
    body.set('content_id', params.contentId);
    if (params.title !== undefined) body.set('title', params.title);
    if (params.mediaKey !== undefined) body.set('media_key', params.mediaKey);
    if (params.completeTime !== undefined) body.set('complete_time', String(params.completeTime));

    return requestJson<number>(`/tutor_lms/api/haksa_video_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteHaksaVideo(params: HaksaCourseKey & { contentId: string }) {
    const body = new URLSearchParams();
    body.set('course_code', params.courseCode);
    body.set('open_year', params.openYear);
    body.set('open_term', params.openTerm);
    body.set('bunban_code', params.bunbanCode);
    body.set('group_code', params.groupCode);
    body.set('content_id', params.contentId);

    return requestJson<number>(`/tutor_lms/api/haksa_video_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHaksaAttendance(params: HaksaCourseKey & { courseId?: number; sessionId: string }) {
    const url = `/tutor_lms/api/haksa_attendance.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
      course_id: params.courseId,
      session_id: params.sessionId,
    })}`;
    return requestJson<HaksaAttendanceRow[]>(url);
  },

  async getHaksaExams(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_exam_get.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<{ exams_json?: string }>(url);
  },

  async updateHaksaExams(params: HaksaCourseKey & { examsJson: string }) {
    const body = new URLSearchParams();
    body.set('course_code', params.courseCode);
    body.set('open_year', params.openYear);
    body.set('open_term', params.openTerm);
    body.set('bunban_code', params.bunbanCode);
    body.set('group_code', params.groupCode);
    body.set('exams_json', params.examsJson);

    return requestJson<number>(`/tutor_lms/api/haksa_exam_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHaksaGrades(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_grade_list.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<HaksaGradeRow[]>(url);
  },
  async resolveHaksaCourse(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_resolve.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<HaksaResolveResult>(url);
  },

  async updateHaksaGrades(params: HaksaCourseKey & { gradesJson: string }) {
    const body = new URLSearchParams();
    body.set('course_code', params.courseCode);
    body.set('open_year', params.openYear);
    body.set('open_term', params.openTerm);
    body.set('bunban_code', params.bunbanCode);
    body.set('group_code', params.groupCode);
    body.set('grades_json', params.gradesJson);

    return requestJson<number>(`/tutor_lms/api/haksa_grade_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async addCourseStudents(payload: { courseId: number; userIds: Array<number | string> }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('user_ids', payload.userIds.join(','));

    return requestJson<number>(`/tutor_lms/api/course_students_add.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async removeCourseStudent(payload: { courseId: number; userId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('user_id', String(payload.userId));

    return requestJson<number>(`/tutor_lms/api/course_students_remove.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // ----- 진도(progress_*) -----
  async getProgressSummary(params: { courseId: number }) {
    const url = `/tutor_lms/api/progress_summary.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorProgressSummaryRow[]>(url);
  },

  async getProgressStudents(params: { courseId: number; lessonId: number }) {
    const url = `/tutor_lms/api/progress_students.jsp${buildQuery({ course_id: params.courseId, lesson_id: params.lessonId })}`;
    return requestJson<TutorProgressStudentRow[]>(url);
  },

  async getProgressDetail(params: { courseId: number; courseUserId: number; lessonId: number }) {
    const url = `/tutor_lms/api/progress_detail.jsp${buildQuery({
      course_id: params.courseId,
      course_user_id: params.courseUserId,
      lesson_id: params.lessonId,
    })}`;
    return requestJson<TutorProgressDetailRow>(url);
  },

  // =========================
  // 마감 운영: 시험
  // =========================
  async getExams(params: { courseId: number }) {
    const url = `/tutor_lms/api/exam_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorExamRow[]>(url);
  },

  async createExam(payload: {
    courseId: number;
    title: string;
    description?: string;
    examDate: string;
    examTime: string;
    examEndDate?: string;
    examEndTime?: string;
    duration: number;
    questionCount?: number;
    totalScore?: number;
    allowRetake?: boolean;
    showResults?: boolean;
    onoffType?: 'N' | 'F';
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('title', payload.title);
    body.set('description', payload.description ?? '');
    body.set('examDate', payload.examDate);
    body.set('examTime', payload.examTime);
    if (payload.examEndDate) body.set('examEndDate', payload.examEndDate);
    if (payload.examEndTime) body.set('examEndTime', payload.examEndTime);
    body.set('duration', String(payload.duration));
    body.set('questionCount', String(payload.questionCount ?? 0));
    body.set('totalScore', String(payload.totalScore ?? 100));
    body.set('allowRetake', String(Boolean(payload.allowRetake)));
    body.set('showResults', String(payload.showResults ?? true));
    body.set('onoff_type', payload.onoffType ?? 'F');

    return requestJson<number>(`/tutor_lms/api/exam_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateExam(payload: {
    courseId: number;
    examId: number;
    title: string;
    description?: string;
    examDate: string;
    examTime: string;
    examEndDate?: string;
    examEndTime?: string;
    duration: number;
    questionCount?: number;
    totalScore?: number;
    allowRetake?: boolean;
    showResults?: boolean;
    onoffType?: 'N' | 'F';
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('exam_id', String(payload.examId));
    body.set('title', payload.title);
    body.set('description', payload.description ?? '');
    body.set('examDate', payload.examDate);
    body.set('examTime', payload.examTime);
    if (payload.examEndDate) body.set('examEndDate', payload.examEndDate);
    if (payload.examEndTime) body.set('examEndTime', payload.examEndTime);
    body.set('duration', String(payload.duration));
    body.set('questionCount', String(payload.questionCount ?? 0));
    body.set('totalScore', String(payload.totalScore ?? 100));
    body.set('allowRetake', String(Boolean(payload.allowRetake)));
    body.set('showResults', String(payload.showResults ?? true));
    body.set('onoff_type', payload.onoffType ?? 'F');

    return requestJson<number>(`/tutor_lms/api/exam_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 기존 시험을 과목에 연결만 합니다 (시험 복사 없음)
  async linkExam(payload: {
    courseId: number;
    examId: number;
    startDate?: string;
    endDate?: string;
    assignScore?: number;
    allowRetake?: boolean;
    showResults?: boolean;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('exam_id', String(payload.examId));
    if (payload.startDate) body.set('start_date', payload.startDate);
    if (payload.endDate) body.set('end_date', payload.endDate);
    if (payload.assignScore !== undefined) body.set('assign_score', String(payload.assignScore));
    if (payload.allowRetake !== undefined) body.set('retry_yn', payload.allowRetake ? 'Y' : 'N');
    if (payload.showResults !== undefined) body.set('result_yn', payload.showResults ? 'Y' : 'N');

    return requestJson<number>(`/tutor_lms/api/exam_link.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteExam(payload: { courseId: number; examId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('exam_id', String(payload.examId));

    return requestJson<number>(`/tutor_lms/api/exam_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getExamUsers(params: { courseId: number; examId: number }) {
    const url = `/tutor_lms/api/exam_users.jsp${buildQuery({ course_id: params.courseId, exam_id: params.examId })}`;
    return requestJson<TutorExamUserRow[]>(url);
  },

  async updateExamScore(payload: { courseId: number; examId: number; courseUserId: number; markingScore: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('exam_id', String(payload.examId));
    body.set('course_user_id', String(payload.courseUserId));
    body.set('marking_score', String(payload.markingScore));

    return requestJson<number>(`/tutor_lms/api/exam_score_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 응시 취소는 점수 수정과 성격이 달라 별도 엔드포인트로 분리합니다.
  async cancelExamSubmit(payload: { courseId: number; examId: number; courseUserId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('exam_id', String(payload.examId));
    body.set('course_user_id', String(payload.courseUserId));

    return requestJson<number>(`/tutor_lms/api/exam_submit_cancel.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // =========================
  // 마감 운영: 과제
  // =========================
  async getHomeworks(params: { courseId: number }) {
    const url = `/tutor_lms/api/homework_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorHomeworkRow[]>(url);
  },

  async createHomework(payload: {
    courseId: number;
    courseIds?: number[];
    title: string;
    description: string;
    startDate?: string;
    startTime?: string;
    dueDate: string;
    dueTime: string;
    totalScore: number;
    onoffType?: 'N' | 'F';
    submitFileExtMode?: 'ALL' | 'DOC' | 'IMAGE' | 'ARCHIVE' | 'AUDIO' | 'CUSTOM';
    submitFileExts?: string;
    allowLateSubmission?: boolean;
    latePenalty?: number;
    file?: File | null;
  }) {
    const body = new FormData();
    body.set('course_id', String(payload.courseId));
    if (payload.courseIds && payload.courseIds.length > 0) body.set('course_ids', payload.courseIds.join(','));
    body.set('title', payload.title);
    body.set('description', payload.description);
    if (payload.startDate) body.set('startDate', payload.startDate);
    if (payload.startTime) body.set('startTime', payload.startTime);
    body.set('dueDate', payload.dueDate);
    body.set('dueTime', payload.dueTime);
    body.set('totalScore', String(payload.totalScore));
    body.set('onoff_type', payload.onoffType ?? 'N');
    body.set('submit_file_ext_mode', payload.submitFileExtMode ?? 'ALL');
    body.set('submit_file_exts', payload.submitFileExts ?? '');
    body.set('allowLateSubmission', payload.allowLateSubmission ? 'Y' : 'N');
    body.set('latePenalty', String(Math.max(0, Math.min(100, Number(payload.latePenalty ?? 0)))));
    if(payload.file) body.set('homework_file', payload.file);

    return requestJson<number>(`/tutor_lms/api/homework_insert.jsp`, {
      method: 'POST',
      body,
    });
  },

  async updateHomework(payload: {
    courseId: number;
    homeworkId: number;
    title: string;
    description: string;
    startDate?: string;
    startTime?: string;
    dueDate: string;
    dueTime: string;
    totalScore: number;
    onoffType?: 'N' | 'F';
    deleteHomeworkFileYn?: boolean;
    submitFileExtMode?: 'ALL' | 'DOC' | 'IMAGE' | 'ARCHIVE' | 'AUDIO' | 'CUSTOM';
    submitFileExts?: string;
    allowLateSubmission?: boolean;
    latePenalty?: number;
    file?: File | null;
  }) {
    const body = new FormData();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    body.set('title', payload.title);
    body.set('description', payload.description);
    if (payload.startDate) body.set('startDate', payload.startDate);
    if (payload.startTime) body.set('startTime', payload.startTime);
    body.set('dueDate', payload.dueDate);
    body.set('dueTime', payload.dueTime);
    body.set('totalScore', String(payload.totalScore));
    body.set('onoff_type', payload.onoffType ?? 'N');
    body.set('delete_homework_file_yn', payload.deleteHomeworkFileYn ? 'Y' : 'N');
    if (payload.submitFileExtMode) body.set('submit_file_ext_mode', payload.submitFileExtMode);
    if (payload.submitFileExts !== undefined) body.set('submit_file_exts', payload.submitFileExts);
    if (payload.allowLateSubmission !== undefined) body.set('allowLateSubmission', payload.allowLateSubmission ? 'Y' : 'N');
    if (payload.latePenalty !== undefined) body.set('latePenalty', String(Math.max(0, Math.min(100, Number(payload.latePenalty)))));
    if(payload.file) body.set('homework_file', payload.file);

    return requestJson<number>(`/tutor_lms/api/homework_modify.jsp`, {
      method: 'POST',
      body,
    });
  },

  async deleteHomework(payload: { courseId: number; homeworkId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));

    return requestJson<number>(`/tutor_lms/api/homework_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHomeworkUsers(params: { courseId: number; homeworkId: number }) {
    const url = `/tutor_lms/api/homework_users.jsp${buildQuery({ course_id: params.courseId, homework_id: params.homeworkId })}`;
    return requestJson<TutorHomeworkUserRow[]>(url);
  },

  // 왜: 피드백 관리 화면에서 학생 제출물(제목/내용/첨부파일)을 모달로 확인해야 합니다.
  async getHomeworkSubmissionDetail(params: { courseId: number; homeworkId: number; courseUserId: number }) {
    const url = `/tutor_lms/api/homework_user_submission.jsp${buildQuery({
      course_id: params.courseId,
      homework_id: params.homeworkId,
      course_user_id: params.courseUserId,
    })}`;
    return requestJson<TutorHomeworkSubmissionDetail>(url);
  },

  async updateHomeworkFeedback(payload: {
    courseId: number;
    homeworkId: number;
    courseUserId: number;
    markingScore: number;
    feedback: string;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    body.set('course_user_id', String(payload.courseUserId));
    body.set('marking_score', String(payload.markingScore));
    body.set('feedback', payload.feedback);

    return requestJson<number>(`/tutor_lms/api/homework_feedback_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 교수자 피드백 첨부파일(첨삭본)을 학생별로 조회/업로드/삭제할 수 있어야 합니다.
  async getHomeworkFeedbackFiles(params: { courseId: number; homeworkId: number; courseUserId: number }) {
    const url = `/tutor_lms/api/homework_feedback_file_list.jsp${buildQuery({
      course_id: params.courseId,
      homework_id: params.homeworkId,
      course_user_id: params.courseUserId,
    })}`;
    return requestJson<TutorHomeworkFeedbackFileRow[]>(url);
  },

  async uploadHomeworkFeedbackFile(payload: {
    courseId: number;
    homeworkId: number;
    courseUserId: number;
    file: File;
  }) {
    const body = new FormData();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    body.set('course_user_id', String(payload.courseUserId));
    body.set('feedback_file', payload.file);

    return requestJson<TutorHomeworkFeedbackFileRow>(`/tutor_lms/api/homework_feedback_file_upload.jsp`, {
      method: 'POST',
      body,
    });
  },

  async deleteHomeworkFeedbackFile(payload: {
    courseId: number;
    homeworkId: number;
    courseUserId: number;
    fileId: number;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    body.set('course_user_id', String(payload.courseUserId));
    body.set('file_id', String(payload.fileId));

    return requestJson<number>(`/tutor_lms/api/homework_feedback_file_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 과제 유사도 분석은 "실행(run)"과 "목록 조회(list)"를 분리해 제공됩니다.
  async runHomeworkSimilarity(payload: { courseId: number; homeworkId: number; thresholdScore?: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    if (payload.thresholdScore !== undefined) body.set('threshold_score', String(payload.thresholdScore));

    return requestJson<{
      run_id?: number;
      pair_total?: number;
      pair_saved?: number;
      threshold_score?: number;
      message?: string;
    }>(`/tutor_lms/api/homework_similarity_run.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHomeworkSimilarityList(params: {
    courseId: number;
    homeworkId: number;
    page?: number;
    pageSize?: number;
    minScore?: number;
  }) {
    const url = `/tutor_lms/api/homework_similarity_list.jsp${buildQuery({
      course_id: params.courseId,
      homework_id: params.homeworkId,
      page: params.page,
      page_size: params.pageSize,
      min_score: params.minScore,
    })}`;
    return requestJson<TutorHomeworkSimilarityRow[]>(url);
  },

  // 왜: 제출 취소는 피드백 저장과 다르게 제출/첨부 정리를 포함합니다.
  async cancelHomeworkSubmit(payload: { courseId: number; homeworkId: number; courseUserId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    body.set('course_user_id', String(payload.courseUserId));

    return requestJson<number>(`/tutor_lms/api/homework_submit_cancel.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async appendHomeworkTask(payload: { courseId: number; homeworkId: number; courseUserId: number; task: string }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('homework_id', String(payload.homeworkId));
    body.set('course_user_id', String(payload.courseUserId));
    body.set('task', payload.task);

    return requestJson<number>(`/tutor_lms/api/homework_task_append.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 학생이 제출한 추가 과제 내용을 확인하고 교수자가 "확인(평가완료)" 처리합니다.
  async confirmHomeworkTask(payload: { courseId: number; taskId: number; feedback: string }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('task_id', String(payload.taskId));
    body.set('feedback', payload.feedback);

    return requestJson<number>(`/tutor_lms/api/homework_task_confirm.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜: 피드백 관리에서 학생별 추가 과제 목록(재제출 현황 포함)을 조회합니다.
  async getHomeworkTasks(params: { courseId: number; homeworkId: number; courseUserId: number }) {
    const url = `/tutor_lms/api/homework_task_list.jsp${buildQuery({
      course_id: params.courseId,
      homework_id: params.homeworkId,
      course_user_id: params.courseUserId,
    })}`;
    return requestJson<any[]>(url);
  },

  // =========================
  // 마감 운영: 자료
  // =========================
  async getMaterials(params: { courseId: number }) {
    const url = `/tutor_lms/api/materials_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorMaterialRow[]>(url);
  },

  async uploadMaterial(payload: { courseId: number; title: string; content?: string; link?: string; file?: File | null }) {
    const body = new FormData();
    body.set('course_id', String(payload.courseId));
    body.set('title', payload.title);
    body.set('content', payload.content ?? '');
    body.set('library_link', payload.link ?? '');
    if (payload.file) body.set('library_file', payload.file);

    // 왜: 파일 업로드는 multipart/form-data로 전송되는데, 일부 JSP는 request.getParameter로는 값을 못 읽을 수 있습니다.
    //     그래서 course_id를 "쿼리스트링 + multipart 바디" 두 군데에 같이 실어 보내 서버 호환성을 높입니다.
    const url = `/tutor_lms/api/materials_upload.jsp${buildQuery({ course_id: payload.courseId })}`;
    return requestJson<number>(url, {
      method: 'POST',
      body,
    });
  },

  async deleteMaterial(payload: { courseId: number; libraryId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('library_id', String(payload.libraryId));

    return requestJson<number>(`/tutor_lms/api/materials_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // =========================
  // 마감 운영: Q&A
  // =========================
  async getQnas(params: { courseId: number; keyword?: string }) {
    const url = `/tutor_lms/api/qna_list.jsp${buildQuery({ course_id: params.courseId, s_keyword: params.keyword })}`;
    return requestJson<TutorQnaRow[]>(url);
  },

  async getQnaDetail(params: { courseId: number; postId: number }) {
    const url = `/tutor_lms/api/qna_view.jsp${buildQuery({ course_id: params.courseId, post_id: params.postId })}`;
    return requestJson<TutorQnaDetail>(url);
  },

  async answerQna(payload: { courseId: number; postId: number; content: string }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('post_id', String(payload.postId));
    body.set('content', payload.content);

    return requestJson<number>(`/tutor_lms/api/qna_answer.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getFaqNotices(params: { courseId: number; keyword?: string }) {
    const url = `/tutor_lms/api/qna_faq_notice_list.jsp${buildQuery({
      course_id: params.courseId,
      s_keyword: params.keyword,
    })}`;
    return requestJson<TutorFaqNoticeRow[]>(url);
  },

  async saveFaqNotice(payload: {
    courseId: number;
    subject: string;
    content: string;
    faqId?: number;
    displayYn?: 'Y' | 'N';
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('subject', payload.subject);
    body.set('content', payload.content);
    if (payload.faqId) body.set('faq_id', String(payload.faqId));
    body.set('display_yn', payload.displayYn ?? 'Y');

    return requestJson<number>(`/tutor_lms/api/qna_faq_notice_save.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteFaqNotice(payload: { courseId: number; faqId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('faq_id', String(payload.faqId));

    return requestJson<number>(`/tutor_lms/api/qna_faq_notice_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getHomeworkFeedbackTemplates(params: { courseId: number; managerId?: number; limit?: number }) {
    const url = `/tutor_lms/api/homework_feedback_template_list.jsp${buildQuery({
      course_id: params.courseId,
      manager_id: params.managerId,
      limit: params.limit,
    })}`;
    return requestJson<TutorHomeworkFeedbackTemplateRow[]>(url);
  },

  async saveHomeworkFeedbackTemplate(payload: {
    courseId: number;
    id?: number;
    managerId?: number;
    sort?: number;
    content: string;
  }) {
    const body = new URLSearchParams();
    if (payload.id) body.set('id', String(payload.id));
    body.set('course_id', String(payload.courseId));
    if (payload.managerId) body.set('manager_id', String(payload.managerId));
    body.set('sort', String(Math.max(0, payload.sort ?? 0)));
    body.set('content', payload.content);

    return requestJson<number>(`/tutor_lms/api/homework_feedback_template_save.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteHomeworkFeedbackTemplate(payload: { id: number; courseId: number; managerId?: number }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));
    body.set('course_id', String(payload.courseId));
    if (payload.managerId) body.set('manager_id', String(payload.managerId));

    return requestJson<number>(`/tutor_lms/api/homework_feedback_template_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getSurveys(params: { courseId: number }) {
    const url = `/tutor_lms/api/survey_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorSurveyRow[]>(url);
  },

  async createSurvey(payload: {
    courseId: number;
    surveyName: string;
    content?: string;
    questionType: '1' | 'M' | '2' | '3';
    question: string;
    questionItems?: string[];
    anonymousYn?: 'Y' | 'N';
    startDate?: string;
    endDate?: string;
    chapter?: number;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('survey_nm', payload.surveyName);
    body.set('content', payload.content ?? '');
    body.set('question_type', payload.questionType);
    body.set('question', payload.question);
    if (payload.questionItems && payload.questionItems.length > 0) {
      body.set('question_items', payload.questionItems.filter(Boolean).join('||'));
    }
    if (payload.anonymousYn) body.set('anonymous_yn', payload.anonymousYn);
    if (payload.startDate) body.set('start_date', payload.startDate);
    if (payload.endDate) body.set('end_date', payload.endDate);
    if (payload.chapter !== undefined) body.set('chapter', String(payload.chapter));

    return requestJson<number>(`/tutor_lms/api/survey_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateSurvey(payload: {
    courseId: number;
    surveyId: number;
    surveyName: string;
    content?: string;
    anonymousYn?: 'Y' | 'N';
    startDate?: string;
    endDate?: string;
    chapter?: number;
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('survey_id', String(payload.surveyId));
    body.set('survey_nm', payload.surveyName);
    body.set('content', payload.content ?? '');
    if (payload.anonymousYn) body.set('anonymous_yn', payload.anonymousYn);
    if (payload.startDate) body.set('start_date', payload.startDate);
    if (payload.endDate) body.set('end_date', payload.endDate);
    if (payload.chapter !== undefined) body.set('chapter', String(payload.chapter));

    return requestJson<number>(`/tutor_lms/api/survey_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteSurvey(payload: { courseId: number; surveyId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('survey_id', String(payload.surveyId));

    return requestJson<number>(`/tutor_lms/api/survey_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getSurveyResult(params: { courseId: number; surveyId: number; questionId?: number }) {
    const url = `/tutor_lms/api/survey_result.jsp${buildQuery({
      course_id: params.courseId,
      survey_id: params.surveyId,
      question_id: params.questionId,
    })}`;
    return requestJson<TutorSurveyResultQuestionRow[]>(url);
  },

  // =========================
  // 마감 운영: 성적/수료/증명서
  // =========================
  async getGrades(params: { courseId: number }) {
    const url = `/tutor_lms/api/grades_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorGradeRow[]>(url);
  },

  async recalcGrades(payload: { courseId: number; courseUserId?: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    if (payload.courseUserId) body.set('course_user_id', String(payload.courseUserId));

    return requestJson<number>(`/tutor_lms/api/grades_recalc.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getGradesDistribution(params: { courseId: number }) {
    const url = `/tutor_lms/api/grades_distribution.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorScoreDistributionRow[]>(url);
  },

  async getHaksaGradeDistribution(params: HaksaCourseKey) {
    const url = `/tutor_lms/api/haksa_grade_distribution.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
    return requestJson<TutorScoreDistributionRow[]>(url);
  },

  getHaksaGradeExportUrl(params: HaksaCourseKey) {
    return `/tutor_lms/api/haksa_grade_export.jsp${buildQuery({
      course_code: params.courseCode,
      open_year: params.openYear,
      open_term: params.openTerm,
      bunban_code: params.bunbanCode,
      group_code: params.groupCode,
    })}`;
  },

  async getAttendanceCourseSummary(params: { keyword?: string; year?: string; tutorId?: number } = {}) {
    const url = `/tutor_lms/api/attendance_course_summary.jsp${buildQuery({
      s_keyword: params.keyword,
      year: params.year,
      tutor_id: params.tutorId,
    })}`;
    return requestJson<TutorAttendanceCourseSummaryRow[]>(url);
  },

  async getAttendanceWeekLessons(params: { courseId: number }) {
    const url = `/tutor_lms/api/attendance_week_lessons.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorAttendanceLessonRow[]>(url);
  },

  async getAttendanceStudentMatrix(params: { courseId: number; sectionId?: number }) {
    const url = `/tutor_lms/api/attendance_student_matrix.jsp${buildQuery({
      course_id: params.courseId,
      section_id: params.sectionId,
    })}`;
    return requestJson<TutorAttendanceMatrixRow[]>(url);
  },

  async batchUpdateAttendance(payload: {
    courseId: number;
    lessonIds: number[];
    attendStatuses: Array<'Y' | 'N'>;
    courseUserIds: number[];
  }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('lesson_ids', payload.lessonIds.join(','));
    body.set('attend_statuses', payload.attendStatuses.join(','));
    body.set('course_user_ids', payload.courseUserIds.join(','));

    return requestJson<number>(`/tutor_lms/api/attendance_batch_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async applyAttendanceAbsence(payload: { courseId: number }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));

    return requestJson<number>(`/tutor_lms/api/attendance_absence_apply.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getCourseChatRooms(params: { courseId: number; keyword?: string }) {
    const url = `/tutor_lms/api/course_chat.jsp${buildQuery({
      mode: 'rooms',
      course_id: params.courseId,
      s_keyword: params.keyword,
    })}`;
    return requestJson<TutorCourseChatRoomRow[]>(url);
  },

  async getCourseChatMessages(params: { courseId: number; threadId: number }) {
    const url = `/tutor_lms/api/course_chat.jsp${buildQuery({
      mode: 'messages',
      course_id: params.courseId,
      thread_id: params.threadId,
    })}`;
    return requestJson<TutorCourseChatMessageRow[]>(url);
  },

  async sendCourseChatMessage(payload: { courseId: number; threadId: number; content: string }) {
    const body = new URLSearchParams();
    body.set('mode', 'send');
    body.set('course_id', String(payload.courseId));
    body.set('thread_id', String(payload.threadId));
    body.set('content', payload.content);

    return requestJson<number>(`/tutor_lms/api/course_chat.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getCompletions(params: { courseId: number }) {
    const url = `/tutor_lms/api/completion_list.jsp${buildQuery({ course_id: params.courseId })}`;
    return requestJson<TutorCompletionRow[]>(url);
  },

  async updateCompletion(payload: { courseId: number; action: 'complete_y' | 'complete_n' | 'close_y' | 'close_n'; courseUserIds: number[] }) {
    const body = new URLSearchParams();
    body.set('course_id', String(payload.courseId));
    body.set('action', payload.action);
    body.set('course_user_ids', payload.courseUserIds.join(','));

    return requestJson<number>(`/tutor_lms/api/completion_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async getCertificateTemplates(params: { templateType?: 'C' | 'P' } = {}) {
    const url = `/tutor_lms/api/certificate_templates.jsp${buildQuery({ template_type: params.templateType })}`;
    return requestJson<TutorCertificateTemplateRow[]>(url);
  },

  async issueCertificate(payload: { courseUserId: number; type: 'C' | 'P' }) {
    const url = `/tutor_lms/api/certificate_issue.jsp${buildQuery({ course_user_id: payload.courseUserId, type: payload.type })}`;
    return requestJson<string>(url);
  },

  // =========================
  // 문제 카테고리 (교수자용)
  // =========================
  async getQuestionCategories() {
    const url = `/tutor_lms/api/question_category_list.jsp`;
    return requestJson<TutorQuestionCategoryRow[]>(url);
  },

  async createQuestionCategory(payload: { categoryName: string; parentId?: number }) {
    const body = new URLSearchParams();
    body.set('category_nm', payload.categoryName);
    if (payload.parentId !== undefined) body.set('parent_id', String(payload.parentId));

    return requestJson<number>(`/tutor_lms/api/question_category_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateQuestionCategory(payload: { id: number; categoryName: string; parentId?: number }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));
    body.set('category_nm', payload.categoryName);
    if (payload.parentId !== undefined) body.set('parent_id', String(payload.parentId));

    return requestJson<number>(`/tutor_lms/api/question_category_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteQuestionCategory(payload: { id: number }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));

    return requestJson<number>(`/tutor_lms/api/question_category_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // =========================
  // 문제은행 (교수자용)
  // =========================
  async getQuestionBankList(params: {
    categoryId?: number;
    questionType?: number;
    keyword?: string;
    mineOnly?: boolean;
    page?: number;
    limit?: number;
  } = {}) {
    const url = `/tutor_lms/api/question_bank_list.jsp${buildQuery({
      category_id: params.categoryId,
      question_type: params.questionType,
      s_keyword: params.keyword,
      mine_only: params.mineOnly ? 'Y' : undefined,
      page: params.page,
      limit: params.limit,
    })}`;
    return requestJson<TutorQuestionBankRow[]>(url);
  },

  async createQuestion(payload: {
    categoryId?: number;
    questionType: number; // 1=단일선택, 2=다중선택, 3=단답형, 4=서술형
    question: string;
    questionText?: string;
    grade?: number;
    answer: string;
    description?: string;
    points?: number;
    items?: string[]; // 객관식 보기 (최대 5개)
    openYn?: 'Y' | 'N';
  }) {
    const body = new URLSearchParams();
    if (payload.categoryId) body.set('category_id', String(payload.categoryId));
    body.set('question_type', String(payload.questionType));
    body.set('question', payload.question);
    if (payload.questionText) body.set('question_text', payload.questionText);
    if (payload.grade) body.set('grade', String(payload.grade));
    body.set('answer', payload.answer);
    if (payload.description) body.set('description', payload.description);
    if (payload.points) body.set('points', String(payload.points));
    if (payload.openYn) body.set('open_yn', payload.openYn);
    // 객관식 보기 처리
    if (payload.items) {
      payload.items.forEach((item, idx) => {
        body.set(`item${idx + 1}`, item);
      });
    }

    return requestJson<number>(`/tutor_lms/api/question_bank_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateQuestion(payload: {
    id: number;
    categoryId?: number;
    questionType?: number;
    question?: string;
    questionText?: string;
    grade?: number;
    answer?: string;
    description?: string;
    points?: number;
    items?: string[];
    openYn?: 'Y' | 'N';
  }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));
    if (payload.categoryId !== undefined) body.set('category_id', String(payload.categoryId));
    if (payload.questionType !== undefined) body.set('question_type', String(payload.questionType));
    if (payload.question !== undefined) body.set('question', payload.question);
    if (payload.questionText !== undefined) body.set('question_text', payload.questionText);
    if (payload.grade !== undefined) body.set('grade', String(payload.grade));
    if (payload.answer !== undefined) body.set('answer', payload.answer);
    if (payload.description !== undefined) body.set('description', payload.description);
    if (payload.points !== undefined) body.set('points', String(payload.points));
    if (payload.openYn !== undefined) body.set('open_yn', payload.openYn);
    if (payload.items) {
      payload.items.forEach((item, idx) => {
        body.set(`item${idx + 1}`, item);
      });
    }

    return requestJson<number>(`/tutor_lms/api/question_bank_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteQuestion(payload: { id: number }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));

    return requestJson<number>(`/tutor_lms/api/question_bank_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // =========================
  // 시험 템플릿 (교수자용)
  // =========================
  async getExamTemplates(params: { page?: number; limit?: number } = {}) {
    const url = `/tutor_lms/api/exam_template_list.jsp${buildQuery({
      page: params.page,
      limit: params.limit,
    })}`;
    return requestJson<TutorExamTemplateRow[]>(url);
  },

  async createExamTemplate(payload: {
    examName: string;
    examTime?: number;
    questionCnt?: number;
    shuffleYn?: boolean;
    showResultYn?: boolean;
    passingScore?: number;
    questionIds?: string[];
    content?: string;
  }) {
    const body = new URLSearchParams();
    body.set('exam_nm', payload.examName);
    if (payload.examTime) body.set('exam_time', String(payload.examTime));
    if (payload.questionCnt !== undefined) body.set('question_cnt', String(payload.questionCnt));
    if (payload.shuffleYn !== undefined) body.set('shuffle_yn', payload.shuffleYn ? 'Y' : 'N');
    if (payload.showResultYn !== undefined) body.set('show_result_yn', payload.showResultYn ? 'Y' : 'N');
    if (payload.passingScore !== undefined) body.set('passing_score', String(payload.passingScore));
    if (payload.questionIds) body.set('question_ids', payload.questionIds.join(','));
    if (payload.content) body.set('content', payload.content);

    return requestJson<number>(`/tutor_lms/api/exam_template_insert.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateExamTemplate(payload: {
    id: number;
    examName?: string;
    examTime?: number;
    questionCnt?: number;
    shuffleYn?: boolean;
    showResultYn?: boolean;
    passingScore?: number;
    questionIds?: string[];
    content?: string;
  }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));
    if (payload.examName) body.set('exam_nm', payload.examName);
    if (payload.examTime) body.set('exam_time', String(payload.examTime));
    if (payload.questionCnt !== undefined) body.set('question_cnt', String(payload.questionCnt));
    if (payload.shuffleYn !== undefined) body.set('shuffle_yn', payload.shuffleYn ? 'Y' : 'N');
    if (payload.showResultYn !== undefined) body.set('show_result_yn', payload.showResultYn ? 'Y' : 'N');
    if (payload.passingScore !== undefined) body.set('passing_score', String(payload.passingScore));
    if (payload.questionIds) body.set('question_ids', payload.questionIds.join(','));
    if (payload.content !== undefined) body.set('content', payload.content);

    return requestJson<number>(`/tutor_lms/api/exam_template_modify.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async deleteExamTemplate(payload: { id: number }) {
    const body = new URLSearchParams();
    body.set('id', String(payload.id));

    return requestJson<number>(`/tutor_lms/api/exam_template_delete.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  // 왜 필요한가:
  // - Kollus에 동영상을 업로드하려면 먼저 업로드 URL을 생성해야 합니다.
  // - 서버 프록시를 통해 API 토큰을 안전하게 관리합니다.
  async getKollusUploadUrl(payload: {
    title?: string;
    categoryKey?: string;
    expireTime?: number;
  }) {
    const body = new URLSearchParams();
    if (payload.title) body.set('title', payload.title);
    if (payload.categoryKey) body.set('category_key', payload.categoryKey);
    if (payload.expireTime) body.set('expire_time', String(payload.expireTime));

    return requestJson<{ upload_url: string; upload_key?: string; expired_at?: number }>(
      `/tutor_lms/api/kollus_upload_url.jsp`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      }
    );
  },

  // 왜 필요한가:
  // - 업로드(upload_url로 파일 전송)가 끝났더라도, Kollus 채널에 attach가 되어야 목록에서 안정적으로 보입니다.
  // - 채널키는 서버에서 고정하고, 프론트에서는 upload_key만 넘겨서 안전하게 처리합니다.
  async attachKollusToChannel(payload: { uploadKey: string }) {
    const body = new URLSearchParams();
    body.set('upload_key', payload.uploadKey);

    return requestJson<unknown>(`/tutor_lms/api/kollus_attach_channel.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },

  async updateKollusTitle(payload: {
    mediaContentKey: string;
    title: string;
    snapshotUrl?: string;
    categoryKey?: string;
    categoryName?: string;
    originalFileName?: string;
    totalTime?: number;
    contentWidth?: number;
    contentHeight?: number;
  }) {
    const body = new URLSearchParams();
    body.set('media_content_key', payload.mediaContentKey);
    body.set('title', payload.title);
    if (payload.snapshotUrl) body.set('snapshot_url', payload.snapshotUrl);
    if (payload.categoryKey) body.set('category_key', payload.categoryKey);
    if (payload.categoryName) body.set('category_nm', payload.categoryName);
    if (payload.originalFileName) body.set('original_file_name', payload.originalFileName);
    if (payload.totalTime !== undefined) body.set('total_time', String(payload.totalTime));
    if (payload.contentWidth !== undefined) body.set('content_width', String(payload.contentWidth));
    if (payload.contentHeight !== undefined) body.set('content_height', String(payload.contentHeight));

    return requestJson<number>(`/tutor_lms/api/kollus_media_title_update.jsp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
  },
};
