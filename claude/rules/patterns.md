# Patterns (공통 패턴)

이 문서는 `everything-claude-code/rules/patterns.md`의 “공통 패턴을 문서로 고정”하는 아이디어를 참고했습니다.

---

## 1) GrowAILMS API 응답 패턴(필수)
이 프로젝트(JSP API)는 보통 아래 형태를 사용합니다.
- `rst_code`: 결과 코드(예: `0000`)
- `rst_message`: 메시지
- `rst_data`: 데이터(단건/배열/데이터셋 등)

### 주의(자주 터짐)
- JSP에서 `DataSet`을 JSON으로 내릴 때, **1행이어도 배열로 나오는 케이스**가 있습니다.
  - React 쪽에서는 `Array.isArray(rst_data) ? rst_data[0] : rst_data` 처럼 방어합니다.

---

## 2) 레거시 공통 필터
- `site_id` 누락 금지(멀티사이트)
- `status` 값 확인(보통 `1=정상`, `-1=삭제`)

