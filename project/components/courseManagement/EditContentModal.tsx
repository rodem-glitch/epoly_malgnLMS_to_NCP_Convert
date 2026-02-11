import React, { useState, useEffect } from 'react';
import { X, Video, FileText, ClipboardList, BookOpen, FolderOpen } from 'lucide-react';
import { ContentLibraryModal } from '../ContentLibraryModal';
import type { WeekContentItem, ContentType } from './WeeklyContentModal';

interface EditContentModalProps {
  isOpen: boolean;
  onClose: () => void;
  content: WeekContentItem | null;
  courseName?: string;
  onSave: (content: WeekContentItem) => void;
}

export function EditContentModal({ isOpen, onClose, content, courseName, onSave }: EditContentModalProps) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [duration, setDuration] = useState('');
  const [showLibrary, setShowLibrary] = useState(false);
  const [selectedVideo, setSelectedVideo] = useState<{
    mediaKey: string;
    lessonId?: number;
    title: string;
    duration?: string;
  } | null>(null);

  // content가 변경될 때 폼 값 초기화
  useEffect(() => {
    if (content) {
      // 콘텐츠 제목이 원본 영상 제목과 다르면 커스텀 제목으로 표시
      const originalTitle = content.originalVideoTitle || content.title;
      const isCustomTitle = content.title !== originalTitle;
      setTitle(isCustomTitle ? content.title : '');
      setDescription(content.description || '');
      setDuration(content.duration || '');
      if (content.type === 'video' && content.mediaKey) {
        setSelectedVideo({
          mediaKey: content.mediaKey,
          lessonId: content.lessonId,
          title: content.originalVideoTitle || content.title, // 원본 영상 제목 사용
          duration: content.duration,
        });
      } else {
        setSelectedVideo(null);
      }
    }
  }, [content]);

  if (!isOpen || !content) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (content.type === 'video') {
      if (!selectedVideo) return;
      onSave({
        ...content,
        // 콘텐츠 제목이 입력되었으면 사용, 아니면 영상 제목 사용
        title: title.trim() || selectedVideo.title,
        description: description.trim() || undefined,
        duration: selectedVideo.duration,
        mediaKey: selectedVideo.mediaKey,
        lessonId: selectedVideo.lessonId,
        originalVideoTitle: selectedVideo.title, // 원본 영상 제목 유지
      });
    } else {
      if (!title.trim()) return;
      onSave({
        ...content,
        title: title.trim(),
        description: description.trim() || undefined,
      });
    }
  };

  const handleVideoSelect = (videoContent: any) => {
    setSelectedVideo({
      mediaKey: videoContent.mediaKey,
      lessonId: videoContent.lessonId,
      title: videoContent.title,
      duration: videoContent.duration || (videoContent.totalTime ? `${videoContent.totalTime}분` : undefined),
    });
    setShowLibrary(false);
  };

  const getContentTypeLabel = (type: ContentType) => {
    switch (type) {
      case 'video': return '동영상';
      case 'exam': return '시험';
      case 'assignment': return '과제';
      case 'document': return '학습자료';
    }
  };

  const getContentIcon = (type: ContentType) => {
    switch (type) {
      case 'video': return <Video className="w-5 h-5 text-blue-600" />;
      case 'exam': return <ClipboardList className="w-5 h-5 text-red-600" />;
      case 'assignment': return <BookOpen className="w-5 h-5 text-purple-600" />;
      case 'document': return <FileText className="w-5 h-5 text-green-600" />;
    }
  };

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-center justify-center">
        {/* Backdrop */}
        <div className="absolute inset-0 bg-black/50" onClick={onClose} />

        {/* Modal */}
        <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
            <div className="flex items-center gap-3">
              {getContentIcon(content.type)}
              <div>
                <h2 className="text-lg font-semibold text-gray-900">{getContentTypeLabel(content.type)} 편집</h2>
                <p className="text-sm text-gray-500 mt-0.5">{content.weekNumber}주차</p>
              </div>
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
            {/* 동영상: 선택된 영상 표시 및 변경 버튼 */}
            {content.type === 'video' && (
              <>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    선택한 동영상 <span className="text-red-500 ml-1">*</span>
                  </label>
                  {selectedVideo ? (
                    <div className="flex items-center justify-between p-4 bg-blue-50 border border-blue-200 rounded-lg">
                      <div className="flex items-center gap-3">
                        <Video className="w-5 h-5 text-blue-600" />
                        <div>
                          <p className="font-medium text-blue-900">{selectedVideo.title}</p>
                          {selectedVideo.duration && (
                            <p className="text-sm text-blue-600">재생시간: {selectedVideo.duration}</p>
                          )}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => setShowLibrary(true)}
                        className="px-3 py-1.5 text-sm text-blue-600 bg-white border border-blue-300 rounded-lg hover:bg-blue-50 transition-colors"
                      >
                        변경
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setShowLibrary(true)}
                      className="w-full flex items-center justify-center gap-2 p-4 border-2 border-dashed border-gray-300 rounded-lg text-gray-500 hover:border-blue-400 hover:text-blue-600 transition-colors"
                    >
                      <FolderOpen className="w-5 h-5" />
                      <span>콘텐츠 라이브러리에서 영상 선택</span>
                    </button>
                  )}
                </div>
                
                {/* 콘텐츠 제목 (선택사항) */}
                {selectedVideo && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      콘텐츠 제목 (선택)
                    </label>
                    <input
                      type="text"
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      placeholder={selectedVideo.title}
                      className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    />
                    <p className="text-xs text-gray-400 mt-1">
                      비워두면 동영상 제목이 사용됩니다.
                    </p>
                  </div>
                )}
              </>
            )}

            {/* 시험/과제/학습자료: 제목 입력 */}
            {content.type !== 'video' && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  {getContentTypeLabel(content.type)} 제목
                  <span className="text-red-500 ml-1">*</span>
                </label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="제목을 입력하세요"
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  required
                />
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">설명 (선택)</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="콘텐츠에 대한 간단한 설명을 입력하세요"
                rows={3}
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
              />
            </div>

            {/* Footer */}
            <div className="flex justify-end gap-3 pt-4 border-t border-gray-100">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={
                  (content.type === 'video' && !selectedVideo) ||
                  (content.type !== 'video' && !title.trim())
                }
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                저장
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
        multiSelect={false}
        recommendContext={{
          // 왜: 편집 모달에서도 과목명이 빠지면 과목 간 추천이 같아질 수 있어 기본 힌트로 고정 전달합니다.
          courseName: courseName || '',
          // 왜: 편집 화면에서도 현재 입력된 제목/설명을 힌트로 써서 추천을 먼저 보여주면 탐색이 줄어듭니다.
          lessonTitle: title || selectedVideo?.title || '',
          lessonDescription: description,
        }}
      />
    </>
  );
}
