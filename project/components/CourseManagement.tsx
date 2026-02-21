import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Info,
  List,
  Users,
  ClipboardCheck,
  FileText,
  Briefcase,
  FolderOpen,
  MessageSquare,
  Award,
  CheckCircle,
  ArrowLeft,
  Download,
  Upload,
  Edit,
  Trash2,
  Copy,
  ChevronDown,
  ChevronRight,
  Play,
  Clock,
  Plus,
  BookOpen,
  Printer,
  Paperclip,
  AlertTriangle,
  BarChart3,
} from 'lucide-react';
import { SessionEditModal } from './SessionEditModal';
import { CourseInfoTab } from './CourseInfoTabs';
import { ExamCreateModal } from './ExamCreateModal';
import { AssignmentCreateModal } from './AssignmentCreateModal';
import { AssignmentDetailModal } from './AssignmentDetailModal';
import { HomeworkTaskDetailModal } from './HomeworkTaskDetailModal';
import { HomeworkSubmissionDetailModal } from './HomeworkSubmissionDetailModal';
import { tutorLmsApi, type HaksaCourseKey } from '../api/tutorLmsApi';
import { buildHaksaCourseKey } from '../utils/haksa';
import { CurriculumTab } from './courseManagement/CurriculumTab';
import { StudentsTab } from './courseManagement/StudentsTab';
import { AttendanceTab } from './courseManagement/AttendanceTab';
import { downloadCsv } from '../utils/csv';
import { CourseFeedbackReportTab } from './CourseFeedbackReportTab';


interface CourseManagementProps {
  course: {
    id: string;
    mappedCourseId?: number;
    // ?? ?숈궗/?꾨━利???뿉 ?곕씪 API ?몄텧 諛??몄쭛 媛???щ?瑜?寃곗젙?⑸땲??
    sourceType?: 'haksa' | 'prism';
    courseId: string;
    courseType: string;
    subjectName: string;
    programId: number;
    programName: string;
    period: string;
    students: number;
    status: string;
    // ===== ?숈궗 View 25媛??꾨뱶 =====
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
  };
  onBack: () => void;
  initialTab?: CourseManagementTabId;
  initialQnaPostId?: number;
  onTabChange?: (tabId: CourseManagementTabId) => void;
}

const getHaksaWeekCount = (course?: any) => {
  if (course?.haksaWeek) {
    const parsed = parseInt(course.haksaWeek, 10);
    if (!Number.isNaN(parsed) && parsed > 0) return parsed;
  }
  return 15;
};

const buildHaksaSessionName = (sessionNumber: number) => `${sessionNumber}李⑥떆`;

const appendHaksaCurriculumContent = async ({
  haksaKey,
  weekNumber,
  sessionNumber,
  content,
}: {
  haksaKey: HaksaCourseKey | null;
  weekNumber: number;
  sessionNumber: number;
  content: any;
}) => {
  if (!haksaKey) throw new Error('?숈궗 怨쇰ぉ ?ㅺ? 鍮꾩뼱 ?덉뼱 媛뺤쓽紐⑹감????ν븷 ???놁뒿?덈떎.');

  const res = await tutorLmsApi.getHaksaCurriculum(haksaKey);
  if (res.rst_code !== '0000') throw new Error(res.rst_message);

  // ?? DataSet ?묐떟??諛곗뿴濡??????덉뼱 泥?踰덉㎏ ?됱쓣 湲곗??쇰줈 ?댁꽍?⑸땲??
  const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
  const raw = payload?.curriculum_json || '';

  let weeks: any[] = [];
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      weeks = Array.isArray(parsed) ? parsed : [];
    } catch {
      weeks = [];
    }
  }

  const normalizedWeek = Number(weekNumber || 1);
  const normalizedSession = Number(sessionNumber || 1);
  const sessionName = buildHaksaSessionName(normalizedSession);

  let targetWeek = weeks.find((w) => w.weekNumber === normalizedWeek);
  if (!targetWeek) {
    targetWeek = {
      weekNumber: normalizedWeek,
      title: `${normalizedWeek}二쇱감`,
      isExpanded: true,
      sessions: [],
    };
    weeks.push(targetWeek);
  }

  if (!Array.isArray(targetWeek.sessions)) targetWeek.sessions = [];
  let targetSession = targetWeek.sessions.find((s: any) => s.sessionName === sessionName);
  if (!targetSession) {
    targetSession = {
      sessionId: `session_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
      sessionName,
      isExpanded: true,
      contents: [],
    };
    targetWeek.sessions.push(targetSession);
  }

  if (!Array.isArray(targetSession.contents)) targetSession.contents = [];
  targetSession.contents.push({
    ...content,
    id: `content_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
    createdAt: new Date().toISOString(),
    weekNumber: normalizedWeek,
  });

  const saveRes = await tutorLmsApi.updateHaksaCurriculum({
    ...haksaKey,
    curriculumJson: JSON.stringify(weeks),
  });
  if (saveRes.rst_code !== '0000') throw new Error(saveRes.rst_message);
};


export type CourseManagementTabId =
  | 'info'
  | 'info-basic'
  | 'info-evaluation'
  | 'info-completion'
  | 'curriculum'
  | 'students'
  | 'attendance'
  | 'exam'
  | 'assignment'
  | 'assignment-management'
  | 'assignment-feedback'
  | 'materials'
  | 'qna'
  | 'grades'
  | 'completion'
  | 'feedback-report';

type TabType = CourseManagementTabId;

const INFO_SUB_TAB_IDS: CourseManagementTabId[] = ['info-basic', 'info-evaluation', 'info-completion'];
const ASSIGNMENT_SUB_TAB_IDS: CourseManagementTabId[] = ['assignment-management', 'assignment-feedback'];

export function CourseManagement({ course: initialCourse, onBack, initialTab, initialQnaPostId, onTabChange }: CourseManagementProps) {
  const [course, setCourse] = useState(initialCourse);

  const resolveInitialTab = (tab?: CourseManagementTabId): TabType => {
    // ?? ?곸쐞 ??info/assignment)???ㅼ뼱?ㅻ㈃ 湲곕낯 ?섏쐞 ??쑝濡??뺣━?⑸땲??
    if (!tab || tab === 'info') return 'info-basic';
    if (tab === 'assignment') return 'assignment-management';
    return tab;
  };

  const resolvedInitialTab = resolveInitialTab(initialTab);
  const [activeTab, setActiveTab] = useState<TabType>(resolvedInitialTab);
  const [isInfoExpanded, setIsInfoExpanded] = useState(() => INFO_SUB_TAB_IDS.includes(resolvedInitialTab));
  const [isAssignmentExpanded, setIsAssignmentExpanded] = useState(() => ASSIGNMENT_SUB_TAB_IDS.includes(resolvedInitialTab));

  useEffect(() => {
    if (!initialTab) return;
    // ?? ?ㅻ줈媛湲?吏곸젒 二쇱냼 ?대룞 ?????곹깭瑜?二쇱냼 湲곗??쇰줈 ?ㅼ떆 留욎땅?덈떎.
    const nextTab = resolveInitialTab(initialTab);
    setActiveTab(nextTab);
    setIsInfoExpanded(INFO_SUB_TAB_IDS.includes(nextTab));
    setIsAssignmentExpanded(ASSIGNMENT_SUB_TAB_IDS.includes(nextTab));
  }, [initialTab]);

  useEffect(() => {
    // ?? ?곸쐞?먯꽌 二쇱냼 ?숆린?붾? ?????덈룄濡??꾩옱 ??쓣 ?뚮젮以띾땲??
    onTabChange?.(activeTab);
  }, [activeTab, onTabChange]);

  // ?섏씠吏 吏꾩엯 ???ㅽ겕濡ㅼ쓣 留??꾨줈 ?대룞
  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  useEffect(() => {
    setCourse(initialCourse);
  }, [initialCourse]);

  const tabs = [
    { id: 'info' as TabType, label: '과목정보', icon: Info, isSubTab: false, hasSubTabs: true },
    { id: 'curriculum' as TabType, label: '강의목차', icon: List, isSubTab: false },
    { id: 'students' as TabType, label: '수강생', icon: Users, isSubTab: false },
    { id: 'attendance' as TabType, label: '진도/출석', icon: ClipboardCheck, isSubTab: false },
    { id: 'exam' as TabType, label: '시험', icon: FileText, isSubTab: false },
    { id: 'assignment' as TabType, label: '과제', icon: Briefcase, isSubTab: false, hasSubTabs: true },
    { id: 'materials' as TabType, label: '자료', icon: FolderOpen, isSubTab: false },
    { id: 'qna' as TabType, label: 'Q&A', icon: MessageSquare, isSubTab: false },
    { id: 'feedback-report' as TabType, label: '통합출력', icon: Printer, isSubTab: false },
    { id: 'grades' as TabType, label: '성적관리', icon: Award, isSubTab: false },
    { id: 'completion' as TabType, label: '수료관리', icon: CheckCircle, isSubTab: false },
  ];

  // ?? 怨쇰ぉ?뺣낫 ?섏쐞 ??(info ??耳??蹂寃?
  const infoSubTabs = [
    { id: 'info-basic' as TabType, label: '기본 정보', icon: Info },
    { id: 'info-evaluation' as TabType, label: '평가/수료 기준', icon: ClipboardCheck },
    { id: 'info-completion' as TabType, label: '수료증', icon: Award },
  ];

  const assignmentSubTabs = [
    { id: 'assignment-management' as TabType, label: '과제 관리', icon: Briefcase },
    { id: 'assignment-feedback' as TabType, label: '피드백 관리', icon: MessageSquare },
  ];

  const handleTabClick = (tabId: TabType) => {
    if (tabId === 'info') {
      setIsInfoExpanded(!isInfoExpanded);
      if (!isInfoExpanded) {
        setActiveTab('info-basic');
      }
    } else if (tabId === 'assignment') {
      setIsAssignmentExpanded(!isAssignmentExpanded);
      if (!isAssignmentExpanded) {
        setActiveTab('assignment-management');
      }
    } else {
      setActiveTab(tabId);
      // ?ㅻⅨ ??쓣 ?대┃?섎㈃ ?대떦 ??쓽 ?섏쐞 ??쭔 ?좎?
      if (!visibleInfoSubTabs.map((subTab) => subTab.id).includes(tabId)) {
        setIsInfoExpanded(false);
      }
      if (!['assignment-management', 'assignment-feedback'].includes(tabId)) {
        setIsAssignmentExpanded(false);
      }
    }
  };

  const courseIdNum = Number(course?.mappedCourseId ?? course.id);
  const isHaksaCourse =
    course?.sourceType === 'haksa' && (!course?.mappedCourseId || Number.isNaN(courseIdNum) || courseIdNum <= 0);
  const isHaksaMenu =
    course?.sourceType === 'haksa' ||
    Boolean(
      course?.haksaCourseCode ||
      course?.haksaOpenYear ||
      course?.haksaOpenTerm ||
      course?.haksaBunbanCode ||
      course?.haksaGroupCode
    ) ||
    String(course?.courseType || '').includes('?숈궗');
  const visibleTabs = isHaksaMenu ? tabs.filter(tab => tab.id !== 'completion') : tabs;
  const visibleInfoSubTabs = isHaksaMenu
    ? infoSubTabs.filter(subTab => subTab.id !== 'info-completion')
    : infoSubTabs;

  useEffect(() => {
    if (!isHaksaMenu) return;
    if (activeTab === 'completion' || activeTab === 'info-completion') {
      setActiveTab('info-basic');
    }
  }, [activeTab, isHaksaMenu]);

  const [headerActionLoading, setHeaderActionLoading] = useState<'copy' | 'delete' | null>(null);

  const handleCopyCourse = () => {
    if (!Number.isFinite(courseIdNum) || courseIdNum <= 0) {
      alert('蹂듭궗 媛?ν븳 怨쇰ぉ ID瑜?李얠쓣 ???놁뒿?덈떎.');
      return;
    }

    const defaultName = `${course.subjectName} (蹂듭궗)`;
    const nextName = prompt('蹂듭궗??怨쇰ぉ紐낆쓣 ?낅젰??二쇱꽭??', defaultName);
    if (nextName === null) return;

    const trimmedName = nextName.trim();
    if (!trimmedName) {
      alert('怨쇰ぉ紐낆쓣 ?낅젰??二쇱꽭??');
      return;
    }

    void (async () => {
      setHeaderActionLoading('copy');
      try {
        // ?? 蹂듭궗 API???대떦 援먯닔(tutor_id)媛 ?꾩닔?쇱꽌 癒쇱? ?꾩옱 ?ъ슜???쒗꽣 ?뺣낫瑜?議고쉶?⑸땲??
        const tutorRes = await tutorLmsApi.getTutors();
        if (tutorRes.rst_code !== '0000') throw new Error(tutorRes.rst_message);
        const tutors = Array.isArray(tutorRes.rst_data) ? tutorRes.rst_data : [];
        const tutorId = Number(tutors[0]?.user_id ?? 0);
        if (!tutorId) throw new Error('?대떦 援먯닔 ?뺣낫瑜?李얠쓣 ???놁뒿?덈떎.');

        const copyRes = await tutorLmsApi.copyCourse({
          sourceCourseId: courseIdNum,
          courseName: trimmedName,
          tutorId,
        });
        if (copyRes.rst_code !== '0000') throw new Error(copyRes.rst_message);

        alert('怨쇰ぉ??蹂듭궗?섏뿀?듬땲?? 紐⑸줉?먯꽌 ??怨쇰ぉ???뺤씤??二쇱꽭??');
        onBack();
      } catch (e) {
        alert(e instanceof Error ? e.message : '怨쇰ぉ 蹂듭궗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setHeaderActionLoading(null);
      }
    })();
  };

  const handleDeleteCourse = () => {
    if (!Number.isFinite(courseIdNum) || courseIdNum <= 0) {
      alert('??젣 媛?ν븳 怨쇰ぉ ID瑜?李얠쓣 ???놁뒿?덈떎.');
      return;
    }

    if (!confirm(`"${course.subjectName}" 怨쇰ぉ???뺣쭚 ??젣?섏떆寃좎뒿?덇퉴?\n\n????젣??怨쇰ぉ? 蹂듦뎄?????놁뒿?덈떎.`)) {
      return;
    }

    void (async () => {
      setHeaderActionLoading('delete');
      try {
        const res = await tutorLmsApi.deleteCourse({ courseId: courseIdNum });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        alert('怨쇰ぉ????젣?섏뿀?듬땲??');
        onBack();
      } catch (e) {
        alert(e instanceof Error ? e.message : '怨쇰ぉ ??젣 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setHeaderActionLoading(null);
      }
    })();
  };

  const renderTabContent = () => {
    switch (activeTab) {
      case 'info':
      case 'info-basic':
        return <CourseInfoTab course={course} onCourseUpdated={setCourse} initialSubTab="basic" />;
      case 'info-evaluation':
        return <CourseInfoTab course={course} onCourseUpdated={setCourse} initialSubTab="evaluation" />;
      case 'info-completion':
        return isHaksaMenu
          ? <CourseInfoTab course={course} onCourseUpdated={setCourse} initialSubTab="basic" />
          : <CourseInfoTab course={course} onCourseUpdated={setCourse} initialSubTab="completion" />;
      case 'curriculum':
        return <CurriculumTab courseId={courseIdNum} course={course} />;
      case 'students':
        return <StudentsTab courseId={courseIdNum} course={course} />;
      case 'attendance':
        return <AttendanceTab courseId={courseIdNum} course={course} />;
      case 'exam':
        return isHaksaCourse ? <HaksaExamTab course={course} /> : <ExamTab courseId={courseIdNum} course={course} />;
      case 'assignment':
        return <AssignmentTab courseId={courseIdNum} course={course} />;
      case 'assignment-management':
        return <AssignmentManagementTab courseId={courseIdNum} course={course} />;
      case 'assignment-feedback':
        return <AssignmentFeedbackTab courseId={courseIdNum} />;
      case 'materials':
        return (
          <MaterialsTab
            courseId={courseIdNum}
            showWeekSession={course?.sourceType === 'haksa'}
            weekCount={getHaksaWeekCount(course)}
            course={course}
          />
        );
      case 'feedback-report':
        return <CourseFeedbackReportTab course={course} />;
      case 'qna':
        return <QnaTab courseId={courseIdNum} initialPostId={initialQnaPostId} />;
      case 'grades':
        // ?? ?숈궗 ?깆쟻? A/B/C/D/F ?먯젙 UI媛 ?붽뎄?섎?濡? 留ㅽ븨 ?щ?? 臾닿??섍쾶 ?숈궗 ?꾩슜 ?붾㈃???ъ슜?⑸땲??
        return course?.sourceType === 'haksa'
          ? <HaksaGradingContent course={course} />
          : <GradesTab courseId={courseIdNum} />;
      case 'completion':
        return isHaksaMenu ? null : <CompletionTab courseId={courseIdNum} course={course} />;
      default:
        return null;
    }
  };

  return (
    <div className="max-w-7xl mx-auto">
      {/* Vertical Tabs Layout */}
      <div className="flex gap-6">
        {/* Left Sidebar - Vertical Tabs (Fixed Position) */}
        <div className="w-64 flex-shrink-0">
          <div className="fixed w-64" style={{ maxHeight: 'calc(100dvh - 40px)', overflowY: 'auto' }}>
            {/* 紐⑸줉?쇰줈 ?뚯븘媛湲?踰꾪듉 */}
            <button
              onClick={onBack}
              className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4 transition-colors"
            >
              <ArrowLeft className="w-5 h-5" />
              <span>紐⑸줉?쇰줈 ?뚯븘媛湲?</span>
            </button>
            
            {/* 硫붾돱 ?ㅻ퉬寃뚯씠??*/}
            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
              <nav className="flex flex-col">
                {visibleTabs.map((tab) => {
                  const Icon = tab.icon;
                  const isInfoTab = tab.id === 'info';
                  const isAssignmentTab = tab.id === 'assignment';
                  const isActive = activeTab === tab.id || 
                    (isInfoTab && visibleInfoSubTabs.map((subTab) => subTab.id).includes(activeTab)) ||
                    (isAssignmentTab && (activeTab === 'assignment-management' || activeTab === 'assignment-feedback'));
                  
                  return (
                    <React.Fragment key={tab.id}>
                      <button
                        onClick={() => handleTabClick(tab.id)}
                        className={`flex items-center justify-between px-4 py-3 border-l-4 transition-colors text-left ${
                          isActive
                            ? 'border-blue-600 bg-blue-50 text-blue-700'
                            : 'border-transparent text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <Icon className="w-5 h-5" />
                          <span>{tab.label}</span>
                        </div>
                        {(isInfoTab || isAssignmentTab) && (
                          (isInfoTab ? isInfoExpanded : isAssignmentExpanded) ? (
                            <ChevronDown className="w-4 h-4" />
                          ) : (
                            <ChevronRight className="w-4 h-4" />
                          )
                        )}
                      </button>
                      
                      {/* 怨쇰ぉ?뺣낫 ?섏쐞 ??*/}
                      {isInfoTab && isInfoExpanded && (
                        <div className="bg-gray-50">
                          {visibleInfoSubTabs.map((subTab) => {
                            const SubIcon = subTab.icon;
                            return (
                              <button
                                key={subTab.id}
                                onClick={() => {
                                  setActiveTab(subTab.id);
                                }}
                                className={`w-full flex items-center gap-3 pl-12 pr-4 py-2.5 border-l-4 transition-colors text-left text-sm ${
                                  activeTab === subTab.id
                                    ? 'border-blue-600 bg-blue-100 text-blue-700'
                                    : 'border-transparent text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                                }`}
                              >
                                <SubIcon className="w-4 h-4" />
                                <span>{subTab.label}</span>
                              </button>
                            );
                          })}
                        </div>
                      )}
                      
                      {/* 怨쇱젣 ?섏쐞 ??*/}
                      {isAssignmentTab && isAssignmentExpanded && (
                        <div className="bg-gray-50">
                          {assignmentSubTabs.map((subTab) => {
                            const SubIcon = subTab.icon;
                            return (
                              <button
                                key={subTab.id}
                                onClick={() => {
                                  setActiveTab(subTab.id);
                                }}
                                className={`w-full flex items-center gap-3 pl-12 pr-4 py-2.5 border-l-4 transition-colors text-left text-sm ${
                                  activeTab === subTab.id
                                    ? 'border-blue-600 bg-blue-100 text-blue-700'
                                    : 'border-transparent text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                                }`}
                              >
                                <SubIcon className="w-4 h-4" />
                                <span>{subTab.label}</span>
                              </button>
                            );
                          })}
                        </div>
                      )}
                    </React.Fragment>
                  );
                })}
              </nav>
            </div>
          </div>
        </div>

        {/* Right Content Area */}
        <div className="flex-1 min-w-0">
          {/* 媛뺤쥖 ?뺣낫 ?ㅻ뜑 (?ㅽ겕濡ㅺ낵 ?④퍡 ?대룞) */}
          <div className="mb-6">
            <div className="flex items-center justify-between mb-2">
              <h2 className="text-gray-900">{course.subjectName}</h2>
              {course.sourceType === 'prism' && (
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => setActiveTab('info-basic')}
                    className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-blue-600 bg-blue-50 border border-blue-200 rounded hover:bg-blue-100 transition-colors"
                    title="怨쇰ぉ ?뺣낫 ?섏젙"
                  >
                    <Edit className="w-3.5 h-3.5" />
                    <span>?섏젙</span>
                  </button>
                  <button
                    disabled={headerActionLoading !== null}
                    className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-gray-600 bg-gray-50 border border-gray-200 rounded hover:bg-gray-100 transition-colors"
                    title="怨쇰ぉ 蹂듭궗"
                    onClick={handleCopyCourse}
                  >
                    <Copy className="w-3.5 h-3.5" />
                    <span>{headerActionLoading === 'copy' ? '蹂듭궗 以?..' : '蹂듭궗'}</span>
                  </button>
                  <button
                    disabled={headerActionLoading !== null}
                    className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-red-600 bg-red-50 border border-red-200 rounded hover:bg-red-100 transition-colors"
                    title="怨쇰ぉ ??젣"
                    onClick={handleDeleteCourse}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>{headerActionLoading === 'delete' ? '??젣 以?..' : '??젣'}</span>
                  </button>
                </div>
              )}
            </div>
            <div className="flex items-center gap-4 text-sm text-gray-600">
              <span>怨쇱젙ID: {course.courseId}</span>
              <span className="text-gray-300">쨌</span>
              <span>{course.courseType}</span>
              <span className="text-gray-300">쨌</span>
              <span>{course.period}</span>
              <span className="text-gray-300">쨌</span>
              <span>?섍컯?? {course.students}紐?</span>
            </div>
          </div>
          
          {/* ??肄섑뀗痢?*/}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            {renderTabContent()}
          </div>
        </div>
      </div>
    </div>
  );
}

// 怨쇰ぉ?뺣낫 ??(?댁젣 CourseInfoTabs.tsx?먯꽌 import??

// ?숈궗 怨쇰ぉ: ?쒗뿕 ??媛뺤쓽紐⑹감 湲곕컲?쇰줈 ?쒖떆)
function HaksaExamTab({ course }: { course?: any }) {
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

  const [haksaExams, setHaksaExams] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!haksaKey) {
      setErrorMessage('?숈궗 怨쇰ぉ ?ㅺ? 鍮꾩뼱 ?덉뼱 ?쒗뿕??遺덈윭?????놁뒿?덈떎.');
      setHaksaExams([]);
      return;
    }

    let cancelled = false;
    const fetchHaksaExams = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getHaksaCurriculum(haksaKey);
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        // ?? Malgn DataSet ?묐떟??諛곗뿴濡??????덉뼱 泥?踰덉㎏ ?됱쓣 湲곗??쇰줈 ?댁꽍?⑸땲??
        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        const raw = payload?.curriculum_json || '';
        if (!raw) {
          if (!cancelled) setHaksaExams([]);
          return;
        }

        const parsed = JSON.parse(raw);
        const contents = Array.isArray(parsed)
          ? parsed.flatMap((w: any) => (w.sessions || []).flatMap((s: any) => s.contents || []))
          : [];
        const exams = contents.filter((c: any) => String(c.type || '').toLowerCase() === 'exam');
        if (!cancelled) setHaksaExams(exams);
      } catch (e) {
        if (!cancelled) {
          setHaksaExams([]);
          setErrorMessage(e instanceof Error ? e.message : '?쒗뿕 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void fetchHaksaExams();
    return () => {
      cancelled = true;
    };
  }, [haksaKey]);

  return (
    <div className="space-y-4">
      <div className="p-4 bg-blue-50 border border-blue-200 text-blue-800 rounded-lg text-sm">
        ?숈궗 怨쇰ぉ???쒗뿕? <b>媛뺤쓽紐⑹감</b>?먯꽌 二쇱감/李⑥떆???깅줉????ぉ??湲곗??쇰줈 ?쒖떆?⑸땲??
      </div>

      {errorMessage && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">{errorMessage}</div>
      )}

      {loading && <div className="p-6 text-center text-gray-500">?쒗뿕 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>}

      {!loading && haksaExams.length > 0 ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-medium text-gray-900">?깅줉???쒗뿕</h3>
            <span className="px-3 py-1 bg-red-100 text-red-700 text-sm rounded-full">珥?{haksaExams.length}媛?</span>
          </div>

          {haksaExams.map((exam: any) => (
            <div
              key={exam.id}
              className="p-4 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <FileText className="w-5 h-5 text-red-600" />
                    <span className="font-medium text-gray-900">{exam.title || '?쒗뿕'}</span>
                    <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">
                      {exam.weekNumber || 1}二쇱감
                    </span>
                  </div>
                  {exam.description && <p className="text-sm text-gray-500 mt-1 ml-7">{exam.description}</p>}

                  {exam.examSettings && (
                    <div className="mt-2 ml-7 text-sm text-gray-600 space-y-1">
                      <div>諛곗젏: {exam.examSettings.points ?? 0}??</div>
                      <div>
                        ?ъ쓳??{' '}
                        {exam.examSettings.allowRetake
                          ? `媛??(${exam.examSettings.retakeScore ?? 0}??誘몃쭔, ${exam.examSettings.retakeCount ?? 0}??`
                          : '遺덇?'}
                      </div>
                      <div>결과노출: {exam.examSettings.showResults === false ? '비노출' : '노출'}</div>
                    </div>
                  )}
                </div>

                {exam.createdAt && (
                  <div className="text-sm text-gray-400 flex-shrink-0">
                    {new Date(exam.createdAt).toLocaleDateString('ko-KR')}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        !loading && (
          <div className="text-center text-gray-500 py-12 border border-dashed border-gray-300 rounded-lg">
            <FileText className="w-12 h-12 mx-auto text-gray-300 mb-3" />
            <p className="mb-2">?깅줉???쒗뿕???놁뒿?덈떎.</p>
            <p className="text-sm text-gray-400">媛뺤쓽紐⑹감?먯꽌 二쇱감蹂꾨줈 ?쒗뿕??異붽??????덉뒿?덈떎.</p>
          </div>
        )
      )}
    </div>
  );
}

// ?숈궗 怨쇰ぉ: ?먮즺 ??媛뺤쓽紐⑹감 湲곕컲?쇰줈 ?쒖떆)
function HaksaMaterialsTab({ course }: { course?: any }) {
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

  const [haksaDocs, setHaksaDocs] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!haksaKey) {
      setErrorMessage('?숈궗 怨쇰ぉ ?ㅺ? 鍮꾩뼱 ?덉뼱 ?먮즺瑜?遺덈윭?????놁뒿?덈떎.');
      setHaksaDocs([]);
      return;
    }

    let cancelled = false;
    const fetchHaksaDocs = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getHaksaCurriculum(haksaKey);
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        const raw = payload?.curriculum_json || '';
        if (!raw) {
          if (!cancelled) setHaksaDocs([]);
          return;
        }

        const parsed = JSON.parse(raw);
        const contents = Array.isArray(parsed)
          ? parsed.flatMap((w: any) => (w.sessions || []).flatMap((s: any) => s.contents || []))
          : [];
        const docs = contents.filter((c: any) => {
          const t = String(c.type || '').toLowerCase();
          return t === 'document' || t === 'file' || t === 'library';
        });
        if (!cancelled) setHaksaDocs(docs);
      } catch (e) {
        if (!cancelled) {
          setHaksaDocs([]);
          setErrorMessage(e instanceof Error ? e.message : '?먮즺 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void fetchHaksaDocs();
    return () => {
      cancelled = true;
    };
  }, [haksaKey]);

  return (
    <div className="space-y-4">
      <div className="p-4 bg-blue-50 border border-blue-200 text-blue-800 rounded-lg text-sm">
        ?숈궗 怨쇰ぉ???먮즺??<b>媛뺤쓽紐⑹감</b>?먯꽌 二쇱감/李⑥떆???깅줉????ぉ??湲곗??쇰줈 ?쒖떆?⑸땲??
      </div>

      {errorMessage && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">{errorMessage}</div>
      )}

      {loading && <div className="p-6 text-center text-gray-500">?먮즺 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>}

      {!loading && haksaDocs.length > 0 ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-medium text-gray-900">?깅줉???먮즺</h3>
            <span className="px-3 py-1 bg-green-100 text-green-700 text-sm rounded-full">珥?{haksaDocs.length}媛?</span>
          </div>

          {haksaDocs.map((doc: any) => (
            <div
              key={doc.id}
              className="p-4 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors"
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <FolderOpen className="w-5 h-5 text-green-600" />
                    <span className="font-medium text-gray-900">{doc.title || '?숈뒿?먮즺'}</span>
                    <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">
                      {doc.weekNumber || 1}二쇱감
                    </span>
                  </div>
                  {doc.description && <p className="text-sm text-gray-500 mt-1 ml-7">{doc.description}</p>}
                </div>

                {doc.createdAt && (
                  <div className="text-sm text-gray-400 flex-shrink-0">
                    {new Date(doc.createdAt).toLocaleDateString('ko-KR')}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        !loading && (
          <div className="p-10 text-center text-gray-500 border border-dashed border-gray-200 rounded-lg">
            ?깅줉???먮즺媛 ?놁뒿?덈떎. 媛뺤쓽紐⑹감?먯꽌 二쇱감蹂꾨줈 ?먮즺瑜?異붽??????덉뒿?덈떎.
          </div>
        )
      )}
    </div>
  );
}

function ExamTab({ courseId, course }: { courseId: number; course?: any }) {
  // ?? ?숈궗 怨쇰ぉ? courseId媛 NaN ?먮뒗 0?대?濡? 鍮??곹깭濡??쒖옉?섏뿬 援먯닔?먭? 吏곸젒 異붽??????덈룄濡??⑸땲??
  const isHaksaCourse =
    course?.sourceType === 'haksa' && (!course?.mappedCourseId || Number.isNaN(courseId) || courseId <= 0);
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

  const [selectedExam, setSelectedExam] = useState<number | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [exams, setExams] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // ?숈궗 怨쇰ぉ??寃쎌슦 濡쒖뺄?ㅽ넗由ъ??먯꽌 ?쒗뿕 紐⑸줉 遺덈윭?ㅺ린
  const [haksaExams, setHaksaExams] = useState<any[]>([]);
  const [haksaExamsLoaded, setHaksaExamsLoaded] = useState(false);
  const latestHaksaExamsRef = useRef<any[]>([]);
  const latestHaksaKeyRef = useRef(haksaKey);

  // ?꾨━利?怨쇰ぉ ?쒗뿕 ?섏젙 愿???곹깭
  const [editingPrismExam, setEditingPrismExam] = useState<any | null>(null);
  const [prismExamSettings, setPrismExamSettings] = useState<Record<number, any>>({});

  // ?ㅻ뒛 ?좎쭨 湲곕낯媛?
  const today = new Date().toISOString().split('T')[0];
  const nextWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

  const [examEditSettings, setExamEditSettings] = useState({
    startDate: today,
    startTime: '09:00',
    endDate: nextWeek,
    endTime: '18:00',
    points: 0,
    allowRetake: false,
    retakeScore: 0,
    retakeCount: 0,
    showResults: true,
  });

  useEffect(() => {
    if (!isHaksaCourse || !haksaKey) return;
    let cancelled = false;

    const fetchHaksaExams = async () => {
      setHaksaExamsLoaded(false);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getHaksaExams(haksaKey);
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        // ?? DataSet ?묐떟??諛곗뿴濡??????덉뼱 泥?踰덉㎏ ?됱쓣 湲곗??쇰줈 ?댁꽍?⑸땲??
        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        const raw = payload?.exams_json || '';
        if (raw) {
          const parsed = JSON.parse(raw);
          if (!cancelled) setHaksaExams(Array.isArray(parsed) ? parsed : []);
          return;
        }

        // ?? 湲곗〈 濡쒖뺄?ㅽ넗由ъ? ?곗씠?곕? DB濡??댁쟾?⑸땲???댁쟾 ?곗씠???먯떎 諛⑹?).
        if (course?.id) {
          try {
            const saved = localStorage.getItem(`haksa_exams_${course.id}`);
            if (saved) {
              const parsed = JSON.parse(saved);
              if (!cancelled) setHaksaExams(Array.isArray(parsed) ? parsed : []);
              await tutorLmsApi.updateHaksaExams({
                ...haksaKey,
                examsJson: saved,
              });
            }
          } catch {
            if (!cancelled) setHaksaExams([]);
          }
        }
      } catch (e) {
        if (!cancelled) {
          setErrorMessage(e instanceof Error ? e.message : '?쒗뿕 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      } finally {
        if (!cancelled) setHaksaExamsLoaded(true);
      }
    };

    void fetchHaksaExams();
    return () => {
      cancelled = true;
    };
  }, [isHaksaCourse, haksaKey, course?.id]);
  useEffect(() => {
    if (isHaksaCourse && !haksaKey) {
      setErrorMessage('?숈궗 怨쇰ぉ ?ㅺ? 鍮꾩뼱 ?덉뼱 ?쒗뿕 ???議고쉶媛 遺덇??ν빀?덈떎.');
    }
  }, [isHaksaCourse, haksaKey]);
  useEffect(() => {
    latestHaksaExamsRef.current = haksaExams;
  }, [haksaExams]);
  useEffect(() => {
    latestHaksaKeyRef.current = haksaKey;
  }, [haksaKey]);
  useEffect(() => {
    return () => {
      if (!isHaksaCourse || !latestHaksaKeyRef.current || !haksaExamsLoaded) return;
      // ?? ???대룞/?몃쭏?댄듃 ??留덉?留??곹깭 ??μ씠 ?꾨씫?????덉뼱 ??踰???蹂댁옣?⑸땲??
      void tutorLmsApi.updateHaksaExams({
        ...latestHaksaKeyRef.current,
        examsJson: JSON.stringify(latestHaksaExamsRef.current),
      });
    };
  }, [isHaksaCourse, haksaExamsLoaded]);

  // ?꾨━利?怨쇰ぉ ?쒗뿕 ?ㅼ젙 濡쒖뺄?ㅽ넗由ъ??먯꽌 遺덈윭?ㅺ린
  useEffect(() => {
    if (courseId && !isHaksaCourse) {
      try {
        const saved = localStorage.getItem(`prism_exam_settings_${courseId}`);
        if (saved) {
          setPrismExamSettings(JSON.parse(saved));
        }
      } catch {
        setPrismExamSettings({});
      }
    }
  }, [courseId, isHaksaCourse]);

  const fetchExams = async () => {
    if (!courseId || isHaksaCourse) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getExams({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row) => ({
        id: Number(row.exam_id),
        title: row.exam_nm,
        date: row.start_date_conv || row.start_date || '-',
        duration: row.exam_time ? `${row.exam_time}분` : '-',
        submitted: Number(row.submitted_cnt ?? 0),
        total: Number(row.total_cnt ?? 0),
      }));
      setExams(mapped);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?쒗뿕 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isHaksaCourse) void fetchExams();
  }, [courseId, isHaksaCourse]);

  // ?꾨━利??쒗뿕 ?섏젙 ?쒖옉
  const handleEditPrismExam = (exam: any) => {
    const settings = prismExamSettings[exam.id] || {};
    setEditingPrismExam(exam);
    setExamEditSettings({
      startDate: settings.startDate || today,
      startTime: settings.startTime || '09:00',
      endDate: settings.endDate || nextWeek,
      endTime: settings.endTime || '18:00',
      points: settings.points || 0,
      allowRetake: settings.allowRetake || false,
      retakeScore: settings.retakeScore || 0,
      retakeCount: settings.retakeCount || 0,
      showResults: settings.showResults !== false,
    });
  };

  // ?꾨━利??쒗뿕 ?섏젙 ???
  const handleSavePrismExamEdit = () => {
    if (!editingPrismExam) return;

    const updated = {
      ...prismExamSettings,
      [editingPrismExam.id]: { ...examEditSettings },
    };
    setPrismExamSettings(updated);

    // 濡쒖뺄?ㅽ넗由ъ? ???
    if (courseId) {
      try {
        localStorage.setItem(`prism_exam_settings_${courseId}`, JSON.stringify(updated));
      } catch {}
    }

    setEditingPrismExam(null);
  };

  // ?꾨━利??쒗뿕 ??젣
  const handleDeletePrismExam = async (examId: number) => {
    if (!confirm('???쒗뿕????젣?섏떆寃좎뒿?덇퉴?')) return;
    
    try {
      const res = await tutorLmsApi.deleteExam({ courseId, examId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      
      // 濡쒖뺄?ㅽ넗由ъ??먯꽌 ?ㅼ젙 ??젣
      const updated = { ...prismExamSettings };
      delete updated[examId];
      setPrismExamSettings(updated);
      if (courseId) {
        try {
          localStorage.setItem(`prism_exam_settings_${courseId}`, JSON.stringify(updated));
        } catch {}
      }
      
      await fetchExams();
      alert('?쒗뿕????젣?섏뿀?듬땲??');
    } catch (e) {
      alert(e instanceof Error ? e.message : '?쒗뿕 ??젣 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    }
  };

  // ?? ?숈궗 怨쇰ぉ??寃쎌슦 ?쒗뿕愿由ъ뿉???깅줉???쒗뿕???쒖떆?섍퀬 異붽??????덉뒿?덈떎.
  if (isHaksaCourse) {
    return (
      <HaksaExamContent
        haksaKey={haksaKey}
        haksaExams={haksaExams}
        setHaksaExams={setHaksaExams}
        weekCount={getHaksaWeekCount(course)}
      />
    );
  }



  if (selectedExam !== null) {
    const exam = exams.find((e) => e.id === selectedExam);

    return (
      <ExamDetailView
        courseId={courseId}
        examId={selectedExam}
        exam={exam}
        onBack={() => setSelectedExam(null)}
        onRefresh={() => void fetchExams()}
      />
    );
  }

  // ?꾨━利?怨쇰ぉ: ?쒗뿕愿由?紐⑸줉?먯꽌 ?좏깮?섎뒗 紐⑤떖 ?쒖떆
  return (
    <>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-medium text-gray-900">?깅줉???쒗뿕</h3>
          <button 
            onClick={() => setShowCreateModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>?쒗뿕 異붽?</span>
          </button>
        </div>
        {errorMessage && (
          <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
            {errorMessage}
          </div>
        )}

        {loading && (
          <div className="p-6 text-center text-gray-500">?쒗뿕 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>
        )}

        {!loading && exams.length > 0 && (
          <div className="space-y-3">
            {exams.map((exam) => {
              const settings = prismExamSettings[exam.id] || {};
              return (
                <div
                  key={exam.id}
                  className="p-4 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1 cursor-pointer" onClick={() => setSelectedExam(exam.id)}>
                      <div className="flex items-center gap-2 mb-3">
                        <ClipboardCheck className="w-5 h-5 text-red-600" />
                        <span className="font-medium text-gray-900">{exam.title}</span>
                        <span className="px-2 py-0.5 bg-purple-100 text-purple-700 text-xs rounded">
                          {exam.submitted}/{exam.total}紐??쒖텧
                        </span>
                      </div>
                      
                      {/* ?ㅼ젙 ??ぉ???뚯씠釉??뺥깭濡??쒖떆 */}
                      <div className="ml-7 text-sm space-y-2 bg-gray-50 p-3 rounded-lg">
                        <div className="flex items-center">
                          <span className="w-24 text-gray-500">?묒떆湲곌컙</span>
                          <span className="text-gray-900">
                            {settings.startDate || exam.date || '-'} {settings.startTime || ''} ~ {settings.endDate || '-'} {settings.endTime || ''}
                          </span>
                        </div>
                        <div className="flex items-center">
                          <span className="w-24 text-gray-500">?쒗뿕?쒓컙</span>
                          <span className="text-gray-900">{exam.duration}</span>
                        </div>
                        <div className="flex items-center">
                          <span className="w-24 text-gray-500">諛곗젏</span>
                          <span className="text-gray-900">{settings.points || 0}??</span>
                        </div>
                        <div className="flex items-center">
                          <span className="w-24 text-gray-500">?ъ쓳??媛??</span>
                          <span className="text-gray-900">
                            {settings.allowRetake ? (
                              <>媛??({settings.retakeScore}??誘몃쭔, {settings.retakeCount}??</>
                            ) : '遺덇?'}
                          </span>
                        </div>
                        <div className="flex items-center">
                          <span className="w-24 text-gray-500">?쒗뿕寃곌낵?몄텧</span>
                          <span className="text-gray-900">{settings.showResults !== false ? '노출' : '비노출'}</span>
                        </div>
                      </div>
                    </div>
                    
                    <div className="flex gap-1 ml-4">
                      <button
                        onClick={(e) => { e.stopPropagation(); handleEditPrismExam(exam); }}
                        className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        title="?섏젙"
                      >
                        <Edit className="w-4 h-4" />
                      </button>
                      <button
                        onClick={(e) => { e.stopPropagation(); handleDeletePrismExam(exam.id); }}
                        className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                        title="??젣"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {!loading && exams.length === 0 && (
          <div className="text-center text-gray-500 py-12 border border-dashed border-gray-300 rounded-lg">
            <ClipboardCheck className="w-12 h-12 mx-auto text-gray-300 mb-3" />
            <p className="mb-2">?깅줉???쒗뿕???놁뒿?덈떎.</p>
            <p className="text-sm text-gray-400">?쒗뿕 異붽? 踰꾪듉???뚮윭 ?쒗뿕愿由ъ뿉??留뚮뱺 ?쒗뿕???깅줉?섏꽭??</p>
          </div>
        )}
      </div>
      
      <ExamSelectModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        weekCount={getHaksaWeekCount(course)}
        showWeekSession={course?.sourceType === 'haksa'}
        onSave={async (examData) => {
          try {
            // ?? 湲곗〈 ?쒗뿕??怨쇰ぉ???곌껐留??⑸땲??(?쒗뿕 蹂듭궗 ?놁쓬)
            const startDateTime = (examData.startDate || new Date().toISOString().split('T')[0]).replace(/-/g, '') + '090000';
            const endDateTime = (examData.endDate || new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]).replace(/-/g, '') + '180000';
            
            const res = await tutorLmsApi.linkExam({
              courseId,
              examId: examData.examId,
              startDate: startDateTime,
              endDate: endDateTime,
              assignScore: examData.points || 100,
              allowRetake: examData.allowRetake || false,
              showResults: examData.showResults !== false,
            });
            if (res.rst_code !== '0000') throw new Error(res.rst_message);

            if (course?.sourceType === 'haksa') {
              try {
                await appendHaksaCurriculumContent({
                  haksaKey,
                  weekNumber: examData.weekNumber,
                  sessionNumber: examData.sessionNumber,
                  content: {
                    type: 'exam',
                    title: examData.title,
                    description: examData.description || '',
                    examId: String(examData.examId),
                    examSettings: {
                      points: examData.points,
                      allowRetake: examData.allowRetake,
                      retakeScore: examData.retakeScore,
                      retakeCount: examData.retakeCount,
                      showResults: examData.showResults,
                      startDate: examData.startDate,
                      endDate: examData.endDate,
                    },
                  },
                });
              } catch (e) {
                alert(e instanceof Error ? e.message : '媛뺤쓽紐⑹감???쒗뿕???깅줉?섎뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
              }
            }

            // ?쒗뿕 ?ㅼ젙 ???
            const newSettings = {
              ...prismExamSettings,
              [examData.examId]: {
                startDate: examData.startDate,
                startTime: '09:00',
                endDate: examData.endDate,
                endTime: '18:00',
                points: examData.points,
                allowRetake: examData.allowRetake,
                retakeScore: examData.retakeScore,
                retakeCount: examData.retakeCount,
                showResults: examData.showResults,
                weekNumber: examData.weekNumber,
                sessionNumber: examData.sessionNumber,
              },
            };
            setPrismExamSettings(newSettings);
            try {
              localStorage.setItem(`prism_exam_settings_${courseId}`, JSON.stringify(newSettings));
            } catch {}

            await fetchExams();
            alert('?쒗뿕???깅줉?섏뿀?듬땲??');
          } catch (e) {
            alert(e instanceof Error ? e.message : '?쒗뿕 ?깅줉 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
          }
        }}
      />

      {/* ?꾨━利??쒗뿕 ?섏젙 紐⑤떖 */}
      {editingPrismExam && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setEditingPrismExam(null)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">?쒗뿕 ?섏젙</h3>
              <button
                onClick={() => setEditingPrismExam(null)}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
              >
                횞
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* ?쒗뿕 ?쒕ぉ (?섏젙 遺덇?) */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">?쒗뿕 ?좏깮</label>
                <div className="px-4 py-2.5 bg-gray-100 border border-gray-300 rounded-lg text-gray-700">
                  {editingPrismExam.title}
                </div>
              </div>

              {/* ?쒗뿕 ?곸꽭 ?ㅼ젙 */}
              <div className="space-y-4 pt-4 border-t border-gray-100">
                {/* ?묒떆 媛??湲곌컙 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">?묒떆 媛??湲곌컙</label>
                  <div className="flex items-center gap-2 flex-wrap">
                    <input
                      type="date"
                      value={examEditSettings.startDate}
                      onChange={(e) => setExamEditSettings(prev => ({ ...prev, startDate: e.target.value }))}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <input
                      type="time"
                      value={examEditSettings.startTime}
                      onChange={(e) => setExamEditSettings(prev => ({ ...prev, startTime: e.target.value }))}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-gray-500">遺??</span>
                    <input
                      type="date"
                      value={examEditSettings.endDate}
                      onChange={(e) => setExamEditSettings(prev => ({ ...prev, endDate: e.target.value }))}
                      min={examEditSettings.startDate}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <input
                      type="time"
                      value={examEditSettings.endTime}
                      onChange={(e) => setExamEditSettings(prev => ({ ...prev, endTime: e.target.value }))}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-gray-500">源뚯?</span>
                  </div>
                </div>

                {/* 諛곗젏 */}
                <div className="flex items-center gap-3">
                  <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">諛곗젏</label>
                  <input
                    type="number"
                    value={examEditSettings.points}
                    onChange={(e) => setExamEditSettings(prev => ({ ...prev, points: parseInt(e.target.value) || 0 }))}
                    min={0}
                    className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-600">??</span>
                </div>

                {/* ?ъ쓳??媛?μ뿬遺 */}
                <div className="flex items-center gap-3">
                  <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛?μ뿬遺</label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={examEditSettings.allowRetake}
                      onChange={(e) => setExamEditSettings(prev => ({ ...prev, allowRetake: e.target.checked }))}
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">?ъ쓳??媛??</span>
                  </label>
                  <span className="text-xs text-gray-400">???ъ쓳?쒕? 吏?뺥븯硫?湲곗??먯닔 誘몃쭔??寃쎌슦 ?잛닔?쒗븳 踰붿쐞?덉뿉???ъ쓳?쒗븷 ???덉뒿?덈떎.</span>
                </div>

                {/* ?ъ쓳??湲곗? ?먯닔 */}
                {examEditSettings.allowRetake && (
                  <>
                    <div className="flex items-center gap-3">
                      <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??湲곗? ?먯닔</label>
                      <input
                        type="number"
                        value={examEditSettings.retakeScore}
                        onChange={(e) => setExamEditSettings(prev => ({ ...prev, retakeScore: parseInt(e.target.value) || 0 }))}
                        min={0}
                        max={100}
                        className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-sm text-gray-600">??誘몃쭔?쇰븣 ?ъ쓳?쒓? 媛?ν빀?덈떎.</span>
                      <span className="text-xs text-gray-400">??100??留뚯젏 湲곗??낅땲??</span>
                    </div>

                    <div className="flex items-center gap-3">
                      <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛???잛닔</label>
                      <input
                        type="number"
                        value={examEditSettings.retakeCount}
                        onChange={(e) => setExamEditSettings(prev => ({ ...prev, retakeCount: parseInt(e.target.value) || 0 }))}
                        min={0}
                        className="w-16 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-sm text-gray-600">?뚭퉴吏 ?ъ쓳?쒓? 媛?ν빀?덈떎.</span>
                    </div>
                  </>
                )}

                {/* ?쒗뿕寃곌낵?몄텧 */}
                <div className="flex items-center gap-3">
                  <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?쒗뿕寃곌낵?몄텧</label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={examEditSettings.showResults}
                      onChange={(e) => setExamEditSettings(prev => ({ ...prev, showResults: e.target.checked }))}
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">?몄텧</span>
                  </label>
                  <span className="text-xs text-gray-400">???묒떆 ???섍컯?앹씠 ?뺣떟???뺤씤?????덉뒿?덈떎.</span>
                </div>
              </div>
            </div>

            <div className="sticky bottom-0 bg-white border-t border-gray-200 px-6 py-4 flex gap-3">
              <button
                onClick={() => setEditingPrismExam(null)}
                className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                痍⑥냼
              </button>
              <button
                onClick={handleSavePrismExamEdit}
                className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                ?쒗뿕?섏젙
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

// ?쒗뿕 ?곸꽭 ?붾㈃
function ExamDetailView({
  courseId,
  examId,
  exam,
  onBack,
  onRefresh,
}: {
  courseId: number;
  examId: number;
  exam: any;
  onBack: () => void;
  onRefresh: () => void;
}) {
  const [editingScore, setEditingScore] = useState<number | null>(null);
  const [tempScore, setTempScore] = useState<string>('');
  const [studentScores, setStudentScores] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const examTitle = exam?.title ?? '?쒗뿕';
  const examDate = exam?.date ?? '-';
  const examDuration = exam?.duration ?? '-';

  const fetchExamUsers = async () => {
    if (!courseId || !examId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getExamUsers({ courseId, examId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        courseUserId: Number(row.course_user_id),
        studentId: row.login_id,
        name: row.user_nm,
        score: Number(row.marking_score ?? 0),
        submitted: Boolean(row.submitted),
        submittedAt: row.submitted_at ?? '-',
      }));
      setStudentScores(mapped);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?쒖텧 ?꾪솴??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchExamUsers();
  }, [courseId, examId]);

  const submittedScores = studentScores.filter((s) => s.submitted);
  
  // ?듦퀎 怨꾩궛
  const stats = {
    average: submittedScores.length > 0 
      ? Math.round(submittedScores.reduce((sum, s) => sum + s.score, 0) / submittedScores.length * 10) / 10
      : 0,
    highest: submittedScores.length > 0 ? Math.max(...submittedScores.map((s) => s.score)) : 0,
    lowest: submittedScores.length > 0 ? Math.min(...submittedScores.map((s) => s.score)) : 0,
    submitted: submittedScores.length,
    total: studentScores.length,
  };

  // ?먯닔 援ш컙蹂?遺꾪룷 怨꾩궛
  const getScoreDistribution = () => {
    const ranges = [
      { range: '90-100', min: 90, max: 100, count: 0 },
      { range: '80-89', min: 80, max: 89, count: 0 },
      { range: '70-79', min: 70, max: 79, count: 0 },
      { range: '60-69', min: 60, max: 69, count: 0 },
      { range: '0-59', min: 0, max: 59, count: 0 },
    ];

    submittedScores.forEach((student) => {
      const range = ranges.find((r) => student.score >= r.min && student.score <= r.max);
      if (range) range.count++;
    });

    return ranges;
  };

  const distribution = getScoreDistribution();

  // ?묒? ?ㅼ슫濡쒕뱶 ?⑥닔
  const handleDownloadExcel = () => {
    // ?? ?쒕쾭??蹂꾨룄 ?뚯씪 ?앹꽦 湲곕뒫???놁뼱?? ?붾㈃???덈뒗 ?곗씠?곕? CSV濡??대젮諛쏆쓣 ???덉뒿?덈떎.
    const ymd = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const filename = `course_${courseId}_exam_${examId}_${ymd}.csv`;

    const headers = ['No', '?숇쾲', '?대쫫', '?쒖텧?щ?', '?쒖텧?쒓컙', '?먯닔'];
    const rows = studentScores.map((student, index) => ([
      index + 1,
      student.studentId ?? '',
      student.name ?? '',
      student.submitted ? 'Y' : 'N',
      student.submittedAt ?? '',
      student.submitted ? Number(student.score ?? 0) : '',
    ]));

    downloadCsv(filename, headers, rows);
  };

  // ?먯닔 ?섏젙 ?쒖옉
  const handleStartEdit = (courseUserId: number, currentScore: number) => {
    setEditingScore(courseUserId);
    setTempScore(currentScore.toString());
  };

  // ?먯닔 ?섏젙 ???
  const handleSaveScore = async (courseUserId: number) => {
    const newScore = parseInt(tempScore, 10);
    if (isNaN(newScore) || newScore < 0 || newScore > 100) {
      alert('?먯닔??0~100 ?ъ씠???レ옄?ъ빞 ?⑸땲??');
      return;
    }

    try {
      const res = await tutorLmsApi.updateExamScore({
        courseId,
        examId,
        courseUserId,
        markingScore: newScore,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      await fetchExamUsers();
      onRefresh();
      setEditingScore(null);
      setTempScore('');
    } catch (e) {
      alert(e instanceof Error ? e.message : '?먯닔 ???以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    }
  };

    // ?먯닔 ?섏젙 痍⑥냼
    const handleCancelEdit = () => {
      setEditingScore(null);
      setTempScore('');
    };

    // ?? ?쒖텧???쒗뿕??痍⑥냼?섎㈃ ?묒떆 湲곕줉????젣?섍퀬 ?깆쟻???ш퀎?곕맗?덈떎.
    const handleCancelSubmit = async (courseUserId: number) => {
      if (!confirm('?대떦 ?숈깮???쒗뿕 ?묒떆瑜?痍⑥냼?섏떆寃좎뒿?덇퉴?')) return;

      try {
        const res = await tutorLmsApi.cancelExamSubmit({ courseId, examId, courseUserId });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchExamUsers();
        onRefresh();
        alert('?묒떆媛 痍⑥냼?섏뿀?듬땲??');
      } catch (e) {
        alert(e instanceof Error ? e.message : '?묒떆 痍⑥냼 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      }
    };

  return (
    <div className="space-y-6">
      {/* ?ㅻ뜑 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h3 className="text-xl text-gray-900">{examTitle}</h3>
            <p className="text-sm text-gray-600">
              ?쒗뿕?? {examDate} 쨌 ?쒗뿕?쒓컙: {examDuration}
            </p>
          </div>
        </div>
        <button
          onClick={handleDownloadExcel}
          className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
        >
          <Download className="w-4 h-4" />
          <span>?묒? ?ㅼ슫濡쒕뱶</span>
        </button>
      </div>

      {errorMessage && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {errorMessage}
        </div>
      )}

      {loading && (
        <div className="p-6 text-center text-gray-500">?쒖텧 ?꾪솴??遺덈윭?ㅻ뒗 以?..</div>
      )}

      {/* ?듦퀎 移대뱶 */}
      <div className="grid grid-cols-5 gap-4">
        <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <div className="text-sm text-blue-700 mb-1">?됯퇏 ?먯닔</div>
          <div className="text-2xl text-blue-900">{stats.average}??</div>
        </div>
        <div className="p-4 bg-green-50 border border-green-200 rounded-lg">
          <div className="text-sm text-green-700 mb-1">理쒓퀬 ?먯닔</div>
          <div className="text-2xl text-green-900">{stats.highest}??</div>
        </div>
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
          <div className="text-sm text-red-700 mb-1">理쒖? ?먯닔</div>
          <div className="text-2xl text-red-900">{stats.lowest}??</div>
        </div>
        <div className="p-4 bg-purple-50 border border-purple-200 rounded-lg">
          <div className="text-sm text-purple-700 mb-1">?쒖텧 ?몄썝</div>
          <div className="text-2xl text-purple-900">{stats.submitted}紐?</div>
        </div>
        <div className="p-4 bg-gray-50 border border-gray-200 rounded-lg">
          <div className="text-sm text-gray-700 mb-1">誘몄젣異??몄썝</div>
          <div className="text-2xl text-gray-900">{stats.total - stats.submitted}紐?</div>
        </div>
      </div>

      {/* ?먯닔 遺꾪룷 */}
      <div className="border border-gray-200 rounded-lg p-6">
        <h4 className="text-gray-900 mb-4">?먯닔 遺꾪룷</h4>
        <div className="space-y-3">
          {distribution.map((item) => (
            <div key={item.range}>
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm text-gray-700">{item.range}??</span>
                <span className="text-sm text-gray-900">{item.count}紐?</span>
              </div>
              <div className="w-full h-8 bg-gray-200 rounded-lg overflow-hidden">
                <div
                  className="h-full bg-blue-600 flex items-center justify-end px-2"
                  style={{
                    width: stats.submitted > 0 ? `${(item.count / stats.submitted) * 100}%` : '0%',
                  }}
                >
                  {item.count > 0 && (
                    <span className="text-xs text-white">
                      {Math.round((item.count / stats.submitted) * 100)}%
                    </span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ?숈깮蹂??먯닔 ?뚯씠釉?*/}
      <div className="border border-gray-200 rounded-lg">
        <div className="bg-gray-50 px-4 py-3 border-b border-gray-200 flex items-center justify-between">
          <h4 className="text-gray-900">?숈깮蹂??먯닔</h4>
          <p className="text-sm text-gray-600">?먯닔瑜??대┃?섏뿬 ?섏젙?????덉뒿?덈떎</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm text-gray-700">No</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">?숇쾲</th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">?대쫫</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?먯닔</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?쒖텧 ?곹깭</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?쒖텧 ?쒓컙</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">梨꾩젏</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {studentScores.map((student, index) => (
                <tr key={student.courseUserId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-4 text-sm text-gray-900">{index + 1}</td>
                  <td className="px-4 py-4 text-sm text-gray-900">{student.studentId}</td>
                  <td className="px-4 py-4 text-sm text-gray-900">{student.name}</td>
                  <td className="px-4 py-4 text-center">
                    {student.submitted ? (
                      editingScore === student.courseUserId ? (
                        <div className="flex items-center justify-center gap-2">
                          <input
                            type="number"
                            min="0"
                            max="100"
                            value={tempScore}
                            onChange={(e) => setTempScore(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                handleSaveScore(student.courseUserId);
                              } else if (e.key === 'Escape') {
                                handleCancelEdit();
                              }
                            }}
                            className="w-16 px-2 py-1 border border-blue-500 rounded text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                            autoFocus
                          />
                          <button
                            onClick={() => handleSaveScore(student.courseUserId)}
                            className="p-1 text-green-600 hover:bg-green-100 rounded transition-colors"
                            title="저장"
                          >
                            <CheckCircle className="w-4 h-4" />
                          </button>
                          <button
                            onClick={handleCancelEdit}
                            className="p-1 text-red-600 hover:bg-red-100 rounded transition-colors"
                            title="痍⑥냼"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ) : (
                        <button
                          onClick={() => handleStartEdit(student.courseUserId, student.score)}
                          className={`inline-flex items-center justify-center px-3 py-1 rounded-lg hover:opacity-80 transition-opacity ${
                            student.score >= 90
                              ? 'bg-green-100 text-green-700'
                              : student.score >= 80
                              ? 'bg-blue-100 text-blue-700'
                              : student.score >= 70
                              ? 'bg-yellow-100 text-yellow-700'
                              : 'bg-red-100 text-red-700'
                          }`}
                        >
                          {student.score}??
                        </button>
                      )
                    ) : (
                      <span className="text-sm text-gray-400">-</span>
                    )}
                  </td>
                  <td className="px-4 py-4 text-center">
                    {student.submitted ? (
                      <span className="inline-flex items-center gap-1 px-2 py-1 bg-green-100 text-green-700 rounded text-xs">
                        <CheckCircle className="w-3 h-3" />
                        ?쒖텧 ?꾨즺
                      </span>
                    ) : (
                      <span className="inline-flex items-center px-2 py-1 bg-red-100 text-red-700 rounded text-xs">
                        誘몄젣異?
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-4 text-center text-sm text-gray-600">
                    {student.submittedAt}
                  </td>
                    <td className="px-4 py-4 text-center">
                      {student.submitted && editingScore !== student.courseUserId && (
                        <div className="flex items-center justify-center gap-1">
                          <button
                            onClick={() => handleStartEdit(student.courseUserId, student.score)}
                            className="p-1 text-blue-600 hover:bg-blue-100 rounded transition-colors"
                            title="?먯닔 ?섏젙"
                          >
                            <Edit className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleCancelSubmit(student.courseUserId)}
                            className="p-1 text-red-600 hover:bg-red-100 rounded transition-colors"
                            title="?묒떆 痍⑥냼"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
        </div>
      </div>
    </div>
  );
}

// 怨쇱젣 ??
function AssignmentTab({ courseId, course }: { courseId: number; course?: any }) {
  const [subTab, setSubTab] = useState<'management' | 'feedback'>('management');

  return (
    <div className="space-y-4">
      {/* ?섏쐞 ???ㅻ퉬寃뚯씠??*/}
      <div className="flex gap-2 border-b border-gray-200">
        <button
          onClick={() => setSubTab('management')}
          className={`px-4 py-2 transition-colors ${
            subTab === 'management'
              ? 'border-b-2 border-blue-600 text-blue-600'
              : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          怨쇱젣 愿由?
        </button>
        <button
          onClick={() => setSubTab('feedback')}
          className={`px-4 py-2 transition-colors ${
            subTab === 'feedback'
              ? 'border-b-2 border-blue-600 text-blue-600'
              : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          ?쇰뱶諛?愿由?
        </button>
      </div>

      {/* ?섏쐞 ??肄섑뀗痢?*/}
      {subTab === 'management' && <AssignmentManagementTab courseId={courseId} course={course} />}
      {subTab === 'feedback' && <AssignmentFeedbackTab courseId={courseId} />}
    </div>
  );
}

// 怨쇱젣 愿由??섏쐞 ??
function AssignmentManagementTab({ courseId, course }: { courseId: number; course?: any }) {
  const [showCreateModal, setShowCreateModal] = useState(false);
  // ?? 怨쇱젣 ?섏젙 紐⑤떖 ?몄텧 ?щ?? ?섏젙 ????곗씠?곕? 遺꾨━?댁꽌 愿由ы빀?덈떎.
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingHomework, setEditingHomework] = useState<any | null>(null);
  // ?? 怨쇱젣 ?곸꽭 蹂닿린 紐⑤떖 ?곹깭瑜?愿由ы빀?덈떎.
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [selectedHomework, setSelectedHomework] = useState<any | null>(null);
const courseIdNum = Number(course?.mappedCourseId ?? courseId);
const isHaksaCourse =
  course?.sourceType === 'haksa' &&
  (!course?.mappedCourseId || Number.isNaN(courseIdNum) || courseIdNum <= 0);
  const weekCount = getHaksaWeekCount(course);
  const showWeekSession = course?.sourceType === 'haksa';

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

  const [homeworks, setHomeworks] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // ?숈궗 怨쇰ぉ??寃쎌슦 濡쒖뺄?ㅽ넗由ъ??먯꽌 怨쇱젣 紐⑸줉 遺덈윭?ㅺ린
  const [haksaAssignments, setHaksaAssignments] = useState<any[]>([]);
  const [haksaLoading, setHaksaLoading] = useState(false);

  useEffect(() => {
    if (!isHaksaCourse || !haksaKey) return;
    let cancelled = false;

    const fetchHaksaAssignments = async () => {
      setHaksaLoading(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getHaksaCurriculum(haksaKey);
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        // ?? Malgn DataSet ?묐떟??諛곗뿴濡??????덉뼱 泥?踰덉㎏ ?됱쓣 湲곗??쇰줈 ?댁꽍?⑸땲??
        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        const raw = payload?.curriculum_json || '';
        if (!raw) {
          if (!cancelled) setHaksaAssignments([]);
          return;
        }
        const parsed = JSON.parse(raw);
        const contents = Array.isArray(parsed)
          ? parsed.flatMap((w: any) => (w.sessions || []).flatMap((s: any) => s.contents || []))
          : [];
        const assignmentContents = contents.filter((c: any) => {
          const t = String(c.type || '').toLowerCase();
          return t === 'assignment' || t === 'homework';
        });
        if (!cancelled) setHaksaAssignments(assignmentContents);
      } catch (e) {
        if (!cancelled) {
          setHaksaAssignments([]);
          setErrorMessage(e instanceof Error ? e.message : '怨쇱젣 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      } finally {
        if (!cancelled) setHaksaLoading(false);
      }
    };

    void fetchHaksaAssignments();
    return () => {
      cancelled = true;
    };
  }, [isHaksaCourse, haksaKey]);

  // ?? 怨쇱젣 ??? "?덈줈怨좎묠?대룄 ?좎??섎뒗 ?ㅻ뜲?댄꽣"媛 ?듭떖?대씪?? ?붾㈃?????뚮쭏??DB(?쒕쾭)?먯꽌 ?ㅼ떆 ?쎌뼱?듬땲??
  const fetchHomeworks = async () => {
    if (!courseId || isHaksaCourse) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getHomeworks({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        id: Number(row.homework_id),
        title: row.homework_nm || row.module_nm || '怨쇱젣',
        description: row.content || '',
        startDate: row.start_date_conv || row.start_date || '-',
        dueDate: row.end_date_conv || row.end_date || '-',
        totalScore: Number(row.assign_score ?? 100),
        submitted: Number(row.submitted_cnt ?? 0),
        total: Number(row.total_cnt ?? 0),
        homeworkFile: row.homework_file || '',
        submitFileExtMode: String(row.submit_file_ext_mode || 'ALL'),
        submitFileExts: String(row.submit_file_exts || ''),
        allowLateSubmission:
          row.allowLateSubmission === true ||
          row.allowLateSubmission === 'true' ||
          row.allow_late_submission_yn === 'Y',
        latePenalty: Number(row.latePenalty ?? row.late_penalty ?? 0),
      }));
      setHomeworks(mapped);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '怨쇱젣 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isHaksaCourse) void fetchHomeworks();
  }, [courseId, isHaksaCourse]);

  // ?숈궗 怨쇰ぉ??寃쎌슦 媛뺤쓽紐⑹감?먯꽌 ?깅줉??怨쇱젣 ?쒖떆
  if (isHaksaCourse) {
    return (
      <div className="space-y-4">
        {errorMessage && (
          <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
            {errorMessage}
          </div>
        )}

        {haksaLoading && (
          <div className="p-6 text-center text-gray-500">怨쇱젣 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>
        )}

        {haksaAssignments.length > 0 ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-medium text-gray-900">?깅줉??怨쇱젣</h3>
              <span className="px-3 py-1 bg-purple-100 text-purple-700 text-sm rounded-full">
                珥?{haksaAssignments.length}媛?
              </span>
            </div>
            {haksaAssignments.map((assignment: any) => (
              <div
                key={assignment.id}
                className="p-4 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors"
              >
                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <BookOpen className="w-5 h-5 text-purple-600" />
                      <span className="font-medium text-gray-900">{assignment.title}</span>
                      <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">
                        {assignment.weekNumber}二쇱감
                      </span>
                    </div>
                    {assignment.description && (
                      <p className="text-sm text-gray-500 mt-1 ml-7">{assignment.description}</p>
                    )}
                  </div>
                  <div className="text-sm text-gray-400">
                    {new Date(assignment.createdAt).toLocaleDateString('ko-KR')}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center text-gray-500 py-12 border border-dashed border-gray-300 rounded-lg">
            <BookOpen className="w-12 h-12 mx-auto text-gray-300 mb-3" />
            <p className="mb-2">?깅줉??怨쇱젣媛 ?놁뒿?덈떎.</p>
            <p className="text-sm text-gray-400">媛뺤쓽紐⑹감?먯꽌 二쇱감蹂꾨줈 怨쇱젣瑜?異붽??????덉뒿?덈떎.</p>
          </div>
        )}
      </div>
    );
  }

  const parseDateTimeInput = (value: string, defaultTime: string) => {
    const digits = String(value || '').replace(/\D/g, '');
    let date = '';
    let time = defaultTime;
    if (digits.length >= 8) {
      date = `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
    }
    if (digits.length >= 12) {
      time = `${digits.slice(8, 10)}:${digits.slice(10, 12)}`;
    }
    return { date, time };
  };

  // ?? 怨쇱젣 ?섏젙???쒖옉?섎㈃ 紐⑤떖???닿퀬 湲곗〈 ?곗씠?곕? 梨꾩썎?덈떎.
  const handleEditHomework = (homework: any) => {
    // ?? API ?묐떟??yyyyMMddHHmmss / yyyy.MM.dd HH:mm / yyyy-MM-dd HH:mm 泥섎읆 ?욎뿬 ?ㅼ뼱?????덉뼱
    //      ?レ옄留?異붿텧?댁꽌 ?낅젰???좎쭨/?쒓컙?쇰줈 蹂?섑빀?덈떎.
    const parsedStart = parseDateTimeInput(homework.startDate || '', '00:00');
    const parsedEnd = parseDateTimeInput(homework.dueDate || '', '23:59');
    
    setEditingHomework({
      id: homework.id,
      title: homework.title || '',
      description: homework.description || '',
      startDate: parsedStart.date,
      startTime: parsedStart.time || '00:00',
      dueDate: parsedEnd.date,
      dueTime: parsedEnd.time || '23:59',
      totalScore: homework.totalScore || 100,
      fileTypes: homework.submitFileExts || '',
      allowLateSubmission: Boolean(homework.allowLateSubmission),
      latePenalty: Number(homework.latePenalty || 0),
      existingFileName: homework.homeworkFile || '',
    });
    setShowEditModal(true);
  };

  // ?? 怨쇱젣 ?섏젙????ν븯硫??쒕쾭???낅뜲?댄듃?섍퀬 紐⑸줉???덈줈怨좎묠?⑸땲??
  const handleSaveHomeworkEdit = async (data: any) => {
    if (!editingHomework) return;
    
    try {
        const res = await tutorLmsApi.updateHomework({
          courseId,
          homeworkId: editingHomework.id,
          title: data.title,
          description: data.description,
          startDate: data.startDate,
          startTime: data.startTime,
          dueDate: data.dueDate,
          dueTime: data.dueTime,
          totalScore: Number(data.totalScore || 0),
          deleteHomeworkFileYn: Boolean(data.deleteFile),
          submitFileExtMode: data.fileTypes && String(data.fileTypes).trim() ? 'CUSTOM' : 'ALL',
          submitFileExts: data.fileTypes || '',
          allowLateSubmission: Boolean(data.allowLateSubmission),
          latePenalty: Number(data.latePenalty || 0),
          file: data.file,
        });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      
      await fetchHomeworks();
      alert('怨쇱젣媛 ?섏젙?섏뿀?듬땲??');
      setShowEditModal(false);
      setEditingHomework(null);
    } catch (e) {
      alert(e instanceof Error ? e.message : '怨쇱젣 ?섏젙 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    }
  };

  const handleDeleteHomework = (homeworkId: number, title: string) => {
    void (async () => {
      // ?? ?쒖텧/梨꾩젏 ?곗씠?곌? ?대? ?볦씤 怨쇱젣瑜?吏?곕㈃ ?댁쁺 ?곗씠?곌? 源⑥쭏 ???덉뼱?? ?ъ슜?먯뿉寃???踰????뺤씤諛쏆뒿?덈떎.
      const ok = confirm(
        `怨쇱젣 "${title}"??瑜? ??젣?섏떆寃좎뒿?덇퉴?\n\n?대? ?쒖텧 ?댁뿭???덈뒗 寃쎌슦, ?쒕쾭?먯꽌 ??젣媛 李⑤떒?????덉뒿?덈떎.`
      );
      if (!ok) return;

      try {
        const res = await tutorLmsApi.deleteHomework({ courseId, homeworkId });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchHomeworks();
        alert('??젣?섏뿀?듬땲??');
      } catch (e) {
        alert(e instanceof Error ? e.message : '??젣 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      }
    })();
  };

  return (
    <>
      <div className="space-y-4">
        <div className="flex justify-end">
          <button 
            onClick={() => setShowCreateModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Briefcase className="w-4 h-4" />
            <span>怨쇱젣 ?깅줉</span>
          </button>
        </div>

        {errorMessage && (
          <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
            {errorMessage}
          </div>
        )}

        {loading && (
          <div className="p-6 text-center text-gray-500">怨쇱젣 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>
        )}

        {!loading && homeworks.length === 0 && (
          <div className="p-10 text-center text-gray-500 border border-dashed border-gray-200 rounded-lg">
            ?깅줉??怨쇱젣媛 ?놁뒿?덈떎. ?곗륫 ?곷떒?먯꽌 怨쇱젣瑜??깅줉??二쇱꽭??
          </div>
        )}

        {!loading && homeworks.length > 0 && (
          <div className="space-y-3">
            {homeworks.map((assignment) => (
              <div
                key={assignment.id}
                className="p-4 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors cursor-pointer"
                onClick={() => {
                  setSelectedHomework(assignment);
                  setShowDetailModal(true);
                }}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-3">
                      <Briefcase className="w-5 h-5 text-purple-600" />
                      <span className="font-medium text-gray-900">{assignment.title}</span>
                      <span className="px-2 py-0.5 bg-purple-100 text-purple-700 text-xs rounded">
                        {assignment.submitted}/{assignment.total}紐??쒖텧
                      </span>
                    </div>
                    
                    {/* ?ㅼ젙 ??ぉ???뚯씠釉??뺥깭濡??쒖떆 (?쒗뿕怨??숈씪???ㅽ??? */}
                    <div className="ml-7 text-sm space-y-2 bg-gray-50 p-3 rounded-lg">
                      <div className="flex items-center">
                        <span className="w-24 text-gray-500">?쒖텧湲곌컙</span>
                        <span className="text-gray-900">{assignment.startDate || '-'} ~ {assignment.dueDate || '-'}</span>
                      </div>
                      <div className="flex items-center">
                        <span className="w-24 text-gray-500">諛곗젏</span>
                        <span className="text-gray-900">{assignment.totalScore || 0}??</span>
                      </div>
                      <div className="flex items-center">
                        <span className="w-24 text-gray-500">?쒖텧 ?꾪솴</span>
                        <span className="text-gray-900">{assignment.submitted} / {assignment.total}紐?</span>
                      </div>
                      {assignment.homeworkFile && (
                        <div className="flex items-center">
                          <span className="w-24 text-gray-500">泥⑤??뚯씪</span>
                          <span className="flex items-center gap-1.5 text-blue-600">
                            <Paperclip className="w-3.5 h-3.5" />
                            <span className="truncate max-w-[200px]">{assignment.homeworkFile}</span>
                          </span>
                        </div>
                      )}
                    </div>
                  </div>
                  
                  <div className="flex gap-1 ml-4">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleEditHomework(assignment);
                      }}
                      className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                      title="?섏젙"
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteHomework(assignment.id, assignment.title);
                      }}
                      className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      title="??젣"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      
      <AssignmentCreateModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        showWeekSession={showWeekSession}
        weekCount={weekCount}
        onSave={(assignmentData) => {
          void (async () => {
            try {
                const res = await tutorLmsApi.createHomework({
                  courseId,
                  title: assignmentData.title,
                  description: assignmentData.description,
                  startDate: assignmentData.startDate,
                  startTime: assignmentData.startTime,
                  dueDate: assignmentData.dueDate,
                  dueTime: assignmentData.dueTime,
                  totalScore: Number(assignmentData.totalScore || 0),
                  onoffType: 'N',
                  submitFileExtMode: assignmentData.fileTypes && String(assignmentData.fileTypes).trim() ? 'CUSTOM' : 'ALL',
                  submitFileExts: assignmentData.fileTypes || '',
                  allowLateSubmission: Boolean(assignmentData.allowLateSubmission),
                  latePenalty: Number(assignmentData.latePenalty || 0),
                  file: assignmentData.file,
                });
              if (res.rst_code !== '0000') throw new Error(res.rst_message);

              if (course?.sourceType === 'haksa') {
                try {
                  await appendHaksaCurriculumContent({
                    haksaKey,
                    weekNumber: Number(assignmentData.weekNumber || 1),
                    sessionNumber: Number(assignmentData.sessionNumber || 1),
                    content: {
                      type: 'assignment',
                      title: assignmentData.title,
                      description: assignmentData.description,
                      dueDate: assignmentData.dueDate,
                      dueTime: assignmentData.dueTime,
                      totalScore: Number(assignmentData.totalScore || 0),
                    },
                  });
                } catch (e) {
                  alert(e instanceof Error ? e.message : '媛뺤쓽紐⑹감??怨쇱젣瑜??깅줉?섎뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
                }
              }

              await fetchHomeworks();
              alert('怨쇱젣媛 ?깅줉?섏뿀?듬땲??');
            } catch (e) {
              alert(e instanceof Error ? e.message : '怨쇱젣 ?깅줉 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
            }
          })();
        }}
      />
      
      {/* 怨쇱젣 ?섏젙 紐⑤떖 */}
      <AssignmentCreateModal
        isOpen={showEditModal}
        onClose={() => {
          setShowEditModal(false);
          setEditingHomework(null);
        }}
        onSave={handleSaveHomeworkEdit}
        mode="edit"
        showWeekSession={showWeekSession}
        weekCount={weekCount}
        initialData={editingHomework || undefined}
      />

      {/* 怨쇱젣 ?곸꽭 蹂닿린 紐⑤떖 */}
      <AssignmentDetailModal
        isOpen={showDetailModal}
        onClose={() => {
          setShowDetailModal(false);
          setSelectedHomework(null);
        }}
        onEdit={() => {
          if (selectedHomework) {
            handleEditHomework(selectedHomework);
          }
        }}
        assignment={selectedHomework}
      />
    </>
  );
}

// ?쇰뱶諛?愿由??섏쐞 ??
function AssignmentFeedbackTab({ courseId }: { courseId: number }) {
  const DEFAULT_FEEDBACK_TEMPLATES = [
    { id: -1, label: '우수', text: '과제를 매우 충실하게 수행하셨습니다. 우수한 결과입니다.', sort: 1 },
    { id: -2, label: '양호', text: '전반적으로 잘 작성하셨으나 일부 보완이 필요합니다.', sort: 2 },
    { id: -3, label: '보완필요', text: '과제 내용이 부족합니다. 요구사항을 다시 확인하고 보완해 주세요.', sort: 3 },
    { id: -4, label: '지각제출', text: '과제 기한이 지났습니다. 수정 후 지각 제출 바랍니다.', sort: 4 },
    { id: -5, label: '형식오류', text: '제출 파일 형식 또는 양식이 올바르지 않습니다. 확인 후 다시 제출해 주세요.', sort: 5 },
  ];
  const [homeworks, setHomeworks] = useState<any[]>([]);
  const [selectedHomeworkId, setSelectedHomeworkId] = useState<number | null>(null);

  const [students, setStudents] = useState<any[]>([]);
  const [selectedCourseUserId, setSelectedCourseUserId] = useState<number | null>(null);

  const [tempScore, setTempScore] = useState<string>('0');
  const [feedbackText, setFeedbackText] = useState<string>('');
  // ?? 援먯닔?먭? ?덈줈 泥⑤???泥⑥궘 ?뚯씪 紐⑸줉(?꾩쭅 ?낅줈???????곕줈 愿由ы빀?덈떎.
  const [feedbackFiles, setFeedbackFiles] = useState<File[]>([]);
  // ?? ?쒕쾭???대? ??λ맂 ?쇰뱶諛??뚯씪怨?濡쒖뺄 ?좏깮 ?뚯씪??援щ텇?댁꽌 蹂댁뿬以섏빞 ?쇱꽑???놁뒿?덈떎.
  const [uploadedFeedbackFiles, setUploadedFeedbackFiles] = useState<
    { id: number; filename: string; downloadUrl: string }[]
  >([]);
  const [feedbackFileBusy, setFeedbackFileBusy] = useState(false);
  const feedbackFileInputRef = useRef<HTMLInputElement>(null);

  // ?? ?숈깮 ?쒖텧臾??쒕ぉ/?댁슜/泥⑤??뚯씪)??紐⑤떖濡??뺤씤?????덉뼱???⑸땲??
  const [showSubmissionModal, setShowSubmissionModal] = useState(false);
  const [submissionLoading, setSubmissionLoading] = useState(false);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [submissionDetail, setSubmissionDetail] = useState<null | {
    submitted: boolean;
    submittedAt: string;
    subject: string;
    content: string;
    files: { id: number; filename: string; downloadUrl: string }[];
    feedbackFiles: { id: number; filename: string; downloadUrl: string }[];
  }>(null);

  const [loadingHomeworks, setLoadingHomeworks] = useState(false);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<'all' | 'unsubmitted' | 'need_feedback' | 'done'>('all');
  const [feedbackTemplates, setFeedbackTemplates] = useState<
    { id: number; label: string; text: string; sort: number }[]
  >(DEFAULT_FEEDBACK_TEMPLATES);

  const toBool = (value: any) =>
    value === true || value === 1 || value === '1' || value === 'Y' || value === 'true';

  const fetchHomeworkFeedbackFiles = async (homeworkId: number, courseUserId: number) => {
    try {
      const res = await tutorLmsApi.getHomeworkFeedbackFiles({ courseId, homeworkId, courseUserId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
      setUploadedFeedbackFiles(
        rows.map((row: any) => ({
          id: Number(row.id),
          filename: String(row.filename ?? ''),
          downloadUrl: String(row.download_url ?? ''),
        }))
      );
    } catch {
      setUploadedFeedbackFiles([]);
    }
  };

  const fetchFeedbackTemplates = async () => {
    if (!courseId) return;
    try {
      const res = await tutorLmsApi.getHomeworkFeedbackTemplates({ courseId, limit: 20 });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
      if (rows.length === 0) {
        setFeedbackTemplates(DEFAULT_FEEDBACK_TEMPLATES);
        return;
      }
      setFeedbackTemplates(
        rows
          .map((row: any, index: number) => {
            const text = String(row.content ?? '').trim();
            return {
              id: Number(row.id ?? index + 1),
              label: text.length > 10 ? `${text.slice(0, 10)}...` : text || `?쒗뵆由?${index + 1}`,
              text,
              sort: Number(row.sort ?? index + 1),
            };
          })
          .filter((row) => row.text)
          .sort((a, b) => a.sort - b.sort)
      );
    } catch {
      setFeedbackTemplates(DEFAULT_FEEDBACK_TEMPLATES);
    }
  };

  // ?? ?쇰뱶諛??붾㈃? "?꾩옱 怨쇱젣 紐⑸줉"??癒쇱? ?꾩슂?섎?濡? 吏꾩엯 ??怨쇱젣 紐⑸줉??癒쇱? 遺덈윭?듬땲??
  const fetchHomeworks = async () => {
    if (!courseId) return;
    setLoadingHomeworks(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getHomeworks({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        id: Number(row.homework_id),
        title: row.homework_nm || row.module_nm || '怨쇱젣',
      }));
      setHomeworks(mapped);

      const firstId = mapped[0]?.id ?? null;
      setSelectedHomeworkId((prev) => {
        if (prev && mapped.some((h: any) => h.id === prev)) return prev;
        return firstId;
      });

      if (!firstId) {
        setStudents([]);
        setSelectedCourseUserId(null);
        setTempScore('0');
        setFeedbackText('');
      }
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '怨쇱젣 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoadingHomeworks(false);
    }
  };

  const fetchHomeworkUsers = async (homeworkId: number) => {
    if (!courseId || !homeworkId) return;
    setLoadingUsers(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getHomeworkUsers({ courseId, homeworkId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        courseUserId: Number(row.course_user_id),
        studentId: row.login_id,
        name: row.user_nm,
        submitted: toBool(row.submitted),
        submittedAt: row.submitted_at ?? '-',
        confirm: toBool(row.confirm),
        markingScore: Number(row.marking_score ?? 0),
        scoreConv: row.score_conv ?? '',
        feedback: row.feedback ?? '',
        taskCnt: Number(row.task_cnt ?? 0),
      }));
      setStudents(mapped);

      // ?? ?ъ“?????좏깮???숈깮??紐⑸줉?먯꽌 ?щ씪吏硫?沅뚰븳/?곹깭 蹂???? ?좏깮???댁젣?댁빞 ?붾㈃??源⑥?吏 ?딆뒿?덈떎.
      setSelectedCourseUserId((prev) => {
        if (prev && mapped.some((s: any) => s.courseUserId === prev)) return prev;
        return null;
      });
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?쒖텧 ?꾪솴??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoadingUsers(false);
    }
  };

  useEffect(() => {
    void fetchHomeworks();
    void fetchFeedbackTemplates();
  }, [courseId]);

  useEffect(() => {
    if (!selectedHomeworkId) return;
    void fetchHomeworkUsers(selectedHomeworkId);
  }, [courseId, selectedHomeworkId]);

  useEffect(() => {
    if (!selectedHomeworkId || !selectedCourseUserId) {
      setUploadedFeedbackFiles([]);
      return;
    }
    void fetchHomeworkFeedbackFiles(selectedHomeworkId, selectedCourseUserId);
  }, [courseId, selectedHomeworkId, selectedCourseUserId]);

  const selectedStudent = students.find((s: any) => s.courseUserId === selectedCourseUserId) ?? null;

  const handleSelectStudent = (courseUserId: number) => {
    setSelectedCourseUserId(courseUserId);
    const student = students.find((s: any) => s.courseUserId === courseUserId);
    setTempScore(String(student?.markingScore ?? 0));
    setFeedbackText(String(student?.feedback ?? ''));
    // ?? ?숈깮??諛붾뚮㈃ ?댁쟾 ?숈깮??泥⑤??뚯씪??珥덇린?뷀빐???쇱꽑???놁뒿?덈떎.
    setFeedbackFiles([]);
    setUploadedFeedbackFiles([]);
  };

  const handleOpenSubmissionModal = () => {
    if (!selectedHomeworkId || !selectedCourseUserId) return;
    setShowSubmissionModal(true);
    setSubmissionLoading(true);
    setSubmissionError(null);
    setSubmissionDetail(null);

    void (async () => {
      try {
        const res = await tutorLmsApi.getHomeworkSubmissionDetail({
          courseId,
          homeworkId: selectedHomeworkId,
          courseUserId: selectedCourseUserId,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const d: any = res.rst_data;
        const files = Array.isArray(d?.files)
          ? d.files.map((f: any) => ({
              id: Number(f.id),
              filename: String(f.filename ?? ''),
              downloadUrl: String(f.download_url ?? ''),
            }))
          : [];
        const feedbackFiles = Array.isArray(d?.feedback_files)
          ? d.feedback_files.map((f: any) => ({
              id: Number(f.id),
              filename: String(f.filename ?? ''),
              downloadUrl: String(f.download_url ?? ''),
            }))
          : [];

        setSubmissionDetail({
          submitted: Boolean(d?.submitted),
          submittedAt: String(d?.submitted_at ?? '-'),
          subject: String(d?.subject ?? ''),
          content: String(d?.content ?? ''),
          files,
          feedbackFiles,
        });
      } catch (e) {
        setSubmissionError(e instanceof Error ? e.message : '?쒖텧臾쇱쓣 遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setSubmissionLoading(false);
      }
    })();
  };

  const handleSubmitFeedback = () => {
    if (!selectedHomeworkId || !selectedCourseUserId) return;

    const score = parseInt(tempScore, 10);
    if (isNaN(score) || score < 0 || score > 100) {
      alert('?먯닔??0~100 ?ъ씠???レ옄?ъ빞 ?⑸땲??');
      return;
    }

    void (async () => {
      setFeedbackFileBusy(true);
      try {
        // ?? ???利됱떆 ?깆쟻(homework_score/total_score)??諛섏쁺?섏뼱??"?ㅼ궗?? ?먮쫫???딄린吏 ?딆뒿?덈떎.
        const res = await tutorLmsApi.updateHomeworkFeedback({
          courseId,
          homeworkId: selectedHomeworkId,
          courseUserId: selectedCourseUserId,
          markingScore: score,
          feedback: feedbackText,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const pendingFiles = [...feedbackFiles];
        for (const file of pendingFiles) {
          const uploadRes = await tutorLmsApi.uploadHomeworkFeedbackFile({
            courseId,
            homeworkId: selectedHomeworkId,
            courseUserId: selectedCourseUserId,
            file,
          });
          if (uploadRes.rst_code !== '0000') {
            throw new Error(`[${file.name}] ${uploadRes.rst_message}`);
          }
        }

        setFeedbackFiles([]);
        await fetchHomeworkUsers(selectedHomeworkId);
        await fetchHomeworkFeedbackFiles(selectedHomeworkId, selectedCourseUserId);
        alert(pendingFiles.length > 0 ? '?먯닔/?쇰뱶諛깃낵 泥⑥궘 ?뚯씪????λ릺?덉뒿?덈떎.' : '??λ릺?덉뒿?덈떎.');
      } catch (e) {
        alert(e instanceof Error ? e.message : '???以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setFeedbackFileBusy(false);
      }
    })();
  };

  const handleDeleteFeedbackFile = (fileId: number) => {
    if (!selectedHomeworkId || !selectedCourseUserId) return;
    if (!confirm('?좏깮??泥⑥궘 ?뚯씪????젣?섏떆寃좎뒿?덇퉴?')) return;

    void (async () => {
      setFeedbackFileBusy(true);
      try {
        const res = await tutorLmsApi.deleteHomeworkFeedbackFile({
          courseId,
          homeworkId: selectedHomeworkId,
          courseUserId: selectedCourseUserId,
          fileId,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchHomeworkFeedbackFiles(selectedHomeworkId, selectedCourseUserId);
      } catch (e) {
        alert(e instanceof Error ? e.message : '泥⑥궘 ?뚯씪 ??젣 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setFeedbackFileBusy(false);
      }
    })();
  };

    // ?? ?쒖텧 痍⑥냼???먯닔/?쇰뱶諛깃낵 蹂꾧컻濡??쒖텧 湲곕줉 ?먯껜瑜??뺣━?댁빞 ?⑸땲??
    const handleCancelHomeworkSubmit = () => {
      if (!selectedHomeworkId || !selectedCourseUserId) return;
      if (!confirm('?대떦 ?숈깮??怨쇱젣 ?쒖텧??痍⑥냼?섏떆寃좎뒿?덇퉴?')) return;

      void (async () => {
        try {
          const res = await tutorLmsApi.cancelHomeworkSubmit({
            courseId,
            homeworkId: selectedHomeworkId,
            courseUserId: selectedCourseUserId,
          });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);

          await fetchHomeworkUsers(selectedHomeworkId);
          setSelectedCourseUserId(null);
          setTempScore('0');
          setFeedbackText('');
          alert('?쒖텧??痍⑥냼?섏뿀?듬땲??');
        } catch (e) {
          alert(e instanceof Error ? e.message : '?쒖텧 痍⑥냼 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      })();
    };

  const handleAppendTask = () => {
    if (!selectedHomeworkId || !selectedCourseUserId) return;

    const task = prompt('異붽?怨쇱젣 ?댁슜???낅젰??二쇱꽭??');
    if (!task || !task.trim()) return;

    void (async () => {
      try {
        const res = await tutorLmsApi.appendHomeworkTask({
          courseId,
          homeworkId: selectedHomeworkId,
          courseUserId: selectedCourseUserId,
          task: task.trim(),
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchHomeworkUsers(selectedHomeworkId);
        alert('異붽?怨쇱젣媛 遺?щ릺?덉뒿?덈떎.');
      } catch (e) {
        alert(e instanceof Error ? e.message : '異붽?怨쇱젣 遺??以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      }
    })();
  };

  // ?? ?숈깮蹂?異붽? 怨쇱젣 紐⑸줉??議고쉶?섏뿬 ?ъ젣異??꾪솴???뺤씤?⑸땲??
  const [homeworkTasks, setHomeworkTasks] = useState<any[]>([]);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [selectedTask, setSelectedTask] = useState<any | null>(null);

  const fetchHomeworkTasks = async (homeworkId: number, courseUserId: number) => {
    setLoadingTasks(true);
    try {
      const res = await tutorLmsApi.getHomeworkTasks({ courseId, homeworkId, courseUserId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      setHomeworkTasks(res.rst_data ?? []);
    } catch (e) {
      setHomeworkTasks([]);
    } finally {
      setLoadingTasks(false);
    }
  };

  const handleSelectStudentWithTasks = (courseUserId: number) => {
    handleSelectStudent(courseUserId);
    if (selectedHomeworkId) {
      void fetchHomeworkTasks(selectedHomeworkId, courseUserId);
    }
  };

  const summary = {
    total: students.length,
    needFeedback: students.filter((s: any) => s.submitted && !s.confirm).length,
    doneFeedback: students.filter((s: any) => s.confirm).length,
  };

  // ?? ?숈깮 媛?怨쇱젣 ?좎궗??遺꾩꽍 寃곌낵瑜??쒖떆?섍린 ?꾪븳 ?곹깭?낅땲??
  type SimilarityResult = {
    studentAId: number;
    studentAName: string;
    studentALoginId: string;
    studentBId: number;
    studentBName: string;
    studentBLoginId: string;
    titleScore: number;
    contentScore: number;
    fileScore: number;
    totalScore: number;
  };
  const [similarityData, setSimilarityData] = useState<SimilarityResult[]>([]);
  const [similarityLoading, setSimilarityLoading] = useState(false);
  const [similarityAnalyzed, setSimilarityAnalyzed] = useState(false);

  // ?? ?좏깮???숈깮怨?愿?⑤맂 ?좎궗??寃곌낵留??꾪꽣留곹빀?덈떎.
  const studentSimilarities = selectedCourseUserId
    ? similarityData.filter(
        (r) => r.studentAId === selectedCourseUserId || r.studentBId === selectedCourseUserId
      )
    : [];

  // ?? ?좎궗???꾧퀎移??댁긽???숈깮 ID瑜?鍮좊Ⅴ寃?議고쉶?섍린 ?꾪븳 Set?낅땲??
  const flaggedStudentIds = new Set<number>();
  similarityData.forEach((r) => {
    flaggedStudentIds.add(r.studentAId);
    flaggedStudentIds.add(r.studentBId);
  });

  const toSimilarityNumber = (value: any) => {
    const n = Number(value);
    return Number.isFinite(n) ? n : 0;
  };

  const handleAnalyzeSimilarity = () => {
    if (!selectedHomeworkId) return;
    setSimilarityLoading(true);
    setSimilarityAnalyzed(false);

    void (async () => {
      try {
        // ?? 紐⑸줉 議고쉶 ?꾩뿉 理쒖떊 寃곌낵瑜?媛뺤젣濡?媛깆떊??援먯닔?먭? 諛붾줈 ?뺤씤?????덇쾶 ?⑸땲??
        const runRes = await tutorLmsApi.runHomeworkSimilarity({
          courseId,
          homeworkId: selectedHomeworkId,
          thresholdScore: 70,
        });
        if (runRes.rst_code !== '0000') throw new Error(runRes.rst_message);

        const listRes = await tutorLmsApi.getHomeworkSimilarityList({
          courseId,
          homeworkId: selectedHomeworkId,
          page: 1,
          pageSize: 200,
          minScore: 70,
        });
        if (listRes.rst_code !== '0000') throw new Error(listRes.rst_message);

        const rows = Array.isArray(listRes.rst_data) ? listRes.rst_data : [];
        const mapped: SimilarityResult[] = rows.map((row: any) => ({
          studentAId: toSimilarityNumber(row.left_course_user_id),
          studentAName: String(row.left_user_nm ?? ''),
          studentALoginId: String(row.left_login_id ?? ''),
          studentBId: toSimilarityNumber(row.right_course_user_id),
          studentBName: String(row.right_user_nm ?? ''),
          studentBLoginId: String(row.right_login_id ?? ''),
          titleScore: toSimilarityNumber(row.subject_score),
          contentScore: toSimilarityNumber(row.content_score),
          fileScore: toSimilarityNumber(row.file_score),
          totalScore: toSimilarityNumber(row.total_score),
        }));

        setSimilarityData(mapped);
        setSimilarityAnalyzed(true);
      } catch (e) {
        setSimilarityData([]);
        setErrorMessage(e instanceof Error ? e.message : '?좎궗??遺꾩꽍 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setSimilarityLoading(false);
      }
    })();
  };

  return (
    <div className="space-y-4">
      {/* 怨쇱젣 ?좏깮 + ?좎궗??遺꾩꽍 踰꾪듉 */}
      <div className="flex items-center gap-4">
        <label className="text-sm text-gray-700">怨쇱젣 ?좏깮:</label>
        <select
          value={selectedHomeworkId ?? ''}
          onChange={(e) => {
            const nextId = e.target.value ? Number(e.target.value) : null;
            setSelectedHomeworkId(nextId);
            setSelectedCourseUserId(null);
            setTempScore('0');
            setFeedbackText('');
            setStatusFilter('all');
            // ?? 怨쇱젣媛 諛붾뚮㈃ ?댁쟾 ?좎궗??寃곌낵瑜?珥덇린?뷀빐???쇱꽑???놁뒿?덈떎.
            setSimilarityData([]);
            setSimilarityAnalyzed(false);
          }}
          disabled={loadingHomeworks || homeworks.length === 0}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
        >
          {homeworks.length === 0 && <option value="">怨쇱젣媛 ?놁뒿?덈떎</option>}
          {homeworks.map((hw: any) => (
            <option key={hw.id} value={hw.id}>
              {hw.title}
            </option>
          ))}
        </select>
        {/* ?? 援먯닔?먭? ?먰븷 ???좎궗??遺꾩꽍???ㅽ뻾?????덈룄濡?踰꾪듉??諛곗튂?⑸땲?? */}
        <button
          type="button"
          onClick={handleAnalyzeSimilarity}
          disabled={!selectedHomeworkId || similarityLoading}
          className="flex items-center gap-1.5 px-4 py-2 text-sm border border-amber-300 bg-amber-50 text-amber-800 rounded-lg hover:bg-amber-100 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <AlertTriangle className="w-4 h-4" />
          {similarityLoading ? '遺꾩꽍 以?..' : '?좎궗??遺꾩꽍'}
        </button>
        {similarityAnalyzed && similarityData.length === 0 && (
          <span className="text-xs text-green-600">???좎궗 怨쇱젣媛 諛쒓껄?섏? ?딆븯?듬땲??</span>
        )}
        {similarityAnalyzed && similarityData.length > 0 && (
          <span className="text-xs text-red-600">??{similarityData.length}嫄댁쓽 ?좎궗 怨쇱젣媛 諛쒓껄?섏뿀?듬땲??</span>
        )}
      </div>

      {errorMessage && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {errorMessage}
        </div>
      )}

      {loadingUsers && (
        <div className="p-6 text-center text-gray-500">?쒖텧 ?꾪솴??遺덈윭?ㅻ뒗 以?..</div>
      )}

      {homeworks.length === 0 && !loadingHomeworks ? (
        <div className="p-10 text-center text-gray-500 border border-dashed border-gray-200 rounded-lg">
          癒쇱? 怨쇱젣瑜??깅줉??二쇱꽭?? (怨쇱젣 愿由???뿉???깅줉?????덉뒿?덈떎.)
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-6">
            {/* ?쇱そ: ?섍컯??紐⑸줉 */}
            <div>
              <div className="bg-gray-50 px-4 py-3 rounded-t-lg border border-b-0 border-gray-200">
                <h4 className="text-gray-900">?섍컯??紐⑸줉 ({students.length}紐?</h4>
              </div>
              {/* ?꾪꽣 踰꾪듉 */}
              <div className="flex gap-2 px-4 py-2.5 border border-b-0 border-gray-200 bg-white">
                {[
                  { key: 'all' as const, label: '전체', count: summary.total, bg: 'bg-gray-100 text-gray-700', activeBg: 'bg-gray-700 text-white' },
                  { key: 'unsubmitted' as const, label: '미제출', count: summary.total - summary.needFeedback - summary.doneFeedback, bg: 'bg-red-50 text-red-600', activeBg: 'bg-red-600 text-white' },
                  { key: 'need_feedback' as const, label: '피드백필요', count: summary.needFeedback, bg: 'bg-orange-50 text-orange-600', activeBg: 'bg-orange-500 text-white' },
                  { key: 'done' as const, label: '피드백완료', count: summary.doneFeedback, bg: 'bg-green-50 text-green-600', activeBg: 'bg-green-600 text-white' },
                ].map((f) => (
                  <button
                    key={f.key}
                    onClick={() => setStatusFilter(f.key)}
                    className={`px-3 py-1 text-xs rounded-full font-medium transition-colors ${
                      statusFilter === f.key ? f.activeBg : `${f.bg} hover:opacity-80`
                    }`}
                  >
                    {f.label} ({f.count})
                  </button>
                ))}
              </div>
              <div className="border border-gray-200 rounded-b-lg divide-y divide-gray-200 max-h-[600px] overflow-y-auto">
                {students.filter((s: any) => {
                  if (statusFilter === 'unsubmitted') return !s.submitted;
                  if (statusFilter === 'need_feedback') return s.submitted && !s.confirm;
                  if (statusFilter === 'done') return s.confirm;
                  return true;
                }).map((student: any) => {
                  const isSelected = selectedCourseUserId === student.courseUserId;
                  const badge = !student.submitted
                    ? { label: '미제출', className: 'bg-red-100 text-red-700' }
                    : student.confirm
                    ? { label: '피드백완료', className: 'bg-green-100 text-green-700' }
                    : { label: '피드백필요', className: 'bg-orange-100 text-orange-700' };

                  return (
                    <button
                      key={student.courseUserId}
                      onClick={() => handleSelectStudentWithTasks(student.courseUserId)}

                      className={`w-full p-4 text-left transition-colors ${
                        isSelected
                          ? 'bg-blue-50 border-l-4 border-blue-600'
                          : 'hover:bg-gray-50 border-l-4 border-transparent'
                      }`}
                    >
                      <div className="flex items-start justify-between mb-2">
                        <div>
                          <div className="text-gray-900 mb-1">{student.name}</div>
                          <div className="text-sm text-gray-600">{student.studentId}</div>
                        </div>
                        <div className="flex items-center gap-1">
                          <span className={`px-2 py-1 rounded text-xs ${badge.className}`}>
                            {badge.label}
                          </span>
                          {/* ?? ?좎궗???꾧퀎移??댁긽???숈깮?먭쾶 寃쎄퀬 諭껋?瑜??쒖떆?⑸땲?? */}
                          {flaggedStudentIds.has(student.courseUserId) && (
                            <span className="px-1.5 py-1 rounded text-xs bg-red-100 text-red-700 flex items-center gap-0.5" title="?좎궗 怨쇱젣 媛먯?">
                              <AlertTriangle className="w-3 h-3" />
                              ?좎궗
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center justify-between text-xs text-gray-500">
                        <span>?쒖텧: {student.submittedAt}</span>
                        <span>
                          점수: {student.markingScore}점{student.scoreConv ? ` (${student.scoreConv})` : ''}
                        </span>
                      </div>
                      {0 < student.taskCnt && (
                        <div className="mt-2 text-xs text-blue-700">異붽?怨쇱젣 {student.taskCnt}嫄?</div>
                      )}
                    </button>
                  );
                })}

                {students.length === 0 && (
                  <div className="p-8 text-center text-gray-500">?섍컯?앹씠 ?놁뒿?덈떎.</div>
                )}
              </div>
            </div>

            {/* ?ㅻⅨ履? ?쇰뱶諛?梨꾩젏 */}
            <div>
              {selectedStudent ? (
                <div className="border border-gray-200 rounded-lg">
                  <div className="bg-gray-50 px-4 py-3 border-b border-gray-200">
                    <h4 className="text-gray-900">
                      {selectedStudent.name} ({selectedStudent.studentId})
                    </h4>
                    <div className="mt-1 flex items-center justify-between gap-3">
                      <p className="text-xs text-gray-500">?쒖텧?쒓컙: {selectedStudent.submittedAt}</p>
                      {selectedStudent.submitted && (
                        <button
                          type="button"
                          onClick={handleOpenSubmissionModal}
                          className="px-3 py-1.5 text-xs border border-gray-200 rounded-md text-gray-700 hover:bg-gray-50 transition-colors"
                        >
                          ?쒖텧臾?蹂닿린
                        </button>
                      )}
                    </div>
                  </div>
                  <div className="p-4 space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="block text-sm text-gray-700 mb-2">?먯닔(0~100)</label>
                        <input
                          type="number"
                          min="0"
                          max="100"
                          value={tempScore}
                          onChange={(e) => setTempScore(e.target.value)}
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </div>
                      <div className="flex items-end justify-end">
                        <button
                          onClick={handleAppendTask}
                          className="flex items-center gap-2 px-4 py-2 border border-blue-200 text-blue-700 rounded-lg hover:bg-blue-50 transition-colors"
                        >
                          <Plus className="w-4 h-4" />
                          <span>異붽?怨쇱젣 遺??</span>
                        </button>
                      </div>
                    </div>

                    <div>
                      <div className="flex items-center justify-between mb-2">
                        <label className="block text-sm text-gray-700">피드백</label>
                      </div>

                      {/* ?쇰뱶諛?鍮좊Ⅸ ?쒗뵆由?*/}
                      <div className="flex flex-wrap gap-1.5 mb-2">
                        {feedbackTemplates.map((tpl) => (
                          <button
                            key={tpl.id}
                            type="button"
                            onClick={() => {
                              setFeedbackText((prev: string) => {
                                if (!prev.trim()) return tpl.text;
                                return `${prev}\n${tpl.text}`;
                              });
                            }}
                            className="px-2.5 py-1 text-xs rounded-full border border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100 transition-colors"
                            title={tpl.text}
                          >
                            {tpl.label}
                          </button>
                        ))}
                      </div>

                      <textarea
                        value={feedbackText}
                        onChange={(e) => setFeedbackText(e.target.value)}
                        placeholder="?숈깮?먭쾶 ?꾨떖???쇰뱶諛깆쓣 ?낅젰?섏꽭??.."
                        rows={6}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                      />

                      {/* ?? ?쒕쾭 ????뚯씪怨??대쾲??異붽????뚯씪??遺꾨━??蹂댁뿬以섏빞 ?ㅽ빐 ?놁씠 愿由ы븷 ???덉뒿?덈떎. */}
                      <div className="mt-3 space-y-2">
                        <div className="flex items-center gap-2">
                          <Paperclip className="w-4 h-4 text-gray-500" />
                          <span className="text-sm text-gray-700">泥⑤??뚯씪</span>
                          <button
                            type="button"
                            onClick={() => feedbackFileInputRef.current?.click()}
                            disabled={feedbackFileBusy}
                            className="px-3 py-1 text-xs border border-blue-200 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors"
                          >
                            + ?뚯씪 ?좏깮
                          </button>
                          <input
                            ref={feedbackFileInputRef}
                            type="file"
                            multiple
                            className="hidden"
                            disabled={feedbackFileBusy}
                            onChange={(e) => {
                              const newFiles = Array.from(e.target.files || []);
                              if (newFiles.length > 0) {
                                setFeedbackFiles((prev) => [...prev, ...newFiles]);
                              }
                              // ?? 媛숈? ?뚯씪???ㅼ떆 ?좏깮?????덈룄濡?value瑜?珥덇린?뷀빀?덈떎.
                              e.target.value = '';
                            }}
                          />
                        </div>
                        {uploadedFeedbackFiles.length > 0 && (
                          <div className="border border-gray-200 rounded-lg divide-y divide-gray-100">
                            {uploadedFeedbackFiles.map((file) => (
                              <div key={file.id} className="flex items-center justify-between px-3 py-2">
                                <a
                                  href={file.downloadUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="flex items-center gap-2 text-sm text-blue-700 hover:underline truncate"
                                  title={file.filename}
                                >
                                  <Paperclip className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                                  <span className="truncate">{file.filename}</span>
                                </a>
                                <button
                                  type="button"
                                  onClick={() => handleDeleteFeedbackFile(file.id)}
                                  disabled={feedbackFileBusy}
                                  className="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors flex-shrink-0 disabled:opacity-50"
                                  title="?쒕쾭 ?뚯씪 ??젣"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                        {feedbackFiles.length > 0 && (
                          <div className="border border-gray-200 rounded-lg divide-y divide-gray-100">
                            {feedbackFiles.map((file, idx) => (
                              <div key={`${file.name}-${idx}`} className="flex items-center justify-between px-3 py-2">
                                <div className="flex items-center gap-2 text-sm text-gray-700 truncate">
                                  <Paperclip className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                                  <span className="truncate">{file.name}</span>
                                  <span className="text-xs text-gray-400 flex-shrink-0">
                                    ({(file.size / 1024).toFixed(0)}KB)
                                  </span>
                                </div>
                                <button
                                  type="button"
                                  onClick={() => setFeedbackFiles((prev) => prev.filter((_, i) => i !== idx))}
                                  disabled={feedbackFileBusy}
                                  className="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors flex-shrink-0 disabled:opacity-50"
                                  title="??젣"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                        {uploadedFeedbackFiles.length === 0 && feedbackFiles.length === 0 && (
                          <div className="text-xs text-gray-400 pl-6">
                            泥⑥궘 ?뚯씪???덉쑝硫??좏깮??二쇱꽭?? (?좏깮?ы빆)
                          </div>
                        )}
                      </div>
                    </div>

                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => {
                            setTempScore(String(selectedStudent.markingScore ?? 0));
                            setFeedbackText(String(selectedStudent.feedback ?? ''));
                        }}
                        className="px-4 py-2 text-sm border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
                        >
                          痍⑥냼
                        </button>
                        {selectedStudent.submitted && (
                          <button
                            onClick={handleCancelHomeworkSubmit}
                            className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                          >
                            ?쒖텧 痍⑥냼
                          </button>
                        )}
                        <button
                          onClick={handleSubmitFeedback}
                          disabled={feedbackFileBusy}
                          className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-400"
                        >
                          {feedbackFileBusy ? '저장 중...' : '저장'}
                      </button>
                    </div>

                    <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
                      <div className="text-sm text-blue-900 mb-2">?쇰뱶諛??덈궡</div>
                      <div className="text-sm text-blue-700">
                        - ??μ쓣 ?꾨Ⅴ硫??먯닔/?쇰뱶諛깆씠 DB????λ릺怨? ?깆쟻(怨쇱젣 ?먯닔)??利됱떆 諛섏쁺?⑸땲??
                        <br />- ?ㅽ봽?쇱씤 怨쇱젣泥섎읆 ?쒖텧 湲곕줉???놁뼱?? ?꾩슂?섎㈃ ?먯닔 ?낅젰??媛?ν빀?덈떎.
                      </div>
                    </div>

                    {/* ?? ?좏깮???숈깮怨?愿?⑤맂 ?좎궗??遺꾩꽍 寃곌낵瑜??곸꽭?섍쾶 ?쒖떆?⑸땲?? */}
                    {similarityAnalyzed && (
                      <div className="mt-4 border border-amber-200 rounded-lg overflow-hidden">
                        <div className="bg-amber-50 px-4 py-2.5 flex items-center gap-2">
                          <AlertTriangle className="w-4 h-4 text-amber-600" />
                          <span className="text-sm font-medium text-amber-900">?좎궗??遺꾩꽍 寃곌낵</span>
                        </div>
                        {studentSimilarities.length > 0 ? (
                          <div className="divide-y divide-amber-100">
                            {studentSimilarities.map((sim, idx) => {
                              // ?? ?좏깮???숈깮 湲곗??쇰줈 鍮꾧탳 ????숈깮 ?뺣낫瑜?媛?몄샃?덈떎.
                              const isA = sim.studentAId === selectedCourseUserId;
                              const peerName = isA ? sim.studentBName : sim.studentAName;
                              const peerLoginId = isA ? sim.studentBLoginId : sim.studentALoginId;
                              const scoreColor =
                                sim.totalScore >= 90
                                  ? 'text-red-700 bg-red-50'
                                  : sim.totalScore >= 70
                                  ? 'text-orange-700 bg-orange-50'
                                  : 'text-gray-700 bg-gray-50';

                              return (
                                <div key={idx} className="px-4 py-3">
                                  <div className="flex items-center justify-between mb-2">
                                    <span className="text-sm text-gray-900">
                                      {peerName}
                                      {peerLoginId && <span className="text-gray-400 ml-1">({peerLoginId})</span>}
                                    </span>
                                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${scoreColor}`}>
                                      ?좎궗??{sim.totalScore}%
                                    </span>
                                  </div>
                                  <div className="flex gap-3 text-xs text-gray-500">
                                    <span>?쒕ぉ: <span className="font-medium text-gray-700">{sim.titleScore}%</span></span>
                                    <span>蹂몃Ц: <span className="font-medium text-gray-700">{sim.contentScore}%</span></span>
                                    <span>?뚯씪: <span className="font-medium text-gray-700">{sim.fileScore}%</span></span>
                                  </div>
                                  {/* ?? 媛以묒튂 ?뺣낫瑜??쒖떆?섏뿬 援먯닔?먭? ?먮떒 洹쇨굅瑜??????덇쾶 ?⑸땲?? */}
                                  <div className="mt-1.5">
                                    <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden">
                                      <div
                                        className={`h-full rounded-full ${
                                          sim.totalScore >= 90 ? 'bg-red-500' : sim.totalScore >= 70 ? 'bg-orange-400' : 'bg-gray-300'
                                        }`}
                                        style={{ width: `${sim.totalScore}%` }}
                                      />
                                    </div>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div className="px-4 py-4 text-sm text-gray-500 text-center">
                            ???숈깮??怨쇱젣? ?좎궗??怨쇱젣媛 ?놁뒿?덈떎.
                          </div>
                        )}
                      </div>
                    )}

                    {/* 異붽? 怨쇱젣 紐⑸줉 */}
                    {homeworkTasks.length > 0 && (
                      <div className="mt-4">
                        <div className="text-sm font-medium text-gray-700 mb-2">異붽? 怨쇱젣 紐⑸줉 ({homeworkTasks.length}嫄?</div>
                        <div className="border border-gray-200 rounded-lg divide-y divide-gray-200 max-h-[300px] overflow-y-auto">
                          {homeworkTasks.map((task: any) => (
                            <button
                              key={task.id}
                              onClick={() => setSelectedTask(task)}
                              className="w-full text-left p-3 hover:bg-gray-50 transition-colors"
                            >
                              <div className="flex items-start justify-between mb-1">
                                <div className="text-sm text-gray-900 font-medium group-hover:text-blue-600">
                                  {task.task_preview}
                                </div>
                                <div className="flex gap-1 flex-shrink-0 ml-2">
                                  {task.need_review ? (
                                    <span className="px-2 py-0.5 bg-orange-100 text-orange-700 text-xs rounded border border-orange-200">?ы룊媛 ?꾩슂</span>
                                  ) : (
                                    <>
                                      <span className={`px-2 py-0.5 text-xs rounded ${
                                        task.submit_yn === 'Y' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'
                                      }`}>{task.submit_yn_label}</span>
                                      <span className={`px-2 py-0.5 text-xs rounded ${
                                        task.confirm_yn === 'Y' ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-600'
                                      }`}>{task.confirm_yn_label}</span>
                                    </>
                                  )}
                                </div>
                              </div>
                              <div className="text-xs text-gray-500">
                                遺?? {task.reg_date_conv}
                                {task.submit_yn === 'Y' && <span className="ml-3">?쒖텧: {task.submit_date_conv}</span>}
                              </div>
                              {task.subject && (
                                <div className="mt-1 text-xs text-gray-700">
                                  <span className="font-medium">?쒖텧 ?쒕ぉ:</span> {task.subject}
                                </div>
                              )}
                            </button>
                          ))}
                        </div>
                        <div className="mt-2 text-[11px] text-gray-400">
                          * 媛???ぉ???대┃?섎㈃ ?쒖텧 ?곸꽭 ?댁슜 ?뺤씤 諛??됯?媛 媛?ν빀?덈떎.
                        </div>
                      </div>
                    )}
                    {loadingTasks && (
                      <div className="text-center text-gray-500 text-sm py-2">異붽? 怨쇱젣 紐⑸줉 遺덈윭?ㅻ뒗 以?..</div>
                    )}
                  </div>
                </div>

              ) : (
                <div className="border border-gray-200 rounded-lg">
                  <div className="p-12 text-center text-gray-500">
                    <MessageSquare className="w-12 h-12 text-gray-400 mx-auto mb-3" />
                    <p>?숈깮???좏깮?섏뿬</p>
                    <p>?먯닔? ?쇰뱶諛깆쓣 ??ν븯?몄슂</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          <HomeworkSubmissionDetailModal
            open={showSubmissionModal}
            onOpenChange={setShowSubmissionModal}
            title="?숈깮 ?쒖텧臾??뺤씤"
            meta={
              selectedStudent
                ? {
                    studentName: selectedStudent.name,
                    studentId: selectedStudent.studentId,
                    submittedAt: selectedStudent.submittedAt,
                  }
                : undefined
            }
            loading={submissionLoading}
            errorMessage={submissionError}
            detail={submissionDetail}
          />

          {/* ?듦퀎 ?붿빟 */}
          <div className="grid grid-cols-3 gap-4 pt-4 border-t border-gray-200">
            <div className="p-4 bg-gray-50 rounded-lg">
              <div className="text-sm text-gray-600 mb-1">珥??몄썝</div>
              <div className="text-2xl text-gray-900">{summary.total}紐?</div>
            </div>
            <div className="p-4 bg-orange-50 rounded-lg">
              <div className="text-sm text-orange-600 mb-1">?쇰뱶諛??꾩슂</div>
              <div className="text-2xl text-orange-900">{summary.needFeedback}紐?</div>
            </div>
            <div className="p-4 bg-green-50 rounded-lg">
              <div className="text-sm text-green-600 mb-1">?쇰뱶諛??꾨즺</div>
              <div className="text-2xl text-green-900">{summary.doneFeedback}紐?</div>
            </div>
          </div>
        </>
      )}

      {/* 異붽?怨쇱젣 ?곸꽭 紐⑤떖 */}
      <HomeworkTaskDetailModal
        isOpen={!!selectedTask}
        onClose={() => setSelectedTask(null)}
        courseId={courseId}
        homeworkId={selectedHomeworkId ?? 0}
        courseUserId={selectedCourseUserId ?? 0}
        task={selectedTask}
        onRefresh={() => {
          if (selectedHomeworkId && selectedCourseUserId) {
            void fetchHomeworkTasks(selectedHomeworkId, selectedCourseUserId);
            void fetchHomeworkUsers(selectedHomeworkId);
          }
        }}
      />
    </div>
  );
}

// ?먮즺 ??
function MaterialsTab({
  courseId,
  showWeekSession = false,
  weekCount = 15,
  course,
}: {
  courseId: number;
  showWeekSession?: boolean;
  weekCount?: number;
  course?: any;
}) {
  const [showUploadModal, setShowUploadModal] = useState(false);

  const [materials, setMaterials] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [resolvingCourseId, setResolvingCourseId] = useState(false);
  const [resolvedCourseId, setResolvedCourseId] = useState<number | null>(null);

  const isHaksaCourse = course?.sourceType === 'haksa';
  // ?? ?숈궗 怨쇰ぉ? 紐⑸줉??id媛 ?꾨땶 留ㅽ븨??怨쇱젙 ID留??좏슚?섎?濡? 留ㅽ븨媛믪씠 ?놁쑝硫?0?쇰줈 痍④툒?⑸땲??
  const baseCourseId = isHaksaCourse
    ? Number(course?.mappedCourseId ?? 0)
    : Number.isFinite(courseId)
      ? courseId
      : 0;
  const effectiveCourseId = resolvedCourseId && resolvedCourseId > 0 ? resolvedCourseId : baseCourseId;

  const [uploadForm, setUploadForm] = useState<{
    title: string;
    content: string;
    link: string;
    file: File | null;
    weekNumber: number;
    sessionNumber: number;
  }>({
    title: '',
    content: '',
    link: '',
    file: null,
    weekNumber: 1,
    sessionNumber: 1,
  });

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

  const resetUploadForm = () => {
    setUploadForm({ title: '', content: '', link: '', file: null, weekNumber: 1, sessionNumber: 1 });
  };

  useEffect(() => {
    if (!isHaksaCourse) {
      setResolvedCourseId(null);
      return;
    }

    if (baseCourseId > 0) {
      setResolvedCourseId(null);
      return;
    }

    if (!haksaKey) {
      setErrorMessage('?숈궗 怨쇰ぉ ?ㅺ? 鍮꾩뼱 ?덉뼱 怨쇱젙 留ㅽ븨??吏꾪뻾?????놁뒿?덈떎.');
      return;
    }

    let cancelled = false;
    const resolveCourseId = async () => {
      setResolvingCourseId(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.resolveHaksaCourse(haksaKey);
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
        const mapped = Number(payload?.mapped_course_id ?? 0);
        if (!mapped || Number.isNaN(mapped)) throw new Error('留ㅽ븨??怨쇱젙ID瑜?李얠? 紐삵뻽?듬땲??');

        if (!cancelled) setResolvedCourseId(mapped);
      } catch (e) {
        if (!cancelled) {
          setResolvedCourseId(null);
          setErrorMessage(e instanceof Error ? e.message : '怨쇱젙 留ㅽ븨 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      } finally {
        if (!cancelled) setResolvingCourseId(false);
      }
    };

    void resolveCourseId();
    return () => {
      cancelled = true;
    };
  }, [isHaksaCourse, baseCourseId, haksaKey]);

  // ?? ?먮즺 紐⑸줉? DB媛 湲곗??대?濡? ??吏꾩엯/?낅줈????젣 ?꾩뿉???쒕쾭?먯꽌 ?ㅼ떆 ?쎌뼱????⑸땲??
  const fetchMaterials = async () => {
    if (!effectiveCourseId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getMaterials({ courseId: effectiveCourseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        id: Number(row.library_id),
        title: row.library_nm,
        uploadDate: row.upload_date_conv || '-',
        size: row.file_size_conv || '-',
        downloadUrl: row.file_url || row.library_link || '',
        hasFile: !!row.file_url,
        hasLink: !!row.library_link,
      }));
      setMaterials(mapped);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?먮즺 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchMaterials();
  }, [effectiveCourseId]);

  const handleDownload = (material: any) => {
    const url = material.downloadUrl;
    if (!url) {
      alert('?ㅼ슫濡쒕뱶???뚯씪/留곹겕媛 ?놁뒿?덈떎.');
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const handleDelete = (libraryId: number, title: string) => {
    void (async () => {
      if (!effectiveCourseId) {
        alert('怨쇱젙 ID媛 ?놁뼱 ??젣?????놁뒿?덈떎. 怨쇱젙 留ㅽ븨 ???ㅼ떆 ?쒕룄??二쇱꽭??');
        return;
      }
      // ?? ??젣???섎룎由ш린 ?대졄湲??뚮Ц?? ?댁쁺 ?섍꼍?먯꽌??諛섎뱶???뺤씤????踰???諛쏆뒿?덈떎.
      const ok = confirm(`?먮즺 "${title}"??瑜? ??젣?섏떆寃좎뒿?덇퉴?`);
      if (!ok) return;

      try {
        const res = await tutorLmsApi.deleteMaterial({ courseId: effectiveCourseId, libraryId });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchMaterials();
        alert('??젣?섏뿀?듬땲??');
      } catch (e) {
        alert(e instanceof Error ? e.message : '??젣 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      }
    })();
  };

  const handleUpload = () => {
    void (async () => {
      const title = uploadForm.title.trim();
      const hasFile = !!uploadForm.file;
      const hasLink = !!uploadForm.link.trim();

      if (!title) {
        alert('?먮즺紐낆쓣 ?낅젰??二쇱꽭??');
        return;
      }
      if (!effectiveCourseId) {
        alert('怨쇱젙 ID媛 ?놁뼱 ?낅줈?쒗븷 ???놁뒿?덈떎. 怨쇱젙 留ㅽ븨 ???ㅼ떆 ?쒕룄??二쇱꽭??');
        return;
      }
      if (!hasFile && !hasLink) {
        alert('?먮즺 ?뚯씪 ?먮뒗 留곹겕 以??섎굹???꾩슂?⑸땲??');
        return;
      }

      setUploading(true);
      try {
        const res = await tutorLmsApi.uploadMaterial({
          courseId: effectiveCourseId,
          title,
          content: uploadForm.content,
          link: uploadForm.link,
          file: uploadForm.file,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        if (course?.sourceType === 'haksa') {
          try {
            await appendHaksaCurriculumContent({
              haksaKey,
              weekNumber: Number(uploadForm.weekNumber || 1),
              sessionNumber: Number(uploadForm.sessionNumber || 1),
              content: {
                type: 'document',
                title: uploadForm.title,
                description: uploadForm.content,
                link: uploadForm.link,
                fileName: uploadForm.file?.name || '',
              },
            });
          } catch (e) {
            alert(e instanceof Error ? e.message : '媛뺤쓽紐⑹감???먮즺瑜??깅줉?섎뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
          }
        }

        await fetchMaterials();
        setShowUploadModal(false);
        resetUploadForm();
        alert('?낅줈?쒕릺?덉뒿?덈떎.');
      } catch (e) {
        alert(e instanceof Error ? e.message : '?낅줈??以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setUploading(false);
      }
    })();
  };

  return (
    <>
      <div className="space-y-4">
        <div className="flex justify-end">
          <button
            onClick={() => setShowUploadModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Upload className="w-4 h-4" />
            <span>?먮즺 ?낅줈??</span>
          </button>
        </div>

        {errorMessage && (
          <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
            {errorMessage}
          </div>
        )}
        {resolvingCourseId && (
          <div className="p-4 bg-blue-50 border border-blue-200 text-blue-700 rounded-lg">
            怨쇱젙 留ㅽ븨 以묒엯?덈떎. ?좎떆留?湲곕떎??二쇱꽭??
          </div>
        )}

        {loading && (
          <div className="p-6 text-center text-gray-500">?먮즺 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>
        )}

        {!loading && materials.length === 0 && (
          <div className="p-10 text-center text-gray-500 border border-dashed border-gray-200 rounded-lg">
            ?깅줉???먮즺媛 ?놁뒿?덈떎. ?곗륫 ?곷떒?먯꽌 ?먮즺瑜??낅줈?쒗빐 二쇱꽭??
          </div>
        )}

        <div className="space-y-2">
          {materials.map((material) => (
            <div
              key={material.id}
              className="flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <FolderOpen className="w-5 h-5 text-blue-600" />
                <div>
                  <div className="text-gray-900">{material.title}</div>
                  <div className="text-sm text-gray-600">
                    {material.uploadDate} 쨌 {material.size}
                    {material.hasLink && !material.hasFile && <span className="ml-2">(留곹겕)</span>}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleDownload(material)}
                  className="px-3 py-1.5 text-sm text-blue-700 hover:bg-blue-50 rounded transition-colors"
                >
                  ?ㅼ슫濡쒕뱶
                </button>
                <button
                  onClick={() => handleDelete(material.id, material.title)}
                  className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  title="??젣"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {showUploadModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg w-full max-w-2xl max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-gray-900">?먮즺 ?낅줈??</h3>
              <button
                onClick={() => {
                  setShowUploadModal(false);
                  resetUploadForm();
                }}
                className="text-gray-500 hover:text-gray-700 transition-colors"
              >
                ?リ린
              </button>
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleUpload();
              }}
              className="p-6 space-y-6"
            >
              <div>
                <label className="block text-sm text-gray-700 mb-2">
                  ?먮즺紐?<span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={uploadForm.title}
                  onChange={(e) => setUploadForm({ ...uploadForm, title: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="?? 媛뺤쓽?먮즺.pdf"
                  required
                />
              </div>

              {/* ?? ?숈궗 怨쇰ぉ? ?먮즺 ?깅줉 ??二쇱감/李⑥떆 湲곗????꾩슂?⑸땲?? */}
              {showWeekSession && (
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm text-gray-700 mb-2">
                      二쇱감 <span className="text-red-500">*</span>
                    </label>
                    <select
                      value={uploadForm.weekNumber}
                      onChange={(e) =>
                        setUploadForm({ ...uploadForm, weekNumber: parseInt(e.target.value, 10) || 1 })
                      }
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      required
                    >
                      {Array.from({ length: weekCount }, (_, i) => i + 1).map((week) => (
                        <option key={week} value={week}>
                          {week}二쇱감
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm text-gray-700 mb-2">
                      李⑥떆 <span className="text-red-500">*</span>
                    </label>
                    <select
                      value={uploadForm.sessionNumber}
                      onChange={(e) =>
                        setUploadForm({ ...uploadForm, sessionNumber: parseInt(e.target.value, 10) || 1 })
                      }
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      required
                    >
                      {Array.from({ length: 10 }, (_, i) => i + 1).map((session) => (
                        <option key={session} value={session}>
                          {session}李⑥떆
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              )}

              <div>
                <label className="block text-sm text-gray-700 mb-2">?ㅻ챸</label>
                <textarea
                  value={uploadForm.content}
                  onChange={(e) => setUploadForm({ ...uploadForm, content: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  rows={3}
                  placeholder="자료에 대한 간단한 설명을 입력해 주세요"
                />
              </div>

              <div>
                <label className="block text-sm text-gray-700 mb-2">留곹겕(?좏깮)</label>
                <input
                  type="url"
                  value={uploadForm.link}
                  onChange={(e) => setUploadForm({ ...uploadForm, link: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="https://..."
                />
                <p className="text-sm text-gray-500 mt-1">?뚯씪 ?낅줈?????留곹겕留??깅줉???섎룄 ?덉뒿?덈떎.</p>
              </div>

              <div>
                <label className="block text-sm text-gray-700 mb-2">?뚯씪(?좏깮)</label>
                <input
                  type="file"
                  onChange={(e) =>
                    setUploadForm({ ...uploadForm, file: e.target.files?.[0] ?? null })
                  }
                  className="w-full"
                />
                <p className="text-sm text-gray-500 mt-1">?뚯씪 ?먮뒗 留곹겕 以??섎굹???꾩닔?낅땲??</p>
              </div>

              <div className="flex gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setShowUploadModal(false);
                    resetUploadForm();
                  }}
                  className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
                  disabled={uploading}
                >
                  痍⑥냼
                </button>
                <button
                  type="submit"
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-300"
                  disabled={uploading}
                >
                    {uploading ? '업로드 중...' : '업로드'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}

// Q&A ??
function QnaTab({ courseId, initialPostId }: { courseId: number; initialPostId?: number }) {
  const [keyword, setKeyword] = useState('');
  const [qnas, setQnas] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [selectedPostId, setSelectedPostId] = useState<number | null>(null);
  const [detail, setDetail] = useState<any | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [answerText, setAnswerText] = useState('');
  const initialAppliedRef = useRef(false);

  const toBool = (value: any) =>
    value === true || value === 1 || value === '1' || value === 'Y' || value === 'true';

  // ?? Q&A????湲/?듬????섏떆濡??앷린誘濡? 紐⑸줉? ??긽 ?쒕쾭(DB)?먯꽌 ?ㅼ떆 ?쎈뒗 諛⑹떇???덉쟾?⑸땲??
  const fetchQnas = async (params?: { keyword?: string }) => {
    if (!courseId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getQnas({ courseId, keyword: params?.keyword });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        id: Number(row.id),
        subject: row.subject,
        student: row.user_nm || row.login_id || '-',
        date: row.reg_date_conv || '-',
        answered: toBool(row.answered) || Number(row.proc_status ?? 0) === 1,
      }));
      setQnas(mapped);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : 'Q&A 紐⑸줉??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  const fetchDetail = async (postId: number) => {
    if (!courseId || !postId) return;
    setDetailLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getQnaDetail({ courseId, postId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const payload = Array.isArray(res.rst_data) ? res.rst_data[0] : res.rst_data;
      setDetail(payload ?? null);
      setAnswerText(String(payload?.answer_content ?? ''));
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : 'Q&A ?곸꽭瑜?遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    void fetchQnas({ keyword: keyword.trim() ? keyword.trim() : undefined });
  }, [courseId]);

  useEffect(() => {
    // ?? ??쒕낫??"理쒓렐 Q&A"?먯꽌 ?ㅼ뼱??寃쎌슦, Q&A ??뿉???대떦 湲 ?곸꽭濡?諛붾줈 ?댁뼱以띾땲??
    //     (二쇱냼 ?뚮씪誘명꽣???좏깮 ???뺣━?????덉뼱, 理쒖큹 1?뚮쭔 ?곸슜?⑸땲??)
    initialAppliedRef.current = false;
    setSelectedPostId(null);
    setDetail(null);
    setAnswerText('');
  }, [courseId]);

  useEffect(() => {
    if (!initialPostId) return;
    if (initialAppliedRef.current) return;
    initialAppliedRef.current = true;
    setSelectedPostId(initialPostId);
  }, [initialPostId]);

  useEffect(() => {
    if (!selectedPostId) return;
    void fetchDetail(selectedPostId);
  }, [courseId, selectedPostId]);

  const handleSaveAnswer = () => {
    if (!selectedPostId) return;
    const content = answerText.trim();
    if (!content) {
      alert('?듬? ?댁슜???낅젰??二쇱꽭??');
      return;
    }

    void (async () => {
      try {
        const res = await tutorLmsApi.answerQna({ courseId, postId: selectedPostId, content });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchDetail(selectedPostId);
        await fetchQnas({ keyword: keyword.trim() ? keyword.trim() : undefined });
        alert('??λ릺?덉뒿?덈떎.');
      } catch (e) {
        alert(e instanceof Error ? e.message : '???以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      }
    })();
  };

  if (selectedPostId) {
    const answered = detail ? toBool(detail.answered) : false;

    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={() => {
                setSelectedPostId(null);
                setDetail(null);
                setAnswerText('');
              }}
              className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <h3 className="text-xl text-gray-900">Q&A</h3>
              <p className="text-sm text-gray-600">吏덈Ц ?곸꽭 諛??듬?</p>
            </div>
          </div>
          <span
            className={`px-3 py-1 text-xs rounded-full ${
              answered ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'
            }`}
          >
            {answered ? '?듬??꾨즺' : '?湲곗쨷'}
          </span>
        </div>

        {errorMessage && (
          <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
            {errorMessage}
          </div>
        )}

        {detailLoading && (
          <div className="p-6 text-center text-gray-500">?곸꽭瑜?遺덈윭?ㅻ뒗 以?..</div>
        )}

        {detail && (
          <div className="space-y-4">
            <div className="border border-gray-200 rounded-lg">
              <div className="bg-gray-50 px-4 py-3 border-b border-gray-200">
                <div className="text-gray-900 mb-1">{detail.subject}</div>
                <div className="text-sm text-gray-600">
                  {detail.question_user_nm} 쨌 {detail.question_reg_date_conv || '-'}
                </div>
              </div>
              <div
                className="p-4 text-sm text-gray-800 prose max-w-none"
                dangerouslySetInnerHTML={{ __html: detail.question_content || '' }}
              />
            </div>

            <div className="border border-gray-200 rounded-lg">
              <div className="bg-gray-50 px-4 py-3 border-b border-gray-200 flex items-center justify-between">
                <div className="text-gray-900">?듬?</div>
                <div className="text-xs text-gray-500">
                  {detail.answer_reg_date_conv ? `理쒓렐 ??? ${detail.answer_reg_date_conv}` : ''}
                </div>
              </div>
              <div className="p-4 space-y-3">
                <textarea
                  value={answerText}
                  onChange={(e) => setAnswerText(e.target.value)}
                  rows={6}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                  placeholder="?듬? ?댁슜???낅젰?섏꽭??.."
                />
                <div className="flex justify-end">
                  <button
                    onClick={handleSaveAnswer}
                    className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                  >
                    {answered ? '?듬? ?섏젙' : '?듬? ?깅줉'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="寃?됱뼱(?쒕ぉ)"
          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          onKeyDown={(e) => {
            if (e.key === 'Enter') void fetchQnas({ keyword: keyword.trim() ? keyword.trim() : undefined });
          }}
        />
        <button
          onClick={() => void fetchQnas({ keyword: keyword.trim() ? keyword.trim() : undefined })}
          className="px-4 py-2 bg-gray-900 text-white rounded-lg hover:bg-gray-800 transition-colors"
        >
          寃??
        </button>
      </div>

      {errorMessage && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {errorMessage}
        </div>
      )}

      {loading && <div className="p-6 text-center text-gray-500">Q&A 紐⑸줉??遺덈윭?ㅻ뒗 以?..</div>}

      {!loading && qnas.length === 0 && (
        <div className="p-10 text-center text-gray-500 border border-dashed border-gray-200 rounded-lg">
          Q&A 湲???놁뒿?덈떎.
        </div>
      )}

      <div className="space-y-4">
        {qnas.map((qna) => (
          <button
            key={qna.id}
            onClick={() => setSelectedPostId(qna.id)}
            className="w-full text-left p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <div className="flex items-start justify-between mb-2">
              <div className="flex-1">
                <div className="text-gray-900 mb-1">{qna.subject}</div>
                <div className="text-sm text-gray-600">
                  {qna.student} 쨌 {qna.date}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className={`px-3 py-1 text-xs rounded-full ${
                    qna.answered ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'
                  }`}
                >
                  {qna.answered ? '?듬??꾨즺' : '?湲곗쨷'}
                </span>
                <ChevronRight className="w-5 h-5 text-gray-400" />
              </div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

// ?깆쟻愿由???
function GradesTab({ courseId }: { courseId: number }) {
  const [grades, setGrades] = useState<any[]>([]);
  const [courseInfo, setCourseInfo] = useState<any | null>(null);
  const [distributionRows, setDistributionRows] = useState<any[]>([]);
  const [distributionSummary, setDistributionSummary] = useState<any | null>(null);

  const [loading, setLoading] = useState(false);
  const [recalcLoading, setRecalcLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const toNum = (value: any, fallback = 0) => {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
  };

  const getCourseValue = (key: string) => {
    // ?? ?쒕쾭?먯꽌 ?대젮?ㅻ뒗 DataSet 而щ읆紐낆씠 ?섍꼍???곕씪 ?臾몄옄(ASSIGN_EXAM)濡??대젮?????덉뼱,
    //     ?꾨줎?몄뿉?쒕뒗 ?뚮Ц???臾몄옄 ?ㅻ? 紐⑤몢 吏?먰빐 ?붾㈃ ?쒖떆瑜??덉젙?뷀빀?덈떎.
    //     ?먰븳 DataSet??JSON?쇰줈 蹂?섎맆 ??諛곗뿴 ?뺥깭濡??대젮?????덉뼱 泥?踰덉㎏ ?붿냼濡??묎렐?⑸땲??
    if (!courseInfo) return undefined;
    const obj = Array.isArray(courseInfo) ? courseInfo[0] : courseInfo;
    if (!obj || typeof obj !== 'object') return undefined;
    return (obj as any)[key] ?? (obj as any)[key.toUpperCase()];
  };

  // ?? ?깆쟻 ?붾㈃? "?꾩옱 DB ?먯닔"媛 湲곗??대?濡? ??吏꾩엯/?ш퀎???꾩뿉???쒕쾭?먯꽌 ?ㅼ떆 遺덈윭?듬땲??
  const fetchGrades = async () => {
    if (!courseId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getGrades({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const rows = res.rst_data ?? [];
      const mapped = rows.map((row: any) => ({
        courseUserId: Number(row.course_user_id),
        studentId: row.login_id,
        name: row.user_nm,
        progressRatio: toNum(row.progress_ratio, 0),
        examScore: toNum(row.exam_score, 0),
        homeworkScore: toNum(row.homework_score, 0),
        etcScore: toNum(row.etc_score, 0),
        totalScore: toNum(row.total_score, 0),
        statusLabel: row.status_label || '',
      }));
      setGrades(mapped);
      setCourseInfo(res.rst_course ?? null);

      // ?? ?깆쟻 遺꾪룷 洹몃옒?꾧? ?쒕쾭 吏묎퀎? ?숈씪?댁빞 ?ㅼ젣 ?깆쟻?쒖? 遺덉씪移섍? ?섏? ?딆뒿?덈떎.
      const distRes = await tutorLmsApi.getGradesDistribution({ courseId });
      if (distRes.rst_code === '0000') {
        setDistributionRows(distRes.rst_data ?? []);
        const summary = Array.isArray(distRes.rst_summary) ? distRes.rst_summary[0] : distRes.rst_summary;
        setDistributionSummary(summary ?? null);
      } else {
        setDistributionRows([]);
        setDistributionSummary(null);
      }
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?깆쟻??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchGrades();
  }, [courseId]);

  const getStatusBadge = (label: string) => {
    if (label === '?⑷꺽') return 'bg-green-100 text-green-700';
    if (label === '?섎즺') return 'bg-blue-100 text-blue-700';
    return 'bg-red-100 text-red-700';
  };

  const completionCriteria = {
    progressRate: toNum(getCourseValue('complete_limit_progress'), 0),
    totalScore: toNum(getCourseValue('complete_limit_total_score'), 0),
  };
  const passEnabled = String(getCourseValue('pass_yn') || '') === 'Y';
  const passCriteria = {
    progressRate: toNum(getCourseValue('limit_progress'), 0),
    totalScore: toNum(getCourseValue('limit_total_score'), 0),
  };

  const scoreWeights = {
    progress: toNum(getCourseValue('assign_progress'), 0),
    exam: toNum(getCourseValue('assign_exam'), 0),
    final: toNum(getCourseValue('assign_final'), 0),
    homework: toNum(getCourseValue('assign_homework'), 0),
    etc: toNum(getCourseValue('assign_etc'), 0),
    forum: toNum(getCourseValue('assign_forum'), 0),
  };
  const scoreWeightSum =
    scoreWeights.progress + scoreWeights.exam + scoreWeights.final + scoreWeights.homework + scoreWeights.etc + scoreWeights.forum;

  const handleRecalc = () => {
    void (async () => {
      // ?? ?ш퀎?곗? ?꾩껜 ?섍컯???먯닔/珥앹젏???ㅼ떆 怨꾩궛?섎?濡??쒓컙??嫄몃┫ ???덉뼱, 紐낆떆?곸쑝濡??뚮????뚮쭔 ?ㅽ뻾?⑸땲??
      const ok = confirm('?깆쟻???ш퀎?고븯?쒓쿋?듬땲源?\n\n(?쒗뿕/怨쇱젣 ?먯닔, 吏꾨룄???깆쓣 湲곗??쇰줈 珥앹젏???ㅼ떆 怨꾩궛?⑸땲??)');
      if (!ok) return;

      setRecalcLoading(true);
      try {
        const res = await tutorLmsApi.recalcGrades({ courseId });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchGrades();
        alert('?ш퀎?곗씠 ?꾨즺?섏뿀?듬땲??');
      } catch (e) {
        alert(e instanceof Error ? e.message : '?ш퀎??以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setRecalcLoading(false);
      }
    })();
  };

  const handleDownloadGrades = () => {
    // ?? ?깆쟻?쒕뒗 ?쒗쁽???붾㈃??蹂댁씠??寃곌낵?앷? 以묒슂?섎?濡? ?붾㈃ ?곹깭(grades)瑜?洹몃?濡?CSV濡??대젮諛쏆뒿?덈떎.
    const ymd = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const filename = `course_${courseId}_grades_${ymd}.csv`;

    const headers = ['No', 'course_user_id', '학번', '이름', '출석', '중간', '기말', '과제', '기타', '참여도', '총점', '상태'];
    const rows = grades.map((g, index) => ([
      index + 1,
      g.courseUserId ?? '',
      g.studentId ?? '',
      g.name ?? '',
      Math.round(toNum(g.progressRatio, 0)),
      toNum(g.examScore, 0),
      toNum((g as any).finalScore, 0),
      toNum(g.homeworkScore, 0),
      toNum(g.etcScore, 0),
      toNum((g as any).forumScore, 0),
      toNum(g.totalScore, 0),
      g.statusLabel ?? '',
    ]));

    downloadCsv(filename, headers, rows);
  };

  return (
    <div>
      <div className="mb-4 flex justify-between items-center">
        <div className="text-sm text-gray-600">?깆쟻 議고쉶 諛?愿由?</div>
        <div className="flex gap-2">
          <button
            onClick={handleRecalc}
            disabled={recalcLoading}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-300"
          >
            <Play className="w-4 h-4" />
              <span>{recalcLoading ? '재계산 중...' : '성적 재계산'}</span>
          </button>
          <button
            onClick={handleDownloadGrades}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
          >
            <Download className="w-4 h-4" />
            <span>?깆쟻???ㅼ슫濡쒕뱶(CSV)</span>
          </button>
        </div>
      </div>

      {/* 湲곗? ?덈궡 */}
      <div className="mb-4 p-4 bg-gray-50 border border-gray-200 rounded-lg">
        <div className="mb-3 text-sm">
          <div className="text-gray-700 mb-1">諛곗젏 鍮꾩쑉</div>
          <div className="text-gray-600">
            異쒖꽍 {scoreWeights.progress} / 以묎컙 {scoreWeights.exam} / 湲곕쭚 {scoreWeights.final} / 怨쇱젣 {scoreWeights.homework}
             / 湲고? {scoreWeights.etc} / 李몄뿬??{scoreWeights.forum}
            {scoreWeightSum > 0 ? ` (?⑷퀎 ${scoreWeightSum})` : ''}
          </div>
        </div>
        <div className={`grid gap-4 text-sm ${passEnabled ? 'grid-cols-2' : 'grid-cols-1'}`}>
          <div>
            <div className="text-gray-700 mb-1">?섎즺 湲곗?</div>
            <div className="text-gray-600">
              吏꾨룄??{completionCriteria.progressRate}% ?댁긽
              {completionCriteria.totalScore > 0 ? `, 珥앹젏 ${completionCriteria.totalScore}???댁긽` : ''}
            </div>
          </div>
          {passEnabled && (
            <div>
              <div className="text-gray-700 mb-1">?⑷꺽 湲곗?</div>
              <div className="text-gray-600">
                吏꾨룄??{passCriteria.progressRate}% ?댁긽{passCriteria.totalScore > 0 ? `, 珥앹젏 ${passCriteria.totalScore}???댁긽` : ''}
              </div>
            </div>
          )}
        </div>
      </div>

      {errorMessage && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {errorMessage}
        </div>
      )}

      {loading && <div className="p-6 text-center text-gray-500">?깆쟻??遺덈윭?ㅻ뒗 以?..</div>}

      {!loading && (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm text-gray-700">?대쫫</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?숇쾲</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">異쒖꽍</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">以묎컙</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">湲곕쭚</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">怨쇱젣</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">湲고?</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">李몄뿬??</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">珥앹젏</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">寃곌낵</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {grades.map((grade) => (
                <tr key={grade.courseUserId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-4 text-sm text-gray-900">{grade.name}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-600">{grade.studentId}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">
                    {Math.round(grade.progressRatio * 10) / 10}
                  </td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">{grade.examScore}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">{(grade as any).finalScore ?? 0}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">{grade.homeworkScore}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">{grade.etcScore}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">{(grade as any).forumScore ?? 0}</td>
                  <td className="px-4 py-4 text-center">
                    <span className="inline-flex px-3 py-1 bg-blue-100 text-blue-700 rounded-full">
                      {Math.round(grade.totalScore * 100) / 100}
                    </span>
                  </td>
                  <td className="px-4 py-4 text-center">
                    <span className={`inline-flex px-3 py-1 rounded-full ${getStatusBadge(grade.statusLabel)}`}>
                      {grade.statusLabel || '誘몃떖'}
                    </span>
                  </td>
                </tr>
              ))}

              {grades.length === 0 && (
                <tr>
                  <td colSpan={10} className="px-4 py-10 text-center text-gray-500">
                    ?깆쟻 ?곗씠?곌? ?놁뒿?덈떎.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* ?깆쟻 遺꾪룷 洹몃옒??*/}
      {!loading && grades.length > 0 && (
        <GradeDistributionChart
          grades={grades}
          distributionRows={distributionRows}
          summary={distributionSummary}
        />
      )}
    </div>
  );
}

// ?깆쟻 遺꾪룷 洹몃옒??(CSS-only 媛濡?諛?李⑦듃)
function GradeDistributionChart({
  grades,
  distributionRows,
  summary,
}: {
  grades: any[];
  distributionRows?: any[];
  summary?: any | null;
}) {
  // ?? 10???⑥쐞 援ш컙蹂??숈깮 ?섎? ?몄뼱 諛?李⑦듃濡??쒓컖?뷀빀?덈떎.
  const bins = [
    { label: '90~100', min: 90, max: 100 },
    { label: '80~89', min: 80, max: 89.99 },
    { label: '70~79', min: 70, max: 79.99 },
    { label: '60~69', min: 60, max: 69.99 },
    { label: '50~59', min: 50, max: 59.99 },
    { label: '40~49', min: 40, max: 49.99 },
    { label: '30~39', min: 30, max: 39.99 },
    { label: '20~29', min: 20, max: 29.99 },
    { label: '10~19', min: 10, max: 19.99 },
    { label: '0~9', min: 0, max: 9.99 },
  ];

  const fallbackBinCounts = bins.map(bin => ({
    ...bin,
    count: grades.filter(g => {
      const score = Number(g.totalScore) || 0;
      return score >= bin.min && score <= bin.max;
    }).length,
  }));
  const binCounts = (distributionRows && distributionRows.length > 0)
    ? distributionRows.map((row: any) => ({
      label: row.bucket_label || `${Number(row.min_score ?? 0)}~${Number(row.max_score ?? 0)}`,
      count: Number(row.student_count ?? 0),
    }))
    : fallbackBinCounts;

  const maxCount = Math.max(...binCounts.map(b => b.count), 1);
  const total = Number(summary?.total_count ?? grades.length ?? 0);

  // ?됯퇏, 理쒓퀬, 理쒖?
  const scores = grades.map(g => Number(g.totalScore) || 0);
  const avg = Number(summary?.avg_score ?? (scores.length > 0 ? scores.reduce((a, b) => a + b, 0) / scores.length : 0));
  const maxScore = Number(summary?.max_score ?? (scores.length > 0 ? Math.max(...scores) : 0));
  const minScore = Number(summary?.min_score ?? (scores.length > 0 ? Math.min(...scores) : 0));

  const barColors = [
    'bg-blue-600', 'bg-blue-500', 'bg-blue-400', 'bg-sky-500',
    'bg-emerald-500', 'bg-yellow-500', 'bg-orange-400', 'bg-orange-500',
    'bg-red-400', 'bg-red-500',
  ];

  return (
    <div className="mt-6 p-5 bg-white border border-gray-200 rounded-xl">
      <h4 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
        <BarChart3 className="w-5 h-5 text-blue-600" />
        ?깆쟻 遺꾪룷
      </h4>

      {/* ?듦퀎 ?붿빟 */}
      <div className="grid grid-cols-4 gap-3 mb-5">
        <div className="p-3 bg-blue-50 rounded-lg text-center">
          <div className="text-xs text-blue-600 mb-1">?섍컯??</div>
          <div className="text-lg font-bold text-blue-900">{total}紐?</div>
        </div>
        <div className="p-3 bg-green-50 rounded-lg text-center">
          <div className="text-xs text-green-600 mb-1">?됯퇏</div>
          <div className="text-lg font-bold text-green-900">{avg.toFixed(1)}??</div>
        </div>
        <div className="p-3 bg-purple-50 rounded-lg text-center">
          <div className="text-xs text-purple-600 mb-1">理쒓퀬??</div>
          <div className="text-lg font-bold text-purple-900">{maxScore.toFixed(1)}??</div>
        </div>
        <div className="p-3 bg-orange-50 rounded-lg text-center">
          <div className="text-xs text-orange-600 mb-1">理쒖???</div>
          <div className="text-lg font-bold text-orange-900">{minScore.toFixed(1)}??</div>
        </div>
      </div>

      {/* 諛?李⑦듃 */}
      <div className="space-y-2">
        {binCounts.map((bin, idx) => {
          const pct = maxCount > 0 ? (bin.count / maxCount) * 100 : 0;
          const ratio = total > 0 ? ((bin.count / total) * 100).toFixed(0) : '0';
          return (
            <div key={bin.label} className="flex items-center gap-3">
              <div className="w-14 text-right text-xs font-medium text-gray-500">{bin.label}</div>
              <div className="flex-1 h-7 bg-gray-100 rounded-md overflow-hidden relative">
                <div
                  className={`h-full ${barColors[idx]} rounded-md transition-all duration-500 ease-out`}
                  style={{ width: `${pct}%`, minWidth: bin.count > 0 ? '2px' : '0' }}
                />
                {bin.count > 0 && (
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-medium text-gray-600">
                    {bin.count}紐?({ratio}%)
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ?섎즺愿由???API ?곕룞)
function CompletionTab({ courseId, course }: { courseId: number; course?: any }) {
  // ?숈궗 怨쇰ぉ ?щ? 泥댄겕
  const isHaksaCourse =
    course?.sourceType === 'haksa' && (!course?.mappedCourseId || Number.isNaN(courseId) || courseId <= 0);
  const [selectedCourseUserIds, setSelectedCourseUserIds] = useState<number[]>([]);

  const [rows, setRows] = useState<any[]>([]);
  const [courseInfo, setCourseInfo] = useState<any | null>(null);

  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const toNum = (value: any, fallback = 0) => {
    const n = Number(value);
    return Number.isFinite(n) ? n : fallback;
  };

  const getCourseValue = (key: string) => {
    // ?? DataSet??JSON?쇰줈 蹂?섎맆 ??諛곗뿴 ?뺥깭濡??대젮?????덉뼱 泥?踰덉㎏ ?붿냼濡??묎렐?⑸땲??
    if (!courseInfo) return undefined;
    const obj = Array.isArray(courseInfo) ? courseInfo[0] : courseInfo;
    if (!obj || typeof obj !== 'object') return undefined;
    return (obj as any)[key] ?? (obj as any)[key.toUpperCase()];
  };

  // ?? ?섎즺/醫낅즺/利앸챸??異쒕젰? "?댁쁺 DB ?곹깭"媛 湲곗??대?濡? ?붾㈃ 吏꾩엯/泥섎━ ?꾩뿉??諛섎뱶???ㅼ떆 議고쉶?⑸땲??
  const fetchCompletions = async () => {
    if (!courseId) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getCompletions({ courseId });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const list = res.rst_data ?? [];
      const mapped = list.map((row: any) => ({
        courseUserId: Number(row.course_user_id),
        studentId: row.login_id,
        name: row.user_nm,
        progressRatio: toNum(row.progress_ratio, 0),
        totalScore: toNum(row.total_score, 0),
        completeStatus: String(row.complete_status || ''), //P/C/F/''
        completeYn: String(row.complete_yn || ''),
        completeDate: row.complete_date_conv || '-',
        closeYn: String(row.close_yn || ''),
        closeDate: row.close_date_conv || '-',
        statusLabel: row.status_label || '',
      }));
      setRows(mapped);
      setCourseInfo(res.rst_course ?? null);

      // ?좏깮????ぉ??紐⑸줉?먯꽌 ?щ씪吏?寃쎌슦(?곹깭 蹂???? ?좏깮???뺣━?⑸땲??
      setSelectedCourseUserIds((prev) =>
        prev.filter((id) => mapped.some((r: any) => r.courseUserId === id))
      );
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?섎즺 ?뺣낫瑜?遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchCompletions();
  }, [courseId]);

  const completionCriteria = {
    progressRate: toNum(getCourseValue('complete_limit_progress'), 0),
    totalScore: toNum(getCourseValue('complete_limit_total_score'), 0),
  };
  const passEnabled = String(getCourseValue('pass_yn') || '') === 'Y';
  const passCriteria = {
    progressRate: toNum(getCourseValue('limit_progress'), 0),
    totalScore: toNum(getCourseValue('limit_total_score'), 0),
  };

  const canPrintCompletion = (row: any) => row.completeStatus === 'C' || row.completeStatus === 'P';
  const canPrintPass = (row: any) => row.completeStatus === 'P';

  const getStatusBadge = (label: string) => {
    if (label === '?⑷꺽') return 'bg-green-100 text-green-700';
    if (label === '?섎즺') return 'bg-blue-100 text-blue-700';
    if (label === '醫낅즺') return 'bg-gray-100 text-gray-700';
    if (label === '미수료') return 'bg-red-100 text-red-700';
    return 'bg-yellow-100 text-yellow-800';
  };

  const handleSelectAll = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.checked) {
      setSelectedCourseUserIds(rows.map((r) => r.courseUserId));
    } else {
      setSelectedCourseUserIds([]);
    }
  };

  const handleSelectStudent = (courseUserId: number) => {
    setSelectedCourseUserIds((prev) =>
      prev.includes(courseUserId) ? prev.filter((id) => id !== courseUserId) : [...prev, courseUserId]
    );
  };

  const handleAction = (action: 'complete_y' | 'complete_n' | 'close_y' | 'close_n', label: string) => {
    if (selectedCourseUserIds.length === 0) {
      alert('泥섎━???숈깮???좏깮??二쇱꽭??');
      return;
    }

    void (async () => {
      const ok = confirm(`${label}를 실행하시겠습니까?\n\n선택 인원: ${selectedCourseUserIds.length}명`);
      if (!ok) return;

      setActionLoading(true);
      try {
        const res = await tutorLmsApi.updateCompletion({ courseId, action, courseUserIds: selectedCourseUserIds });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        await fetchCompletions();
        alert('泥섎━媛 ?꾨즺?섏뿀?듬땲??');
      } catch (e) {
        alert(e instanceof Error ? e.message : '泥섎━ 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      } finally {
        setActionLoading(false);
      }
    })();
  };

  const openCertificate = (courseUserId: number, type: 'C' | 'P') => {
    // ?? ?앹뾽 李⑤떒???쇳븯?ㅻ㈃(釉뚮씪?곗? ?뺤콉), ?대┃ 吏곹썑??李쎌쓣 癒쇱? ?댁뼱 ????URL??梨꾩썙???⑸땲??
    const win = window.open('', '_blank');
    if (!win) {
      alert('?앹뾽??李⑤떒?섏뿀?듬땲?? 釉뚮씪?곗??먯꽌 ?앹뾽 ?덉슜 ???ㅼ떆 ?쒕룄??二쇱꽭??');
      return;
    }

    void (async () => {
      try {
        const res = await tutorLmsApi.issueCertificate({ courseUserId, type });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        const url = res.rst_data;
        if (!url) throw new Error('?몄뇙 URL??諛쏆? 紐삵뻽?듬땲??');
        win.location.href = url;
      } catch (e) {
        try { win.close(); } catch (ignore) {}
        alert(e instanceof Error ? e.message : '利앸챸??異쒕젰 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      }
    })();
  };

  const handlePrintBulk = (type: 'C' | 'P') => {
    if (selectedCourseUserIds.length === 0) {
      alert('異쒕젰???숈깮???좏깮??二쇱꽭??');
      return;
    }

    const selectedRows = rows.filter((r) => selectedCourseUserIds.includes(r.courseUserId));
    const eligible = selectedRows.filter((r) => (type === 'P' ? canPrintPass(r) : canPrintCompletion(r)));

    if (eligible.length === 0) {
      alert(type === 'P' ? '?⑷꺽利앹쓣 異쒕젰????곸씠 ?놁뒿?덈떎.' : '?섎즺利앹쓣 異쒕젰????곸씠 ?놁뒿?덈떎.');
      return;
    }

    if (eligible.length > 20) {
      alert('??踰덉뿉 ?덈Т 留롮씠 異쒕젰?섎㈃ ?앹뾽 李⑤떒???????덉뒿?덈떎. 20紐??댄븯濡??섎닠??異쒕젰??二쇱꽭??');
      return;
    }

    // ?앹뾽? ?숆린?곸쑝濡?癒쇱? ?댁뼱 ?〓땲??
    const opened = eligible.map((r) => ({
      row: r,
      win: window.open('', '_blank'),
    }));

    if (opened.some((x) => !x.win)) {
      opened.forEach((x) => {
        try { x.win?.close(); } catch (ignore) {}
      });
      alert('?앹뾽??李⑤떒?섏뿀?듬땲?? 釉뚮씪?곗??먯꽌 ?앹뾽 ?덉슜 ???ㅼ떆 ?쒕룄??二쇱꽭??');
      return;
    }

    void (async () => {
      const errors: string[] = [];
      for (const x of opened) {
        try {
          const res = await tutorLmsApi.issueCertificate({ courseUserId: x.row.courseUserId, type });
          if (res.rst_code !== '0000') throw new Error(res.rst_message);
          const url = res.rst_data;
          if (!url) throw new Error('?몄뇙 URL??諛쏆? 紐삵뻽?듬땲??');
          x.win!.location.href = url;
        } catch (e) {
          try { x.win?.close(); } catch (ignore) {}
          errors.push(`${x.row.name}: ${e instanceof Error ? e.message : '오류'}`);
        }
      }
      if (errors.length > 0) {
        alert(`?쇰? 異쒕젰???ㅽ뙣?덉뒿?덈떎.\n\n${errors.join('\n')}`);
      }
    })();
  };

  const passEligibleCount = rows.filter(
    (r) => selectedCourseUserIds.includes(r.courseUserId) && canPrintPass(r)
  ).length;

  // ?숈궗 怨쇰ぉ: A/B/C/D/F ?깆쟻 ?먯젙 UI
  if (isHaksaCourse) {
    return (
      <HaksaGradingContent course={course} />
    );
  }

  // ?꾨━利?怨쇰ぉ: 湲곗〈 ?섎즺/怨쇰씫 ?먯젙 UI
  return (
    <div>
      <div className="mb-4 p-4 bg-gray-50 border border-gray-200 rounded-lg">
        <div className={`grid gap-4 text-sm ${passEnabled ? 'grid-cols-2' : 'grid-cols-1'}`}>
          <div>
            <div className="text-gray-700 mb-1">?섎즺 湲곗?</div>
            <div className="text-gray-600">
              吏꾨룄??{completionCriteria.progressRate}% ?댁긽
              {completionCriteria.totalScore > 0 ? `, 珥앹젏 ${completionCriteria.totalScore}???댁긽` : ''}
            </div>
          </div>
          {passEnabled && (
            <div>
              <div className="text-gray-700 mb-1">?⑷꺽 湲곗?</div>
              <div className="text-gray-600">
                吏꾨룄??{passCriteria.progressRate}% ?댁긽{passCriteria.totalScore > 0 ? `, 珥앹젏 ${passCriteria.totalScore}???댁긽` : ''}
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="mb-4 flex flex-wrap gap-2 justify-end">
        <button
          onClick={() => void fetchCompletions()}
          className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
          disabled={loading || actionLoading}
        >
          ?덈줈怨좎묠
        </button>
        <button
          onClick={() => handleAction('complete_y', passEnabled ? '?섎즺/?⑷꺽 泥섎━' : '?섎즺 泥섎━')}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-300"
          disabled={actionLoading}
        >
          {passEnabled ? '?섎즺/?⑷꺽 泥섎━' : '?섎즺 泥섎━'}
        </button>
          <button
            onClick={() => handleAction('complete_n', '판정 초기화')}
            className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors disabled:bg-gray-200"
            disabled={actionLoading}
          >
          ?먯젙 珥덇린??
        </button>
        <button
          onClick={() => handleAction('close_y', '醫낅즺(留덇컧) 泥섎━')}
          className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors disabled:bg-gray-300"
          disabled={actionLoading}
        >
          醫낅즺(留덇컧)
        </button>
        <button
          onClick={() => handleAction('close_n', '醫낅즺 ?댁젣')}
          className="px-4 py-2 border border-purple-200 text-purple-700 rounded-lg hover:bg-purple-50 transition-colors disabled:bg-gray-200"
          disabled={actionLoading}
        >
          醫낅즺 ?댁젣
        </button>
      </div>

      <div className="mb-4 flex gap-2 justify-end">
        <button
          onClick={() => handlePrintBulk('C')}
          disabled={selectedCourseUserIds.length === 0}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
        >
          <Download className="w-4 h-4" />
          <span>?섎즺利??쇨큵異쒕젰 ({selectedCourseUserIds.length})</span>
        </button>
        {passEnabled && (
          <button
            onClick={() => handlePrintBulk('P')}
            disabled={selectedCourseUserIds.length === 0}
            className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            <Download className="w-4 h-4" />
            <span>?⑷꺽利??쇨큵異쒕젰 ({passEligibleCount})</span>
          </button>
        )}
      </div>

      {errorMessage && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {errorMessage}
        </div>
      )}

      {loading && <div className="p-6 text-center text-gray-500">?섎즺 ?뺣낫瑜?遺덈윭?ㅻ뒗 以?..</div>}

      {!loading && (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-center">
                  <input
                    type="checkbox"
                    onChange={handleSelectAll}
                    checked={rows.length > 0 && selectedCourseUserIds.length === rows.length}
                    className="w-4 h-4 text-blue-600 rounded"
                  />
                </th>
                <th className="px-4 py-3 text-left text-sm text-gray-700">?대쫫</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?숇쾲</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">吏꾨룄??</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">珥앹젏</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?곹깭</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">?먯젙/醫낅즺</th>
                <th className="px-4 py-3 text-center text-sm text-gray-700">利앸챸??異쒕젰</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {rows.map((data: any) => (
                <tr key={data.courseUserId} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-4 text-center">
                    <input
                      type="checkbox"
                      checked={selectedCourseUserIds.includes(data.courseUserId)}
                      onChange={() => handleSelectStudent(data.courseUserId)}
                      className="w-4 h-4 text-blue-600 rounded"
                    />
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-900">{data.name}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-600">{data.studentId}</td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">
                    {Math.round(data.progressRatio * 10) / 10}%
                  </td>
                  <td className="px-4 py-4 text-center text-sm text-gray-900">
                    {Math.round(data.totalScore * 100) / 100}??
                  </td>
                  <td className="px-4 py-4 text-center">
                      <span className={`inline-flex px-3 py-1 rounded-full text-xs ${getStatusBadge(data.statusLabel)}`}>
                        {data.statusLabel || '미판정'}
                      </span>
                  </td>
                  <td className="px-4 py-4 text-center text-xs text-gray-600">
                    <div>?먯젙: {data.completeStatus || '-'}</div>
                    <div>醫낅즺: {data.closeYn || '-'}</div>
                  </td>
                  <td className="px-4 py-4 text-center">
                    <div className="flex gap-2 justify-center">
                      <button
                        onClick={() => openCertificate(data.courseUserId, 'C')}
                        disabled={!canPrintCompletion(data)}
                        className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
                      >
                        ?섎즺利?
                      </button>
                      {passEnabled && (
                        <button
                          onClick={() => openCertificate(data.courseUserId, 'P')}
                          disabled={!canPrintPass(data)}
                          className="px-3 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700 transition-colors disabled:bg-gray-300 disabled:cursor-not-allowed"
                        >
                          ?⑷꺽利?
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}

              {rows.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-10 text-center text-gray-500">
                    ?섎즺 ?곗씠?곌? ?놁뒿?덈떎.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ?숈궗 怨쇰ぉ ?쒗뿕 愿由?而댄룷?뚰듃
function HaksaExamContent({
  haksaKey,
  haksaExams,
  setHaksaExams,
  weekCount,
}: {
  haksaKey: HaksaCourseKey | null;
  haksaExams: any[];
  setHaksaExams: React.Dispatch<React.SetStateAction<any[]>>;
  weekCount: number;
}) {
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingExam, setEditingExam] = useState<any | null>(null); // ?섏젙 以묒씤 ?쒗뿕
  const [examList, setExamList] = useState<any[]>([]);
  const [selectedExamId, setSelectedExamId] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedWeek, setSelectedWeek] = useState(1);
  const [selectedSession, setSelectedSession] = useState(1);
  
  // ?ㅻ뒛 ?좎쭨 湲곕낯媛?
  const today = new Date().toISOString().split('T')[0];
  const nextWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  
  const [examSettings, setExamSettings] = useState({
    startDate: today,
    startTime: '09:00',
    endDate: nextWeek,
    endTime: '18:00',
    points: 0,
    allowRetake: false,
    retakeScore: 0,
    retakeCount: 0,
    showResults: true,
  });

  // ?쒗뿕愿由??쒗뵆由? 紐⑸줉 遺덈윭?ㅺ린
  useEffect(() => {
    if (!showAddModal) return;
    let cancelled = false;

    const fetchTemplates = async () => {
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getExamTemplates({ limit: 200 });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        const rows = res.rst_data ?? [];
        const mapped = rows.map((row: any) => ({
          id: String(row.id),
          title: row.exam_nm || '?쒗뿕',
          description: row.content || '',
          questionCount: Number(row.question_cnt ?? 0),
          totalPoints: Number(row.total_points ?? 0),
        }));
        if (!cancelled) setExamList(mapped);
      } catch (e) {
        if (!cancelled) {
          setExamList([]);
          setErrorMessage(e instanceof Error ? e.message : '?쒗뿕 ?쒗뵆由우쓣 遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      }
    };

    void fetchTemplates();
    return () => {
      cancelled = true;
    };
  }, [showAddModal]);

  // ?좏깮???쒗뿕 ?뺣낫
  const selectedExam = examList.find(e => e.id === selectedExamId);

  useEffect(() => {
    if (selectedExam) {
      setExamSettings(prev => ({ ...prev, points: selectedExam.totalPoints || 0 }));
    }
  }, [selectedExam]);

  const resetSettings = () => {
    setSelectedExamId('');
    setSelectedWeek(1);
    setSelectedSession(1);
    setExamSettings({
      startDate: today,
      startTime: '09:00',
      endDate: nextWeek,
      endTime: '18:00',
      points: 0,
      allowRetake: false,
      retakeScore: 0,
      retakeCount: 0,
      showResults: true,
    });
  };

  const persistHaksaExams = async (next: any[]) => {
    setHaksaExams(next);
    if (!haksaKey) return;
    try {
      const res = await tutorLmsApi.updateHaksaExams({
        ...haksaKey,
        examsJson: JSON.stringify(next),
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?쒗뿕 ???以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
    }
  };

  const handleAddExam = () => {
    if (!selectedExamId || !selectedExam) return;

    const newExam = {
      id: `exam_${Date.now()}`,
      examId: selectedExamId,
      title: selectedExam.title,
      description: selectedExam.description,
      questionCount: selectedExam.questionCount || 0,
      totalPoints: selectedExam.totalPoints,
      type: 'exam',
      weekNumber: selectedWeek,
      sessionNumber: selectedSession,
      sessionName: `${selectedSession}李⑥떆`,
      createdAt: new Date().toISOString(),
      settings: { ...examSettings },
    };

    const updated = [...haksaExams, newExam];
    void persistHaksaExams(updated);

    setShowAddModal(false);
    resetSettings();
  };

  // ?쒗뿕 ?섏젙 ?쒖옉
  const handleEditExam = (exam: any) => {
    setEditingExam(exam);
    setSelectedExamId(exam.examId);
    setSelectedWeek(exam.weekNumber || 1);
    setSelectedSession(exam.sessionNumber || 1);
    setExamSettings({
      startDate: exam.settings?.startDate || today,
      startTime: exam.settings?.startTime || '09:00',
      endDate: exam.settings?.endDate || nextWeek,
      endTime: exam.settings?.endTime || '18:00',
      points: exam.settings?.points || exam.totalPoints || 0,
      allowRetake: exam.settings?.allowRetake || false,
      retakeScore: exam.settings?.retakeScore || 0,
      retakeCount: exam.settings?.retakeCount || 0,
      showResults: exam.settings?.showResults !== false,
    });
  };

  // ?쒗뿕 ?섏젙 ???
  const handleSaveEdit = () => {
    if (!editingExam) return;

    const updated = haksaExams.map(e => {
      if (e.id === editingExam.id) {
        return {
          ...e,
          weekNumber: selectedWeek,
          sessionNumber: selectedSession,
          sessionName: `${selectedSession}李⑥떆`,
          settings: { ...examSettings },
        };
      }
      return e;
    });

    void persistHaksaExams(updated);

    setEditingExam(null);
    resetSettings();
  };

  const handleDeleteExam = (examId: string) => {
    if (!confirm('???쒗뿕????젣?섏떆寃좎뒿?덇퉴?')) return;
    const updated = haksaExams.filter(e => e.id !== examId);
    void persistHaksaExams(updated);
  };

  return (
    <div className="space-y-4">
      {errorMessage && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
          {errorMessage}
        </div>
      )}

      <div className="flex items-center justify-between">
        <h3 className="text-lg font-medium text-gray-900">?깅줉???쒗뿕</h3>
        <button
          onClick={() => setShowAddModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>?쒗뿕 異붽?</span>
        </button>
      </div>

      {haksaExams.length > 0 ? (
        <div className="space-y-3">
          {haksaExams.map((exam: any) => (
            <div
              key={exam.id}
              className="p-4 border border-gray-200 rounded-lg bg-white hover:bg-gray-50 transition-colors"
            >
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-3">
                    <ClipboardCheck className="w-5 h-5 text-red-600" />
                    <span className="font-medium text-gray-900">{exam.title}</span>
                    <span className="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">
                      {exam.questionCount || 0}臾몄젣
                    </span>
                    <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 text-xs rounded">
                      {exam.weekNumber || 1}二쇱감
                    </span>
                    <span className="px-2 py-0.5 bg-indigo-50 text-indigo-700 text-xs rounded">
                      {exam.sessionName || `${exam.sessionNumber || 1}李⑥떆`}
                    </span>
                  </div>
                  
                  {/* ?ㅼ젙 ??ぉ???뚯씠釉??뺥깭濡??쒖떆 */}
                  <div className="ml-7 text-sm space-y-2 bg-gray-50 p-3 rounded-lg">
                    <div className="flex items-center">
                      <span className="w-24 text-gray-500">?묒떆湲곌컙</span>
                      <span className="text-gray-900">
                        {exam.settings?.startDate || '-'} {exam.settings?.startTime || ''} ~ {exam.settings?.endDate || '-'} {exam.settings?.endTime || ''}
                      </span>
                    </div>
                    <div className="flex items-center">
                      <span className="w-24 text-gray-500">諛곗젏</span>
                      <span className="text-gray-900">{exam.settings?.points || exam.totalPoints || 0}??</span>
                    </div>
                    <div className="flex items-center">
                      <span className="w-24 text-gray-500">?ъ쓳??媛??</span>
                      <span className="text-gray-900">
                        {exam.settings?.allowRetake ? (
                          <>媛??({exam.settings.retakeScore}??誘몃쭔, {exam.settings.retakeCount}??</>
                        ) : '遺덇?'}
                      </span>
                    </div>
                    <div className="flex items-center">
                      <span className="w-24 text-gray-500">?쒗뿕寃곌낵?몄텧</span>
                      <span className="text-gray-900">{exam.settings?.showResults ? '노출' : '비노출'}</span>
                    </div>
                  </div>
                </div>
                
                <div className="flex gap-1 ml-4">
                  <button
                    onClick={() => handleEditExam(exam)}
                    className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                    title="?섏젙"
                  >
                    <Edit className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDeleteExam(exam.id)}
                    className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    title="??젣"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-center text-gray-500 py-12 border border-dashed border-gray-300 rounded-lg">
          <ClipboardCheck className="w-12 h-12 mx-auto text-gray-300 mb-3" />
          <p className="mb-2">?깅줉???쒗뿕???놁뒿?덈떎.</p>
          <p className="text-sm text-gray-400">?쒗뿕 異붽? 踰꾪듉???뚮윭 ?쒗뿕愿由ъ뿉??留뚮뱺 ?쒗뿕???깅줉?섏꽭??</p>
        </div>
      )}

      {/* ?쒗뿕 異붽? 紐⑤떖 */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowAddModal(false)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">?쒗뿕 異붽?</h3>
              <button
                onClick={() => setShowAddModal(false)}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
              >
                횞
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* ?? ?숈궗 ?쒗뿕 ?깅줉? 二쇱감/李⑥떆 ?뺣낫瑜??④퍡 ??ν빐???댁꽌 ?좏깮 UI瑜?異붽??⑸땲?? */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    二쇱감 <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={selectedWeek}
                    onChange={(e) => setSelectedWeek(parseInt(e.target.value, 10) || 1)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {Array.from({ length: weekCount }, (_, i) => i + 1).map((week) => (
                      <option key={week} value={week}>
                        {week}二쇱감
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    李⑥떆 <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={selectedSession}
                    onChange={(e) => setSelectedSession(parseInt(e.target.value, 10) || 1)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {Array.from({ length: 10 }, (_, i) => i + 1).map((session) => (
                      <option key={session} value={session}>
                        {session}李⑥떆
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* ?쒗뿕 ?좏깮 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  ?쒗뿕 ?좏깮 <span className="text-red-500">*</span>
                </label>
                {examList.length > 0 ? (
                  <select
                    value={selectedExamId}
                    onChange={(e) => setSelectedExamId(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">?쒗뿕???좏깮?섏꽭??</option>
                    {examList.map(exam => (
                      <option key={exam.id} value={exam.id}>
                        {exam.title} ({exam.questionCount || 0}臾몄젣, {exam.totalPoints}??
                      </option>
                    ))}
                  </select>
                ) : (
                  <div className="p-4 bg-gray-50 rounded-lg text-center text-gray-500">
                    <p className="text-sm">?깅줉???쒗뿕???놁뒿?덈떎.</p>
                    <p className="text-xs mt-1">醫뚯륫 硫붾돱???쒗뿕愿由ъ뿉??癒쇱? ?쒗뿕???앹꽦?댁＜?몄슂.</p>
                  </div>
                )}
              </div>

              {/* ?쒗뿕 ?곸꽭 ?ㅼ젙 */}
              {selectedExamId && (
                <div className="space-y-4 pt-4 border-t border-gray-100">
                  {/* ?묒떆 媛??湲곌컙 */}
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">?묒떆 媛??湲곌컙</label>
                    <div className="flex items-center gap-2 flex-wrap">
                      <input
                        type="date"
                        value={examSettings.startDate}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, startDate: e.target.value }))}
                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <input
                        type="time"
                        value={examSettings.startTime}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, startTime: e.target.value }))}
                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-gray-500">遺??</span>
                      <input
                        type="date"
                        value={examSettings.endDate}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, endDate: e.target.value }))}
                        min={examSettings.startDate}
                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <input
                        type="time"
                        value={examSettings.endTime}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, endTime: e.target.value }))}
                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-gray-500">源뚯?</span>
                    </div>
                  </div>

                  {/* 諛곗젏 */}
                  <div className="flex items-center gap-3">
                    <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">諛곗젏</label>
                    <input
                      type="number"
                      value={examSettings.points}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, points: parseInt(e.target.value) || 0 }))}
                      min={0}
                      className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">??</span>
                  </div>

                  {/* ?ъ쓳??媛?μ뿬遺 */}
                  <div className="flex items-center gap-3">
                    <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛?μ뿬遺</label>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={examSettings.allowRetake}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, allowRetake: e.target.checked }))}
                        className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                      />
                      <span className="text-sm text-gray-600">?ъ쓳??媛??</span>
                    </label>
                  </div>

                  {/* ?ъ쓳??湲곗? ?먯닔 */}
                  {examSettings.allowRetake && (
                    <>
                      <div className="flex items-center gap-3">
                        <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??湲곗? ?먯닔</label>
                        <input
                          type="number"
                          value={examSettings.retakeScore}
                          onChange={(e) => setExamSettings(prev => ({ ...prev, retakeScore: parseInt(e.target.value) || 0 }))}
                          min={0}
                          max={100}
                          className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        <span className="text-sm text-gray-600">??誘몃쭔?쇰븣 ?ъ쓳??媛??</span>
                      </div>

                      <div className="flex items-center gap-3">
                        <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛???잛닔</label>
                        <input
                          type="number"
                          value={examSettings.retakeCount}
                          onChange={(e) => setExamSettings(prev => ({ ...prev, retakeCount: parseInt(e.target.value) || 0 }))}
                          min={0}
                          className="w-16 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                        <span className="text-sm text-gray-600">??</span>
                      </div>
                    </>
                  )}

                  {/* ?쒗뿕寃곌낵?몄텧 */}
                  <div className="flex items-center gap-3">
                    <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?쒗뿕寃곌낵?몄텧</label>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={examSettings.showResults}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, showResults: e.target.checked }))}
                        className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                      />
                      <span className="text-sm text-gray-600">?몄텧</span>
                    </label>
                    <span className="text-xs text-gray-400">???묒떆 ???섍컯?앹씠 ?뺣떟???뺤씤?????덉뒿?덈떎.</span>
                  </div>
                </div>
              )}
            </div>

            <div className="sticky bottom-0 bg-white border-t border-gray-200 px-6 py-4 flex gap-3">
              <button
                onClick={() => setShowAddModal(false)}
                className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                痍⑥냼
              </button>
              <button
                onClick={handleAddExam}
                disabled={!selectedExamId}
                className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                ?쒗뿕異붽?
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ?쒗뿕 ?섏젙 紐⑤떖 */}
      {editingExam && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/50" onClick={() => setEditingExam(null)} />
          <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">?쒗뿕 ?섏젙</h3>
              <button
                onClick={() => setEditingExam(null)}
                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
              >
                횞
              </button>
            </div>

            <div className="p-6 space-y-6">
              {/* ?? ?쒗뿕 ?섏젙?먯꽌??二쇱감/李⑥떆 蹂寃쎌씠 媛?ν빐???쇨??깆씠 ?좎??⑸땲?? */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    二쇱감 <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={selectedWeek}
                    onChange={(e) => setSelectedWeek(parseInt(e.target.value, 10) || 1)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {Array.from({ length: weekCount }, (_, i) => i + 1).map((week) => (
                      <option key={week} value={week}>
                        {week}二쇱감
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    李⑥떆 <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={selectedSession}
                    onChange={(e) => setSelectedSession(parseInt(e.target.value, 10) || 1)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {Array.from({ length: 10 }, (_, i) => i + 1).map((session) => (
                      <option key={session} value={session}>
                        {session}李⑥떆
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* ?쒗뿕 ?좏깮 (?섏젙 遺덇?) */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">?쒗뿕 ?좏깮</label>
                <div className="px-4 py-2.5 bg-gray-100 border border-gray-300 rounded-lg text-gray-700">
                  {editingExam.title}
                </div>
              </div>

              {/* ?쒗뿕 ?곸꽭 ?ㅼ젙 */}
              <div className="space-y-4 pt-4 border-t border-gray-100">
                {/* ?묒떆 媛??湲곌컙 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">?묒떆 媛??湲곌컙</label>
                  <div className="flex items-center gap-2 flex-wrap">
                    <input
                      type="date"
                      value={examSettings.startDate}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, startDate: e.target.value }))}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <input
                      type="time"
                      value={examSettings.startTime}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, startTime: e.target.value }))}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-gray-500">遺??</span>
                    <input
                      type="date"
                      value={examSettings.endDate}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, endDate: e.target.value }))}
                      min={examSettings.startDate}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <input
                      type="time"
                      value={examSettings.endTime}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, endTime: e.target.value }))}
                      className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-gray-500">源뚯?</span>
                  </div>
                </div>

                {/* 諛곗젏 */}
                <div className="flex items-center gap-3">
                  <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">諛곗젏</label>
                  <input
                    type="number"
                    value={examSettings.points}
                    onChange={(e) => setExamSettings(prev => ({ ...prev, points: parseInt(e.target.value) || 0 }))}
                    min={0}
                    className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-600">??</span>
                </div>

                {/* ?ъ쓳??媛?μ뿬遺 */}
                <div className="flex items-center gap-3">
                  <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛?μ뿬遺</label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={examSettings.allowRetake}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, allowRetake: e.target.checked }))}
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">?ъ쓳??媛??</span>
                  </label>
                  <span className="text-xs text-gray-400">???ъ쓳?쒕? 吏?뺥븯硫?湲곗??먯닔 誘몃쭔??寃쎌슦 ?잛닔?쒗븳 踰붿쐞?덉뿉???ъ쓳?쒗븷 ???덉뒿?덈떎.</span>
                </div>

                {/* ?ъ쓳??湲곗? ?먯닔 */}
                {examSettings.allowRetake && (
                  <>
                    <div className="flex items-center gap-3">
                      <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??湲곗? ?먯닔</label>
                      <input
                        type="number"
                        value={examSettings.retakeScore}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, retakeScore: parseInt(e.target.value) || 0 }))}
                        min={0}
                        max={100}
                        className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-sm text-gray-600">??誘몃쭔?쇰븣 ?ъ쓳?쒓? 媛?ν빀?덈떎.</span>
                      <span className="text-xs text-gray-400">??100??留뚯젏 湲곗??낅땲??</span>
                    </div>

                    <div className="flex items-center gap-3">
                      <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛???잛닔</label>
                      <input
                        type="number"
                        value={examSettings.retakeCount}
                        onChange={(e) => setExamSettings(prev => ({ ...prev, retakeCount: parseInt(e.target.value) || 0 }))}
                        min={0}
                        className="w-16 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                      <span className="text-sm text-gray-600">?뚭퉴吏 ?ъ쓳?쒓? 媛?ν빀?덈떎.</span>
                    </div>
                  </>
                )}

                {/* ?쒗뿕寃곌낵?몄텧 */}
                <div className="flex items-center gap-3">
                  <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?쒗뿕寃곌낵?몄텧</label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={examSettings.showResults}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, showResults: e.target.checked }))}
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">?몄텧</span>
                  </label>
                  <span className="text-xs text-gray-400">???묒떆 ???섍컯?앹씠 ?뺣떟???뺤씤?????덉뒿?덈떎.</span>
                </div>
              </div>
            </div>

            <div className="sticky bottom-0 bg-white border-t border-gray-200 px-6 py-4 flex gap-3">
              <button
                onClick={() => setEditingExam(null)}
                className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                痍⑥냼
              </button>
              <button
                onClick={handleSaveEdit}
                className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                ?쒗뿕?섏젙
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ?쒗뿕 ?좏깮 紐⑤떖 (?쒗뿕愿由ъ뿉???앹꽦???쒗뿕 ?좏깮)
function ExamSelectModal({
  isOpen,
  onClose,
  onSave,
  showWeekSession = false,
  weekCount = 15,
}: {
  isOpen: boolean;
  onClose: () => void;
  onSave: (examData: {
    examId: number;
    title: string;
    description?: string;
    duration: number;
    questionCount: number;
    points: number;
    allowRetake: boolean;
    retakeScore: number;
    retakeCount: number;
    showResults: boolean;
    startDate?: string;
    endDate?: string;
    weekNumber: number;
    sessionNumber: number;
  }) => void;
  showWeekSession?: boolean;
  weekCount?: number;
}) {
  const [examList, setExamList] = useState<any[]>([]);
  const [selectedExamId, setSelectedExamId] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedWeek, setSelectedWeek] = useState(1);
  const [selectedSession, setSelectedSession] = useState(1);
  
  // ?ㅻ뒛 ?좎쭨 湲곕낯媛?
  const today = new Date().toISOString().split('T')[0];
  const nextWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
  
  const [examSettings, setExamSettings] = useState({
    startDate: today,
    endDate: nextWeek,
    points: 0,
    allowRetake: false,
    retakeScore: 0,
    retakeCount: 0,
    showResults: true,
  });

  // ?쒗뿕愿由?紐⑸줉 遺덈윭?ㅺ린
  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;

    const fetchTemplates = async () => {
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getExamTemplates({ limit: 200 });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        const rows = res.rst_data ?? [];
        const mapped = rows.map((row: any) => ({
          id: String(row.id),
          title: row.exam_nm || '?쒗뿕',
          description: row.content || '',
          duration: Number(row.exam_time ?? 60),
          questionCount: Number(row.question_cnt ?? 0),
          totalPoints: Number(row.total_points ?? 0),
        }));
        if (!cancelled) setExamList(mapped);
      } catch (e) {
        if (!cancelled) {
          setExamList([]);
          setErrorMessage(e instanceof Error ? e.message : '?쒗뿕 ?쒗뵆由우쓣 遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      }
    };

    void fetchTemplates();
    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  // ?좏깮???쒗뿕 ?뺣낫
  const selectedExam = examList.find(e => e.id === selectedExamId);

  useEffect(() => {
    if (selectedExam) {
      setExamSettings(prev => ({ ...prev, points: selectedExam.totalPoints || 0 }));
    }
  }, [selectedExam]);

  const handleSave = () => {
    if (!selectedExamId || !selectedExam) return;
    
    onSave({
      examId: parseInt(selectedExamId, 10),
      title: selectedExam.title,
      description: selectedExam.description,
      duration: selectedExam.duration || 60,
      questionCount: selectedExam.questionCount || 0,
      points: examSettings.points,
      allowRetake: examSettings.allowRetake,
      retakeScore: examSettings.retakeScore,
      retakeCount: examSettings.retakeCount,
      showResults: examSettings.showResults,
      startDate: examSettings.startDate,
      endDate: examSettings.endDate,
      weekNumber: selectedWeek,
      sessionNumber: selectedSession,
    });
    
    // 珥덇린??
    setSelectedExamId('');
    setExamSettings({
      startDate: today,
      endDate: nextWeek,
      points: 0,
      allowRetake: false,
      retakeScore: 0,
      retakeCount: 0,
      showResults: true,
    });
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900">?쒗뿕 異붽?</h3>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg"
          >
            횞
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* ?? ?숈궗 怨쇰ぉ? ?깅줉 ??二쇱감/李⑥떆瑜?吏?뺥빐???섎?濡??좏깮 UI瑜?蹂댁뿬以띾땲?? */}
          {showWeekSession && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  二쇱감 <span className="text-red-500">*</span>
                </label>
                <select
                  value={selectedWeek}
                  onChange={(e) => setSelectedWeek(parseInt(e.target.value, 10) || 1)}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {Array.from({ length: weekCount }, (_, i) => i + 1).map((week) => (
                    <option key={week} value={week}>
                      {week}二쇱감
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  李⑥떆 <span className="text-red-500">*</span>
                </label>
                <select
                  value={selectedSession}
                  onChange={(e) => setSelectedSession(parseInt(e.target.value, 10) || 1)}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {Array.from({ length: 10 }, (_, i) => i + 1).map((session) => (
                    <option key={session} value={session}>
                      {session}李⑥떆
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {/* ?쒗뿕 ?좏깮 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              ?쒗뿕 ?좏깮 <span className="text-red-500">*</span>
            </label>
            {examList.length > 0 ? (
              <select
                value={selectedExamId}
                onChange={(e) => setSelectedExamId(e.target.value)}
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">?쒗뿕???좏깮?섏꽭??</option>
                {examList.map(exam => (
                  <option key={exam.id} value={exam.id}>
                    {exam.title} ({exam.questionCount || 0}臾몄젣, {exam.totalPoints}??
                  </option>
                ))}
              </select>
            ) : (
              <div className="p-4 bg-gray-50 rounded-lg text-center text-gray-500">
                <p className="text-sm">?깅줉???쒗뿕???놁뒿?덈떎.</p>
                <p className="text-xs mt-1">醫뚯륫 硫붾돱???쒗뿕愿由ъ뿉??癒쇱? ?쒗뿕???앹꽦?댁＜?몄슂.</p>
              </div>
            )}
          </div>

          {/* ?쒗뿕 ?곸꽭 ?ㅼ젙 */}
          {selectedExamId && (
            <div className="space-y-4 pt-4 border-t border-gray-100">
              {/* ?묒떆 媛??湲곌컙 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">?묒떆 媛??湲곌컙</label>
                <div className="flex items-center gap-2">
                  <input
                    type="date"
                    value={examSettings.startDate}
                    onChange={(e) => setExamSettings(prev => ({ ...prev, startDate: e.target.value }))}
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <span className="text-gray-500">~</span>
                  <input
                    type="date"
                    value={examSettings.endDate}
                    onChange={(e) => setExamSettings(prev => ({ ...prev, endDate: e.target.value }))}
                    min={examSettings.startDate}
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
              </div>

              {/* 諛곗젏 */}
              <div className="flex items-center gap-3">
                <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">諛곗젏</label>
                <input
                  type="number"
                  value={examSettings.points}
                  onChange={(e) => setExamSettings(prev => ({ ...prev, points: parseInt(e.target.value) || 0 }))}
                  min={0}
                  className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <span className="text-sm text-gray-600">??</span>
              </div>

              {/* ?ъ쓳??媛?μ뿬遺 */}
              <div className="flex items-center gap-3">
                <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛?μ뿬遺</label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={examSettings.allowRetake}
                    onChange={(e) => setExamSettings(prev => ({ ...prev, allowRetake: e.target.checked }))}
                    className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-600">?ъ쓳??媛??</span>
                </label>
              </div>

              {/* ?ъ쓳??湲곗? ?먯닔 */}
              {examSettings.allowRetake && (
                <>
                  <div className="flex items-center gap-3">
                    <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??湲곗? ?먯닔</label>
                    <input
                      type="number"
                      value={examSettings.retakeScore}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, retakeScore: parseInt(e.target.value) || 0 }))}
                      min={0}
                      max={100}
                      className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">??誘몃쭔?쇰븣 ?ъ쓳??媛??</span>
                  </div>

                  <div className="flex items-center gap-3">
                    <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?ъ쓳??媛???잛닔</label>
                    <input
                      type="number"
                      value={examSettings.retakeCount}
                      onChange={(e) => setExamSettings(prev => ({ ...prev, retakeCount: parseInt(e.target.value) || 0 }))}
                      min={0}
                      className="w-16 px-3 py-2 border border-gray-300 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-600">??</span>
                  </div>
                </>
              )}

              {/* ?쒗뿕寃곌낵?몄텧 */}
              <div className="flex items-center gap-3">
                <label className="w-28 text-sm font-medium text-gray-700 flex-shrink-0">?쒗뿕寃곌낵?몄텧</label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={examSettings.showResults}
                    onChange={(e) => setExamSettings(prev => ({ ...prev, showResults: e.target.checked }))}
                    className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  />
                  <span className="text-sm text-gray-600">?몄텧</span>
                </label>
                <span className="text-xs text-gray-400">???묒떆 ???섍컯?앹씠 ?뺣떟???뺤씤?????덉뒿?덈떎.</span>
              </div>
            </div>
          )}
        </div>

        {errorMessage && (
          <div className="px-6 pb-2 text-sm text-red-600">
            {errorMessage}
          </div>
        )}

        <div className="sticky bottom-0 bg-white border-t border-gray-200 px-6 py-4 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
          >
            痍⑥냼
          </button>
          <button
            onClick={handleSave}
            disabled={!selectedExamId}
            className="flex-1 px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            </button>
        </div>
      </div>
    </div>
  );
}

// ?숈궗 怨쇰ぉ ?깆쟻 ?먯젙 而댄룷?뚰듃 (A/B/C/D/F)
function HaksaGradingContent({
  course,
}: {
  course?: any;
}) {
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
  const courseId = course?.id;
  const [students, setStudents] = useState<any[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [bulkGrade, setBulkGrade] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [recalcLoading, setRecalcLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [scoreDistributionRows, setScoreDistributionRows] = useState<any[]>([]);
  const [gradeDistributionRows, setGradeDistributionRows] = useState<any[]>([]);
  const [distributionSummary, setDistributionSummary] = useState<any | null>(null);

  // ?깆쟻 湲곗? (A+~F, A~D源뚯? + ?깃툒 ?ы븿)
  const GRADES = [
    { value: 'A+', label: 'A+ (95-100)', min: 95, color: 'bg-blue-200 text-blue-800' },
    { value: 'A', label: 'A (90-94)', min: 90, color: 'bg-blue-100 text-blue-700' },
    { value: 'B+', label: 'B+ (85-89)', min: 85, color: 'bg-green-200 text-green-800' },
    { value: 'B', label: 'B (80-84)', min: 80, color: 'bg-green-100 text-green-700' },
    { value: 'C+', label: 'C+ (75-79)', min: 75, color: 'bg-yellow-200 text-yellow-800' },
    { value: 'C', label: 'C (70-74)', min: 70, color: 'bg-yellow-100 text-yellow-700' },
    { value: 'D+', label: 'D+ (65-69)', min: 65, color: 'bg-orange-200 text-orange-800' },
    { value: 'D', label: 'D (60-64)', min: 60, color: 'bg-orange-100 text-orange-700' },
    { value: 'F', label: 'F (0-59)', min: 0, color: 'bg-red-100 text-red-700' },
  ];

  // ?먯닔?먯꽌 ?먮룞 ?깃툒 怨꾩궛
  const calculateGrade = (score: number): string => {
    if (score >= 95) return 'A+';
    if (score >= 90) return 'A';
    if (score >= 85) return 'B+';
    if (score >= 80) return 'B';
    if (score >= 75) return 'C+';
    if (score >= 70) return 'C';
    if (score >= 65) return 'D+';
    if (score >= 60) return 'D';
    return 'F';
  };

  // API?먯꽌 ?섍컯??+ ??λ맂 ?깆쟻 濡쒕뱶
  useEffect(() => {
    if (!haksaKey) return;
    let cancelled = false;

    const loadStudents = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const [studentRes, gradeRes, distRes] = await Promise.all([
          tutorLmsApi.getHaksaCourseStudents({
            courseCode: haksaKey.courseCode,
            openYear: haksaKey.openYear,
            openTerm: haksaKey.openTerm,
            bunbanCode: haksaKey.bunbanCode,
            groupCode: haksaKey.groupCode,
          }),
          tutorLmsApi.getHaksaGrades(haksaKey),
          tutorLmsApi.getHaksaGradeDistribution(haksaKey),
        ]);

        if (studentRes.rst_code !== '0000') throw new Error(studentRes.rst_message);
        if (gradeRes.rst_code !== '0000') throw new Error(gradeRes.rst_message);
        if (distRes.rst_code === '0000') {
          setScoreDistributionRows(distRes.rst_data ?? []);
          setGradeDistributionRows(distRes.rst_grade_data ?? []);
          const summary = Array.isArray(distRes.rst_summary) ? distRes.rst_summary[0] : distRes.rst_summary;
          setDistributionSummary(summary ?? null);
        } else {
          setScoreDistributionRows([]);
          setGradeDistributionRows([]);
          setDistributionSummary(null);
        }

        const gradeMap = new Map<string, { grade?: string; score?: number }>();
        (gradeRes.rst_data ?? []).forEach((row: any) => {
          if (!row.student_id) return;
          gradeMap.set(String(row.student_id), {
            grade: row.grade || '',
            score: Number(row.score ?? 0),
          });
        });

        const mapped = (studentRes.rst_data ?? []).map((row: any) => {
          const studentId = String(row.student_id || '');
          const saved = gradeMap.get(studentId);
          return {
            id: studentId,
            name: row.name || '-',
            studentId: studentId || '-',
            score: saved?.score ?? 0,
            grade: saved?.grade ?? '',
          };
        });

        // ?? ?댁쟾 濡쒖뺄?ㅽ넗由ъ? ?깆쟻???덈떎硫?珥덇린 ??踰?DB濡?留덉씠洹몃젅?댁뀡?⑸땲??
        if (courseId && mapped.length > 0 && gradeMap.size === 0) {
          try {
            const saved = localStorage.getItem(`haksa_grades_${courseId}`);
            if (saved) {
              const parsed = JSON.parse(saved);
              const migrated = mapped.map((row: any) => {
                const found = parsed.find((s: any) => String(s.id) === row.id);
                return { ...row, grade: found?.grade || row.grade, score: Number(found?.score ?? row.score ?? 0) };
              });
              await tutorLmsApi.updateHaksaGrades({
                ...haksaKey,
                gradesJson: JSON.stringify(
                  migrated.map((s: any) => ({
                    student_id: s.id,
                    grade: s.grade,
                    score: s.score ?? 0,
                  }))
                ),
              });
              if (!cancelled) setStudents(migrated);
              return;
            }
          } catch {}
        }

        if (!cancelled) setStudents(mapped);
      } catch (e) {
        if (!cancelled) {
          setStudents([]);
          setScoreDistributionRows([]);
          setGradeDistributionRows([]);
          setDistributionSummary(null);
          setErrorMessage(e instanceof Error ? e.message : '?깆쟻??遺덈윭?ㅻ뒗 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void loadStudents();
  }, [haksaKey, courseId]);

  // ???
  const persistGrades = async (updatedStudents: any[]) => {
    if (!haksaKey) return false;
    try {
      const payload = updatedStudents.map((s) => ({
        student_id: s.id,
        grade: s.grade,
        score: Number(s.score ?? 0),
      }));
      const res = await tutorLmsApi.updateHaksaGrades({
        ...haksaKey,
        gradesJson: JSON.stringify(payload),
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      const distRes = await tutorLmsApi.getHaksaGradeDistribution(haksaKey);
      if (distRes.rst_code === '0000') {
        setScoreDistributionRows(distRes.rst_data ?? []);
        setGradeDistributionRows(distRes.rst_grade_data ?? []);
        const summary = Array.isArray(distRes.rst_summary) ? distRes.rst_summary[0] : distRes.rst_summary;
        setDistributionSummary(summary ?? null);
      }
      return true;
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '?깆쟻 ???以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.');
      return false;
    }
  };

  const saveGrades = (updatedStudents: any[]) => {
    setStudents(updatedStudents);
    void persistGrades(updatedStudents);
  };

  // 媛쒕퀎 ?깆쟻 蹂寃?
  const handleGradeChange = (studentId: string, grade: string) => {
    const updated = students.map(s => 
      s.id === studentId ? { ...s, grade } : s
    );
    saveGrades(updated);
  };

  // ?깆쟻 ?ш퀎???먯닔 湲곕컲)
  const handleRecalc = () => {
    void (async () => {
      const ok = confirm('?깆쟻???ш퀎?고븯?쒓쿋?듬땲源?\n\n(?꾩옱 ?먯닔瑜?湲곗??쇰줈 A/B/C/D/F媛 ?ㅼ떆 ?먯젙?⑸땲??)');
      if (!ok) return;
      setRecalcLoading(true);
      const updated = students.map(s => ({
        ...s,
        grade: calculateGrade(s.score),
      }));
      setStudents(updated);
      const saved = await persistGrades(updated);
      if (saved) alert('?ш퀎?곗씠 ?꾨즺?섏뿀?듬땲??');
      setRecalcLoading(false);
    })();
  };

  // ?좏깮???숈깮 ?쇨큵 ?깆쟻 ?곸슜
  const handleBulkGrade = () => {
    if (!bulkGrade || selectedIds.length === 0) return;
    const updated = students.map(s =>
      selectedIds.includes(s.id) ? { ...s, grade: bulkGrade } : s
    );
    saveGrades(updated);
    setSelectedIds([]);
    setBulkGrade('');
  };

  // ?꾩껜 ?좏깮/?댁젣
  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedIds(students.map(s => s.id));
    } else {
      setSelectedIds([]);
    }
  };

  // 媛쒕퀎 ?좏깮
  const handleSelect = (studentId: string) => {
    setSelectedIds(prev =>
      prev.includes(studentId)
        ? prev.filter(id => id !== studentId)
        : [...prev, studentId]
    );
  };

  const getGradeBadge = (grade: string) => {
    const found = GRADES.find(g => g.value === grade);
    return found?.color || 'bg-gray-100 text-gray-700';
  };

  const gradeCountMap = new Map<string, number>();
  if (gradeDistributionRows.length > 0) {
    gradeDistributionRows.forEach((row: any) => {
      gradeCountMap.set(String(row.grade_key || '').toUpperCase(), Number(row.student_count ?? 0));
    });
  }

  const chartGradeRows = GRADES.map((g) => ({
    ...g,
    count: gradeCountMap.has(g.value)
      ? Number(gradeCountMap.get(g.value) ?? 0)
      : students.filter((s) => s.grade === g.value).length,
  }));
  const ungradedCount = gradeCountMap.has('ETC')
    ? Number(gradeCountMap.get('ETC') ?? 0)
    : students.filter((s) => !s.grade).length;
  const maxGradeCount = Math.max(...chartGradeRows.map((g) => g.count), ungradedCount, 1);
  const summaryTotalCount = Number(distributionSummary?.total_count ?? students.length ?? 0);
  const summaryAvgScore = Number(distributionSummary?.avg_score ?? 0);
  const summaryMaxScore = Number(distributionSummary?.max_score ?? 0);
  const summaryMinScore = Number(distributionSummary?.min_score ?? 0);

  return (
    <div className="space-y-6">
      {errorMessage && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {errorMessage}
        </div>
      )}

      {/* ?깆쟻 湲곗? ?덈궡 */}
      <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
        <h4 className="font-medium text-blue-900 mb-2">?숈궗 怨쇰ぉ ?깆쟻 湲곗?</h4>
        <div className="flex flex-wrap gap-3 text-sm">
          {GRADES.map(g => (
            <span key={g.value} className={`px-3 py-1 rounded-full ${g.color}`}>
              {g.label}
            </span>
          ))}
        </div>
      </div>

      {/* ?≪뀡 踰꾪듉 */}
      <div className="flex items-center gap-3 flex-wrap">
        <button
          onClick={handleRecalc}
          disabled={recalcLoading}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          {recalcLoading ? '재계산 중...' : '성적 재계산'}
        </button>
        <button
          onClick={() => {
            if (!haksaKey) return;
            // ?? ?숈궗 ?깆쟻?쒕뒗 ?쒕쾭 而룹삤??洹쒖튃??諛섏쁺???대젮諛쏆븘???붾㈃怨??됱젙?먮즺媛 ?쇱튂?⑸땲??
            const url = tutorLmsApi.getHaksaGradeExportUrl(haksaKey);
            window.open(url, '_blank', 'noopener,noreferrer');
          }}
          disabled={students.length === 0 || !haksaKey}
          className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
        >
          <Download className="w-4 h-4" />
          <span>?깆쟻???ㅼ슫濡쒕뱶(CSV)</span>
        </button>

        {selectedIds.length > 0 && (
          <div className="flex items-center gap-2">
            <select
              value={bulkGrade}
              onChange={(e) => setBulkGrade(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">?깆쟻 ?좏깮</option>
              {GRADES.map(g => (
                <option key={g.value} value={g.value}>{g.value}</option>
              ))}
            </select>
            <button
              onClick={handleBulkGrade}
              disabled={!bulkGrade}
              className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
            >
              ?좏깮 ?숈깮 ?쇨큵 ?곸슜 ({selectedIds.length}紐?
            </button>
          </div>
        )}
      </div>

      {/* ?숈깮 紐⑸줉 ?뚯씠釉?*/}
      <div className="overflow-x-auto border border-gray-200 rounded-lg">
        <table className="w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-center w-12">
                <input
                  type="checkbox"
                  checked={selectedIds.length === students.length && students.length > 0}
                  onChange={(e) => handleSelectAll(e.target.checked)}
                  className="w-4 h-4 text-blue-600 rounded"
                />
              </th>
              <th className="px-4 py-3 text-left text-gray-700">?대쫫</th>
              <th className="px-4 py-3 text-center text-gray-700">?숇쾲</th>
              <th className="px-4 py-3 text-center text-gray-700">?먯닔</th>
              <th className="px-4 py-3 text-center text-gray-700">?깆쟻</th>
              <th className="px-4 py-3 text-center text-gray-700">?깆쟻 蹂寃?</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {loading && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-gray-500">
                  遺덈윭?ㅻ뒗 以?..
                </td>
              </tr>
            )}

            {!loading && students.map((student) => (
              <tr key={student.id} className="hover:bg-gray-50">
                <td className="px-4 py-4 text-center">
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(student.id)}
                    onChange={() => handleSelect(student.id)}
                    className="w-4 h-4 text-blue-600 rounded"
                  />
                </td>
                <td className="px-4 py-4 text-gray-900">{student.name}</td>
                <td className="px-4 py-4 text-center text-gray-600">{student.studentId}</td>
                <td className="px-4 py-4 text-center text-gray-900 font-medium">{student.score}??</td>
                <td className="px-4 py-4 text-center">
                  {student.grade ? (
                    <span className={`inline-flex px-3 py-1 rounded-full text-sm font-medium ${getGradeBadge(student.grade)}`}>
                      {student.grade}
                    </span>
                  ) : (
                    <span className="text-gray-400">誘명뙋??</span>
                  )}
                </td>
                <td className="px-4 py-4 text-center">
                  <select
                    value={student.grade || ''}
                    onChange={(e) => handleGradeChange(student.id, e.target.value)}
                    className="px-3 py-1.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
                  >
                    <option value="">?좏깮</option>
                    {GRADES.map(g => (
                      <option key={g.value} value={g.value}>{g.value}</option>
                    ))}
                  </select>
                </td>
              </tr>
            ))}

            {!loading && students.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-gray-500">
                  ?깅줉???숈깮???놁뒿?덈떎.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* ?깆쟻 遺꾪룷 洹몃옒??*/}
      {students.length > 0 && (
        <div className="p-5 bg-white border border-gray-200 rounded-xl">
          <h4 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-blue-600" />
            ?? ??
          </h4>

          <div className="grid grid-cols-4 gap-3 mb-5">
            <div className="p-3 bg-blue-50 rounded-lg text-center">
              <div className="text-xs text-blue-600 mb-1">???</div>
              <div className="text-lg font-bold text-blue-900">{summaryTotalCount}?</div>
            </div>
            <div className="p-3 bg-green-50 rounded-lg text-center">
              <div className="text-xs text-green-600 mb-1">??</div>
              <div className="text-lg font-bold text-green-900">{summaryAvgScore.toFixed(1)}?</div>
            </div>
            <div className="p-3 bg-purple-50 rounded-lg text-center">
              <div className="text-xs text-purple-600 mb-1">???</div>
              <div className="text-lg font-bold text-purple-900">{summaryMaxScore.toFixed(1)}?</div>
            </div>
            <div className="p-3 bg-orange-50 rounded-lg text-center">
              <div className="text-xs text-orange-600 mb-1">???</div>
              <div className="text-lg font-bold text-orange-900">{summaryMinScore.toFixed(1)}?</div>
            </div>
          </div>

          <div className="flex flex-wrap gap-3 mb-4">
            {chartGradeRows.map((g) => (
              <div key={g.value} className="flex items-center gap-2">
                <span className={`px-3 py-1 rounded-full text-sm ${g.color}`}>{g.value}</span>
                <span className="text-gray-600 text-sm">{g.count}?</span>
              </div>
            ))}
            <div className="flex items-center gap-2">
              <span className="px-3 py-1 rounded-full text-sm bg-gray-100 text-gray-700">???</span>
              <span className="text-gray-600 text-sm">{ungradedCount}?</span>
            </div>
          </div>

          <div className="space-y-2">
            {chartGradeRows.map((g) => {
              const pct = maxGradeCount > 0 ? (g.count / maxGradeCount) * 100 : 0;
              const ratio = summaryTotalCount > 0 ? ((g.count / summaryTotalCount) * 100).toFixed(0) : '0';
              return (
                <div key={g.value} className="flex items-center gap-3">
                  <div className="w-10 text-right text-xs font-semibold text-gray-600">{g.value}</div>
                  <div className="flex-1 h-7 bg-gray-100 rounded-md overflow-hidden relative">
                    <div
                      className="h-full bg-blue-500 rounded-md transition-all duration-500 ease-out"
                      style={{ width: `${pct}%`, minWidth: g.count > 0 ? '2px' : '0' }}
                    />
                    {g.count > 0 && (
                      <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-medium text-gray-600">
                        {g.count}?({ratio}%)
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
            <div className="flex items-center gap-3">
              <div className="w-10 text-right text-xs font-semibold text-gray-400">-</div>
              <div className="flex-1 h-7 bg-gray-100 rounded-md overflow-hidden relative">
                <div
                  className="h-full bg-gray-400 rounded-md transition-all duration-500 ease-out"
                  style={{ width: `${maxGradeCount > 0 ? (ungradedCount / maxGradeCount) * 100 : 0}%`, minWidth: ungradedCount > 0 ? '2px' : '0' }}
                />
                {ungradedCount > 0 && (
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-medium text-gray-600">
                    {ungradedCount}?
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
