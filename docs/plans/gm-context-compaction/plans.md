# GM 입력 구성·대화 압축·장기 기록 계획

상위 이슈: [#342](https://github.com/omegafrog/dnd-master/issues/342)

이 문서는 승인된 작업의 이동 경로입니다. 작업 내용과 상태는 각 GitHub 이슈 및 프로젝트에서 확인합니다.

## 실행 순서

1. [#343 · 시작·일반 GM 입력을 단일 프롬프트로 구성](https://github.com/omegafrog/dnd-master/issues/343)
   - 목적: 시작 장면과 일반 GM Turn에서 최신 캐릭터 시트와 Current Situation, 현재 근거, 최근 대화를 정해진 다섯 구역의 한 프롬프트로 전달해 긴 모험에서도 필수 사실을 잃지 않도록 한다.
   - 선행 작업: 없음
2. [#344 · 전투 중 서술에 같은 GM 입력 구성 적용](https://github.com/omegafrog/dnd-master/issues/344)
   - 목적: 일반 GM Turn과 별도로 처리되는 전투 행동의 플레이어 서술에도 동일한 단일 프롬프트 규칙을 적용해 전투 전후의 이야기와 최신 판정 결과를 잇는다.
   - 선행 작업: [#343](https://github.com/omegafrog/dnd-master/issues/343)
3. [#345 · 확정 턴 이후 오래된 대화 비동기 압축](https://github.com/omegafrog/dnd-master/issues/345)
   - 목적: 오래된 대화를 확정 턴 응답 뒤 별도 작업으로 요약해 다음 요청에 제공하면서 플레이어 응답과 원문 보존을 압축 장애로부터 분리한다.
   - 선행 작업: [#343](https://github.com/omegafrog/dnd-master/issues/343)
4. [#346 · 현재 상황에 관련된 확정 사실별 장기 기록](https://github.com/omegafrog/dnd-master/issues/346)
   - 목적: 이후 플레이에 영향을 주는 확정 사건·관계·목표·위협을 출처와 공개 여부가 있는 모험별 기록으로 남겨 장소가 바뀐 뒤에도 필요한 사실을 다시 찾는다.
   - 선행 작업: [#345](https://github.com/omegafrog/dnd-master/issues/345)
5. [#347 · 긴 모험의 요약 재압축과 캐시·비용 실측](https://github.com/omegafrog/dnd-master/issues/347)
   - 목적: 요약이 누적된 긴 모험에서도 이전 상황을 이어가도록 오래된 요약을 재압축·선택하고, 단일 프롬프트 배치가 실제 입력 재사용량과 전체 비용을 개선하는지 확인한다.
   - 선행 작업: [#344](https://github.com/omegafrog/dnd-master/issues/344), [#345](https://github.com/omegafrog/dnd-master/issues/345), [#346](https://github.com/omegafrog/dnd-master/issues/346)

## 관련 명세

- [제품 명세](../../specs/gm-context-compaction/product-spec.md)
- [아키텍처 명세](../../specs/gm-context-compaction/architecture-spec.md)

## 다이어그램

- [단일 프롬프트 구성 흐름](../../../docs/specs/gm-context-compaction/diagrams/product/UC-01.activity.svg)
- [단일 프롬프트 구성 참여 관계](../../../docs/specs/gm-context-compaction/diagrams/product/UC-01.usecase.svg)
- [대화 압축 흐름](../../../docs/specs/gm-context-compaction/diagrams/product/UC-02.activity.svg)
- [대화 압축 참여 관계](../../../docs/specs/gm-context-compaction/diagrams/product/UC-02.usecase.svg)
- [입력 구성 클래스 관계](../../../docs/specs/gm-context-compaction/diagrams/architecture/adventure-runtime-context.class.svg)
- [비동기 압축 작업 상태](../../../docs/specs/gm-context-compaction/diagrams/architecture/async-conversation-compaction.state.svg)

기준 브랜치: `spec/gm-context-compaction` · 기준 커밋: `1a9d66ad22839ac62473605e48a0e5bbcf9b29be`
