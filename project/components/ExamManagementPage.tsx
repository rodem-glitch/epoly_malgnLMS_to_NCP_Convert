import { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Search, ClipboardList, Clock, FileQuestion, X, Save, Check, Loader2 } from 'lucide-react';
import { tutorLmsApi, type TutorQuestionBankRow, type TutorQuestionCategoryRow, type TutorExamTemplateRow } from '../api/tutorLmsApi';

// 프론트엔드용 시험 타입
export interface Exam {
  id: string;
  title: string;
  description: string;
  questionIds: string[];
  duration: number;
  totalPoints: number;
  passingScore: number;
  shuffleQuestions: boolean;
  showResults: boolean;
  createdAt: string;
}

// 프론트엔드용 문제 타입 (간소화)
interface Question {
  id: string;
  type: 'multiple_choice' | 'short_answer' | 'ox';
  title: string;
  content?: string;
  points: number;
  categoryId?: string;
  choices?: Array<{ id: string; text: string }>;
}

const normalizeNumber = (value: unknown, defaultValue = 0): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : defaultValue;
};

const getRowField = (row: TutorQuestionBankRow, lowerKey: string, upperKey: string): unknown => {
  const raw = row as unknown as Record<string, unknown>;
  return raw[lowerKey] ?? raw[upperKey];
};

// 서버 데이터를 프론트엔드 형식으로 변환
const serverExamToLocal = (row: TutorExamTemplateRow): Exam => ({
  id: String(row.id),
  title: row.exam_nm,
  description: row.content || '',
  questionIds: row.range_idx ? row.range_idx.split(',').filter(Boolean) : [],
  duration: row.exam_time || 60,
  totalPoints: row.total_points || 0,
  passingScore: row.assign1 || 60,
  shuffleQuestions: row.shuffle_yn === 'Y',
  showResults: true,
  createdAt: row.reg_date || new Date().toISOString(),
});

const serverQuestionToLocal = (row: TutorQuestionBankRow): Question => {
  const questionType = normalizeNumber(getRowField(row, 'question_type', 'QUESTION_TYPE'));
  let type: 'multiple_choice' | 'short_answer' | 'ox' = 'multiple_choice';
  if (questionType === 3 || questionType === 4) type = 'short_answer';
  const choices: Array<{ id: string; text: string }> = [];
  if (questionType === 1 || questionType === 2) {
    const itemCount = Math.max(1, Math.min(5, normalizeNumber(getRowField(row, 'item_cnt', 'ITEM_CNT'), 4)));
    for (let i = 1; i <= itemCount; i++) {
      // 왜: 운영 환경 JSON 키 대소문자 차이를 흡수해 선택지 누락을 막습니다.
      const itemText = String(getRowField(row, `item${i}`, `ITEM${i}`) ?? '');
      choices.push({ id: String(i), text: itemText });
    }
  }
  
  return {
    id: String(getRowField(row, 'id', 'ID') ?? row.id),
    type,
    title: String(getRowField(row, 'question', 'QUESTION') ?? ''),
    content: String(getRowField(row, 'question_text', 'QUESTION_TEXT') ?? ''),
    points: Math.max(1, normalizeNumber(getRowField(row, 'score', 'SCORE'), 5)),
    categoryId: normalizeNumber(getRowField(row, 'category_id', 'CATEGORY_ID')) > 0
      ? String(getRowField(row, 'category_id', 'CATEGORY_ID'))
      : undefined,
    choices: type === 'multiple_choice' ? choices : undefined,
  };
};

// 시험 목록을 외부에서도 사용할 수 있도록 export (API 버전)
export const getExamList = async (): Promise<Exam[]> => {
  try {
    const res = await tutorLmsApi.getExamTemplates();
    if (res.rst_code === '0000') {
      return (res.rst_data ?? []).map(serverExamToLocal);
    }
    return [];
  } catch {
    return [];
  }
};

// 시험 상세 조회
export const getExamById = async (examId: string): Promise<Exam | null> => {
  try {
    const exams = await getExamList();
    return exams.find(e => e.id === examId) || null;
  } catch {
    return null;
  }
};

export function ExamManagementPage() {
  const [exams, setExams] = useState<Exam[]>([]);
  const [questions, setQuestions] = useState<Question[]>([]);
  const [categories, setCategories] = useState<TutorQuestionCategoryRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 검색
  const [searchQuery, setSearchQuery] = useState('');
  
  // 모달 상태
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingExam, setEditingExam] = useState<Exam | null>(null);
  
  // 문제 선택 모달
  const [isQuestionModalOpen, setIsQuestionModalOpen] = useState(false);
  const [selectedQuestionIds, setSelectedQuestionIds] = useState<Set<string>>(new Set());
  const [questionFilterCategory, setQuestionFilterCategory] = useState<string>('');
  const [questionsLoading, setQuestionsLoading] = useState(false);
  
  // 폼 상태
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    questionIds: [] as string[],
    duration: 60,
    passingScore: 60,
    shuffleQuestions: false,
    showResults: true,
  });

  // 시험 목록 로드
  const loadExams = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await tutorLmsApi.getExamTemplates();
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      
      const localExams = (res.rst_data ?? []).map(serverExamToLocal);
      setExams(localExams);
    } catch (e) {
      setError(e instanceof Error ? e.message : '시험 목록을 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadExams();
  }, []);

  // 카테고리 로드
  const loadCategories = async () => {
    try {
      const res = await tutorLmsApi.getQuestionCategories();
      if (res.rst_code === '0000') {
        setCategories(res.rst_data ?? []);
      }
    } catch (e) {
      console.error('카테고리 로드 실패:', e);
    }
  };

  // 문제 목록 로드
  const loadQuestions = async (categoryId?: string) => {
    try {
      setQuestionsLoading(true);
      const res = await tutorLmsApi.getQuestionBankList({
        categoryId: categoryId ? Number(categoryId) : undefined,
        limit: 200,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      
      const localQuestions = (res.rst_data ?? []).map(serverQuestionToLocal);
      // 왜: 출제 모달에서 선택지가 비어 보이는 이슈를 운영 중 빠르게 확인하기 위한 최소 로그입니다.
      const emptyChoiceQuestions = localQuestions.filter(
        q => q.type === 'multiple_choice' && (q.choices ?? []).every(choice => !choice.text.trim())
      );
      if (emptyChoiceQuestions.length > 0) {
        console.warn(`[ExamManagementPage] 선택지 비어있는 객관식 문항 수=${emptyChoiceQuestions.length}`);
      }
      setQuestions(localQuestions);
    } catch (e) {
      setError(e instanceof Error ? e.message : '문제 목록을 불러오는 중 오류가 발생했습니다.');
    } finally {
      setQuestionsLoading(false);
    }
  };

  // 필터링된 시험 목록
  const filteredExams = exams.filter(e =>
    !searchQuery || e.title.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // 필터링된 문제 목록
  const filteredQuestions = questions.filter(q =>
    !questionFilterCategory || q.categoryId === questionFilterCategory
  );

  const openAddModal = () => {
    setEditingExam(null);
    setFormData({
      title: '',
      description: '',
      questionIds: [],
      duration: 60,
      passingScore: 60,
      shuffleQuestions: false,
      showResults: true,
    });
    setIsModalOpen(true);
  };

  const openEditModal = (exam: Exam) => {
    setEditingExam(exam);
    setFormData({
      title: exam.title,
      description: exam.description,
      questionIds: exam.questionIds,
      duration: exam.duration,
      passingScore: exam.passingScore,
      shuffleQuestions: exam.shuffleQuestions,
      showResults: exam.showResults,
    });
    setIsModalOpen(true);
  };

  const handleSave = async () => {
    if (!formData.title.trim()) return;

    try {
      setSaving(true);
      setError(null);

      if (editingExam) {
        // 수정
        const res = await tutorLmsApi.updateExamTemplate({
          id: Number(editingExam.id),
          examName: formData.title.trim(),
          examTime: formData.duration,
          questionCnt: formData.questionIds.length,
          shuffleYn: formData.shuffleQuestions,
          passingScore: formData.passingScore,
          questionIds: formData.questionIds,
          content: formData.description.trim(),
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      } else {
        // 추가
        const res = await tutorLmsApi.createExamTemplate({
          examName: formData.title.trim(),
          examTime: formData.duration,
          questionCnt: formData.questionIds.length,
          shuffleYn: formData.shuffleQuestions,
          passingScore: formData.passingScore,
          questionIds: formData.questionIds,
          content: formData.description.trim(),
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      }

      await loadExams();
      setIsModalOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('이 시험을 삭제하시겠습니까?')) return;

    try {
      setSaving(true);
      const res = await tutorLmsApi.deleteExamTemplate({ id: Number(id) });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      await loadExams();
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  // 문제 선택 모달 열기
  const openQuestionModal = async () => {
    setSelectedQuestionIds(new Set(formData.questionIds));
    setIsQuestionModalOpen(true);
    await Promise.all([loadCategories(), loadQuestions()]);
  };

  // 카테고리 필터 변경
  const handleCategoryFilterChange = async (categoryId: string) => {
    setQuestionFilterCategory(categoryId);
    await loadQuestions(categoryId || undefined);
  };

  // 문제 선택 토글
  const toggleQuestion = (id: string) => {
    setSelectedQuestionIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // 문제 선택 확인
  const confirmQuestionSelection = () => {
    setFormData(prev => ({
      ...prev,
      questionIds: Array.from(selectedQuestionIds),
    }));
    setIsQuestionModalOpen(false);
  };

  // 선택된 문제 정보
  const getSelectedQuestionsInfo = () => {
    const selected = formData.questionIds.map(id => questions.find(q => q.id === id)).filter(Boolean) as Question[];
    const totalPoints = selected.reduce((sum, q) => sum + q.points, 0);
    return { count: selected.length, totalPoints };
  };

  const questionInfo = getSelectedQuestionsInfo();

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">시험관리</h2>
          <p className="text-gray-500 mt-1">시험을 생성하고 관리합니다.</p>
        </div>
        <button
          onClick={openAddModal}
          disabled={saving}
          className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50"
        >
          <Plus className="w-5 h-5" />
          <span>시험 생성</span>
        </button>
      </div>

      {/* 에러 메시지 */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
          <button onClick={() => setError(null)} className="ml-2 underline">닫기</button>
        </div>
      )}

      {/* 검색 */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
        <div className="relative max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="시험 검색..."
            className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
      </div>

      {/* 시험 목록 */}
      {loading ? (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm flex items-center justify-center py-16 text-gray-400">
          <Loader2 className="w-8 h-8 animate-spin mr-2" />
          <span>불러오는 중...</span>
        </div>
      ) : (
        <div className="grid gap-4">
          {filteredExams.length > 0 ? (
            filteredExams.map(exam => (
              <div
                key={exam.id}
                className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <h3 className="text-lg font-semibold text-gray-900">{exam.title}</h3>
                    {exam.description && (
                      <p className="text-gray-500 mt-1">{exam.description}</p>
                    )}
                    <div className="flex items-center gap-6 mt-4 text-sm text-gray-600">
                      <div className="flex items-center gap-2">
                        <FileQuestion className="w-4 h-4 text-indigo-500" />
                        <span>{exam.questionIds.length}문제</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Clock className="w-4 h-4 text-blue-500" />
                        <span>{exam.duration}분</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-gray-500">합격:</span>
                        <span className="font-medium">{exam.passingScore}점</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 ml-4">
                    <button
                      onClick={() => openEditModal(exam)}
                      disabled={saving}
                      className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      title="수정"
                    >
                      <Edit className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => handleDelete(exam.id)}
                      disabled={saving}
                      className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      title="삭제"
                    >
                      <Trash2 className="w-5 h-5" />
                    </button>
                  </div>
                </div>
              </div>
            ))
          ) : (
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm text-center py-16 text-gray-400">
              <ClipboardList className="w-12 h-12 mx-auto mb-4 opacity-50" />
              <p>등록된 시험이 없습니다.</p>
              <button
                onClick={openAddModal}
                className="mt-4 text-indigo-600 hover:underline"
              >
                + 첫 번째 시험 생성하기
              </button>
            </div>
          )}
        </div>
      )}

      {/* 시험 생성/수정 모달 */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setIsModalOpen(false)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">
                {editingExam ? '시험 수정' : '시험 생성'}
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* 시험 제목 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  시험 제목 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                  placeholder="예: 중간고사"
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              {/* 시험 설명 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">설명</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                  placeholder="시험에 대한 설명을 입력하세요"
                  rows={3}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                />
              </div>

              {/* 문제 선택 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">출제 문제</label>
                <button
                  type="button"
                  onClick={openQuestionModal}
                  className="w-full flex items-center justify-between px-4 py-3 border-2 border-dashed border-gray-300 rounded-lg text-gray-500 hover:border-indigo-400 hover:text-indigo-600 transition-colors"
                >
                  <span>
                    {formData.questionIds.length > 0
                      ? `${questionInfo.count}문제 선택됨 (총 ${questionInfo.totalPoints}점)`
                      : '문제은행에서 문제 선택'}
                  </span>
                  <FileQuestion className="w-5 h-5" />
                </button>
              </div>

              {/* 시험 시간 & 합격 점수 */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">시험 시간 (분)</label>
                  <input
                    type="number"
                    value={formData.duration}
                    onChange={(e) => setFormData(prev => ({ ...prev, duration: parseInt(e.target.value) || 0 }))}
                    min={1}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">합격 점수</label>
                  <input
                    type="number"
                    value={formData.passingScore}
                    onChange={(e) => setFormData(prev => ({ ...prev, passingScore: parseInt(e.target.value) || 0 }))}
                    min={0}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>

              {/* 옵션 */}
              <div className="space-y-3">
                <label className="flex items-center gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.shuffleQuestions}
                    onChange={(e) => setFormData(prev => ({ ...prev, shuffleQuestions: e.target.checked }))}
                    className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
                  />
                  <span className="text-sm text-gray-700">문제 순서 랜덤 배치</span>
                </label>
                <label className="flex items-center gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.showResults}
                    onChange={(e) => setFormData(prev => ({ ...prev, showResults: e.target.checked }))}
                    className="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
                  />
                  <span className="text-sm text-gray-700">시험 종료 후 결과 공개</span>
                </label>
              </div>
            </div>

            <div className="sticky bottom-0 bg-white border-t border-gray-200 px-6 py-4 flex gap-3">
              <button
                onClick={() => setIsModalOpen(false)}
                className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleSave}
                disabled={!formData.title.trim() || saving}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition-colors"
              >
                {saving ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                <span>저장</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 문제 선택 모달 */}
      {isQuestionModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setIsQuestionModalOpen(false)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-3xl mx-4 max-h-[85vh] overflow-hidden flex flex-col">
            <div className="flex-shrink-0 border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">
                문제 선택 ({selectedQuestionIds.size}개 선택됨)
              </h3>
              <button
                onClick={() => setIsQuestionModalOpen(false)}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* 필터 */}
            <div className="flex-shrink-0 px-6 py-3 border-b border-gray-100 bg-gray-50">
              <select
                value={questionFilterCategory}
                onChange={(e) => handleCategoryFilterChange(e.target.value)}
                className="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="">전체 카테고리</option>
                {categories.map(c => (
                  <option key={c.id} value={c.id}>{c.category_nm}</option>
                ))}
              </select>
            </div>

            {/* 문제 목록 */}
            <div className="flex-1 overflow-y-auto">
              {questionsLoading ? (
                <div className="flex items-center justify-center py-12 text-gray-400">
                  <Loader2 className="w-6 h-6 animate-spin mr-2" />
                  <span>문제 불러오는 중...</span>
                </div>
              ) : filteredQuestions.length > 0 ? (
                <div className="divide-y divide-gray-100">
                  {filteredQuestions.map(question => (
                    <div
                      key={question.id}
                      className={`px-6 py-4 cursor-pointer transition-colors ${
                        selectedQuestionIds.has(question.id)
                          ? 'bg-indigo-50'
                          : 'hover:bg-gray-50'
                      }`}
                      onClick={() => toggleQuestion(question.id)}
                    >
                      <div className="flex items-start gap-4">
                        <div className={`flex-shrink-0 w-6 h-6 rounded border-2 flex items-center justify-center mt-0.5 ${
                          selectedQuestionIds.has(question.id)
                            ? 'border-indigo-500 bg-indigo-500 text-white'
                            : 'border-gray-300'
                        }`}>
                          {selectedQuestionIds.has(question.id) && <Check className="w-4 h-4" />}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="font-medium text-gray-900">{question.title}</div>
                          {question.content && (
                            <div className="text-sm text-gray-500 mt-1 truncate">{question.content}</div>
                          )}
                          {question.type === 'multiple_choice' && question.choices && question.choices.some(choice => choice.text.trim()) && (
                            <div className="mt-2 space-y-1">
                              {question.choices
                                .filter(choice => choice.text.trim())
                                .slice(0, 5)
                                .map(choice => (
                                  <div key={`question-choice-${question.id}-${choice.id}`} className="text-xs text-gray-500 truncate">
                                    {choice.id}. {choice.text}
                                  </div>
                                ))}
                            </div>
                          )}
                          <div className="flex items-center gap-3 mt-2 text-xs text-gray-500">
                            <span className={`px-2 py-0.5 rounded-full ${
                              question.type === 'multiple_choice' ? 'bg-blue-100 text-blue-700' :
                              question.type === 'short_answer' ? 'bg-green-100 text-green-700' :
                              'bg-purple-100 text-purple-700'
                            }`}>
                              {question.type === 'multiple_choice' ? '객관식' : question.type === 'short_answer' ? '주관식' : 'OX형'}
                            </span>
                            <span>{question.points}점</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-12 text-gray-400">
                  <FileQuestion className="w-10 h-10 mx-auto mb-3 opacity-50" />
                  <p>문제가 없습니다.</p>
                  <p className="text-sm mt-1">문제은행에서 먼저 문제를 추가해주세요.</p>
                </div>
              )}
            </div>

            <div className="flex-shrink-0 border-t border-gray-200 px-6 py-4 flex gap-3">
              <button
                onClick={() => setIsQuestionModalOpen(false)}
                className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={confirmQuestionSelection}
                className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                <Check className="w-4 h-4" />
                <span>선택 완료</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
