import { useState, useEffect } from 'react';
import { Plus, Trash2, Save, MessageSquare, RotateCcw } from 'lucide-react';

type FeedbackTemplate = {
  label: string;
  text: string;
};

const STORAGE_KEY = 'feedback-templates';

const DEFAULT_TEMPLATES: FeedbackTemplate[] = [
  { label: '우수', text: '과제를 매우 훌륭하게 수행하였습니다. 우수한 성과입니다.' },
  { label: '양호', text: '전반적으로 잘 작성하였으나, 일부 보완이 필요합니다.' },
  { label: '보완필요', text: '과제 내용이 부족합니다. 요구사항을 다시 확인하고 보완해 주세요.' },
  { label: '재제출', text: '과제 기준에 미달합니다. 수정 후 재제출 바랍니다.' },
  { label: '형식오류', text: '제출 파일 형식 또는 양식이 올바르지 않습니다. 확인 후 다시 제출해 주세요.' },
];

function loadTemplates(): FeedbackTemplate[] {
  try {
    const json = localStorage.getItem(STORAGE_KEY);
    const parsed = json ? JSON.parse(json) : null;
    if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    return DEFAULT_TEMPLATES;
  } catch {
    return DEFAULT_TEMPLATES;
  }
}

function saveTemplates(templates: FeedbackTemplate[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(templates));
}

export function FeedbackTemplateTab() {
  const [templates, setTemplates] = useState<FeedbackTemplate[]>(() => loadTemplates());
  const [saved, setSaved] = useState(true);

  useEffect(() => {
    setSaved(false);
  }, [templates]);

  const handleAdd = () => {
    setTemplates((prev) => [...prev, { label: '', text: '' }]);
  };

  const handleRemove = (idx: number) => {
    setTemplates((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleChange = (idx: number, field: 'label' | 'text', value: string) => {
    setTemplates((prev) =>
      prev.map((t, i) => (i === idx ? { ...t, [field]: value } : t)),
    );
  };

  const handleSave = () => {
    const valid = templates.filter((t) => t.label.trim() && t.text.trim());
    if (valid.length === 0) {
      alert('최소 1개 이상의 템플릿이 필요합니다.');
      return;
    }
    saveTemplates(valid);
    setTemplates(valid);
    setSaved(true);
  };

  const handleReset = () => {
    if (!confirm('기본 템플릿으로 초기화하시겠습니까?')) return;
    setTemplates(DEFAULT_TEMPLATES);
    saveTemplates(DEFAULT_TEMPLATES);
    setSaved(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">피드백 템플릿 관리</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            과제 피드백 시 사용할 빠른 템플릿의 라벨과 문구를 편집합니다.
          </p>
        </div>
        <div className="flex items-center gap-2">
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
            className={`flex items-center gap-1.5 px-4 py-2 text-sm rounded-lg transition-colors ${
              saved
                ? 'bg-green-600 text-white'
                : 'bg-blue-600 text-white hover:bg-blue-700'
            }`}
          >
            <Save className="w-4 h-4" />
            {saved ? '저장됨' : '저장'}
          </button>
        </div>
      </div>

      {/* 미리보기 */}
      <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
        <div className="text-sm text-gray-500 mb-2">미리보기 (피드백 입력 시 표시되는 버튼)</div>
        <div className="flex flex-wrap gap-1.5">
          {templates.filter((t) => t.label.trim()).map((tpl, idx) => (
            <span
              key={idx}
              className="px-2.5 py-1 text-xs rounded-full border border-blue-200 bg-blue-50 text-blue-700"
            >
              {tpl.label}
            </span>
          ))}
          {templates.filter((t) => t.label.trim()).length === 0 && (
            <span className="text-sm text-gray-400">템플릿을 추가해 주세요</span>
          )}
        </div>
      </div>

      {/* 템플릿 편집 목록 */}
      {templates.length === 0 ? (
        <div className="text-center py-12 border-2 border-dashed border-gray-200 rounded-lg">
          <MessageSquare className="w-10 h-10 mx-auto text-gray-300 mb-2" />
          <p className="text-gray-500 mb-1">템플릿이 없습니다.</p>
          <p className="text-sm text-gray-400">"추가" 버튼을 눌러 새 템플릿을 만들어 보세요.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {templates.map((tpl, idx) => (
            <div
              key={idx}
              className="bg-white border border-gray-200 rounded-lg p-4 flex gap-4 items-start"
            >
              <div className="text-sm font-medium text-gray-400 mt-2 w-6 text-center flex-shrink-0">
                {idx + 1}
              </div>
              <div className="flex-1 space-y-2">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">라벨 (버튼에 표시)</label>
                  <input
                    type="text"
                    value={tpl.label}
                    onChange={(e) => handleChange(idx, 'label', e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="예: 우수"
                    maxLength={10}
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
                onClick={() => handleRemove(idx)}
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
