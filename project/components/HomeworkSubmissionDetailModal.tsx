import { Dialog, DialogContent, DialogHeader, DialogTitle } from './ui/dialog';
import { ScrollArea } from './ui/scroll-area';

export type HomeworkSubmissionDetail = {
  submitted: boolean;
  submittedAt: string;
  subject: string;
  content: string;
  files: { id: number; filename: string; downloadUrl: string }[];
};

function toPlainText(htmlOrText: string) {
  // 왜: 학생 제출물이 HTML(<p>...</p>)로 저장되는 경우가 있어,
  //     모달에서는 태그를 그대로 보여주지 않고 "텍스트만" 보여주기 위함입니다.
  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(String(htmlOrText ?? ''), 'text/html');
    const text = doc?.body?.innerText ?? doc?.body?.textContent ?? '';
    return String(text).replace(/\u00a0/g, ' ').trim();
  } catch {
    // DOMParser가 없는 환경은 이 화면(브라우저)에서 사실상 발생하지 않습니다.
    // 그래도 비정상 데이터가 들어오면 최소한 문자열로만 표시합니다.
    return String(htmlOrText ?? '').trim();
  }
}

export function HomeworkSubmissionDetailModal(props: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  meta?: { studentName: string; studentId: string; submittedAt: string };
  loading?: boolean;
  errorMessage?: string | null;
  detail?: HomeworkSubmissionDetail | null;
}) {
  const { open, onOpenChange, title, meta, loading, errorMessage, detail } = props;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="text-balance">{title}</DialogTitle>
        </DialogHeader>

        {meta && (
          <div className="rounded-lg border border-border bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
            <div className="flex flex-wrap gap-x-4 gap-y-1">
              <div>
                <span className="text-foreground">학생:</span> {meta.studentName} ({meta.studentId})
              </div>
              <div>
                <span className="text-foreground">제출시간:</span> {meta.submittedAt}
              </div>
            </div>
          </div>
        )}

        <ScrollArea className="max-h-[60dvh] pr-3">
          <div className="space-y-6">
            {loading && (
              <div className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
                제출물을 불러오는 중입니다...
              </div>
            )}

            {!loading && errorMessage && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
                {errorMessage}
              </div>
            )}

            {!loading && !errorMessage && detail && (
              <>
                <section className="space-y-2">
                  <h4 className="text-sm font-semibold text-foreground">제출 제목</h4>
                  <div className="rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground">
                    {toPlainText(detail.subject) ? (
                      toPlainText(detail.subject)
                    ) : (
                      <span className="text-muted-foreground">없음</span>
                    )}
                  </div>
                </section>

                <section className="space-y-2">
                  <h4 className="text-sm font-semibold text-foreground">제출 내용</h4>
                  <div className="rounded-lg border border-border bg-background px-3 py-2">
                    {toPlainText(detail.content) ? (
                      <pre className="whitespace-pre-wrap break-words text-sm text-pretty text-foreground">
                        {toPlainText(detail.content)}
                      </pre>
                    ) : (
                      <div className="text-sm text-muted-foreground">없음</div>
                    )}
                  </div>
                </section>

                <section className="space-y-2">
                  <h4 className="text-sm font-semibold text-foreground">첨부파일</h4>
                  {detail.files?.length ? (
                    <div className="divide-y divide-border rounded-lg border border-border bg-background">
                      {detail.files.map((f) => (
                        <a
                          key={f.id}
                          href={f.downloadUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="block px-3 py-2 text-sm text-foreground hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                        >
                          {f.filename}
                        </a>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                      첨부파일이 없습니다.
                    </div>
                  )}
                </section>
              </>
            )}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
}
