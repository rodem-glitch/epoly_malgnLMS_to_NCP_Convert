import React, { useCallback, useEffect, useState } from 'react';
import {
  ChevronDown,
  ChevronRight,
  Copy,
  Download,
  Edit,
  FolderOpen,
  Plus,
  Save,
  Trash2,
  Upload,
  Video,
} from 'lucide-react';
import { ContentLibraryModal } from './ContentLibraryModal';

// 왜: 동영상 그룹 관리 — 주차별 영상 구성을 미리 만들어 놓고
//     차시관리(CurriculumTab)에서 불러올 수 있도록 하는 독립 페이지입니다.

// === 타입 ===
export interface VideoGroupItem {
  id: string;
  mediaKey: string;
  lessonId?: number;
  title: string;
  duration?: string;
  completeTime?: number; // 인정시간(분)
}

export interface VideoWeek {
  weekNumber: number;
  videos: VideoGroupItem[];
}

export interface VideoGroup {
  id: string;
  name: string;
  description: string;
  weekCount: number;
  weeks: VideoWeek[];
  updatedAt: string;
}

// === 로컬스토리지 키 ===
const STORAGE_KEY = 'video_groups_v1';

export function getVideoGroups(): VideoGroup[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveVideoGroups(groups: VideoGroup[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(groups));
}

function createId() {
  return `vg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

function createWeeks(count: number): VideoWeek[] {
  return Array.from({ length: count }, (_, i) => ({ weekNumber: i + 1, videos: [] }));
}

// === 메인 컴포넌트 ===
export function VideoGroupManagePage() {
  const [groups, setGroups] = useState<VideoGroup[]>(() => getVideoGroups());
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [editingGroup, setEditingGroup] = useState<VideoGroup | null>(null);
  const [dirty, setDirty] = useState(false);

  // 콘텐츠 라이브러리 모달
  const [libraryOpen, setLibraryOpen] = useState(false);
  const [targetWeek, setTargetWeek] = useState<number>(1);

  // 주차 접힘 상태
  const [expandedWeeks, setExpandedWeeks] = useState<Set<number>>(new Set([1]));

  // 상태 동기화
  useEffect(() => {
    saveVideoGroups(groups);
  }, [groups]);

  const selectedGroup = groups.find((g) => g.id === selectedGroupId) ?? null;

  // === 그룹 CRUD ===
  const handleCreateGroup = () => {
    const name = prompt('새 동영상 그룹 이름을 입력하세요:', `강의영상 그룹 ${groups.length + 1}`);
    if (!name?.trim()) return;
    const newGroup: VideoGroup = {
      id: createId(),
      name: name.trim(),
      description: '',
      weekCount: 15,
      weeks: createWeeks(15),
      updatedAt: new Date().toISOString(),
    };
    setGroups((prev) => [...prev, newGroup]);
    setSelectedGroupId(newGroup.id);
    setEditingGroup({ ...newGroup });
    setDirty(false);
    setExpandedWeeks(new Set([1]));
  };

  const handleSelectGroup = (groupId: string) => {
    if (dirty) {
      const ok = confirm('저장하지 않은 변경사항이 있습니다. 다른 그룹으로 이동하시겠습니까?');
      if (!ok) return;
    }
    setSelectedGroupId(groupId);
    const group = groups.find((g) => g.id === groupId);
    setEditingGroup(group ? { ...group, weeks: group.weeks.map((w) => ({ ...w, videos: [...w.videos] })) } : null);
    setDirty(false);
    setExpandedWeeks(new Set([1]));
  };

  const handleDeleteGroup = (groupId: string) => {
    const group = groups.find((g) => g.id === groupId);
    if (!confirm(`"${group?.name}" 그룹을 삭제하시겠습니까?`)) return;
    setGroups((prev) => prev.filter((g) => g.id !== groupId));
    if (selectedGroupId === groupId) {
      setSelectedGroupId(null);
      setEditingGroup(null);
      setDirty(false);
    }
  };

  const handleDuplicateGroup = (groupId: string) => {
    const src = groups.find((g) => g.id === groupId);
    if (!src) return;
    const dup: VideoGroup = {
      ...src,
      id: createId(),
      name: `${src.name} (복사)`,
      updatedAt: new Date().toISOString(),
      weeks: src.weeks.map((w) => ({ ...w, videos: w.videos.map((v) => ({ ...v, id: createId() })) })),
    };
    setGroups((prev) => [...prev, dup]);
  };

  const handleSave = () => {
    if (!editingGroup) return;
    const updated = { ...editingGroup, updatedAt: new Date().toISOString() };
    setGroups((prev) => prev.map((g) => (g.id === updated.id ? updated : g)));
    setDirty(false);
  };

  // === 편집 핸들러 ===
  const updateField = <K extends keyof VideoGroup>(field: K, value: VideoGroup[K]) => {
    if (!editingGroup) return;
    const next = { ...editingGroup, [field]: value };
    // 주차 수 변경 시 주차 배열 조정
    if (field === 'weekCount') {
      const newCount = value as number;
      const existing = editingGroup.weeks;
      next.weeks = Array.from({ length: newCount }, (_, i) => {
        const w = i + 1;
        return existing.find((e) => e.weekNumber === w) || { weekNumber: w, videos: [] };
      });
    }
    setEditingGroup(next);
    setDirty(true);
  };

  // 주차에 영상 추가 (라이브러리에서 선택)
  const handleVideoSelect = (content: any) => {
    if (!editingGroup) return;
    const newVideo: VideoGroupItem = {
      id: createId(),
      mediaKey: content.mediaKey,
      lessonId: content.lessonId,
      title: content.title,
      duration: content.duration || (content.totalTime ? `${content.totalTime}분` : undefined),
      completeTime: content.totalTime || 0,
    };
    const nextWeeks = editingGroup.weeks.map((w) =>
      w.weekNumber === targetWeek ? { ...w, videos: [...w.videos, newVideo] } : w,
    );
    setEditingGroup({ ...editingGroup, weeks: nextWeeks });
    setDirty(true);
    setLibraryOpen(false);
  };

  // 영상 삭제
  const removeVideo = (weekNumber: number, videoId: string) => {
    if (!editingGroup) return;
    const nextWeeks = editingGroup.weeks.map((w) =>
      w.weekNumber === weekNumber ? { ...w, videos: w.videos.filter((v) => v.id !== videoId) } : w,
    );
    setEditingGroup({ ...editingGroup, weeks: nextWeeks });
    setDirty(true);
  };

  // 인정시간 수정
  const updateCompleteTime = (weekNumber: number, videoId: string, time: number) => {
    if (!editingGroup) return;
    const nextWeeks = editingGroup.weeks.map((w) =>
      w.weekNumber === weekNumber
        ? { ...w, videos: w.videos.map((v) => (v.id === videoId ? { ...v, completeTime: time } : v)) }
        : w,
    );
    setEditingGroup({ ...editingGroup, weeks: nextWeeks });
    setDirty(true);
  };

  // 주차 접힘 토글
  const toggleWeek = (weekNumber: number) => {
    setExpandedWeeks((prev) => {
      const next = new Set(prev);
      next.has(weekNumber) ? next.delete(weekNumber) : next.add(weekNumber);
      return next;
    });
  };

  // JSON 내보내기
  const handleExportJson = () => {
    if (!editingGroup) return;
    const blob = new Blob([JSON.stringify(editingGroup, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${editingGroup.name.replace(/[^가-힣a-zA-Z0-9]/g, '_')}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // JSON 가져오기
  const handleImportJson = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const data = JSON.parse(reader.result as string) as VideoGroup;
          if (!data.name || !data.weeks) throw new Error('잘못된 형식');
          const imported: VideoGroup = {
            ...data,
            id: createId(),
            name: `${data.name} (가져옴)`,
            updatedAt: new Date().toISOString(),
          };
          setGroups((prev) => [...prev, imported]);
          setSelectedGroupId(imported.id);
          setEditingGroup({ ...imported, weeks: imported.weeks.map((w) => ({ ...w, videos: [...w.videos] })) });
          setDirty(false);
        } catch {
          alert('JSON 파일 형식이 올바르지 않습니다.');
        }
      };
      reader.readAsText(file);
    };
    input.click();
  };

  // 통계
  const getGroupStats = (group: VideoGroup) => {
    const totalVideos = group.weeks.reduce((sum, w) => sum + w.videos.length, 0);
    const filledWeeks = group.weeks.filter((w) => w.videos.length > 0).length;
    return { totalVideos, filledWeeks };
  };

  return (
    <div className="space-y-5">
      {/* 페이지 헤더 */}
      <div>
        <div className="flex flex-wrap items-end gap-4">
          <div className="mr-auto">
            <h1 className="text-2xl font-bold text-gray-900">동영상 그룹 관리</h1>
            <p className="text-gray-500 text-sm mt-0.5">
              주차별 강의영상 구성을 미리 만들어 차시관리에서 불러올 수 있습니다.
            </p>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleImportJson}
              className="flex items-center gap-2 px-3 py-2.5 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors text-sm"
            >
              <Upload className="w-4 h-4" />
              <span>JSON 가져오기</span>
            </button>
            <button
              onClick={handleCreateGroup}
              className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm"
            >
              <Plus className="w-4 h-4" />
              <span>새 그룹 만들기</span>
            </button>
          </div>
        </div>
      </div>

      <div className="flex gap-5">
        {/* 좌측: 그룹 목록 */}
        <div className="w-72 shrink-0">
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <div className="p-3 bg-gray-50 border-b border-gray-200">
              <h3 className="text-sm font-semibold text-gray-700">그룹 목록 ({groups.length})</h3>
            </div>
            <div className="max-h-[calc(100vh-260px)] overflow-y-auto">
              {groups.length === 0 ? (
                <div className="p-6 text-center text-gray-400 text-sm">
                  <Video className="w-8 h-8 mx-auto mb-2 opacity-50" />
                  <p>그룹이 없습니다.</p>
                  <p className="text-xs mt-1">"새 그룹 만들기"를 클릭하세요.</p>
                </div>
              ) : (
                groups.map((g) => {
                  const stats = getGroupStats(g);
                  return (
                    <div
                      key={g.id}
                      className={`border-b border-gray-100 transition-colors ${
                        selectedGroupId === g.id ? 'bg-blue-50 border-l-4 border-l-blue-600' : ''
                      }`}
                    >
                      <button
                        onClick={() => handleSelectGroup(g.id)}
                        className={`w-full text-left px-4 py-3 text-sm ${
                          selectedGroupId === g.id ? 'text-blue-700 font-medium' : 'hover:bg-gray-50 text-gray-700'
                        }`}
                      >
                        <div className="truncate font-medium">{g.name}</div>
                        <div className="text-xs text-gray-400 mt-0.5">
                          {g.weekCount}주 · 영상 {stats.totalVideos}개 · {stats.filledWeeks}주 구성됨
                        </div>
                      </button>
                      <div className="flex px-4 pb-2 gap-1">
                        <button
                          onClick={() => handleDuplicateGroup(g.id)}
                          className="p-1 text-gray-400 hover:text-blue-500 rounded transition-colors"
                          title="복제"
                        >
                          <Copy className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleDeleteGroup(g.id)}
                          className="p-1 text-gray-400 hover:text-red-500 rounded transition-colors"
                          title="삭제"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>

        {/* 우측: 편집 영역 */}
        <div className="flex-1 min-w-0">
          {!editingGroup ? (
            <div className="bg-white rounded-xl border-2 border-dashed border-gray-300 p-16 text-center">
              <Video className="w-10 h-10 mx-auto mb-3 text-gray-300" />
              <p className="text-lg font-medium text-gray-400">좌측에서 그룹을 선택하세요</p>
              <p className="text-sm text-gray-400 mt-1">또는 "새 그룹 만들기"로 시작하세요.</p>
            </div>
          ) : (
            <div className="space-y-4">
              {/* 그룹 정보 + 저장 */}
              <div className="bg-white rounded-xl border border-gray-200 p-5">
                <div className="flex flex-wrap items-end gap-4">
                  <div className="flex-1 min-w-0 grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-500 mb-1">그룹 이름</label>
                      <input
                        type="text"
                        value={editingGroup.name}
                        onChange={(e) => updateField('name', e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-500 mb-1">설명</label>
                      <input
                        type="text"
                        value={editingGroup.description}
                        onChange={(e) => updateField('description', e.target.value)}
                        placeholder="선택사항"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-500 mb-1">주차 수</label>
                      <input
                        type="number"
                        min={1}
                        max={30}
                        value={editingGroup.weekCount}
                        onChange={(e) =>
                          updateField('weekCount', Math.max(1, Math.min(30, Number(e.target.value) || 15)))
                        }
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={handleExportJson}
                      className="flex items-center gap-2 px-3 py-2.5 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors text-sm"
                    >
                      <Download className="w-4 h-4" />
                      <span>JSON</span>
                    </button>
                    <button
                      onClick={handleSave}
                      disabled={!dirty}
                      className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors text-sm"
                    >
                      <Save className="w-4 h-4" />
                      <span>저장</span>
                    </button>
                  </div>
                </div>
              </div>

              {/* 주차별 영상 배치 */}
              <div className="space-y-2">
                {editingGroup.weeks.map((week) => {
                  const isExpanded = expandedWeeks.has(week.weekNumber);
                  return (
                    <div key={week.weekNumber} className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                      {/* 주차 헤더 */}
                      <div
                        className="flex items-center justify-between px-4 py-3 bg-gray-50 cursor-pointer hover:bg-gray-100 transition-colors"
                        onClick={() => toggleWeek(week.weekNumber)}
                      >
                        <div className="flex items-center gap-3">
                          {isExpanded ? (
                            <ChevronDown className="w-5 h-5 text-gray-500" />
                          ) : (
                            <ChevronRight className="w-5 h-5 text-gray-500" />
                          )}
                          <span className="font-medium text-gray-900">{week.weekNumber}주차</span>
                          {week.videos.length > 0 && (
                            <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">
                              영상 {week.videos.length}개
                            </span>
                          )}
                        </div>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setTargetWeek(week.weekNumber);
                            setLibraryOpen(true);
                          }}
                          className="flex items-center gap-1 px-3 py-1.5 text-sm text-blue-600 bg-blue-50 rounded-lg hover:bg-blue-100 transition-colors"
                        >
                          <Plus className="w-4 h-4" />
                          <span>영상 추가</span>
                        </button>
                      </div>

                      {/* 주차 영상 목록 */}
                      {isExpanded && (
                        <div className="px-4 py-3 border-t border-gray-200 bg-white">
                          {week.videos.length > 0 ? (
                            <div className="space-y-2">
                              {week.videos.map((video, vIdx) => (
                                <div
                                  key={video.id}
                                  className="flex items-center gap-3 px-4 py-3 bg-gray-50 rounded-lg group hover:bg-gray-100 transition-colors"
                                >
                                  <span className="text-xs text-gray-400 w-5 text-center">{vIdx + 1}</span>
                                  <Video className="w-4 h-4 text-blue-600 flex-shrink-0" />
                                  <div className="flex-1 min-w-0">
                                    <span className="font-medium text-gray-800 truncate block">{video.title}</span>
                                    {video.duration && (
                                      <span className="text-xs text-gray-500">{video.duration}</span>
                                    )}
                                  </div>
                                  <div className="flex items-center gap-1 flex-shrink-0">
                                    <span className="text-xs text-gray-500">인정시간</span>
                                    <input
                                      type="number"
                                      min={0}
                                      value={video.completeTime || ''}
                                      onChange={(e) =>
                                        updateCompleteTime(week.weekNumber, video.id, Number(e.target.value))
                                      }
                                      onClick={(e) => e.stopPropagation()}
                                      className="w-16 px-2 py-1 text-sm border border-gray-300 rounded text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                                      placeholder="분"
                                    />
                                    <span className="text-xs text-gray-500">분</span>
                                  </div>
                                  <button
                                    onClick={() => removeVideo(week.weekNumber, video.id)}
                                    className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded opacity-0 group-hover:opacity-100 transition-all"
                                    title="삭제"
                                  >
                                    <Trash2 className="w-4 h-4" />
                                  </button>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div className="text-center py-6 text-gray-400">
                              <p>등록된 영상이 없습니다.</p>
                              <button
                                onClick={() => {
                                  setTargetWeek(week.weekNumber);
                                  setLibraryOpen(true);
                                }}
                                className="mt-2 text-sm text-blue-600 hover:underline"
                              >
                                + 콘텐츠 라이브러리에서 영상 추가
                              </button>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 콘텐츠 라이브러리 모달 */}
      {libraryOpen && (
        <ContentLibraryModal
          isOpen={libraryOpen}
          onClose={() => setLibraryOpen(false)}
          onSelect={handleVideoSelect}
          filterType="video"
        />
      )}
    </div>
  );
}
