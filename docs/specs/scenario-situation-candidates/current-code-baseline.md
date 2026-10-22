# 상황 내용 개선의 현재 코드 기준

확인 기준은 2026-10-21의 `8dcf12443bd571e8f4ab61b9a4509618b7d259b5`와 그 위의 현재 작업 트리다. 작업 트리에는 이전 작업의 미커밋 변경이 있으므로 아래 내용은 해당 커밋만의 동작을 보증하지 않는다. 읽기 전용 코드 조사 결과이며 앱 실행·테스트 통과 기록이 아니다.

| 확인 대상 | 관찰한 현재 구현 | 이번 개선에서 남은 일 |
|---|---|---|
| 상황 정의 | [SituationDefinition.java](../../../src/adventure-service/src/main/java/com/dndmaster/adventure/domain/scenario/SituationDefinition.java)는 이미 `title`·`description`을 필수로 검증하며 기존 의도·출처·필수 여부·선행 관계·지도 필요 여부도 보존한다. | 실제 생성 입력·저장 왕복과 표현 품질을 검증한다. 이 필드는 새로 추가하는 것으로 취급하지 않는다. |
| 시작 전 자료 생성 | [StageArtifactPreparationApplicationService.java](../../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/scenario/preparation/StageArtifactPreparationApplicationService.java)의 `prepare`는 골격의 모든 단계 요청을 만들고 `generateAll`·단계별 검증·`validateInitialSituationSet`을 거친 뒤 `saveInitialArtifacts`를 호출한다. | 전체 준비 생명주기는 이미 존재한다. 이 범위에서는 전 단계 상황 관계가 단계 배열 순서에 묶이지 않도록 검증·런타임 선택을 바꾼다. |
| 생성 요청과 표현 | [ScenarioPackageStageArtifactAdapter.java](../../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/scenario/preparation/ScenarioPackageStageArtifactAdapter.java)는 `title`·`description`을 구조화해 만들며 프롬프트는 구체 사건 이름과 0개·1개·여러 중간 후보를 허용한다. `StageDetailedGenerationPort.Request`는 단계 맥락과 제한된 RAG 발췌를 직접 받지 않는다. | 검토된 RAG 발췌와 단계 맥락을 생성 요청에 전달하고, `title`·`description`의 의미 적합성을 실제 모델 결과에서 확인한다. |
| 필수 상황 검증 | `validatePreparedStage`는 해당 단계의 필수 상황·출처·opening을 검사한다. `validateInitialSituationSet`은 전체 ID와 관계를 검사하지만, 필수 선행 상황의 단계 순서가 뒤처지지 않게 제한한다. 두 검사에 중간 후보 최소 개수 검사는 없다. | 필수 관계를 전체 모험 범위에서만 검증하고 단계 번호로 플레이 순서를 강제하지 않는다. |
| 저장 | [PostgresStageArtifactRepository.java](../../../src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/persistence/PostgresStageArtifactRepository.java)는 `DetailedStage` JSON을 저장·복원하고 모험 범위의 키를 사용한다. | 기존 JSONB 저장 경계를 유지한다. 신규 테이블·마이그레이션은 추가하지 않는다. |
| 런타임 문맥 | [RuntimeTurnApplicationService.java](../../../src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurnApplicationService.java)의 `situationSelectionContext`는 현재 단계에서 선택 가능한 후보의 ID·이름·설명·의도·상태를 전달한다. `StoryRuntimeState`는 단일 단계의 상황 상태를 갖는다. | 모든 단계 후보를 한 모험의 이야기 진행 상태에서 고르고, 단계별 진행 자료를 보존하며, 현재 장면과 활성 준비 상황의 차이를 프롬프트에 분명히 전달한다. |

실제 실행·테스트 성공은 확인하지 않았다. 독립 리뷰에 따라 이 기준은 검토된 소스의 관찰이며 런타임 구현 완료 주장과 구별한다.
