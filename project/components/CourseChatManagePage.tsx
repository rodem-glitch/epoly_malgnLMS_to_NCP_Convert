import React, { useEffect, useMemo, useState } from 'react';
import { MessageSquare, Send } from 'lucide-react';
import { tutorLmsApi, type TutorCourseChatMessageRow, type TutorCourseChatRoomRow } from '../api/tutorLmsApi';

type CourseOption = {
  id: number;
  name: string;
};

export function CourseChatManagePage() {
  const [courses, setCourses] = useState<CourseOption[]>([]);
  const [courseId, setCourseId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState('');
  const [rooms, setRooms] = useState<TutorCourseChatRoomRow[]>([]);
  const [selectedThreadId, setSelectedThreadId] = useState<number | null>(null);
  const [messages, setMessages] = useState<TutorCourseChatMessageRow[]>([]);
  const [draft, setDraft] = useState('');
  const [loadingCourses, setLoadingCourses] = useState(false);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const selectedRoom = useMemo(
    () => rooms.find((room) => room.thread_id === selectedThreadId) ?? null,
    [rooms, selectedThreadId]
  );

  useEffect(() => {
    let cancelled = false;

    const loadCourses = async () => {
      setLoadingCourses(true);
      setErrorMessage(null);
      try {
        const res = await tutorLmsApi.getMyCoursesCombined({ limit: 200 });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);

        const map = new Map<number, string>();
        (res.rst_data ?? []).forEach((row: any) => {
          const id = Number(row.course_id ?? row.id ?? 0);
          if (!Number.isFinite(id) || id <= 0) return;
          if (map.has(id)) return;
          map.set(id, String(row.course_nm ?? row.course_name ?? row.name ?? `과목 ${id}`));
        });
        const options = Array.from(map.entries()).map(([id, name]) => ({ id, name }));
        options.sort((a, b) => a.name.localeCompare(b.name));

        if (cancelled) return;
        setCourses(options);
        if (options.length > 0) setCourseId((prev) => prev ?? options[0].id);
      } catch (e) {
        if (!cancelled) {
          setErrorMessage(e instanceof Error ? e.message : '과목 목록을 불러오는 중 오류가 발생했습니다.');
        }
      } finally {
        if (!cancelled) setLoadingCourses(false);
      }
    };

    void loadCourses();
    return () => {
      cancelled = true;
    };
  }, []);

  const fetchRooms = async (targetCourseId: number, searchKeyword?: string) => {
    setLoadingRooms(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getCourseChatRooms({
        courseId: targetCourseId,
        keyword: searchKeyword?.trim() ? searchKeyword.trim() : undefined,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      const nextRooms = res.rst_data ?? [];
      setRooms(nextRooms);
      setSelectedThreadId((prev) => {
        if (prev && nextRooms.some((room) => room.thread_id === prev)) return prev;
        return nextRooms.length > 0 ? nextRooms[0].thread_id : null;
      });
    } catch (e) {
      setRooms([]);
      setSelectedThreadId(null);
      setMessages([]);
      setErrorMessage(e instanceof Error ? e.message : '채팅 목록을 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoadingRooms(false);
    }
  };

  useEffect(() => {
    if (!courseId) return;
    void fetchRooms(courseId);
  }, [courseId]);

  const fetchMessages = async (targetCourseId: number, threadId: number) => {
    setLoadingMessages(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.getCourseChatMessages({
        courseId: targetCourseId,
        threadId,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);
      setMessages(res.rst_data ?? []);
    } catch (e) {
      setMessages([]);
      setErrorMessage(e instanceof Error ? e.message : '메시지를 불러오는 중 오류가 발생했습니다.');
    } finally {
      setLoadingMessages(false);
    }
  };

  useEffect(() => {
    if (!courseId || !selectedThreadId) {
      setMessages([]);
      return;
    }
    void fetchMessages(courseId, selectedThreadId);
  }, [courseId, selectedThreadId]);

  const handleSearch = () => {
    if (!courseId) return;
    void fetchRooms(courseId, keyword);
  };

  const handleSend = async () => {
    if (!courseId || !selectedThreadId) return;
    const content = draft.trim();
    if (!content) return;

    setSending(true);
    setErrorMessage(null);
    try {
      const res = await tutorLmsApi.sendCourseChatMessage({
        courseId,
        threadId: selectedThreadId,
        content,
      });
      if (res.rst_code !== '0000') throw new Error(res.rst_message);

      setDraft('');
      await fetchMessages(courseId, selectedThreadId);
      await fetchRooms(courseId, keyword);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : '메시지 전송 중 오류가 발생했습니다.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-gray-900 mb-1">수강생 채팅</h1>
        <p className="text-gray-600">과목별 학생 문의를 스레드 형태로 조회하고 답변합니다.</p>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
        <div className="flex flex-col md:flex-row gap-3">
          <select
            value={courseId ?? ''}
            onChange={(e) => setCourseId(Number(e.target.value) || null)}
            className="md:w-72 px-3 py-2 border border-gray-300 rounded-lg"
            disabled={loadingCourses || courses.length === 0}
          >
            {courses.length === 0 && <option value="">과목 없음</option>}
            {courses.map((course) => (
              <option key={course.id} value={course.id}>
                {course.name}
              </option>
            ))}
          </select>
          <div className="flex-1 flex gap-2">
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSearch();
              }}
              placeholder="학생명/학번/제목 검색"
              className="flex-1 px-3 py-2 border border-gray-300 rounded-lg"
            />
            <button
              type="button"
              onClick={handleSearch}
              className="px-4 py-2 bg-gray-900 text-white rounded-lg hover:bg-gray-800 transition-colors"
              disabled={!courseId}
            >
              검색
            </button>
          </div>
        </div>
        {errorMessage && (
          <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
            {errorMessage}
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-4 min-h-[560px]">
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-200 text-sm text-gray-600">
            문의 스레드
          </div>
          <div className="max-h-[520px] overflow-y-auto divide-y divide-gray-100">
            {loadingRooms && (
              <div className="p-6 text-center text-gray-500 text-sm">불러오는 중...</div>
            )}
            {!loadingRooms && rooms.length === 0 && (
              <div className="p-6 text-center text-gray-500 text-sm">문의가 없습니다.</div>
            )}
            {!loadingRooms && rooms.map((room) => (
              <button
                type="button"
                key={room.thread_id}
                onClick={() => setSelectedThreadId(room.thread_id)}
                className={`w-full text-left p-4 transition-colors ${
                  selectedThreadId === room.thread_id ? 'bg-blue-50' : 'hover:bg-gray-50'
                }`}
              >
                <div className="flex items-start justify-between gap-2 mb-1">
                  <div className="font-medium text-gray-900 line-clamp-1">{room.subject || '(제목 없음)'}</div>
                  {room.waiting_answer && (
                    <span className="px-2 py-0.5 bg-orange-100 text-orange-700 text-xs rounded-full whitespace-nowrap">
                      답변대기
                    </span>
                  )}
                </div>
                <div className="text-sm text-gray-600 mb-1">
                  {room.student_user_nm || '-'} ({room.student_login_id || '-'})
                </div>
                <div className="text-xs text-gray-500 line-clamp-2">{room.preview_conv || '-'}</div>
                <div className="text-xs text-gray-400 mt-1">{room.last_reg_date_conv || room.question_reg_date_conv || '-'}</div>
              </button>
            ))}
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden flex flex-col">
          <div className="px-4 py-3 border-b border-gray-200 flex items-center gap-2">
            <MessageSquare className="w-4 h-4 text-blue-600" />
            <span className="text-sm text-gray-700">
              {selectedRoom
                ? `${selectedRoom.student_user_nm || '-'} 님과의 대화`
                : '스레드를 선택해 주세요'}
            </span>
          </div>

          <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50">
            {loadingMessages && (
              <div className="text-center text-gray-500 text-sm py-8">메시지를 불러오는 중...</div>
            )}
            {!loadingMessages && selectedThreadId && messages.length === 0 && (
              <div className="text-center text-gray-500 text-sm py-8">메시지가 없습니다.</div>
            )}
            {!loadingMessages && !selectedThreadId && (
              <div className="text-center text-gray-500 text-sm py-8">왼쪽에서 스레드를 선택해 주세요.</div>
            )}
            {!loadingMessages && messages.map((message) => {
              const mine = Boolean(message.mine) || message.sender_role === 'professor';
              return (
                <div key={message.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[80%] px-3 py-2 rounded-lg text-sm ${
                      mine
                        ? 'bg-blue-600 text-white'
                        : 'bg-white border border-gray-200 text-gray-800'
                    }`}
                  >
                    <div className={`text-[11px] mb-1 ${mine ? 'text-blue-100' : 'text-gray-500'}`}>
                      {message.writer || (mine ? '교수' : '학생')} · {message.reg_date_conv || '-'}
                    </div>
                    <div className="whitespace-pre-wrap break-words">{message.content || ''}</div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="border-t border-gray-200 p-3">
            <div className="flex gap-2">
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    if (!sending) void handleSend();
                  }
                }}
                rows={2}
                placeholder={selectedThreadId ? '답변 내용을 입력하세요. (Enter 전송 / Shift+Enter 줄바꿈)' : '스레드를 선택해 주세요.'}
                className="flex-1 px-3 py-2 border border-gray-300 rounded-lg resize-none focus:outline-none focus:ring-2 focus:ring-blue-500"
                disabled={!selectedThreadId || sending}
              />
              <button
                type="button"
                onClick={() => void handleSend()}
                disabled={!selectedThreadId || sending || !draft.trim()}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors inline-flex items-center gap-1"
              >
                <Send className="w-4 h-4" />
                전송
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

