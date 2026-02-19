import { useState, useEffect } from 'react';
import {
  Plus,
  Edit,
  Trash2,
  Upload,
  X,
  BookOpen,
  Paperclip,
  Copy,
  CheckSquare,
  Square,
} from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';

// ─── 템플릿 데이터 타입 ──────────────────────────────────────────
type AssignmentTemplate = {
  id: string;
  title: string;
  description: string;
  totalScore: number;
  submissionType: 'file' | 'text' | 'both';
  fileTypes: string;
  maxFileSize: number;
  allowLateSubmission: boolean;
  latePenalty: number;
  createdAt: string;
};

const STORAGE_KEY = 'assignment-templates';

function loadTemplates(): AssignmentTemplate[] {
  try {
    const json = localStorage.getItem(STORAGE_KEY);
    return json ? JSON.parse(json) : [];
  } catch {
    return [];
  }
}

function saveTemplates(templates: AssignmentTemplate[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(templates));
}

// ─── 과목 타입 (다중 업로드용) ──────────────────────────────────
type CourseOption = {
  id: number;
  name: string;
  sourceType: string;
};

// ─── 메인 탭 컴포넌트 ───────────────────────────────────────────
export function AssignmentTemplateTab() {
  const [templates, setTemplates] = useState<AssignmentTemplate[]>(() => loadTemplates());
  const [showFormModal, setShowFormModal] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<AssignmentTemplate | null>(null);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadTarget, setUploadTarget] = useState<AssignmentTemplate | null>(null);

  // persist
  useEffect(() => {
    saveTemplates(templates);
  }, [templates]);

  const handleCreate = () => {
    setEditingTemplate(null);
    setShowFormModal(true);
  };

  const handleEdit = (tpl: AssignmentTemplate) => {
    setEditingTemplate(tpl);
    setShowFormModal(true);
  };

  const handleDelete = (id: string) => {
    if (!confirm('이 템플릿을 삭제하시겠습니까?')) return;
    setTemplates((prev) => prev.filter((t) => t.id !== id));
  };

  const handleSave = (data: Omit<AssignmentTemplate, 'id' | 'createdAt'>) => {
    if (editingTemplate) {
      setTemplates((prev) =>
        prev.map((t) => (t.id === editingTemplate.id ? { ...t, ...data } : t)),
      );
    } else {
      const newTpl: AssignmentTemplate = {
        ...data,
        id: `tpl_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
        createdAt: new Date().toISOString(),
      };
      setTemplates((prev) => [newTpl, ...prev]);
    }
    setShowFormModal(false);
    setEditingTemplate(null);
  };

  const handleOpenUpload = (tpl: AssignmentTemplate) => {
    setUploadTarget(tpl);
    setShowUploadModal(true);
  };

  const submissionLabel = (type: string) => {
    switch (type) {
      case 'file': return '파일 업로드';
      case 'text': return '텍스트 입력';
      case 'both': return '파일 + 텍스트';
      default: return type;
    }
  };

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">과제 템플릿</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            자주 출제하는 과제를 템플릿으로 저장하고, 여러 강의에 동시 업로드할 수 있습니다.
          </p>
        </div>
        <button
          onClick={handleCreate}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>새 템플릿</span>
        </button>
      </div>

      {/* 템플릿 카드 목록 */}
      {templates.length === 0 ? (
        <div className="text-center py-16 border-2 border-dashed border-gray-200 rounded-lg">
          <BookOpen className="w-12 h-12 mx-auto text-gray-300 mb-3" />
          <p className="text-gray-500 mb-1">저장된 과제 템플릿이 없습니다.</p>
          <p className="text-sm text-gray-400">위의 "새 템플릿" 버튼으로 과제를 템플릿화할 수 있습니다.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {templates.map((tpl) => (
            <div
              key={tpl.id}
              className="bg-white border border-gray-200 rounded-lg p-5 hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between mb-3">
                <div className="flex-1 min-w-0">
                  <h3 className="font-medium text-gray-900 truncate">{tpl.title}</h3>
                  <p className="text-sm text-gray-500 mt-1 line-clamp-2">{tpl.description || '설명 없음'}</p>
                </div>
              </div>

              <div className="flex flex-wrap gap-2 mb-4">
                <span className="px-2 py-0.5 bg-purple-100 text-purple-700 text-xs rounded-full">
                  {tpl.totalScore}점
                </span>
                <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded-full">
                  {submissionLabel(tpl.submissionType)}
                </span>
                {tpl.allowLateSubmission && (
                  <span className="px-2 py-0.5 bg-orange-100 text-orange-700 text-xs rounded-full">
                    지각제출 허용 (-{tpl.latePenalty}%)
                  </span>
                )}
                {tpl.fileTypes && (
                  <span className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded-full flex items-center gap-1">
                    <Paperclip className="w-3 h-3" />
                    {tpl.fileTypes}
                  </span>
                )}
              </div>

              <div className="flex items-center justify-between pt-3 border-t border-gray-100">
                <span className="text-xs text-gray-400">
                  {new Date(tpl.createdAt).toLocaleDateString('ko-KR')}
                </span>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => handleEdit(tpl)}
                    className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                    title="수정"
                  >
                    <Edit className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(tpl.id)}
                    className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    title="삭제"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleOpenUpload(tpl)}
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white text-xs rounded-lg hover:bg-green-700 transition-colors ml-1"
                    title="여러 강의에 업로드"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    다중 업로드
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 템플릿 생성/수정 모달 */}
      {showFormModal && (
        <TemplateFormModal
          initial={editingTemplate}
          onSave={handleSave}
          onClose={() => {
            setShowFormModal(false);
            setEditingTemplate(null);
          }}
        />
      )}

      {/* 다중 강의 업로드 모달 */}
      {showUploadModal && uploadTarget && (
        <MultiCourseUploadModal
          template={uploadTarget}
          onClose={() => {
            setShowUploadModal(false);
            setUploadTarget(null);
          }}
        />
      )}
    </div>
  );
}

// ─── 템플릿 생성/수정 모달 ──────────────────────────────────────
function TemplateFormModal({
  initial,
  onSave,
  onClose,
}: {
  initial: AssignmentTemplate | null;
  onSave: (data: Omit<AssignmentTemplate, 'id' | 'createdAt'>) => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState({
    title: initial?.title ?? '',
    description: initial?.description ?? '',
    totalScore: initial?.totalScore ?? 100,
    submissionType: initial?.submissionType ?? 'file' as 'file' | 'text' | 'both',
    fileTypes: initial?.fileTypes ?? '',
    maxFileSize: initial?.maxFileSize ?? 10,
    allowLateSubmission: initial?.allowLateSubmission ?? false,
    latePenalty: initial?.latePenalty ?? 0,
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(form);
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center z-50" style={{ backgroundColor: 'rgba(0,0,0,0.3)' }}>
      <div className="bg-white rounded-lg w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900">
            {initial ? '템플릿 수정' : '새 과제 템플릿'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          <div>
            <label className="block text-sm text-gray-700 mb-1">
              과제 제목 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="예: HTML 포트폴리오 제작"
              required
            />
          </div>

          <div>
            <label className="block text-sm text-gray-700 mb-1">
              과제 설명 <span className="text-red-500">*</span>
            </label>
            <textarea
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              rows={4}
              placeholder="과제 내용 및 요구사항"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-700 mb-1">배점</label>
              <input
                type="number"
                value={form.totalScore}
                onChange={(e) => setForm({ ...form, totalScore: parseInt(e.target.value) || 0 })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-1">제출 방식</label>
              <select
                value={form.submissionType}
                onChange={(e) => setForm({ ...form, submissionType: e.target.value as 'file' | 'text' | 'both' })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="file">파일 업로드</option>
                <option value="text">텍스트 입력</option>
                <option value="both">파일 + 텍스트</option>
              </select>
            </div>
          </div>

          {(form.submissionType === 'file' || form.submissionType === 'both') && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-gray-700 mb-1">허용 파일 형식</label>
                <input
                  type="text"
                  value={form.fileTypes}
                  onChange={(e) => setForm({ ...form, fileTypes: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder=".pdf, .docx, .zip"
                />
              </div>
              <div>
                <label className="block text-sm text-gray-700 mb-1">최대 파일 크기 (MB)</label>
                <input
                  type="number"
                  value={form.maxFileSize}
                  onChange={(e) => setForm({ ...form, maxFileSize: parseInt(e.target.value) || 0 })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
            </div>
          )}

          <div className="space-y-2">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={form.allowLateSubmission}
                onChange={(e) => setForm({ ...form, allowLateSubmission: e.target.checked })}
                className="w-4 h-4 text-blue-600 border-gray-300 rounded"
              />
              <span className="text-sm text-gray-700">지각 제출 허용</span>
            </label>
            {form.allowLateSubmission && (
              <div>
                <label className="block text-sm text-gray-700 mb-1">지각 감점 (%)</label>
                <input
                  type="number"
                  value={form.latePenalty}
                  onChange={(e) => setForm({ ...form, latePenalty: parseInt(e.target.value) || 0 })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  min="0"
                  max="100"
                />
              </div>
            )}
          </div>

          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              취소
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              {initial ? '수정' : '저장'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ─── 다중 강의 업로드 모달 ──────────────────────────────────────
function MultiCourseUploadModal({
  template,
  onClose,
}: {
  template: AssignmentTemplate;
  onClose: () => void;
}) {
  const [courses, setCourses] = useState<CourseOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const today = new Date().toISOString().slice(0, 10);
  const [startDate, setStartDate] = useState(today);
  const [startTime, setStartTime] = useState('00:00');
  const [dueDate, setDueDate] = useState('');
  const [dueTime, setDueTime] = useState('23:59');
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const fetchCourses = async () => {
      setLoading(true);
      try {
        // 왜: 프리즘 + 학사 과목을 각각 불러와서 합칩니다.
        const [prismRes, haksaRes] = await Promise.all([
          tutorLmsApi.getMyCoursesCombined({ tab: 'prism', pageSize: 200 }),
          tutorLmsApi.getMyCoursesCombined({ tab: 'haksa', pageSize: 200 }),
        ]);
        const mapped: CourseOption[] = [];
        for (const row of prismRes.rst_data ?? []) {
          mapped.push({ id: row.id, name: row.course_nm || row.course_nm_conv || `과목 #${row.id}`, sourceType: 'prism' });
        }
        for (const row of haksaRes.rst_data ?? []) {
          mapped.push({ id: row.id, name: row.course_nm || row.course_nm_conv || `과목 #${row.id}`, sourceType: 'haksa' });
        }
        if (!cancelled) setCourses(mapped);
      } catch {
        if (!cancelled) setCourses([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void fetchCourses();
    return () => { cancelled = true; };
  }, []);

  const toggleId = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    if (selectedIds.size === courses.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(courses.map((c) => c.id)));
    }
  };

  const handleUpload = async () => {
    if (selectedIds.size === 0) {
      alert('업로드할 강의를 1개 이상 선택해 주세요.');
      return;
    }
    if (!dueDate) {
      alert('마감 날짜를 입력해 주세요.');
      return;
    }

    setUploading(true);
    try {
      // 왜: 선택된 각 강의에 과제를 동시 등록합니다.
      const results = await Promise.allSettled(
        Array.from(selectedIds).map((courseId) =>
          tutorLmsApi.createHomework({
            courseId,
            title: template.title,
            description: template.description,
            startDate,
            startTime,
            dueDate,
            dueTime,
            totalScore: template.totalScore,
            onoffType: 'N',
          }),
        ),
      );

      const successCount = results.filter((r) => r.status === 'fulfilled' && (r.value as any).rst_code === '0000').length;
      const failCount = selectedIds.size - successCount;

      if (failCount === 0) {
        alert(`${successCount}개 강의에 과제가 등록되었습니다.`);
      } else {
        alert(`성공: ${successCount}개 / 실패: ${failCount}개\n일부 강의에 과제 등록이 실패했습니다.`);
      }
      onClose();
    } catch (e) {
      alert(e instanceof Error ? e.message : '과제 업로드 중 오류가 발생했습니다.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center z-50" style={{ backgroundColor: 'rgba(0,0,0,0.3)' }}>
      <div className="bg-white rounded-lg w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between z-10">
          <div>
            <h3 className="text-lg font-semibold text-gray-900">다중 강의 업로드</h3>
            <p className="text-sm text-gray-500 mt-0.5">"{template.title}" 템플릿을 선택한 강의에 일괄 등록합니다.</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* 템플릿 요약 */}
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Copy className="w-4 h-4 text-blue-600" />
              <span className="font-medium text-blue-900">템플릿 정보</span>
            </div>
            <div className="text-sm text-blue-800 space-y-1">
              <p><span className="text-blue-600">제목:</span> {template.title}</p>
              <p><span className="text-blue-600">배점:</span> {template.totalScore}점</p>
              <p className="line-clamp-2"><span className="text-blue-600">설명:</span> {template.description || '-'}</p>
            </div>
          </div>

          {/* 제출 기간 설정 */}
          <div>
            <h4 className="text-sm font-medium text-gray-700 mb-3">제출 기간 설정</h4>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-gray-500 mb-1">시작 날짜</label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">시작 시간</label>
                <input
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">마감 날짜 <span className="text-red-500">*</span></label>
                <input
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  min={startDate || undefined}
                  required
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">마감 시간</label>
                <input
                  type="time"
                  value={dueTime}
                  onChange={(e) => setDueTime(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
            </div>
          </div>

          {/* 강의 선택 */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-sm font-medium text-gray-700">
                강의 선택 ({selectedIds.size}/{courses.length})
              </h4>
              <button
                type="button"
                onClick={toggleAll}
                className="text-xs text-blue-600 hover:text-blue-800 transition-colors"
              >
                {selectedIds.size === courses.length ? '전체 해제' : '전체 선택'}
              </button>
            </div>

            {loading ? (
              <div className="text-center py-8 text-gray-500 text-sm">강의 목록을 불러오는 중...</div>
            ) : courses.length === 0 ? (
              <div className="text-center py-8 text-gray-400 text-sm border border-dashed border-gray-200 rounded-lg">
                담당 강의가 없습니다.
              </div>
            ) : (
              <div className="border border-gray-200 rounded-lg divide-y divide-gray-100 max-h-[280px] overflow-y-auto">
                {courses.map((course) => {
                  const checked = selectedIds.has(course.id);
                  return (
                    <button
                      key={course.id}
                      type="button"
                      onClick={() => toggleId(course.id)}
                      className={`w-full flex items-center gap-3 px-4 py-3 text-left transition-colors ${
                        checked ? 'bg-blue-50' : 'hover:bg-gray-50'
                      }`}
                    >
                      {checked ? (
                        <CheckSquare className="w-4 h-4 text-blue-600 flex-shrink-0" />
                      ) : (
                        <Square className="w-4 h-4 text-gray-400 flex-shrink-0" />
                      )}
                      <div className="flex-1 min-w-0">
                        <span className="text-sm text-gray-900 truncate block">{course.name}</span>
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        {course.sourceType === 'haksa' ? '학사' : '프리즘'}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* 하단 버튼 */}
        <div className="sticky bottom-0 bg-white border-t border-gray-200 px-6 py-4 flex gap-3">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleUpload}
            disabled={selectedIds.size === 0 || uploading}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Upload className="w-4 h-4" />
            <span>{uploading ? '업로드 중...' : `${selectedIds.size}개 강의에 업로드`}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
