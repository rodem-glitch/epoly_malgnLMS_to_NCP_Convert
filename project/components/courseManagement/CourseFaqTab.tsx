import React, { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, HelpCircle, Search } from 'lucide-react';
import { tutorLmsApi } from '../../api/tutorLmsApi';

interface FaqViewItem {
  id: string;
  question: string;
  answer: string;
  category: string;
}

interface CourseFaqTabProps {
  courseId: number;
  course?: any;
}

export function CourseFaqTab({ courseId }: CourseFaqTabProps) {
  const [faqs, setFaqs] = useState<FaqViewItem[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterCategory, setFilterCategory] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!courseId) {
      setFaqs([]);
      return;
    }
    setLoading(true);
    void (async () => {
      try {
        const res = await tutorLmsApi.getFaqNotices({ courseId });
        if (res.rst_code !== '0000') throw new Error(res.rst_message);
        const rows = Array.isArray(res.rst_data) ? res.rst_data : [];
        setFaqs(
          rows.map((row: any) => ({
            id: String(row.faq_id),
            question: String(row.question ?? ''),
            answer: String(row.answer ?? ''),
            category: '일반',
          }))
        );
      } catch {
        setFaqs([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [courseId]);

  const categories = useMemo(() => [...new Set(faqs.map((f) => f.category))], [faqs]);

  const filteredFaqs = faqs.filter((faq) => {
    if (filterCategory && faq.category !== filterCategory) return false;
    if (searchQuery) {
      const kw = searchQuery.toLowerCase();
      if (!faq.question.toLowerCase().includes(kw) && !faq.answer.toLowerCase().includes(kw)) return false;
    }
    return true;
  });

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
          <HelpCircle className="w-5 h-5 text-blue-500" />
          자주 묻는 질문 (FAQ)
        </h2>
        <p className="text-gray-500 text-sm mt-0.5">궁금한 사항이 있으면 먼저 아래 FAQ를 확인해 주세요.</p>
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex-1 min-w-[200px]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="FAQ 검색..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
        {categories.length > 1 && (
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">전체 카테고리</option>
            {categories.map((cat) => (
              <option key={cat} value={cat}>{cat}</option>
            ))}
          </select>
        )}
      </div>

      {loading ? (
        <div className="bg-gray-50 rounded-xl border-2 border-dashed border-gray-300 p-10 text-center text-gray-500 text-sm">
          FAQ를 불러오는 중입니다.
        </div>
      ) : filteredFaqs.length === 0 ? (
        <div className="bg-gray-50 rounded-xl border-2 border-dashed border-gray-300 p-10 text-center">
          <HelpCircle className="w-8 h-8 mx-auto mb-2 text-gray-300" />
          <p className="text-gray-400 text-sm">{searchQuery ? '검색 결과가 없습니다.' : '등록된 FAQ가 없습니다.'}</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filteredFaqs.map((faq, idx) => {
            const isExpanded = expandedId === faq.id;
            return (
              <div
                key={faq.id}
                className={`bg-white rounded-lg border transition-all ${
                  isExpanded ? 'border-blue-300 shadow-sm' : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <button
                  className="w-full flex items-center gap-3 px-4 py-3 text-left"
                  onClick={() => setExpandedId(isExpanded ? null : faq.id)}
                >
                  <span className="flex-shrink-0 w-7 h-7 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center text-xs font-bold">
                    Q{idx + 1}
                  </span>
                  {isExpanded ? (
                    <ChevronDown className="w-4 h-4 text-gray-400 flex-shrink-0" />
                  ) : (
                    <ChevronRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                  )}
                  <span className="flex-1 text-sm font-medium text-gray-900">{faq.question}</span>
                  <span className="px-2 py-0.5 bg-blue-50 text-blue-600 text-xs rounded-full flex-shrink-0">
                    {faq.category}
                  </span>
                </button>
                {isExpanded && (
                  <div className="px-4 pb-4">
                    <div className="ml-10 bg-blue-50 rounded-lg p-4">
                      <div className="flex items-start gap-2">
                        <span className="flex-shrink-0 w-6 h-6 rounded-full bg-blue-500 text-white flex items-center justify-center text-xs font-bold mt-0.5">
                          A
                        </span>
                        <p className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">{faq.answer}</p>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
