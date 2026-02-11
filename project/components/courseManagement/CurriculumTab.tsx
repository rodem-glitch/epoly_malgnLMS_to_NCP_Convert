import React, { useEffect, useMemo, useRef, useState } from 'react';
import { CurriculumEditor } from '../CurriculumEditor';
import { Info, ChevronDown, ChevronRight, Plus, Video, FileText, BookOpen, ClipboardList, Trash2, Edit, GripVertical } from 'lucide-react';
import { WeeklyContentModal, type WeekContentItem, type ContentType } from './WeeklyContentModal';
import { EditContentModal } from './EditContentModal';
import { tutorLmsApi } from '../../api/tutorLmsApi';
import { buildHaksaCourseKey } from '../../utils/haksa';

interface CourseProps {
  id: string;
  sourceType?: 'haksa' | 'prism';
  mappedCourseId?: number;
  courseId: string;
  subjectName: string;
  haksaWeek?: string;
  haksaCourseName?: string;
  [key: string]: unknown;
}

interface CurriculumTabProps {
  courseId: number;
  course?: CourseProps;
}

// 차시(Session) 인터페이스 - 프리즘과 유사한 구조
interface HaksaSession {
  sessionId: string;
  sessionName: string;
  sessionNo?: number;
  startDate?: string;
  startTime?: string;
  endDate?: string;
  endTime?: string;
  contents: WeekContentItem[];
  isExpanded: boolean;
}

interface WeekData {
  weekNumber: number;
  title: string;
  isExpanded: boolean;
  sessions: HaksaSession[];
}

// 로컬스토리지 키 (마이그레이션용)
const getStorageKey = (courseId: string) => `haksa_curriculum_v2_${courseId}`;

// 마이그레이션: 기존 v1 데이터를 v2로 변환
const migrateV1ToV2 = (courseId: string): WeekData[] | null => {
  const oldKey = `haksa_curriculum_${courseId}`;
  try {
    const oldData = localStorage.getItem(oldKey);
    if (!oldData) return null;
    
    const oldContents: WeekContentItem[] = JSON.parse(oldData);
    if (!oldContents.length) return null;
    
    // 주차별로 그룹화하여 각 주차에 1개의 차시 생성
    const weekMap = new Map<number, WeekContentItem[]>();
    oldContents.forEach(content => {
      const week = content.weekNumber || 1;
      if (!weekMap.has(week)) weekMap.set(week, []);
      weekMap.get(week)!.push(content);
    });
    
    // 변환된 데이터 생성 (null 반환 대신 빈 배열이 아닌 실제 데이터만)
    const migratedWeeks: { weekNumber: number; sessions: HaksaSession[] }[] = [];
    weekMap.forEach((contents, weekNumber) => {
      migratedWeeks.push({
        weekNumber,
        sessions: [{
          sessionId: `session_migrated_${weekNumber}_${Date.now()}`,
          sessionName: `1차시`,
          sessionNo: weekNumber,
          startDate: '',
          startTime: '',
          endDate: '',
          endTime: '',
          contents,
          isExpanded: true,
        }]
      });
    });
    
    // 마이그레이션 완료 후 기존 키 삭제
    localStorage.removeItem(oldKey);
    
    return migratedWeeks.length > 0 ? migratedWeeks as any : null;
  } catch {
    return null;
  }
};

const createDefaultWeeks = (weekCount: number) =>
  Array.from({ length: weekCount }, (_, i) => ({
    weekNumber: i + 1,
    title: `${i + 1}주차`,
    isExpanded: i === 0,
    sessions: [],
  }));

const getNextSessionNo = (weeks: WeekData[]) => {
  const numbers = weeks
    .flatMap((w) => w.sessions.map((s) => s.sessionNo || 0))
    .filter((n) => n > 0);
  const maxNo = numbers.length > 0 ? Math.max(...numbers) : 0;
  return maxNo + 1;
};

const normalizeWeeks = (source: WeekData[], weekCount: number) =>
  Array.from({ length: weekCount }, (_, i) => {
    const existing = source.find((w) => w.weekNumber === i + 1);
    return existing || {
      weekNumber: i + 1,
      title: `${i + 1}주차`,
      isExpanded: i === 0,
      sessions: [],
    };
  });

export function CurriculumTab({ courseId, course }: CurriculumTabProps) {
  // 왜: 학사 과목은 "주차 → 차시" UI를 그대로 유지해야 하므로, UI 분기 기준은 sourceType만 봅니다.
  const isHaksaCourse = course?.sourceType === 'haksa';
  const haksaKey = useMemo(
    () =>
      buildHaksaCourseKey({
        haksaCourseCode: course?.haksaCourseCode,
        haksaOpenYear: course?.haksaOpenYear,
        haksaOpenTerm: course?.haksaOpenTerm,
        haksaBunbanCode: course?.haksaBunbanCode,
        haksaGroupCode: course?.haksaGroupCode,
      }),
    [
      course?.haksaCourseCode,
      course?.haksaOpenYear,
      course?.haksaOpenTerm,
      course?.haksaBunbanCode,
      course?.haksaGroupCode,
    ]
  );
  
  // 주차 수 결정
  const weekCount = (() => {
    if (course?.haksaWeek) {
      const parsed = parseInt(course.haksaWeek, 10);
      if (!isNaN(parsed) && parsed > 0) return parsed;
    }
    return 15;
  })();
  const recommendCourseName = (course?.subjectName || course?.haksaCourseName || '').trim();

  // 주차별 데이터 초기화 (차시 포함)
  const [weeks, setWeeks] = useState<WeekData[]>(() => createDefaultWeeks(weekCount));
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const isMountedRef = useRef(true);
  const latestWeeksRef = useRef<WeekData[]>([]);
  const latestHaksaKeyRef = useRef(haksaKey);

  const normalizedWeeks = useMemo(() => normalizeWeeks(weeks, weekCount), [weekCount, weeks]);
  useEffect(() => {
    latestWeeksRef.current = normalizedWeeks;
  }, [normalizedWeeks]);
  useEffect(() => {
    latestHaksaKeyRef.current = haksaKey;
  }, [haksaKey]);

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (isHaksaCourse && !haksaKey) {
      setErrorMessage('학사 과목 키가 비어 있어 저장/조회가 불가능합니다.');
    }
  }, [isHaksaCourse, haksaKey]);

  useEffect(() => {
    if (!isHaksaCourse || !haksaKey) return;
    let cancelled = false;

    const fetchCurriculum = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getHaksaCurriculum(haksaKey);
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        // 왜: Malgn DataSet은 배열 형태로 내려오는 경우가 있어 첫 번째 행을 꺼내옵니다.
        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        const raw = payload?.curriculum_json || '';
        if (raw) {
          const parsed = JSON.parse(raw) as WeekData[];
          if (!cancelled) setWeeks(normalizeWeeks(parsed, weekCount));
        } else if (course?.id) {
          // 왜: 기존 로컬스토리지 데이터를 DB로 이전합니다(이전 데이터 손실 방지).
          let migrated: WeekData[] | null = null;
          try {
            const saved = localStorage.getItem(getStorageKey(course.id));
            if (saved) migrated = JSON.parse(saved) as WeekData[];
          } catch {}
          if (!migrated) migrated = migrateV1ToV2(course.id);

          if (migrated && !cancelled) {
            const normalized = normalizeWeeks(migrated, weekCount);
            setWeeks(normalized);
            await tutorLmsApi.updateHaksaCurriculum({
              ...haksaKey,
              curriculumJson: JSON.stringify(normalized),
            });
          }
        }
      } catch (e) {
        if (!cancelled) {
          setErrorMessage(e instanceof Error ? e.message : '강의목차를 불러오는 중 오류가 발생했습니다.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
          setIsLoaded(true);
        }
      }
    };

    void fetchCurriculum();
    return () => {
      cancelled = true;
    };
  }, [isHaksaCourse, haksaKey, course?.id, weekCount]);

  useEffect(() => {
    if (!isHaksaCourse || !haksaKey || !isLoaded) return;
    const timer = setTimeout(() => {
      void (async () => {
        try {
          const res = await tutorLmsApi.updateHaksaCurriculum({
            ...haksaKey,
            curriculumJson: JSON.stringify(normalizedWeeks),
          });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);
        } catch (e) {
          if (isMountedRef.current) {
            setErrorMessage(e instanceof Error ? e.message : '강의목차 저장 중 오류가 발생했습니다.');
          }
        }
      })();
    }, 400);
    return () => clearTimeout(timer);
  }, [isHaksaCourse, haksaKey, isLoaded, normalizedWeeks]);
  useEffect(() => {
    return () => {
      if (!isHaksaCourse || !latestHaksaKeyRef.current || !isLoaded) return;
      // 왜: 탭 이동/언마운트 시 디바운스 저장이 취소될 수 있어 마지막 상태를 즉시 저장합니다.
      void tutorLmsApi.updateHaksaCurriculum({
        ...latestHaksaKeyRef.current,
        curriculumJson: JSON.stringify(latestWeeksRef.current),
      });
    };
  }, [isHaksaCourse, isLoaded]);

  // 모달 상태
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedWeek, setSelectedWeek] = useState(1);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  
  // 편집 모달 상태
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingContent, setEditingContent] = useState<WeekContentItem | null>(null);
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);

  // 주차 토글
  const toggleWeek = (weekNumber: number) => {
    setWeeks(prev =>
      prev.map(w =>
        w.weekNumber === weekNumber ? { ...w, isExpanded: !w.isExpanded } : w
      )
    );
  };

  // 차시 토글
  const toggleSession = (weekNumber: number, sessionId: string) => {
    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === weekNumber) {
          return {
            ...w,
            sessions: w.sessions.map(s =>
              s.sessionId === sessionId ? { ...s, isExpanded: !s.isExpanded } : s
            ),
          };
        }
        return w;
      })
    );
  };


  // 차시 추가
  const addSession = (weekNumber: number) => {
    setWeeks(prev => {
      const nextSessionNo = getNextSessionNo(prev);
      return prev.map(w => {
        if (w.weekNumber === weekNumber) {
          const sessionCount = w.sessions.length + 1;
          const newSession: HaksaSession = {
            sessionId: `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            sessionName: `${sessionCount}차시`,
            sessionNo: nextSessionNo,
            startDate: '',
            startTime: '',
            endDate: '',
            endTime: '',
            contents: [],
            isExpanded: true,
          };
          return { ...w, sessions: [...w.sessions, newSession] };
        }
        return w;
      });
    });
  };

  // 차시 이름 수정
  const updateSessionName = (weekNumber: number, sessionId: string, name: string) => {
    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === weekNumber) {
          return {
            ...w,
            sessions: w.sessions.map(s =>
              s.sessionId === sessionId ? { ...s, sessionName: name } : s
            ),
          };
        }
        return w;
      })
    );
  };

  const updateSessionPeriod = (
    weekNumber: number,
    sessionId: string,
    field: 'startDate' | 'startTime' | 'endDate' | 'endTime',
    value: string
  ) => {
    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === weekNumber) {
          return {
            ...w,
            sessions: w.sessions.map(s =>
              s.sessionId === sessionId ? { ...s, [field]: value } : s
            ),
          };
        }
        return w;
      })
    );
  };

  // 차시 삭제
  const removeSession = (weekNumber: number, sessionId: string) => {
    if (!confirm('이 차시와 포함된 모든 콘텐츠를 삭제하시겠습니까?')) return;
    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === weekNumber) {
          return {
            ...w,
            sessions: w.sessions.filter(s => s.sessionId !== sessionId),
          };
        }
        return w;
      })
    );
  };

  // 콘텐츠 추가 모달 열기
  const openAddModal = (weekNumber: number, sessionId: string) => {
    setSelectedWeek(weekNumber);
    setSelectedSessionId(sessionId);
    setModalOpen(true);
  };

  // 콘텐츠 편집 모달 열기
  const openEditModal = (content: WeekContentItem, sessionId: string) => {
    setEditingContent(content);
    setEditingSessionId(sessionId);
    setEditModalOpen(true);
  };

  // 콘텐츠 추가
  const handleAddContent = (content: Omit<WeekContentItem, 'id' | 'createdAt'>) => {
    if (!selectedSessionId) return;
    
    const newContent: WeekContentItem = {
      ...content,
      id: `content_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      createdAt: new Date().toISOString(),
    };

    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === selectedWeek) {
          return {
            ...w,
            sessions: w.sessions.map(s =>
              s.sessionId === selectedSessionId
                ? { ...s, contents: [...s.contents, newContent] }
                : s
            ),
          };
        }
        return w;
      })
    );
  };

  // 콘텐츠 삭제
  const handleDeleteContent = (weekNumber: number, sessionId: string, contentId: string) => {
    if (!confirm('이 콘텐츠를 삭제하시겠습니까?')) return;
    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === weekNumber) {
          return {
            ...w,
            sessions: w.sessions.map(s =>
              s.sessionId === sessionId
                ? { ...s, contents: s.contents.filter(c => c.id !== contentId) }
                : s
            ),
          };
        }
        return w;
      })
    );
  };

  // 콘텐츠 편집
  const handleEditContent = (updatedContent: WeekContentItem) => {
    if (!editingSessionId) return;
    
    setWeeks(prev =>
      prev.map(w => ({
        ...w,
        sessions: w.sessions.map(s =>
          s.sessionId === editingSessionId
            ? { ...s, contents: s.contents.map(c => c.id === updatedContent.id ? updatedContent : c) }
            : s
        ),
      }))
    );
    setEditModalOpen(false);
    setEditingContent(null);
    setEditingSessionId(null);
  };

  // 인정시간 수정 (동영상 콘텐츠 전용)
  // 왜: 비정규 쪽 CurriculumEditor.tsx의 updateLessonCompleteTime과 동일한 역할
  const updateContentCompleteTime = (weekNumber: number, sessionId: string, contentId: string, time: number) => {
    setWeeks(prev =>
      prev.map(w => {
        if (w.weekNumber === weekNumber) {
          return {
            ...w,
            sessions: w.sessions.map(s =>
              s.sessionId === sessionId
                ? {
                    ...s,
                    contents: s.contents.map(c =>
                      c.id === contentId ? { ...c, completeTime: time } : c
                    ),
                  }
                : s
            ),
          };
        }
        return w;
      })
    );
  };

  // 아이콘/뱃지 헬퍼
  const getContentIcon = (type: ContentType) => {
    switch (type) {
      case 'video': return <Video className="w-4 h-4 text-blue-600" />;
      case 'exam': return <ClipboardList className="w-4 h-4 text-red-600" />;
      case 'assignment': return <BookOpen className="w-4 h-4 text-purple-600" />;
      case 'document': return <FileText className="w-4 h-4 text-green-600" />;
    }
  };

  const getContentTypeBadge = (type: ContentType) => {
    switch (type) {
      case 'video': return <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">동영상</span>;
      case 'exam': return <span className="px-2 py-0.5 bg-red-100 text-red-700 text-xs rounded">시험</span>;
      case 'assignment': return <span className="px-2 py-0.5 bg-purple-100 text-purple-700 text-xs rounded">과제</span>;
      case 'document': return <span className="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded">자료</span>;
    }
  };

  // 통계 계산
  const allContents = normalizedWeeks.flatMap(w => w.sessions.flatMap(s => s.contents));
  const totalSessions = normalizedWeeks.reduce((sum, w) => sum + w.sessions.length, 0);
  const stats = {
    sessions: totalSessions,
    total: allContents.length,
    videos: allContents.filter(c => c.type === 'video').length,
    exams: allContents.filter(c => c.type === 'exam').length,
    assignments: allContents.filter(c => c.type === 'assignment').length,
    documents: allContents.filter(c => c.type === 'document').length,
  };

  if (isHaksaCourse) {
    return (
      <div className="space-y-4">
        {errorMessage && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
            {errorMessage}
          </div>
        )}

        {loading && (
          <div className="bg-white rounded-lg border border-gray-200 p-6 text-center text-gray-600">
            불러오는 중...
          </div>
        )}

        {/* 주차 정보 헤더 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-medium text-gray-900">주차별 강의목차</h3>
            <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs font-medium rounded">
              총 {weekCount}주차
            </span>
          </div>
          <div className="text-sm text-gray-500">
            {course?.haksaCourseName || course?.subjectName || '강좌명 없음'}
          </div>
        </div>

        {/* 콘텐츠 통계 */}
        {(stats.sessions > 0 || stats.total > 0) && (
          <div className="grid grid-cols-6 gap-3">
            <div className="px-4 py-3 bg-indigo-50 rounded-lg text-center">
              <div className="text-2xl font-semibold text-indigo-600">{stats.sessions}</div>
              <div className="text-xs text-indigo-600">차시</div>
            </div>
            <div className="px-4 py-3 bg-gray-50 rounded-lg text-center">
              <div className="text-2xl font-semibold text-gray-900">{stats.total}</div>
              <div className="text-xs text-gray-500">전체</div>
            </div>
            <div className="px-4 py-3 bg-blue-50 rounded-lg text-center">
              <div className="text-2xl font-semibold text-blue-600">{stats.videos}</div>
              <div className="text-xs text-blue-600">동영상</div>
            </div>
            <div className="px-4 py-3 bg-red-50 rounded-lg text-center">
              <div className="text-2xl font-semibold text-red-600">{stats.exams}</div>
              <div className="text-xs text-red-600">시험</div>
            </div>
            <div className="px-4 py-3 bg-purple-50 rounded-lg text-center">
              <div className="text-2xl font-semibold text-purple-600">{stats.assignments}</div>
              <div className="text-xs text-purple-600">과제</div>
            </div>
            <div className="px-4 py-3 bg-green-50 rounded-lg text-center">
              <div className="text-2xl font-semibold text-green-600">{stats.documents}</div>
              <div className="text-xs text-green-600">자료</div>
            </div>
          </div>
        )}

        {/* 주차별 아코디언 */}
        <div className="space-y-2">
          {normalizedWeeks.map(week => (
            <div
              key={week.weekNumber}
              className="border border-gray-200 rounded-lg overflow-hidden"
            >
              {/* 주차 헤더 */}
              <div
                className="flex items-center justify-between px-4 py-3 bg-gray-50 cursor-pointer hover:bg-gray-100 transition-colors"
                onClick={() => toggleWeek(week.weekNumber)}
              >
                <div className="flex items-center gap-3">
                  {week.isExpanded ? (
                    <ChevronDown className="w-5 h-5 text-gray-500" />
                  ) : (
                    <ChevronRight className="w-5 h-5 text-gray-500" />
                  )}
                  <span className="font-medium text-gray-900">{week.title}</span>
                  {week.sessions.length > 0 && (
                    <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 text-xs rounded">
                      {week.sessions.length}개 차시
                    </span>
                  )}
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    addSession(week.weekNumber);
                  }}
                  className="flex items-center gap-1 px-3 py-1.5 text-sm text-indigo-600 bg-indigo-50 rounded-lg hover:bg-indigo-100 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  <span>차시 추가</span>
                </button>
              </div>

              {/* 주차 콘텐츠 (차시 목록) */}
              {week.isExpanded && (
                <div className="px-4 py-3 border-t border-gray-200 bg-white space-y-3">
                  {week.sessions.length > 0 ? (
                    week.sessions.map((session, sessionIdx) => (
                      <div
                        key={session.sessionId}
                        className="border border-gray-200 rounded-lg overflow-hidden"
                      >
                        {/* 차시 헤더 */}
                        <div
                          className="flex items-center justify-between px-4 py-2.5 bg-indigo-50/50 cursor-pointer hover:bg-indigo-50 transition-colors"
                          onClick={() => toggleSession(week.weekNumber, session.sessionId)}
                        >
                          <div className="flex items-center gap-3">
                            {session.isExpanded ? (
                              <ChevronDown className="w-4 h-4 text-indigo-500" />
                            ) : (
                              <ChevronRight className="w-4 h-4 text-indigo-500" />
                            )}
                            <input
                              type="text"
                              value={session.sessionName}
                              onChange={(e) => updateSessionName(week.weekNumber, session.sessionId, e.target.value)}
                              onClick={(e) => e.stopPropagation()}
                              className="px-2 py-1 bg-white border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                            />
                            {session.contents.length > 0 && (
                              <span className="px-2 py-0.5 bg-gray-200 text-gray-600 text-xs rounded">
                                {session.contents.length}개 콘텐츠
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2">
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                openAddModal(week.weekNumber, session.sessionId);
                              }}
                              className="flex items-center gap-1 px-2.5 py-1 text-xs text-blue-600 bg-blue-50 rounded hover:bg-blue-100 transition-colors"
                            >
                              <Plus className="w-3.5 h-3.5" />
                              <span>추가</span>
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                removeSession(week.weekNumber, session.sessionId);
                              }}
                              className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
                              title="차시 삭제"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>

                        {/* 차시 콘텐츠 목록 */}
                        {session.isExpanded && (
                          <div className="px-4 py-3 border-t border-gray-100 bg-white">
                            {/* 차시 수강기간 */}
                            <div className="mb-3 p-3 bg-gray-50 rounded-lg">
                              <div className="text-xs text-gray-600 mb-2">차시 수강기간(날짜+시간)</div>
                              <div className="flex flex-wrap items-center gap-2">
                                <input
                                  type="date"
                                  value={session.startDate || ''}
                                  onChange={(e) =>
                                    updateSessionPeriod(week.weekNumber, session.sessionId, 'startDate', e.target.value)
                                  }
                                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                />
                                <input
                                  type="time"
                                  value={session.startTime || ''}
                                  onChange={(e) =>
                                    updateSessionPeriod(week.weekNumber, session.sessionId, 'startTime', e.target.value)
                                  }
                                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                />
                                <span className="text-sm text-gray-500">~</span>
                                <input
                                  type="date"
                                  value={session.endDate || ''}
                                  onChange={(e) =>
                                    updateSessionPeriod(week.weekNumber, session.sessionId, 'endDate', e.target.value)
                                  }
                                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                />
                                <input
                                  type="time"
                                  value={session.endTime || ''}
                                  onChange={(e) =>
                                    updateSessionPeriod(week.weekNumber, session.sessionId, 'endTime', e.target.value)
                                  }
                                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                />
                              </div>
                              <div className="text-xs text-gray-500 mt-1">
                                왜: 차시 수강기간을 지나면 콘텐츠를 잠그고, 출석 판정도 이 기간으로 계산합니다.
                              </div>
                            </div>
                            {session.contents.length > 0 ? (
                              <div className="space-y-2">
                                {session.contents.map(content => (
                                  <div
                                    key={content.id}
                                    className="flex items-center gap-3 px-4 py-3 bg-gray-50 rounded-lg group hover:bg-gray-100 transition-colors"
                                  >
                                    {getContentIcon(content.type)}
                                    <div className="flex-1 min-w-0">
                                      <div className="flex items-center gap-2">
                                        <span className="font-medium text-gray-800 truncate">
                                          {content.title}
                                        </span>
                                        {getContentTypeBadge(content.type)}
                                      </div>
                                      {content.description && (
                                        <p className="text-sm text-gray-500 truncate mt-0.5">
                                          {content.description}
                                        </p>
                                      )}
                                    </div>
                                    {content.duration && (
                                      <span className="text-sm text-gray-500 flex-shrink-0">
                                        {content.duration}
                                      </span>
                                    )}
                                    {/* 인정시간 입력 칸 (동영상 콘텐츠 전용) */}
                                    {content.type === 'video' && (
                                      <div className="flex items-center gap-1 flex-shrink-0">
                                        <span className="text-xs text-gray-500">인정시간</span>
                                        <input
                                          type="number"
                                          min="0"
                                          value={content.completeTime || ''}
                                          onChange={(e) => updateContentCompleteTime(
                                            week.weekNumber,
                                            session.sessionId,
                                            content.id,
                                            Number(e.target.value)
                                          )}
                                          onClick={(e) => e.stopPropagation()}
                                          className="w-16 px-2 py-1 text-sm border border-gray-300 rounded text-center focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                          placeholder="분"
                                        />
                                        <span className="text-xs text-gray-500">분</span>
                                      </div>
                                    )}
                                    <button
                                      onClick={() => openEditModal(content, session.sessionId)}
                                      className="p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 rounded opacity-0 group-hover:opacity-100 transition-all"
                                      title="편집"
                                    >
                                      <Edit className="w-4 h-4" />
                                    </button>
                                    <button
                                      onClick={() => handleDeleteContent(week.weekNumber, session.sessionId, content.id)}
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
                                <p>등록된 콘텐츠가 없습니다.</p>
                                <button
                                  onClick={() => openAddModal(week.weekNumber, session.sessionId)}
                                  className="mt-2 text-sm text-blue-600 hover:underline"
                                >
                                  + 콘텐츠 추가하기
                                </button>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    ))
                  ) : (
                    <div className="text-center py-8 text-gray-400">
                      <p>등록된 차시가 없습니다.</p>
                      <button
                        onClick={() => addSession(week.weekNumber)}
                        className="mt-2 text-sm text-indigo-600 hover:underline"
                      >
                        + 차시 추가하기
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* 콘텐츠 추가 모달 */}
        <WeeklyContentModal
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          weekNumber={selectedWeek}
          courseName={recommendCourseName}
          onAdd={handleAddContent}
        />
        
        {/* 콘텐츠 편집 모달 */}
        <EditContentModal
          isOpen={editModalOpen}
          onClose={() => {
            setEditModalOpen(false);
            setEditingContent(null);
            setEditingSessionId(null);
          }}
          content={editingContent}
          courseName={recommendCourseName}
          onSave={handleEditContent}
        />
      </div>
    );
  }

  return (
    <CurriculumEditor
      courseId={courseId}
      courseName={recommendCourseName}
      embedded={true}
    />
  );
}

// 다른 탭에서 사용할 수 있도록 콘텐츠 조회 함수 export
export function getHaksaCurriculumContents(courseId: string): WeekContentItem[] {
  try {
    // v2 형식에서 로드
    const saved = localStorage.getItem(getStorageKey(courseId));
    if (saved) {
      const weeks: WeekData[] = JSON.parse(saved);
      return weeks.flatMap(w => w.sessions.flatMap(s => s.contents));
    }
    // v1 형식 fallback
    const oldKey = `haksa_curriculum_${courseId}`;
    const oldData = localStorage.getItem(oldKey);
    return oldData ? JSON.parse(oldData) : [];
  } catch {
    return [];
  }
}

export function getHaksaExams(courseId: string): WeekContentItem[] {
  return getHaksaCurriculumContents(courseId).filter(c => c.type === 'exam');
}

export function getHaksaAssignments(courseId: string): WeekContentItem[] {
  return getHaksaCurriculumContents(courseId).filter(c => c.type === 'assignment');
}
