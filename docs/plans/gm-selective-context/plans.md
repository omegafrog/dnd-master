# Codex 모험 턴 컨텍스트 개선 — 구현 계획 인덱스

이 문서는 승인된 분할 계획을 찾기 위한 인덱스다. 구현 범위·수용 기준·테스트 계약 및 상태의 정본은 각 GitHub 이슈다. Beads는 공유 보드에 이슈와 의존성을 연결한다.

## 실행 기준

- 세션 작업 디렉터리: `/home/jiwoo/workspace/dnd-master-spec-gm-selective-context`
- 세션 브랜치 및 구현 통합 브랜치(`execution_line`): `spec/gm-selective-context`
- 계획 분할 기준 명세 커밋: `775d78ef1ceaaa39c0a08b55a40717ca20d7d611`
- 부모 이슈: [#383 — Codex 모험 턴 컨텍스트·추가 조회·공개 서술 개선](https://github.com/omegafrog/dnd-master/issues/383)
- Beads 부모: `dnd-master-zic`
- 사용자 승인: 사용자는 5개 분할과 의존성 1→2→3·4→5에 ㅇㅇ로 동의했다. 3과 4는 공유 턴 코드 변경으로 순차 실행한다.
- 부모와 하위 이슈는 GitHub 프로젝트 5의 `Workflow Status`를 사용한다. 구현·검증 전에는 이슈를 닫지 않는다.

## 하위 구현 계획

| 순서 | 계획 ID | GitHub 이슈 및 구현 목적 | 선행 이슈 | Beads |
|---|---|---|---|---|
| 1 | `01-public-narration` | [#384 — 공개 정보 전용 서술 경계 연결](https://github.com/omegafrog/dnd-master/issues/384) | 없음 | `dnd-master-zic.1` |
| 2 | `02-basic-context-memory` | [#385 — 선택된 시츄에이션과 관련 기억을 기본 컨텍스트로 준비](https://github.com/omegafrog/dnd-master/issues/385) | [#384](https://github.com/omegafrog/dnd-master/issues/384) | `dnd-master-zic.2` |
| 3 | `03-selective-query-evidence` | [#386 — Codex 실행 중 추가 조회와 서버 근거 검증 연결](https://github.com/omegafrog/dnd-master/issues/386) | [#385](https://github.com/omegafrog/dnd-master/issues/385) | `dnd-master-zic.3` |
| 4 | `04-situation-turn-commit` | [#387 — 시츄에이션 활성화·종료 제안을 같은 턴에서 확정](https://github.com/omegafrog/dnd-master/issues/387) | [#385](https://github.com/omegafrog/dnd-master/issues/385) | `dnd-master-zic.4` |
| 5 | `05-turn-latency-verification` | [#388 — 실제 Codex 전체 턴 5초 측정과 지연 개선](https://github.com/omegafrog/dnd-master/issues/388) | [#386](https://github.com/omegafrog/dnd-master/issues/386), [#387](https://github.com/omegafrog/dnd-master/issues/387) | `dnd-master-zic.5` |

## 의존성과 실행 순서

기능 의존성은 #384 → #385 → (#386, #387) → #388다. 세 번째와 네 번째 계획은 같은 턴 코드를 수정하므로 실제 실행은 표의 순서대로 진행한다. 하나의 작업이 끝난 뒤 다음 작업으로 인계한다.

GitHub의 공식 하위 이슈 관계와 차단 의존성, Beads의 부모·차단 관계를 사용한다. 이 문서의 링크만으로 관계를 대신하지 않는다.

## 관련 명세

- [제품 명세](../../specs/gm-selective-context/product-spec.md)
- [아키텍처 명세](../../specs/gm-selective-context/architecture-spec.md)
- [성능·상태 목표](../../specs/gm-selective-context/system-targets.yaml)
- [설계 검토 기록](../../specs/gm-selective-context/review-notes.md)

## 다이어그램

- 제품 유스케이스 · 턴 진행과 선택 조회: [로컬 SVG](../../specs/gm-selective-context/diagrams/product/UC-01.usecase.svg) / [GitHub SVG](https://github.com/omegafrog/dnd-master/blob/spec/gm-selective-context/docs/specs/gm-selective-context/diagrams/product/UC-01.usecase.svg?raw=true)
- 제품 액티비티 · 턴 진행과 선택 조회: [로컬 SVG](../../specs/gm-selective-context/diagrams/product/UC-01.activity.svg) / [GitHub SVG](https://github.com/omegafrog/dnd-master/blob/spec/gm-selective-context/docs/specs/gm-selective-context/diagrams/product/UC-01.activity.svg?raw=true)
- 제품 유스케이스 · 확정 턴 기억과 다음 턴: [로컬 SVG](../../specs/gm-selective-context/diagrams/product/UC-02.usecase.svg) / [GitHub SVG](https://github.com/omegafrog/dnd-master/blob/spec/gm-selective-context/docs/specs/gm-selective-context/diagrams/product/UC-02.usecase.svg?raw=true)
- 제품 액티비티 · 확정 턴 기억과 다음 턴: [로컬 SVG](../../specs/gm-selective-context/diagrams/product/UC-02.activity.svg) / [GitHub SVG](https://github.com/omegafrog/dnd-master/blob/spec/gm-selective-context/docs/specs/gm-selective-context/diagrams/product/UC-02.activity.svg?raw=true)
- 아키텍처 클래스 · 컨텍스트·조회·공개 서술 연결: [로컬 SVG](../../specs/gm-selective-context/diagrams/architecture/runtime-context.class.svg) / [GitHub SVG](https://github.com/omegafrog/dnd-master/blob/spec/gm-selective-context/docs/specs/gm-selective-context/diagrams/architecture/runtime-context.class.svg?raw=true)
- 아키텍처 상태 · 실행 중 조회 근거의 수명: [로컬 SVG](../../specs/gm-selective-context/diagrams/architecture/runtime-context.state.svg) / [GitHub SVG](https://github.com/omegafrog/dnd-master/blob/spec/gm-selective-context/docs/specs/gm-selective-context/diagrams/architecture/runtime-context.state.svg?raw=true)

## 구현 인계

첫 실행 대상은 [#384](https://github.com/omegafrog/dnd-master/issues/384)이다. `implement`에 작업 루트 `/home/jiwoo/workspace/dnd-master-spec-gm-selective-context`, 세션 브랜치와 `execution_line` `spec/gm-selective-context`, 해당 이슈의 전체 계약을 전달한다. 계획을 위해 별도 브랜치를 생성하거나 전환하지 않는다.

각 작업의 단위·정책 검증과 화면 입력에서 저장 상태·공개 응답까지의 전체 흐름 검증은 해당 이슈의 테스트 계약을 따른다. 실제 Codex 검증은 [EXE.md](../../../EXE.md)의 작업 디렉터리와 실행 프로세스 확인을 포함한다. 마지막 작업은 전체 성공 턴의 5초 목표가 기록된 실제 조건에서 충족되는지 확인하며, 미충족 상태를 완료로 바꾸지 않는다.

## 작성·게시 상태

세션 브랜치는 로컬에만 있으며, to-ticket은 원격에 푸시하지 않는다. GitHub의 브랜치별 명세·SVG 링크는 세션 브랜치가 게시된 뒤 표시된다.

이 계획에서는 명세와 계획 인덱스만 준비했다. 애플리케이션 구현·테스트·실제 모델 성능 측정은 실행하지 않았다. 계획 PR은 만들지 않는다. 모든 구현이 같은 실행 브랜치에 통합된 뒤 downstream 구현 과정에서 최종 PR을 만든다.

## 인계 전 확인

부모 #383과 하위 #384–#388의 공식 부모·자식 관계, 차단 의존성, 담당자, 프로젝트의 `Workflow Status=Planned`, Beads 연결을 실제 조회로 확인했다. 이슈 본문은 검증된 구조화 계획을 렌더링한 결과와 일치한다. 계획 인덱스 검사는 통과했다. 원격 명세·다이어그램 표시만 브랜치 게시 전이라 대기 중이다.
