import React, { useState, useRef, useEffect } from 'react';
import { Search, BookOpen, Heart, Play, Upload, Pencil, Check, X } from 'lucide-react';
import { tutorLmsApi } from '../api/tutorLmsApi';
import { KollusUploadModal } from './KollusUploadModal';

interface Content {
  id: string;
  mediaKey: string;
  title: string;
  description: string;
  category: string;
  categoryKey?: string;
  snapshotUrl?: string;
  originalFileName?: string;
  thumbnail: string;
  isFavorite: boolean;
  duration?: string;
  totalTime?: number;
  contentWidth?: number;
  contentHeight?: number;
}

interface ContentLibraryPageProps {
  activeTab: 'all' | 'favorites';
}

export function ContentLibraryPage({ activeTab }: ContentLibraryPageProps) {
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');

  const [contents, setContents] = useState<Content[]>([]);
  const [categories, setCategories] = useState<{ key: string; name: string }[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(1);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);

  // ---------- 제목 인라인 편집 ----------
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingTitle, setEditingTitle] = useState('');
  const editInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (editingId && editInputRef.current) editInputRef.current.focus();
  }, [editingId]);

  const startEditTitle = (e: React.MouseEvent, content: Content) => {
    e.stopPropagation();
    setEditingId(content.id);
    setEditingTitle(content.title);
  };

  const cancelEditTitle = (e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingId(null);
    setEditingTitle('');
  };

  const saveEditTitle = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!editingId || !editingTitle.trim()) return;
    // 왜: 로컬 상태만 업데이트합니다. 백엔드 연동 시 아래 주석을 해제하세요.
    // await tutorLmsApi.updateKollusTitle({ mediaContentKey, title: editingTitle.trim() });
    setContents(prev => prev.map(c =>
      c.id === editingId ? { ...c, title: editingTitle.trim() } : c,
    ));
    setEditingId(null);
    setEditingTitle('');
  };

  const limit = 30;

  // 검색/필터 변경 시 초기화
  React.useEffect(() => {
    setPage(1);
    setContents([]);
    setTotalCount(0);
  }, [searchTerm, categoryFilter, activeTab]);

  // 콘텐츠 목록 불러오기(콜러스)
  React.useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      const fetchKollusList = async () => {
        const isFirstPage = page === 1;
        setLoading(isFirstPage);
        setLoadingMore(!isFirstPage);
        setErrorMessage(null);
        try {
          const res =
            activeTab === 'favorites'
              ? await tutorLmsApi.getKollusWishlistList({
                  keyword: searchTerm,
                  page,
                  limit,
                })
              : await tutorLmsApi.getKollusList({
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
            categoryKey: row.category_key,
            snapshotUrl: row.snapshot_url,
            originalFileName: row.original_file_name || '',
            thumbnail: row.thumbnail || row.snapshot_url || '',
            duration: row.duration || '-',
            totalTime: Number(row.total_time ?? 0),
            contentWidth: Number(row.content_width ?? 0),
            contentHeight: Number(row.content_height ?? 0),
            isFavorite: Boolean(row.is_favorite),
          }));

          const nextCategories = Array.isArray(res.rst_categories)
            ? (res.rst_categories as { key: string; name: string }[])
            : [];

          if (!cancelled) {
            setContents((prev) => (isFirstPage ? mapped : [...prev, ...mapped]));
            setTotalCount(Number(res.rst_total ?? mapped.length));
            if (activeTab !== 'favorites' && nextCategories.length > 0) setCategories(nextCategories);
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
  }, [activeTab, searchTerm, categoryFilter, page]);

  const categoryOptions = categories;

  const handleToggleFavorite = async (e: React.MouseEvent, content: Content) => {
    e.preventDefault();
    e.stopPropagation();

    try {
      const res = await tutorLmsApi.toggleKollusWishlist({
        mediaContentKey: content.mediaKey,
        title: content.title,
        snapshotUrl: content.snapshotUrl || content.thumbnail,
        categoryKey: content.categoryKey,
        categoryName: content.category,
        originalFileName: content.originalFileName,
        totalTime: content.totalTime,
        contentWidth: content.contentWidth,
        contentHeight: content.contentHeight,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const next = Number(res.rst_data ?? 0) === 1;
      setContents((prev) => {
        if (activeTab === 'favorites' && !next) {
          return prev.filter((c) => c.mediaKey !== content.mediaKey);
        }
        return prev.map((c) => (c.mediaKey === content.mediaKey ? { ...c, isFavorite: next } : c));
      });
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : '찜 처리 중 오류가 발생했습니다.');
    }
  };

  const handlePreview = (content: Content) => {
    // 왜: 교수자 LMS에서도 sysop처럼 "영상 미리보기"를 바로 확인할 수 있어야 합니다.
    //     콜러스는 외부 플레이어 URL로 리다이렉트되는 구조이므로, 전용 preview JSP를 새 창으로 엽니다.
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

  return (
    <div className="max-w-7xl mx-auto">
      {/* Page Header */}
      <div className="mb-8">
        <h2 className="text-gray-900 mb-2">
          {activeTab === 'all' ? '전체 콘텐츠' : '찜한 콘텐츠'}
        </h2>
        <p className="text-gray-600">
          {activeTab === 'all' 
            ? '모든 교육 콘텐츠를 검색하고 과정에 활용하세요.'
            : '즐겨찾기한 콘텐츠만 모아서 확인하세요.'}
        </p>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-6">
        <div className="flex items-center gap-4">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="콘텐츠 제목이나 태그로 검색..."
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          {activeTab !== 'favorites' && (
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">전체</option>
              {categoryOptions.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
          <div className="text-sm text-gray-600">
            총 <span className="text-blue-600">{totalCount}</span>개의 콘텐츠
          </div>
          {/* 업로드 버튼 - 전체 콘텐츠 탭에서만 표시 */}
          {activeTab === 'all' && (
            <button
              onClick={() => setUploadModalOpen(true)}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              <Upload className="w-4 h-4" />
              <span>영상 업로드</span>
            </button>
          )}
        </div>
      </div>

      {errorMessage && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6">
          {errorMessage}
        </div>
      )}

      {loading && (
        <div className="bg-white rounded-lg border border-gray-200 p-16 text-center text-gray-500">
          <p>불러오는 중...</p>
        </div>
      )}

      {/* Content Grid */}
      {!loading && (
        <div className="grid grid-cols-3 gap-6">
          {contents.map((content) => (
            <div
              key={content.id}
              className="group bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
              role="button"
              tabIndex={0}
              onClick={() => handlePreview(content)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') handlePreview(content);
              }}
            >
              {/* Thumbnail */}
              <div className="relative h-40 bg-gray-100">
                {content.thumbnail ? (
                  <img
                    src={content.thumbnail}
                    alt={content.title}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center">
                    <BookOpen className="w-12 h-12 text-gray-300" />
                  </div>
                )}
                <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="w-12 h-12 rounded-full bg-black bg-opacity-60 flex items-center justify-center">
                    <Play className="w-6 h-6 text-white ml-0.5" />
                  </div>
                </div>
                <div className="absolute top-2 left-2 bg-white px-2 py-1 rounded text-xs">
                  {content.category}
                </div>
                <button
                  type="button"
                  onClick={(e) => handleToggleFavorite(e, content)}
                  className="absolute top-2 right-2 w-8 h-8 bg-white rounded-full flex items-center justify-center hover:bg-gray-50 transition-colors"
                  title={content.isFavorite ? '찜 해제' : '찜하기'}
                >
                  <Heart
                    className={`w-4 h-4 ${
                      content.isFavorite ? 'fill-red-500 text-red-500' : 'text-gray-400'
                    }`}
                  />
                </button>
                {content.duration && content.duration !== '-' && (
                  <div className="absolute bottom-2 right-2 bg-black bg-opacity-75 text-white px-2 py-1 rounded text-xs">
                    {content.duration}
                  </div>
                )}
              </div>

              {/* Content Info */}
              <div className="p-4">
                {editingId === content.id ? (
                  <div className="flex items-center gap-1.5 mb-2" onClick={e => e.stopPropagation()}>
                    <input
                      ref={editInputRef}
                      value={editingTitle}
                      onChange={e => setEditingTitle(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter') saveEditTitle(e as any);
                        if (e.key === 'Escape') { setEditingId(null); setEditingTitle(''); }
                      }}
                      className="flex-1 px-2 py-1 border border-blue-400 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <button
                      onClick={saveEditTitle}
                      className="p-1 text-green-600 hover:bg-green-50 rounded transition-colors"
                      title="저장"
                    >
                      <Check className="w-4 h-4" />
                    </button>
                    <button
                      onClick={cancelEditTitle}
                      className="p-1 text-gray-400 hover:bg-gray-100 rounded transition-colors"
                      title="취소"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-1.5 mb-2 group/title">
                    <h4 className="text-gray-900 line-clamp-1 flex-1">{content.title}</h4>
                    <button
                      onClick={(e) => startEditTitle(e, content)}
                      className="p-1 text-gray-300 hover:text-blue-600 hover:bg-blue-50 rounded opacity-0 group-hover/title:opacity-100 transition-all shrink-0"
                      title="제목 수정"
                    >
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
                <p className="text-sm text-gray-600 mb-3 line-clamp-2">{content.description}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && !errorMessage && contents.length === 0 && (
        <div className="bg-white rounded-lg border-2 border-dashed border-gray-300 p-16 text-center">
          <div className="text-gray-400">
            <BookOpen className="w-16 h-16 mx-auto mb-4 opacity-50" />
            <p className="text-lg">검색 결과가 없습니다</p>
            <p className="text-sm mt-2">다른 검색어나 필터를 시도해보세요</p>
          </div>
        </div>
      )}

      {!loading && contents.length < totalCount && (
        <div className="flex justify-center mt-8">
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

      {/* Kollus 업로드 모달 */}
      <KollusUploadModal
        isOpen={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        categories={categories}
      />
    </div>
  );
}
