import React, { useEffect, useMemo, useState } from 'react';
import { Download, Info, Plus, Search, Trash2 } from 'lucide-react';
import { tutorLmsApi, TutorCourseStudentRow, HaksaCourseStudentRow } from '../../api/tutorLmsApi';
import { downloadCsv } from '../../utils/csv';

function maskName(name?: string) {
  if (!name) return '-';
  const trimmed = String(name).trim();
  if (trimmed.length <= 1) return '*';
  return `${trimmed[0]}${'*'.repeat(trimmed.length - 1)}`;
}

function maskStudentId(studentId?: string) {
  if (!studentId) return '-';
  const raw = String(studentId).trim();
  if (raw.length <= 2) return '*'.repeat(raw.length);
  if (raw.length <= 4) return `${raw[0]}${'*'.repeat(raw.length - 1)}`;
  return `${raw.slice(0, 2)}${'*'.repeat(raw.length - 4)}${raw.slice(-2)}`;
}

function maskEmail(email?: string) {
  if (!email) return '-';
  const raw = String(email).trim();
  const [local, domain] = raw.split('@');
  if (!domain) return maskStudentId(raw);
  const maskedLocal = local.length <= 1 ? '*' : `${local[0]}${'*'.repeat(Math.min(3, local.length - 1))}`;
  return `${maskedLocal}@${domain}`;
}

function maskPhone(phone?: string) {
  if (!phone) return '-';
  const digits = String(phone).replace(/\D/g, '');
  if (digits.length < 7) return phone;
  if (digits.length === 10) return `${digits.slice(0, 3)}-***-${digits.slice(6)}`;
  if (digits.length >= 11) return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`;
  return phone;
}

type StudentTabCourse = {
  sourceType?: 'haksa' | 'prism';
  mappedCourseId?: number;
  haksaCourseCode?: string;
  haksaOpenYear?: string;
  haksaOpenTerm?: string;
  haksaBunbanCode?: string;
  haksaGroupCode?: string;
};

type LearnerRow = {
  id: string;
  name: string;
  campus: string;
  major: string;
  studentId?: string;
  email?: string;
  deptPath?: string;
};

export function StudentsTab({ courseId, course }: { courseId: number; course?: StudentTabCourse }) {
  // 왜: 학사 탭은 매핑 여부와 관계없이 학사 수강생을 보여야 하므로, sourceType만 기준으로 분기합니다.
  const resolvedCourseId = Number(course?.mappedCourseId ?? courseId);
  const isHaksaCourse = course?.sourceType === 'haksa';

  const [rows, setRows] = useState<TutorCourseStudentRow[]>([]);
  const [haksaRows, setHaksaRows] = useState<HaksaCourseStudentRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [keyword, setKeyword] = useState('');
  const [isMasked, setIsMasked] = useState(true);
  const [showPrivacyModal, setShowPrivacyModal] = useState(false);
  const [privacyPurpose, setPrivacyPurpose] = useState('');
  const [privacyAction, setPrivacyAction] = useState<'view' | 'download' | null>(null);
  const [privacyError, setPrivacyError] = useState<string | null>(null);
  const [privacySaving, setPrivacySaving] = useState(false);

  const [showAddModal, setShowAddModal] = useState(false);
  const [addSearchTerm, setAddSearchTerm] = useState('');
  const [addDebouncedSearchTerm, setAddDebouncedSearchTerm] = useState('');
  const [addCampus, setAddCampus] = useState('전체');
  const [addMajor, setAddMajor] = useState('전체');
  const [addLearners, setAddLearners] = useState<LearnerRow[]>([]);
  const [addPage, setAddPage] = useState(1);
  const [addTotalCount, setAddTotalCount] = useState(0);
  const [addLoading, setAddLoading] = useState(false);
  const [addErrorMessage, setAddErrorMessage] = useState<string | null>(null);
  const [selectedLearners, setSelectedLearners] = useState<LearnerRow[]>([]);

  const refresh = async (nextKeyword?: string) => {
    setLoading(true);
    setErrorMessage(null);
    setIsMasked(true);
    try {
      const res = await tutorLmsApi.getCourseStudents({ courseId: resolvedCourseId, keyword: nextKeyword ?? keyword });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      setRows(res.rst_data ?? []);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '수강생을 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 왜: 학사 과목은 courseId가 유효하지 않아 API 호출이 불가능하므로, 빈 상태로 시작합니다.
    if (isHaksaCourse) return;

    let cancelled = false;

    // 왜: 새로고침해도 동일한 DB 결과가 보여야 하므로, 탭 진입 시 서버에서 다시 가져옵니다.
    const run = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getCourseStudents({ courseId: resolvedCourseId });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        if (!cancelled) setRows(res.rst_data ?? []);
      } catch (e) {
        if (!cancelled) setErrorMessage(e instanceof Error ? e.message : '수강생을 불러오는 중 오류가 발생했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    run();
    return () => {
      cancelled = true;
    };
  }, [resolvedCourseId, isHaksaCourse]);

  const refreshHaksa = async (nextKeyword: string) => {
    if (!course?.haksaCourseCode) {
      setErrorMessage('학사 과목 코드가 없습니다.');
      return;
    }
    setLoading(true);
    setErrorMessage(null);
    setIsMasked(true);
    try {
      const res = await tutorLmsApi.getHaksaCourseStudents({
        courseCode: course.haksaCourseCode,
        openYear: course.haksaOpenYear,
        openTerm: course.haksaOpenTerm,
        bunbanCode: course.haksaBunbanCode,
        groupCode: course.haksaGroupCode,
        keyword: nextKeyword,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      setHaksaRows(res.rst_data ?? []);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '학사 수강생을 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isHaksaCourse) return;
    void refreshHaksa('');
  }, [isHaksaCourse, course?.haksaCourseCode, course?.haksaOpenYear, course?.haksaOpenTerm, course?.haksaBunbanCode, course?.haksaGroupCode]);

  const totalCount = rows.length;

  useEffect(() => {
    // 왜: 이름 검색은 입력 중에는 기다렸다가, 입력이 멈추면 실제 검색을 실행합니다.
    const timer = setTimeout(() => setAddDebouncedSearchTerm(addSearchTerm), 250);
    return () => clearTimeout(timer);
  }, [addSearchTerm]);

  useEffect(() => {
    if (!showAddModal) return;

    let cancelled = false;
    const fetchLearners = async () => {
      setAddLoading(true);
      setAddErrorMessage(null);
      try {
        // 왜: 캠퍼스/전공은 사이트별 DB 구조가 달라, 부서명 포함 검색으로 단순 지원합니다.
        const deptKeyword = addMajor !== '전체' ? addMajor : (addCampus !== '전체' ? addCampus : undefined);

        const res = await tutorLmsApi.getLearners({
          keyword: addDebouncedSearchTerm,
          deptKeyword,
          page: addPage,
          limit: 30,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const rows = res.rst_data ?? [];
        const mapped: LearnerRow[] = rows.map((row: any) => {
          const deptPath = String(row.dept_path || '');
          const parts = deptPath.split(' > ').map((p: string) => p.trim()).filter(Boolean);
          const campusName = parts[0] || String(row.dept_nm || '-') || '-';
          const majorName = (parts.length > 0 ? parts[parts.length - 1] : String(row.dept_nm || '-')) || '-';

          return {
            id: String(row.id),
            name: String(row.name || row.user_nm || '-'),
            campus: campusName,
            major: majorName,
            studentId: String(row.student_id || ''),
            email: String(row.email || ''),
            deptPath,
          };
        });

        if (!cancelled) {
          setAddLearners(mapped);
          setAddTotalCount(Number(res.rst_total ?? res.rst_count ?? rows.length ?? 0));
        }
      } catch (e) {
        if (!cancelled) setAddErrorMessage(e instanceof Error ? e.message : '조회 중 오류가 발생했습니다.');
      } finally {
        if (!cancelled) setAddLoading(false);
      }
    };

    void fetchLearners();
    return () => {
      cancelled = true;
    };
  }, [showAddModal, addDebouncedSearchTerm, addCampus, addMajor, addPage]);

  const openPrivacyModal = (action: 'view' | 'download') => {
    setPrivacyAction(action);
    setPrivacyError(null);
    setShowPrivacyModal(true);
  };

  const closePrivacyModal = () => {
    if (privacySaving) return;
    setShowPrivacyModal(false);
    setPrivacyError(null);
    setPrivacyAction(null);
  };

  const logPrivacyAccess = async (action: 'view' | 'download') => {
    const purpose = privacyPurpose.trim();
    if (!purpose) {
      setPrivacyError('사유를 입력해 주세요.');
      return false;
    }

    const userIds = isHaksaCourse
      ? []
      : rows.map((row) => Number(row.user_id)).filter((id) => Number.isFinite(id) && id > 0);
    const userCnt = isHaksaCourse ? haksaRows.length : rows.length;
    const pageName = action === 'download' ? '수강생 정보 다운로드' : '수강생 정보 보기';

    try {
      setPrivacySaving(true);
      const res = await tutorLmsApi.logPrivacyAccess({
        logType: action === 'download' ? 'E' : 'V',
        purpose,
        pageName,
        courseId: resolvedCourseId,
        userIds,
        userCnt,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      return true;
    } catch (e) {
      setPrivacyError(e instanceof Error ? e.message : '개인정보 로그 저장 중 오류가 발생했습니다.');
      return false;
    } finally {
      setPrivacySaving(false);
    }
  };

  const renderPrivacyModal = (onDownload: () => void) => {
    if (!showPrivacyModal) return null;
    const title = privacyAction === 'download' ? '개인 정보 보기' : '가려진 정보 보기';

    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-xl shadow-xl w-full max-w-xl">
          <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
            <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
            <button
              onClick={closePrivacyModal}
              className="text-gray-400 hover:text-gray-600"
              aria-label="닫기"
            >
              ✕
            </button>
          </div>
          <div className="px-6 py-4 space-y-4 text-sm text-gray-700">
            <div className="bg-gray-100 rounded-lg px-4 py-3 leading-6">
              개인정보보호를 위하여 개인정보의 입.출력 및 수정사항, 파일별.담당자별 데이터접근내역을 기록합니다.
            </div>

            <div>
              <div className="font-semibold mb-1">기록 항목</div>
              <div>아이디(식별정보), 성명, 조회일시, 조회자아이피, 수행업무, 조회 목적 등</div>
            </div>

            <div>
              <div className="font-semibold mb-1">참고</div>
              <div>
                개인정보 보호법 제29조(안전조치의무), 동법 시행령 제30조(개인정보의 안전성 확보 조치), 개인정보의
                안전성 확보조치 기준 고시 제8조(접속기록의 보관 및 점검)
              </div>
            </div>

            <div>
              <div className="font-semibold mb-1">조회 목적</div>
              <input
                value={privacyPurpose}
                onChange={(e) => setPrivacyPurpose(e.target.value)}
                placeholder="조회 목적을 입력하세요."
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            {privacyError && <div className="text-sm text-red-600">{privacyError}</div>}
          </div>
          <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-center">
            <button
              onClick={async () => {
                if (!privacyAction) return;
                const ok = await logPrivacyAccess(privacyAction);
                if (!ok) return;
                if (privacyAction === 'view') setIsMasked(false);
                if (privacyAction === 'download') {
                  onDownload();
                }
                closePrivacyModal();
              }}
              className="px-6 py-2 text-sm text-white bg-amber-500 rounded-lg hover:bg-amber-600 disabled:bg-amber-300"
              disabled={privacySaving}
            >
              {privacySaving ? '저장 중...' : '개인정보 조회 기록 내용을 확인하였습니다.'}
            </button>
          </div>
        </div>
      </div>
    );
  };

  const openAddModal = () => {
    setShowAddModal(true);
    setAddSearchTerm('');
    setAddDebouncedSearchTerm('');
    setAddCampus('전체');
    setAddMajor('전체');
    setAddPage(1);
    setSelectedLearners([]);
  };

  const closeAddModal = () => {
    if (addLoading) return;
    setShowAddModal(false);
  };

  const toggleLearner = (learner: LearnerRow) => {
    const isSelected = selectedLearners.some((l) => l.id === learner.id);
    if (isSelected) {
      setSelectedLearners((prev) => prev.filter((l) => l.id !== learner.id));
    } else {
      setSelectedLearners((prev) => [...prev, learner]);
    }
  };

  const selectAllLearners = () => {
    // 왜: 페이징 화면에서는 "현재 페이지에 보이는 목록"만 전체 선택하는 것이 안전합니다.
    setSelectedLearners([...addLearners]);
  };

  const deselectAllLearners = () => {
    setSelectedLearners([]);
  };

  const handleAddSelectedLearners = async () => {
    const userIds = selectedLearners
      .map((learner) => Number(learner.id))
      .filter((id) => Number.isFinite(id) && id > 0);

    if (userIds.length === 0) {
      alert('선택된 수강생이 없습니다.');
      return;
    }

    try {
      const res = await tutorLmsApi.addCourseStudents({ courseId: resolvedCourseId, userIds });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const inserted = Number(res.rst_data ?? 0);
      const skipped = Number(res.rst_skipped ?? 0);
      const notFound = Number((res as any).rst_not_found ?? 0);
      alert(
        `추가 완료: ${inserted}명` +
          `${skipped > 0 ? ` (중복 ${skipped}명 제외)` : ''}` +
          `${notFound > 0 ? ` (미존재 ${notFound}건)` : ''}`
      );

      closeAddModal();
      await refresh();
    } catch (e) {
      alert(e instanceof Error ? e.message : '수강생 추가 중 오류가 발생했습니다.');
    }
  };

  const handleRemoveStudent = async (userId: number, name?: string) => {
    const ok = window.confirm(`${name || '해당 수강생'}을(를) 과목에서 제외하시겠습니까?`);
    if (!ok) return;

    try {
      const res = await tutorLmsApi.removeCourseStudent({ courseId: resolvedCourseId, userId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      await refresh();
    } catch (e) {
      alert(e instanceof Error ? e.message : '수강생 제외 중 오류가 발생했습니다.');
    }
  };

  const sortedRows = useMemo(() => {
    // 왜: 서버에서는 id DESC로 내려오지만, 화면에서는 이름 기준이 더 읽기 쉽습니다.
    return [...rows].sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
  }, [rows]);

  const doDownloadCsv = () => {
    // 왜: 운영자가 바로 확인할 수 있도록, 화면에 표시 중인 목록을 그대로 CSV로 내려받습니다.
    const ymd = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const filename = `course_${resolvedCourseId}_students_${ymd}.csv`;

    const headers = ['No', 'course_user_id', 'user_id', '학번', '이름', '이메일', '진도율(%)'];
    const body = sortedRows.map((student, index) => {
      const progress = Number(student.progress ?? student.progress_ratio ?? 0);
      return [
        index + 1,
        student.course_user_id,
        student.user_id,
        student.student_id ?? '',
        student.name ?? '',
        student.email ?? '',
        Math.round(progress),
      ];
    });

    downloadCsv(filename, headers, body);
  };

  const handleDownloadCsv = () => {
    openPrivacyModal('download');
  };

  const renderAddModal = () => {
    if (!showAddModal) return null;

    const campusOptions = ['전체', ...Array.from(new Set(addLearners.map((l) => l.campus).filter(Boolean)))];
    const majorOptions = ['전체', ...Array.from(new Set(
      addLearners
        .filter((l) => addCampus === '전체' || l.campus === addCampus)
        .map((l) => l.major)
        .filter(Boolean),
    ))];
    const totalPages = Math.max(1, Math.ceil((addTotalCount || 0) / 30));
    const canPrev = addPage > 1;
    const canNext = addPage < totalPages;

    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-xl shadow-xl w-full max-w-5xl max-h-[90vh] overflow-hidden flex flex-col">
          <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
            <h3 className="text-lg font-semibold text-gray-900">학습자 선택</h3>
            <button
              onClick={closeAddModal}
              className="text-gray-400 hover:text-gray-600"
              aria-label="닫기"
            >
              ?
            </button>
          </div>

          <div className="p-6 space-y-4 overflow-y-auto">
            <div className="bg-gray-50 p-4 rounded-lg space-y-4">
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm text-gray-700 mb-2">이름 검색</label>
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                    <input
                      type="text"
                      value={addSearchTerm}
                      onChange={(e) => {
                        setAddSearchTerm(e.target.value);
                        setAddPage(1);
                      }}
                      placeholder="학습자 이름 검색..."
                      className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm text-gray-700 mb-2">캠퍼스</label>
                  <select
                    value={addCampus}
                    onChange={(e) => {
                      setAddCampus(e.target.value);
                      setAddMajor('전체');
                      setAddPage(1);
                    }}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {campusOptions.map((opt) => (
                      <option key={opt} value={opt}>{opt}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm text-gray-700 mb-2">전공</label>
                  <select
                    value={addMajor}
                    onChange={(e) => {
                      setAddMajor(e.target.value);
                      setAddPage(1);
                    }}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {majorOptions.map((opt) => (
                      <option key={opt} value={opt}>{opt}</option>
                    ))}
                  </select>
                </div>
              </div>

              {addErrorMessage && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
                  {addErrorMessage}
                </div>
              )}

              <div className="flex items-center justify-between">
                <div className="flex gap-3">
                  <button
                    onClick={selectAllLearners}
                    disabled={addLoading}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                  >
                    전체 선택
                  </button>
                  <button
                    onClick={deselectAllLearners}
                    disabled={addLoading}
                    className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
                  >
                    현재 목록 전체 해제
                  </button>
                </div>
                <div className="text-sm text-gray-600">
                  {addLoading ? '불러오는 중...' : `검색 결과: ${addTotalCount}명 / 선택된: ${selectedLearners.length}명`}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              {addLearners.map((learner) => {
                const isSelected = selectedLearners.some((l) => l.id === learner.id);
                return (
                  <div
                    key={learner.id}
                    onClick={() => toggleLearner(learner)}
                    className={`p-4 border-2 rounded-lg cursor-pointer transition-all ${
                      isSelected ? 'border-blue-600 bg-blue-50' : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="text-gray-900">{learner.name}</div>
                      {isSelected && (
                        <div className="w-5 h-5 bg-blue-600 rounded-full flex items-center justify-center">
                          <span className="text-xs text-white">✓</span>
                        </div>
                      )}
                    </div>
                    <div className="text-sm text-gray-600">{learner.campus}</div>
                    <div className="text-sm text-gray-600">{learner.major}</div>
                  </div>
                );
              })}
              {!addLoading && !addErrorMessage && addLearners.length === 0 && (
                <div className="col-span-3 text-center text-sm text-gray-500 py-6">
                  표시할 학습자가 없습니다.
                </div>
              )}
            </div>

            <div className="flex items-center justify-center gap-3">
              <button
                type="button"
                disabled={addLoading || !canPrev}
                onClick={() => setAddPage((prev) => Math.max(1, prev - 1))}
                className="px-3 py-2 border border-gray-300 rounded-lg text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition-colors"
              >
                이전
              </button>
              <div className="text-sm text-gray-600">
                {addPage} / {totalPages}
              </div>
              <button
                type="button"
                disabled={addLoading || !canNext}
                onClick={() => setAddPage((prev) => Math.min(totalPages, prev + 1))}
                className="px-3 py-2 border border-gray-300 rounded-lg text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition-colors"
              >
                다음
              </button>
            </div>
          </div>

          <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-end gap-3">
            <button
              onClick={closeAddModal}
              className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
            >
              취소
            </button>
            <button
              onClick={handleAddSelectedLearners}
              disabled={addLoading || selectedLearners.length === 0}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-blue-300 transition-colors"
            >
              선택 학습자 추가
            </button>
          </div>
        </div>
      </div>
    );
  };

  // 왜: 학사 과목은 LMS 수강생을 저장하지 않으므로, 학사 View 조회 결과만 읽기 전용으로 보여줍니다.
  if (isHaksaCourse) {
    const haksaCount = haksaRows.length;
    const haksaSorted = [...haksaRows].sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
    const doHaksaDownloadCsv = () => {
      const ymd = new Date().toISOString().slice(0, 10).replace(/-/g, '');
      const filename = `haksa_course_${course?.haksaCourseCode || 'unknown'}_students_${ymd}.csv`;
      const headers = ['No', '학번', '이름', '이메일', '휴대폰', '상태'];
      const body = haksaSorted.map((student, index) => [
        index + 1,
        student.student_id ?? '',
        student.name ?? '',
        student.email ?? '',
        student.mobile ?? '',
        student.visible === 'Y' ? '정상' : student.visible === 'N' ? '폐강' : student.visible ?? '',
      ]);
      downloadCsv(filename, headers, body);
    };

    const handleHaksaDownloadCsv = () => {
      openPrivacyModal('download');
    };

    return (
      <div className="space-y-4">
        <div className="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-lg text-sm flex items-start gap-2">
          <Info className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <div>
            <strong>학사 연동 과목</strong>: 이 과목은 학사 시스템(e-poly)에서 연동되었습니다. 
            수강생 목록은 학사 View를 읽어오는 방식으로 제공됩니다(읽기 전용).
          </div>
        </div>
        {isMasked && (
          <div className="bg-gray-50 border border-gray-200 text-gray-700 px-4 py-3 rounded-lg text-sm flex items-start gap-2">
            <Info className="w-5 h-5 flex-shrink-0 mt-0.5" />
            <div>
              개인정보 보호를 위해 이름/이메일/휴대폰이 가려져 있습니다. 확인이 필요하면 “가려진 정보 보기”를 눌러 주세요.
            </div>
          </div>
        )}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="text-sm text-gray-600">총 {haksaCount}명</div>
          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="학번/이름/이메일 검색"
                className="pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              onClick={() => refreshHaksa(keyword)}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors whitespace-nowrap"
            >
              검색
            </button>
            {isMasked ? (
              <button
                onClick={() => openPrivacyModal('view')}
                disabled={haksaCount === 0}
                className="flex items-center gap-1.5 px-3 py-2 bg-amber-500 text-white text-sm rounded-lg hover:bg-amber-600 disabled:bg-amber-300 transition-colors whitespace-nowrap"
              >
                <span>가려진 정보 보기</span>
              </button>
            ) : (
              <span className="text-sm text-emerald-600 whitespace-nowrap">개인정보 표시중</span>
            )}
            <button
              onClick={handleHaksaDownloadCsv}
              className="flex items-center gap-1.5 px-3 py-2 bg-white text-gray-700 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors whitespace-nowrap"
            >
              <Download className="w-3.5 h-3.5" />
              <span>엑셀(CSV)</span>
            </button>
          </div>
        </div>

        {loading && <div className="text-sm text-gray-600">불러오는 중...</div>}
        {errorMessage && <div className="text-sm text-red-600">{errorMessage}</div>}

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm text-gray-700">No</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">학번</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">이름</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">이메일</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">휴대폰</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">상태</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {haksaSorted.map((student, index) => {
                const visibleLabel =
                  student.visible === 'Y' ? '정상' : student.visible === 'N' ? '폐강' : student.visible || '-';

                return (
                  <tr key={`${student.student_id}-${index}`} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-4 text-sm text-gray-900">{index + 1}</td>
                    <td className="px-4 py-4 text-sm text-gray-900">
                      {isMasked ? maskStudentId(student.student_id) : student.student_id || '-'}
                    </td>
                    <td className="px-4 py-4 text-sm text-gray-900">
                      {isMasked ? maskName(student.name) : student.name || '-'}
                    </td>
                    <td className="px-4 py-4 text-sm text-gray-600">
                      {isMasked ? maskEmail(student.email) : student.email || '-'}
                    </td>
                    <td className="px-4 py-4 text-sm text-gray-600">
                      {isMasked ? maskPhone(student.mobile) : student.mobile || '-'}
                    </td>
                    <td className="px-4 py-4 text-center text-sm text-gray-900">{visibleLabel}</td>
                  </tr>
                );
              })}
              {!loading && !errorMessage && haksaSorted.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-sm text-gray-500">
                    수강생이 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {renderPrivacyModal(doHaksaDownloadCsv)}
      </div>
    );
  }

  return (
    <div>
      {isMasked && (
        <div className="mb-4 bg-gray-50 border border-gray-200 text-gray-700 px-4 py-3 rounded-lg text-sm flex items-start gap-2">
          <Info className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <div>개인정보 보호를 위해 이름/이메일이 가려져 있습니다. 확인이 필요하면 “가려진 정보 보기”를 눌러 주세요.</div>
        </div>
      )}
      <div className="mb-4 flex items-center justify-between gap-3 flex-wrap">
        <div className="text-sm text-gray-600">총 {totalCount}명</div>

        <div className="flex items-center gap-2 flex-wrap">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="이름/아이디/이메일 검색"
              className="pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            onClick={() => refresh(keyword)}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors whitespace-nowrap"
          >
            검색
          </button>

          <button
            onClick={openAddModal}
            className="flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 transition-colors whitespace-nowrap"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>수강생 추가</span>
          </button>

          {isMasked ? (
            <button
              onClick={() => openPrivacyModal('view')}
              disabled={totalCount === 0}
              className="flex items-center gap-1.5 px-3 py-2 bg-amber-500 text-white text-sm rounded-lg hover:bg-amber-600 disabled:bg-amber-300 transition-colors whitespace-nowrap"
            >
              <span>가려진 정보 보기</span>
            </button>
          ) : (
            <span className="text-sm text-emerald-600 whitespace-nowrap">개인정보 표시중</span>
          )}

          <button
            onClick={handleDownloadCsv}
            className="flex items-center gap-1.5 px-3 py-2 bg-white text-gray-700 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors whitespace-nowrap"
          >
            <Download className="w-3.5 h-3.5" />
            <span>엑셀(CSV)</span>
          </button>
        </div>
      </div>

      {loading && <div className="text-sm text-gray-600">불러오는 중...</div>}
      {errorMessage && <div className="text-sm text-red-600">{errorMessage}</div>}

      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 text-left text-sm text-gray-700">No</th>
              <th className="px-4 py-3 text-left text-sm text-gray-700">학번</th>
              <th className="px-4 py-3 text-left text-sm text-gray-700">이름</th>
              <th className="px-4 py-3 text-left text-sm text-gray-700">이메일</th>
              <th className="px-4 py-3 text-center text-sm text-gray-700">진도율</th>
              <th className="px-4 py-3 text-center text-sm text-gray-700">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {sortedRows.map((student, index) => {
              const progress = Number(student.progress ?? student.progress_ratio ?? 0);

              return (
                <tr key={student.course_user_id || `${student.user_id}-${index}`} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-4 text-sm text-gray-900">{index + 1}</td>
                  <td className="px-4 py-4 text-sm text-gray-900">
                    {isMasked ? maskStudentId(student.student_id) : student.student_id || '-'}
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-900">
                    {isMasked ? maskName(student.name) : student.name || '-'}
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-600">
                    {isMasked ? maskEmail(student.email) : student.email || '-'}
                  </td>
                  <td className="px-4 py-4">
                    <div className="flex items-center justify-center gap-2">
                      <div className="w-24 h-2 bg-gray-200 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-blue-600 rounded-full"
                          style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}
                        />
                      </div>
                      <span className="text-sm text-gray-900">{Math.round(progress)}%</span>
                    </div>
                  </td>
                  <td className="px-4 py-4 text-center">
                    <button
                      onClick={() => handleRemoveStudent(Number(student.user_id), student.name)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                      제외
                    </button>
                  </td>
                </tr>
              );
            })}
            {!loading && !errorMessage && sortedRows.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-gray-500">
                  수강생이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {renderPrivacyModal(doDownloadCsv)}
      {renderAddModal()}
    </div>
  );
}

