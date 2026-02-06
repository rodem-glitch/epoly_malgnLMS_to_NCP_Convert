# claude/ (에이전트 강제 규칙 세트)

왜 이 폴더가 필요한가?
- 에이전트가 “항상 같은 방식”으로 작업하도록 **강제**하기 위한 문서 모음입니다.
- 실수(문서 삭제, 검증 누락, 과한 리팩터링 등)를 줄이기 위해 체크리스트를 고정합니다.

이 폴더는 어떤 아이디어를 참고했나요?
- 구조 아이디어는 `https://github.com/affaan-m/everything-claude-code` 형태(agents/commands/rules/skills)를 참고했습니다.
- 또한 그 저장소의 **핵심 내용(보안/깃/테스트/품질/훅/에이전트 운영 철학)**도 참고해,
  GrowAILMS에 맞게 **한글로 요약·번역하여** 이 폴더에 녹였습니다.
- 단, 이 저장소(GrowAILMS) 특성에 맞게 **필요한 만큼만** 적용합니다(오버엔지니어링 금지).

---

## 폴더 구조
- `claude/rules/` : 항상 지켜야 하는 규칙(강제)
- `claude/commands/` : 작업 유형별 실행 절차(가이드)
- `claude/skills/` : GrowAILMS 도메인 지식/패턴(가이드)

---

## 에이전트 사용 규칙(강제)
1) 작업 시작 전에 `claude/rules/00-core.md`를 먼저 확인합니다.
2) 작업 유형이 뚜렷하면 `claude/commands/`에서 해당 문서를 찾아 그 순서대로 진행합니다.
3) GrowAILMS 특이사항이 필요하면 `claude/skills/` 문서를 참고합니다.

> 중요: 에이전트는 이 폴더/문서를 임의로 삭제/정리하면 안 됩니다.

---

## [강제] 규칙 문서 목록
- 필수 체크리스트: `claude/rules/00-core.md`
- 보안: `claude/rules/security.md`
- Git/커밋: `claude/rules/git-workflow.md`
- 테스트/검증: `claude/rules/testing.md`
- 코드 스타일: `claude/rules/coding-style.md`
- 성능/작업 효율(컨텍스트/도구 사용): `claude/rules/performance.md`
- 공통 패턴(API 응답 등): `claude/rules/patterns.md`
- 에이전트 운영(계획/리뷰): `claude/rules/agents.md`
- 훅(선택, 개념/예시): `claude/rules/hooks.md`
