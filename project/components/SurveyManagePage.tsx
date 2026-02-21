import React, { useEffect, useMemo, useState } from 'react';
import { Plus, Edit, Trash2, Search, Save, X, BarChart3, Download } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';
import { downloadCsv } from '../utils/csv';

type CourseOption = {
  id: number;
  name: string;
};

type SurveyItem = {
  id: number;
  title: string;
  description: string;
  anonymous: boolean;
  applyText: string;
  startDate: string;
  endDate: string;
  responseCount: number;
  totalCount: number;
  questionCount: number;
};

type SurveyQuestionStat = {
  questionId: number;
  question: string;
  questionType: string;
  responseCount: number;
  options: Array<{ label: string; count: number }>;
};

export function SurveyManagePage() {
  const [courses, setCourses] = useState<CourseOption[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState<number | null>(null);
  const [loadingCourses, setLoadingCourses] = useState(true);
  const [loadingList, setLoadingList] = useState(false);
  const [surveys, setSurveys] = useState<SurveyItem[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [resultSurvey, setResultSurvey] = useState<SurveyItem | null>(null);
  const [resultStats, setResultStats] = useState<SurveyQuestionStat[]>([]);
  const [loadingResult, setLoadingResult] = useState(false);

  const [formTitle, setFormTitle] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formAnon, setFormAnon] = useState(true);
  const [formStart, setFormStart] = useState('');
  const [formEnd, setFormEnd] = useState('');
  const [formQuestionType, setFormQuestionType] = useState<'1' | 'M' | '2' | '3'>('3');
  const [formQuestion, setFormQuestion] = useState('');
  const [formOptions, setFormOptions] = useState<string[]>(['']);

  const filtered = useMemo(() => {
    if (!searchTerm.trim()) return surveys;
    const kw = searchTerm.toLowerCase();
    return surveys.filter((s) => s.title.toLowerCase().includes(kw) || s.description.toLowerCase().includes(kw));
  }, [surveys, searchTerm]);

  const fetchCourses = async () => {
    setLoadingCourses(true);
    try {
      const [prismRes, haksaRes] = await Promise.all([
        tutorLmsApi.getMyCoursesCombined({ tab: 'prism', pageSize: 200 }),
        tutorLmsApi.getMyCoursesCombined({ tab: 'haksa', pageSize: 200 }),
      ]);
      const mapped: CourseOption[] = [];
      for (const row of prismRes.rst_data ?? []) {
        mapped.push({ id: Number(row.id), name: String(row.course_nm_conv || row.subject_nm_conv || row.course_nm || `과목 #${row.id}`) });
      }
      for (const row of haksaRes.rst_data ?? []) {
        mapped.push({ id: Number(row.id), name: String(row.course_nm_conv || row.subject_nm_conv || row.course_nm || `과목 #${row.id}`) });
      }
      const uniqMap = new Map<number, CourseOption>();
      mapped.forEach((item) => {
        if (Number.isFinite(item.id) && item.id > 0 && !uniqMap.has(item.id)) uniqMap.set(item.id, item);
      });
      const uniq = Array.from(uniqMap.values());
      setCourses(uniq);
      setSelectedCourseId((prev) => {
        if (prev && uniq.some((course) => course.id === prev)) return prev;
        return uniq[0]?.id ?? null;
      });
    } catch {
      setCourses([]);
      setSelectedCourseId(null);
    } finally {
      setLoadingCourses(false);
    }
  };

  const fetchSurveys = async (courseId: number) => {
    setLoadingList(true);
    try {
      const res = await tutorLmsApi.getSurveys({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
      setSurveys(
        rows.map((row: any) => ({
          id: Number(row.survey_id),
          title: String(row.survey_nm || row.module_nm || `설문 #${row.survey_id}`),
          description: '',
          anonymous: String(row.anonymous_yn ?? 'Y') === 'Y',
          applyText: String(row.apply_conv ?? ''),
          startDate: String(row.start_date_conv ?? ''),
          endDate: String(row.end_date_conv ?? ''),
          responseCount: Number(row.submitted_cnt ?? 0),
          totalCount: Number(row.total_cnt ?? 0),
          questionCount: Number(row.item_cnt ?? 1),
        }))
      );
    } catch {
      setSurveys([]);
    } finally {
      setLoadingList(false);
    }
  };

  const fetchSurveyResult = async (survey: SurveyItem) => {
    if (!selectedCourseId) return;
    setLoadingResult(true);
    setResultSurvey(survey);
    try {
      const res = await tutorLmsApi.getSurveyResult({ courseId: selectedCourseId, surveyId: survey.id });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
      const mapped: SurveyQuestionStat[] = rows.map((row: any) => {
        const itemCnt = Number(row.item_cnt ?? 0);
        const options: Array<{ label: string; count: number }> = [];
        for (let i = 1; i <= itemCnt; i++) {
          const label = String(row[`item${i}`] ?? '');
          const count = Number(row[`item${i}_cnt`] ?? 0);
          if (label) options.push({ label, count });
        }
        return {
          questionId: Number(row.question_id),
          question: String(row.question ?? ''),
          questionType: String(row.question_type_conv ?? ''),
          responseCount: Number(row.response_cnt ?? 0),
          options,
        };
      });
      setResultStats(mapped);
    } catch {
      setResultStats([]);
    } finally {
      setLoadingResult(false);
    }
  };

  useEffect(() => {
    void fetchCourses();
  }, []);

  useEffect(() => {
    if (!selectedCourseId) {
      setSurveys([]);
      return;
    }
    void fetchSurveys(selectedCourseId);
  }, [selectedCourseId]);

  const openAddModal = () => {
    setEditingId(null);
    setFormTitle('');
    setFormDesc('');
    setFormAnon(true);
    setFormStart('');
    setFormEnd('');
    setFormQuestionType('3');
    setFormQuestion('');
    setFormOptions(['']);
    setModalOpen(true);
  };

  const openEditModal = (survey: SurveyItem) => {
    setEditingId(survey.id);
    setFormTitle(survey.title);
    setFormDesc(survey.description);
    setFormAnon(survey.anonymous);
    setFormStart(survey.startDate ? survey.startDate.slice(0, 10).replace(/\./g, '-') : '');
    setFormEnd(survey.endDate ? survey.endDate.slice(0, 10).replace(/\./g, '-') : '');
    setFormQuestionType('3');
    setFormQuestion('강의에 대한 의견을 자유롭게 작성해 주세요.');
    setFormOptions(['']);
    setModalOpen(true);
  };

  const handleSave = async () => {
    if (!selectedCourseId) {
      alert('과목을 먼저 선택해 주세요.');
      return;
    }
    if (!formTitle.trim()) {
      alert('설문 제목을 입력해 주세요.');
      return;
    }
    if (!formQuestion.trim()) {
      alert('질문을 입력해 주세요.');
      return;
    }
    if ((formQuestionType === '1' || formQuestionType === 'M') && formOptions.filter((opt) => opt.trim()).length < 2) {
      alert('선택형은 2개 이상의 선택지가 필요합니다.');
      return;
    }
    setSaving(true);
    try {
      if (editingId) {
        const res = await tutorLmsApi.updateSurvey({
          courseId: selectedCourseId,
          surveyId: editingId,
          surveyName: formTitle.trim(),
          content: formDesc.trim(),
          anonymousYn: formAnon ? 'Y' : 'N',
          startDate: formStart || undefined,
          endDate: formEnd || undefined,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      } else {
        const res = await tutorLmsApi.createSurvey({
          courseId: selectedCourseId,
          surveyName: formTitle.trim(),
          content: formDesc.trim(),
          questionType: formQuestionType,
          question: formQuestion.trim(),
          questionItems: formOptions.filter((opt) => opt.trim()),
          anonymousYn: formAnon ? 'Y' : 'N',
          startDate: formStart || undefined,
          endDate: formEnd || undefined,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      }
      setModalOpen(false);
      await fetchSurveys(selectedCourseId);
    } catch (e) {
      alert(e instanceof Error ? e.message : '설문 저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = (id: number) => {
    if (!selectedCourseId) return;
    if (!confirm('이 설문을 삭제하시겠습니까?')) return;
    void (async () => {
      try {
        const res = await tutorLmsApi.deleteSurvey({ courseId: selectedCourseId, surveyId: id });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        await fetchSurveys(selectedCourseId);
      } catch (e) {
        alert(e instanceof Error ? e.message : '설문 삭제 중 오류가 발생했습니다.');
      }
    })();
  };

  const handleDownloadSummary = () => {
    if (!resultSurvey) return;
    const headers = ['질문번호', '질문', '유형', '응답수', '선택지 통계'];
    const rows = resultStats.map((stat, index) => [
      index + 1,
      stat.question,
      stat.questionType,
      stat.responseCount,
      stat.options.map((opt) => `${opt.label}:${opt.count}`).join(' | '),
    ]);
    downloadCsv(`설문결과_${resultSurvey.title}.csv`, headers, rows);
  };

  if (resultSurvey) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <button onClick={() => setResultSurvey(null)} className="text-sm text-blue-600 hover:underline mb-2">
              ← 목록으로 돌아가기
            </button>
            <h1 className="text-gray-900 mb-1">설문 결과</h1>
            <p className="text-gray-600">{resultSurvey.title}</p>
          </div>
          <button
            onClick={handleDownloadSummary}
            className="flex items-center gap-1.5 px-3 py-2 text-sm text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
          >
            <Download className="w-4 h-4" />
            <span>결과 CSV</span>
          </button>
        </div>

        {loadingResult ? (
          <div className="bg-white rounded-xl border border-gray-200 p-10 text-center text-gray-500 text-sm">결과를 불러오는 중...</div>
        ) : resultStats.length === 0 ? (
          <div className="bg-white rounded-xl border border-gray-200 p-10 text-center text-gray-500 text-sm">결과 데이터가 없습니다.</div>
        ) : (
          <div className="space-y-4">
            {resultStats.map((stat, index) => (
              <div key={stat.questionId} className="bg-white rounded-xl border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-medium text-gray-900">{index + 1}. {stat.question}</h3>
                  <span className="text-xs text-gray-500">{stat.questionType}</span>
                </div>
                <p className="text-sm text-gray-600 mb-3">응답 수: {stat.responseCount}</p>
                {stat.options.length > 0 ? (
                  <div className="space-y-2">
                    {stat.options.map((opt, optIdx) => (
                      <div key={optIdx} className="flex items-center gap-3">
                        <span className="w-48 text-sm text-gray-700 truncate">{opt.label}</span>
                        <div className="flex-1 bg-gray-100 h-4 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-blue-500"
                            style={{
                              width: `${stat.responseCount > 0 ? Math.min(100, Math.round((opt.count / stat.responseCount) * 100)) : 0}%`,
                            }}
                          />
                        </div>
                        <span className="w-10 text-right text-sm text-gray-600">{opt.count}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-gray-500">서술형 응답은 상세 조회 API에서 확인 가능합니다.</div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-gray-900 mb-1">설문 관리</h1>
          <p className="text-gray-600">과목별 설문을 생성/수정/삭제하고 결과를 조회합니다.</p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selectedCourseId ?? ''}
            onChange={(e) => setSelectedCourseId(Number(e.target.value) || null)}
            disabled={loadingCourses || courses.length === 0}
            className="px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-64"
          >
            {courses.map((course) => (
              <option key={course.id} value={course.id}>{course.name}</option>
            ))}
          </select>
          <button
            onClick={openAddModal}
            disabled={!selectedCourseId}
            className="flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm disabled:opacity-50"
          >
            <Plus className="w-5 h-5" />
            <span>새 설문</span>
          </button>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="설문 제목 검색..."
            className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      {loadingList ? (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-500">목록을 불러오는 중...</div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center text-gray-500">등록된 설문이 없습니다.</div>
      ) : (
        <div className="space-y-3">
          {filtered.map((survey) => (
            <div key={survey.id} className="bg-white rounded-xl border border-gray-200 hover:border-blue-200 transition-colors">
              <div className="flex items-center justify-between p-5">
                <div className="flex-1 min-w-0">
                  <h3 className="font-semibold text-gray-900 truncate">{survey.title}</h3>
                  <p className="text-sm text-gray-500 truncate mt-1">{survey.applyText || '적용 정보 없음'}</p>
                  <div className="flex items-center gap-4 text-xs text-gray-400 mt-2">
                    <span>문항 {survey.questionCount}개</span>
                    <span>응답 {survey.responseCount}/{survey.totalCount}</span>
                    <span>{survey.anonymous ? '익명' : '실명'}</span>
                  </div>
                </div>
                <div className="flex items-center gap-2 ml-4 shrink-0">
                  <button
                    onClick={() => { void fetchSurveyResult(survey); }}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-blue-600 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
                  >
                    <BarChart3 className="w-4 h-4" />
                    <span>결과</span>
                  </button>
                  <button onClick={() => openEditModal(survey)} className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors" title="편집">
                    <Edit className="w-4 h-4" />
                  </button>
                  <button onClick={() => handleDelete(survey.id)} className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors" title="삭제">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {modalOpen && (
        <div className="fixed inset-0 bg-gray-900/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] flex flex-col">
            <div className="flex items-center justify-between p-5 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">{editingId ? '설문 편집' : '새 설문 만들기'}</h3>
              <button onClick={() => setModalOpen(false)} className="p-2 hover:bg-gray-100 rounded-lg">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">설문 제목</label>
                <input
                  value={formTitle}
                  onChange={(e) => setFormTitle(e.target.value)}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">설명</label>
                <textarea
                  value={formDesc}
                  onChange={(e) => setFormDesc(e.target.value)}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-y min-h-[70px]"
                  rows={2}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">시작일</label>
                  <input
                    type="date"
                    value={formStart}
                    onChange={(e) => setFormStart(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">종료일</label>
                  <input
                    type="date"
                    value={formEnd}
                    onChange={(e) => setFormEnd(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>
              {!editingId && (
                <>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">질문 유형</label>
                      <select
                        value={formQuestionType}
                        onChange={(e) => setFormQuestionType(e.target.value as '1' | 'M' | '2' | '3')}
                        className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        <option value="3">서술형</option>
                        <option value="2">단답형</option>
                        <option value="1">단일선택</option>
                        <option value="M">복수선택</option>
                      </select>
                    </div>
                    <div className="flex items-end">
                      <label className="flex items-center gap-2 text-sm text-gray-700">
                        <input
                          type="checkbox"
                          checked={formAnon}
                          onChange={(e) => setFormAnon(e.target.checked)}
                          className="rounded border-gray-300"
                        />
                        익명 설문
                      </label>
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">질문</label>
                    <textarea
                      value={formQuestion}
                      onChange={(e) => setFormQuestion(e.target.value)}
                      className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-y"
                      rows={2}
                    />
                  </div>
                  {(formQuestionType === '1' || formQuestionType === 'M') && (
                    <div className="space-y-2">
                      <label className="block text-sm font-medium text-gray-700">선택지</label>
                      {formOptions.map((opt, idx) => (
                        <input
                          key={idx}
                          value={opt}
                          onChange={(e) => {
                            const next = [...formOptions];
                            next[idx] = e.target.value;
                            setFormOptions(next);
                          }}
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                          placeholder={`선택지 ${idx + 1}`}
                        />
                      ))}
                      <button
                        onClick={() => setFormOptions((prev) => [...prev, ''])}
                        type="button"
                        className="text-sm text-blue-600 hover:underline"
                      >
                        + 선택지 추가
                      </button>
                    </div>
                  )}
                </>
              )}
            </div>
            <div className="flex items-center justify-end gap-3 p-5 border-t border-gray-200">
              <button onClick={() => setModalOpen(false)} className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors">
                취소
              </button>
              <button
                onClick={() => { void handleSave(); }}
                disabled={saving}
                className="flex items-center gap-2 px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
              >
                <Save className="w-4 h-4" />
                <span>{saving ? '저장 중...' : editingId ? '수정' : '생성'}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
