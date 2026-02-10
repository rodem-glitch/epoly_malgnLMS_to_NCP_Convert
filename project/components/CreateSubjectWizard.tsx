import React, { useEffect, useState } from 'react';
import {
  Info,
  Users,
  List,
  CheckCircle,
  Upload,
  Search,
  Plus,
  X,
} from 'lucide-react';
import { ContentLibraryModal } from './ContentLibraryModal';
import { CourseSelectionModal } from './CourseSelectionModal';
import { tutorLmsApi, type TutorCourseCategoryRow } from '../api/tutorLmsApi';

type Step = 'basic' | 'learners' | 'curriculum' | 'confirm';

interface Course {
  id: string;
  classification: string;
  name: string;
  department: string;
  major: string;
  departmentName: string;
}

interface Learner {
  id: string;
  name: string;
  campus: string;
  major: string;
  studentId?: string;
  email?: string;
  deptPath?: string;
}

interface SessionVideo {
  id: string;
  title: string;
  url: string; //외부 URL(직접 입력)일 때 사용
  lessonId?: string; //콘텐츠 라이브러리(레슨)에서 선택했을 때 사용
}

interface SessionItem {
  id: string;
  title: string;
  description: string;
  videos: SessionVideo[];
}

interface FormData {
  // 기본 정보
  subjectName: string;
  selectedCourse: Course | null;
  categoryId: number;
  year: string;
  semester: string;
  credits: string;
  hours: string;
  startDate: string;
  endDate: string;
  description: string;
  objectives: string;
  courseFileName: string;
  courseFileUrl: string;
  
  // 학습자
  selectedLearners: Learner[];
  
  // 차시
  sessions: SessionItem[];
}

interface CreateSubjectWizardProps {
  initialStep?: Step;
  onStepChange?: (step: Step) => void;
}

function normalizeSessionCount(hours: string) {
  // 왜: 시수는 사용자가 비워둘 수도 있어, "기본값 15"로 안전하게 보정합니다.
  //     또 너무 큰 값이 들어오면(오타) 화면이 멈출 수 있어 상한을 둡니다.
  const parsed = Number.parseInt(String(hours || '').trim(), 10);
  const safe = Number.isFinite(parsed) && parsed > 0 ? parsed : 15;
  return Math.min(safe, 200);
}

function buildSessionsByCount(count: number, prevSessions: SessionItem[]) {
  // 왜: 사용자가 이미 입력한 차시 제목/설명/영상은 최대한 보존하면서, 필요한 개수만큼 차시를 맞춥니다.
  const byId = new Map<string, SessionItem>();
  prevSessions.forEach((session) => byId.set(String(session.id), session));

  const next: SessionItem[] = [];
  for (let i = 1; i <= count; i += 1) {
    const id = String(i);
    const existing = byId.get(id) ?? prevSessions[i - 1];
    if (existing) {
      next.push({ ...existing, id });
    } else {
      next.push({ id, title: '', description: '', videos: [] });
    }
  }
  return next;
}

export function CreateSubjectWizard({ initialStep, onStepChange }: CreateSubjectWizardProps = {}) {
  const [currentStep, setCurrentStep] = useState<Step>(initialStep ?? 'basic');
  const [autoSessionsEnabled, setAutoSessionsEnabled] = useState(true);
  useEffect(() => {
    // 왜: URL(부모)에서 내려온 step을 로컬 state에 "한 번만" 동기화해야 합니다.
    //     currentStep을 의존성에 넣으면, 사용자가 다음/이전으로 step을 바꾸는 순간
    //     아직 URL이 갱신되기 전(같은 커밋)에는 initialStep 값이 이전 단계로 남아 있어서
    //     화면이 잠깐 바뀌었다가 원래 단계로 되돌아오는 "깜빡임/되돌림"이 생길 수 있습니다.
    const nextStep: Step = initialStep ?? 'basic';
    setCurrentStep((prev) => (prev === nextStep ? prev : nextStep));
  }, [initialStep]);

  useEffect(() => {
    // 왜: 단계 이동을 주소에 반영하기 위해 바깥에 알려줍니다.
    onStepChange?.(currentStep);
  }, [currentStep, onStepChange]);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [categoryLoading, setCategoryLoading] = useState(false);
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [courseCategories, setCourseCategories] = useState<TutorCourseCategoryRow[]>([]);
  const [formData, setFormData] = useState<FormData>({
    subjectName: '',
    selectedCourse: null,
    categoryId: 0,
    year: '2024',
    semester: '1학기',
    credits: '',
    hours: '15',
    startDate: '',
    endDate: '',
    description: '',
    objectives: '',
    courseFileName: '',
    courseFileUrl: '',
    selectedLearners: [],
    sessions: [
      {
        id: '1',
        title: '',
        description: '',
        videos: [],
      },
    ],
  });

  useEffect(() => {
    if (!autoSessionsEnabled) return;

    const count = normalizeSessionCount(formData.hours);
    // 왜: 현재 차시 개수가 이미 맞으면 불필요한 setState로 리렌더를 만들지 않습니다.
    if (formData.sessions.length === count) return;

    setFormData((prev) => {
      const nextCount = normalizeSessionCount(prev.hours);
      if (prev.sessions.length === nextCount) return prev;
      return { ...prev, sessions: buildSessionsByCount(nextCount, prev.sessions) };
    });
  }, [autoSessionsEnabled, formData.hours, formData.sessions.length]);

  useEffect(() => {
    // 왜: 관리자(sysop)와 같은 카테고리 목록(LM_CATEGORY)을 불러와서, PLISM에서도 같은 기준으로 선택하게 하기 위함입니다.
    let alive = true;

    setCategoryLoading(true);
    setCategoryError(null);

    tutorLmsApi
      .getCourseCategories()
      .then((res) => {
        if (!alive) return;
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const rows = (res.rst_data ?? []) as TutorCourseCategoryRow[];
        setCourseCategories(rows);

        // 왜: 화면의 기본값이 텍스트(CLASSROOM)였는데, 실제 DB 카테고리에서는 보통 "자율강좌"가 가장 가까운 성격이라
        //     사용자가 따로 고르지 않아도 기본 선택이 되게 합니다(없으면 0=미지정 유지).
        const preferred =
          rows.find((r) => r.category_nm === '자율강좌') ??
          rows.find((r) => (r.category_nm ?? '').includes('자율'));

        if (preferred) {
          setFormData((prev) =>
            prev.categoryId > 0 ? prev : { ...prev, categoryId: Number(preferred.id) }
          );
        }
      })
      .catch((err) => {
        if (!alive) return;
        setCategoryError(err instanceof Error ? err.message : '카테고리를 불러오는 중 오류가 발생했습니다.');
      })
      .finally(() => {
        if (!alive) return;
        setCategoryLoading(false);
      });

    return () => {
      alive = false;
    };
  }, []);

  const steps = [
    { id: 'basic' as Step, label: '기본 정보', icon: Info },
    { id: 'learners' as Step, label: '학습자 선택', icon: Users },
    { id: 'curriculum' as Step, label: '차시별 구성', icon: List },
    { id: 'confirm' as Step, label: '최종 확인', icon: CheckCircle },
  ];

  const currentStepIndex = steps.findIndex((s) => s.id === currentStep);

  const handleNext = () => {
    const nextIndex = currentStepIndex + 1;
    if (nextIndex < steps.length) {
      setCurrentStep(steps[nextIndex].id);
    }
  };

  const handlePrev = () => {
    const prevIndex = currentStepIndex - 1;
    if (prevIndex >= 0) {
      setCurrentStep(steps[prevIndex].id);
    }
  };

  const updateFormData = (updates: Partial<FormData>) => {
    setFormData((prev) => ({ ...prev, ...updates }));
  };

  const resetWizard = () => {
    const preferred =
      courseCategories.find((c) => c.category_nm === '자율강좌') ??
      courseCategories.find((c) => (c.category_nm ?? '').includes('자율'));

    setCurrentStep('basic');
    setAutoSessionsEnabled(true);
    setFormData({
      subjectName: '',
      selectedCourse: null,
      categoryId: preferred ? Number(preferred.id) : 0,
      year: '2024',
      semester: '1학기',
      credits: '',
      hours: '15',
      startDate: '',
      endDate: '',
      description: '',
      objectives: '',
      courseFileName: '',
      courseFileUrl: '',
      selectedLearners: [],
      sessions: [
        {
          id: '1',
          title: '',
          description: '',
          videos: [],
        },
      ],
    });
  };

  const handleComplete = async () => {
    // 왜: "완료" 버튼을 눌렀을 때 DB에 실제 과목/수강생/차시가 저장돼야 새로고침해도 정보가 유지됩니다.
    setErrorMessage(null);

    const courseName = formData.subjectName.trim();
    if (!courseName) {
      setErrorMessage('과목명을 입력해 주세요.');
      return;
    }
    const yearValue = formData.year.trim();
    if (!yearValue) {
      setErrorMessage('년도를 입력해 주세요.');
      return;
    }
    if (!/^\d{4}$/.test(yearValue)) {
      setErrorMessage('년도는 4자리 숫자(예: 2026)로 입력해 주세요.');
      return;
    }
    if (!formData.startDate || !formData.endDate) {
      setErrorMessage('수업시작일과 수업 종료일을 입력해 주세요.');
      return;
    }

    setSaving(true);
    try {
      const programId = formData.selectedCourse ? Number(formData.selectedCourse.id) : 0;
      const lessonTime = String(normalizeSessionCount(formData.hours));
      // 왜: 학사 정규 과목은 학점 필수값이 있어, 미입력일 때 기본 2학점으로 저장합니다.
      const creditValue = formData.credits.trim() ? formData.credits.trim() : '2';
      const createRes = await tutorLmsApi.createCourse({
        courseName,
        year: yearValue,
        studyStartDate: formData.startDate,
        studyEndDate: formData.endDate,
        programId: programId > 0 ? programId : undefined,
        categoryId: formData.categoryId > 0 ? formData.categoryId : undefined,
        semester: formData.semester,
        credit: creditValue,
        lessonDay: lessonTime,
        lessonTime,
        content1: formData.description,
        content2: formData.objectives,
        courseFile: formData.courseFileName || undefined,
      });
      if (createRes.rst_code !== '0000') throw new Error(createRes.rst_message);

      const courseId = Number(createRes.rst_data);
      if (!courseId) throw new Error('과목 생성 결과(course_id)가 올바르지 않습니다.');

      // (선택) 수강생 등록: 선택된 학습자가 있을 때만 호출합니다.
      if (formData.selectedLearners.length > 0) {
        const userIds = formData.selectedLearners
          .map((l) => Number(l.id))
          .filter((id) => Number.isFinite(id) && id > 0);

        if (userIds.length > 0) {
          const addRes = await tutorLmsApi.addCourseStudents({ courseId, userIds });
          if (addRes.rst_code !== '0000') throw new Error(addRes.rst_message);
        }
      }

      // 차시/레슨 등록: 섹션을 만들고 그 안에 레슨(또는 외부링크)을 추가합니다.
      for (let i = 0; i < formData.sessions.length; i += 1) {
        const session = formData.sessions[i];
        const sectionName = session.title.trim() || `${i + 1}차시`;

        const sectionRes = await tutorLmsApi.insertCurriculumSection({ courseId, sectionName });
        if (sectionRes.rst_code !== '0000') throw new Error(sectionRes.rst_message);

        const sectionId = Number(sectionRes.rst_data);
        if (!sectionId) continue;

        for (const video of session.videos) {
          const lessonId = video.lessonId ? Number(video.lessonId) : 0;
          const url = video.url.trim();

          // 왜: 사용자가 "영상 추가"만 누르고 값(레슨/URL)을 입력하지 않을 수 있어, 빈 행은 건너뜁니다.
          if (!lessonId && !url) continue;

          const addLessonRes = await tutorLmsApi.addCurriculumLesson({
            courseId,
            sectionId,
            lessonId: lessonId > 0 ? lessonId : undefined,
            url: lessonId > 0 ? undefined : url,
            title: video.title.trim() || undefined,
          });
          if (addLessonRes.rst_code !== '0000') throw new Error(addLessonRes.rst_message);
        }
      }

      alert('과목이 개설되었습니다. (담당과목 메뉴에서 확인할 수 있습니다)');
      resetWizard();
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '저장 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h2 className="text-gray-900 mb-2">새 과목 개설</h2>
        <p className="text-gray-600">단계별로 교육과목을 개설하고 설정할 수 있습니다.</p>
      </div>

      {errorMessage && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6">
          {errorMessage}
        </div>
      )}

      {/* Stepper */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
        <div className="flex items-center justify-between">
          {steps.map((step, index) => {
            const Icon = step.icon;
            const isActive = step.id === currentStep;
            const isCompleted = index < currentStepIndex;

            return (
              <React.Fragment key={step.id}>
                <div className="flex items-center">
                  <div
                    className={`flex items-center justify-center w-10 h-10 rounded-full ${
                      isActive || isCompleted
                        ? 'bg-blue-600 text-white'
                        : 'bg-gray-200 text-gray-600'
                    }`}
                  >
                    <Icon className="w-5 h-5" />
                  </div>
                  <span
                    className={`ml-3 ${
                      isActive ? 'text-blue-600' : isCompleted ? 'text-blue-600' : 'text-gray-500'
                    }`}
                  >
                    {step.label}
                  </span>
                </div>
                {index < steps.length - 1 && (
                  <div
                    className={`flex-1 h-0.5 mx-4 ${
                      isCompleted ? 'bg-blue-600' : 'bg-gray-200'
                    }`}
                  />
                )}
              </React.Fragment>
            );
          })}
        </div>
      </div>

      {/* Step Content */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-8">
        {currentStep === 'basic' && (
          <BasicInfoStep
            formData={formData}
            updateFormData={updateFormData}
            courseCategories={courseCategories}
            categoryLoading={categoryLoading}
            categoryError={categoryError}
          />
        )}
        {currentStep === 'learners' && (
          <LearnersStep formData={formData} updateFormData={updateFormData} />
        )}
        {currentStep === 'curriculum' && (
          <CurriculumStep
            formData={formData}
            updateFormData={updateFormData}
            onTouched={() => setAutoSessionsEnabled(false)}
          />
        )}
        {currentStep === 'confirm' && <ConfirmStep formData={formData} courseCategories={courseCategories} />}

        {/* Navigation Buttons */}
        <div className="flex items-center justify-between pt-8 mt-8 border-t border-gray-200">
          <button
            onClick={handlePrev}
            disabled={currentStepIndex === 0}
            className="px-6 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            이전
          </button>
          {currentStepIndex < steps.length - 1 ? (
            <button
              onClick={handleNext}
              className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              다음
            </button>
          ) : (
            <button
              onClick={handleComplete}
              disabled={saving}
              className={`px-6 py-2 bg-green-600 text-white rounded-lg transition-colors ${
                saving ? 'opacity-60 cursor-not-allowed' : 'hover:bg-green-700'
              }`}
            >
              {saving ? '저장 중...' : '과목 개설 완료'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// 기본 정보 입력 단계
function BasicInfoStep({
  formData,
  updateFormData,
  courseCategories,
  categoryLoading,
  categoryError,
}: {
  formData: FormData;
  updateFormData: (updates: Partial<FormData>) => void;
  courseCategories: TutorCourseCategoryRow[];
  categoryLoading: boolean;
  categoryError: string | null;
}) {
  const [isCourseModalOpen, setIsCourseModalOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);

  const handleCourseSelect = (course: Course | null) => {
    updateFormData({ selectedCourse: course });
    setIsCourseModalOpen(false);
  };

  const handlePickImage = () => {
    fileInputRef.current?.click();
  };

  const clearImage = () => {
    updateFormData({ courseFileName: '', courseFileUrl: '' });
  };

  const handleImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    // 왜: 너무 큰 이미지는 업로드 실패/지연이 생길 수 있어, 화면에서 먼저 제한합니다.
    if (file.size > 10 * 1024 * 1024) {
      setUploadError('이미지는 10MB 이하만 업로드할 수 있습니다.');
      return;
    }

    setUploading(true);
    setUploadError(null);
    try {
      const res = await tutorLmsApi.uploadCourseImage({ file });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      // 왜: rst_data가 단일 객체/배열 형태로 올 수 있어, 둘 다 처리합니다.
      const payload: any = Array.isArray(res.rst_data) ? res.rst_data?.[0] : res.rst_data;
      if (!payload?.file_name) throw new Error('업로드 결과가 올바르지 않습니다.');

      updateFormData({
        courseFileName: String(payload.file_name),
        courseFileUrl: String(payload.file_url || ''),
      });
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : '업로드 중 오류가 발생했습니다.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="space-y-6">
      <h3 className="text-gray-900">기본 정보 입력</h3>

      <div className="grid grid-cols-2 gap-6">
        <div>
          <label className="block text-sm text-gray-700 mb-2">소속 과정</label>
          <button
            onClick={() => setIsCourseModalOpen(true)}
            className="w-full px-4 py-2 bg-gray-100 border border-gray-300 rounded-lg text-left text-gray-700 hover:bg-gray-200 transition-colors flex items-center justify-between"
          >
            <span>
              {formData.selectedCourse ? formData.selectedCourse.name : '과정 선택'}
            </span>
            <Upload className="w-4 h-4" />
          </button>
        </div>
        <div>
          <label className="block text-sm text-gray-700 mb-2">과정 카테고리</label>
          <select
            value={String(formData.categoryId)}
            disabled={categoryLoading}
            onChange={(e) => updateFormData({ categoryId: Number(e.target.value) })}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
          >
            <option value="0">{categoryLoading ? '카테고리 불러오는 중...' : '[미지정]'}</option>
            {courseCategories.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.label || c.name_conv || c.category_nm}
              </option>
            ))}
          </select>
          {categoryError && <div className="text-sm text-red-600 mt-1">{categoryError}</div>}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div>
          <label className="block text-sm text-gray-700 mb-2">
            과목명 <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={formData.subjectName}
            onChange={(e) => updateFormData({ subjectName: e.target.value })}
            placeholder="예: AI 기초 프로그래밍"
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-sm text-gray-700 mb-2">메인 이미지</label>
          <div className="space-y-2">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={handlePickImage}
                disabled={uploading}
                className={`flex-1 px-4 py-2 bg-gray-100 border border-gray-300 rounded-lg text-left text-gray-700 transition-colors flex items-center justify-between ${
                  uploading ? 'opacity-60 cursor-not-allowed' : 'hover:bg-gray-200'
                }`}
              >
                <span>{uploading ? '업로드 중...' : (formData.courseFileName ? '이미지 변경' : '파일 업로드')}</span>
                <Upload className="w-4 h-4" />
              </button>
              {formData.courseFileName && (
                <button
                  type="button"
                  onClick={clearImage}
                  className="px-3 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
                >
                  삭제
                </button>
              )}
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleImageChange}
            />

            {uploadError && (
              <div className="text-sm text-red-600">{uploadError}</div>
            )}

            {formData.courseFileUrl && (
              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <img
                  src={formData.courseFileUrl}
                  alt="메인 이미지"
                  className="w-full h-32 object-cover"
                />
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div>
          <label className="block text-sm text-gray-700 mb-2">년도/학기</label>
          <div className="flex gap-2">
            <input
              type="text"
              value={formData.year}
              onChange={(e) => {
                // 왜: 년도는 DB에서 4자리 숫자 컬럼으로 관리하므로, 입력 단계에서 숫자 4자리로 고정합니다.
                const onlyDigits = e.target.value.replace(/[^0-9]/g, '').slice(0, 4);
                updateFormData({ year: onlyDigits });
              }}
              placeholder="2024"
              inputMode="numeric"
              maxLength={4}
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <select
              value={formData.semester}
              onChange={(e) => updateFormData({ semester: e.target.value })}
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option>1학기</option>
              <option>2학기</option>
            </select>
          </div>
        </div>
        <div>
          <label className="block text-sm text-gray-700 mb-2">시수</label>
          <input
            type="number"
            value={formData.hours}
            onChange={(e) => updateFormData({ hours: e.target.value })}
            onBlur={() => {
              // 왜: 입력을 비우고 넘어가면 기본값(15)로 동작하게 맞춥니다.
              const normalized = String(normalizeSessionCount(formData.hours));
              if (String(formData.hours || '').trim() !== normalized) updateFormData({ hours: normalized });
            }}
            placeholder="48"
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm text-gray-700 mb-2">수업 기간</label>
        <div className="flex items-center gap-3">
          <input
            type="date"
            value={formData.startDate}
            onChange={(e) => updateFormData({ startDate: e.target.value })}
            className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <span className="text-gray-500">~</span>
          <input
            type="date"
            value={formData.endDate}
            onChange={(e) => updateFormData({ endDate: e.target.value })}
            className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm text-gray-700 mb-2">과목 소개</label>
        <textarea
          value={formData.description}
          onChange={(e) => updateFormData({ description: e.target.value })}
          placeholder="과목 소개"
          rows={4}
          className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <div>
        <label className="block text-sm text-gray-700 mb-2">과목 세부내용</label>
        <textarea
          value={formData.objectives}
          onChange={(e) => updateFormData({ objectives: e.target.value })}
          placeholder="과목 소개 내용"
          rows={4}
          className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Course Selection Modal */}
      <CourseSelectionModal
        isOpen={isCourseModalOpen}
        onClose={() => setIsCourseModalOpen(false)}
        onSelect={handleCourseSelect}
        selectedCourse={formData.selectedCourse}
      />
    </div>
  );
}

// 학습자 선택 단계
function LearnersStep({
  formData,
  updateFormData,
}: {
  formData: FormData;
  updateFormData: (updates: Partial<FormData>) => void;
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
  const [campus, setCampus] = useState('전체');
  const [major, setMajor] = useState('전체');

  const [allLearners, setAllLearners] = useState<Learner[]>([]);
  const [page, setPage] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const pageSize = 30;
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  React.useEffect(() => {
    // 왜: 이름 검색은 입력할 때마다 API를 치면 서버/브라우저 모두 부담이 커집니다.
    //     그래서 250ms 동안 입력이 멈췄을 때만 실제 검색어를 확정합니다.
    const timer = setTimeout(() => setDebouncedSearchTerm(searchTerm), 250);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  // 왜: 샘플 배열이 아니라, 실제 회원(TB_USER)을 "페이지 단위"로 검색해서 선택해야 합니다.
  React.useEffect(() => {
    let cancelled = false;

    const fetchLearners = async () => {
      setLoading(true);
      setErrorMessage(null);
      try {
        // 왜: 캠퍼스/전공은 사이트마다 DB 필드가 달라서, 현재는 "부서명 포함 검색"으로 단순 지원합니다.
        const deptKeyword = major !== '전체' ? major : (campus !== '전체' ? campus : undefined);

        const res = await tutorLmsApi.getLearners({
          keyword: debouncedSearchTerm,
          deptKeyword,
          page,
          limit: pageSize,
        });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const rows = res.rst_data ?? [];
        const mapped: Learner[] = rows.map((row: any) => {
          const deptPath = String(row.dept_path || '');
          const parts = deptPath.split(' > ').map((p: string) => p.trim()).filter(Boolean);
          const campusName = parts[0] || String(row.dept_nm || '-') || '-';
          const majorName = (parts.length > 0 ? parts[parts.length - 1] : String(row.dept_nm || '-')) || '-';

          return {
            id: String(row.id),
            name: String(row.name || row.user_nm || '-'),
            campus: campusName,
            major: majorName,
            studentId: String(row.student_id || ''),
            email: String(row.email || ''),
            deptPath,
          };
        });

        if (!cancelled) {
          setAllLearners(mapped);
          setTotalCount(Number(res.rst_total ?? res.rst_count ?? rows.length ?? 0));
        }
      } catch (e) {
        if (!cancelled) setErrorMessage(e instanceof Error ? e.message : '조회 중 오류가 발생했습니다.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    fetchLearners();

    return () => {
      cancelled = true;
    };
  }, [debouncedSearchTerm, campus, major, page]);

  // 왜: DB 구조(부서 트리)에 따라 옵션이 달라지므로, 현재 조회된 목록 기준으로 필터 옵션을 만들어 제공합니다.
  const campusOptions = ['전체', ...Array.from(new Set(allLearners.map((l) => l.campus).filter(Boolean)))];
  const majorOptions = ['전체', ...Array.from(new Set(
    allLearners
      .filter((l) => campus === '전체' || l.campus === campus)
      .map((l) => l.major)
      .filter(Boolean),
  ))];

  const toggleLearner = (learner: Learner) => {
    const isSelected = formData.selectedLearners.some((l) => l.id === learner.id);
    if (isSelected) {
      updateFormData({
        selectedLearners: formData.selectedLearners.filter((l) => l.id !== learner.id),
      });
    } else {
      updateFormData({
        selectedLearners: [...formData.selectedLearners, learner],
      });
    }
  };

  const selectAll = () => {
    // 왜: 페이징 화면에서는 "현재 페이지에 보이는 목록"만 전체 선택하는 것이 안전합니다.
    updateFormData({ selectedLearners: [...allLearners] });
  };

  const deselectAll = () => {
    updateFormData({ selectedLearners: [] });
  };

  const totalPages = Math.max(1, Math.ceil((totalCount || 0) / pageSize));
  const canPrev = page > 1;
  const canNext = page < totalPages;

  return (
    <div className="space-y-6">
      <h3 className="text-gray-900">학습자 선택</h3>

      {/* 검색 및 필터 */}
      <div className="bg-gray-50 p-4 rounded-lg space-y-4">
        <div className="grid grid-cols-3 gap-4">
          <div>
            <label className="block text-sm text-gray-700 mb-2">이름 검색</label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => {
                  setSearchTerm(e.target.value);
                  setPage(1);
                }}
                placeholder="학습자 이름 검색..."
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm text-gray-700 mb-2">캠퍼스</label>
            <select
              value={campus}
              onChange={(e) => {
                setCampus(e.target.value);
                setMajor('전체');
                setPage(1);
              }}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {campusOptions.map((opt) => (
                <option key={opt} value={opt}>{opt}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm text-gray-700 mb-2">전공</label>
            <select
              value={major}
              onChange={(e) => {
                setMajor(e.target.value);
                setPage(1);
              }}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {majorOptions.map((opt) => (
                <option key={opt} value={opt}>{opt}</option>
              ))}
            </select>
          </div>
        </div>

        {errorMessage && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
            {errorMessage}
          </div>
        )}

        <div className="flex items-center justify-between">
          <div className="flex gap-3">
            <button
              onClick={selectAll}
              disabled={loading}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              전체 선택
            </button>
            <button
              onClick={deselectAll}
              disabled={loading}
              className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
            >
              현재 목록 전체 해제
            </button>
          </div>
          <div className="text-sm text-gray-600">
            {loading ? '불러오는 중...' : `검색 결과: ${totalCount}명 / 선택된: ${formData.selectedLearners.length}명`}
          </div>
        </div>
      </div>

      {/* 학습자 목록 */}
      <div className="grid grid-cols-3 gap-4">
        {allLearners.map((learner) => {
          const isSelected = formData.selectedLearners.some((l) => l.id === learner.id);
          return (
            <div
              key={learner.id}
              onClick={() => toggleLearner(learner)}
              className={`p-4 border-2 rounded-lg cursor-pointer transition-all ${
                isSelected
                  ? 'border-blue-600 bg-blue-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <div className="text-gray-900">{learner.name}</div>
                {isSelected && (
                  <div className="w-5 h-5 bg-blue-600 rounded-full flex items-center justify-center">
                    <CheckCircle className="w-3 h-3 text-white" />
                  </div>
                )}
              </div>
              <div className="text-sm text-gray-600">{learner.campus}</div>
              <div className="text-sm text-gray-600">{learner.major}</div>
            </div>
          );
        })}
      </div>

      {/* 페이징 */}
      <div className="flex items-center justify-center gap-3">
        <button
          type="button"
          disabled={loading || !canPrev}
          onClick={() => setPage((prev) => Math.max(1, prev - 1))}
          className="px-3 py-2 border border-gray-300 rounded-lg text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition-colors"
        >
          이전
        </button>
        <div className="text-sm text-gray-600">
          {page} / {totalPages}
        </div>
        <button
          type="button"
          disabled={loading || !canNext}
          onClick={() => setPage((prev) => Math.min(totalPages, prev + 1))}
          className="px-3 py-2 border border-gray-300 rounded-lg text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition-colors"
        >
          다음
        </button>
      </div>
    </div>
  );
}

// 차시 구성 단계
function CurriculumStep({
  formData,
  updateFormData,
  onTouched,
}: {
  formData: FormData;
  updateFormData: (updates: Partial<FormData>) => void;
  onTouched?: () => void;
}) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const currentSession = currentSessionId
    ? formData.sessions.find((s) => s.id === currentSessionId) ?? null
    : null;

  const addSession = () => {
    onTouched?.();
    const newSession = {
      id: String(formData.sessions.length + 1),
      title: '',
      description: '',
      videos: [],
    };
    updateFormData({ sessions: [...formData.sessions, newSession] });
  };

  const updateSession = (id: string, updates: Partial<typeof formData.sessions[0]>) => {
    onTouched?.();
    const updatedSessions = formData.sessions.map((session) =>
      session.id === id ? { ...session, ...updates } : session
    );
    updateFormData({ sessions: updatedSessions });
  };

  const removeSession = (id: string) => {
    onTouched?.();
    if (formData.sessions.length > 1) {
      updateFormData({
        sessions: formData.sessions.filter((session) => session.id !== id),
      });
    }
  };

  const addVideo = (sessionId: string) => {
    onTouched?.();
    const session = formData.sessions.find((s) => s.id === sessionId);
    if (session) {
      const newVideo = {
        id: `${sessionId}-video-${Date.now()}-${Math.random()}`,
        title: '',
        url: '',
      };
      updateSession(sessionId, {
        videos: [...session.videos, newVideo],
      });
    }
  };

  const updateVideo = (sessionId: string, videoId: string, updates: { title?: string; url?: string }) => {
    onTouched?.();
    const session = formData.sessions.find((s) => s.id === sessionId);
    if (session) {
      const updatedVideos = session.videos.map((video) =>
        video.id === videoId ? { ...video, ...updates } : video
      );
      updateSession(sessionId, { videos: updatedVideos });
    }
  };

  const removeVideo = (sessionId: string, videoId: string) => {
    onTouched?.();
    const session = formData.sessions.find((s) => s.id === sessionId);
    if (session) {
      updateSession(sessionId, {
        videos: session.videos.filter((video) => video.id !== videoId),
      });
    }
  };

  const handleContentSelect = (content: any) => {
    onTouched?.();
    if (currentSessionId) {
      const session = formData.sessions.find((s) => s.id === currentSessionId);
      if (session) {
        const newVideo = {
          id: `${currentSessionId}-video-${Date.now()}-${Math.random()}`,
          title: content.title,
          url: '',
          // 왜: 콘텐츠 모달에서 고른 것은 "레슨 ID"로 저장하고, 실제 DB 추가 시 lesson_id로 처리합니다.
          lessonId: String(content.lessonId ?? content.id),
        };
        updateSession(currentSessionId, {
          videos: [...session.videos, newVideo],
        });
      }
    }
    setIsModalOpen(false);
  };

  const openContentModal = (sessionId: string) => {
    setCurrentSessionId(sessionId);
    setIsModalOpen(true);
  };

  return (
    <div className="space-y-6">
      <h3 className="text-gray-900">차시별 구성</h3>

      <div className="space-y-6">
        {formData.sessions.map((session, index) => (
          <div key={session.id} className="border border-gray-200 rounded-lg p-6 relative">
            {formData.sessions.length > 1 && (
              <button
                onClick={() => removeSession(session.id)}
                className="absolute top-4 right-4 text-gray-400 hover:text-red-600 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            )}

            <h4 className="text-gray-900 mb-4">{index + 1}차시</h4>

            <div className="space-y-4">
              <div>
                <label className="block text-sm text-gray-700 mb-2">차시 제목</label>
                <input
                  type="text"
                  value={session.title}
                  onChange={(e) => updateSession(session.id, { title: e.target.value })}
                  placeholder="예: Python 기초 문법"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-sm text-gray-700 mb-2">차시 설명</label>
                <textarea
                  value={session.description}
                  onChange={(e) => updateSession(session.id, { description: e.target.value })}
                  placeholder="이 차시에서 학습할 내용을 설명하세요"
                  rows={3}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* 강의 영상 목록 */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <label className="block text-sm text-gray-700">강의 영상 ({session.videos.length})</label>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => openContentModal(session.id)}
                      className="px-3 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                    >
                      콘텐츠 검색
                    </button>
                    <button
                      type="button"
                      onClick={() => addVideo(session.id)}
                      className="flex items-center gap-1 px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                      영상 추가
                    </button>
                  </div>
                </div>

                {/* 영상 목록 */}
                {session.videos.length > 0 ? (
                  <div className="space-y-3">
                    {session.videos.map((video, videoIndex) => (
                      <div
                        key={video.id}
                        className="bg-gray-50 border border-gray-200 rounded-lg p-4"
                      >
                        <div className="flex items-start gap-3">
                          <div className="flex items-center justify-center w-8 h-8 bg-blue-100 text-blue-700 rounded-lg text-sm shrink-0">
                            {videoIndex + 1}
                          </div>
                          <div className="flex-1 space-y-3">
                            <div>
                              <label className="block text-xs text-gray-600 mb-1">
                                영상 제목
                              </label>
                              <input
                                type="text"
                                value={video.title}
                                onChange={(e) =>
                                  updateVideo(session.id, video.id, { title: e.target.value })
                                }
                                placeholder="영상 제목을 입력하세요"
                                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                              />
                            </div>
                            <div>
                              <label className="block text-xs text-gray-600 mb-1">
                                레슨 ID 또는 URL
                              </label>
                              <input
                                type="text"
                                value={video.lessonId ? `레슨 ID: ${video.lessonId}` : video.url}
                                onChange={(e) => {
                                  if (video.lessonId) return;
                                  updateVideo(session.id, video.id, { url: e.target.value });
                                }}
                                disabled={Boolean(video.lessonId)}
                                placeholder={video.lessonId ? '' : '외부 URL을 입력하세요'}
                                className={`w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                                  video.lessonId ? 'bg-gray-100 text-gray-500' : ''
                                }`}
                              />
                            </div>
                          </div>
                          <button
                            type="button"
                            onClick={() => removeVideo(session.id, video.id)}
                            className="p-2 text-gray-400 hover:text-red-600 transition-colors shrink-0"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="bg-gray-50 border border-gray-200 rounded-lg p-8 text-center">
                    <p className="text-sm text-gray-500">
                      영상을 추가해주세요
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      <button
        onClick={addSession}
        className="w-full py-3 border-2 border-dashed border-gray-300 rounded-lg text-gray-600 hover:border-gray-400 hover:text-gray-700 transition-colors flex items-center justify-center gap-2"
      >
        <Plus className="w-5 h-5" />
        차시 추가
      </button>

      {/* Content Library Modal */}
      <ContentLibraryModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSelect={handleContentSelect}
        recommendContext={{
          // 왜: 추천은 "과목/차시 입력값"을 기반으로 해야 품질이 나오므로, 현재 폼 상태를 그대로 전달합니다.
          courseName: formData.subjectName,
          courseIntro: formData.description,
          courseDetail: formData.objectives,
          lessonTitle: currentSession?.title ?? '',
          lessonDescription: currentSession?.description ?? '',
        }}
        excludeLessonIds={formData.sessions
          .flatMap((s) => s.videos)
          .map((v) => (v.lessonId ? Number(String(v.lessonId).replace(/[^0-9]/g, '')) : 0))
          .filter((n) => Number.isFinite(n) && n > 0)}
      />
    </div>
  );
}

// 최종 확인 단계
function ConfirmStep({
  formData,
  courseCategories,
}: {
  formData: FormData;
  courseCategories: TutorCourseCategoryRow[];
}) {
  const selectedCategory = courseCategories.find(
    (c) => Number(c.id) === Number(formData.categoryId)
  );
  const categoryLabel =
    selectedCategory?.label || selectedCategory?.name_conv || selectedCategory?.category_nm || '-';

  return (
    <div className="space-y-8">
      <h3 className="text-gray-900">최종 확인</h3>

      <div className="space-y-6">
        {/* 기본 정보 */}
        <div>
          <h4 className="text-gray-900 mb-4 pb-2 border-b border-gray-200">기본 정보</h4>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-gray-600">과목명:</span>
              <span className="ml-2 text-gray-900">{formData.subjectName || '-'}</span>
            </div>
            <div>
              <span className="text-gray-600">소속 과정:</span>
              <span className="ml-2 text-gray-900">
                {formData.selectedCourse ? formData.selectedCourse.name : '-'}
              </span>
            </div>
            <div>
              <span className="text-gray-600">카테고리:</span>
              <span className="ml-2 text-gray-900">{categoryLabel}</span>
            </div>
            <div>
              <span className="text-gray-600">년도/학기:</span>
              <span className="ml-2 text-gray-900">
                {formData.year} {formData.semester}
              </span>
            </div>
            <div>
              <span className="text-gray-600">시수:</span>
              <span className="ml-2 text-gray-900">{formData.hours || '-'}</span>
            </div>
            <div>
              <span className="text-gray-600">학점:</span>
              <span className="ml-2 text-gray-900">{formData.credits || '-'}</span>
            </div>
          </div>
        </div>

        {/* 학습자 정보 */}
        <div>
          <h4 className="text-gray-900 mb-4 pb-2 border-b border-gray-200">학습자</h4>
          <div className="text-sm">
            <span className="text-gray-600">선택된 학습자:</span>
            <span className="ml-2 text-gray-900">{formData.selectedLearners.length}명</span>
          </div>
          {formData.selectedLearners.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-2">
              {formData.selectedLearners.map((learner) => (
                <span
                  key={learner.id}
                  className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm"
                >
                  {learner.name}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* 차시 정보 */}
        <div>
          <h4 className="text-gray-900 mb-4 pb-2 border-b border-gray-200">차시 구성</h4>
          <div className="text-sm mb-3">
            <span className="text-gray-600">총 차시:</span>
            <span className="ml-2 text-gray-900">{formData.sessions.length}차시</span>
          </div>
          <div className="space-y-3">
            {formData.sessions.map((session, index) => (
              <div key={session.id} className="p-4 bg-gray-50 rounded-lg">
                <div className="text-gray-900 mb-1">
                  {index + 1}차시: {session.title || '(제목 없음)'}
                </div>
                {session.description && (
                  <div className="text-sm text-gray-600 mb-2">{session.description}</div>
                )}
                {session.videos.length > 0 && (
                  <div className="mt-3 pl-4 border-l-2 border-blue-300">
                    <div className="text-sm text-gray-700 mb-2">
                      강의 영상 {session.videos.length}개
                    </div>
                    <div className="space-y-1">
                      {session.videos.map((video, videoIndex) => (
                        <div key={video.id} className="text-sm text-gray-600">
                          {videoIndex + 1}. {video.title || '(제목 없음)'}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
        <p className="text-sm text-blue-900">
          위 내용을 확인하고 "과목 개설 완료" 버튼을 클릭하여 과목 개설을 완료하세요.
        </p>
      </div>
    </div>
  );
}
