import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Search, Users, Settings, ArrowUpDown, ArrowUp, ArrowDown } from 'lucide-react';
import { CourseManagement, type CourseManagementTabId } from './CourseManagement';
import { tutorLmsApi } from '../api/tutorLmsApi';
import {
  CourseFilterProvider,
  useCourseFilters,
  buildParamsFromFilters,
  parseFiltersFromParams,
  type TabType,
  type CourseFilterState,
} from './CourseFilterContext';

// ============================================================================
// 왜 리팩토링했나:
// - 기존 코드는 useState 15개+, useEffect 10개+ 가 서로 충돌하면서 깜빡임이 발생
// - 이제 필터 상태는 CourseFilterContext에서 중앙 관리
// - URL → Context → 컴포넌트 "단방향" 흐름으로 깜빡임 해결
// ============================================================================

interface Course {
  id: string;
  mappedCourseId?: number;
  sourceType: 'haksa' | 'prism';
  courseId: string;
  courseType: string;
  subjectName: string;
  programId: number;
  programName: string;
  period: string;
  students: number;
  status: '대기' | '신청기간' | '학습기간' | '종료' | '-';
  // ===== 학사 View 25개 필드 =====
  haksaCategory?: string;
  haksaDeptName?: string;
  haksaWeek?: string;
  haksaOpenTerm?: string;
  haksaCourseCode?: string;
  haksaVisible?: string;
  haksaStartdate?: string;
  haksaBunbanCode?: string;
  haksaGrade?: string;
  haksaGradName?: string;
  haksaDayCd?: string;
  haksaClassroom?: string;
  haksaCurriculumCode?: string;
  haksaCourseEname?: string;
  haksaTypeSyllabus?: string;
  haksaOpenYear?: string;
  haksaDeptCode?: string;
  haksaCourseName?: string;
  haksaGroupCode?: string;
  haksaEnddate?: string;
  haksaEnglish?: string;
  haksaHour1?: string;
  haksaCurriculumName?: string;
  haksaGradCode?: string;
  haksaIsSyllabus?: string;
}

interface MyCoursesListProps {
  routeSubPath?: string;
  routeParams?: Record<string, string>;
  onRouteChange?: (next: { subPath?: string; params: Record<string, string> }) => void;
}

const COURSE_MANAGEMENT_TAB_IDS: CourseManagementTabId[] = [
  'info', 'info-basic', 'info-evaluation', 'info-completion', 'curriculum',
  'students', 'attendance', 'exam', 'assignment', 'assignment-management',
  'assignment-feedback', 'materials', 'qna', 'grades', 'completion',
];
const COURSE_MANAGEMENT_TAB_SET = new Set<string>(COURSE_MANAGEMENT_TAB_IDS);

// 필터 옵션
const GRAD_OPTIONS = [
  '전체',
  '서울정수', '서울강서', '성남', '분당융합', '제주',
  '인천', '남인천', '화성', '광명융합',
  '춘천', '원주', '강릉',
  '대전', '청주', '아산', '충남', '충주',
  '광주', '전북', '전남', '익산', '순천',
  '대구', '구미', '남대구', '포항', '영주', '영남융합기술',
  '창원', '부산', '울산', '동부산', '진주', '석유화학공정',
  '반도체융합', '바이오', '로봇', '항공', '신기술교육원',
];
const CURRICULUM_OPTIONS = ['전체', '전공필수', '전공교과', '전공선택', '교양선택', '교양교과', '교양필수'];
const CATEGORY_OPTIONS = ['전체', 'off', 'elearning'];

// 유틸 함수
const isSameParams = (a: Record<string, string> = {}, b: Record<string, string> = {}) => {
  const aKeys = Object.keys(a);
  const bKeys = Object.keys(b);
  if (aKeys.length !== bKeys.length) return false;
  for (const key of aKeys) {
    if (a[key] !== b[key]) return false;
  }
  return true;
};

const normalizeText = (value?: string) => (value || '').toLowerCase().replace(/\s+/g, ' ').trim();

const buildHaksaCourseId = (row: any) => {
  const courseCode = String(row?.haksa_course_code ?? '').trim();
  const openYear = String(row?.haksa_open_year ?? '').trim();
  const openTerm = String(row?.haksa_open_term ?? '').trim();
  const bunbanCode = String(row?.haksa_bunban_code ?? '').trim();
  const groupCode = String(row?.haksa_group_code ?? '').trim();

  const parts = [courseCode, openYear, openTerm, bunbanCode, groupCode].filter(Boolean);
  if (parts.length === 5) return `H_${parts.join('_')}`;

  const fallback = String(row?.course_id_conv ?? row?.course_cd ?? row?.id ?? '').trim();
  return `H_${courseCode || 'UNKNOWN'}_${bunbanCode || '0'}_${fallback || '0'}`;
};

const getStatusColor = (status: string) => {
  switch (status) {
    case '대기': return 'bg-gray-100 text-gray-700';
    case '신청기간': return 'bg-blue-100 text-blue-700';
    case '학습기간': return 'bg-green-100 text-green-700';
    case '종료': return 'bg-gray-200 text-gray-600';
    default: return 'bg-gray-100 text-gray-700';
  }
};

// ============================================================================
// 내부 콘텐츠 컴포넌트 (Context 사용)
// ============================================================================

interface MyCoursesListContentProps {
  routeSubPath?: string;
  routeParams?: Record<string, string>;
  onRouteChange?: (next: { subPath?: string; params: Record<string, string> }) => void;
}

function MyCoursesListContent({ routeSubPath, routeParams, onRouteChange }: MyCoursesListContentProps) {
  // Context에서 필터 상태 가져오기
  const {
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
    syncFromUrl,
    getUrlParams,
  } = useCourseFilters();

  // 로컬 상태 (Context로 관리하지 않는 것들)
  const [yearOptions, setYearOptions] = useState<string[]>(['전체', String(new Date().getFullYear())]);
  const [searchTerm, setSearchTerm] = useState(filters.keyword);
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [totalCount, setTotalCount] = useState(0);
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null);
  const [selectedCourseTab, setSelectedCourseTab] = useState<CourseManagementTabId | null>(null);
  const [resolvingCourseId, setResolvingCourseId] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [sortColumn, setSortColumn] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  const handleSort = useCallback((column: string) => {
    if (sortColumn === column) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortColumn(column);
      setSortDirection('asc');
    }
  }, [sortColumn]);

  const SortIcon = useCallback(({ column }: { column: string }) => {
    if (sortColumn !== column) return <ArrowUpDown className="inline w-3.5 h-3.5 ml-1 text-gray-400" />;
    return sortDirection === 'asc'
      ? <ArrowUp className="inline w-3.5 h-3.5 ml-1 text-blue-600" />
      : <ArrowDown className="inline w-3.5 h-3.5 ml-1 text-blue-600" />;
  }, [sortColumn, sortDirection]);

  // URL 동기화용 ref
  const routeRef = useRef<{ subPath?: string; params: Record<string, string> }>({
    subPath: routeSubPath,
    params: routeParams ?? {},
  });
  
  // 왜: 뒤로가기/앞으로가기 시 URL에서 상태를 복원합니다.
  const prevRouteParamsRef = useRef<Record<string, string> | undefined>(routeParams);
  useEffect(() => {
    // 목록 화면일 때만 동기화 (상세 화면에서는 건드리지 않음)
    if (routeSubPath === 'manage') {
      prevRouteParamsRef.current = routeParams;
      return;
    }
    
    // params가 실제로 변경됐을 때만 동기화 (첫 마운트 제외)
    const prev = prevRouteParamsRef.current;
    const curr = routeParams ?? {};
    prevRouteParamsRef.current = curr;
    
    // 첫 마운트가 아니고 params가 바뀌었으면 동기화
    if (prev !== undefined && !isSameParams(prev, curr)) {
      syncFromUrl(curr);
      // 검색어 입력 필드도 동기화
      const newKeyword = curr.keyword ?? '';
      if (newKeyword !== searchTerm) {
        setSearchTerm(newKeyword);
      }
    }
  }, [routeParams, routeSubPath, syncFromUrl, searchTerm]);

  routeRef.current = { subPath: routeSubPath, params: routeParams ?? {} };

  // 왜: URL 변경을 위한 단일 함수
  const pushRoute = useCallback((next: { subPath?: string; params: Record<string, string> }) => {
    if (!onRouteChange) return;
    const current = routeRef.current;
    if (current.subPath === next.subPath && isSameParams(current.params, next.params)) return;
    onRouteChange(next);
  }, [onRouteChange]);

  // 왜: 행 데이터를 Course 객체로 변환
  const mapRowToCourse = useCallback((row: any, fallbackSource: TabType): Course => {
    const sourceType = row?.source_type === 'haksa' || row?.source_type === 'prism' ? row.source_type : fallbackSource;
    const statusLabel = (row.status_label as Course['status']) ?? '대기';
    const typeParts = [
      (row.course_type_conv || '').trim(),
      (row.onoff_type_conv || '').trim(),
    ].filter(Boolean);
    const isHaksa = sourceType === 'haksa';
    const idValue = isHaksa ? (row.id ? String(row.id) : buildHaksaCourseId(row)) : String(row.id);
    const mappedCourseId = row.mapped_course_id ? Number(row.mapped_course_id) : undefined;

    return {
      id: idValue,
      mappedCourseId: mappedCourseId && Number.isFinite(mappedCourseId) ? mappedCourseId : undefined,
      sourceType,
      courseId: row.course_id_conv || row.course_cd || String(row.id),
      courseType: typeParts.length > 0 ? typeParts.join(' / ') : '미지정',
      subjectName: row.course_nm_conv || row.subject_nm_conv || row.course_nm || '-',
      programId: Number(row.program_id ?? 0),
      programName: row.program_nm_conv || '-',
      period: row.period_conv || '-',
      students: Number(row.student_cnt ?? 0),
      status: statusLabel,
      haksaCategory: row.haksa_category || '',
      haksaDeptName: row.haksa_dept_name || '',
      haksaWeek: row.haksa_week || '',
      haksaOpenTerm: row.haksa_open_term || '',
      haksaCourseCode: row.haksa_course_code || '',
      haksaVisible: row.haksa_visible || '',
      haksaStartdate: row.haksa_startdate || '',
      haksaBunbanCode: row.haksa_bunban_code || '',
      haksaGrade: row.haksa_grade || '',
      haksaGradName: row.haksa_grad_name || '',
      haksaDayCd: row.haksa_day_cd || '',
      haksaClassroom: row.haksa_classroom || '',
      haksaCurriculumCode: row.haksa_curriculum_code || '',
      haksaCourseEname: row.haksa_course_ename || '',
      haksaTypeSyllabus: row.haksa_type_syllabus || '',
      haksaOpenYear: row.haksa_open_year || '',
      haksaDeptCode: row.haksa_dept_code || '',
      haksaCourseName: row.haksa_course_name || '',
      haksaGroupCode: row.haksa_group_code || '',
      haksaEnddate: row.haksa_enddate || '',
      haksaEnglish: row.haksa_english || '',
      haksaHour1: row.haksa_hour1 || '',
      haksaCurriculumName: row.haksa_curriculum_name || '',
      haksaGradCode: row.haksa_grad_code || '',
      haksaIsSyllabus: row.haksa_is_syllabus || '',
    };
  }, []);

  // ============================================================================
  // Effect 1: 연도 옵션 조회 (마운트 시 1회)
  // ============================================================================
  useEffect(() => {
    let cancelled = false;
    const currentYear = String(new Date().getFullYear());
    const prismDefaultYear = '2024';

    const fetchYears = async () => {
      try {
        const res = await tutorLmsApi.getCourseYears();
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const years = (res.rst_data ?? [])
          .map((r) => String(r.year || '').trim())
          .filter(Boolean);

        // 왜: 비정규 탭 기본값(2024)이 목록에 없으면 select 표시가 깨질 수 있어 옵션에는 항상 포함합니다.
        const uniq = Array.from(new Set([...years, prismDefaultYear])).sort((a, b) => b.localeCompare(a));
        const options = ['전체', ...uniq];

        if (cancelled) return;
        setYearOptions(options.length > 1 ? options : ['전체', currentYear]);
      } catch {
        if (!cancelled) {
          const fallbackYears = Array.from(new Set([currentYear, prismDefaultYear])).sort((a, b) => b.localeCompare(a));
          setYearOptions(['전체', ...fallbackYears]);
        }
      }
    };

    void fetchYears();
    return () => { cancelled = true; };
  }, []);

  // ============================================================================
  // Effect 2: 필터 변경 시 과목 목록 조회 (핵심 API 호출)
  // ============================================================================
  useEffect(() => {
    let cancelled = false;

    const fetchCourses = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getMyCoursesCombined({
          tab: filters.tab,
          year: filters.year === '전체' ? undefined : filters.year,
          keyword: filters.keyword || undefined,
          page: filters.page,
          pageSize: filters.pageSize,
          haksaCategory: filters.tab === 'haksa' && filters.haksaCategory !== '전체' ? filters.haksaCategory : undefined,
          haksaGrad: filters.tab === 'haksa' && filters.haksaGrad !== '전체' ? filters.haksaGrad : undefined,
          haksaCurriculum: filters.tab === 'haksa' && filters.haksaCurriculum !== '전체' ? filters.haksaCurriculum : undefined,
          sortOrder: filters.tab === 'haksa' ? filters.sortOrder : undefined,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const rows = res.rst_data ?? [];
        const serverTotal = Number(res.rst_total_count ?? rows.length);
        const mapped: Course[] = rows.map((row) => mapRowToCourse(row, filters.tab));

        if (!cancelled) {
          setCourses(mapped);
          setTotalCount(Number.isNaN(serverTotal) ? rows.length : serverTotal);
        }
      } catch (e) {
        if (!cancelled) setErrorMessage(e instanceof Error ? e.message : '조회 중 오류가 발생했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void fetchCourses();
    return () => { cancelled = true; };
  }, [
    filters.tab,
    filters.year,
    filters.keyword,
    filters.page,
    filters.pageSize,
    filters.haksaCategory,
    filters.haksaGrad,
    filters.haksaCurriculum,
    filters.sortOrder,
    mapRowToCourse,
  ]);

  // ============================================================================
  // Effect 3: 검색어 디바운스 (300ms)
  // ============================================================================
  useEffect(() => {
    const timer = setTimeout(() => {
      if (searchTerm.trim() !== filters.keyword) {
        setKeyword(searchTerm.trim());
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchTerm, filters.keyword, setKeyword]);

  // ============================================================================
  // Effect 4: 목록 화면일 때 URL 동기화
  // ============================================================================
  useEffect(() => {
    if (!onRouteChange) return;
    if (routeSubPath === 'manage') return;
    if (selectedCourse) return;
    pushRoute({ subPath: undefined, params: getUrlParams() });
  }, [getUrlParams, onRouteChange, pushRoute, routeSubPath, selectedCourse]);

  // ============================================================================
  // Effect 5: 뒤로가기 시 상세 화면 닫기
  // ============================================================================
  useEffect(() => {
    if (routeSubPath === 'manage') return;
    // 왜: selectedCourse를 의존성에서 제거해야 합니다.
    // - 이 Effect의 목적은 "routeSubPath가 'manage'가 아닐 때 상세 화면 닫기" 입니다.
    // - selectedCourse가 의존성에 있으면, 버튼 클릭 시 selectedCourse가 설정되는 순간
    //   아직 routeSubPath가 'manage'로 변경되기 전에 Effect가 실행되어
    //   selectedCourse가 즉시 null로 리셋됩니다 (경쟁 상태).
    setSelectedCourse(null);
    setSelectedCourseTab(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeSubPath]);

  // ============================================================================
  // Effect: 과목 선택/해제 시 스크롤을 맨 위로 리셋
  // ============================================================================
  useEffect(() => {
    // 왜: 목록에서 스크롤을 내린 뒤 과목을 선택하면, 상세 화면이 스크롤 중간부터 보이는 문제를 방지합니다.
    const el = containerRef.current;
    if (!el) return;
    // 가장 가까운 스크롤 가능한 부모(main)를 찾아 스크롤 리셋
    const scrollParent = el.closest('main') ?? el.parentElement;
    if (scrollParent) scrollParent.scrollTop = 0;
  }, [selectedCourse]);

  // ============================================================================
  // Effect 6: 직접 링크(direct=1) 처리
  // ============================================================================
  const getRouteParam = useCallback((keys: string[]) => {
    for (const key of keys) {
      const value = routeParams?.[key];
      if (value) return value;
    }
    return '';
  }, [routeParams]);

  const routeCourseId = getRouteParam(['courseId', 'course_id', 'id']);
  const routeDirectParam = getRouteParam(['direct', 'directOpen', 'direct_open']);
  const isDirectOpen = routeSubPath === 'manage' && routeDirectParam === '1';
  const rawTabParam = getRouteParam(['cmTab', 'cm_tab', 'tab', 'targetTab', 'tab_id']);
  const routeTargetTab = COURSE_MANAGEMENT_TAB_SET.has(rawTabParam) ? (rawTabParam as CourseManagementTabId) : undefined;
  const rawSourceParam = getRouteParam(['source', 'source_type', 'sourceType', 'listTab']);
  const routeSourceType = rawSourceParam === 'haksa' || rawSourceParam === 'prism' ? rawSourceParam : undefined;
  const routeQnaPostIdRaw = getRouteParam(['qnaPostId', 'qna_post_id', 'postId', 'post_id']);
  const routeQnaPostId = (() => {
    const parsed = Number(routeQnaPostIdRaw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
  })();

  useEffect(() => {
    if (!isDirectOpen || !routeCourseId) return;
    let cancelled = false;

    const fetchDirectCourse = async () => {
      try {
        const courseIdNum = Number(routeCourseId);
        if (!Number.isFinite(courseIdNum) || courseIdNum <= 0) {
          throw new Error('과목 ID가 올바르지 않습니다.');
        }
        const res = await tutorLmsApi.getCourseResolve({
          courseId: courseIdNum,
          sourceType: routeSourceType,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        if (!payload) throw new Error('과목 정보를 찾지 못했습니다.');

        const mapped = mapRowToCourse(payload, (routeSourceType ?? 'prism') as TabType);
        if (!cancelled) {
          setSelectedCourseTab(routeTargetTab ?? null);
          setSelectedCourse(mapped);
        }
      } catch (e) {
        if (!cancelled) {
          setErrorMessage(e instanceof Error ? e.message : '과목 조회 중 오류가 발생했습니다.');
        }
      }
    };

    void fetchDirectCourse();
    return () => { cancelled = true; };
  }, [isDirectOpen, routeCourseId, routeSourceType, routeTargetTab, mapRowToCourse]);

  // 과목 선택 핸들러
  const handleSelectCourse = useCallback(async (course: Course, targetTab?: CourseManagementTabId) => {
    setSelectedCourseTab(targetTab ?? null);
    if (course.sourceType !== 'haksa') {
      setSelectedCourse(course);
      return;
    }

    if (course.mappedCourseId && course.mappedCourseId > 0) {
      setSelectedCourse(course);
      return;
    }

    if (!course.haksaCourseCode || !course.haksaOpenYear || !course.haksaOpenTerm || !course.haksaBunbanCode || !course.haksaGroupCode) {
      alert('학사 과목 키가 비어 있어 과정 매핑을 진행할 수 없습니다.');
      return;
    }

    setResolvingCourseId(course.id);
    try {
      const res = await tutorLmsApi.resolveHaksaCourse({
        courseCode: course.haksaCourseCode,
        openYear: course.haksaOpenYear,
        openTerm: course.haksaOpenTerm,
        bunbanCode: course.haksaBunbanCode,
        groupCode: course.haksaGroupCode,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
      const mapped = Number(payload?.mapped_course_id ?? 0);
      if (!mapped || Number.isNaN(mapped)) throw new Error('매핑된 과정ID를 찾지 못했습니다.');

      setSelectedCourse({ ...course, mappedCourseId: mapped });
    } catch (e) {
      alert(e instanceof Error ? e.message : '학사 과목 매핑 중 오류가 발생했습니다.');
    } finally {
      setResolvingCourseId(null);
    }
  }, []);

  // 과목 선택 시 URL 동기화
  useEffect(() => {
    if (!onRouteChange || !selectedCourse) return;
    const nextTab = selectedCourseTab ?? routeTargetTab ?? 'info-basic';
    const courseNameParam = selectedCourse.subjectName || selectedCourse.courseId;
    pushRoute({
      subPath: 'manage',
      params: {
        courseId: selectedCourse.id,
        source: selectedCourse.sourceType,
        cmTab: nextTab,
        courseName: courseNameParam,
      },
    });
  }, [onRouteChange, pushRoute, routeTargetTab, selectedCourse, selectedCourseTab]);

  // 클라이언트 필터링 (서버 필터링 후 추가 적용)
  const filteredCourses = useMemo(() => {
    if (filters.tab === 'haksa') {
      return courses.filter((course) => {
        const matchesCategory = filters.haksaCategory === '전체' || 
          (course.haksaCategory || '').toLowerCase() === filters.haksaCategory.toLowerCase();
        const matchesGrad = filters.haksaGrad === '전체' || 
          (course.haksaGradName || '').includes(filters.haksaGrad);
        const matchesCurriculum = filters.haksaCurriculum === '전체' || 
          (course.haksaCurriculumName || '') === filters.haksaCurriculum;
        return matchesCategory && matchesGrad && matchesCurriculum;
      });
    }

    return courses.filter((course) => {
      const matchesCourseType = filters.courseType === '전체' || course.courseType === filters.courseType;
      const matchesStatus = filters.status === '전체' || course.status === filters.status;
      return matchesCourseType && matchesStatus;
    });
  }, [filters, courses]);

  // 클라이언트 정렬
  const sortedCourses = useMemo(() => {
    if (!sortColumn) return filteredCourses;
    return [...filteredCourses].sort((a, b) => {
      let aVal = '';
      let bVal = '';
      switch (sortColumn) {
        case 'courseId': aVal = a.courseId; bVal = b.courseId; break;
        case 'courseType':
          aVal = a.sourceType === 'haksa' ? (a.haksaCategory || '') : a.courseType;
          bVal = b.sourceType === 'haksa' ? (b.haksaCategory || '') : b.courseType;
          break;
        case 'subjectName': aVal = a.subjectName; bVal = b.subjectName; break;
        case 'programName':
          aVal = a.sourceType === 'haksa' ? (a.haksaDeptName || '') : a.programName;
          bVal = b.sourceType === 'haksa' ? (b.haksaDeptName || '') : b.programName;
          break;
        case 'period': aVal = a.period; bVal = b.period; break;
        case 'students': return sortDirection === 'asc' ? a.students - b.students : b.students - a.students;
        case 'status': aVal = a.status; bVal = b.status; break;
        default: return 0;
      }
      const cmp = aVal.localeCompare(bVal, 'ko');
      return sortDirection === 'asc' ? cmp : -cmp;
    });
  }, [filteredCourses, sortColumn, sortDirection]);

  // 과정 유형 옵션 (동적 생성)
  const courseTypeOptions = useMemo(() => {
    const types = Array.from(new Set(courses.map((c) => c.courseType).filter((t) => t && t !== '미지정'))).sort((a, b) =>
      a.localeCompare(b)
    );
    return ['전체', ...types];
  }, [courses]);

  // 페이지네이션
  const totalPages = Math.max(1, Math.ceil(totalCount / filters.pageSize));
  const pageNumbers = useMemo(() => {
    const start = Math.max(1, filters.page - 2);
    const end = Math.min(totalPages, filters.page + 2);
    return Array.from({ length: end - start + 1 }, (_, idx) => start + idx);
  }, [filters.page, totalPages]);

  // 페이지 조정 (총 페이지 초과 방지)
  useEffect(() => {
    if (filters.page > totalPages && totalPages > 0) {
      setPage(totalPages);
    }
  }, [totalPages, filters.page, setPage]);

  // ============================================================================
  // 렌더링: 선택된 과목이 있으면 관리 페이지 표시
  // ============================================================================
  if (selectedCourse) {
    return (
      <div ref={containerRef}>
        <CourseManagement
          course={selectedCourse}
          initialTab={selectedCourseTab ?? routeTargetTab ?? undefined}
          initialQnaPostId={routeSubPath === 'manage' ? routeQnaPostId : undefined}
          onTabChange={(tabId) => setSelectedCourseTab(tabId)}
          onBack={() => {
            setSelectedCourse(null);
            setSelectedCourseTab(null);
            pushRoute({ subPath: undefined, params: getUrlParams() });
          }}
        />
      </div>
    );
  }

  // ============================================================================
  // 렌더링: 목록 화면
  // ============================================================================
  return (
    <div ref={containerRef} className="max-w-7xl mx-auto">
      <div className="mb-8">
        <h2 className="text-gray-900 mb-2">담당 과목</h2>
        <p className="text-gray-600">담당하고 있는 과목 목록을 확인하고 관리합니다.</p>
      </div>

      {/* 탭 영역 */}
      <div className="flex gap-2 mb-4">
        <button
          onClick={() => setTab('haksa')}
          className={`px-6 py-2.5 rounded-lg font-medium transition-all ${
            filters.tab === 'haksa'
              ? 'bg-blue-600 text-white shadow-md'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          정규
        </button>
        <button
          onClick={() => setTab('prism')}
          className={`px-6 py-2.5 rounded-lg font-medium transition-all ${
            filters.tab === 'prism'
              ? 'bg-blue-600 text-white shadow-md'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          비정규
        </button>
      </div>

      {/* 필터 영역 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        {filters.tab === 'haksa' ? (
          <div className="grid grid-cols-4 gap-4">
            <div>
              <label className="block text-sm text-gray-700 mb-2">년도</label>
              <select
                value={filters.year}
                onChange={(e) => setYear(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {yearOptions.map((y) => (
                  <option key={y} value={y}>{y}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">유형</label>
              <select
                value={filters.haksaCategory}
                onChange={(e) => setHaksaCategory(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {CATEGORY_OPTIONS.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">단과대학</label>
              <select
                value={filters.haksaGrad}
                onChange={(e) => setHaksaGrad(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {GRAD_OPTIONS.map((g) => (
                  <option key={g} value={g}>{g}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">과목구분</label>
              <select
                value={filters.haksaCurriculum}
                onChange={(e) => setHaksaCurriculum(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {CURRICULUM_OPTIONS.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm text-gray-700 mb-2">년도</label>
              <select
                value={filters.year}
                onChange={(e) => setYear(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {yearOptions.map((y) => (
                  <option key={y} value={y}>{y}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">유형</label>
              <select
                value={filters.courseType}
                onChange={(e) => setCourseType(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {courseTypeOptions.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-gray-700 mb-2">상태</label>
              <select
                value={filters.status}
                onChange={(e) => setStatus(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="전체">전체</option>
                <option value="대기">대기</option>
                <option value="신청기간">신청기간</option>
                <option value="학습기간">학습기간</option>
                <option value="종료">종료</option>
              </select>
            </div>
          </div>
        )}
        {/* 검색 (별도 줄) */}
        <div className="mt-4 relative max-w-md">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="과목명, 과정명, 과정ID 검색"
            className="w-full px-4 py-2 pl-10 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        </div>
      </div>

      {/* 과목 목록 테이블 */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px]">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm text-gray-700 whitespace-nowrap">No</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700 whitespace-nowrap">요청</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700 whitespace-nowrap w-20 cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('courseId')}>과정ID<SortIcon column="courseId" /></th>
                <th className="px-4 py-3 text-left text-sm text-gray-700 whitespace-nowrap cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('courseType')}>유형<SortIcon column="courseType" /></th>
                <th className="px-4 py-3 text-left text-sm text-gray-700 whitespace-nowrap cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('subjectName')}>과목명<SortIcon column="subjectName" /></th>
                <th className="px-4 py-3 text-left text-sm text-gray-700 whitespace-nowrap cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('programName')}>
                  {filters.tab === 'haksa' ? '학과/전공' : '소속 과정명'}<SortIcon column="programName" />
                </th>
                <th className="px-4 py-3 text-left text-sm text-gray-700 whitespace-nowrap cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('period')}>기간<SortIcon column="period" /></th>
                <th className="px-4 py-3 text-center text-sm text-gray-700 whitespace-nowrap cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('students')}>수강생<SortIcon column="students" /></th>
                <th className="px-4 py-3 text-center text-sm text-gray-700 whitespace-nowrap cursor-pointer select-none hover:text-blue-600" onClick={() => handleSort('status')}>상태<SortIcon column="status" /></th>
                <th className="px-4 py-3 text-center text-sm text-gray-700 whitespace-nowrap">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {errorMessage ? (
                <tr>
                  <td colSpan={10} className="px-4 py-12 text-center text-gray-500">
                    <p>{errorMessage}</p>
                  </td>
                </tr>
              ) : loading ? (
                <tr>
                  <td colSpan={10} className="px-4 py-12 text-center text-gray-500">
                    <p>불러오는 중...</p>
                  </td>
                </tr>
              ) : sortedCourses.length > 0 ? (
                sortedCourses.map((course, index) => (
                  <tr key={course.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-4 text-sm text-gray-900 whitespace-nowrap">{(filters.page - 1) * filters.pageSize + index + 1}</td>
                    <td className="px-4 py-4 text-center whitespace-nowrap">
                      <span className={`inline-flex px-2 py-0.5 text-xs rounded-full ${
                        course.sourceType === 'prism' ? 'bg-blue-100 text-blue-700' : 'bg-amber-100 text-amber-700'
                      }`}>
                        {course.sourceType === 'prism' ? 'LMS' : '학사'}
                      </span>
                    </td>
                    <td className="px-4 py-4 text-sm text-gray-900 whitespace-nowrap">{course.courseId}</td>
                    <td className="px-4 py-4 text-sm text-gray-600 whitespace-nowrap">
                      {course.sourceType === 'haksa' && course.haksaCategory 
                        ? course.haksaCategory 
                        : course.courseType}
                    </td>
                    <td className="px-4 py-4 text-sm text-gray-900">{course.subjectName}</td>
                    <td className="px-4 py-4 text-sm text-gray-600">
                      {course.sourceType === 'haksa' && course.haksaDeptName
                        ? course.haksaDeptName
                        : course.programName}
                    </td>
                    <td className="px-4 py-4 text-sm text-gray-600 whitespace-nowrap">{course.period}</td>
                    <td className="px-4 py-4 text-center whitespace-nowrap">
                      <div className="flex items-center justify-center gap-1 text-sm text-gray-900">
                        <Users className="w-4 h-4 text-gray-500" />
                        <span>{course.students}</span>
                      </div>
                    </td>
                    <td className="px-4 py-4 text-center whitespace-nowrap">
                      <span className={`inline-flex px-3 py-1 text-xs rounded-full ${getStatusColor(course.status)}`}>
                        {course.status}
                      </span>
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      <div className="flex items-center justify-center">
                        <button
                          className="flex items-center gap-1 px-4 py-1.5 text-xs text-blue-700 bg-blue-50 rounded hover:bg-blue-100 transition-colors disabled:opacity-60"
                          title="과목 관리"
                          onClick={() => void handleSelectCourse(course)}
                          disabled={resolvingCourseId === course.id}
                        >
                          <Settings className="w-4 h-4" />
                          <span>{resolvingCourseId === course.id ? '연동 중...' : '관리'}</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={10} className="px-4 py-12 text-center text-gray-500">
                    <p>검색 결과가 없습니다.</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 페이지/카운트 */}
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-gray-600">
        <div className="flex items-center gap-2">
          <span>총</span>
          <span className="text-blue-600">{totalCount}</span>
          <span>개의 과목</span>
          <span className="text-gray-400">/ 현재 {sortedCourses.length}개 표시</span>
        </div>
        <div className="flex items-center gap-2">
          <label htmlFor="page-size" className="text-gray-600">페이지당</label>
          <select
            id="page-size"
            value={filters.pageSize}
            onChange={(e) => setPageSize(Number(e.target.value))}
            className="px-2 py-1 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {[20, 50, 100].map((size) => (
              <option key={size} value={size}>{size}</option>
            ))}
          </select>
        </div>
      </div>

      {/* 페이지네이션 */}
      <div className="mt-4 flex items-center justify-center gap-2">
        <button
          type="button"
          onClick={() => setPage(1)}
          disabled={filters.page === 1}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        >
          처음
        </button>
        <button
          type="button"
          onClick={() => setPage(Math.max(1, filters.page - 1))}
          disabled={filters.page === 1}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        >
          이전
        </button>
        {pageNumbers.map((num) => (
          <button
            key={num}
            type="button"
            onClick={() => setPage(num)}
            className={`px-3 py-1.5 text-sm border rounded-md ${
              num === filters.page ? 'bg-blue-600 text-white border-blue-600' : 'border-gray-300'
            }`}
          >
            {num}
          </button>
        ))}
        <button
          type="button"
          onClick={() => setPage(Math.min(totalPages, filters.page + 1))}
          disabled={filters.page === totalPages}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        >
          다음
        </button>
        <button
          type="button"
          onClick={() => setPage(totalPages)}
          disabled={filters.page === totalPages}
          className="px-3 py-1.5 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        >
          끝
        </button>
      </div>
    </div>
  );
}

// ============================================================================
// 외부 export 컴포넌트: Provider로 감싸서 내보냄
// ============================================================================

export function MyCoursesList({ routeSubPath, routeParams, onRouteChange }: MyCoursesListProps) {
  // 왜: URL에서 필터 초기값을 파싱해서 Context에 전달
  const initialParams = useMemo(() => routeParams ?? {}, []);

  // 왜: Context 내부에서 필터가 바뀌면 URL을 업데이트
  const handleParamsChange = useCallback((params: Record<string, string>) => {
    if (!onRouteChange) return;
    if (routeSubPath === 'manage') return; // 상세 화면에서는 건드리지 않음
    onRouteChange({ subPath: undefined, params });
  }, [onRouteChange, routeSubPath]);

  return (
    <CourseFilterProvider
      initialParams={initialParams}
      onParamsChange={handleParamsChange}
    >
      <MyCoursesListContent
        routeSubPath={routeSubPath}
        routeParams={routeParams}
        onRouteChange={onRouteChange}
      />
    </CourseFilterProvider>
  );
}
