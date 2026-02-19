import React, { useEffect, useMemo, useState } from 'react';
import { ClipboardCheck, Clock } from 'lucide-react';
import { tutorLmsApi, type TutorHomeworkSubmissionRow } from '../api/tutorLmsApi';
import type { CourseManagementTabId } from './CourseManagement';

type CourseLinkPayload = {
  courseId: number;
  courseName?: string;
  targetTab?: CourseManagementTabId;
  sourceType?: 'prism' | 'haksa';
};

export function AssignmentManagePage({
  onOpenCourse,
}: {
  onOpenCourse?: (payload: CourseLinkPayload) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [rows, setRows] = useState<TutorHomeworkSubmissionRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);
  const [statusFilter, setStatusFilter] = useState<'all' | 'unconfirmed' | 'confirmed'>('all');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const pageNumbers = useMemo(() => {
    const start = Math.max(1, page - 2);
    const end = Math.min(totalPages, page + 2);
    return Array.from({ length: end - start + 1 }, (_, idx) => start + idx);
  }, [page, totalPages]);

  useEffect(() => {
    let cancelled = false;

    const fetchRows = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getHomeworkSubmissions({
          keyword: searchKeyword || undefined,
          page,
          pageSize,
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          status: statusFilter === 'all' ? undefined : statusFilter,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        if (cancelled) return;
        setRows(res.rst_data ?? []);
        setTotalCount(Number(res.rst_total ?? res.rst_count ?? (res.rst_data ?? []).length));
      } catch (e) {
        if (!cancelled) setErrorMessage(e instanceof Error ? e.message : '과제 목록을 불러오는 중 오류가 발생했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void fetchRows();
    return () => {
      cancelled = true;
    };
  }, [page, pageSize, searchKeyword, startDate, endDate, statusFilter]);

  const handleSearch = () => {
    // 왜: 검색어가 바뀌면 1페이지부터 다시 조회해야 결과가 어긋나지 않습니다.
    setPage(1);
    setSearchKeyword(keyword.trim());
  };

  const handleFilterApply = () => {
    // 왜: 기간/상태 필터 변경 시 첫 페이지부터 다시 조회합니다.
    setPage(1);
  };

  const openCourse = (row: TutorHomeworkSubmissionRow) => {
    // 왜: 통합관리에서 과제 상세는 과목의 “과제 피드백” 탭에서 바로 열리는 게 가장 빠릅니다.
    onOpenCourse?.({
      courseId: row.course_id,
      courseName: row.course_nm,
      targetTab: 'assignment-feedback',
      sourceType: row.source_type === 'haksa' ? 'haksa' : 'prism',
    });
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-gray-900 mb-1">제출 현황</h1>
        <p className="text-gray-600">모든 과목의 과제 제출을 한 곳에서 확인합니다.</p>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-4 flex flex-col gap-3">
        <div className="flex flex-col md:flex-row gap-3 md:items-center">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="과제명/과목명/학생명 검색"
          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSearch();
          }}
        />
          <div className="flex items-center gap-2">
          <select
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value));
              setPage(1);
            }}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value={20}>20개</option>
            <option value={50}>50개</option>
            <option value={100}>100개</option>
          </select>
          <button
            onClick={handleSearch}
            className="px-4 py-2 bg-gray-900 text-white rounded-lg hover:bg-gray-800 transition-colors"
          >
            검색
          </button>
        </div>
        </div>
        <div className="flex flex-col md:flex-row gap-3 md:items-center">
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-600">기간</span>
            <input
              type="date"
              value={startDate}
              onChange={(e) => {
                setStartDate(e.target.value);
                handleFilterApply();
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
            <span className="text-sm text-gray-400">~</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => {
                setEndDate(e.target.value);
                handleFilterApply();
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            />
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-600">상태</span>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as 'all' | 'unconfirmed' | 'confirmed');
                handleFilterApply();
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="all">전체</option>
              <option value="unconfirmed">미확인</option>
              <option value="confirmed">확인완료</option>
            </select>
          </div>
        </div>
      </div>

      {errorMessage && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {errorMessage}
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-lg">
        <div className="p-6 border-b border-gray-200 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ClipboardCheck className="w-5 h-5 text-orange-600" />
            <h3 className="text-gray-900">과제 제출 목록</h3>
          </div>
          <div className="text-sm text-gray-500">총 {totalCount}건</div>
        </div>
        {loading ? (
          <div className="p-10 text-center text-gray-500">불러오는 중...</div>
        ) : rows.length === 0 ? (
          <div className="p-10 text-center text-gray-500">조회 결과가 없습니다.</div>
        ) : (
          <div className="divide-y divide-gray-200">
            {rows.map((row) => {
              const confirmed = Boolean(row.confirmed);
              const studentLabel = `${row.user_nm || '-'} · ${row.course_nm}`;
              const submittedAt = row.submitted_at || '-';

              return (
                <button
                  key={`${row.homework_id}-${row.course_user_id}`}
                  type="button"
                  onClick={() => openCourse(row)}
                  className="w-full text-left p-6 hover:bg-gray-50 transition-colors"
                >
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1">
                      <h4 className="text-gray-900 mb-1">{row.homework_nm}</h4>
                      <p className="text-sm text-gray-600 mb-1">{studentLabel}</p>
                    </div>
                    {!confirmed ? (
                      <span className="px-2 py-1 bg-orange-100 text-orange-700 text-xs rounded-full whitespace-nowrap">
                        미확인
                      </span>
                    ) : (
                      <span className="px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full whitespace-nowrap">
                        확인완료
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-1 text-sm text-gray-500">
                    <Clock className="w-4 h-4" />
                    <span>{submittedAt}</span>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="flex items-center justify-center gap-2">
        <button
          onClick={() => setPage((prev) => Math.max(1, prev - 1))}
          disabled={page <= 1}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 disabled:opacity-50"
        >
          이전
        </button>
        {pageNumbers.map((num) => (
          <button
            key={num}
            onClick={() => setPage(num)}
            className={`px-3 py-2 border rounded-lg text-sm ${
              num === page
                ? 'bg-blue-600 border-blue-600 text-white'
                : 'border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}
          >
            {num}
          </button>
        ))}
        <button
          onClick={() => setPage((prev) => Math.min(totalPages, prev + 1))}
          disabled={page >= totalPages}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 disabled:opacity-50"
        >
          다음
        </button>
      </div>
    </div>
  );
}
