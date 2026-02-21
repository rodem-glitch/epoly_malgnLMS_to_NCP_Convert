import React, { useState, useMemo } from 'react';
import {
  Plus, Edit, Trash2, Search, Eye, EyeOff, Save, X,
  Users, BarChart3, ChevronDown, ChevronRight,
  ClipboardList, Lock, Unlock, Calendar, CheckCircle2, Download,
} from 'lucide-react';
import { downloadCsv } from '../utils/csv';

// ===== 타입 정의 =====

type QuestionType = 'single' | 'multi' | 'text' | 'scale';

interface SurveyQuestion {
  id: string;
  type: QuestionType;
  text: string;
  options: string[]; // single, multi 일 때 선택지
  scaleMin?: number; // scale 일 때
  scaleMax?: number;
  required: boolean;
}

type SurveyStatus = 'draft' | 'active' | 'closed';

interface Survey {
  id: string;
  title: string;
  description: string;
  anonymous: boolean;       // 익명제 여부
  status: SurveyStatus;
  questions: SurveyQuestion[];
  targetCourses: string[];  // 빈 배열 = 전체
  startDate: string;
  endDate: string;
  responseCount: number;
  createdAt: string;
}

// ===== 샘플 데이터 =====

const SAMPLE_SURVEYS: Survey[] = [
  {
    id: '1',
    title: '수업 만족도 설문',
    description: '한 학기 수업에 대한 전반적인 만족도를 조사합니다.',
    anonymous: true,
    status: 'active',
    questions: [
      {
        id: 'q1', type: 'scale', text: '수업 내용에 대한 만족도를 평가해 주세요.',
        options: [], scaleMin: 1, scaleMax: 5, required: true,
      },
      {
        id: 'q2', type: 'single', text: '수업 난이도는 어떻게 느끼셨나요?',
        options: ['매우 쉬움', '쉬움', '보통', '어려움', '매우 어려움'], required: true,
      },
      {
        id: 'q3', type: 'text', text: '수업에 대한 자유 의견을 작성해 주세요.',
        options: [], required: false,
      },
    ],
    targetCourses: [],
    startDate: '2026-02-01',
    endDate: '2026-02-28',
    responseCount: 42,
    createdAt: '2026-01-25',
  },
  {
    id: '2',
    title: '선호 과제 유형 조사',
    description: '학생들이 선호하는 과제 유형을 파악합니다.',
    anonymous: false,
    status: 'draft',
    questions: [
      {
        id: 'q1', type: 'multi', text: '선호하는 과제 유형을 모두 선택해 주세요.',
        options: ['레포트', '프레젠테이션', '코딩 과제', '팀 프로젝트', '실습 보고서'], required: true,
      },
      {
        id: 'q2', type: 'single', text: '과제 제출 빈도로 적절한 것은?',
        options: ['매주', '2주마다', '월 1회', '중간/기말 각 1회'], required: true,
      },
    ],
    targetCourses: [],
    startDate: '',
    endDate: '',
    responseCount: 0,
    createdAt: '2026-02-18',
  },
  {
    id: '3',
    title: '온라인 강의 개선 의견',
    description: '온라인 강의 환경 개선을 위한 의견을 수집합니다.',
    anonymous: true,
    status: 'closed',
    questions: [
      {
        id: 'q1', type: 'scale', text: '온라인 강의 화질/음질에 대한 만족도',
        options: [], scaleMin: 1, scaleMax: 5, required: true,
      },
      {
        id: 'q2', type: 'text', text: '개선이 필요한 사항을 자유롭게 작성해 주세요.',
        options: [], required: false,
      },
    ],
    targetCourses: [],
    startDate: '2026-01-01',
    endDate: '2026-01-31',
    responseCount: 67,
    createdAt: '2025-12-20',
  },
];

// ===== 유틸/상수 =====

const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  single: '단일 선택',
  multi: '복수 선택',
  text: '서술형',
  scale: '척도(리커트)',
};

const STATUS_CONFIG: Record<SurveyStatus, { label: string; color: string }> = {
  draft: { label: '임시저장', color: 'bg-gray-100 text-gray-600' },
  active: { label: '진행 중', color: 'bg-green-100 text-green-700' },
  closed: { label: '마감', color: 'bg-red-100 text-red-600' },
};

const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);

// ===== 컴포넌트 =====

export function SurveyManagePage() {
  // ---------- 목록 ----------
  const [surveys, setSurveys] = useState<Survey[]>(SAMPLE_SURVEYS);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState<SurveyStatus | 'all'>('all');

  // ---------- 모달 ----------
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  // ---------- 결과 보기 ----------
  const [resultSurvey, setResultSurvey] = useState<Survey | null>(null);

  // ---------- 폼 ----------
  const [formTitle, setFormTitle] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formAnon, setFormAnon] = useState(true);
  const [formStart, setFormStart] = useState('');
  const [formEnd, setFormEnd] = useState('');
  const [formQuestions, setFormQuestions] = useState<SurveyQuestion[]>([]);

  // ---------- 필터링 ----------
  const filtered = useMemo(() => {
    let list = surveys;
    if (filterStatus !== 'all') list = list.filter(s => s.status === filterStatus);
    if (searchTerm.trim()) {
      const term = searchTerm.toLowerCase();
      list = list.filter(s => s.title.toLowerCase().includes(term) || s.description.toLowerCase().includes(term));
    }
    return list;
  }, [surveys, searchTerm, filterStatus]);

  // ---------- 모달 열기 ----------
  const openAddModal = () => {
    setEditingId(null);
    setFormTitle('');
    setFormDesc('');
    setFormAnon(true);
    setFormStart('');
    setFormEnd('');
    setFormQuestions([]);
    setModalOpen(true);
  };

  const openEditModal = (survey: Survey) => {
    setEditingId(survey.id);
    setFormTitle(survey.title);
    setFormDesc(survey.description);
    setFormAnon(survey.anonymous);
    setFormStart(survey.startDate);
    setFormEnd(survey.endDate);
    setFormQuestions(JSON.parse(JSON.stringify(survey.questions)));
    setModalOpen(true);
  };

  // ---------- 설문 저장 ----------
  const handleSave = () => {
    if (!formTitle.trim()) { alert('설문 제목을 입력해 주세요.'); return; }
    if (formQuestions.length === 0) { alert('질문을 최소 1개 이상 추가해 주세요.'); return; }

    if (editingId) {
      setSurveys(prev => prev.map(s =>
        s.id === editingId ? {
          ...s,
          title: formTitle.trim(),
          description: formDesc.trim(),
          anonymous: formAnon,
          startDate: formStart,
          endDate: formEnd,
          questions: formQuestions,
        } : s,
      ));
    } else {
      const newSurvey: Survey = {
        id: uid(),
        title: formTitle.trim(),
        description: formDesc.trim(),
        anonymous: formAnon,
        status: 'draft',
        questions: formQuestions,
        targetCourses: [],
        startDate: formStart,
        endDate: formEnd,
        responseCount: 0,
        createdAt: new Date().toISOString().slice(0, 10),
      };
      setSurveys(prev => [newSurvey, ...prev]);
    }
    setModalOpen(false);
  };

  // ---------- 삭제 ----------
  const handleDelete = (id: string) => {
    if (!confirm('이 설문을 삭제하시겠습니까?')) return;
    setSurveys(prev => prev.filter(s => s.id !== id));
  };

  // ---------- 상태 변경 ----------
  const toggleStatus = (id: string) => {
    setSurveys(prev => prev.map(s => {
      if (s.id !== id) return s;
      const next: SurveyStatus = s.status === 'draft' ? 'active' : s.status === 'active' ? 'closed' : 'draft';
      return { ...s, status: next };
    }));
  };

  // ---------- 질문 CRUD ----------
  const addQuestion = (type: QuestionType) => {
    const q: SurveyQuestion = {
      id: uid(),
      type,
      text: '',
      options: type === 'single' || type === 'multi' ? [''] : [],
      scaleMin: type === 'scale' ? 1 : undefined,
      scaleMax: type === 'scale' ? 5 : undefined,
      required: true,
    };
    setFormQuestions(prev => [...prev, q]);
  };

  const updateQuestion = (qid: string, patch: Partial<SurveyQuestion>) => {
    setFormQuestions(prev => prev.map(q => q.id === qid ? { ...q, ...patch } : q));
  };

  const removeQuestion = (qid: string) => {
    setFormQuestions(prev => prev.filter(q => q.id !== qid));
  };

  const addOption = (qid: string) => {
    setFormQuestions(prev => prev.map(q =>
      q.id === qid ? { ...q, options: [...q.options, ''] } : q,
    ));
  };

  const updateOption = (qid: string, index: number, value: string) => {
    setFormQuestions(prev => prev.map(q => {
      if (q.id !== qid) return q;
      const opts = [...q.options];
      opts[index] = value;
      return { ...q, options: opts };
    }));
  };

  const removeOption = (qid: string, index: number) => {
    setFormQuestions(prev => prev.map(q => {
      if (q.id !== qid) return q;
      return { ...q, options: q.options.filter((_, i) => i !== index) };
    }));
  };

  // ---------- 통계 ----------
  const stats = useMemo(() => ({
    total: surveys.length,
    active: surveys.filter(s => s.status === 'active').length,
    draft: surveys.filter(s => s.status === 'draft').length,
    closed: surveys.filter(s => s.status === 'closed').length,
    totalResponses: surveys.reduce((sum, s) => sum + s.responseCount, 0),
  }), [surveys]);

  // ---------- 엑셀 다운로드 ----------
  const handleDownloadResponseList = (survey: Survey) => {
    // 왜: 백엔드 연동 전이므로 설문 구조 기반 샘플 데이터를 생성합니다.
    //     실제 연동 시 API에서 받은 응답 데이터로 교체하면 됩니다.
    const headers = [
      '번호',
      ...(survey.anonymous ? [] : ['학번', '이름']),
      '응답일시',
      ...survey.questions.map((q, i) => `Q${i + 1}. ${q.text}`),
    ];

    const rows = Array.from({ length: survey.responseCount }, (_, ri) => {
      const row: (string | number)[] = [ri + 1];
      if (!survey.anonymous) {
        row.push(`2024${String(ri + 1).padStart(4, '0')}`, `학생${ri + 1}`);
      }
      row.push(`2026-02-${String((ri % 28) + 1).padStart(2, '0')}`);
      survey.questions.forEach(q => {
        if (q.type === 'single') {
          row.push(q.options[Math.floor(Math.random() * q.options.length)] || '');
        } else if (q.type === 'multi') {
          const count = Math.floor(Math.random() * q.options.length) + 1;
          const picked = q.options.sort(() => Math.random() - 0.5).slice(0, count);
          row.push(picked.join('; '));
        } else if (q.type === 'scale') {
          row.push(Math.floor(Math.random() * ((q.scaleMax ?? 5) - (q.scaleMin ?? 1) + 1)) + (q.scaleMin ?? 1));
        } else {
          row.push('(서술형 응답 텍스트)');
        }
      });
      return row;
    });

    downloadCsv(`설문_응답목록_${survey.title}.csv`, headers, rows);
  };

  const handleDownloadResultSummary = (survey: Survey) => {
    const headers = ['질문번호', '유형', '질문내용', '필수여부', '선택지/범위', '응답수'];
    const rows = survey.questions.map((q, i) => {
      let optionsStr = '';
      if (q.type === 'single' || q.type === 'multi') {
        optionsStr = q.options.join(' / ');
      } else if (q.type === 'scale') {
        optionsStr = `${q.scaleMin ?? 1} ~ ${q.scaleMax ?? 5}`;
      } else {
        optionsStr = '자유 서술';
      }
      return [
        `Q${i + 1}`,
        QUESTION_TYPE_LABELS[q.type],
        q.text,
        q.required ? '필수' : '선택',
        optionsStr,
        survey.responseCount,
      ];
    });

    downloadCsv(`설문_결과요약_${survey.title}.csv`, headers, rows);
  };

  // ========== 결과 화면 ==========
  if (resultSurvey) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <button
              onClick={() => setResultSurvey(null)}
              className="text-sm text-blue-600 hover:underline mb-2 inline-block"
            >
              ← 목록으로 돌아가기
            </button>
            <h1 className="text-gray-900 mb-1">설문 결과</h1>
            <p className="text-gray-600">{resultSurvey.title}</p>
          </div>
          <div className="flex items-center gap-3 text-sm">
            <button
              onClick={() => handleDownloadResponseList(resultSurvey)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm text-green-700 bg-green-50 border border-green-200 rounded-lg hover:bg-green-100 transition-colors"
              title="개별 응답을 엑셀로 다운로드"
            >
              <Download className="w-4 h-4" />
              <span>응답 목록</span>
            </button>
            <button
              onClick={() => handleDownloadResultSummary(resultSurvey)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
              title="결과 요약을 엑셀로 다운로드"
            >
              <Download className="w-4 h-4" />
              <span>결과 요약</span>
            </button>
            <span className="border-l border-gray-300 h-5" />
            <span className={`px-3 py-1 rounded-full text-xs font-medium ${STATUS_CONFIG[resultSurvey.status].color}`}>
              {STATUS_CONFIG[resultSurvey.status].label}
            </span>
            <span className="flex items-center gap-1.5 text-gray-600">
              {resultSurvey.anonymous ? <Lock className="w-4 h-4" /> : <Unlock className="w-4 h-4" />}
              {resultSurvey.anonymous ? '익명' : '실명'}
            </span>
            <span className="flex items-center gap-1.5 text-gray-600">
              <Users className="w-4 h-4" />
              응답 {resultSurvey.responseCount}건
            </span>
          </div>
        </div>

        {/* 질문별 결과 */}
        <div className="space-y-4">
          {resultSurvey.questions.map((q, qi) => (
            <div key={q.id} className="bg-white rounded-xl border border-gray-200 p-6">
              <div className="flex items-start gap-3 mb-4">
                <span className="flex items-center justify-center w-7 h-7 bg-blue-100 text-blue-700 text-sm font-bold rounded-full shrink-0">
                  {qi + 1}
                </span>
                <div>
                  <p className="font-medium text-gray-900">{q.text || '(질문 내용 없음)'}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{QUESTION_TYPE_LABELS[q.type]}{q.required ? ' · 필수' : ''}</p>
                </div>
              </div>

              {/* 왜: 백엔드 연동 전이므로 임의의 시각화(플레이스홀더)를 표시합니다. */}
              {(q.type === 'single' || q.type === 'multi') && (
                <div className="space-y-2">
                  {q.options.map((opt, oi) => {
                    const pct = Math.max(5, Math.round(Math.random() * 70 + 10));
                    return (
                      <div key={oi} className="flex items-center gap-3">
                        <span className="w-32 text-sm text-gray-700 truncate">{opt || `선택지 ${oi + 1}`}</span>
                        <div className="flex-1 bg-gray-100 rounded-full h-5 overflow-hidden">
                          <div className="h-full bg-blue-500 rounded-full transition-all" style={{ width: `${pct}%` }} />
                        </div>
                        <span className="text-sm text-gray-600 w-10 text-right">{pct}%</span>
                      </div>
                    );
                  })}
                </div>
              )}

              {q.type === 'scale' && (
                <div className="flex items-end gap-2 h-32">
                  {Array.from({ length: (q.scaleMax ?? 5) - (q.scaleMin ?? 1) + 1 }, (_, i) => {
                    const val = (q.scaleMin ?? 1) + i;
                    const pct = Math.max(10, Math.round(Math.random() * 80 + 10));
                    return (
                      <div key={val} className="flex flex-col items-center flex-1 gap-1">
                        <div className="w-full bg-blue-500 rounded-t" style={{ height: `${pct}%` }} />
                        <span className="text-xs text-gray-600">{val}</span>
                      </div>
                    );
                  })}
                </div>
              )}

              {q.type === 'text' && (
                <div className="bg-gray-50 rounded-lg p-4 text-sm text-gray-500 italic">
                  서술형 응답은 백엔드 연동 후 개별 응답 목록으로 표시됩니다.
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    );
  }

  // ========== 목록 화면 ==========
  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-gray-900 mb-1">설문 관리</h1>
          <p className="text-gray-600">학생 의견을 수렴하기 위한 설문을 만들고 관리합니다.</p>
        </div>
        <button
          onClick={openAddModal}
          className="flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
        >
          <Plus className="w-5 h-5" />
          <span>새 설문 만들기</span>
        </button>
      </div>

      {/* 통계 카드 */}
      <div className="grid grid-cols-4 gap-4">
        {[
          { label: '전체 설문', value: stats.total, color: 'text-gray-700', bg: 'bg-white' },
          { label: '진행 중', value: stats.active, color: 'text-green-700', bg: 'bg-green-50' },
          { label: '임시저장', value: stats.draft, color: 'text-gray-500', bg: 'bg-gray-50' },
          { label: '총 응답 수', value: stats.totalResponses, color: 'text-blue-700', bg: 'bg-blue-50' },
        ].map(c => (
          <div key={c.label} className={`${c.bg} rounded-xl border border-gray-200 p-4`}>
            <p className="text-sm text-gray-500 mb-1">{c.label}</p>
            <p className={`text-2xl font-bold ${c.color}`}>{c.value}</p>
          </div>
        ))}
      </div>

      {/* 필터 + 검색 */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            placeholder="설문 제목 또는 설명 검색..."
            className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <select
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value as any)}
          className="px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="all">전체 상태</option>
          <option value="draft">임시저장</option>
          <option value="active">진행 중</option>
          <option value="closed">마감</option>
        </select>
      </div>

      {/* 설문 목록 */}
      {filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center">
          <ClipboardList className="w-12 h-12 mx-auto mb-3 text-gray-300" />
          <p className="text-gray-500">등록된 설문이 없습니다.</p>
          <p className="text-sm text-gray-400 mt-1">새 설문을 만들어 학생 의견을 수렴해 보세요.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(survey => (
            <div
              key={survey.id}
              className="bg-white rounded-xl border border-gray-200 hover:border-blue-200 transition-colors"
            >
              <div className="flex items-center justify-between p-5">
                {/* 왼쪽: 설문 정보 */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1.5">
                    <h3 className="font-semibold text-gray-900 truncate">{survey.title}</h3>
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_CONFIG[survey.status].color}`}>
                      {STATUS_CONFIG[survey.status].label}
                    </span>
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      survey.anonymous
                        ? 'bg-purple-100 text-purple-700'
                        : 'bg-amber-100 text-amber-700'
                    }`}>
                      {survey.anonymous ? '🔒 익명' : '👤 실명'}
                    </span>
                  </div>
                  <p className="text-sm text-gray-500 truncate mb-2">{survey.description || '설명 없음'}</p>
                  <div className="flex items-center gap-4 text-xs text-gray-400">
                    <span>질문 {survey.questions.length}개</span>
                    <span>응답 {survey.responseCount}건</span>
                    {survey.startDate && survey.endDate && (
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3 h-3" />
                        {survey.startDate} ~ {survey.endDate}
                      </span>
                    )}
                    <span>생성일: {survey.createdAt}</span>
                  </div>
                </div>

                {/* 오른쪽: 액션 */}
                <div className="flex items-center gap-2 ml-4 shrink-0">
                  <button
                    onClick={() => setResultSurvey(survey)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-blue-600 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
                    title="결과 보기"
                  >
                    <BarChart3 className="w-4 h-4" />
                    <span>결과</span>
                  </button>
                  <button
                    onClick={() => toggleStatus(survey.id)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 bg-gray-50 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors"
                    title={survey.status === 'draft' ? '설문 시작' : survey.status === 'active' ? '마감하기' : '초기화'}
                  >
                    {survey.status === 'draft' ? <Eye className="w-4 h-4" /> : survey.status === 'active' ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    <span>{survey.status === 'draft' ? '시작' : survey.status === 'active' ? '마감' : '초기화'}</span>
                  </button>
                  <button
                    onClick={() => openEditModal(survey)}
                    className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                    title="편집"
                  >
                    <Edit className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(survey.id)}
                    className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    title="삭제"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ========== 설문 생성/편집 모달 ========== */}
      {modalOpen && (
        <div className="fixed inset-0 bg-gray-900/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-3xl w-full max-h-[90vh] flex flex-col">
            {/* 헤더 */}
            <div className="flex items-center justify-between p-5 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">
                {editingId ? '설문 편집' : '새 설문 만들기'}
              </h3>
              <button onClick={() => setModalOpen(false)} className="p-2 hover:bg-gray-100 rounded-lg">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* 본문 */}
            <div className="flex-1 overflow-y-auto p-5 space-y-5">
              {/* 기본 정보 */}
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">설문 제목 <span className="text-red-500">*</span></label>
                  <input
                    value={formTitle}
                    onChange={e => setFormTitle(e.target.value)}
                    placeholder="예: 수업 만족도 조사"
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">설명</label>
                  <textarea
                    value={formDesc}
                    onChange={e => setFormDesc(e.target.value)}
                    placeholder="설문에 대한 간략한 설명..."
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-y min-h-[70px]"
                    rows={2}
                  />
                </div>

                {/* 익명/실명 토글 */}
                <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="flex items-center gap-3">
                    {formAnon ? <Lock className="w-5 h-5 text-purple-600" /> : <Unlock className="w-5 h-5 text-amber-600" />}
                    <div>
                      <span className="font-medium text-gray-800">{formAnon ? '익명 설문' : '실명 설문'}</span>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {formAnon
                          ? '응답자의 신원이 공개되지 않아 솔직한 의견을 유도합니다.'
                          : '응답자의 이름/학번이 결과에 표시됩니다.'}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setFormAnon(!formAnon)}
                    className={`relative w-12 h-6 rounded-full transition-colors ${formAnon ? 'bg-purple-500' : 'bg-gray-300'}`}
                  >
                    <span className={`absolute top-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${formAnon ? 'left-6' : 'left-0.5'}`} />
                  </button>
                </div>

                {/* 기간 */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">시작일</label>
                    <input
                      type="date"
                      value={formStart}
                      onChange={e => setFormStart(e.target.value)}
                      className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">종료일</label>
                    <input
                      type="date"
                      value={formEnd}
                      onChange={e => setFormEnd(e.target.value)}
                      className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
              </div>

              {/* 구분선 */}
              <hr className="border-gray-200" />

              {/* 질문 목록 */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <h4 className="font-semibold text-gray-900">질문 구성</h4>
                  <span className="text-sm text-gray-500">{formQuestions.length}개</span>
                </div>

                {formQuestions.length === 0 && (
                  <div className="bg-gray-50 rounded-lg p-6 text-center text-gray-400 text-sm border border-dashed border-gray-300">
                    아래 버튼으로 질문을 추가해 주세요.
                  </div>
                )}

                <div className="space-y-4">
                  {formQuestions.map((q, qi) => (
                    <div key={q.id} className="border border-gray-200 rounded-lg p-4 bg-white">
                      <div className="flex items-start justify-between gap-3 mb-3">
                        <div className="flex items-center gap-2">
                          <span className="flex items-center justify-center w-6 h-6 bg-blue-100 text-blue-700 text-xs font-bold rounded-full">
                            {qi + 1}
                          </span>
                          <span className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                            {QUESTION_TYPE_LABELS[q.type]}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={q.required}
                              onChange={e => updateQuestion(q.id, { required: e.target.checked })}
                              className="rounded border-gray-300"
                            />
                            필수
                          </label>
                          <button
                            onClick={() => removeQuestion(q.id)}
                            className="p-1 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      {/* 질문 텍스트 */}
                      <input
                        value={q.text}
                        onChange={e => updateQuestion(q.id, { text: e.target.value })}
                        placeholder="질문 내용을 입력하세요"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />

                      {/* 선택지 (single / multi) */}
                      {(q.type === 'single' || q.type === 'multi') && (
                        <div className="space-y-2">
                          {q.options.map((opt, oi) => (
                            <div key={oi} className="flex items-center gap-2">
                              <span className="w-5 h-5 flex items-center justify-center border border-gray-300 rounded-full text-xs text-gray-400 shrink-0">
                                {oi + 1}
                              </span>
                              <input
                                value={opt}
                                onChange={e => updateOption(q.id, oi, e.target.value)}
                                placeholder={`선택지 ${oi + 1}`}
                                className="flex-1 px-3 py-1.5 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                              />
                              {q.options.length > 1 && (
                                <button
                                  onClick={() => removeOption(q.id, oi)}
                                  className="p-1 text-gray-400 hover:text-red-500"
                                >
                                  <X className="w-3.5 h-3.5" />
                                </button>
                              )}
                            </div>
                          ))}
                          <button
                            onClick={() => addOption(q.id)}
                            className="text-sm text-blue-600 hover:underline ml-7"
                          >
                            + 선택지 추가
                          </button>
                        </div>
                      )}

                      {/* 척도 설정 */}
                      {q.type === 'scale' && (
                        <div className="flex items-center gap-4 text-sm">
                          <div className="flex items-center gap-2">
                            <span className="text-gray-600">최소</span>
                            <input
                              type="number"
                              value={q.scaleMin ?? 1}
                              onChange={e => updateQuestion(q.id, { scaleMin: Number(e.target.value) })}
                              className="w-16 px-2 py-1 border border-gray-300 rounded text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                          </div>
                          <span className="text-gray-400">~</span>
                          <div className="flex items-center gap-2">
                            <span className="text-gray-600">최대</span>
                            <input
                              type="number"
                              value={q.scaleMax ?? 5}
                              onChange={e => updateQuestion(q.id, { scaleMax: Number(e.target.value) })}
                              className="w-16 px-2 py-1 border border-gray-300 rounded text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                          </div>
                        </div>
                      )}

                      {/* 서술형 안내 */}
                      {q.type === 'text' && (
                        <p className="text-xs text-gray-400 italic">학생이 자유롭게 텍스트를 입력합니다.</p>
                      )}
                    </div>
                  ))}
                </div>

                {/* 질문 추가 버튼들 */}
                <div className="flex items-center gap-2 mt-4">
                  <span className="text-sm text-gray-500 mr-1">질문 추가:</span>
                  {(['single', 'multi', 'text', 'scale'] as QuestionType[]).map(type => (
                    <button
                      key={type}
                      onClick={() => addQuestion(type)}
                      className="px-3 py-1.5 text-xs text-blue-600 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
                    >
                      + {QUESTION_TYPE_LABELS[type]}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* 푸터 */}
            <div className="flex items-center justify-end gap-3 p-5 border-t border-gray-200">
              <button
                onClick={() => setModalOpen(false)}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleSave}
                className="flex items-center gap-2 px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Save className="w-4 h-4" />
                <span>{editingId ? '저장' : '생성'}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
