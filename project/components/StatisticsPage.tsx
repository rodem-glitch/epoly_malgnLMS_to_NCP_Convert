import { useEffect, useRef, useState } from 'react';

// 왜: 기존 통계 대시보드(polytech-lms-api의 정적 HTML)를 교수자 LMS 안에서 바로 보이게 해야 합니다.
//     단, 화면 내부에서 `/statistics/api/*`를 호출하므로, 같은 도메인에서 프록시를 거치도록 URL을 고정합니다.
const STATISTICS_DASHBOARD_URL = '/tutor_lms/api/statistics_proxy.jsp/statistics/dashboard.html';

// 기술업종 정보 모달 컴포넌트
function TechInfoModal({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-[9999]"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-white rounded-2xl max-w-[720px] w-[90%] max-h-[85vh] overflow-y-auto shadow-2xl">
        {/* 헤더 */}
        <div className="bg-blue-600 text-white px-6 py-5 flex items-center justify-between rounded-t-2xl">
          <div>
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" viewBox="0 0 16 16">
                <path d="M5 0a.5.5 0 0 1 .5.5V2h1V.5a.5.5 0 0 1 1 0V2h1V.5a.5.5 0 0 1 1 0V2h1V.5a.5.5 0 0 1 1 0V2A2.5 2.5 0 0 1 14 4.5h1.5a.5.5 0 0 1 0 1H14v1h1.5a.5.5 0 0 1 0 1H14v1h1.5a.5.5 0 0 1 0 1H14v1h1.5a.5.5 0 0 1 0 1H14a2.5 2.5 0 0 1-2.5 2.5v1.5a.5.5 0 0 1-1 0V14h-1v1.5a.5.5 0 0 1-1 0V14h-1v1.5a.5.5 0 0 1-1 0V14h-1v1.5a.5.5 0 0 1-1 0V14A2.5 2.5 0 0 1 2 11.5H.5a.5.5 0 0 1 0-1H2v-1H.5a.5.5 0 0 1 0-1H2v-1H.5a.5.5 0 0 1 0-1H2v-1H.5a.5.5 0 0 1 0-1H2A2.5 2.5 0 0 1 4.5 2V.5A.5.5 0 0 1 5 0zm-.5 3A1.5 1.5 0 0 0 3 4.5v7A1.5 1.5 0 0 0 4.5 13h7a1.5 1.5 0 0 0 1.5-1.5v-7A1.5 1.5 0 0 0 11.5 3h-7zM5 6.5A1.5 1.5 0 0 1 6.5 5h3A1.5 1.5 0 0 1 11 6.5v3A1.5 1.5 0 0 1 9.5 11h-3A1.5 1.5 0 0 1 5 9.5v-3zM6.5 6a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5h-3z"/>
              </svg>
              기술업종
            </h2>
            <p className="text-sm opacity-90 mt-1">신기술을 기반으로 수익을 창출하는 기술집약적 업종</p>
          </div>
          <button
            onClick={onClose}
            className="bg-white/20 hover:bg-white/30 rounded-full w-9 h-9 flex items-center justify-center transition-colors"
            aria-label="닫기"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16">
              <path d="M2.146 2.854a.5.5 0 1 1 .708-.708L8 7.293l5.146-5.147a.5.5 0 0 1 .708.708L8.707 8l5.147 5.146a.5.5 0 0 1-.708.708L8 8.707l-5.146 5.147a.5.5 0 0 1-.708-.708L7.293 8 2.146 2.854Z"/>
            </svg>
          </button>
        </div>

        {/* 본문 */}
        <div className="p-6">
          {/* 기술혁신정도 */}
          <div className="mb-6">
            <div className="flex items-center gap-2 text-base font-semibold text-gray-800 mb-2 pb-2 border-b-2 border-blue-600">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" className="text-blue-600" viewBox="0 0 16 16">
                <path d="M9.405 1.05c-.413-1.4-2.397-1.4-2.81 0l-.1.34a1.464 1.464 0 0 1-2.105.872l-.31-.17c-1.283-.698-2.686.705-1.987 1.987l.169.311c.446.82.023 1.841-.872 2.105l-.34.1c-1.4.413-1.4 2.397 0 2.81l.34.1a1.464 1.464 0 0 1 .872 2.105l-.17.31c-.698 1.283.705 2.686 1.987 1.987l.311-.169a1.464 1.464 0 0 1 2.105.872l.1.34c.413 1.4 2.397 1.4 2.81 0l.1-.34a1.464 1.464 0 0 1 2.105-.872l.31.17c1.283.698 2.686-.705 1.987-1.987l-.169-.311a1.464 1.464 0 0 1 .872-2.105l.34-.1c1.4-.413 1.4-2.397 0-2.81l-.34-.1a1.464 1.464 0 0 1-.872-2.105l.17-.31c.698-1.283-.705-2.686-1.987-1.987l-.311.169a1.464 1.464 0 0 1-2.105-.872l-.1-.34zM8 10.93a2.929 2.929 0 1 1 0-5.86 2.929 2.929 0 0 1 0 5.858z"/>
              </svg>
              기술혁신정도
            </div>
            <p className="text-sm text-gray-600 mb-4 leading-relaxed">
              연구개발집약도에 따라 제조업을 첨단기술, 고기술, 중기술, 저기술 등으로 분류하는 산업
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">🚀 첨단기술업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">기술집약도가 높고 제품의 수명주기가 짧으며 경제적 파급효과가 큰 산업</div>
              </div>
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">🔧 고기술업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">진보되고 정교한 기술을 포함하는 산업</div>
              </div>
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">🔩 중기술업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">기술이전이 용이한 성숙기술을 포함하는 산업</div>
              </div>
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">🔨 저기술업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">사회의 다양한 세부 분야에 침투되어 낮은 수준의 공업기술 산업</div>
              </div>
            </div>
          </div>

          {/* 지식집약정도 */}
          <div className="mb-4">
            <div className="flex items-center gap-2 text-base font-semibold text-gray-800 mb-2 pb-2 border-b-2 border-blue-600">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" className="text-blue-600" viewBox="0 0 16 16">
                <path d="M2 6a6 6 0 1 1 10.174 4.31c-.203.196-.359.4-.453.619l-.762 1.769A.5.5 0 0 1 10.5 13a.5.5 0 0 1 0 1 .5.5 0 0 1 0 1l-.224.447a1 1 0 0 1-.894.553H6.618a1 1 0 0 1-.894-.553L5.5 15a.5.5 0 0 1 0-1 .5.5 0 0 1 0-1 .5.5 0 0 1-.46-.302l-.761-1.77a1.964 1.964 0 0 0-.453-.618A5.984 5.984 0 0 1 2 6zm6-5a5 5 0 0 0-3.479 8.592c.263.254.514.564.676.941L5.83 12h4.342l.632-1.467c.162-.377.413-.687.676-.941A5 5 0 0 0 8 1z"/>
              </svg>
              지식집약정도
            </div>
            <p className="text-sm text-gray-600 mb-4 leading-relaxed">
              기술, 정보, 지식 등 무형자산의 활용도가 높은 정보 통신업, 금융 및 보험업, 사업서비스업 등으로 분류하는 산업
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">🎨 창의 및 디지털 업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">인간의 감성, 창의력, 상상력을 기반으로 경제적 가치를 창출하는 산업</div>
              </div>
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">💻 ICT업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">정보통신기술을 기반으로 하는 전기통신, 컴퓨터 프로그램 등을 포함하는 정보서비스 산업</div>
              </div>
              <div className="bg-gray-50 rounded-lg p-4 border border-gray-200">
                <div className="text-sm font-semibold text-blue-600 mb-1 flex items-center gap-1">💼 전문서비스업종</div>
                <div className="text-xs text-gray-600 leading-relaxed">전문지식을 갖춘 인력자원이 주요 요소로 투입되는 산업</div>
              </div>
            </div>
          </div>

          {/* 출처 */}
          <div className="mt-4 pt-4 border-t border-gray-200 text-xs text-gray-400 text-right">
            ℹ️ 출처: SGIS 통계지리정보서비스
          </div>
        </div>
      </div>
    </div>
  );
}

// 왜: 통계 대시보드를 전체 페이지 스크롤로 표시하기 위해 iframe 높이를 콘텐츠에 맞춰 동적으로 조정합니다.
//     고정 높이 컨테이너를 사용하면 이중 스크롤이 발생하므로, 콘텐츠 크기에 맞춰 확장합니다.
export function StatisticsPage() {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [iframeHeight, setIframeHeight] = useState(800); // 초기 높이
  const [isTechInfoModalOpen, setIsTechInfoModalOpen] = useState(false);

  useEffect(() => {
    const iframe = iframeRef.current;
    if (!iframe) return;

    const adjustHeight = () => {
      try {
        const doc = iframe.contentDocument || iframe.contentWindow?.document;
        if (doc && doc.body) {
          // 왜: scrollHeight로 전체 콘텐츠 높이를 측정해 iframe 높이를 콘텐츠에 맞춥니다.
          //     (주의) 반복 측정 중에 여유값(+px)을 더하면 iframe 높이가 계속 커질 수 있어, 계산값 그대로 사용합니다.
          const contentHeight = Math.max(doc.documentElement.scrollHeight, doc.body.scrollHeight);
          setIframeHeight(contentHeight);
        }
      } catch (e) {
        // 왜: cross-origin 제한이 있을 경우 기본 높이를 충분히 크게 설정합니다.
        setIframeHeight(1600);
      }
    };

    iframe.addEventListener('load', adjustHeight);
    // 왜: 탭 전환 등으로 콘텐츠가 변경될 때도 높이를 재조정합니다.
    const resizeTimer = setInterval(adjustHeight, 500);

    return () => {
      iframe.removeEventListener('load', adjustHeight);
      clearInterval(resizeTimer);
    };
  }, []);

  // 왜: iframe 내부에서 postMessage로 모달 열기 요청을 보내면 부모(React)에서 모달을 표시합니다.
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.data && event.data.type === 'openTechInfoModal') {
        setIsTechInfoModalOpen(true);
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, []);

  return (
    <div className="overflow-visible">
      <iframe
        ref={iframeRef}
        src={STATISTICS_DASHBOARD_URL}
        title="통계 대시보드"
        className="w-full border-0"
        style={{ height: `${iframeHeight}px`, minHeight: '600px' }}
      />
      <TechInfoModal isOpen={isTechInfoModalOpen} onClose={() => setIsTechInfoModalOpen(false)} />
    </div>
  );
}
