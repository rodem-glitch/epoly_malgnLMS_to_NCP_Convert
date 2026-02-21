import React, { useEffect, useState } from 'react';
import { Plus, Edit, Trash2, Search, ChevronDown, ChevronRight, Eye, EyeOff, Save, GripVertical, HelpCircle, BookOpen } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';

// 왜: 자주 묻는 질문(FAQ)을 공지 형태로 등록·관리하는 페이지입니다.
// 왜: 학생들이 반복적으로 질문하는 내용을 미리 정리해두면 Q&A 부담을 줄일 수 있습니다.

interface FaqItem {
  id: string;
  question: string;
  answer: string;
  category: string;
  isPublished: boolean; // 왜: 공개 여부 — 미공개 FAQ는 학생에게 보이지 않음
  targetCourses: string[]; // 왜: 빈 배열이면 전체 과목에 노출, 특정 과목 ID가 있으면 해당 과목에만 노출
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

interface FaqCategory {
  id: string;
  name: string;
  sortOrder: number;
}

interface CourseForSelect {
  courseId: string;
  courseName: string;
  sourceType: 'haksa' | 'prism';
}

const DEFAULT_CATEGORIES: FaqCategory[] = [
  { id: 'general', name: '일반', sortOrder: 1 },
  { id: 'assignment', name: '과제', sortOrder: 2 },
  { id: 'exam', name: '시험', sortOrder: 3 },
  { id: 'grade', name: '성적', sortOrder: 4 },
  { id: 'attendance', name: '출석', sortOrder: 5 },
];

// 왜: 샘플 FAQ — 백엔드 연동 전 UI 확인용
const SAMPLE_FAQS: FaqItem[] = [
  {
    id: '1',
    question: '과제 제출 기한이 지나면 어떻게 되나요?',
    answer: '과제 제출 기한이 지나면 기본적으로 제출이 불가능합니다. 다만, 교수자가 지각 제출을 허용한 경우 감점이 적용될 수 있습니다. 자세한 사항은 각 과제의 안내를 확인해 주세요.',
    category: 'assignment',
    isPublished: true,
    targetCourses: [], // 전체 과목
    sortOrder: 1,
    createdAt: '2026-02-15',
    updatedAt: '2026-02-15',
  },
  {
    id: '2',
    question: '시험 응시 중 인터넷이 끊기면 어떻게 되나요?',
    answer: '시험 응시 중 인터넷이 끊기더라도 자동 저장 기능이 작동합니다. 인터넷이 복구되면 이전 상태에서 이어서 응시할 수 있습니다. 단, 시험 시간은 계속 흘러가므로 주의해 주세요.',
    category: 'exam',
    isPublished: true,
    targetCourses: [], // 전체 과목
    sortOrder: 2,
    createdAt: '2026-02-15',
    updatedAt: '2026-02-15',
  },
  {
    id: '3',
    question: '출석은 어떻게 확인하나요?',
    answer: '출석은 LMS에서 동영상 강의 시청 기록, 실시간 강의 참여 여부 등을 기준으로 자동 기록됩니다. 출석 현황은 "내 학습현황" 메뉴에서 확인할 수 있습니다.',
    category: 'attendance',
    isPublished: false,
    targetCourses: [],
    sortOrder: 3,
    createdAt: '2026-02-16',
    updatedAt: '2026-02-16',
  },
];

export function FaqManagePage() {
  const [faqs, setFaqs] = useState<FaqItem[]>(SAMPLE_FAQS);
  const [categories, setCategories] = useState<FaqCategory[]>(DEFAULT_CATEGORIES);
  const [filterCategory, setFilterCategory] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // 과목 목록 (노출 대상 과목 선택용)
  const [courses, setCourses] = useState<CourseForSelect[]>([]);
  const [loadingCourses, setLoadingCourses] = useState(true);

  // 모달
  const [showModal, setShowModal] = useState(false);
  const [editingFaq, setEditingFaq] = useState<FaqItem | null>(null);
  const [formData, setFormData] = useState({
    question: '',
    answer: '',
    category: 'general',
    isPublished: true,
    targetCourses: [] as string[], // 빈 배열 = 전체 과목
    targetMode: 'all' as 'all' | 'selected',
  });

  // 카테고리 관리 모달
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');

  // === 과목 목록 로드 ===
  useEffect(() => {
    let cancelled = false;
    const fetchCourses = async () => {
      setLoadingCourses(true);
      try {
        const [resH, resP] = await Promise.all([
          tutorLmsApi.getMyCoursesCombined({ tab: 'haksa', pageSize: 200 }),
          tutorLmsApi.getMyCoursesCombined({ tab: 'prism', pageSize: 200 }),
        ]);
        if (cancelled) return;

        const haksaList: CourseForSelect[] = (resH.rst_code === '0000' && Array.isArray(resH.rst_data))
          ? resH.rst_data.map((c: any) => ({
              courseId: String(c.id ?? c.course_id ?? ''),
              courseName: String(c.course_nm_conv || c.subject_nm_conv || c.course_nm || '-'),
              sourceType: 'haksa' as const,
            })).filter((c: CourseForSelect) => c.courseId)
          : [];

        const prismList: CourseForSelect[] = (resP.rst_code === '0000' && Array.isArray(resP.rst_data))
          ? resP.rst_data.map((c: any) => ({
              courseId: String(c.id ?? c.course_id ?? ''),
              courseName: String(c.course_nm_conv || c.subject_nm_conv || c.course_nm || '-'),
              sourceType: 'prism' as const,
            })).filter((c: CourseForSelect) => c.courseId)
          : [];

        const seen = new Set<string>();
        const merged: CourseForSelect[] = [];
        for (const c of [...haksaList, ...prismList]) {
          if (!seen.has(c.courseId)) {
            seen.add(c.courseId);
            merged.push(c);
          }
        }
        setCourses(merged);
      } catch {
        // 과목 로드 실패 시 빈 배열 유지
      } finally {
        if (!cancelled) setLoadingCourses(false);
      }
    };
    void fetchCourses();
    return () => { cancelled = true; };
  }, []);

  // === 필터링 ===
  const filteredFaqs = faqs.filter(faq => {
    if (filterCategory && faq.category !== filterCategory) return false;
    if (searchQuery) {
      const kw = searchQuery.toLowerCase();
      if (!faq.question.toLowerCase().includes(kw) && !faq.answer.toLowerCase().includes(kw)) return false;
    }
    return true;
  }).sort((a, b) => a.sortOrder - b.sortOrder);

  // === CRUD ===
  const openAddModal = () => {
    setEditingFaq(null);
    setFormData({ question: '', answer: '', category: 'general', isPublished: true, targetCourses: [], targetMode: 'all' });
    setShowModal(true);
  };

  const openEditModal = (faq: FaqItem) => {
    setEditingFaq(faq);
    setFormData({
      question: faq.question,
      answer: faq.answer,
      category: faq.category,
      isPublished: faq.isPublished,
      targetCourses: [...faq.targetCourses],
      targetMode: faq.targetCourses.length === 0 ? 'all' : 'selected',
    });
    setShowModal(true);
  };

  const handleSave = () => {
    if (!formData.question.trim() || !formData.answer.trim()) {
      alert('질문과 답변을 모두 입력해 주세요.');
      return;
    }

    const resolvedTargetCourses = formData.targetMode === 'all' ? [] : formData.targetCourses;

    if (editingFaq) {
      setFaqs(prev => prev.map(f =>
        f.id === editingFaq.id
          ? {
              ...f,
              question: formData.question.trim(),
              answer: formData.answer.trim(),
              category: formData.category,
              isPublished: formData.isPublished,
              targetCourses: resolvedTargetCourses,
              updatedAt: new Date().toISOString().slice(0, 10),
            }
          : f
      ));
    } else {
      const newFaq: FaqItem = {
        id: `faq_${Date.now()}`,
        question: formData.question.trim(),
        answer: formData.answer.trim(),
        category: formData.category,
        isPublished: formData.isPublished,
        targetCourses: resolvedTargetCourses,
        sortOrder: faqs.length + 1,
        createdAt: new Date().toISOString().slice(0, 10),
        updatedAt: new Date().toISOString().slice(0, 10),
      };
      setFaqs(prev => [...prev, newFaq]);
    }
    setShowModal(false);
  };

  const handleDelete = (id: string) => {
    if (!confirm('이 FAQ를 삭제하시겠습니까?')) return;
    setFaqs(prev => prev.filter(f => f.id !== id));
  };

  const togglePublish = (id: string) => {
    setFaqs(prev => prev.map(f =>
      f.id === id ? { ...f, isPublished: !f.isPublished } : f
    ));
  };

  const toggleCourseInForm = (courseId: string) => {
    setFormData(prev => {
      const next = prev.targetCourses.includes(courseId)
        ? prev.targetCourses.filter(id => id !== courseId)
        : [...prev.targetCourses, courseId];
      return { ...prev, targetCourses: next };
    });
  };

  // === 카테고리 관리 ===
  const addCategory = () => {
    if (!newCategoryName.trim()) return;
    const newCat: FaqCategory = {
      id: `cat_${Date.now()}`,
      name: newCategoryName.trim(),
      sortOrder: categories.length + 1,
    };
    setCategories(prev => [...prev, newCat]);
    setNewCategoryName('');
  };

  const removeCategory = (id: string) => {
    if (id === 'general') {
      alert('기본 카테고리는 삭제할 수 없습니다.');
      return;
    }
    if (!confirm('이 카테고리를 삭제하시겠습니까? 해당 카테고리의 FAQ는 "일반"으로 이동됩니다.')) return;
    setFaqs(prev => prev.map(f => f.category === id ? { ...f, category: 'general' } : f));
    setCategories(prev => prev.filter(c => c.id !== id));
  };

  const getCategoryName = (id: string) => categories.find(c => c.id === id)?.name || id;

  // 과목 이름 조회
  const getCourseName = (courseId: string) => courses.find(c => c.courseId === courseId)?.courseName || courseId;

  // === 통계 ===
  const publishedCount = faqs.filter(f => f.isPublished).length;
  const draftCount = faqs.filter(f => !f.isPublished).length;

  return (
    <div className="space-y-5">
      {/* 헤더 */}
      <div className="flex flex-wrap items-end gap-4">
        <div className="mr-auto">
          <h1 className="text-2xl font-bold text-gray-900">FAQ 공지 설정</h1>
          <p className="text-gray-500 text-sm mt-0.5">자주 묻는 질문을 공지 형태로 등록하여 학생들의 반복 질문을 줄입니다.</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setShowCategoryModal(true)}
            className="flex items-center gap-2 px-3 py-2.5 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors text-sm"
          >
            카테고리 관리
          </button>
          <button
            onClick={openAddModal}
            className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm"
          >
            <Plus className="w-4 h-4" />
            <span>FAQ 추가</span>
          </button>
        </div>
      </div>

      {/* 통계 */}
      <div className="grid grid-cols-3 gap-3">
        <div className="px-4 py-3 bg-gray-50 rounded-lg text-center border border-gray-200">
          <div className="text-2xl font-semibold text-gray-900">{faqs.length}</div>
          <div className="text-xs text-gray-500">전체 FAQ</div>
        </div>
        <div className="px-4 py-3 bg-green-50 rounded-lg text-center border border-green-200">
          <div className="text-2xl font-semibold text-green-600">{publishedCount}</div>
          <div className="text-xs text-green-600">공개 중</div>
        </div>
        <div className="px-4 py-3 bg-orange-50 rounded-lg text-center border border-orange-200">
          <div className="text-2xl font-semibold text-orange-600">{draftCount}</div>
          <div className="text-xs text-orange-600">비공개</div>
        </div>
      </div>

      {/* 필터 바 */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                placeholder="질문 또는 답변 검색..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">전체 카테고리</option>
            {categories.map(cat => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>
      </div>

      {/* FAQ 목록 (아코디언) */}
      <div className="space-y-2">
        {filteredFaqs.length === 0 ? (
          <div className="bg-white rounded-xl border-2 border-dashed border-gray-300 p-12 text-center">
            <HelpCircle className="w-10 h-10 mx-auto mb-3 text-gray-400 opacity-50" />
            <p className="text-gray-500 text-lg font-medium">등록된 FAQ가 없습니다</p>
            <p className="text-gray-400 text-sm mt-1">상단의 'FAQ 추가' 버튼으로 자주 묻는 질문을 등록하세요.</p>
          </div>
        ) : (
          filteredFaqs.map((faq, index) => {
            const isExpanded = expandedId === faq.id;
            const courseLabel = faq.targetCourses.length === 0
              ? '전체 과목'
              : `${faq.targetCourses.length}개 과목`;
            return (
              <div
                key={faq.id}
                className={`bg-white rounded-lg border transition-all ${
                  isExpanded ? 'border-blue-300 shadow-sm' : 'border-gray-200'
                }`}
              >
                {/* 질문 헤더 */}
                <div
                  className="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-gray-50 transition-colors"
                  onClick={() => setExpandedId(isExpanded ? null : faq.id)}
                >
                  <GripVertical className="w-4 h-4 text-gray-300 flex-shrink-0" />
                  <span className="text-sm text-gray-400 w-6">{index + 1}.</span>
                  {isExpanded ? (
                    <ChevronDown className="w-4 h-4 text-gray-500 flex-shrink-0" />
                  ) : (
                    <ChevronRight className="w-4 h-4 text-gray-500 flex-shrink-0" />
                  )}
                  <span className="flex-1 text-sm font-medium text-gray-900 line-clamp-1">
                    {faq.question}
                  </span>
                  <span className={`px-2 py-0.5 text-xs rounded-full flex-shrink-0 ${
                    faq.isPublished ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                  }`}>
                    {faq.isPublished ? '공개' : '비공개'}
                  </span>
                  <span className="px-2 py-0.5 bg-blue-50 text-blue-600 text-xs rounded-full flex-shrink-0">
                    {getCategoryName(faq.category)}
                  </span>
                  {/* 노출 과목 뱃지 */}
                  <span className={`px-2 py-0.5 text-xs rounded-full flex-shrink-0 ${
                    faq.targetCourses.length === 0
                      ? 'bg-purple-50 text-purple-600'
                      : 'bg-amber-50 text-amber-600'
                  }`}>
                    <BookOpen className="w-3 h-3 inline mr-0.5 -mt-0.5" />
                    {courseLabel}
                  </span>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button
                      onClick={(e) => { e.stopPropagation(); togglePublish(faq.id); }}
                      className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                      title={faq.isPublished ? '비공개로 전환' : '공개로 전환'}
                    >
                      {faq.isPublished ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); openEditModal(faq); }}
                      className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors"
                      title="수정"
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(faq.id); }}
                      className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                      title="삭제"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                {/* 답변 (펼침) */}
                {isExpanded && (
                  <div className="px-4 pb-4 pt-1 border-t border-gray-100">
                    <div className="ml-14 bg-blue-50 rounded-lg p-4 text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">
                      {faq.answer}
                    </div>
                    <div className="ml-14 mt-2 flex flex-wrap gap-3 text-xs text-gray-400">
                      <span>등록: {faq.createdAt}</span>
                      <span>수정: {faq.updatedAt}</span>
                      <span className="text-purple-500">
                        노출: {faq.targetCourses.length === 0
                          ? '전체 과목'
                          : faq.targetCourses.map(id => getCourseName(id)).join(', ')
                        }
                      </span>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* ===== FAQ 추가/수정 모달 ===== */}
      {showModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 max-h-[85vh] overflow-y-auto">
            <div className="px-6 py-4 border-b border-gray-200">
              <h2 className="text-lg font-bold text-gray-900">
                {editingFaq ? 'FAQ 수정' : '새 FAQ 추가'}
              </h2>
            </div>
            <div className="px-6 py-4 space-y-4">
              {/* 카테고리 + 공개 여부 */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">카테고리</label>
                  <select
                    value={formData.category}
                    onChange={(e) => setFormData(prev => ({ ...prev, category: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {categories.map(cat => (
                      <option key={cat.id} value={cat.id}>{cat.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">공개 여부</label>
                  <div className="flex gap-2 mt-1">
                    <button
                      onClick={() => setFormData(prev => ({ ...prev, isPublished: true }))}
                      className={`flex-1 py-2 text-sm rounded-lg border transition-colors ${
                        formData.isPublished
                          ? 'bg-green-100 border-green-400 text-green-700'
                          : 'border-gray-300 text-gray-500 hover:bg-gray-50'
                      }`}
                    >
                      공개
                    </button>
                    <button
                      onClick={() => setFormData(prev => ({ ...prev, isPublished: false }))}
                      className={`flex-1 py-2 text-sm rounded-lg border transition-colors ${
                        !formData.isPublished
                          ? 'bg-gray-200 border-gray-400 text-gray-700'
                          : 'border-gray-300 text-gray-500 hover:bg-gray-50'
                      }`}
                    >
                      비공개
                    </button>
                  </div>
                </div>
              </div>

              {/* 노출 과목 설정 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">노출 대상 과목</label>
                <div className="flex gap-2 mb-2">
                  <button
                    onClick={() => setFormData(prev => ({ ...prev, targetMode: 'all', targetCourses: [] }))}
                    className={`flex-1 py-2 text-sm rounded-lg border transition-colors ${
                      formData.targetMode === 'all'
                        ? 'bg-purple-100 border-purple-400 text-purple-700'
                        : 'border-gray-300 text-gray-500 hover:bg-gray-50'
                    }`}
                  >
                    전체 과목
                  </button>
                  <button
                    onClick={() => setFormData(prev => ({ ...prev, targetMode: 'selected' }))}
                    className={`flex-1 py-2 text-sm rounded-lg border transition-colors ${
                      formData.targetMode === 'selected'
                        ? 'bg-amber-100 border-amber-400 text-amber-700'
                        : 'border-gray-300 text-gray-500 hover:bg-gray-50'
                    }`}
                  >
                    선택 과목만
                  </button>
                </div>
                {formData.targetMode === 'selected' && (
                  <div className="border border-gray-200 rounded-lg p-3 max-h-[200px] overflow-y-auto">
                    {loadingCourses ? (
                      <p className="text-sm text-gray-400 text-center py-2">과목 목록 불러오는 중...</p>
                    ) : courses.length === 0 ? (
                      <p className="text-sm text-gray-400 text-center py-2">담당 과목이 없습니다.</p>
                    ) : (
                      <div className="space-y-1">
                        {courses.map(course => {
                          const isChecked = formData.targetCourses.includes(course.courseId);
                          return (
                            <label
                              key={course.courseId}
                              className={`flex items-center gap-2 px-3 py-2 rounded-lg cursor-pointer transition-colors ${
                                isChecked ? 'bg-amber-50' : 'hover:bg-gray-50'
                              }`}
                            >
                              <input
                                type="checkbox"
                                checked={isChecked}
                                onChange={() => toggleCourseInForm(course.courseId)}
                                className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                              />
                              <span className="text-sm text-gray-900 flex-1">{course.courseName}</span>
                              <span className={`px-1.5 py-0.5 text-[10px] rounded ${
                                course.sourceType === 'haksa'
                                  ? 'bg-blue-100 text-blue-600'
                                  : 'bg-gray-100 text-gray-500'
                              }`}>
                                {course.sourceType === 'haksa' ? '정규' : '비정규'}
                              </span>
                            </label>
                          );
                        })}
                      </div>
                    )}
                    {formData.targetCourses.length > 0 && (
                      <div className="mt-2 pt-2 border-t border-gray-200 text-xs text-amber-600">
                        {formData.targetCourses.length}개 과목 선택됨
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* 질문 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">질문 (Q)</label>
                <input
                  type="text"
                  value={formData.question}
                  onChange={(e) => setFormData(prev => ({ ...prev, question: e.target.value }))}
                  placeholder="예: 과제 제출 기한이 지나면 어떻게 되나요?"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* 답변 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">답변 (A)</label>
                <textarea
                  value={formData.answer}
                  onChange={(e) => setFormData(prev => ({ ...prev, answer: e.target.value }))}
                  placeholder="답변을 입력하세요..."
                  rows={6}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-y"
                />
              </div>
            </div>
            <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-2">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors text-sm"
              >
                취소
              </button>
              <button
                onClick={handleSave}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm"
              >
                <Save className="w-4 h-4" />
                <span>{editingFaq ? '수정' : '추가'}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ===== 카테고리 관리 모달 ===== */}
      {showCategoryModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4">
            <div className="px-6 py-4 border-b border-gray-200">
              <h2 className="text-lg font-bold text-gray-900">카테고리 관리</h2>
            </div>
            <div className="px-6 py-4 space-y-3">
              {categories.map(cat => (
                <div key={cat.id} className="flex items-center justify-between px-3 py-2 bg-gray-50 rounded-lg">
                  <span className="text-sm text-gray-900">{cat.name}</span>
                  {cat.id !== 'general' && (
                    <button
                      onClick={() => removeCategory(cat.id)}
                      className="p-1 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))}
              <div className="flex gap-2">
                <input
                  type="text"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="새 카테고리 이름"
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  onKeyDown={(e) => { if (e.key === 'Enter') addCategory(); }}
                />
                <button
                  onClick={addCategory}
                  className="px-3 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>
            </div>
            <div className="px-6 py-4 border-t border-gray-200 flex justify-end">
              <button
                onClick={() => setShowCategoryModal(false)}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors text-sm"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
