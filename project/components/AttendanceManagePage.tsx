import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, ChevronDown, Download, Save, Search } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';
import { downloadCsv } from '../utils/csv';

// 왜: 교수자가 담당하는 모든 과목의 출결을 한눈에 관리하는 페이지입니다.

type AttendanceStatus = 'present' | 'late' | 'absent' | 'excused';

interface CourseItem {
  courseId: string;           // 왜: 학사 과목은 복합키(H_CODE_YEAR_...), 비정규는 숫자 ID
  mappedCourseId?: number;   // 왜: 학사 과목이 매핑된 LMS 과정 ID (수강생 조회용)
  courseName: string;
  sourceType: 'haksa' | 'prism';
  // 왜: 학사 과목 메타 (나중에 백엔드에서 resolve 시 필요)
  haksaCourseCode?: string;
  haksaOpenYear?: string;
  haksaOpenTerm?: string;
  haksaBunbanCode?: string;
  haksaGroupCode?: string;
}

interface StudentAttendance {
  courseUserId: number;
  studentId: string;
  name: string;
  weeks: Record<number, AttendanceStatus>;
}

const STATUS_LABELS: Record<AttendanceStatus, string> = {
  present: '출석',
  late: '지각',
  absent: '결석',
  excused: '공결',
};

const STATUS_COLORS: Record<AttendanceStatus, string> = {
  present: 'bg-green-100 text-green-800',
  late: 'bg-yellow-100 text-yellow-800',
  absent: 'bg-red-100 text-red-800',
  excused: 'bg-blue-100 text-blue-800',
};

const STATUS_CYCLE: AttendanceStatus[] = ['present', 'late', 'absent', 'excused'];

function toNum(val: unknown, fallback = 0): number {
  const n = Number(val);
  return Number.isFinite(n) ? n : fallback;
}

export function AttendanceManagePage() {
  // === 과목 목록 ===
  const [courses, setCourses] = useState<CourseItem[]>([]);
  const [loadingCourses, setLoadingCourses] = useState(true);
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);

  // === 선택된 과목의 출석 데이터 ===
  const [students, setStudents] = useState<StudentAttendance[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  const [weekCount, setWeekCount] = useState(15);
  const [absenceLimit, setAbsenceLimit] = useState(4);
  const [dirty, setDirty] = useState(false);

  // === 학사 복합키 생성 (MyCoursesList와 동일) ===
  const buildHaksaCourseId = (row: any): string => {
    const courseCode = String(row?.haksa_course_code ?? '').trim();
    const openYear = String(row?.haksa_open_year ?? '').trim();
    const openTerm = String(row?.haksa_open_term ?? '').trim();
    const bunbanCode = String(row?.haksa_bunban_code ?? '').trim();
    const groupCode = String(row?.haksa_group_code ?? '').trim();
    const parts = [courseCode, openYear, openTerm, bunbanCode, groupCode].filter(Boolean);
    if (parts.length === 5) return `H_${parts.join('_')}`;
    const fallback = String(row?.course_id_conv ?? row?.course_cd ?? row?.id ?? '').trim();
    return `H_${courseCode || 'UNKNOWN'}_${bunbanCode || '0'}_${fallback || '0'}`;
  };

  // === 과목 목록 불러오기 ===
  useEffect(() => {
    let cancelled = false;
    const fetchCourses = async () => {
      setLoadingCourses(true);
      try {
        // 왜: 학사(정규) + 비정규 과목을 병렬로 불러옵니다.
        const [resH, resP] = await Promise.all([
          tutorLmsApi.getMyCoursesCombined({ tab: 'haksa', pageSize: 200 }),
          tutorLmsApi.getMyCoursesCombined({ tab: 'prism', pageSize: 200 }),
        ]);
        if (cancelled) return;

        const haksaList: CourseItem[] = (resH.rst_code === '0000' && Array.isArray(resH.rst_data))
          ? resH.rst_data.map((c: any) => {
              const mapped = c.mapped_course_id ? Number(c.mapped_course_id) : undefined;
              return {
                courseId: c.id ? String(c.id) : buildHaksaCourseId(c),
                mappedCourseId: mapped && Number.isFinite(mapped) ? mapped : undefined,
                courseName: String(c.course_nm_conv || c.subject_nm_conv || c.course_nm || '-'),
                sourceType: 'haksa' as const,
                haksaCourseCode: c.haksa_course_code,
                haksaOpenYear: c.haksa_open_year,
                haksaOpenTerm: c.haksa_open_term,
                haksaBunbanCode: c.haksa_bunban_code,
                haksaGroupCode: c.haksa_group_code,
              };
            }).filter((c: CourseItem) => c.courseId)
          : [];

        const prismList: CourseItem[] = (resP.rst_code === '0000' && Array.isArray(resP.rst_data))
          ? resP.rst_data.map((c: any) => ({
              courseId: String(c.id ?? c.course_id ?? ''),
              courseName: String(c.course_nm_conv || c.subject_nm_conv || c.course_nm || '-'),
              sourceType: 'prism' as const,
            })).filter((c: CourseItem) => c.courseId)
          : [];

        // 왜: 중복 제거 (courseId 기준)
        const seen = new Set<string>();
        const merged: CourseItem[] = [];
        for (const c of [...haksaList, ...prismList]) {
          if (!seen.has(c.courseId)) {
            seen.add(c.courseId);
            merged.push(c);
          }
        }
        setCourses(merged);
      } catch {
        // 왜: 과목 목록 로드 실패 시 빈 배열로 유지합니다.
      } finally {
        if (!cancelled) setLoadingCourses(false);
      }
    };
    void fetchCourses();
    return () => { cancelled = true; };
  }, []);

  // === 과목 선택 시 수강생(출석 데이터) 불러오기 ===
  const fetchAttendance = useCallback(async (course: CourseItem) => {
    setLoading(true);
    setErrorMessage(null);
    setStudents([]);
    setDirty(false);
    try {
      // 왜: 학사 과목은 mappedCourseId가 있어야 수강생 조회 가능.
      //     없으면 학사 수강생 API로 대체 시도.
      const numericId = course.mappedCourseId ?? Number(course.courseId);
      let studentList: { courseUserId: number; studentId: string; name: string }[] = [];

      if (Number.isFinite(numericId) && numericId > 0) {
        const res = await tutorLmsApi.getCourseStudents({ courseId: numericId });
        if (res.rst_code === '0000' && Array.isArray(res.rst_data)) {
          studentList = res.rst_data.map((s: any) => ({
            courseUserId: toNum(s.course_user_id ?? s.COURSE_USER_ID),
            studentId: String(s.student_id ?? s.STUDENT_ID ?? s.login_id ?? s.LOGIN_ID ?? ''),
            name: String(s.user_nm ?? s.USER_NM ?? s.name ?? s.NAME ?? ''),
          }));
        }
      } else if (course.sourceType === 'haksa' && course.haksaCourseCode) {
        // 왜: mappedCourseId가 없는 학사 과목은 학사 수강생 API를 사용
        const res = await tutorLmsApi.getHaksaCourseStudents({
          courseCode: course.haksaCourseCode!,
          openYear: course.haksaOpenYear,
          openTerm: course.haksaOpenTerm,
          bunbanCode: course.haksaBunbanCode,
          groupCode: course.haksaGroupCode,
        });
        if (res.rst_code === '0000' && Array.isArray(res.rst_data)) {
          studentList = res.rst_data.map((s: any) => ({
            courseUserId: toNum(s.course_user_id ?? s.COURSE_USER_ID ?? s.id ?? 0),
            studentId: String(s.student_id ?? s.STUDENT_ID ?? s.login_id ?? s.LOGIN_ID ?? ''),
            name: String(s.user_nm ?? s.USER_NM ?? s.name ?? s.NAME ?? ''),
          }));
        }
      }

      // TODO: 실제 출석 데이터 API 호출. 현재는 전원 '출석'으로 초기화.
      const initial: StudentAttendance[] = studentList.map((s) => {
        const weeks: Record<number, AttendanceStatus> = {};
        for (let w = 1; w <= weekCount; w++) {
          weeks[w] = 'present';
        }
        return { ...s, weeks };
      });
      setStudents(initial);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '출석 데이터를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }, [weekCount]);

  const handleSelectCourse = (courseId: string) => {
    if (dirty) {
      const ok = confirm('저장하지 않은 변경사항이 있습니다. 다른 과목으로 이동하시겠습니까?');
      if (!ok) return;
    }
    setSelectedCourseId(courseId);
    setKeyword('');
    const course = courses.find((c) => c.courseId === courseId);
    if (course) void fetchAttendance(course);
  };

  // === 출석 상태 변경 ===
  const toggleStatus = (studentIdx: number, week: number) => {
    setStudents((prev) => {
      const next = [...prev];
      const student = { ...next[studentIdx] };
      const current = student.weeks[week] || 'present';
      const ci = STATUS_CYCLE.indexOf(current);
      student.weeks = { ...student.weeks, [week]: STATUS_CYCLE[(ci + 1) % STATUS_CYCLE.length] };
      next[studentIdx] = student;
      return next;
    });
    setDirty(true);
  };

  // === 결석 횟수 ===
  const getAbsenceCount = (student: StudentAttendance): number =>
    Object.values(student.weeks).filter((s) => s === 'absent').length;

  // === 결석 초과 학생 ===
  const autoFailStudents = useMemo(
    () => students.filter((s) => getAbsenceCount(s) >= absenceLimit),
    [students, absenceLimit]
  );

  // === 검색 ===
  const filteredStudents = useMemo(() => {
    if (!keyword.trim()) return students;
    const kw = keyword.trim().toLowerCase();
    return students.filter(
      (s) => s.name.toLowerCase().includes(kw) || s.studentId.toLowerCase().includes(kw)
    );
  }, [students, keyword]);

  const selectedCourse = courses.find((c) => c.courseId === selectedCourseId);

  // === 저장 ===
  const handleSave = async () => {
    setSaving(true);
    setErrorMessage(null);
    try {
      // TODO: 백엔드 API 연동
      alert('출석 데이터가 저장되었습니다.\n(백엔드 연동 후 실제 저장됩니다.)');
      setDirty(false);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  // === CSV 다운로드 ===
  const handleDownloadCsv = () => {
    const ymd = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const filename = `attendance_${selectedCourse?.courseName ?? selectedCourseId}_${ymd}.csv`;
    const weekHeaders = Array.from({ length: weekCount }, (_, i) => `${i + 1}주차`);
    const headers = ['No', '이름', '학번', ...weekHeaders, '결석횟수', 'F처리'];
    const rows = students.map((s, i) => [
      i + 1,
      s.name,
      s.studentId,
      ...Array.from({ length: weekCount }, (_, w) => STATUS_LABELS[s.weeks[w + 1] || 'present']),
      getAbsenceCount(s),
      getAbsenceCount(s) >= absenceLimit ? 'Y' : 'N',
    ]);
    downloadCsv(filename, headers, rows);
  };

  // === 결석 F 일괄 처리 ===
  const handleAutoFail = () => {
    if (autoFailStudents.length === 0) {
      alert('결석 초과 학생이 없습니다.');
      return;
    }
    const ok = confirm(
      `결석 ${absenceLimit}회 이상인 학생 ${autoFailStudents.length}명을 F 처리하시겠습니까?\n\n` +
        autoFailStudents.map((s) => `• ${s.name} (${s.studentId}) — 결석 ${getAbsenceCount(s)}회`).join('\n')
    );
    if (!ok) return;
    // TODO: 백엔드 API
    alert(`${autoFailStudents.length}명 F 처리 완료.\n(백엔드 연동 후 실제 반영됩니다.)`);
  };

  return (
    <div className="space-y-5">
      {/* ===== 페이지 헤더 + 과목 드롭다운 (상단 고정) ===== */}
      <div>
        <div className="flex flex-wrap items-end gap-4">
          {/* 제목 */}
          <div className="mr-auto">
            <h1 className="text-2xl font-bold text-gray-900">출석 관리</h1>
            <p className="text-gray-500 text-sm mt-0.5">담당 과목별 학생들의 출결을 관리합니다.</p>
          </div>

          {/* 과목 드롭다운 */}
          <div className="w-80">
            <label className="block text-xs font-medium text-gray-500 mb-1">담당 과목 선택</label>
            <div className="relative">
              <select
                value={selectedCourseId ?? ''}
                onChange={(e) => {
                  const id = e.target.value;
                  if (id) handleSelectCourse(id);
                }}
                disabled={loadingCourses}
                className="w-full appearance-none pl-3 pr-9 py-2.5 border border-gray-300 rounded-lg bg-white text-sm
                           focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
                           disabled:bg-gray-100 disabled:text-gray-400 transition-colors"
              >
                <option value="">
                  {loadingCourses ? '불러오는 중...' : courses.length === 0 ? '담당 과목 없음' : '— 과목을 선택하세요 —'}
                </option>
                {courses.map((c) => (
                  <option key={c.courseId} value={c.courseId}>
                    {c.courseName || `과목 #${c.courseId}`}
                    {c.sourceType === 'haksa' ? ' [정규]' : c.sourceType === 'prism' ? ' [비정규]' : ''}
                  </option>
                ))}
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            </div>
          </div>

          {/* 액션 버튼 */}
          {selectedCourseId && (
            <div className="flex gap-2">
              <button
                onClick={handleDownloadCsv}
                disabled={students.length === 0}
                className="flex items-center gap-2 px-3 py-2.5 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50 transition-colors text-sm"
              >
                <Download className="w-4 h-4" />
                <span>CSV</span>
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !dirty}
                className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors text-sm"
              >
                <Save className="w-4 h-4" />
                <span>{saving ? '저장 중...' : '저장'}</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ===== 본문 영역 ===== */}
      {!selectedCourseId ? (
        <div className="bg-white rounded-xl border-2 border-dashed border-gray-300 p-16 text-center">
          <div className="text-gray-400">
            <ChevronDown className="w-10 h-10 mx-auto mb-3 opacity-50" />
            <p className="text-lg font-medium">상단에서 과목을 선택하세요</p>
            <p className="text-sm mt-1">선택한 과목의 출석 관리 화면이 표시됩니다.</p>
          </div>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          {errorMessage && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {errorMessage}
            </div>
          )}

          {/* 설정 바 */}
          <div className="mb-4 p-4 bg-gray-50 border border-gray-200 rounded-lg">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 items-end">
              <div>
                <label className="block text-sm text-gray-700 mb-1">총 주차 수</label>
                <input
                  type="number"
                  min={1}
                  max={30}
                  value={weekCount}
                  onChange={(e) => setWeekCount(Math.max(1, Math.min(30, Number(e.target.value) || 15)))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm text-gray-700 mb-1">결석 자동 F 기준 (N회 이상)</label>
                <input
                  type="number"
                  min={1}
                  max={30}
                  value={absenceLimit}
                  onChange={(e) => setAbsenceLimit(Math.max(1, Number(e.target.value) || 4))}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm text-gray-700 mb-1">학생 검색</label>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                  <input
                    type="text"
                    placeholder="이름 또는 학번"
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                    className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>
              <div>
                <button
                  onClick={handleAutoFail}
                  disabled={autoFailStudents.length === 0}
                  className="flex items-center gap-2 w-full justify-center px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors text-sm"
                >
                  <AlertTriangle className="w-4 h-4" />
                  <span>결석 초과 F 처리 ({autoFailStudents.length}명)</span>
                </button>
              </div>
            </div>
          </div>

          {/* 범례 */}
          <div className="mb-3 flex gap-3 text-xs">
            {STATUS_CYCLE.map((status) => (
              <span key={status} className={`px-2 py-1 rounded ${STATUS_COLORS[status]}`}>
                {STATUS_LABELS[status]}
              </span>
            ))}
            <span className="text-gray-500 ml-2">셀을 클릭하면 상태가 전환됩니다</span>
          </div>

          {/* 출석 테이블 */}
          {loading ? (
            <div className="p-10 text-center text-gray-500 text-sm">불러오는 중...</div>
          ) : (
            <div className="overflow-x-auto border border-gray-200 rounded-lg">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-3 py-2 text-left text-gray-700 sticky left-0 bg-gray-50 z-10 min-w-[110px]">
                      이름
                    </th>
                    <th className="px-3 py-2 text-center text-gray-700 min-w-[80px]">학번</th>
                    {Array.from({ length: weekCount }, (_, i) => (
                      <th key={i} className="px-1 py-2 text-center text-gray-700 min-w-[48px] whitespace-nowrap">
                        {i + 1}주
                      </th>
                    ))}
                    <th className="px-3 py-2 text-center text-gray-700 min-w-[60px]">결석</th>
                    <th className="px-3 py-2 text-center text-gray-700 min-w-[50px]">상태</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {filteredStudents.map((student) => {
                    const absences = getAbsenceCount(student);
                    const isFail = absences >= absenceLimit;
                    const originalIdx = students.indexOf(student);
                    return (
                      <tr
                        key={student.courseUserId}
                        className={`hover:bg-gray-50 transition-colors ${isFail ? 'bg-red-50' : ''}`}
                      >
                        <td className="px-3 py-2 text-gray-900 sticky left-0 bg-white z-10">{student.name}</td>
                        <td className="px-3 py-2 text-center text-gray-600">{student.studentId}</td>
                        {Array.from({ length: weekCount }, (_, w) => {
                          const week = w + 1;
                          const status = student.weeks[week] || 'present';
                          return (
                            <td
                              key={week}
                              className="px-1 py-2 text-center cursor-pointer select-none"
                              onClick={() => toggleStatus(originalIdx, week)}
                              title={`${week}주차: ${STATUS_LABELS[status]} (클릭하여 변경)`}
                            >
                              <span
                                className={`inline-block px-1.5 py-0.5 rounded text-xs font-medium ${STATUS_COLORS[status]}`}
                              >
                                {STATUS_LABELS[status].charAt(0)}
                              </span>
                            </td>
                          );
                        })}
                        <td className="px-3 py-2 text-center">
                          <span className={`font-medium ${isFail ? 'text-red-700' : 'text-gray-700'}`}>
                            {absences}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-center">
                          {isFail ? (
                            <span className="inline-flex px-2 py-0.5 bg-red-200 text-red-800 rounded-full text-xs font-bold">
                              F
                            </span>
                          ) : (
                            <span className="inline-flex px-2 py-0.5 bg-green-100 text-green-700 rounded-full text-xs">
                              정상
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}

                  {filteredStudents.length === 0 && !loading && (
                    <tr>
                      <td colSpan={weekCount + 4} className="px-4 py-10 text-center text-gray-500">
                        {keyword ? '검색 결과가 없습니다.' : '수강생 데이터가 없습니다.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}

          {/* 결석 초과 안내 */}
          {autoFailStudents.length > 0 && (
            <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
              <AlertTriangle className="inline w-4 h-4 mr-1 -mt-0.5" />
              결석 {absenceLimit}회 이상 학생: <strong>{autoFailStudents.length}명</strong>
              {' — '}
              {autoFailStudents.map((s) => s.name).join(', ')}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
