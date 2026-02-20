import React, { useState, useEffect } from 'react';
import { X, Video, FileText, ClipboardList, BookOpen, FolderOpen, Trash2 } from 'lucide-react';
import { ContentLibraryModal } from '../ContentLibraryModal';
import { Exam, getExamList } from '../ExamManagementPage';

export type ContentType = 'video' | 'exam' | 'assignment' | 'document';

export interface WeekContentItem {
  id: string;
  weekNumber: number;
  type: ContentType;
  title: string;
  description?: string;
  duration?: string;
  completeTime?: number; // 인정시간(분 단위) - 동영상 콘텐츠에서 사용
  createdAt: string;
  // 콘텐츠 라이브러리에서 선택한 영상 정보
  mediaKey?: string;
  lessonId?: number;
  originalVideoTitle?: string;
  // 시험 관련 정보
  examId?: string;
  examSettings?: {
    startDate: string;
    startTime: string;
    endDate: string;
    endTime: string;
    points: number;
    allowRetake: boolean;
    retakeScore: number;
    retakeCount: number;
    showResults: boolean;
  };
  assignmentSettings?: {
    dueDate: string;
    dueTime: string;
    totalScore: number;
    submissionType: string;
    fileTypes?: string;
    maxFileSize: number;
    allowLateSubmission: boolean;
    latePenalty: number;
    fileName?: string;
  };
  documentSettings?: {
    link?: string;
    fileName?: string;
  };
}

interface WeeklyContentModalProps {
  isOpen: boolean;
  onClose: () => void;
  weekNumber: number;
  courseName?: string;
  onAdd: (content: Omit<WeekContentItem, 'id' | 'createdAt'>) => void;
}

const buildDefaultExamSettings = () => {
  const today = new Date().toISOString().split('T')[0];
  const nextWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

  return {
    startDate: today,
    startTime: '09:00',
    endDate: nextWeek,
    endTime: '18:00',
    points: 0,
    allowRetake: false,
    retakeScore: 0,
    retakeCount: 0,
    showResults: true,
  };
};

const buildDefaultAssignmentData = () => ({
  title: '',
  description: '',
  dueDate: '',
  dueTime: '',
  totalScore: 100,
  submissionType: 'file',
  fileTypes: '',
  maxFileSize: 10,
  allowLateSubmission: false,
  latePenalty: 0,
  file: null as File | null,
});

const buildDefaultDocumentData = () => ({
  title: '',
  description: '',
  link: '',
  file: null as File | null,
});

export function WeeklyContentModal({ isOpen, onClose, weekNumber, courseName, onAdd }: WeeklyContentModalProps) {
  const [selectedType, setSelectedType] = useState<ContentType | null>(null);
  const [assignmentData, setAssignmentData] = useState(buildDefaultAssignmentData());
  const [documentData, setDocumentData] = useState(buildDefaultDocumentData());
  
  // 콘텐츠 라이브러리 모달 상태
  const [showLibrary, setShowLibrary] = useState(false);
  // 왜: 여러 영상을 한 번에 선택하고, 각각에 제목/설명을 편집할 수 있도록 배열로 관리합니다.
  const [selectedVideos, setSelectedVideos] = useState<Array<{
    mediaKey: string;
    lessonId?: number;
    title: string;
    customTitle: string;
    description: string;
    duration?: string;
    totalTime?: number;
  }>>([]);

  // 시험 선택 상태
  const [examList, setExamList] = useState<Exam[]>([]);
  const [selectedExamId, setSelectedExamId] = useState<string>('');
  const [examSettings, setExamSettings] = useState(buildDefaultExamSettings());

  // 왜: 모달을 열 때마다 이전 입력 내역을 초기화하여 새 상태에서 시작합니다.
  useEffect(() => {
    if (isOpen) {
      setSelectedType(null);
      setSelectedVideos([]);
      setSelectedExamId('');
      setExamSettings(buildDefaultExamSettings());
      setAssignmentData(buildDefaultAssignmentData());
      setDocumentData(buildDefaultDocumentData());
      setShowLibrary(false);
    }
  }, [isOpen]);

  // 시험 목록 로드
  useEffect(() => {
    if (!isOpen || selectedType !== 'exam') return;
    let cancelled = false;

    const loadExams = async () => {
      try {
        const exams = await getExamList();
        if (!cancelled) setExamList(exams);
      } catch {
        if (!cancelled) setExamList([]);
      }
    };

    void loadExams();
    return () => {
      cancelled = true;
    };
  }, [isOpen, selectedType]);

  // 선택한 시험 정보
  const selectedExam = examList.find(e => e.id === selectedExamId);

  // 시험 선택 시 배점 자동 설정
  useEffect(() => {
    if (selectedExam) {
      setExamSettings(prev => ({
        ...prev,
        points: selectedExam.totalPoints,
      }));
    }
  }, [selectedExam]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedType) return;
    
    // 동영상
    if (selectedType === 'video') {
      if (selectedVideos.length === 0) return;
      // 왜: 여러 영상 각각에 대해 onAdd를 호출하여 개별 콘텐츠 항목으로 등록합니다.
      for (const video of selectedVideos) {
        onAdd({
          weekNumber,
          type: selectedType,
          title: video.customTitle.trim() || video.title,
          description: video.description.trim() || undefined,
          duration: video.duration,
          completeTime: video.totalTime || 0,
          mediaKey: video.mediaKey,
          lessonId: video.lessonId,
          originalVideoTitle: video.title,
        });
      }
    }
    // 시험
    else if (selectedType === 'exam') {
      if (!selectedExamId) return;
      onAdd({
        weekNumber,
        type: selectedType,
        title: selectedExam?.title || '시험',
        description: selectedExam?.description?.trim() || undefined,
        examId: selectedExamId,
        examSettings: {
          startDate: examSettings.startDate,
          startTime: examSettings.startTime,
          endDate: examSettings.endDate,
          endTime: examSettings.endTime,
          points: examSettings.points,
          allowRetake: examSettings.allowRetake,
          retakeScore: examSettings.retakeScore,
          retakeCount: examSettings.retakeCount,
          showResults: examSettings.showResults,
        },
      });
    }
    // 과제
    else if (selectedType === 'assignment') {
      if (!assignmentData.title.trim()) return;
      if (!assignmentData.description.trim()) return;
      if (!assignmentData.dueDate || !assignmentData.dueTime) return;

      onAdd({
        weekNumber,
        type: selectedType,
        title: assignmentData.title.trim(),
        description: assignmentData.description.trim(),
        // 왜: 강의목차 JSON에 파일 객체를 직접 저장하면 직렬화 오류가 나므로 파일명만 보관합니다.
        assignmentSettings: {
          dueDate: assignmentData.dueDate,
          dueTime: assignmentData.dueTime,
          totalScore: Number(assignmentData.totalScore || 0),
          submissionType: assignmentData.submissionType,
          fileTypes: assignmentData.fileTypes.trim() || undefined,
          maxFileSize: Number(assignmentData.maxFileSize || 0),
          allowLateSubmission: assignmentData.allowLateSubmission,
          latePenalty: Number(assignmentData.latePenalty || 0),
          fileName: assignmentData.file ? assignmentData.file.name : undefined,
        },
      });
    }
    // 자료
    else if (selectedType === 'document') {
      if (!documentData.title.trim()) return;
      const link = documentData.link.trim();
      const fileName = documentData.file ? documentData.file.name : '';
      if (!link && !fileName) return;

      onAdd({
        weekNumber,
        type: selectedType,
        title: documentData.title.trim(),
        description: documentData.description.trim() || undefined,
        // 왜: 자료는 목록 표시용으로만 저장되므로 파일 자체 대신 링크/파일명만 기록합니다.
        documentSettings: {
          link: link || undefined,
          fileName: fileName || undefined,
        },
      });
    }

    // 폼 초기화
    resetForm();
    onClose();
  };

  const resetForm = () => {
    setSelectedType(null);
    setSelectedVideos([]);
    setSelectedExamId('');
    setExamSettings(buildDefaultExamSettings());
    setAssignmentData(buildDefaultAssignmentData());
    setDocumentData(buildDefaultDocumentData());
  };

  // 왜: 여러 영상 선택을 위해 단일 콘텐츠 대신 배열에 추가합니다.
  const handleVideoSelect = (content: any) => {
    setSelectedVideos(prev => {
      // 이미 선택된 영상은 중복 추가하지 않음
      if (prev.some(v => v.mediaKey === content.mediaKey)) return prev;
      return [...prev, {
        mediaKey: content.mediaKey,
        lessonId: content.lessonId,
        title: content.title,
        customTitle: '',
        description: '',
        duration: content.duration || (content.totalTime ? `${content.totalTime}분` : undefined),
        totalTime: content.totalTime || 0,
      }];
    });
    setShowLibrary(false);
  };

  // 왜: 멀티셀렉트에서 여러 영상을 한 번에 수신합니다.
  const handleMultiVideoSelect = (contents: any[]) => {
    setSelectedVideos(prev => {
      const existingKeys = new Set(prev.map(v => v.mediaKey));
      const newItems = contents
        .filter(c => !existingKeys.has(c.mediaKey))
        .map(c => ({
          mediaKey: c.mediaKey,
          lessonId: c.lessonId,
          title: c.title,
          customTitle: '',
          description: '',
          duration: c.duration || (c.totalTime ? `${c.totalTime}분` : undefined),
          totalTime: c.totalTime || 0,
        }));
      return [...prev, ...newItems];
    });
    setShowLibrary(false);
  };

  const handleRemoveVideo = (mediaKey: string) => {
    setSelectedVideos(prev => prev.filter(v => v.mediaKey !== mediaKey));
  };

  const updateVideoField = (mediaKey: string, field: 'customTitle' | 'description', value: string) => {
    setSelectedVideos(prev => prev.map(v =>
      v.mediaKey === mediaKey ? { ...v, [field]: value } : v
    ));
  };

  const contentTypes = [
    { type: 'video' as ContentType, icon: Video, label: '동영상', color: 'blue', desc: '콘텐츠 라이브러리에서 선택' },
    { type: 'exam' as ContentType, icon: ClipboardList, label: '시험', color: 'red', desc: '시험관리에서 시험을 선택합니다' },
    { type: 'assignment' as ContentType, icon: BookOpen, label: '과제', color: 'purple', desc: '해당 주차에 과제를 등록합니다' },
    { type: 'document' as ContentType, icon: FileText, label: '학습자료', color: 'green', desc: 'PDF, 문서 등 학습자료를 추가합니다' },
  ];

  const getColorClasses = (color: string, isSelected: boolean) => {
    if (isSelected) {
      switch (color) {
        case 'blue': return 'border-blue-500 bg-blue-50 text-blue-700';
        case 'red': return 'border-red-500 bg-red-50 text-red-700';
        case 'purple': return 'border-purple-500 bg-purple-50 text-purple-700';
        case 'green': return 'border-green-500 bg-green-50 text-green-700';
        default: return 'border-gray-500 bg-gray-50 text-gray-700';
      }
    }
    return 'border-gray-200 hover:border-gray-300 hover:bg-gray-50';
  };

  const getIconColorClass = (color: string) => {
    switch (color) {
      case 'blue': return 'text-blue-600';
      case 'red': return 'text-red-600';
      case 'purple': return 'text-purple-600';
      case 'green': return 'text-green-600';
      default: return 'text-gray-600';
    }
  };

  const isAssignmentInvalid = selectedType === 'assignment' && (
    !assignmentData.title.trim() ||
    !assignmentData.description.trim() ||
    !assignmentData.dueDate ||
    !assignmentData.dueTime
  );
  const isDocumentInvalid = selectedType === 'document' && (
    !documentData.title.trim() ||
    (!documentData.link.trim() && !documentData.file)
  );
  const isSubmitDisabled = (
    !selectedType ||
    (selectedType === 'video' && selectedVideos.length === 0) ||
    (selectedType === 'exam' && !selectedExamId) ||
    isAssignmentInvalid ||
    isDocumentInvalid
  );
  const submitLabel = selectedType === 'exam'
    ? '시험추가'
    : selectedType === 'assignment'
      ? '등록'
      : selectedType === 'document'
        ? '업로드'
        : selectedType === 'video' && selectedVideos.length > 0
          ? `추가하기 (${selectedVideos.length}건)`
          : '추가하기';

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center">
        {/* Backdrop */}
        <div className="absolute inset-0 bg-black/50" onClick={onClose} />

        {/* Modal */}
        <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">{weekNumber}주차 콘텐츠 추가</h2>
              <p className="text-sm text-gray-500 mt-0.5">추가할 콘텐츠 유형을 선택해 주세요</p>
            </div>
            <button
              onClick={onClose}
              className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Body */}
          <form onSubmit={handleSubmit} className="p-6 space-y-6">
            {/* Step 1: 콘텐츠 유형 선택 */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-3">콘텐츠 유형</label>
              <div className="grid grid-cols-2 gap-3">
                {contentTypes.map(({ type, icon: Icon, label, color, desc }) => (
                  <button
                    key={type}
                    type="button"
                    onClick={() => {
                      setSelectedType(type);
                      setSelectedVideos([]);
                      setSelectedExamId('');
                      setAssignmentData(buildDefaultAssignmentData());
                      setDocumentData(buildDefaultDocumentData());
                      setExamSettings(buildDefaultExamSettings());
                    }}
                    className={`flex flex-col items-start p-4 border-2 rounded-lg transition-all ${getColorClasses(color, selectedType === type)}`}
                  >
                    <Icon className={`w-6 h-6 mb-2 ${selectedType === type ? '' : getIconColorClass(color)}`} />
                    <span className="font-medium">{label}</span>
                    <span className={`text-xs mt-1 ${selectedType === type ? 'opacity-80' : 'text-gray-500'}`}>
                      {desc}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            {/* Step 2: 상세 정보 입력 */}
            {selectedType && (
              <>
                {/* 동영상 선택 */}
                {selectedType === 'video' && (
                  <>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        선택한 동영상 <span className="text-red-500 ml-1">*</span>
                        {selectedVideos.length > 0 && (
                          <span className="ml-2 text-blue-600 font-normal">({selectedVideos.length}건 선택됨)</span>
                        )}
                      </label>

                      {/* 선택된 영상 목록 */}
                      {selectedVideos.length > 0 && (
                        <div className="space-y-3 mb-3">
                          {selectedVideos.map((video, idx) => (
                            <div key={video.mediaKey} className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
                              <div className="flex items-center justify-between mb-3">
                                <div className="flex items-center gap-2">
                                  <span className="flex items-center justify-center w-6 h-6 bg-blue-600 text-white text-xs font-bold rounded-full">
                                    {idx + 1}
                                  </span>
                                  <Video className="w-4 h-4 text-blue-600" />
                                  <span className="text-sm font-medium text-blue-900 truncate max-w-xs">
                                    {video.title}
                                  </span>
                                  {video.duration && (
                                    <span className="text-xs text-blue-500">({video.duration})</span>
                                  )}
                                </div>
                                <button
                                  type="button"
                                  onClick={() => handleRemoveVideo(video.mediaKey)}
                                  className="p-1 text-red-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors"
                                  title="선택 해제"
                                >
                                  <Trash2 className="w-4 h-4" />
                                </button>
                              </div>
                              <div className="space-y-2">
                                <div>
                                  <label className="block text-xs font-medium text-gray-600 mb-1">제목 (선택)</label>
                                  <input
                                    type="text"
                                    value={video.customTitle}
                                    onChange={(e) => updateVideoField(video.mediaKey, 'customTitle', e.target.value)}
                                    placeholder={video.title}
                                    className="w-full px-3 py-2 text-sm border border-blue-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                  />
                                </div>
                                <div>
                                  <label className="block text-xs font-medium text-gray-600 mb-1">설명 (선택)</label>
                                  <input
                                    type="text"
                                    value={video.description}
                                    onChange={(e) => updateVideoField(video.mediaKey, 'description', e.target.value)}
                                    placeholder="콘텐츠에 대한 간단한 설명"
                                    className="w-full px-3 py-2 text-sm border border-blue-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                  />
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* 영상 추가 버튼 */}
                      <button
                        type="button"
                        onClick={() => setShowLibrary(true)}
                        className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-gray-300 rounded-lg text-gray-500 hover:border-blue-400 hover:text-blue-600 transition-colors"
                      >
                        <FolderOpen className="w-5 h-5" />
                        <span>{selectedVideos.length > 0 ? '영상 더 추가하기' : '콘텐츠 라이브러리에서 영상 선택'}</span>
                      </button>
                      {selectedVideos.length === 0 && (
                        <p className="text-xs text-gray-400 mt-1">
                          여러 영상을 한 번에 선택할 수 있습니다.
                        </p>
                      )}
                    </div>
                  </>
                )}

                {/* 시험 선택 및 상세 설정 */}
                {selectedType === 'exam' && (
                  <>
                    {/* 시험 선택 */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        시험 선택 <span className="text-red-500 ml-1">*</span>
                      </label>
                      {examList.length > 0 ? (
                        <select
                          value={selectedExamId}
                          onChange={(e) => setSelectedExamId(e.target.value)}
                          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        >
                          <option value="">시험을 선택하세요</option>
                          {examList.map(exam => (
                            <option key={exam.id} value={exam.id}>
                              {exam.title} ({exam.questionIds.length}문제, {exam.totalPoints}점)
                            </option>
                          ))}
                        </select>
                      ) : (
                        <div className="p-4 bg-gray-50 rounded-lg text-center text-gray-500">
                          <ClipboardList className="w-8 h-8 mx-auto mb-2 opacity-50" />
                          <p className="text-sm">등록된 시험이 없습니다.</p>
                          <p className="text-xs mt-1">시험관리 메뉴에서 먼저 시험을 생성해주세요.</p>
                        </div>
                      )}
                    </div>

                    {/* 시험 상세 설정 (시험 선택 후) */}
                    {selectedExamId && (
                      <div className="space-y-4 pt-4 border-t border-gray-100">
                        {/* 응시 가능 기간 */}
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-2">응시 가능 기간</label>
                          <div className="flex items-center gap-2 flex-wrap">
                            <input
                              type="date"
                              value={examSettings.startDate}
                              onChange={(e) => setExamSettings(prev => ({ ...prev, startDate: e.target.value }))}
                              className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <input
                              type="time"
                              value={examSettings.startTime}
                              onChange={(e) => setExamSettings(prev => ({ ...prev, startTime: e.target.value }))}
                              className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <span className="text-gray-500">부터</span>
                            <input
                              type="date"
                              value={examSettings.endDate}
                              onChange={(e) => setExamSettings(prev => ({ ...prev, endDate: e.target.value }))}
                              min={examSettings.startDate}
                              className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <input
                              type="time"
                              value={examSettings.endTime}
                              onChange={(e) => setExamSettings(prev => ({ ...prev, endTime: e.target.value }))}
                              className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            <span className="text-gray-500">까지</span>
                          </div>
                        </div>

                        {/* 배점 */}
                        <div className="flex items-center gap-3">
                          <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">배점</label>
                          <input
                            type="number"
                            value={examSettings.points}
                            onChange={(e) => setExamSettings(prev => ({ ...prev, points: parseInt(e.target.value) || 0 }))}
                            min={0}
                            className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                          <span className="text-sm text-gray-600">점</span>
                        </div>

                        {/* 재응시 가능여부 */}
                        <div className="flex items-center gap-3">
                          <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">재응시 가능여부</label>
                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={examSettings.allowRetake}
                              onChange={(e) => setExamSettings(prev => ({ ...prev, allowRetake: e.target.checked }))}
                              className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                            />
                            <span className="text-sm text-gray-600">재응시 가능</span>
                          </label>
                        </div>

                        {/* 재응시 기준 점수 */}
                        {examSettings.allowRetake && (
                          <>
                            <div className="flex items-center gap-3">
                              <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">재응시 기준 점수</label>
                              <input
                                type="number"
                                value={examSettings.retakeScore}
                                onChange={(e) => setExamSettings(prev => ({ ...prev, retakeScore: parseInt(e.target.value) || 0 }))}
                                min={0}
                                max={100}
                                className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                              />
                              <span className="text-sm text-gray-600">점 미만일때 재응시 가능</span>
                            </div>

                            {/* 재응시 가능 횟수 */}
                            <div className="flex items-center gap-3">
                              <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">재응시 가능 횟수</label>
                              <input
                                type="number"
                                value={examSettings.retakeCount}
                                onChange={(e) => setExamSettings(prev => ({ ...prev, retakeCount: parseInt(e.target.value) || 0 }))}
                                min={0}
                                className="w-16 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                              />
                              <span className="text-sm text-gray-600">회</span>
                            </div>
                          </>
                        )}

                        {/* 시험결과노출 */}
                        <div className="flex items-center gap-3">
                          <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">시험결과노출</label>
                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={examSettings.showResults}
                              onChange={(e) => setExamSettings(prev => ({ ...prev, showResults: e.target.checked }))}
                              className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                            />
                            <span className="text-sm text-gray-600">노출</span>
                          </label>
                          <span className="text-xs text-gray-400">▶ 응시 후 수강생이 정답을 확인할 수 있습니다.</span>
                        </div>
                      </div>
                    )}
                  </>
                )}

                {/* 과제 등록 폼 */}
                {selectedType === 'assignment' && (
                  <>
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

                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="block text-sm text-gray-700 mb-2">
                          마감 날짜 <span className="text-red-500">*</span>
                        </label>
                        <input
                          type="date"
                          value={assignmentData.dueDate}
                          onChange={(e) => setAssignmentData({ ...assignmentData, dueDate: e.target.value })}
                          className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
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
                  </>
                )}

                {/* 자료 등록 폼 */}
                {selectedType === 'document' && (
                  <>
                    <div>
                      <label className="block text-sm text-gray-700 mb-2">
                        자료명 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="text"
                        value={documentData.title}
                        onChange={(e) => setDocumentData({ ...documentData, title: e.target.value })}
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        placeholder="예: 강의자료.pdf"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm text-gray-700 mb-2">설명</label>
                      <textarea
                        value={documentData.description}
                        onChange={(e) => setDocumentData({ ...documentData, description: e.target.value })}
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        rows={3}
                        placeholder="자료에 대한 간단한 설명을 입력하세요"
                      />
                    </div>

                    <div>
                      <label className="block text-sm text-gray-700 mb-2">링크(선택)</label>
                      <input
                        type="url"
                        value={documentData.link}
                        onChange={(e) => setDocumentData({ ...documentData, link: e.target.value })}
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        placeholder="https://..."
                      />
                      <p className="text-sm text-gray-500 mt-1">파일 업로드 대신 링크만 등록할 수도 있습니다.</p>
                    </div>

                    <div>
                      <label className="block text-sm text-gray-700 mb-2">파일(선택)</label>
                      <input
                        type="file"
                        onChange={(e) =>
                          setDocumentData({ ...documentData, file: e.target.files?.[0] ?? null })
                        }
                        className="w-full"
                      />
                      <p className="text-sm text-gray-500 mt-1">파일 또는 링크 중 하나는 필수입니다.</p>
                      {documentData.file && (
                        <p className="text-xs text-gray-500 mt-1">선택됨: {documentData.file.name}</p>
                      )}
                    </div>
                  </>
                )}
              </>
            )}

            {/* Footer */}
            <div className="flex gap-3 pt-4 border-t border-gray-100">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={isSubmitDisabled}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitLabel}
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* 콘텐츠 라이브러리 모달 */}
      <ContentLibraryModal
        isOpen={showLibrary}
        onClose={() => setShowLibrary(false)}
        onSelect={handleVideoSelect}
        multiSelect={true}
        onMultiSelect={handleMultiVideoSelect}
        recommendContext={{
          courseName: courseName || '',
        }}
        // 왜: 이미 선택된 영상은 라이브러리에서 제외하여 중복 선택을 방지합니다.
        excludeLessonIds={selectedVideos.filter(v => v.lessonId).map(v => v.lessonId!)}
      />
    </>
  );
}
