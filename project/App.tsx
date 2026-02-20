import { useCallback, useEffect, useState } from 'react';
import { GraduationCap, BookOpen, FolderPlus, Compass, Library, ChevronDown, ChevronRight, Heart, RefreshCw, ClipboardList, BookPlus, BarChart3, ClipboardCheck, MessageSquare, UserCheck, Video } from 'lucide-react';
import { CreateCourseForm } from './components/CreateCourseForm';
import { MyCoursesList } from './components/MyCoursesList';
import { CourseExplorer } from './components/CourseExplorer';
import { Dashboard } from './components/Dashboard';
import { ContentLibraryPage } from './components/ContentLibraryPage';
import { QuestionCategoryPage } from './components/QuestionCategoryPage';
import { QuestionBankPage } from './components/QuestionBankPage';
import { ExamManagementPage } from './components/ExamManagementPage';
import { CreateSubjectWizard } from './components/CreateSubjectWizard';
import { StatisticsPage } from './components/StatisticsPage';
import { AssignmentManagePage } from './components/AssignmentManagePage';
import { AssignmentTemplateTab } from './components/AssignmentTemplateTab';
import { FeedbackTemplateTab } from './components/FeedbackTemplateTab';
import { QnaManagePage } from './components/QnaManagePage';
import { FaqManagePage } from './components/FaqManagePage';
import { AttendanceManagePage } from './components/AttendanceManagePage';
import { VideoGroupManagePage } from './components/VideoGroupManagePage';
import type { CourseManagementTabId } from './components/CourseManagement';

const MENU_IDS = [
  'dashboard',
  'explore',
  'courses',
  'assignment-manage',
  'assignment-submissions',
  'assignment-templates',
  'feedback-templates',
  'qna-manage',
  'faq-manage',
  'create-course',
  'content-all',
  'content-favorites',
  'exam-categories',
  'exam-questions',
  'exam-management',
  'attendance-manage',
  'video-group-manage',
  'subject-create',
  'statistics',
] as const;

type MenuId = (typeof MENU_IDS)[number];

const MENU_ID_SET = new Set<string>(MENU_IDS);
const CREATE_COURSE_STEP_IDS = ['basic', 'subjects'] as const;
const CREATE_COURSE_STEP_SET = new Set<string>(CREATE_COURSE_STEP_IDS);
const SUBJECT_STEP_IDS = ['basic', 'learners', 'curriculum', 'confirm'] as const;
const SUBJECT_STEP_SET = new Set<string>(SUBJECT_STEP_IDS);

type RouteState = {
  menu: MenuId;
  subPath?: string;
  params: Record<string, string>;
};

type ParsedRoute = RouteState & {
  isFallback: boolean;
};

const isSameParams = (a: Record<string, string>, b: Record<string, string>) => {
  const aKeys = Object.keys(a);
  const bKeys = Object.keys(b);
  if (aKeys.length !== bKeys.length) return false;
  for (const key of aKeys) {
    if (a[key] !== b[key]) return false;
  }
  return true;
};

const isSameRoute = (a: RouteState, b: RouteState) => {
  return a.menu === b.menu && a.subPath === b.subPath && isSameParams(a.params, b.params);
};

const parseRouteFromHash = (hash: string): ParsedRoute => {
  // 왜: 해시 주소를 메뉴/서브경로/쿼리로 분해해 화면 상태에 맞춥니다.
  const normalized = hash.replace(/^#\/?/, '').trim();
  if (!normalized) {
    return { menu: 'dashboard', params: {}, isFallback: false };
  }

  const [pathPart, queryPart] = normalized.split('?');
  const segments = pathPart.split('/').filter(Boolean);
  const menuId = segments[0] ?? '';
  const isValidMenu = MENU_ID_SET.has(menuId);
  const params: Record<string, string> = {};

  if (queryPart) {
    const searchParams = new URLSearchParams(queryPart);
    searchParams.forEach((value, key) => {
      if (value) params[key] = value;
    });
  }

  return {
    menu: isValidMenu ? (menuId as MenuId) : 'dashboard',
    subPath: segments.length > 1 ? segments.slice(1).join('/') : undefined,
    params,
    isFallback: !isValidMenu,
  };
};

const buildHashFromRoute = (route: RouteState) => {
  // 왜: 현재 화면 상태를 항상 같은 규칙의 주소로 만들기 위함입니다.
  const path = [route.menu, route.subPath].filter(Boolean).join('/');
  const searchParams = new URLSearchParams();
  Object.entries(route.params).forEach(([key, value]) => {
    if (value) searchParams.set(key, value);
  });
  const query = searchParams.toString();
  return `#/${path}${query ? `?${query}` : ''}`;
};

export default function App() {
  const [routeState, setRouteState] = useState<RouteState>(() => {
    const parsed = parseRouteFromHash(window.location.hash);
    return { menu: parsed.menu, subPath: parsed.subPath, params: parsed.params };
  });
  const [contentLibraryExpanded, setContentLibraryExpanded] = useState(false);
  const [examMenuExpanded, setExamMenuExpanded] = useState(false);
  const [assignmentMenuExpanded, setAssignmentMenuExpanded] = useState(false);
  const [qnaMenuExpanded, setQnaMenuExpanded] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0); // 컴포넌트 재렌더링용 키
  const activeMenu = routeState.menu;

  // 콘텐츠 라이브러리 하위 메뉴 여부 확인
  const isContentLibrarySubMenu = activeMenu === 'content-all' || activeMenu === 'content-favorites';
  
  // 시험관리 하위 메뉴 여부 확인
  const isExamSubMenu = activeMenu === 'exam-categories' || activeMenu === 'exam-questions' || activeMenu === 'exam-management';

  // 과제 통합관리 하위 메뉴 여부 확인
  const isAssignmentSubMenu = activeMenu === 'assignment-manage' || activeMenu === 'assignment-submissions' || activeMenu === 'assignment-templates' || activeMenu === 'feedback-templates';

  // Q&A 하위 메뉴 여부 확인
  const isQnaSubMenu = activeMenu === 'qna-manage' || activeMenu === 'faq-manage';

  const syncHash = useCallback((route: RouteState, replace = false) => {
    // 왜: 서버 라우팅 없이도 뒤로가기/직접 주소 접근이 되도록 해시를 동기화합니다.
    const nextHash = buildHashFromRoute(route);
    if (window.location.hash === nextHash) return;
    const nextUrl = `${window.location.pathname}${window.location.search}${nextHash}`;
    if (replace) {
      window.history.replaceState(null, '', nextUrl);
    } else {
      window.location.hash = nextHash;
    }
  }, []);

  const applyRoute = useCallback((route: RouteState, options?: { syncHash?: boolean; replaceHash?: boolean }) => {
    setRouteState((prev) => (isSameRoute(prev, route) ? prev : route));
    if (route.menu === 'content-all' || route.menu === 'content-favorites') {
      setContentLibraryExpanded(true);
    }
    if (route.menu === 'exam-categories' || route.menu === 'exam-questions' || route.menu === 'exam-management') {
      setExamMenuExpanded(true);
    }
    if (route.menu === 'assignment-manage' || route.menu === 'assignment-submissions' || route.menu === 'assignment-templates' || route.menu === 'feedback-templates') {
      setAssignmentMenuExpanded(true);
    }
    if (route.menu === 'qna-manage' || route.menu === 'faq-manage') {
      setQnaMenuExpanded(true);
    }
    if (options?.syncHash !== false) {
      syncHash(route, options?.replaceHash);
    }
  }, [syncHash]);

  const applyMenu = useCallback((menu: MenuId) => {
    applyRoute({ menu, params: {} });
  }, [applyRoute]);

  const handleOpenCourseFromDashboard = useCallback((payload: { courseId: number; courseName?: string; targetTab?: CourseManagementTabId; qnaPostId?: number; sourceType?: 'prism' | 'haksa' }) => {
    // 왜: 대시보드에서 선택한 과목/탭(필요 시 Q&A 글)을 주소에 담아 바로 이동합니다.
    const params: Record<string, string> = {
      courseId: String(payload.courseId),
      // 왜: 목록 탭(tab=prism/haksa)과 충돌을 피하기 위해, 관리 탭은 cmTab으로 분리합니다.
      cmTab: payload.targetTab ?? 'attendance',
      source: payload.sourceType ?? 'prism',
      // 왜: 대시보드 딥링크는 목록/페이징을 거치지 않고 바로 과목을 열어야 안정적입니다.
      direct: '1',
    };
    if (payload.courseName) params.courseName = payload.courseName;
    if (payload.qnaPostId) params.qnaPostId = String(payload.qnaPostId);
    applyRoute({
      menu: 'courses',
      subPath: 'manage',
      params,
    });
  }, [applyRoute]);

  const handleCoursesRouteChange = useCallback((next: { subPath?: string; params: Record<string, string> }) => {
    applyRoute({ menu: 'courses', subPath: next.subPath, params: next.params });
  }, [applyRoute]);

  useEffect(() => {
    const syncFromHash = () => {
      const parsed = parseRouteFromHash(window.location.hash);
      if (parsed.isFallback) {
        // 왜: 잘못된 주소가 들어오면 기본 화면으로 정리합니다.
        applyRoute({ menu: 'dashboard', params: {} }, { syncHash: true, replaceHash: true });
        return;
      }
      applyRoute({ menu: parsed.menu, subPath: parsed.subPath, params: parsed.params }, { syncHash: false });
    };

    syncFromHash();
    window.addEventListener('hashchange', syncFromHash);
    return () => window.removeEventListener('hashchange', syncFromHash);
  }, [applyRoute]);

  const handleContentLibraryClick = () => {
    setContentLibraryExpanded((prev) => !prev);
  };

  const handleExamMenuClick = () => {
    setExamMenuExpanded((prev) => !prev);
  };

  const handleAssignmentMenuClick = () => {
    setAssignmentMenuExpanded((prev) => !prev);
  };

  const handleQnaMenuClick = () => {
    setQnaMenuExpanded((prev) => !prev);
  };

  // 현재 화면 새로고침
  const handleRefresh = () => {
    setRefreshKey(prev => prev + 1);
  };

  const createCourseStep = routeState.menu === 'create-course' && CREATE_COURSE_STEP_SET.has(routeState.params.step ?? '')
    ? (routeState.params.step as typeof CREATE_COURSE_STEP_IDS[number])
    : undefined;

  const subjectStep = routeState.menu === 'subject-create' && SUBJECT_STEP_SET.has(routeState.params.step ?? '')
    ? (routeState.params.step as typeof SUBJECT_STEP_IDS[number])
    : undefined;

  // 왜: onStepChange 콜백을 JSX에서 매번 새로 만들면(익명 함수) 자식의 useEffect 의존성이 불필요하게 변합니다.
  //     화면 깜빡임의 직접 원인은 아니지만, 단계 전환/주소 동기화가 많은 화면이라 성능과 안정성을 위해 고정합니다.
  const handleCreateCourseStepChange = useCallback((step: typeof CREATE_COURSE_STEP_IDS[number]) => {
    applyRoute({
      menu: 'create-course',
      params: step === 'basic' ? {} : { step },
    });
  }, [applyRoute]);

  const handleSubjectStepChange = useCallback((step: typeof SUBJECT_STEP_IDS[number]) => {
    applyRoute({
      menu: 'subject-create',
      params: step === 'basic' ? {} : { step },
    });
  }, [applyRoute]);

  return (
    <div className="h-dvh flex flex-col bg-background text-foreground overflow-hidden">
      {/* Header */}
      <header className="bg-card border-b border-border shrink-0 z-50">
        <div className="flex h-16 items-center justify-between px-6">
          {/* Logo */}
          <div className="flex items-center gap-3 cursor-pointer" onClick={() => applyMenu('dashboard')}>
            <div className="bg-blue-600 p-2 rounded-lg">
              <GraduationCap className="w-7 h-7 text-white" />
            </div>
            <div className="leading-tight">
              <h1 className="text-base font-semibold text-balance">교수자 LMS</h1>
              <p className="text-sm text-muted-foreground text-pretty">Learning Management System</p>
            </div>
          </div>

          {/* 새로고침 버튼 */}
          <button
            onClick={handleRefresh}
            className="p-2 text-muted-foreground hover:text-blue-700 hover:bg-blue-50 rounded-lg transition-colors"
            title="현재 화면 새로고침"
            aria-label="현재 화면 새로고침"
          >
            <RefreshCw className="w-5 h-5" />
          </button>
        </div>
      </header>

      <div className="flex w-full flex-1 gap-6 px-6 py-6 overflow-hidden">
        {/* Left Navigation Sidebar */}
        <aside className="h-full w-64 shrink-0 overflow-y-auto rounded-xl border border-border bg-card p-3">
          <nav className="flex flex-col gap-1 text-sm">
            {/* === 메인 === */}
            <button
              onClick={() => applyMenu('dashboard')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'dashboard'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <GraduationCap className="w-5 h-5" />
              <span>대시보드</span>
            </button>
            <button
              onClick={() => applyMenu('explore')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'explore'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <Compass className="w-5 h-5" />
              <span>과정탐색</span>
            </button>
            <button
              onClick={() => applyMenu('courses')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'courses'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <BookOpen className="w-5 h-5" />
              <span>담당과목</span>
            </button>

            {/* === 개설 === */}
            <div className="mt-3 mb-1 border-t border-border pt-3">
              <span className="px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">개설</span>
            </div>
            <button
              onClick={() => applyMenu('create-course')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'create-course'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <FolderPlus className="w-5 h-5" />
              <span>과정개설</span>
            </button>
            <button
              onClick={() => applyMenu('subject-create')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'subject-create'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <BookPlus className="w-5 h-5" />
              <span>과목개설</span>
            </button>

            {/* === 학습 관리 === */}
            <div className="mt-3 mb-1 border-t border-border pt-3">
              <span className="px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">학습 관리</span>
            </div>
            <button
              onClick={() => applyMenu('attendance-manage')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'attendance-manage'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <UserCheck className="w-5 h-5" />
              <span>출석 관리</span>
            </button>
            <button
              onClick={() => applyMenu('video-group-manage')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'video-group-manage'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <Video className="w-5 h-5" />
              <span>동영상그룹관리</span>
            </button>
            <div>
              <button
                onClick={handleExamMenuClick}
                className={`w-full flex items-center justify-between px-4 py-3 rounded-lg transition-colors text-left ${
                  isExamSubMenu
                    ? 'bg-blue-600 text-white'
                    : 'text-sidebar-foreground hover:bg-muted'
                }`}
              >
                <div className="flex items-center gap-3">
                  <ClipboardList className="w-5 h-5" />
                  <span>시험관리</span>
                </div>
                {examMenuExpanded ? (
                  <ChevronDown className="w-4 h-4" />
                ) : (
                  <ChevronRight className="w-4 h-4" />
                )}
              </button>
              {examMenuExpanded && (
                <div className="ml-4 mt-1 flex flex-col gap-1">
                  <button
                    onClick={() => applyMenu('exam-categories')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'exam-categories'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>문제카테고리</span>
                  </button>
                  <button
                    onClick={() => applyMenu('exam-questions')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'exam-questions'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>문제은행</span>
                  </button>
                  <button
                    onClick={() => applyMenu('exam-management')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'exam-management'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>시험관리</span>
                  </button>
                </div>
              )}
            </div>
            <div>
              <button
                onClick={handleAssignmentMenuClick}
                className={`w-full flex items-center justify-between px-4 py-3 rounded-lg transition-colors text-left ${
                  isAssignmentSubMenu
                    ? 'bg-blue-600 text-white'
                    : 'text-sidebar-foreground hover:bg-muted'
                }`}
              >
                <div className="flex items-center gap-3">
                  <ClipboardCheck className="w-5 h-5" />
                  <span>과제 통합관리</span>
                </div>
                {assignmentMenuExpanded ? (
                  <ChevronDown className="w-4 h-4" />
                ) : (
                  <ChevronRight className="w-4 h-4" />
                )}
              </button>
              {assignmentMenuExpanded && (
                <div className="ml-4 mt-1 flex flex-col gap-1">
                  <button
                    onClick={() => applyMenu('assignment-submissions')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'assignment-submissions'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>제출 현황</span>
                  </button>
                  <button
                    onClick={() => applyMenu('assignment-templates')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'assignment-templates'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>과제 템플릿</span>
                  </button>
                  <button
                    onClick={() => applyMenu('feedback-templates')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'feedback-templates'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>피드백 템플릿</span>
                  </button>
                </div>
              )}
            </div>
            <div>
              <button
                onClick={handleQnaMenuClick}
                className={`w-full flex items-center justify-between px-4 py-3 rounded-lg transition-colors text-left ${
                  isQnaSubMenu
                    ? 'bg-blue-600 text-white'
                    : 'text-sidebar-foreground hover:bg-muted'
                }`}
              >
                <div className="flex items-center gap-3">
                  <MessageSquare className="w-5 h-5" />
                  <span>Q&A 통합관리</span>
                </div>
                {qnaMenuExpanded ? (
                  <ChevronDown className="w-4 h-4" />
                ) : (
                  <ChevronRight className="w-4 h-4" />
                )}
              </button>
              {qnaMenuExpanded && (
                <div className="ml-4 mt-1 flex flex-col gap-1">
                  <button
                    onClick={() => applyMenu('qna-manage')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'qna-manage'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>Q&A 관리</span>
                  </button>
                  <button
                    onClick={() => applyMenu('faq-manage')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'faq-manage'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>FAQ 공지 설정</span>
                  </button>
                </div>
              )}
            </div>

            {/* === 리소스 === */}
            <div className="mt-3 mb-1 border-t border-border pt-3">
              <span className="px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">리소스</span>
            </div>
            <div>
              <button
                onClick={handleContentLibraryClick}
                className={`w-full flex items-center justify-between px-4 py-3 rounded-lg transition-colors text-left ${
                  isContentLibrarySubMenu
                    ? 'bg-blue-600 text-white'
                    : 'text-sidebar-foreground hover:bg-muted'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Library className="w-5 h-5" />
                  <span>콘텐츠 라이브러리</span>
                </div>
                {contentLibraryExpanded ? (
                  <ChevronDown className="w-4 h-4" />
                ) : (
                  <ChevronRight className="w-4 h-4" />
                )}
              </button>
              {contentLibraryExpanded && (
                <div className="ml-4 mt-1 flex flex-col gap-1">
                  <button
                    onClick={() => applyMenu('content-all')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'content-all'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>전체 콘텐츠</span>
                  </button>
                  <button
                    onClick={() => applyMenu('content-favorites')}
                    className={`flex items-center gap-3 px-4 py-2 rounded-lg transition-colors text-left text-sm ${
                      activeMenu === 'content-favorites'
                        ? 'bg-blue-100 text-blue-700 font-medium'
                        : 'text-muted-foreground hover:bg-muted'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                    <span>찜한 콘텐츠</span>
                  </button>
                </div>
              )}
            </div>
            <button
              onClick={() => applyMenu('statistics')}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors text-left ${
                activeMenu === 'statistics'
                  ? 'bg-blue-600 text-white'
                  : 'text-sidebar-foreground hover:bg-muted'
              }`}
            >
              <BarChart3 className="w-5 h-5" />
              <span>통계</span>
            </button>
          </nav>
        </aside>

        {/* Main Content Area */}
        <main className="min-w-0 flex-1 overflow-y-auto">
          {/* Empty content area - 추후 추가될 컨텐츠 영역 */}
          {activeMenu === 'dashboard' ? (
            <Dashboard
              key={refreshKey}
              onNavigate={(menu) => applyMenu(menu)}
              onOpenCourse={handleOpenCourseFromDashboard}
            />
          ) : activeMenu === 'explore' ? (
            <CourseExplorer key={refreshKey} />
          ) : activeMenu === 'courses' ? (
            <MyCoursesList
              key={refreshKey}
              routeSubPath={routeState.subPath}
              routeParams={routeState.params}
              onRouteChange={handleCoursesRouteChange}
            />
          ) : activeMenu === 'assignment-manage' || activeMenu === 'assignment-submissions' ? (
            <AssignmentManagePage
              key={refreshKey}
              onOpenCourse={handleOpenCourseFromDashboard}
            />
          ) : activeMenu === 'assignment-templates' ? (
            <div className="space-y-6" key={refreshKey}>
              <div>
                <h1 className="text-gray-900 mb-1">과제 템플릿</h1>
                <p className="text-gray-600">과제 템플릿을 관리하고 여러 강의에 동시 업로드합니다.</p>
              </div>
              <AssignmentTemplateTab />
            </div>
          ) : activeMenu === 'feedback-templates' ? (
            <div className="space-y-6" key={refreshKey}>
              <FeedbackTemplateTab />
            </div>
          ) : activeMenu === 'qna-manage' ? (
            <QnaManagePage
              key={refreshKey}
              onOpenCourse={handleOpenCourseFromDashboard}
            />
          ) : activeMenu === 'faq-manage' ? (
            <FaqManagePage key={refreshKey} />
          ) : activeMenu === 'create-course' ? (
            <CreateCourseForm
              key={refreshKey}
              initialStep={createCourseStep}
              onStepChange={handleCreateCourseStepChange}
              onCreated={() => applyMenu('explore')}
            />
          ) : activeMenu === 'content-all' ? (
            <ContentLibraryPage key={refreshKey} activeTab="all" />
          ) : activeMenu === 'content-favorites' ? (
            <ContentLibraryPage key={`${refreshKey}-fav`} activeTab="favorites" />
          ) : activeMenu === 'exam-categories' ? (
            <QuestionCategoryPage key={refreshKey} />
          ) : activeMenu === 'exam-questions' ? (
            <QuestionBankPage key={refreshKey} />
          ) : activeMenu === 'exam-management' ? (
            <ExamManagementPage key={refreshKey} />
          ) : activeMenu === 'subject-create' ? (
            <CreateSubjectWizard
              key={refreshKey}
              initialStep={subjectStep}
              onStepChange={handleSubjectStepChange}
            />
          ) : activeMenu === 'statistics' ? (
            <StatisticsPage key={refreshKey} />
          ) : activeMenu === 'attendance-manage' ? (
            <AttendanceManagePage key={refreshKey} />
          ) : activeMenu === 'video-group-manage' ? (
            <VideoGroupManagePage key={refreshKey} />
          ) : (
            <div className="bg-card rounded-xl border-2 border-dashed border-border p-16 text-center">
              <div className="text-muted-foreground">
                <GraduationCap className="w-16 h-16 mx-auto mb-4 opacity-50" />
                <p className="text-lg font-medium">컨텐츠 영역</p>
                <p className="text-sm mt-2">메뉴를 선택하면 관련 내용이 표시됩니다</p>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

