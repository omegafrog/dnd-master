# 2026-10-13 오프라인 전처리 이슈와 경로 누락 진단

## 실행 기준

- 코드: `8ef50e1b0e939104445c4f7a73442cb42e3fdada`
- 입력 PDF 해시, 정책, 정답 초안 및 평가기 정보: [기준 실행 기록](/home/jiwoo/workspace/dnd-master/docs/asset/preprocessing-eval/runs/2026-10-13-structural-baseline-8ef50e1/pilot-evaluation/offline-baseline-run.json)
- 전체 실행 결과와 256개 경로 누락 청크의 상세 진단: `/home/jiwoo/workspace/dnd-master/docs/asset/preprocessing-eval/runs/2026-10-13-structural-baseline-8ef50e1/`
- 정답 라벨은 사용자 검수 전 초안이다.

## 검증 이슈 집계

| 문서 | 이슈 기록 | 고유 청크 | 경고 | 게시 차단 오류 |
|---|---:|---:|---:|---:|
| Basic Roleplaying | 683 | 579 | 588 | 95 |
| Cairn Player | 343 | 232 | 274 | 69 |
| Cairn Warden | 548 | 390 | 455 | 93 |
| 합계 | 1,574 | 1,201 | 1,317 | 257 |

이슈 기록은 독립 결함 수가 아니다. 같은 청크에 여러 이슈가 붙을 수 있다. 경고는 998개 청크, 오류는 257개 청크에 영향을 주고, 그중 54개 청크는 양쪽 이슈를 모두 가진다.

게시 차단 오류 257건 중 256건은 `missing_section_path`(청크의 의미상 소속 절을 확인할 수 없음), 1건은 `orphan_heading`(짧은 표 청크를 제목 고아로 분류)이다. `too_small_chunk` 845건과 `broken_sentence` 472건은 게시를 막지 않는 경고다.

## 216개 `content_type=table` 경로 오류의 실제 구성

`content_type=table`은 실제로 복원된 표라는 뜻이 아니다. 페이지의 표 구조 객체, 청크의 표 셀 출처와 문서 트리를 함께 대조하면 다음과 같다.

| 문서 | 한 표·한 페이지의 셀만 담은 청크 | 여러 표 또는 여러 페이지가 섞인 청크 | 실제 표 구조 출처가 없는 청크 |
|---|---:|---:|---:|
| Basic Roleplaying | 0 | 0 | 95 |
| Cairn Player | 48 | 21 | 0 |
| Cairn Warden | 52 | 0 | 0 |
| 합계 | 100 | 21 | 95 |

Basic Roleplaying의 95개는 실제 표 구조가 없는 루트 문서 내용이다. 그중 84개 청크가 여러 페이지에 걸쳐 있다. 파이프라인은 문서 트리의 루트에도 전체 내용을 대상으로 콘텐츠 유형을 판정해 `table`을 부여한다. 루트 자체가 개별 표를 뜻하지 않으므로 이 95건은 표 경로 문제가 아니라 전체 문서의 유형 판정과 루트 내용 묶음 문제다.

Cairn Player의 48개와 Cairn Warden의 52개는 각각 하나의 표 셀 출처를 가진 실제 표 청크다. 이 청크들은 표 ID와 셀 출처를 보존하지만, 의미상 상위 절은 루트 또는 계층 미확정 제목 아래에 있어 `section_path`가 비어 있다. 출처를 잃은 것이 확인된 것은 아니며, 상위 제목 관계가 확정되지 않은 상태다.

Player의 나머지 21개는 모두 여러 페이지 출처가 섞였다. 이들은 단일 표가 아니라 루트 문서 내용에서 나온 혼합 청크다. 따라서 216건을 모두 실제 표 216개의 경로 문제로 취급하면 원인을 오진한다.

## 별도 확인된 표 셀의 문서 트리 누락

청크의 모든 출처 범위는 원본 페이지의 블록으로 연결되고 `invalid_source_span` 오류도 없다. 다만 원본 소유 정보가 문서 트리에서 완전히 보존되지는 않는다.

- Cairn Player: 경로 오류 청크가 참조하지만 문서 트리에는 없는 표 셀 블록 12개. 대부분 반복되는 `d6` 머리글이다.
- Cairn Warden: 같은 조건의 표 셀 블록 71개. `d20`, `d100`, `d6`, `Description`, `Spellbook` 머리글이 포함된다.

코드상 원인은 반복 장식물 제거다. 반복 여부를 페이지 가장자리에서 판단한 뒤, 같은 정규화 문자열을 가진 블록을 위치 확인 없이 문서 전체에서 제외한다. 숫자를 제거해 정규화하므로 `d6`, `d20`, `d100`이 같은 키가 될 수 있다. 또한 표 셀은 글꼴 크기가 없어서 기본값 0으로 처리되므로 가장자리 반복 후보에 포함될 수 있다. 관련 코드는 [tree_builder.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/structure/tree_builder.py:58)와 [tree_builder.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/structure/tree_builder.py:119)에 있다.

## 혼합 청크가 생기는 코드 경로

`_classify_tree()`는 루트의 모든 원문 구간을 하나로 합쳐 콘텐츠 유형을 정한다. Basic Roleplaying과 Cairn Player의 루트가 `table`, Cairn Warden의 루트가 `rule`로 저장된 것이 확인됐다. 분류기는 입력 내용 어디든 표 모양이 있다고 판단하면 전체 노드에 `table`을 부여한다. 관련 코드는 [pipeline.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/pipeline/pipeline.py:95), [classifier.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/classification/classifier.py:33)에 있다.

`ChunkPlanner`는 일반 내용 후보를 만들 때 `node.source_segments`가 있으면 표 셀 블록을 제외하지 않고 그대로 사용한다. 표 셀 제외는 `node.block_ids`에서 원문 구간을 재구성하는 대체 경로에만 적용된다. 그래서 루트 청크에 여러 페이지의 본문과 표 출처가 함께 들어갈 수 있다. 관련 코드는 [planner.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/chunking/planner.py:66)에 있다.

제목 계층을 미확정으로 둔 경우 `DocumentTreeBuilder`가 해당 제목을 루트에 보존하고 후속 내용의 상위 경로를 확정하지 않는다. 이 원칙 자체는 근거 없는 `section_path`를 만들지 않기 위한 안전 동작이다. 다만 모든 루트 내용을 한 후보로 분류·청킹하는 동작, 표 셀 구간을 일반 후보에서 제외하지 않는 동작은 별도로 바로잡아야 한다. 관련 코드는 [tree_builder.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/structure/tree_builder.py:73)와 [planner.py](/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range/src/preprocessing_agent/chunking/planner.py:67)에 있다.

## Cairn Warden 규칙 청크 40개

Warden의 규칙 청크 40개도 모두 문서 트리 루트 소유이며 확정된 제목 경로가 없다. 39개는 여러 페이지 출처를 포함한다. 이 중 25개는 표 셀 출처가 없고, 15개는 루트 구간에 표 셀 출처가 함께 들어간다. 따라서 규칙 청크 40건을 표 경로 오류와 같은 수정으로 처리할 수 없다. 현재 증거는 상위 제목 관계 미확정과 루트 내용 과다 묶음을 가리킨다.

## 수정 원칙과 다음 순서

1. 표 청크를 임의의 제목에 연결하지 않는다. 한 표의 원본 위치와 셀 관계는 현재 표 ID·셀 출처로 보존하고, 의미상 상위 절은 근거가 있을 때만 확정한다.
2. 반복 장식물 제거가 실제 표 셀을 버리지 않도록 고친다. 표 셀은 출처 보존 대상이며, 반복 문자열만으로 제외하지 않는다.
3. 일반 내용 후보에서 표 셀의 원문 구간을 제외한다. `source_segments` 경로와 블록 재구성 경로가 같은 소유 경계를 지키도록 한다.
4. 루트 전체 문서에 개별 절의 콘텐츠 유형을 부여하지 않는다. 확정된 절 단위에서 유형을 판단하고, 불확실한 루트 내용은 그 상태를 보존한다.
5. 그 뒤에도 상위 절 근거가 없는 100개 단일 표 청크와 Warden 규칙 청크는 미확정으로 남겨 검토한다. 검증을 느슨하게 해서 게시만 통과시키지 않는다.
6. 수정 전후에는 동일한 고정 기준 자료로 표 셀 보존, 중복·누락, 페이지 범위, 콘텐츠 유형, 경로 판정률을 함께 비교한다.

상세한 청크별 구간, 표 ID, 문서 트리 소유 상태는 보존 자료의 `section-path-root-cause.json`에 있다. 표 탐지 원시 결과와 행 수 비교는 같은 폴더의 `table-stage-diagnostics.json`에 있다.

## 수정 후 재실행

기준선과 같은 세 PDF를 현재 코드로 순차 전처리했다. 입력 PDF 해시는 모두 기준선과 일치한다. 실행 기록과 원시 산출물은 [최종 비교 실행](/home/jiwoo/workspace/dnd-master/docs/asset/preprocessing-eval/runs/2026-10-13-root-path-fixes-final/)에 보존했다. 정답 라벨은 아직 검수 전 초안이다.

| 문서 | 전체 이슈 | 경로 누락 | `content_type=table` 경로 누락 | 최종 청크 |
|---|---:|---:|---:|---:|
| Basic Roleplaying | 683 → 622 | 95 → 71 | 95 → 0 | 1,161 → 1,151 |
| Cairn Player | 343 → 300 | 69 → 41 | 69 → 36 | 249 → 233 |
| Cairn Warden | 548 → 510 | 92 → 51 | 52 → 25 | 416 → 405 |
| 합계 | 1,574 → 1,432 | 256 → 163 | 216 → 61 | 1,826 → 1,789 |

게시 차단 오류는 257건에서 163건으로 감소했지만 세 문서 모두 여전히 `valid=false`다. 전체 경로 누락 163건 중 102건은 이제 `content_type=unknown`이고, 61건만 실제 표 청크다. Basic Roleplaying의 루트 전체를 표로 잘못 분류하던 95건은 표 경로 오류 집계에서 사라졌지만 71건은 경로가 없는 문서 루트 콘텐츠로 남아 있다. 따라서 유형 오분류를 바로잡은 것과 의미상 절 경로를 복원한 것은 구분해야 한다.

표 셀 출처를 가진 비표 청크는 세 문서에서 52건에서 0건으로 줄었다. 반복 머리글로 제외되던 표 셀을 문서 트리에 보존하고, 일반 문서 구간을 만들 때 표 셀 원문을 빼서 표가 별도 청크로 한 번만 생성되도록 했다. 미확정 제목 다음의 같은 페이지 본문은 해당 제목 노드의 원문 소유 구간에 연결하되 제목의 상위 관계나 `section_path`를 만들지 않는다. 이로 인해 Warden 규칙 청크가 `rule`로 단정되던 40건 중 14건은 검증 경로 누락에서 해소됐고, 남은 26건은 `unknown`의 빈 경로 오류로 남아 있다.

구조 평가에서 파일럿의 제목 탐지와 읽기 순서는 기준선과 같았다. Cairn Player의 표 8개는 마지막 행을 계속 놓치며 격자 일치율 0%다. Warden 119페이지 표도 예상 26행보다 빈 행 25개가 추가되어 전체 51행으로 남았고 격자 일치율은 0%다. 청크 경로 수정은 이 표 복원 오류를 바꾸지 않는다.

최종 실행에서 진단 로그를 파일로 남겼으며, Warden 로그에는 `unresolvedPageOwnedBlockCount=576`, 표 셀 3,059개 보존, 일반 구간에서 표 출처 1,217개 제외가 기록됐다. 전체 테스트는 314개 통과했다. 처리 시간은 BRP 304초, Player 24초, Warden 50초였고 최대 메모리는 각각 약 191 MiB, 411 MiB, 350 MiB였다. 진단 로그 활성화 여부와 코드 변경이 있어 기준선 처리 시간과 직접 성능 비교하지 않는다.

이번 수정은 출처 소유권과 표·본문 중복 경계를 개선했지만, 남은 빈 경로를 자동 허용하지 않았다. 실제 표 61개와 루트 콘텐츠 102개는 상위 절 근거가 부족하거나 소유 상태가 아직 충분히 표현되지 않아 게시 오류로 남는다. 다음에는 이 두 집단을 각각 원문 위치·지역 설명 관계·미확정 계층으로 나눠 확인해야 하며, 표 후보 행 복원은 독립된 후속 문제로 유지한다.
