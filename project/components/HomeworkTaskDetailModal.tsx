import React, { useEffect, useRef, useState } from 'react';
import { X, CheckCircle, MessageSquare, Clock, User, Paperclip, Trash2 } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';

interface HomeworkTaskDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  courseId: number;
  homeworkId: number;
  courseUserId: number;
  task: any;
  onRefresh: () => void;
}

export function HomeworkTaskDetailModal({
  isOpen,
  onClose,
  courseId,
  homeworkId,
  courseUserId,
  task,
  onRefresh,
}: HomeworkTaskDetailModalProps) {
  const [feedback, setFeedback] = useState('');
  const [loading, setLoading] = useState(false);
  const [fileBusy, setFileBusy] = useState(false);
  // 왜: 교수자가 첨삭 파일을 첨부할 수 있도록 파일 목록을 관리합니다.
  const [feedbackFiles, setFeedbackFiles] = useState<File[]>([]);
  const [uploadedFeedbackFiles, setUploadedFeedbackFiles] = useState<
    { id: number; filename: string; downloadUrl: string }[]
  >([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isOpen || !task) return;
    setFeedback(String(task.feedback ?? ''));
    setFeedbackFiles([]);
  }, [isOpen, task]);

  const fetchUploadedFeedbackFiles = async () => {
    if (!homeworkId || !courseUserId) {
      setUploadedFeedbackFiles([]);
      return;
    }

    try {
      const res = await tutorLmsApi.getHomeworkFeedbackFiles({ courseId, homeworkId, courseUserId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
      setUploadedFeedbackFiles(
        rows.map((row: any) => ({
          id: Number(row.id),
          filename: String(row.filename ?? ''),
          downloadUrl: String(row.download_url ?? ''),
        }))
      );
    } catch {
      setUploadedFeedbackFiles([]);
    }
  };

  useEffect(() => {
    if (!isOpen || !task) return;
    void fetchUploadedFeedbackFiles();
  }, [isOpen, task, courseId, homeworkId, courseUserId]);

  if (!isOpen || !task) return null;

  const handleDeleteUploadedFile = (fileId: number) => {
    if (!homeworkId || !courseUserId) return;
    if (!confirm('선택한 첨삭 파일을 삭제하시겠습니까?')) return;

    void (async () => {
      setFileBusy(true);
      try {
        const res = await tutorLmsApi.deleteHomeworkFeedbackFile({
          courseId,
          homeworkId,
          courseUserId,
          fileId,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        await fetchUploadedFeedbackFiles();
      } catch (e) {
        alert(e instanceof Error ? e.message : '첨삭 파일 삭제 중 오류가 발생했습니다.');
      } finally {
        setFileBusy(false);
      }
    })();
  };

  const handleConfirm = async () => {
    if (!homeworkId || !courseUserId) {
      alert('과제/수강생 정보가 없어 저장할 수 없습니다.');
      return;
    }

    setLoading(true);
    setFileBusy(true);
    try {
      const res = await tutorLmsApi.confirmHomeworkTask({
        courseId,
        taskId: task.id,
        feedback,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const pendingFiles = [...feedbackFiles];
      for (const file of pendingFiles) {
        const uploadRes = await tutorLmsApi.uploadHomeworkFeedbackFile({
          courseId,
          homeworkId,
          courseUserId,
          file,
        });
        if (uploadRes.rst_code !== '0000') {
          throw new Error(`[${file.name}] ${uploadRes.rst_message}`);
        }
      }

      setFeedbackFiles([]);
      await fetchUploadedFeedbackFiles();
      alert(pendingFiles.length > 0 ? '평가완료 및 첨삭 파일 저장이 완료되었습니다.' : '평가완료 처리되었습니다.');
      onRefresh();
      onClose();
    } catch (e) {
      alert(e instanceof Error ? e.message : '처리 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
      setFileBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between bg-white">
          <div className="flex items-center gap-2">
            <CheckCircle className="w-5 h-5 text-blue-600" />
            <h3 className="text-lg font-semibold text-gray-900">추가 과제 상세 확인</h3>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* 과제 지시 내용 */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
              <MessageSquare className="w-4 h-4" />
              <span>과제 지시 내용</span>
            </div>
            <div className="bg-blue-50 border border-blue-100 rounded-lg p-4 text-gray-800 text-sm whitespace-pre-wrap">
              {task.task}
            </div>
            <div className="flex items-center gap-4 text-xs text-gray-500 mt-1">
              <span className="flex items-center gap-1">
                <Clock className="w-3 h-3" />
                부여: {task.reg_date_conv}
              </span>
            </div>
          </div>

          <hr className="border-gray-100" />

          {/* 학생 제출 내용 */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
                <User className="w-4 h-4" />
                <span>학생 제출 내용</span>
              </div>
              <span className={`px-2 py-1 text-xs rounded font-medium ${
                task.submit_yn === 'Y' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'
              }`}>
                {task.submit_yn_label}
              </span>
            </div>

            {task.submit_yn === 'Y' ? (
              <div className="space-y-4">
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
                  {task.subject && (
                    <div className="font-semibold text-gray-900 mb-2 border-b border-gray-200 pb-2">
                      {task.subject}
                    </div>
                  )}
                  <div className="text-gray-800 text-sm whitespace-pre-wrap min-h-[100px]">
                    {task.content || '내용 없음'}
                  </div>
                </div>
                <div className="text-xs text-gray-500">
                  제출: {task.submit_date_conv}
                </div>
              </div>
            ) : (
              <div className="py-10 text-center text-gray-400 border border-dashed border-gray-200 rounded-lg">
                아직 제출된 내용이 없습니다.
              </div>
            )}
          </div>

          <hr className="border-gray-100" />

          {/* 피드백 입력 및 상태 변경 */}
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
              <CheckCircle className="w-4 h-4" />
              <span>교수자 피드백 및 평가</span>
            </div>
            <textarea
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
              placeholder="추가 과제에 대한 피드백을 입력하세요..."
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm resize-none min-h-[120px]"
            />

            {/* 왜: 서버 저장 파일과 이번에 추가할 파일을 구분해 보여야 첨삭 이력이 명확합니다. */}
            <div className="mt-3 space-y-2">
              <div className="flex items-center gap-2">
                <Paperclip className="w-4 h-4 text-gray-500" />
                <span className="text-sm text-gray-700">첨부파일</span>
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={fileBusy}
                  className="px-3 py-1 text-xs border border-blue-200 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors"
                >
                  + 파일 선택
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  className="hidden"
                  disabled={fileBusy}
                  onChange={(e) => {
                    const newFiles = Array.from(e.target.files || []);
                    if (newFiles.length > 0) {
                      setFeedbackFiles((prev) => [...prev, ...newFiles]);
                    }
                    // 왜: 같은 파일을 다시 선택할 수 있도록 value를 초기화합니다.
                    e.target.value = '';
                  }}
                />
              </div>
              {uploadedFeedbackFiles.length > 0 && (
                <div className="border border-gray-200 rounded-lg divide-y divide-gray-100">
                  {uploadedFeedbackFiles.map((file) => (
                    <div key={file.id} className="flex items-center justify-between px-3 py-2">
                      <a
                        href={file.downloadUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="flex items-center gap-2 text-sm text-blue-700 hover:underline truncate"
                        title={file.filename}
                      >
                        <Paperclip className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                        <span className="truncate">{file.filename}</span>
                      </a>
                      <button
                        type="button"
                        onClick={() => handleDeleteUploadedFile(file.id)}
                        disabled={fileBusy}
                        className="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors flex-shrink-0 disabled:opacity-50"
                        title="서버 파일 삭제"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
              {feedbackFiles.length > 0 && (
                <div className="border border-gray-200 rounded-lg divide-y divide-gray-100">
                  {feedbackFiles.map((file, idx) => (
                    <div key={`${file.name}-${idx}`} className="flex items-center justify-between px-3 py-2">
                      <div className="flex items-center gap-2 text-sm text-gray-700 truncate">
                        <Paperclip className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                        <span className="truncate">{file.name}</span>
                        <span className="text-xs text-gray-400 flex-shrink-0">
                          ({(file.size / 1024).toFixed(0)}KB)
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() => setFeedbackFiles((prev) => prev.filter((_, i) => i !== idx))}
                        disabled={fileBusy}
                        className="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors flex-shrink-0 disabled:opacity-50"
                        title="삭제"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
              {uploadedFeedbackFiles.length === 0 && feedbackFiles.length === 0 && (
                <div className="text-xs text-gray-400 pl-6">
                  첨삭 파일이 있으면 선택해 주세요. (선택사항)
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex justify-end gap-3 font-semibold">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-100 transition-colors"
          >
            닫기
          </button>
          {task.submit_yn === 'Y' && (
            <button
              onClick={handleConfirm}
              disabled={loading || fileBusy}
              className="px-6 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-400 flex items-center gap-2"
            >
              {loading ? (
                <span>처리 중...</span>
              ) : (
                <>
                  <CheckCircle className="w-4 h-4" />
                  <span>평가완료(확인)</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
