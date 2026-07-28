# 026-4 - 문서 기반 캐릭터 생성 Playwright 회귀 검증

- Status: completed
- Issue: [#83](https://github.com/omegafrog/dnd-master/issues/83)
- Parent: [#79](https://github.com/omegafrog/dnd-master/issues/79)
- Dependencies: [026-1](026-1-character-tag-extraction.md), [026-2](026-2-storybook-character-options.md), [026-3](026-3-character-input-tree.md)

## Outcome

실제 서버와 데모 유저를 사용해 문서 선택부터 문서 근거 기반 캐릭터 생성까지 검증한다.

## Acceptance criteria

- Printer 문서는 번들에서 제외한다.
- Map 문서는 `MAP` 역할/태그로 저장한다.
- 4판/5판 룰북의 필수 태그가 실제 추출 응답에 나타난다.
- Storybook 추가 옵션과 우선순위가 실제 화면에 나타난다.
- 임의 수동 테스트값 없이 Blueprint를 게시한다.
- 문서 근거값으로 캐릭터 생성이 성공한다.
- Blueprint 화면과 생성 결과 스크린샷을 보존한다.

## Test contract

- Playwright E2E artifact: bundle snapshot, blueprint payload/snapshot, creation result, screenshot.
- 실패 시 API 응답과 source evidence를 기록한다.

## Implementation scope

Demo fixture/document setup, backend/frontend startup contract, Playwright flow and artifact handling, regression documentation. Production behavior changes belong to 026-1~026-3.
