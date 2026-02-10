import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useRef } from 'react';

// ============================================================================
// 왜 필요한가:
// - 담당과목(MyCoursesList)에서 필터(탭/연도/검색어 등)를 변경할 때마다
//   여러 useState와 useEffect가 서로 충돌하면서 화면이 깜빡이는 문제가 있었습니다.
// - 이 Context는 필터 상태를 한 곳에서 관리하고, URL을 "진실의 원천"으로 사용해서
//   상태 꼬임을 원천 차단합니다.
// ============================================================================

// =====================
// 타입 정의
// =====================

export type TabType = 'haksa' | 'prism';

// 왜: 필터 상태를 한 객체로 묶으면, 여러 값을 동시에 바꿔도 리렌더가 한 번만 일어납니다.
export interface CourseFilterState {
  tab: TabType;
  year: string;
  keyword: string;
  page: number;
  pageSize: number;
  // 프리즘 탭 전용
  courseType: string;
  status: string;
  // 학사 탭 전용
  haksaCategory: string;
  haksaGrad: string;
  haksaCurriculum: string;
  sortOrder: 'asc' | 'desc';
}

// 왜: 액션 타입을 명확히 정의하면, 어떤 변경이 가능한지 한눈에 파악할 수 있습니다.
export type CourseFilterAction =
  | { type: 'SET_TAB'; payload: TabType }
  | { type: 'SET_YEAR'; payload: string }
  | { type: 'SET_KEYWORD'; payload: string }
  | { type: 'SET_PAGE'; payload: number }
  | { type: 'SET_PAGE_SIZE'; payload: number }
  | { type: 'SET_COURSE_TYPE'; payload: string }
  | { type: 'SET_STATUS'; payload: string }
  | { type: 'SET_HAKSA_CATEGORY'; payload: string }
  | { type: 'SET_HAKSA_GRAD'; payload: string }
  | { type: 'SET_HAKSA_CURRICULUM'; payload: string }
  | { type: 'SET_SORT_ORDER'; payload: 'asc' | 'desc' }
  | { type: 'SYNC_FROM_URL'; payload: Partial<CourseFilterState> }
  | { type: 'RESET_FILTERS' };

const currentYear = String(new Date().getFullYear());
const PRISM_DEFAULT_YEAR = '2024';

// 왜: 초기 상태를 한 곳에 정의해두면 리셋할 때도 편리합니다.
export const DEFAULT_FILTER_STATE: CourseFilterState = {
  tab: 'haksa',
  year: currentYear,
  keyword: '',
  page: 1,
  pageSize: 20,
  courseType: '전체',
  status: '전체',
  haksaCategory: '전체',
  haksaGrad: '전체',
  haksaCurriculum: '전체',
  sortOrder: 'desc',
};

// =====================
// URL ↔ 상태 변환 유틸
// =====================

// 왜: URL에서 필터 상태를 파싱하는 로직을 분리하면, 테스트하기 쉽고 재사용 가능합니다.
export function parseFiltersFromParams(params: Record<string, string>): Partial<CourseFilterState> {
  const result: Partial<CourseFilterState> = {};

  // 탭 (source 또는 listTab 또는 tab에서 읽음)
  const tabValue = params.source || params.listTab || params.tab;
  if (tabValue === 'haksa' || tabValue === 'prism') {
    result.tab = tabValue;
  }

  // 연도
  if (params.year) {
    result.year = params.year;
  }

  // 검색어
  if (params.keyword !== undefined) {
    result.keyword = params.keyword;
  }

  // 페이지
  const pageNum = Number(params.page);
  if (!Number.isNaN(pageNum) && pageNum > 0) {
    result.page = Math.floor(pageNum);
  }

  // 페이지 크기
  const pageSizeNum = Number(params.pageSize);
  if (!Number.isNaN(pageSizeNum) && pageSizeNum > 0) {
    result.pageSize = Math.floor(pageSizeNum);
  }

  // 프리즘 전용 필터
  if (params.courseType) result.courseType = params.courseType;
  if (params.status) result.status = params.status;

  // 학사 전용 필터
  if (params.haksaCategory) result.haksaCategory = params.haksaCategory;
  if (params.haksaGrad) result.haksaGrad = params.haksaGrad;
  if (params.haksaCurriculum) result.haksaCurriculum = params.haksaCurriculum;
  if (params.sortOrder === 'asc' || params.sortOrder === 'desc') {
    result.sortOrder = params.sortOrder;
  }

  return result;
}

// 왜: 상태를 URL 파라미터로 변환할 때, 기본값은 생략해서 URL을 깔끔하게 유지합니다.
export function buildParamsFromFilters(state: CourseFilterState): Record<string, string> {
  const params: Record<string, string> = {};

  // 탭: 기본값(haksa)이면 생략
  if (state.tab !== 'haksa') params.source = state.tab;

  // 연도: 있으면 항상 표시
  if (state.year) params.year = state.year;

  // 검색어: 있으면 표시
  if (state.keyword) params.keyword = state.keyword;

  // 페이지: 1이면 생략
  if (state.page > 1) params.page = String(state.page);

  // 페이지 크기: 기본값(20)이면 생략
  if (state.pageSize !== 20) params.pageSize = String(state.pageSize);

  // 프리즘 필터: 기본값이면 생략
  if (state.tab === 'prism') {
    if (state.courseType !== '전체') params.courseType = state.courseType;
    if (state.status !== '전체') params.status = state.status;
  }

  // 학사 필터: 기본값이면 생략
  if (state.tab === 'haksa') {
    if (state.haksaCategory !== '전체') params.haksaCategory = state.haksaCategory;
    if (state.haksaGrad !== '전체') params.haksaGrad = state.haksaGrad;
    if (state.haksaCurriculum !== '전체') params.haksaCurriculum = state.haksaCurriculum;
    if (state.sortOrder !== 'desc') params.sortOrder = state.sortOrder;
  }

  return params;
}

// =====================
// Reducer
// =====================

// 왜: Reducer를 쓰면 여러 상태를 한 번에 업데이트할 수 있어서 불필요한 리렌더가 줄어듭니다.
function filterReducer(state: CourseFilterState, action: CourseFilterAction): CourseFilterState {
  switch (action.type) {
    case 'SET_TAB':
      // 왜: 비정규 탭 기본값은 2024지만, 사용자가 직접 고른 년도(예: 2025)는 유지해야 합니다.
      //     그래서 "초기 기본년도(currentYear) 상태에서 prism으로 처음 전환"할 때만 2024를 적용합니다.
      return {
        ...state,
        tab: action.payload,
        year:
          action.payload === 'prism' && state.year === currentYear
            ? PRISM_DEFAULT_YEAR
            : state.year,
        page: 1,
      };

    case 'SET_YEAR':
      return { ...state, year: action.payload, page: 1 };

    case 'SET_KEYWORD':
      return { ...state, keyword: action.payload, page: 1 };

    case 'SET_PAGE':
      return { ...state, page: action.payload };

    case 'SET_PAGE_SIZE':
      return { ...state, pageSize: action.payload, page: 1 };

    case 'SET_COURSE_TYPE':
      return { ...state, courseType: action.payload, page: 1 };

    case 'SET_STATUS':
      return { ...state, status: action.payload, page: 1 };

    case 'SET_HAKSA_CATEGORY':
      return { ...state, haksaCategory: action.payload, page: 1 };

    case 'SET_HAKSA_GRAD':
      return { ...state, haksaGrad: action.payload, page: 1 };

    case 'SET_HAKSA_CURRICULUM':
      return { ...state, haksaCurriculum: action.payload, page: 1 };

    case 'SET_SORT_ORDER':
      return { ...state, sortOrder: action.payload, page: 1 };

    case 'SYNC_FROM_URL':
      // 왜: URL에서 동기화할 때는 부분 업데이트만 합니다 (없는 필드는 유지).
      return { ...state, ...action.payload };

    case 'RESET_FILTERS':
      return { ...DEFAULT_FILTER_STATE, year: state.year }; // 연도는 유지

    default:
      return state;
  }
}

// =====================
// Context 정의
// =====================

interface CourseFilterContextValue {
  // 현재 필터 상태
  filters: CourseFilterState;
  // 개별 필터 변경 함수들
  setTab: (tab: TabType) => void;
  setYear: (year: string) => void;
  setKeyword: (keyword: string) => void;
  setPage: (page: number) => void;
  setPageSize: (pageSize: number) => void;
  setCourseType: (courseType: string) => void;
  setStatus: (status: string) => void;
  setHaksaCategory: (category: string) => void;
  setHaksaGrad: (grad: string) => void;
  setHaksaCurriculum: (curriculum: string) => void;
  setSortOrder: (order: 'asc' | 'desc') => void;
  resetFilters: () => void;
  // URL 동기화 관련
  syncToUrl: () => void;
  syncFromUrl: (params: Record<string, string>) => void; // 왜: 뒤로가기 시 URL → 상태 동기화용
  getUrlParams: () => Record<string, string>;
}

const CourseFilterContext = createContext<CourseFilterContextValue | null>(null);

// =====================
// Provider 컴포넌트
// =====================

interface CourseFilterProviderProps {
  children: React.ReactNode;
  // 왜: 외부(App.tsx)에서 초기 URL 파라미터를 전달받습니다.
  initialParams?: Record<string, string>;
  // 왜: URL이 변경될 때 부모에게 알려서 해시 주소를 동기화합니다.
  onParamsChange?: (params: Record<string, string>) => void;
}

export function CourseFilterProvider({
  children,
  initialParams = {},
  onParamsChange,
}: CourseFilterProviderProps) {
  // 왜: 초기 상태는 기본값 + URL에서 파싱한 값을 합칩니다.
  const initialState = useMemo<CourseFilterState>(() => {
    const fromUrl = parseFiltersFromParams(initialParams);
    const initialTab = fromUrl.tab ?? DEFAULT_FILTER_STATE.tab;
    const initialYear = fromUrl.year ?? (initialTab === 'prism' ? PRISM_DEFAULT_YEAR : DEFAULT_FILTER_STATE.year);
    return { ...DEFAULT_FILTER_STATE, ...fromUrl, tab: initialTab, year: initialYear };
  }, []); // 의도적으로 빈 의존성: 마운트 시 한 번만 계산

  const [filters, dispatch] = useReducer(filterReducer, initialState);

  // 왜: URL 동기화를 위해 onParamsChange를 ref로 저장합니다 (의존성 배열에 안 넣기 위해).
  const onParamsChangeRef = useRef(onParamsChange);
  onParamsChangeRef.current = onParamsChange;

  // 왜: 상태가 바뀔 때마다 URL을 자동으로 업데이트합니다.
  const syncToUrl = useCallback(() => {
    const params = buildParamsFromFilters(filters);
    onParamsChangeRef.current?.(params);
  }, [filters]);

  const getUrlParams = useCallback(() => {
    return buildParamsFromFilters(filters);
  }, [filters]);

  // 왜: 개별 setter를 메모이제이션해서 자식 컴포넌트 리렌더를 방지합니다.
  const setTab = useCallback((tab: TabType) => dispatch({ type: 'SET_TAB', payload: tab }), []);
  const setYear = useCallback((year: string) => dispatch({ type: 'SET_YEAR', payload: year }), []);
  const setKeyword = useCallback((keyword: string) => dispatch({ type: 'SET_KEYWORD', payload: keyword }), []);
  const setPage = useCallback((page: number) => dispatch({ type: 'SET_PAGE', payload: page }), []);
  const setPageSize = useCallback((pageSize: number) => dispatch({ type: 'SET_PAGE_SIZE', payload: pageSize }), []);
  const setCourseType = useCallback((courseType: string) => dispatch({ type: 'SET_COURSE_TYPE', payload: courseType }), []);
  const setStatus = useCallback((status: string) => dispatch({ type: 'SET_STATUS', payload: status }), []);
  const setHaksaCategory = useCallback((category: string) => dispatch({ type: 'SET_HAKSA_CATEGORY', payload: category }), []);
  const setHaksaGrad = useCallback((grad: string) => dispatch({ type: 'SET_HAKSA_GRAD', payload: grad }), []);
  const setHaksaCurriculum = useCallback((curriculum: string) => dispatch({ type: 'SET_HAKSA_CURRICULUM', payload: curriculum }), []);
  const setSortOrder = useCallback((order: 'asc' | 'desc') => dispatch({ type: 'SET_SORT_ORDER', payload: order }), []);
  const resetFilters = useCallback(() => dispatch({ type: 'RESET_FILTERS' }), []);

  // 왜: 뒤로가기/앞으로가기 시 URL에서 상태를 복원합니다.
  const syncFromUrl = useCallback((params: Record<string, string>) => {
    const parsed = parseFiltersFromParams(params);
    const parsedTab = parsed.tab ?? DEFAULT_FILTER_STATE.tab;
    const parsedYear = parsed.year ?? (parsedTab === 'prism' ? PRISM_DEFAULT_YEAR : DEFAULT_FILTER_STATE.year);
    // 왜: 기본값으로 채워서 누락된 필드도 초기화되도록 합니다.
    const newState: Partial<CourseFilterState> = {
      tab: parsedTab,
      year: parsedYear,
      keyword: parsed.keyword ?? DEFAULT_FILTER_STATE.keyword,
      page: parsed.page ?? DEFAULT_FILTER_STATE.page,
      pageSize: parsed.pageSize ?? DEFAULT_FILTER_STATE.pageSize,
      courseType: parsed.courseType ?? DEFAULT_FILTER_STATE.courseType,
      status: parsed.status ?? DEFAULT_FILTER_STATE.status,
      haksaCategory: parsed.haksaCategory ?? DEFAULT_FILTER_STATE.haksaCategory,
      haksaGrad: parsed.haksaGrad ?? DEFAULT_FILTER_STATE.haksaGrad,
      haksaCurriculum: parsed.haksaCurriculum ?? DEFAULT_FILTER_STATE.haksaCurriculum,
      sortOrder: parsed.sortOrder ?? DEFAULT_FILTER_STATE.sortOrder,
    };
    dispatch({ type: 'SYNC_FROM_URL', payload: newState });
  }, []);

  // 왜: Context 값을 메모이제이션해서 불필요한 리렌더를 방지합니다.
  const value = useMemo<CourseFilterContextValue>(
    () => ({
      filters,
      setTab,
      setYear,
      setKeyword,
      setPage,
      setPageSize,
      setCourseType,
      setStatus,
      setHaksaCategory,
      setHaksaGrad,
      setHaksaCurriculum,
      setSortOrder,
      resetFilters,
      syncToUrl,
      syncFromUrl,
      getUrlParams,
    }),
    [
      filters,
      setTab,
      setYear,
      setKeyword,
      setPage,
      setPageSize,
      setCourseType,
      setStatus,
      setHaksaCategory,
      setHaksaGrad,
      setHaksaCurriculum,
      setSortOrder,
      resetFilters,
      syncToUrl,
      syncFromUrl,
      getUrlParams,
    ]
  );

  return (
    <CourseFilterContext.Provider value={value}>
      {children}
    </CourseFilterContext.Provider>
  );
}

// =====================
// 커스텀 훅
// =====================

// 왜: Context를 직접 쓰지 않고 훅으로 감싸면, Provider 없을 때 에러를 명확히 보여줍니다.
export function useCourseFilters(): CourseFilterContextValue {
  const context = useContext(CourseFilterContext);
  if (!context) {
    throw new Error('useCourseFilters는 CourseFilterProvider 안에서 사용해야 합니다.');
  }
  return context;
}

// 왜: 필터만 필요한 컴포넌트를 위한 간단한 훅 (setter 불필요할 때)
export function useCourseFilterState(): CourseFilterState {
  const { filters } = useCourseFilters();
  return filters;
}

// 왜: 디바운스된 검색어가 필요한 경우를 위한 훅
export function useDebouncedKeyword(delayMs = 300): string {
  const { filters } = useCourseFilters();
  const [debouncedKeyword, setDebouncedKeyword] = React.useState(filters.keyword);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(filters.keyword);
    }, delayMs);
    return () => clearTimeout(timer);
  }, [filters.keyword, delayMs]);

  return debouncedKeyword;
}
