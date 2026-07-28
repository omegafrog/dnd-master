# 028-4 - 캐릭터 컨텍스트 최종 E2E

- Status: approved
- Issue: [#93](https://github.com/omegafrog/dnd-master/issues/93)
- Parent: 028
- Dependencies: 028-2, 028-3

## Outcome

실제 indexed RULEBOOK과 STORYBOOK으로 컴파일하고 Blueprint를 검토·게시한 뒤 캐릭터 시트를 생성한다.

## Scope

- API/system fixture: indexed RULEBOOK + STORYBOOK bundle.
- 종족·클래스·배경 등 선택지 검색 및 Blueprint 반영 검증.
- manual/fixed/conflict review 후 publish 검증.
- published Blueprint 기반 character sheet 생성 검증.
- 개발용 Evidence 표시와 정식 화면 숨김 가능성 검증.

## Acceptance

- 실제 RULEBOOK에서 종족 선택지가 자동 추출된다.
- STORYBOOK 결과가 많아도 RULEBOOK 결과가 사라지지 않는다.
- 문서 유형 하나가 비어도 나머지 검색과 컴파일이 계속된다.
- review/publish 후 생성된 character sheet가 Blueprint 값을 사용한다.

## Tests

- system/API integration and regression tests.
- `ui ~ entity` Playwright: upload/indexed documents → bundle → compile → review → publish → character sheet.

## Implementation scope

`system-tests`, `adventure-service` API fixtures, `web-ui` scenario setup/character creation flow, CI test wiring.
