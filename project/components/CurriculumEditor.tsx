// 공통 차시 편집 컴포넌트
// 사용처: CreateCourseForm.tsx, CurriculumTab.tsx
import React from 'react';
import { Video, Trash2, ChevronUp, ChevronDown, Plus, X } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';
import { ContentLibraryModal } from './ContentLibraryModal';

interface CurriculumLesson {
  lessonId: number | string;
  lessonName: string;
  lessonType?: string;
  lessonTypeConv?: string;
  onoffType?: string;
  onoffTypeConv?: string;
  totalTime?: number;
  completeTime?: number;
  duration?: string;
  chapter?: number;
  isNew?: boolean;
}

interface CurriculumSection {
  sectionId: number;
  sectionName: string;
  lessons: CurriculumLesson[];
  isNew?: boolean;
}

interface CurriculumEditorProps {
  courseId: number;
  courseName?: string;
  onClose?: () => void;
  onSaveComplete?: () => void;
  embedded?: boolean; // true면 모달이 아닌 탭 내에서 표시
}

export const CurriculumEditor: React.FC<CurriculumEditorProps> = ({
  courseId,
  courseName,
  onClose,
  onSaveComplete,
  embedded = false,
}) => {
  const [curriculumData, setCurriculumData] = React.useState<CurriculumSection[]>([]);
  const [originalCurriculumData, setOriginalCurriculumData] = React.useState<CurriculumSection[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  
  const [isContentModalOpen, setIsContentModalOpen] = React.useState(false);
  const [currentSectionId, setCurrentSectionId] = React.useState<number | null>(null);
  const currentSectionName = React.useMemo(
    () => curriculumData.find((s) => s.sectionId === currentSectionId)?.sectionName || '',
    [curriculumData, currentSectionId]
  );

  // 차시 데이터 로드
  const loadCurriculum = React.useCallback(async () => {
    if (!courseId) return;
    setLoading(true);
    setError(null);
    
    try {
      const res = await tutorLmsApi.getCurriculum({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      
      const rows = res.rst_data ?? [];
      const sectionsMap = new Map<number, CurriculumSection>();
      
      rows.forEach((row: any) => {
        const sectionId = row.section_id || 0;
        if (!sectionsMap.has(sectionId)) {
          sectionsMap.set(sectionId, {
            sectionId,
            sectionName: row.section_nm || `${row.chapter}차시`,
            lessons: [],
          });
        }
        sectionsMap.get(sectionId)!.lessons.push({
          lessonId: row.lesson_id,
          lessonName: row.lesson_nm || '',
          lessonType: row.lesson_type || '',
          lessonTypeConv: row.lesson_type_conv || '',
          onoffType: row.onoff_type || '',
          onoffTypeConv: row.onoff_type_conv || '',
          totalTime: row.total_time_min || 0,
          completeTime: row.complete_time_min || 0,
          duration: row.duration_conv || '',
          chapter: row.chapter,
        });
      });
      
      const data = Array.from(sectionsMap.values());
      setCurriculumData(data);
      setOriginalCurriculumData(JSON.parse(JSON.stringify(data)));
    } catch (e) {
      setError(e instanceof Error ? e.message : '차시 정보를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }, [courseId]);

  React.useEffect(() => {
    loadCurriculum();
  }, [loadCurriculum]);

  // 섹션 추가
  const addSection = () => {
    const newSectionId = Date.now();
    setCurriculumData([...curriculumData, {
      sectionId: newSectionId,
      sectionName: `${curriculumData.length + 1}차시`,
      lessons: [],
      isNew: true,
    }]);
  };

  // 섹션 이름 수정
  const updateSectionName = (sectionId: number, name: string) => {
    setCurriculumData(curriculumData.map(s => 
      s.sectionId === sectionId ? { ...s, sectionName: name } : s
    ));
  };

  // 섹션 삭제
  const removeSection = (sectionId: number) => {
    setCurriculumData(curriculumData.filter(s => s.sectionId !== sectionId));
  };

  // 영상 추가 모달 열기
  const openContentModal = (sectionId: number) => {
    setCurrentSectionId(sectionId);
    setIsContentModalOpen(true);
  };

  // 다중 영상 선택 시 해당 섹션에 추가
  const handleMultiContentSelect = (contents: any[]) => {
    if (currentSectionId !== null) {
      contents.forEach(content => addLessonToSection(currentSectionId, content));
    }
    setIsContentModalOpen(false);
    setCurrentSectionId(null);
  };

  // 섹션에 레슨 추가 헬퍼
  const addLessonToSection = (sectionId: number, content: any) => {
    setCurriculumData(prev => prev.map(section => {
      if (section.sectionId === sectionId) {
        const nextLessonId = content.lessonId ?? content.id;
        if (section.lessons.find((l: any) => String(l.lessonId) === String(nextLessonId))) {
          return section;
        }
        const newLesson: CurriculumLesson = {
          lessonId: nextLessonId,
          lessonName: content.title,
          lessonType: content.lessonType || content.lesson_type || '05',
          lessonTypeConv: content.category || '',
          totalTime: content.totalTime || 0,
          completeTime: content.totalTime || 0, // 콜러스 목록은 total_time을 기준으로 인정시간 처리
          duration: content.duration || '',
          isNew: true,
        };
        return { ...section, lessons: [...section.lessons, newLesson] };
      }
      return section;
    }));
  };

  // 레슨 삭제
  const removeLesson = (sectionId: number, lessonId: number | string) => {
    setCurriculumData(curriculumData.map(section => {
      if (section.sectionId === sectionId) {
        return { ...section, lessons: section.lessons.filter((l: any) => l.lessonId !== lessonId) };
      }
      return section;
    }));
  };

  // 인정시간 수정
  const updateLessonCompleteTime = (sectionId: number, lessonId: number | string, time: number) => {
    setCurriculumData(curriculumData.map(section => {
      if (section.sectionId === sectionId) {
        return {
          ...section,
          lessons: section.lessons.map((l: any) =>
            l.lessonId === lessonId ? { ...l, completeTime: time } : l
          ),
        };
      }
      return section;
    }));
  };

  // 레슨 순서 변경 (위로)
  const moveLessonUp = (sectionId: number, lessonIndex: number) => {
    if (lessonIndex === 0) return;
    setCurriculumData(curriculumData.map(section => {
      if (section.sectionId === sectionId) {
        const lessons = [...section.lessons];
        [lessons[lessonIndex - 1], lessons[lessonIndex]] = [lessons[lessonIndex], lessons[lessonIndex - 1]];
        return { ...section, lessons };
      }
      return section;
    }));
  };

  // 레슨 순서 변경 (아래로)
  const moveLessonDown = (sectionId: number, lessonIndex: number) => {
    setCurriculumData(curriculumData.map(section => {
      if (section.sectionId === sectionId) {
        if (lessonIndex >= section.lessons.length - 1) return section;
        const lessons = [...section.lessons];
        [lessons[lessonIndex], lessons[lessonIndex + 1]] = [lessons[lessonIndex + 1], lessons[lessonIndex]];
        return { ...section, lessons };
      }
      return section;
    }));
  };

  // 저장
  const saveCurriculum = async () => {
    setSaving(true);
    setError(null);
    
    try {
      // 0. 삭제된 레슨 확인 및 삭제
      const originalLessonIds = new Set<number>();
      originalCurriculumData.forEach(section => {
        section.lessons.forEach((l: any) => {
          if (!l.isNew) originalLessonIds.add(Number(l.lessonId));
        });
      });
      
      const currentLessonIds = new Set<number>();
      curriculumData.forEach(section => {
        section.lessons.forEach((l: any) => {
          if (!l.isNew) currentLessonIds.add(Number(l.lessonId));
        });
      });
      
      for (const lessonId of originalLessonIds) {
        if (!currentLessonIds.has(lessonId)) {
          const res = await tutorLmsApi.deleteCurriculumLesson({ courseId, lessonId });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);
        }
      }
      
      // 0-1. 삭제된 섹션 확인 및 삭제
      const originalSectionIds = new Set(originalCurriculumData.map(s => s.sectionId));
      const currentSectionIds = new Set(curriculumData.filter(s => !s.isNew).map(s => s.sectionId));
      
      for (const sectionId of originalSectionIds) {
        if (!currentSectionIds.has(sectionId) && sectionId !== 0) {
          await tutorLmsApi.deleteCurriculumSection({ courseId, sectionId });
        }
      }
      
      // 1. 새 섹션 추가
      for (const section of curriculumData) {
        if (section.isNew) {
          const res = await tutorLmsApi.insertCurriculumSection({
            courseId,
            sectionName: section.sectionName,
          });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);
          section.sectionId = res.rst_data || section.sectionId;
          section.isNew = false;
        }
      }
      
      // 2. 각 섹션의 신규 레슨 추가
      for (const section of curriculumData) {
        for (const lesson of section.lessons) {
          if (lesson.isNew) {
            const res = await tutorLmsApi.addCurriculumLesson({
              courseId,
              sectionId: section.sectionId,
              lessonId: Number(lesson.lessonId),
            });
            if (res.rst_code !== '0000') throw new Error(res.rst_message);
            lesson.isNew = false;
          }
        }
      }
      
      // 3. 기존 레슨 순서(chapter) 및 인정시간(completeTime) 업데이트
      let chapterOrder = 0;
      for (const section of curriculumData) {
        for (const lesson of section.lessons) {
          chapterOrder++;
          if (!lesson.isNew) {
            await tutorLmsApi.updateCurriculumLesson({
              courseId,
              lessonId: Number(lesson.lessonId),
              chapter: chapterOrder,
              sectionId: section.sectionId,
              completeTime: lesson.completeTime,
            });
          }
        }
      }
      
      alert('차시가 저장되었습니다.');
      await loadCurriculum(); // 새로고침
      onSaveComplete?.();
    } catch (e) {
      const errorMsg = e instanceof Error ? e.message : '저장 중 오류가 발생했습니다.';
      setError(errorMsg);
      alert('저장 실패: ' + errorMsg);
    } finally {
      setSaving(false);
    }
  };

  const totalLessons = curriculumData.reduce((sum, s) => sum + s.lessons.length, 0);

  // 컨테이너 스타일 (모달 vs 탭 내장)
  const containerClass = embedded
    ? 'bg-white'
    : 'fixed inset-0 bg-gray-900/50 flex items-center justify-center z-50 p-4';

  const contentClass = embedded
    ? ''
    : 'bg-white rounded-xl shadow-xl max-w-4xl w-full max-h-[90vh] flex flex-col';

  return (
    <div className={containerClass}>
      <div className={contentClass}>
        {/* 헤더 */}
        {!embedded && (
          <div className="flex items-center justify-between p-4 border-b border-gray-200">
            <div>
              <h3 className="text-lg font-semibold text-gray-900">차시 편집</h3>
              {courseName && <p className="text-sm text-gray-600">{courseName}</p>}
            </div>
            <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-lg">
              <X className="w-5 h-5" />
            </button>
          </div>
        )}

        {/* 본문 */}
        <div className={embedded ? '' : 'flex-1 overflow-y-auto p-4'}>
          {/* 요약 및 차시추가 버튼 */}
          <div className="flex items-center justify-between mb-4">
            <div className="text-sm text-gray-600">
              차시 {curriculumData.length}개 · 레슨 {totalLessons}개
            </div>
            <button
              type="button"
              onClick={addSection}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <Plus className="w-4 h-4" />
              <span>차시 추가</span>
            </button>
          </div>

          {/* 로딩/에러 상태 */}
          {loading && <div className="text-center py-8 text-gray-500">불러오는 중...</div>}
          {error && <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">{error}</div>}

          {/* 차시 목록 */}
          {!loading && curriculumData.map((section, sectionIdx) => (
            <div key={section.sectionId} className="mb-4 border border-gray-200 rounded-lg p-4">
              {/* 섹션 헤더 */}
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-500">{sectionIdx + 1}차시</span>
                  <input
                    type="text"
                    value={section.sectionName}
                    onChange={(e) => updateSectionName(section.sectionId, e.target.value)}
                    className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  {section.isNew && <span className="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded">신규</span>}
                </div>
                <button
                  type="button"
                  onClick={() => removeSection(section.sectionId)}
                  className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg"
                >
                  <Trash2 className="w-5 h-5" />
                </button>
              </div>

              {/* 영상 추가 버튼 */}
              <button
                type="button"
                onClick={() => openContentModal(section.sectionId)}
                className="flex items-center gap-2 px-3 py-2 text-blue-600 border border-blue-300 rounded-lg hover:bg-blue-50 transition-colors mb-3"
              >
                <Video className="w-4 h-4" />
                <span>영상 추가</span>
              </button>

              {/* 레슨 목록 (테이블) */}
              {section.lessons.length > 0 && (
                <div className="border border-gray-200 rounded-lg overflow-hidden">
                  <table className="w-full">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-12">No</th>
                        <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">구분</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">강의명</th>
                        <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-24">학습시간</th>
                        <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-28">인정시간</th>
                        <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">삭제</th>
                        <th className="px-4 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider w-20">순서</th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {section.lessons.map((lesson: any, lessonIdx: number) => (
                        <tr key={lesson.lessonId} className="hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 text-sm text-gray-500 font-medium">{lessonIdx + 1}</td>
                          <td className="px-4 py-3 text-center">
                            <span className={`px-2 py-1 text-xs font-medium rounded-full ${
                              lesson.onoffType === 'N' ? 'bg-green-100 text-green-700' :
                              lesson.onoffType === 'F' ? 'bg-orange-100 text-orange-700' :
                              'bg-purple-100 text-purple-700'
                            }`}>
                              {lesson.onoffTypeConv || (lesson.onoffType === 'N' ? '온라인' : lesson.onoffType === 'F' ? '오프라인' : '블렌디드')}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <span className="text-sm text-gray-900">{lesson.lessonName}</span>
                              {lesson.isNew && (
                                <span className="px-2 py-0.5 bg-green-100 text-green-700 text-xs font-medium rounded-full">신규</span>
                              )}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-center text-sm text-gray-600">
                            {lesson.totalTime || lesson.duration || '-'}분
                          </td>
                          <td className="px-4 py-3 text-center">
                            <input
                              type="number"
                              min="0"
                              value={lesson.completeTime || ''}
                              onChange={(e) => updateLessonCompleteTime(section.sectionId, lesson.lessonId, Number(e.target.value))}
                              className="w-20 px-3 py-1.5 text-sm border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                              placeholder="분"
                            />
                          </td>
                          <td className="px-4 py-3 text-center">
                            <button
                              type="button"
                              onClick={() => removeLesson(section.sectionId, lesson.lessonId)}
                              className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                              title="삭제"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </td>
                          <td className="px-4 py-3 text-center">
                            <div className="flex items-center justify-center gap-1">
                              <button
                                type="button"
                                onClick={() => moveLessonUp(section.sectionId, lessonIdx)}
                                className="p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded disabled:opacity-30"
                                disabled={lessonIdx === 0}
                              >
                                <ChevronUp className="w-4 h-4" />
                              </button>
                              <button
                                type="button"
                                onClick={() => moveLessonDown(section.sectionId, lessonIdx)}
                                className="p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded disabled:opacity-30"
                                disabled={lessonIdx === section.lessons.length - 1}
                              >
                                <ChevronDown className="w-4 h-4" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ))}
        </div>

        {/* 푸터 (저장 버튼) */}
        <div className={`flex items-center justify-end gap-3 p-4 ${embedded ? 'mt-4' : 'border-t border-gray-200'}`}>
          {!embedded && onClose && (
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
            >
              취소
            </button>
          )}
          <button
            type="button"
            onClick={saveCurriculum}
            disabled={saving}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>

      {/* 영상 추가 모달 */}
      <ContentLibraryModal
        isOpen={isContentModalOpen}
        onClose={() => { setIsContentModalOpen(false); setCurrentSectionId(null); }}
        onSelect={() => {}}
        onMultiSelect={handleMultiContentSelect}
        multiSelect={true}
        recommendContext={{
          // 왜: 공통 차시편집에서도 과목명을 추천 힌트로 전달해야 과목별 추천이 달라집니다.
          courseName: courseName || '',
          // 왜: 현재 선택한 차시명을 차시 제목 힌트로 전달하면 같은 과목 내에서도 맥락이 달라집니다.
          lessonTitle: currentSectionName,
        }}
      />
    </div>
  );
};

export default CurriculumEditor;
