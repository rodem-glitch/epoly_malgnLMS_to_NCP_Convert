// 목적:
// - 로컬에서는 되는데 클라우드(web.app)에서만 깨지는 API 응답을 "실제 호출"로 재현/진단합니다.
// - 비밀번호/쿠키 등 민감정보는 출력하지 않습니다(로그에는 URL/상태/응답 일부만).
//
// 사용:
//   node tmp_cloud_tutor_api_smoke.mjs
//   BASE=https://epoly-kopo.web.app node tmp_cloud_tutor_api_smoke.mjs
//   BASE=http://34.64.207.10 node tmp_cloud_tutor_api_smoke.mjs

const DEFAULT_BASE = "https://epoly-kopo.web.app";
const BASE = process.env.BASE || DEFAULT_BASE;

function mustAbsoluteUrl(pathOrUrl) {
  if (/^https?:\/\//i.test(pathOrUrl)) return pathOrUrl;
  if (!pathOrUrl.startsWith("/")) return `${BASE}/${pathOrUrl}`;
  return `${BASE}${pathOrUrl}`;
}

function joinCookieHeader(cookieMap) {
  return Object.entries(cookieMap)
    .map(([k, v]) => `${k}=${v}`)
    .join("; ");
}

function parseSetCookie(setCookieValue) {
  if (!setCookieValue || typeof setCookieValue !== "string") return null;
  const first = setCookieValue.split(";")[0] || "";
  const eq = first.indexOf("=");
  if (eq <= 0) return null;
  const name = first.slice(0, eq).trim();
  const value = first.slice(eq + 1);
  if (!name) return null;
  return { name, value };
}

async function fetchWithJar(url, init, jar) {
  const headers = new Headers(init?.headers || {});
  const cookieHeader = joinCookieHeader(jar);
  if (cookieHeader) headers.set("cookie", cookieHeader);
  // 왜: JSON 응답을 기대하는 호출은 명시해 디버깅을 쉽게 합니다.
  if (!headers.has("accept")) headers.set("accept", "application/json, text/html;q=0.9, */*;q=0.8");

  const res = await fetch(url, { ...init, headers, redirect: "manual" });

  const setCookies = res.headers.getSetCookie?.() || [];
  for (const raw of setCookies) {
    const parsed = parseSetCookie(raw);
    if (!parsed) continue;
    jar[parsed.name] = parsed.value;
  }

  return res;
}

function previewText(text, maxLen = 220) {
  const compact = String(text || "").replace(/\s+/g, " ").trim();
  if (compact.length <= maxLen) return compact;
  return `${compact.slice(0, maxLen)}…`;
}

function safeKeySummary(obj) {
  if (!obj || typeof obj !== "object") return [];
  return Object.keys(obj).slice(0, 20);
}

function extractLoginPreset(html) {
  const idMatch = html.match(/id="login_id"[^>]*value="([^"]+)"/i);
  const pwMatch = html.match(/id="login_passwd"[^>]*value="([^"]+)"/i);
  const returlMatch = html.match(/name="returl"[^>]*value="([^"]*)"/i);
  return {
    id: idMatch?.[1] || "",
    passwd: pwMatch?.[1] || "",
    returl: returlMatch?.[1] || "/tutor_lms/",
  };
}

async function expectJsonShape(label, res) {
  const contentType = res.headers.get("content-type") || "";
  const text = await res.text();
  const trimmed = text.trim();
  const looksJson = contentType.includes("application/json") || trimmed.startsWith("{") || trimmed.startsWith("[");

  let parsed = null;
  if (looksJson) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null;
    }
  }

  const info = {
    label,
    status: res.status,
    contentType,
    url: res.url,
    preview: previewText(trimmed),
  };

  if (!parsed) {
    console.log("[API]", JSON.stringify({ ...info, parsed: false }));
    return { ok: false, kind: "not_json", parsed: null, text: trimmed };
  }

  const rstCode = parsed?.rst_code;
  const rstType = typeof rstCode;
  const keys = safeKeySummary(parsed);

  console.log(
    "[API]",
    JSON.stringify({
      ...info,
      parsed: true,
      rst_code_type: rstType,
      rst_code: rstType === "string" ? rstCode : undefined,
      keys,
    }),
  );

  if (rstType !== "string") {
    return { ok: false, kind: "json_without_rst_code", parsed, text: trimmed };
  }

  return { ok: true, kind: "ok", parsed, text: trimmed };
}

async function main() {
  console.log(`[BASE] ${BASE}`);

  const jar = {};

  // 1) 로그인 페이지(신규 메인)에서 프리셋(테스트 계정)을 읽습니다.
  const loginPageUrl = mustAbsoluteUrl("/mypage/new_main/?login_required=Y&returl=%2Ftutor_lms%2F");
  const loginPageRes = await fetchWithJar(loginPageUrl, { method: "GET" }, jar);
  const loginPageHtml = await loginPageRes.text();
  const preset = extractLoginPreset(loginPageHtml);

  if (!preset.id || !preset.passwd) {
    throw new Error("로그인 프리셋을 찾지 못했습니다(로그인 모달 입력값).");
  }

  // 2) 로그인 POST
  const body = new URLSearchParams();
  body.set("id", preset.id);
  body.set("passwd", preset.passwd);
  body.set("returl", preset.returl || "/tutor_lms/");

  const loginRes = await fetchWithJar(mustAbsoluteUrl("/member/login.jsp"), {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }, jar);

  // 로그인 응답은 JS/HTML인 경우가 많아서, 여기서는 형태만 간단히 기록합니다.
  console.log("[LOGIN]", JSON.stringify({
    status: loginRes.status,
    contentType: loginRes.headers.get("content-type") || "",
    setCookieCount: (loginRes.headers.getSetCookie?.() || []).length,
  }));
  // 응답 본문 전체는 출력하지 않습니다.
  await loginRes.arrayBuffer().catch(() => {});

  // 3) 교수자 LMS API 스모크 테스트
  const tests = [
    { label: "dashboard", url: "/tutor_lms/api/dashboard.jsp" },
    { label: "course_years", url: "/tutor_lms/api/course_years.jsp" },
    { label: "courses_combined_prism", url: "/tutor_lms/api/course_list_combined.jsp?tab=prism&page=1&page_size=5&sort_order=desc" },
    { label: "courses_combined_haksa", url: "/tutor_lms/api/course_list_combined.jsp?tab=haksa&page=1&page_size=5&sort_order=desc" },
    { label: "assignment_manage", url: "/tutor_lms/api/homework_submissions.jsp?page=1&page_size=5" },
    { label: "qna_manage", url: "/tutor_lms/api/qna_manage_list.jsp?page=1&page_size=5" },
    { label: "program_list", url: "/tutor_lms/api/program_list.jsp?page=1&page_size=5" },
    { label: "tutor_list", url: "/tutor_lms/api/tutor_list.jsp" },
    { label: "course_categories", url: "/tutor_lms/api/course_categories.jsp" },
    { label: "question_categories", url: "/tutor_lms/api/question_category_list.jsp" },
    { label: "question_bank_list", url: "/tutor_lms/api/question_bank_list.jsp?page=1&page_size=5" },
    { label: "exam_templates", url: "/tutor_lms/api/exam_template_list.jsp?page=1&page_size=5" },
    { label: "certificate_templates_C", url: "/tutor_lms/api/certificate_templates.jsp?template_type=C" },
    { label: "certificate_templates_P", url: "/tutor_lms/api/certificate_templates.jsp?template_type=P" },
    { label: "kollus_list", url: "/tutor_lms/api/kollus_list.jsp?page=1&limit=5" },
    { label: "kollus_wishlist_list", url: "/tutor_lms/api/kollus_wishlist_list.jsp?page=1&limit=5" },
  ];

  for (const t of tests) {
    const res = await fetchWithJar(mustAbsoluteUrl(t.url), { method: "GET" }, jar);
    await expectJsonShape(t.label, res);
  }

  // 4) 과목관리 화면에서 자주 호출되는 API(과제/시험/수강생/성적/수료)를 실제 course_id로 재현합니다.
  const prismCoursesRes = await fetchWithJar(
    mustAbsoluteUrl("/tutor_lms/api/course_list_combined.jsp?tab=prism&page=1&page_size=20&sort_order=desc"),
    { method: "GET" },
    jar,
  );
  const prismCoursesShape = await expectJsonShape("courses_combined_prism_full", prismCoursesRes);
  const prismCourses = prismCoursesShape.ok ? (prismCoursesShape.parsed?.rst_data || []) : [];

  const pickCourseIds = [];
  for (const row of prismCourses) {
    if (typeof row?.id === "number") pickCourseIds.push(row.id);
    if (pickCourseIds.length >= 5) break;
  }

  for (const courseId of pickCourseIds) {
    const prefix = `course_${courseId}`;
    await expectJsonShape(
      `${prefix}_info`,
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/course_info_get.jsp?course_id=${encodeURIComponent(courseId)}&_t=${Date.now()}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      `${prefix}_students`,
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/course_students_list.jsp?course_id=${encodeURIComponent(courseId)}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      `${prefix}_progress`,
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/progress_summary.jsp?course_id=${encodeURIComponent(courseId)}`), { method: "GET" }, jar),
    );

    const homeworksRes = await fetchWithJar(
      mustAbsoluteUrl(`/tutor_lms/api/homework_list.jsp?course_id=${encodeURIComponent(courseId)}`),
      { method: "GET" },
      jar,
    );
    const homeworksShape = await expectJsonShape(`${prefix}_homeworks`, homeworksRes);
    const homeworks = homeworksShape.ok ? (homeworksShape.parsed?.rst_data || []) : [];
    const firstHomeworkId = homeworks.find((h) => typeof h?.homework_id === "number")?.homework_id;
    if (typeof firstHomeworkId === "number") {
      const usersRes = await fetchWithJar(
        mustAbsoluteUrl(`/tutor_lms/api/homework_users.jsp?course_id=${encodeURIComponent(courseId)}&homework_id=${encodeURIComponent(firstHomeworkId)}`),
        { method: "GET" },
        jar,
      );
      const usersShape = await expectJsonShape(`${prefix}_homework_users_${firstHomeworkId}`, usersRes);
      const users = usersShape.ok ? (usersShape.parsed?.rst_data || []) : [];
      const firstCourseUserId = users.find((u) => typeof u?.course_user_id === "number")?.course_user_id;
      if (typeof firstCourseUserId === "number") {
        await expectJsonShape(
          `${prefix}_homework_submission_${firstHomeworkId}_${firstCourseUserId}`,
          await fetchWithJar(
            mustAbsoluteUrl(
              `/tutor_lms/api/homework_user_submission.jsp?course_id=${encodeURIComponent(courseId)}&homework_id=${encodeURIComponent(firstHomeworkId)}&course_user_id=${encodeURIComponent(firstCourseUserId)}`,
            ),
            { method: "GET" },
            jar,
          ),
        );
      }
    }

    const examsRes = await fetchWithJar(
      mustAbsoluteUrl(`/tutor_lms/api/exam_list.jsp?course_id=${encodeURIComponent(courseId)}`),
      { method: "GET" },
      jar,
    );
    const examsShape = await expectJsonShape(`${prefix}_exams`, examsRes);
    const exams = examsShape.ok ? (examsShape.parsed?.rst_data || []) : [];
    const firstExamId = exams.find((e) => typeof e?.exam_id === "number")?.exam_id;
    if (typeof firstExamId === "number") {
      await expectJsonShape(
        `${prefix}_exam_users_${firstExamId}`,
        await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/exam_users.jsp?course_id=${encodeURIComponent(courseId)}&exam_id=${encodeURIComponent(firstExamId)}`), { method: "GET" }, jar),
      );
    }

    await expectJsonShape(
      `${prefix}_qna_list`,
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/qna_list.jsp?course_id=${encodeURIComponent(courseId)}&page=1&page_size=5`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      `${prefix}_grades`,
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/grades_list.jsp?course_id=${encodeURIComponent(courseId)}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      `${prefix}_completion`,
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/completion_list.jsp?course_id=${encodeURIComponent(courseId)}&page=1&page_size=20`), { method: "GET" }, jar),
    );
  }

  // 5) 학사(정규) 과목 관련 API(외부 연계/JSON 저장)도 한 번 호출해 형식이 깨지지 않는지 확인합니다.
  const haksaCoursesRes = await fetchWithJar(
    mustAbsoluteUrl("/tutor_lms/api/course_list_combined.jsp?tab=haksa&page=1&page_size=20&sort_order=desc"),
    { method: "GET" },
    jar,
  );
  const haksaCoursesShape = await expectJsonShape("courses_combined_haksa_full", haksaCoursesRes);
  const haksaCourses = haksaCoursesShape.ok ? (haksaCoursesShape.parsed?.rst_data || []) : [];
  const firstHaksa = haksaCourses.find((r) => r && typeof r?.haksa_course_code === "string" || typeof r?.haksa_course_code === "number" || typeof r?.course_code === "string");

  // course_list_combined(haksa) 결과는 환경/버전에 따라 키가 다를 수 있어, 가능한 키를 순서대로 매핑합니다.
  const haksaCourseCode = String(firstHaksa?.course_code || firstHaksa?.haksa_course_code || "").trim();
  const haksaOpenYear = String(firstHaksa?.open_year || firstHaksa?.haksa_open_year || "").trim();
  const haksaOpenTerm = String(firstHaksa?.open_term || firstHaksa?.haksa_open_term || "").trim();
  const haksaBunbanCode = String(firstHaksa?.bunban_code || firstHaksa?.haksa_bunban_code || "").trim();
  const haksaGroupCode = String(firstHaksa?.group_code || firstHaksa?.haksa_group_code || "").trim();

  if (haksaCourseCode && haksaOpenYear && haksaOpenTerm && haksaBunbanCode && haksaGroupCode) {
    const keyQs = new URLSearchParams({
      course_code: haksaCourseCode,
      open_year: haksaOpenYear,
      open_term: haksaOpenTerm,
      bunban_code: haksaBunbanCode,
      group_code: haksaGroupCode,
    }).toString();

    await expectJsonShape(
      "haksa_resolve",
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/haksa_resolve.jsp?${keyQs}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      "haksa_curriculum_get",
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/haksa_curriculum_get.jsp?${keyQs}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      "haksa_exam_get",
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/haksa_exam_get.jsp?${keyQs}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      "haksa_grade_list",
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/haksa_grade_list.jsp?${keyQs}`), { method: "GET" }, jar),
    );
    await expectJsonShape(
      "haksa_course_eval_get",
      await fetchWithJar(mustAbsoluteUrl(`/tutor_lms/api/haksa_course_eval_get.jsp?${keyQs}`), { method: "GET" }, jar),
    );
  } else {
    console.log(
      "[SKIP]",
      JSON.stringify({
        reason: "haksa_key_incomplete",
        hasCourseCode: Boolean(haksaCourseCode),
        hasOpenYear: Boolean(haksaOpenYear),
        hasOpenTerm: Boolean(haksaOpenTerm),
        hasBunbanCode: Boolean(haksaBunbanCode),
        hasGroupCode: Boolean(haksaGroupCode),
      }),
    );
  }
}

await main();
