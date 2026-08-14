# 036-2 캐릭터 설정 검토 화면 재구성

Issue: [#163](https://github.com/omegafrog/dnd-master/issues/163)
Parent: [#161](https://github.com/omegafrog/dnd-master/issues/161)
Status: `planned`
Dependencies: 036-1

## 구현 목적

사용자가 룰북 기본 내용, 스토리북 제안, 적용된 설정을 한눈에 구분하고 검토할 수 있도록 원시 입력 트리를 읽기 쉬운 검토 화면으로 재구성한다.

## 구현 범위

- `PackageBlueprintReviewPage`의 단계·상태·다음 행동 UI 재구성
- `StorybookProposalList`와 출처별 제안 카드 추가
- 기본 내용, 제안 목록, 적용된 설정 요약을 별도 영역으로 구성
- UUID, enum, `CharacterCreationBlueprint`, 원시 진단 문자열 제거
- 제안 없음·추출 실패·근거 부족·로딩 상태 UI
- 섹션 요약, 완료 상태, 좁은 화면 대응

## 의존성과 변경 경계

- 036-1의 proposal read model을 사용한다.
- 적용/제외 상태 저장은 036-3에서 연결한다.
- 게시 조건과 세션 생성은 036-4에서 연결한다.

## 테스트 계약

- `StorybookProposalList`와 review view-model 단위 테스트
- `PackageBlueprintReviewPage`의 각 상태 렌더링 테스트
- 준비 API entity fixture를 사용하는 UI~entity E2E
- 390px 폭에서 세 영역과 주요 행동이 사용 가능한지 검증

## 완료 조건

- 룰북 기본 내용과 스토리북 제안이 화면에서 섞이지 않는다.
- 각 제안에 출처와 근거 영역이 보인다.
- 제안 없음/실패 상태가 빈 화면이나 원시 오류로 나타나지 않는다.
- 게시와 캐릭터 생성 버튼은 후속 티켓의 상태 계약을 사용할 수 있는 구조다.
