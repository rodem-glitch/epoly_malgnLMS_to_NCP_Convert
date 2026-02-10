import { useState, useEffect } from 'react';
import { X } from 'lucide-react';

interface AssignmentCreateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (assignmentData: any) => void;
  mode?: 'create' | 'edit';
  showWeekSession?: boolean;
  weekCount?: number;
  initialData?: {
    title?: string;
    description?: string;
    startDate?: string;
    startTime?: string;
    dueDate?: string;
    dueTime?: string;
    totalScore?: number;
    submissionType?: string;
    fileTypes?: string;
    maxFileSize?: number;
    allowLateSubmission?: boolean;
    latePenalty?: number;
    weekNumber?: number;
    sessionNumber?: number;
  };
}

export function AssignmentCreateModal({
  isOpen,
  onClose,
  onSave,
  mode = 'create',
  showWeekSession = false,
  weekCount = 15,
  initialData,
}: AssignmentCreateModalProps) {
  const sessionCount = 10;
  const today = new Date().toISOString().slice(0, 10);
  const [assignmentData, setAssignmentData] = useState({
    title: '',
    description: '',
    startDate: today,
    startTime: '00:00',
    dueDate: '',
    dueTime: '23:59',
    totalScore: 100,
    submissionType: 'file', // file, text, both
    fileTypes: '',
    maxFileSize: 10,
    allowLateSubmission: false,
    latePenalty: 0,
    weekNumber: 1,
    sessionNumber: 1,
    file: null as File | null,
  });

  // 왜: edit 모드일 때 initialData로 폼을 초기화합니다.
  useEffect(() => {
    if (isOpen && mode === 'edit' && initialData) {
      setAssignmentData({
        title: initialData.title || '',
        description: initialData.description || '',
        startDate: initialData.startDate || today,
        startTime: initialData.startTime || '00:00',
        dueDate: initialData.dueDate || '',
        dueTime: initialData.dueTime || '23:59',
        totalScore: initialData.totalScore ?? 100,
        submissionType: initialData.submissionType || 'file',
        fileTypes: initialData.fileTypes || '',
        maxFileSize: initialData.maxFileSize ?? 10,
        allowLateSubmission: initialData.allowLateSubmission ?? false,
        latePenalty: initialData.latePenalty ?? 0,
        weekNumber: initialData.weekNumber ?? 1,
        sessionNumber: initialData.sessionNumber ?? 1,
        file: null,
      });
    } else if (isOpen && mode === 'create') {
      // 왜: create 모드일 때는 빈 폼으로 초기화합니다.
      setAssignmentData({
        title: '',
        description: '',
        startDate: today,
        startTime: '00:00',
        dueDate: '',
        dueTime: '23:59',
        totalScore: 100,
        submissionType: 'file',
        fileTypes: '',
        maxFileSize: 10,
        allowLateSubmission: false,
        latePenalty: 0,
        weekNumber: 1,
        sessionNumber: 1,
        file: null,
      });
    }
  }, [isOpen, mode, initialData, today]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(assignmentData);
    onClose();
  };

  const isEditMode = mode === 'edit';

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h3 className="text-gray-900">{isEditMode ? '과제 수정' : '과제 등록'}</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {/* 과제 제목 */}
          <div>
            <label className="block text-sm text-gray-700 mb-2">
              과제 제목 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={assignmentData.title}
              onChange={(e) => setAssignmentData({ ...assignmentData, title: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="예: HTML 포트폴리오 페이지 제작"
              required
            />
          </div>

          {/* 왜: 학사 과목 등록 시 주차/차시를 지정해야 하므로 선택 UI를 보여줍니다. */}
          {showWeekSession && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-gray-700 mb-2">
                  주차 <span className="text-red-500">*</span>
                </label>
                <select
                  value={assignmentData.weekNumber}
                  onChange={(e) =>
                    setAssignmentData({ ...assignmentData, weekNumber: parseInt(e.target.value, 10) || 1 })
                  }
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                >
                  {Array.from({ length: weekCount }, (_, i) => i + 1).map((week) => (
                    <option key={week} value={week}>
                      {week}주차
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-gray-700 mb-2">
                  차시 <span className="text-red-500">*</span>
                </label>
                <select
                  value={assignmentData.sessionNumber}
                  onChange={(e) =>
                    setAssignmentData({ ...assignmentData, sessionNumber: parseInt(e.target.value, 10) || 1 })
                  }
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                >
                  {Array.from({ length: sessionCount }, (_, i) => i + 1).map((session) => (
                    <option key={session} value={session}>
                      {session}차시
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {/* 과제 설명 */}
          <div>
            <label className="block text-sm text-gray-700 mb-2">
              과제 설명 <span className="text-red-500">*</span>
            </label>
            <textarea
              value={assignmentData.description}
              onChange={(e) => setAssignmentData({ ...assignmentData, description: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              rows={4}
              placeholder="과제 내용 및 요구사항을 입력하세요"
              required
            />
          </div>

          {/* 제출 기간 */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-gray-700 mb-2">
                제출 시작 날짜 <span className="text-red-500">*</span>
              </label>
              <input
                type="date"
                value={assignmentData.startDate}
                onChange={(e) => setAssignmentData({ ...assignmentData, startDate: e.target.value })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">
                제출 시작 시간 <span className="text-red-500">*</span>
              </label>
              <input
                type="time"
                value={assignmentData.startTime}
                onChange={(e) => setAssignmentData({ ...assignmentData, startTime: e.target.value })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">
                마감 날짜 <span className="text-red-500">*</span>
              </label>
              <input
                type="date"
                value={assignmentData.dueDate}
                onChange={(e) => setAssignmentData({ ...assignmentData, dueDate: e.target.value })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                min={assignmentData.startDate || undefined}
                required
              />
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">
                마감 시간 <span className="text-red-500">*</span>
              </label>
              <input
                type="time"
                value={assignmentData.dueTime}
                onChange={(e) => setAssignmentData({ ...assignmentData, dueTime: e.target.value })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                required
              />
            </div>
          </div>

          {/* 배점 */}
          <div>
            <label className="block text-sm text-gray-700 mb-2">
              배점 <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              value={assignmentData.totalScore}
              onChange={(e) => setAssignmentData({ ...assignmentData, totalScore: parseInt(e.target.value) || 0 })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="예: 100"
              required
            />
          </div>

          {/* 제출 방식 */}
          <div>
            <label className="block text-sm text-gray-700 mb-2">
              제출 방식 <span className="text-red-500">*</span>
            </label>
            <select
              value={assignmentData.submissionType}
              onChange={(e) => setAssignmentData({ ...assignmentData, submissionType: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value="file">파일 업로드</option>
              <option value="text">텍스트 입력</option>
              <option value="both">파일 + 텍스트</option>
            </select>
          </div>

          {/* 파일 업로드 설정 */}
          {(assignmentData.submissionType === 'file' || assignmentData.submissionType === 'both') && (
            <>
              <div>
                <label className="block text-sm text-gray-700 mb-2">과제 첨부파일 (선택)</label>
                <div className="flex items-center gap-3">
                  <input
                    type="file"
                    onChange={(e) => {
                      const selected = e.target.files && e.target.files[0] ? e.target.files[0] : null;
                      setAssignmentData({ ...assignmentData, file: selected });
                    }}
                    className="flex-1 text-sm text-gray-700 file:mr-3 file:px-4 file:py-2 file:rounded-lg file:border-0 file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
                  />
                  {assignmentData.file && (
                    <button
                      type="button"
                      onClick={() => setAssignmentData({ ...assignmentData, file: null })}
                      className="px-3 py-2 text-sm border border-gray-300 rounded-lg text-gray-600 hover:bg-gray-50"
                    >
                      첨부 해제
                    </button>
                  )}
                </div>
                {assignmentData.file && (
                  <p className="text-xs text-gray-500 mt-1">선택됨: {assignmentData.file.name}</p>
                )}
              </div>
              <div>
                <label className="block text-sm text-gray-700 mb-2">허용 파일 형식</label>
                <input
                  type="text"
                  value={assignmentData.fileTypes}
                  onChange={(e) => setAssignmentData({ ...assignmentData, fileTypes: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="예: .pdf, .docx, .zip"
                />
                <p className="text-sm text-gray-500 mt-1">쉼표로 구분하여 입력하세요</p>
              </div>

              <div>
                <label className="block text-sm text-gray-700 mb-2">최대 파일 크기 (MB)</label>
                <input
                  type="number"
                  value={assignmentData.maxFileSize}
                  onChange={(e) => setAssignmentData({ ...assignmentData, maxFileSize: parseInt(e.target.value) || 0 })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
            </>
          )}

          {/* 지각 제출 설정 */}
          <div className="space-y-3">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={assignmentData.allowLateSubmission}
                onChange={(e) => setAssignmentData({ ...assignmentData, allowLateSubmission: e.target.checked })}
                className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700">지각 제출 허용</span>
            </label>

            {assignmentData.allowLateSubmission && (
              <div>
                <label className="block text-sm text-gray-700 mb-2">지각 제출 감점 (%)</label>
                <input
                  type="number"
                  value={assignmentData.latePenalty}
                  onChange={(e) => setAssignmentData({ ...assignmentData, latePenalty: parseInt(e.target.value) || 0 })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="예: 10"
                  min="0"
                  max="100"
                />
              </div>
            )}
          </div>

          {/* 버튼 */}
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
              {isEditMode ? '수정' : '등록'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
