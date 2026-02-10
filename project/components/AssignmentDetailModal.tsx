import { X, Edit, Calendar, Award, Users, FileText, Clock } from 'lucide-react';

interface AssignmentDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  onEdit: () => void;
  assignment: {
    id: number;
    title: string;
    description?: string;
    startDate?: string;
    dueDate?: string;
    totalScore?: number;
    submitted?: number;
    total?: number;
  } | null;
}

/**
 * 과제 상세 보기 모달
 * 과제 카드 클릭 시 과제 정보를 표시하고, 수정 버튼을 통해 수정 모달로 전환할 수 있습니다.
 */
export function AssignmentDetailModal({
  isOpen,
  onClose,
  onEdit,
  assignment,
}: AssignmentDetailModalProps) {
  if (!isOpen || !assignment) return null;

  const handleEditClick = () => {
    onClose();
    onEdit();
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg w-full max-w-lg overflow-hidden shadow-xl">
        {/* 헤더 */}
        <div className="bg-purple-600 px-6 py-4 flex items-center justify-between">
          <h3 className="text-lg font-medium text-white">과제 상세 정보</h3>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 본문 */}
        <div className="p-6 space-y-5">
          {/* 과제 제목 */}
          <div>
            <div className="flex items-center gap-2 text-gray-500 text-sm mb-1">
              <FileText className="w-4 h-4" />
              <span>과제명</span>
            </div>
            <p className="text-lg font-medium text-gray-900">{assignment.title}</p>
          </div>

          {/* 설명 */}
          {assignment.description && (
            <div>
              <div className="flex items-center gap-2 text-gray-500 text-sm mb-1">
                <FileText className="w-4 h-4" />
                <span>과제 설명</span>
              </div>
              <p className="text-gray-700 whitespace-pre-wrap bg-gray-50 p-3 rounded-lg">
                {assignment.description}
              </p>
            </div>
          )}

          {/* 정보 그리드 */}
          <div className="grid grid-cols-2 gap-4">
            {/* 시작일 */}
            <div className="bg-gray-50 p-4 rounded-lg">
              <div className="flex items-center gap-2 text-gray-500 text-sm mb-1">
                <Clock className="w-4 h-4" />
                <span>제출 시작일</span>
              </div>
              <p className="text-gray-900 font-medium">{assignment.startDate || '-'}</p>
            </div>

            {/* 마감일 */}
            <div className="bg-gray-50 p-4 rounded-lg">
              <div className="flex items-center gap-2 text-gray-500 text-sm mb-1">
                <Calendar className="w-4 h-4" />
                <span>마감일</span>
              </div>
              <p className="text-gray-900 font-medium">{assignment.dueDate || '-'}</p>
            </div>

            {/* 배점 */}
            <div className="bg-gray-50 p-4 rounded-lg">
              <div className="flex items-center gap-2 text-gray-500 text-sm mb-1">
                <Award className="w-4 h-4" />
                <span>배점</span>
              </div>
              <p className="text-gray-900 font-medium">{assignment.totalScore || 0}점</p>
            </div>

            {/* 제출 현황 */}
            <div className="bg-gray-50 p-4 rounded-lg col-span-2">
              <div className="flex items-center gap-2 text-gray-500 text-sm mb-2">
                <Users className="w-4 h-4" />
                <span>제출 현황</span>
              </div>
              <div className="flex items-center gap-3">
                <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-purple-600 rounded-full"
                    style={{
                      width: assignment.total && assignment.total > 0
                        ? `${((assignment.submitted || 0) / assignment.total) * 100}%`
                        : '0%',
                    }}
                  />
                </div>
                <span className="text-gray-900 font-medium whitespace-nowrap">
                  {assignment.submitted || 0} / {assignment.total || 0}명
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 하단 버튼 */}
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-100 transition-colors"
          >
            닫기
          </button>
          <button
            onClick={handleEditClick}
            className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center justify-center gap-2"
          >
            <Edit className="w-4 h-4" />
            <span>수정</span>
          </button>
        </div>
      </div>
    </div>
  );
}
