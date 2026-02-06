# Git Workflow - 커밋/정리 규칙

이 문서는 `everything-claude-code/rules/git-workflow.md`의 “작은 단위로 계획→리뷰→커밋” 흐름을 참고해,
GrowAILMS에 맞게 정리한 것입니다.

---

## 1) 커밋 메시지 형식(권장)
```
<type>(scope): <설명>

- 변경 요약 1
- 변경 요약 2
```

권장 type: `feat`, `fix`, `refactor`, `docs`, `chore`, `perf`, `test`

예:
- `fix(haksa): 학사 강의목차 인정시간/진도 표시 보정`
- `docs(claude): 에이전트 강제 규칙 세트 추가`

---

## 2) 커밋 단위(권장)
- 기능 수정과 문서/설정은 가능하면 커밋을 분리합니다.
  - 예) 1) 기능 fix, 2) docs(claude) 규칙 세트
- “의도하지 않은 변경”이 섞이지 않게 `git diff`로 확인 후 커밋합니다.

---

## 3) 이 저장소 전용(강제)
- `project/`(React) 코드를 변경했으면 반드시:
  - `cd project && npm run build`
  - 산출물(`public_html/tutor_lms/app`) 변경이 포함되도록 커밋합니다.

