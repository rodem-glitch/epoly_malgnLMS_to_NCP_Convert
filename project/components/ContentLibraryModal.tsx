import React, { useState } from 'react';
import { X, Search, Check, Link, Play } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';

interface Content {
  id: string;
  mediaKey: string;
  title: string;
  description: string;
  category: string;
  thumbnail: string;
  duration?: string;
  totalTime?: number;
  contentWidth?: number;
  contentHeight?: number;
  lessonId?: number; // LMS 레슨 ID(숫자)
  recommendKey?: string; // 추천 응답의 원본 키(콜러스 media_content_key)
  originalFileName?: string;
  score?: number;
  summary?: string;
  keywords?: string;
}

interface ContentLibraryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (content: Content) => void;
  multiSelect?: boolean;
  onMultiSelect?: (contents: Content[]) => void;
  recommendContext?: {
    courseName?: string;
    courseIntro?: string;
    courseDetail?: string;
    lessonTitle?: string;
    lessonDescription?: string;
  };
  excludeLessonIds?: Array<number | string>;
}

export function ContentLibraryModal({
  isOpen,
  onClose,
  onSelect,
  multiSelect = true,
  onMultiSelect,
  recommendContext,
  excludeLessonIds,
}: ContentLibraryModalProps) {
  // 왜: 콜러스 외부 영상 추천 기능이 완성되어, 추천 탭을 다시 노출합니다.
  const hideRecommendTab = false;
  // 왜: 사용자가 모달을 열었을 때 "추천"을 먼저 보길 원합니다. (추천 탭이 숨김이면 전체로 폴백)
  const defaultTab: 'recommend' | 'all' | 'external' = hideRecommendTab ? 'all' : 'recommend';

  // 왜: 추천 탭에서 같은 레슨(lessonId)이 중복으로 내려오는 경우가 있는데,
  //     운영 상황에 따라 "중복 제거(ON)" 또는 "중복 그대로 표시(OFF)"를 빠르게 바꿔야 할 수 있습니다.
  // - 지금은 이슈 확인을 위해 중복을 그대로 보이게(OFF) 둡니다.
  // - 나중에 원복할 때는 아래 값을 true로만 바꾸면 됩니다.
  const enableRecommendDedupe = false;
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [activeTab, setActiveTab] = useState<'recommend' | 'all' | 'external'>(defaultTab);

  // 왜: 외부링크 탭을 위한 입력 상태가 필요합니다.
  const [externalUrl, setExternalUrl] = useState('');
  const [externalTitle, setExternalTitle] = useState('');
  const [externalTime, setExternalTime] = useState<number | ''>('');
  const [externalAdding, setExternalAdding] = useState(false);

  const [contents, setContents] = useState<Content[]>([]);
  const [categories, setCategories] = useState<{ key: string; name: string }[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selecting, setSelecting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [recommendationsRaw, setRecommendationsRaw] = useState<Content[]>([]);
  const [recommendations, setRecommendations] = useState<Content[]>([]);
  const [recommendLoading, setRecommendLoading] = useState(false);
  const [recommendCacheKey, setRecommendCacheKey] = useState<string>('');

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [expandedId, setExpandedId] = useState<string | null>(null);  // 왜: 추천 탭에서 요약 아코디언을 위한 확장 상태
  const limit = 20;

  // 왜: 교수자 LMS에서 콘텐츠 선택 전에 영상을 미리 확인할 수 있어야 합니다.
  const handlePreview = (e: React.MouseEvent, content: Content) => {
    e.stopPropagation();
    if (!content.mediaKey) return;

    const previewUrl = `/kollus/preview.jsp?key=${encodeURIComponent(content.mediaKey)}`;
    const popupWidth = Math.max(Number(content.contentWidth ?? 0), 900);
    const popupHeight = Math.max(Number(content.contentHeight ?? 0), 506); // 16:9 기본

    const win = window.open(
      previewUrl,
      '_blank',
      `width=${popupWidth},height=${popupHeight},resizable=yes,scrollbars=yes`
    );
    if (!win) {
      setErrorMessage('팝업이 차단되었습니다. 브라우저에서 팝업 허용 후 다시 시도해 주세요.');
    }
  };

  React.useEffect(() => {
    if (!isOpen) return;

    // 왜: 모달을 다시 열 때는 이전 검색/선택이 남아 있으면 혼란스러워서 기본 상태로 초기화합니다.
    setSearchTerm('');
    setCategoryFilter('');
    setActiveTab(defaultTab);
    setPage(1);
    setContents([]);
    setTotalCount(0);
    setRecommendations([]);
    setRecommendationsRaw([]);
    setRecommendCacheKey('');
    setSelectedIds(new Set());
    // 왜: 외부링크 입력 상태도 초기화합니다.
    setExternalUrl('');
    setExternalTitle('');
    setExternalTime('');
  }, [isOpen]);

  React.useEffect(() => {
    if (!isOpen) return;

    // 왜: 탭을 바꾸면 조회 대상이 완전히 달라지므로, 선택/페이지/에러를 같이 초기화합니다.
    setPage(1);
    setContents([]);
    setTotalCount(0);
    setSelectedIds(new Set());
    setErrorMessage(null);
  }, [isOpen, activeTab]);

  React.useEffect(() => {
    if (!isOpen) return;

    // 왜: 검색/필터 변경 시 1페이지부터 다시 조회해야 정확한 목록이 됩니다.
    if (activeTab !== 'all') return;
    setPage(1);
    setContents([]);
    setTotalCount(0);
    setSelectedIds(new Set());
  }, [isOpen, activeTab, searchTerm, categoryFilter]);

  React.useEffect(() => {
    if (!isOpen) {
      setSelectedIds(new Set());
    }
  }, [isOpen]);

  React.useEffect(() => {
    if (!isOpen) return;
    if (activeTab !== 'all') return;

    let cancelled = false;
    const timer = setTimeout(() => {
      const fetchKollusList = async () => {
        const isFirstPage = page === 1;
        setLoading(isFirstPage);
        setLoadingMore(!isFirstPage);
        setErrorMessage(null);
        try {
          const res = await tutorLmsApi.getKollusList({
            keyword: searchTerm,
            categoryKey: categoryFilter || undefined,
            page,
            limit,
          });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);

          const rows = res.rst_data ?? [];
          const mapped: Content[] = rows.map((row) => ({
            id: String(row.id || row.media_content_key),
            mediaKey: row.media_content_key,
            title: row.title,
            description: row.original_file_name ? `원본파일이름: ${row.original_file_name}` : '',
            category: row.category_nm || '카테고리 없음',
            thumbnail: row.thumbnail || row.snapshot_url || '',
            duration: row.duration || '-',
            totalTime: Number(row.total_time ?? 0),
            contentWidth: Number(row.content_width ?? 0),
            contentHeight: Number(row.content_height ?? 0),
            originalFileName: row.original_file_name || '',
          }));

          const nextCategories = Array.isArray(res.rst_categories)
            ? (res.rst_categories as { key: string; name: string }[])
            : [];

          if (!cancelled) {
            setContents((prev) => (isFirstPage ? mapped : [...prev, ...mapped]));
            setTotalCount(Number(res.rst_total ?? mapped.length));
            if (nextCategories.length > 0) setCategories(nextCategories);
          }
        } catch (e) {
          if (!cancelled) setErrorMessage(e instanceof Error ? e.message : '조회 중 오류가 발생했습니다.');
        } finally {
          if (!cancelled) {
            setLoading(false);
            setLoadingMore(false);
          }
        }
      };

      fetchKollusList();
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [isOpen, activeTab, searchTerm, categoryFilter, page]);

  React.useEffect(() => {
    if (!isOpen) return;
    if (activeTab !== 'recommend') return;

    let cancelled = false;
    const timer = setTimeout(() => {
      const fetchRecommendations = async () => {
        const nextCacheKey = JSON.stringify({
          courseName: recommendContext?.courseName ?? '',
          courseIntro: recommendContext?.courseIntro ?? '',
          courseDetail: recommendContext?.courseDetail ?? '',
          lessonTitle: recommendContext?.lessonTitle ?? '',
          lessonDescription: recommendContext?.lessonDescription ?? '',
          keywords: searchTerm ?? '',
          // 왜: 제외(이미 추가된 레슨) 후에도 50개가 남도록, 더 넉넉히 받아옵니다.
          topK: 120,
          similarityThreshold: 0.2,
        });

        // 왜: 모달이 열려있는 동안 탭을 왔다갔다 해도, 같은 요청이면 네트워크를 다시 타지 않게 캐시를 사용합니다.
        if (recommendationsRaw.length > 0 && recommendCacheKey === nextCacheKey) return;

        setRecommendLoading(true);
        setErrorMessage(null);
        try {
          const res = await tutorLmsApi.getContentRecommendations({
            courseName: recommendContext?.courseName,
            courseIntro: recommendContext?.courseIntro,
            courseDetail: recommendContext?.courseDetail,
            lessonTitle: recommendContext?.lessonTitle,
            lessonDescription: recommendContext?.lessonDescription,
            // 왜: 추천 탭에서도 사용자가 제목 검색을 하면, 검색어를 "추가 키워드"로 함께 보내서 추천 품질을 올립니다.
            keywords: searchTerm,
            topK: 120,
            similarityThreshold: 0.2,
          });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);

          const rows = res.rst_data ?? [];
          const mapped: Content[] = rows.map((row, idx) => {
            const baseRecommendKey = String(row.lesson_id ?? row.media_content_key ?? row.id ?? '').trim();

            return {
              // 왜: 중복을 그대로 보여줄 때는 row의 고유 id가 겹칠 수 있어서, index를 붙여 React key/선택 id 충돌을 막습니다.
              //     (중복 제거를 다시 켜면 baseLessonId만 써도 안전합니다.)
              id: enableRecommendDedupe ? (baseRecommendKey || String(idx)) : (baseRecommendKey ? `${baseRecommendKey}__${idx}` : String(idx)),
              mediaKey: row.media_content_key || baseRecommendKey,
              title: row.title,
              description: '',
              category: (row.category_nm as string) || '카테고리 없음',
              thumbnail: (row.thumbnail as string) || (row.snapshot_url as string) || '',
              duration: (row.duration as string) || '-',
              totalTime: Number((row.total_time as number) ?? 0),
              contentWidth: Number((row.content_width as number) ?? 0),
              contentHeight: Number((row.content_height as number) ?? 0),
              // 왜: 추천 탭 원본 키를 유지해 로그/추적 시 어떤 추천 항목이었는지 식별합니다.
              recommendKey: baseRecommendKey || undefined,
              originalFileName: (row.original_file_name as string) || '',
              score: row.score ? Number(row.score) : undefined,
              summary: row.summary || '',
              keywords: row.keywords || '',
            };
          });

          if (!cancelled) {
            setRecommendationsRaw(mapped);
            setRecommendCacheKey(nextCacheKey);
          }
        } catch (e) {
          if (!cancelled) setErrorMessage(e instanceof Error ? e.message : '조회 중 오류가 발생했습니다.');
        } finally {
          if (!cancelled) setRecommendLoading(false);
        }
      };

      fetchRecommendations();
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [
    isOpen,
    activeTab,
    searchTerm,
    recommendationsRaw.length,
    recommendCacheKey,
    recommendContext?.courseName,
    recommendContext?.courseIntro,
    recommendContext?.courseDetail,
    recommendContext?.lessonTitle,
    recommendContext?.lessonDescription,
  ]);

  React.useEffect(() => {
    if (!isOpen) return;
    if (activeTab !== 'recommend') return;

    // 왜: 과목 전체 차시에 이미 추가된 레슨은 추천에서 제외해야 하며, 제외 후에도 50개를 유지하는 게 UX가 좋습니다.
    const excludeSet = new Set<string>();
    (excludeLessonIds ?? []).forEach((v) => {
      excludeSet.add(String(v));
    });

    // 왜: 추천 탭에서 중복 제거를 켜면(원래 동작), 같은 lessonId는 1개만 남깁니다.
    // const dedupe = new Set<string>();

    const filtered = recommendationsRaw
      .filter((c) => {
        if (!c.mediaKey) return false;
        if (c.lessonId && excludeSet.has(String(c.lessonId))) return false;
        if (c.recommendKey && excludeSet.has(c.recommendKey)) return false;
        // if (enableRecommendDedupe) {
        //   if (dedupe.has(c.recommendKey || c.mediaKey)) return false;
        //   dedupe.add(c.recommendKey || c.mediaKey);
        // }
        return true;
      })
      .slice(0, 50);

    setRecommendations(filtered);
  }, [isOpen, activeTab, excludeLessonIds, recommendationsRaw]);

  const displayedContents = activeTab === 'recommend' ? recommendations : contents;
  const displayedTotal = activeTab === 'recommend' ? recommendations.length : totalCount;

  const handleSelect = (content: Content) => {
    if (multiSelect) {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        if (next.has(content.id)) next.delete(content.id);
        else next.add(content.id);
        return next;
      });
      return;
    }

    handleSingleSelect(content);
  };

  const handleSingleSelect = async (content: Content) => {
    try {
      setSelecting(true);
      // 왜: 추천/전체 탭 모두 "같은 영상 추가 경로"를 타야 lessonId/시간정보가 일관되게 저장됩니다.
      const res = await tutorLmsApi.upsertKollusLesson({
        mediaContentKey: content.mediaKey,
        title: content.title,
        totalTime: content.totalTime,
        contentWidth: content.contentWidth,
        contentHeight: content.contentHeight,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const lessonId = Number(res.rst_data ?? 0);
      if (lessonId <= 0) throw new Error('레슨 ID를 생성하지 못했습니다.');
      const next = { ...content, lessonId, id: String(lessonId) };
      onSelect(next);
      onClose();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : '레슨 등록 중 오류가 발생했습니다.');
    } finally {
      setSelecting(false);
    }
  };

  const handleAddSelected = async () => {
    const selected = displayedContents.filter((c) => selectedIds.has(c.id));
    if (selected.length === 0) return;

    try {
      setSelecting(true);
      const mapped = await Promise.all(
        selected.map(async (content) => {
          // 왜: 추천/전체 탭 모두 업서트 경로를 통일해 저장 포맷을 동일하게 유지합니다.
          const res = await tutorLmsApi.upsertKollusLesson({
            mediaContentKey: content.mediaKey,
            title: content.title,
            totalTime: content.totalTime,
            contentWidth: content.contentWidth,
            contentHeight: content.contentHeight,
          });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);
          const lessonId = Number(res.rst_data ?? 0);
          if (lessonId <= 0) throw new Error('레슨 ID를 생성하지 못했습니다.');
          return { ...content, lessonId, id: String(lessonId) };
        })
      );

      if (onMultiSelect) onMultiSelect(mapped);
      else mapped.forEach((c) => onSelect(c));

      onClose();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : '레슨 등록 중 오류가 발생했습니다.');
    } finally {
      setSelecting(false);
    }
  };

  const handleSelectAll = () => {
    const ids = displayedContents.map((c) => c.id);
    const allSelected = ids.length > 0 && ids.every((id) => selectedIds.has(id));

    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        ids.forEach((id) => next.delete(id));
      } else {
        ids.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  // 왜: 외부링크 URL을 직접 입력하여 레슨으로 추가하기 위함입니다.
  const handleAddExternalLink = async () => {
    if (!externalUrl.trim()) {
      setErrorMessage('URL을 입력해주세요.');
      return;
    }
    if (!externalTitle.trim()) {
      setErrorMessage('강의명을 입력해주세요.');
      return;
    }
    if (!externalUrl.startsWith('http://') && !externalUrl.startsWith('https://')) {
      setErrorMessage('URL은 http:// 또는 https://로 시작해야 합니다.');
      return;
    }

    try {
      setExternalAdding(true);
      setErrorMessage(null);

      const res = await tutorLmsApi.upsertExternalLinkLesson({
        url: externalUrl.trim(),
        title: externalTitle.trim(),
        totalTime: typeof externalTime === 'number' ? externalTime : undefined,
      });

      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const lessonId = Number(res.rst_data ?? 0);
      const content: Content = {
        id: String(lessonId),
        mediaKey: externalUrl.trim(),
        title: externalTitle.trim(),
        description: '',
        category: '외부링크',
        thumbnail: '',
        totalTime: typeof externalTime === 'number' ? externalTime : 0,
        lessonId,
      };

      if (onMultiSelect) {
        onMultiSelect([content]);
      } else {
        onSelect(content);
      }
      onClose();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : '외부링크 추가 중 오류가 발생했습니다.');
    } finally {
      setExternalAdding(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-6xl w-full max-h-[90vh] overflow-hidden flex flex-col">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-gray-900">콘텐츠 라이브러리</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
            <X className="w-6 h-6" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              {!hideRecommendTab && (
                <button
                  type="button"
                  onClick={() => setActiveTab('recommend')}
                  className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
                    activeTab === 'recommend'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  추천
                </button>
              )}
              <button
                type="button"
                onClick={() => setActiveTab('all')}
                className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
                  activeTab === 'all' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                전체
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('external')}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm transition-colors ${
                  activeTab === 'external' ? 'bg-green-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                <Link className="w-4 h-4" />
                외부링크
              </button>
            </div>

            <div className="text-sm text-gray-600">총: {displayedTotal}건</div>
          </div>

          {activeTab !== 'external' && (
          <div className="flex items-center gap-3 mb-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder={activeTab === 'recommend' ? '추천 키워드 추가...' : '제목으로 검색...'}
                className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            {activeTab === 'all' && (
              <select
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
                className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">전체</option>
                {categories.map((c) => (
                  <option key={c.key} value={c.key}>
                    {c.name}
                  </option>
                ))}
              </select>
            )}
          </div>
          )}

          {activeTab !== 'external' && (
          <div className="flex items-center justify-between mb-3">
            <div className="text-sm text-gray-600">
              총: <span className="text-blue-600 font-medium">{activeTab === 'all' ? totalCount : displayedTotal}</span>건
            </div>
            {multiSelect && (
              <button
                onClick={handleAddSelected}
                disabled={selectedIds.size === 0 || selecting}
                className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-colors ${
                  selectedIds.size > 0 && !selecting
                    ? 'bg-blue-600 text-white hover:bg-blue-700'
                    : 'bg-gray-200 text-gray-400 cursor-not-allowed'
                }`}
              >
                <Check className="w-4 h-4" />
                <span>선택추가 ({selectedIds.size}건)</span>
              </button>
            )}
          </div>
          )}

          {errorMessage && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
              {errorMessage}
            </div>
          )}

          {/* 외부링크 탭: 입력 폼 UI */}
          {activeTab === 'external' && (
            <div className="border border-gray-200 rounded-lg p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">외부 URL로 콘텐츠 추가</h3>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    URL <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="url"
                    value={externalUrl}
                    onChange={(e) => setExternalUrl(e.target.value)}
                    placeholder="https://example.com/video"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500"
                  />
                  <p className="text-xs text-gray-500 mt-1">YouTube, Vimeo 등 외부 영상 URL을 입력하세요</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    강의명 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={externalTitle}
                    onChange={(e) => setExternalTitle(e.target.value)}
                    placeholder="강의 제목을 입력하세요"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">학습시간 (분)</label>
                  <input
                    type="number"
                    min="0"
                    value={externalTime}
                    onChange={(e) => setExternalTime(e.target.value === '' ? '' : Number(e.target.value))}
                    placeholder="예: 30"
                    className="w-32 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-green-500"
                  />
                </div>
                <div className="pt-2">
                  <button
                    type="button"
                    onClick={handleAddExternalLink}
                    disabled={externalAdding}
                    className="flex items-center gap-2 px-6 py-2.5 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
                  >
                    <Link className="w-4 h-4" />
                    {externalAdding ? '추가 중...' : '외부링크 추가'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* 전체/추천 탭: 기존 목록 UI */}
          {activeTab !== 'external' && (loading || recommendLoading) && (
            <div className="py-12 text-center text-gray-500">
              <p>불러오는 중...</p>
            </div>
          )}

          {activeTab !== 'external' && !loading && !recommendLoading && (
            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <table className="w-full">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    {multiSelect && (
                      <th className="px-3 py-2 w-10">
                        <input
                          type="checkbox"
                          checked={
                            displayedContents.length > 0 &&
                            displayedContents.every((c) => selectedIds.has(c.id))
                          }
                          onChange={handleSelectAll}
                          className="w-4 h-4 text-blue-600 rounded"
                        />
                      </th>
                    )}
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-600">No</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-600">카테고리</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-600">
                      {activeTab === 'recommend' ? '제목 / 키워드' : '강의명'}
                    </th>
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-600">시간</th>
                    {activeTab !== 'recommend' && (
                      <th className="px-3 py-2 text-left text-xs font-medium text-gray-600">원본파일</th>
                    )}
                    <th className="px-3 py-2 text-center text-xs font-medium text-gray-600">미리보기</th>
                    {activeTab === 'recommend' && (
                      <th className="px-3 py-2 text-center text-xs font-medium text-gray-600">내용</th>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {displayedContents.map((content, index) => (
                    <React.Fragment key={content.id}>
                      <tr
                        className={`hover:bg-blue-50 cursor-pointer transition-colors ${
                          selectedIds.has(content.id) ? 'bg-blue-50' : ''
                        }`}
                        onClick={() => handleSelect(content)}
                      >
                        {multiSelect && (
                          <td className="px-3 py-2 text-center">
                            <input
                              type="checkbox"
                              checked={selectedIds.has(content.id)}
                              onChange={() => {}}
                              className="w-4 h-4 text-blue-600 rounded"
                            />
                          </td>
                        )}
                        <td className="px-3 py-2 text-sm text-gray-600">{index + 1}</td>
                        <td className="px-3 py-2">
                          <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">
                            {content.category}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-sm text-gray-900">
                          <div className="max-w-md">
                            <div className="truncate font-medium">{content.title}</div>
                            {activeTab === 'recommend' && content.keywords && (
                              <div className="mt-1 flex flex-wrap gap-1">
                                {content.keywords.split(',').slice(0, 5).map((kw, i) => (
                                  <span key={i} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                                    {kw.trim()}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-2 text-center text-sm text-gray-600">
                          {content.totalTime ? `${content.totalTime}분` : content.duration}
                        </td>
                        {activeTab !== 'recommend' && (
                          <td className="px-3 py-2 text-sm text-gray-500 max-w-xs truncate">
                            {content.originalFileName || '-'}
                          </td>
                        )}
                        <td className="px-3 py-2 text-center">
                          <button
                            type="button"
                            onClick={(e) => handlePreview(e, content)}
                            disabled={!content.mediaKey}
                            className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-100 hover:bg-blue-200 text-blue-700 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title="영상 미리보기"
                          >
                            <Play className="w-3 h-3" />
                            <span>미리보기</span>
                          </button>
                        </td>
                        {activeTab === 'recommend' && (
                          <td className="px-3 py-2 text-center">
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setExpandedId(expandedId === content.id ? null : content.id);
                              }}
                              className="px-2 py-1 text-xs bg-gray-100 hover:bg-gray-200 text-gray-600 rounded transition-colors"
                            >
                              {expandedId === content.id ? '닫기' : '보기'}
                            </button>
                          </td>
                        )}
                      </tr>
                      {/* 왜: 추천 탭에서 요약 내용을 펼쳐서 보여주는 아코디언 행 */}
                      {activeTab === 'recommend' && expandedId === content.id && content.summary && (
                        <tr className="bg-gray-50">
                          <td colSpan={multiSelect ? 7 : 6} className="px-6 py-4">
                            <div className="text-sm text-gray-700 whitespace-pre-wrap">
                              <strong className="text-gray-900">요약:</strong> {content.summary}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
                </tbody>
              </table>

              {displayedContents.length === 0 && (
                <div className="text-center py-12 text-gray-500">
                  <p>{activeTab === 'recommend' ? '추천 결과가 없습니다.' : '검색 결과가 없습니다.'}</p>
                </div>
              )}
            </div>
          )}

          {!loading && !recommendLoading && activeTab === 'all' && contents.length < totalCount && (
            <div className="flex justify-center mt-6">
              <button
                type="button"
                onClick={() => setPage((prev) => prev + 1)}
                disabled={loadingMore}
                className="px-6 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              >
                {loadingMore ? '불러오는 중...' : '더보기'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
