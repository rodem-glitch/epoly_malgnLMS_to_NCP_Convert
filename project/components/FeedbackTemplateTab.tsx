import { useEffect, useMemo, useState } from 'react';
import { Plus, Trash2, Save, MessageSquare, RotateCcw } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';

type FeedbackTemplate = {
  id?: number;
  label: string;
  text: string;
};

type CourseOption = {
  id: number;
  name: string;
};

const DEFAULT_TEMPLATES: FeedbackTemplate[] = [
  { label: '우수', text: '과제를 매우 훌륭하게 수행하였습니다. 우수한 성과입니다.' },
  { label: '양호', text: '전반적으로 잘 작성하였으나, 일부 보완이 필요합니다.' },
  { label: '보완필요', text: '과제 내용이 부족합니다. 요구사항을 다시 확인하고 보완해 주세요.' },
  { label: '재제출', text: '과제 기준에 미달합니다. 수정 후 재제출 바랍니다.' },
  { label: '형식오류', text: '제출 파일 형식 또는 양식이 올바르지 않습니다. 확인 후 다시 제출해 주세요.' },
];

function encodeTemplateContent(label: string, text: string) {
  return `${label.trim()}::${text.trim()}`;
}

function decodeTemplateContent(content: string, index: number): FeedbackTemplate {
  const raw = String(content ?? '').trim();
  if (!raw) return { label: `템플릿 ${index + 1}`, text: '' };
  const sepIdx = raw.indexOf('::');
  if (sepIdx > -1) {
    const label = raw.slice(0, sepIdx).trim();
    const text = raw.slice(sepIdx + 2).trim();
    return {
      label: label || `템플릿 ${index + 1}`,
      text,
    };
  }
  return {
    label: raw.length > 10 ? `${raw.slice(0, 10)}...` : raw,
    text: raw,
  };
}

export function FeedbackTemplateTab() {
  const [courses, setCourses] = useState<CourseOption[]>([]);
  const [selectedCourseId, setSelectedCourseId] = useState<number | null>(null);
  const [loadingCourses, setLoadingCourses] = useState(true);
  const [templates, setTemplates] = useState<FeedbackTemplate[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(true);

  const selectedCourseName = useMemo(
    () => courses.find((c) => c.id === selectedCourseId)?.name ?? '-',
    [courses, selectedCourseId]
  );

  const fetchCourses = async () => {
    setLoadingCourses(true);
    try {
      const [prismRes, haksaRes] = await Promise.all([
        tutorLmsApi.getMyCoursesCombined({ tab: 'prism', pageSize: 200 }),
        tutorLmsApi.getMyCoursesCombined({ tab: 'haksa', pageSize: 200 }),
      ]);
      const mapped: CourseOption[] = [];
      for (const row of prismRes.rst_data ?? []) {
        mapped.push({
          id: Number(row.id),
          name: String(row.course_nm_conv || row.subject_nm_conv || row.course_nm || `과목 #${row.id}`),
        });
      }
      for (const row of haksaRes.rst_data ?? []) {
        mapped.push({
          id: Number(row.id),
          name: String(row.course_nm_conv || row.subject_nm_conv || row.course_nm || `과목 #${row.id}`),
        });
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

  const fetchTemplates = async (courseId: number) => {
    setLoadingTemplates(true);
    try {
      const res = await tutorLmsApi.getHomeworkFeedbackTemplates({ courseId, limit: 20 });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
      if (rows.length === 0) {
        setTemplates(DEFAULT_TEMPLATES);
        setSaved(false);
      } else {
        const parsed = rows
          .map((row: any, index: number) => {
            const decoded = decodeTemplateContent(String(row.content ?? ''), index);
            return {
              id: Number(row.id),
              label: decoded.label,
              text: decoded.text,
            };
          })
          .filter((row) => row.text.trim());
        setTemplates(parsed.length > 0 ? parsed : DEFAULT_TEMPLATES);
        setSaved(true);
      }
    } catch {
      setTemplates(DEFAULT_TEMPLATES);
      setSaved(false);
    } finally {
      setLoadingTemplates(false);
    }
  };

  useEffect(() => {
    void fetchCourses();
  }, []);

  useEffect(() => {
    if (!selectedCourseId) return;
    void fetchTemplates(selectedCourseId);
  }, [selectedCourseId]);

  useEffect(() => {
    setSaved(false);
  }, [templates]);

  const handleAdd = () => {
    setTemplates((prev) => [...prev, { label: '', text: '' }]);
  };

  const handleRemove = async (idx: number) => {
    const target = templates[idx];
    if (!target) return;
    if (target.id && selectedCourseId) {
      try {
        const res = await tutorLmsApi.deleteHomeworkFeedbackTemplate({
          id: target.id,
          courseId: selectedCourseId,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      } catch (e) {
        alert(e instanceof Error ? e.message : '템플릿 삭제 중 오류가 발생했습니다.');
        return;
      }
    }
    setTemplates((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleChange = (idx: number, field: 'label' | 'text', value: string) => {
    setTemplates((prev) => prev.map((t, i) => (i === idx ? { ...t, [field]: value } : t)));
  };

  const handleSave = async () => {
    if (!selectedCourseId) {
      alert('과목을 먼저 선택해 주세요.');
      return;
    }
    const valid = templates
      .map((item) => ({ ...item, label: item.label.trim(), text: item.text.trim() }))
      .filter((item) => item.label && item.text);
    if (valid.length === 0) {
      alert('최소 1개 이상의 템플릿이 필요합니다.');
      return;
    }

    setSaving(true);
    try {
      for (let i = 0; i < valid.length; i++) {
        const item = valid[i];
        const res = await tutorLmsApi.saveHomeworkFeedbackTemplate({
          courseId: selectedCourseId,
          id: item.id,
          sort: i + 1,
          content: encodeTemplateContent(item.label, item.text),
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
      }
      await fetchTemplates(selectedCourseId);
      setSaved(true);
    } catch (e) {
      alert(e instanceof Error ? e.message : '템플릿 저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    if (!confirm('기본 템플릿으로 초기화하시겠습니까?')) return;
    setTemplates(DEFAULT_TEMPLATES);
    setSaved(false);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">피드백 템플릿 관리</h2>
          <p className="text-sm text-gray-500 mt-0.5">선택한 과목 기준으로 빠른 피드백 템플릿을 저장합니다.</p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={selectedCourseId ?? ''}
            disabled={loadingCourses || courses.length === 0}
            onChange={(e) => setSelectedCourseId(Number(e.target.value) || null)}
            className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-64"
          >
            {!selectedCourseId && <option value="">과목 선택</option>}
            {courses.map((course) => (
              <option key={course.id} value={course.id}>
                {course.name}
              </option>
            ))}
          </select>
          <button
            onClick={handleReset}
            className="flex items-center gap-1.5 px-3 py-2 text-sm border border-gray-300 text-gray-600 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <RotateCcw className="w-4 h-4" />
            기본값 복원
          </button>
          <button
            onClick={handleAdd}
            className="flex items-center gap-1.5 px-3 py-2 text-sm border border-blue-200 text-blue-700 rounded-lg hover:bg-blue-50 transition-colors"
          >
            <Plus className="w-4 h-4" />
            추가
          </button>
          <button
            onClick={handleSave}
            disabled={saving || loadingTemplates || !selectedCourseId}
            className={`flex items-center gap-1.5 px-4 py-2 text-sm rounded-lg transition-colors ${
              saved ? 'bg-green-600 text-white' : 'bg-blue-600 text-white hover:bg-blue-700'
            } disabled:opacity-50`}
          >
            <Save className="w-4 h-4" />
            {saving ? '저장 중...' : saved ? '저장됨' : '저장'}
          </button>
        </div>
      </div>

      <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 text-sm text-gray-600">
        현재 선택 과목: <span className="font-medium text-gray-900">{selectedCourseName}</span>
      </div>

      <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
        <div className="text-sm text-gray-500 mb-2">미리보기 (피드백 입력 시 표시되는 버튼)</div>
        <div className="flex flex-wrap gap-1.5">
          {templates.filter((t) => t.label.trim()).map((tpl, idx) => (
            <span key={idx} className="px-2.5 py-1 text-xs rounded-full border border-blue-200 bg-blue-50 text-blue-700">
              {tpl.label}
            </span>
          ))}
          {templates.filter((t) => t.label.trim()).length === 0 && (
            <span className="text-sm text-gray-400">템플릿을 추가해 주세요</span>
          )}
        </div>
      </div>

      {loadingTemplates ? (
        <div className="text-center py-10 text-sm text-gray-500 border border-dashed border-gray-200 rounded-lg">템플릿을 불러오는 중...</div>
      ) : templates.length === 0 ? (
        <div className="text-center py-12 border-2 border-dashed border-gray-200 rounded-lg">
          <MessageSquare className="w-10 h-10 mx-auto text-gray-300 mb-2" />
          <p className="text-gray-500 mb-1">템플릿이 없습니다.</p>
          <p className="text-sm text-gray-400">"추가" 버튼을 눌러 새 템플릿을 만들어 보세요.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {templates.map((tpl, idx) => (
            <div key={`${tpl.id ?? 'new'}-${idx}`} className="bg-white border border-gray-200 rounded-lg p-4 flex gap-4 items-start">
              <div className="text-sm font-medium text-gray-400 mt-2 w-6 text-center flex-shrink-0">{idx + 1}</div>
              <div className="flex-1 space-y-2">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">라벨 (버튼에 표시)</label>
                  <input
                    type="text"
                    value={tpl.label}
                    onChange={(e) => handleChange(idx, 'label', e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="예: 우수"
                    maxLength={20}
                  />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">피드백 문구</label>
                  <textarea
                    value={tpl.text}
                    onChange={(e) => handleChange(idx, 'text', e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                    rows={2}
                    placeholder="클릭 시 입력될 피드백 내용"
                  />
                </div>
              </div>
              <button
                onClick={() => { void handleRemove(idx); }}
                className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors flex-shrink-0 mt-2"
                title="삭제"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
