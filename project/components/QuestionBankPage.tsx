import { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Search, FileQuestion, X, Save, Loader2, Eye, EyeOff, User } from 'lucide-react';
import { tutorLmsApi, type TutorQuestionCategoryRow, type TutorQuestionBankRow } from '../api/tutorLmsApi';

// 문제 타입
export type QuestionType = 'multiple_choice' | 'short_answer' | 'ox';

export interface QuestionChoice {
  id: string;
  text: string;
  isCorrect: boolean;
}

export interface Question {
  id: string;
  categoryId: string | null;
  type: QuestionType;
  title: string;
  content: string;
  choices?: QuestionChoice[];
  correctAnswer?: string;
  points: number;
  isPublic: boolean; // 왜: 공개/비공개 여부 — 비공개 문제는 시험 출제 시에만 사용
  creatorId?: string; // 왜: 문제 작성자 ID — 백엔드에서 reg_id로 내려옴
  createdAt: string;
}

const buildDefaultChoices = (): QuestionChoice[] => [
  { id: '1', text: '', isCorrect: false },
  { id: '2', text: '', isCorrect: false },
  { id: '3', text: '', isCorrect: false },
  { id: '4', text: '', isCorrect: false },
];

const normalizeNumber = (value: unknown, defaultValue = 0): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : defaultValue;
};

const getRowField = (row: TutorQuestionBankRow, lowerKey: string, upperKey: string): unknown => {
  const raw = row as unknown as Record<string, unknown>;
  return raw[lowerKey] ?? raw[upperKey];
};

// 서버 question_type을 프론트엔드 타입으로 변환
const serverTypeToLocal = (serverTypeRaw: unknown): QuestionType => {
  const serverType = normalizeNumber(serverTypeRaw);
  if (serverType === 1) return 'multiple_choice'; // 단일선택
  if (serverType === 2) return 'multiple_choice'; // 다중선택도 객관식으로 처리
  if (serverType === 3) return 'short_answer';    // 단답형
  if (serverType === 4) return 'short_answer';    // 서술형도 주관식으로 처리
  return 'multiple_choice';
};

// 프론트엔드 타입을 서버 question_type으로 변환
const localTypeToServer = (localType: QuestionType): number => {
  if (localType === 'multiple_choice') return 1; // 단일선택
  if (localType === 'short_answer') return 3;    // 단답형
  if (localType === 'ox') return 1;              // OX도 단일선택으로
  return 1;
};

// 서버 데이터를 프론트엔드 형식으로 변환
const serverToLocal = (row: TutorQuestionBankRow): Question => {
  const questionType = normalizeNumber(getRowField(row, 'question_type', 'QUESTION_TYPE'));
  const itemCount = Math.max(1, Math.min(5, normalizeNumber(getRowField(row, 'item_cnt', 'ITEM_CNT'), 4)));
  const answerRaw = String(getRowField(row, 'answer', 'ANSWER') ?? '');
  const answerParts = answerRaw.split(/\|\|?/).map(part => part.trim()).filter(Boolean);
  const score = normalizeNumber(getRowField(row, 'score', 'SCORE'), 5);
  const type = serverTypeToLocal(questionType);
  const choices: QuestionChoice[] = [];
  
  // 객관식인 경우 보기 변환
  if (questionType === 1 || questionType === 2) {
    for (let i = 1; i <= itemCount; i++) {
      // 왜: 운영 환경별 JSON 키 케이스(item1/ITEM1)가 달라질 수 있어 둘 다 확인합니다.
      const itemText = String(getRowField(row, `item${i}`, `ITEM${i}`) ?? '');
      choices.push({
        id: String(i),
        text: itemText,
        isCorrect: answerParts.includes(String(i)),
      });
    }
  }

  // 왜: 백엔드에서 is_public 필드가 아직 없을 수 있으므로 기본값 true
  const isPublicRaw = getRowField(row, 'is_public', 'IS_PUBLIC');
  const isPublic = isPublicRaw === undefined || isPublicRaw === null ? true : Boolean(Number(isPublicRaw));

  return {
    id: String(getRowField(row, 'id', 'ID') ?? row.id),
    categoryId: normalizeNumber(getRowField(row, 'category_id', 'CATEGORY_ID')) > 0
      ? String(getRowField(row, 'category_id', 'CATEGORY_ID'))
      : null,
    type,
    title: String(getRowField(row, 'question', 'QUESTION') ?? ''),
    content: String(getRowField(row, 'question_text', 'QUESTION_TEXT') ?? ''),
    choices: type === 'multiple_choice' ? choices : undefined,
    correctAnswer: type !== 'multiple_choice' ? String(getRowField(row, 'answer', 'ANSWER') ?? '') : undefined,
    points: score > 0 ? score : 5,
    isPublic,
    creatorId: String(getRowField(row, 'reg_id', 'REG_ID') ?? ''),
    createdAt: String(getRowField(row, 'reg_date', 'REG_DATE') ?? new Date().toISOString()),
  };
};

const questionTypeLabels: Record<QuestionType, string> = {
  multiple_choice: '객관식',
  short_answer: '주관식',
  ox: 'OX형',
};

const questionTypeColors: Record<QuestionType, string> = {
  multiple_choice: 'bg-blue-100 text-blue-700',
  short_answer: 'bg-green-100 text-green-700',
  ox: 'bg-purple-100 text-purple-700',
};

export function QuestionBankPage() {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [categories, setCategories] = useState<TutorQuestionCategoryRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 필터
  const [searchQuery, setSearchQuery] = useState('');
  const [filterCategory, setFilterCategory] = useState<string>('');
  const [filterType, setFilterType] = useState<QuestionType | ''>('');
  const [filterMineOnly, setFilterMineOnly] = useState(false);
  
  // 모달 상태
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<Question | null>(null);
  
  // 폼 상태
  const [formData, setFormData] = useState({
    categoryId: '' as string | null,
    type: 'multiple_choice' as QuestionType,
    title: '',
    content: '',
    points: 5,
    isPublic: true,
    choices: [
      { id: '1', text: '', isCorrect: false },
      { id: '2', text: '', isCorrect: false },
      { id: '3', text: '', isCorrect: false },
      { id: '4', text: '', isCorrect: false },
    ] as QuestionChoice[],
    correctAnswer: '',
  });

  // 카테고리 및 문제 목록 로드
  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);

      // 카테고리 목록 먼저 로드
      const catRes = await tutorLmsApi.getQuestionCategories();
      if (catRes.rst_code === '0000') {
        setCategories(catRes.rst_data ?? []);
      }

      // 문제 목록 로드
      const questionType = filterType === 'multiple_choice' ? 1 : filterType === 'short_answer' ? 3 : undefined;
      const res = await tutorLmsApi.getQuestionBankList({
        categoryId: filterCategory ? Number(filterCategory) : undefined,
        questionType,
        keyword: searchQuery || undefined,
        limit: 100,
        // TODO: 백엔드에 mineOnly 파라미터 추가 후 활성화
        // mineOnly: filterMineOnly || undefined,
      });

      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      
      const localQuestions = (res.rst_data ?? []).map(serverToLocal);
      // 왜: 객관식 문항의 선택지가 비어 보이는 현상을 빠르게 추적하기 위해 개수만 로그로 남깁니다.
      const emptyChoiceQuestions = localQuestions.filter(
        q => q.type === 'multiple_choice' && (q.choices ?? []).every(choice => !choice.text.trim())
      );
      if (emptyChoiceQuestions.length > 0) {
        console.warn(`[QuestionBankPage] 객관식 선택지 비어있는 문항 수=${emptyChoiceQuestions.length}`);
      }
      setQuestions(localQuestions);
    } catch (e) {
      setError(e instanceof Error ? e.message : '문제를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [searchQuery, filterCategory, filterType, filterMineOnly]);

  // 왜: 백엔드가 mineOnly 필터를 지원하기 전까지 클라이언트에서 필터링합니다.
  // 현재 로그인한 사용자 ID는 tutorLmsApi에서 가져와야 하지만, 아직 미구현이므로
  // creatorId가 있는 경우에만 프론트엔드 필터가 작동합니다.
  // TODO: 백엔드에서 mineOnly 파라미터 지원 시 이 필터 제거
  const displayQuestions = filterMineOnly
    ? questions.filter(q => {
        // creatorId가 없으면(백엔드 미지원) 모두 표시
        if (!q.creatorId) return true;
        // TODO: 실제 로그인 사용자 ID와 비교
        // 현재는 백엔드에서 mineOnly 필터링을 해야 정상 동작
        return true;
      })
    : questions;

  const openAddModal = () => {
    setEditingQuestion(null);
    setFormData({
      categoryId: null,
      type: 'multiple_choice',
      title: '',
      content: '',
      points: 5,
      isPublic: true,
      choices: buildDefaultChoices(),
      correctAnswer: '',
    });
    setIsModalOpen(true);
  };

  const openEditModal = (question: Question) => {
    const editChoices = question.choices && question.choices.length > 0
      ? question.choices
      : buildDefaultChoices();
    setEditingQuestion(question);
    setFormData({
      categoryId: question.categoryId,
      type: question.type,
      title: question.title,
      content: question.content,
      points: question.points,
      isPublic: question.isPublic,
      choices: editChoices,
      correctAnswer: question.correctAnswer || '',
    });
    setIsModalOpen(true);
  };

  const handleSave = async () => {
    if (!formData.title.trim()) return;

    try {
      setSaving(true);
      setError(null);

      // 정답 계산
      let answer = '';
      if (formData.type === 'multiple_choice') {
        const correctIdx = formData.choices.findIndex(c => c.isCorrect);
        answer = correctIdx >= 0 ? String(correctIdx + 1) : '1';
      } else if (formData.type === 'ox') {
        answer = formData.correctAnswer;
      } else {
        answer = formData.correctAnswer;
      }

      // 객관식 보기 배열
      const items = formData.type === 'multiple_choice' 
        // 왜: 보기 순서(정답 인덱스)를 유지해야 하므로 빈 값도 포함해 원본 순서대로 전송합니다.
        ? formData.choices.map(c => c.text.trim())
        : undefined;

      if (editingQuestion) {
        // 수정
        const res = await tutorLmsApi.updateQuestion({
          id: Number(editingQuestion.id),
          categoryId: formData.categoryId ? Number(formData.categoryId) : undefined,
          questionType: localTypeToServer(formData.type),
          question: formData.title.trim(),
          questionText: formData.content.trim(),
          answer,
          points: formData.points,
          items,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      } else {
        // 추가
        const res = await tutorLmsApi.createQuestion({
          categoryId: formData.categoryId ? Number(formData.categoryId) : undefined,
          questionType: localTypeToServer(formData.type),
          question: formData.title.trim(),
          questionText: formData.content.trim(),
          answer,
          points: formData.points,
          items,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      }

      await loadData();
      setIsModalOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('이 문제를 삭제하시겠습니까?')) return;

    try {
      setSaving(true);
      const res = await tutorLmsApi.deleteQuestion({ id: Number(id) });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      await loadData();
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const getCategoryName = (categoryId: string | null) => {
    if (!categoryId) return '-';
    const category = categories.find(c => String(c.id) === categoryId);
    return category?.category_nm || '-';
  };

  const updateChoice = (index: number, field: 'text' | 'isCorrect', value: string | boolean) => {
    setFormData(prev => ({
      ...prev,
      choices: prev.choices.map((c, i) => {
        if (i === index) {
          return { ...c, [field]: value };
        }
        if (field === 'isCorrect' && value === true) {
          return { ...c, isCorrect: false };
        }
        return c;
      }),
    }));
  };

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">문제은행</h2>
          <p className="text-gray-500 mt-1">시험에 사용할 문제를 관리합니다.</p>
        </div>
        <button
          onClick={openAddModal}
          disabled={saving}
          className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50"
        >
          <Plus className="w-5 h-5" />
          <span>문제 추가</span>
        </button>
      </div>

      {/* 에러 메시지 */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
        </div>
      )}

      {/* 필터 */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-4">
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="문제 검색..."
                className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
          </div>
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value)}
            className="px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">전체 카테고리</option>
            {categories.map(c => (
              <option key={c.id} value={c.id}>{c.category_nm}</option>
            ))}
          </select>
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value as QuestionType | '')}
            className="px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">전체 유형</option>
            <option value="multiple_choice">객관식</option>
            <option value="short_answer">주관식</option>
            <option value="ox">OX형</option>
          </select>
          <button
            onClick={() => setFilterMineOnly(prev => !prev)}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-lg border transition-colors text-sm whitespace-nowrap ${
              filterMineOnly
                ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
                : 'border-gray-300 text-gray-600 hover:bg-gray-50'
            }`}
          >
            <User className="w-4 h-4" />
            <span>내 문제만</span>
          </button>
        </div>
      </div>

      {/* 문제 목록 */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16 text-gray-400">
            <Loader2 className="w-8 h-8 animate-spin mr-2" />
            <span>불러오는 중...</span>
          </div>
        ) : displayQuestions.length > 0 ? (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-4 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">문제</th>
                <th className="px-6 py-4 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-28">유형</th>
                <th className="px-6 py-4 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-32">카테고리</th>
                <th className="px-6 py-4 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">배점</th>
                <th className="px-6 py-4 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">공개</th>
                <th className="px-6 py-4 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-24">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {displayQuestions.map(question => (
                <tr key={question.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4">
                    <div className="font-medium text-gray-900">{question.title}</div>
                    {question.content && (
                      <div className="text-sm text-gray-500 truncate max-w-md">{question.content}</div>
                    )}
                    {question.type === 'multiple_choice' && question.choices && question.choices.some(choice => choice.text.trim()) && (
                      <div className="mt-2 space-y-1">
                        {question.choices
                          .filter(choice => choice.text.trim())
                          .slice(0, 5)
                          .map(choice => (
                            <div key={`choice-preview-${question.id}-${choice.id}`} className="text-xs text-gray-500 truncate max-w-md">
                              {choice.id}. {choice.text}
                            </div>
                          ))}
                      </div>
                    )}
                  </td>
                  <td className="px-6 py-4 text-center">
                    <span className={`px-2.5 py-1 text-xs font-medium rounded-full ${questionTypeColors[question.type]}`}>
                      {questionTypeLabels[question.type]}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-center text-sm text-gray-600">
                    {getCategoryName(question.categoryId)}
                  </td>
                  <td className="px-6 py-4 text-center text-sm font-medium text-gray-900">
                    {question.points}점
                  </td>
                  <td className="px-6 py-4 text-center">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium rounded-full ${
                      question.isPublic
                        ? 'bg-emerald-100 text-emerald-700'
                        : 'bg-gray-100 text-gray-500'
                    }`}>
                      {question.isPublic ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                      {question.isPublic ? '공개' : '비공개'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-center">
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() => openEditModal(question)}
                        className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                        title="수정"
                        disabled={saving}
                      >
                        <Edit className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDelete(question.id)}
                        className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                        title="삭제"
                        disabled={saving}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="text-center py-16 text-gray-400">
            <FileQuestion className="w-12 h-12 mx-auto mb-4 opacity-50" />
            <p>등록된 문제가 없습니다.</p>
            <button
              onClick={openAddModal}
              className="mt-4 text-indigo-600 hover:underline"
            >
              + 첫 번째 문제 추가하기
            </button>
          </div>
        )}
      </div>

      {/* 통계 */}
      <div className="text-sm text-gray-500">
        총 {displayQuestions.length}개 문제{filterMineOnly && displayQuestions.length !== questions.length ? ` (전체 ${questions.length}개)` : ''}
      </div>

      {/* 문제 추가/수정 모달 */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setIsModalOpen(false)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">
                {editingQuestion ? '문제 수정' : '문제 추가'}
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* 문제 유형 선택 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">문제 유형</label>
                <div className="flex gap-3">
                  {(['multiple_choice', 'short_answer', 'ox'] as QuestionType[]).map(type => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => setFormData(prev => ({ ...prev, type }))}
                      className={`flex-1 py-3 px-4 rounded-lg border-2 transition-colors ${
                        formData.type === type
                          ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      {questionTypeLabels[type]}
                    </button>
                  ))}
                </div>
              </div>

              {/* 카테고리 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">카테고리</label>
                <select
                  value={formData.categoryId || ''}
                  onChange={(e) => setFormData(prev => ({ ...prev, categoryId: e.target.value || null }))}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="">카테고리 없음</option>
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.category_nm}</option>
                  ))}
                </select>
              </div>

              {/* 문제 제목 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  문제 제목 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => setFormData(prev => ({ ...prev, title: e.target.value }))}
                  placeholder="문제 제목을 입력하세요"
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              {/* 문제 내용 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">문제 내용</label>
                <textarea
                  value={formData.content}
                  onChange={(e) => setFormData(prev => ({ ...prev, content: e.target.value }))}
                  placeholder="문제 내용을 입력하세요"
                  rows={4}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                />
              </div>

              {/* 객관식 선택지 */}
              {formData.type === 'multiple_choice' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">선택지</label>
                  <div className="space-y-3">
                    {formData.choices.map((choice, index) => (
                      <div key={choice.id} className="flex items-center gap-3">
                        <button
                          type="button"
                          onClick={() => updateChoice(index, 'isCorrect', true)}
                          className={`flex-shrink-0 w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors ${
                            choice.isCorrect
                              ? 'border-green-500 bg-green-500 text-white'
                              : 'border-gray-300 hover:border-green-400'
                          }`}
                        >
                          {choice.isCorrect && <span className="text-xs">✓</span>}
                        </button>
                        <span className="text-sm text-gray-500 w-6">{index + 1}.</span>
                        <input
                          type="text"
                          value={choice.text}
                          onChange={(e) => updateChoice(index, 'text', e.target.value)}
                          placeholder={`선택지 ${index + 1}`}
                          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                      </div>
                    ))}
                  </div>
                  <p className="text-xs text-gray-400 mt-2">원형 버튼을 클릭하여 정답을 선택하세요.</p>
                </div>
              )}

              {/* 주관식 정답 */}
              {formData.type === 'short_answer' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">정답</label>
                  <input
                    type="text"
                    value={formData.correctAnswer}
                    onChange={(e) => setFormData(prev => ({ ...prev, correctAnswer: e.target.value }))}
                    placeholder="정답을 입력하세요"
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              )}

              {/* OX 정답 */}
              {formData.type === 'ox' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">정답</label>
                  <div className="flex gap-4">
                    <button
                      type="button"
                      onClick={() => setFormData(prev => ({ ...prev, correctAnswer: 'O' }))}
                      className={`flex-1 py-4 text-2xl font-bold rounded-lg border-2 transition-colors ${
                        formData.correctAnswer === 'O'
                          ? 'border-green-500 bg-green-50 text-green-600'
                          : 'border-gray-200 hover:border-gray-300 text-gray-400'
                      }`}
                    >
                      O
                    </button>
                    <button
                      type="button"
                      onClick={() => setFormData(prev => ({ ...prev, correctAnswer: 'X' }))}
                      className={`flex-1 py-4 text-2xl font-bold rounded-lg border-2 transition-colors ${
                        formData.correctAnswer === 'X'
                          ? 'border-red-500 bg-red-50 text-red-600'
                          : 'border-gray-200 hover:border-gray-300 text-gray-400'
                      }`}
                    >
                      X
                    </button>
                  </div>
                </div>
              )}

              {/* 배점 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">배점</label>
                <input
                  type="number"
                  value={formData.points}
                  onChange={(e) => setFormData(prev => ({ ...prev, points: parseInt(e.target.value) || 0 }))}
                  min={1}
                  className="w-32 px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <span className="ml-2 text-gray-500">점</span>
              </div>

              {/* 공개/비공개 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">공개 여부</label>
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setFormData(prev => ({ ...prev, isPublic: true }))}
                    className={`flex items-center gap-2 px-4 py-2.5 rounded-lg border-2 transition-colors ${
                      formData.isPublic
                        ? 'border-emerald-500 bg-emerald-50 text-emerald-700'
                        : 'border-gray-200 hover:border-gray-300 text-gray-500'
                    }`}
                  >
                    <Eye className="w-4 h-4" />
                    <span>공개</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setFormData(prev => ({ ...prev, isPublic: false }))}
                    className={`flex items-center gap-2 px-4 py-2.5 rounded-lg border-2 transition-colors ${
                      !formData.isPublic
                        ? 'border-gray-500 bg-gray-50 text-gray-700'
                        : 'border-gray-200 hover:border-gray-300 text-gray-500'
                    }`}
                  >
                    <EyeOff className="w-4 h-4" />
                    <span>비공개</span>
                  </button>
                </div>
                <p className="text-xs text-gray-400 mt-1">비공개 문제는 시험 출제 시에만 사용됩니다.</p>
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
    </div>
  );
}
