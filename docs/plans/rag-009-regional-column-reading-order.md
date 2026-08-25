# RAG-009 Regional Column Reading Order

- 상태: `completed`
- 의존성: RAG-008
- Product Spec: UC-02, UC-03; BR-03 ~ BR-06
- Architecture Spec: Sections 3.5, 3.6, 5.13
- GitHub Issue: [#182](https://github.com/omegafrog/dnd-master/issues/182)

## 구현 목적

페이지 전체에 열 수 하나를 강제하는 기존 2열 휴리스틱을 대체하고, 페이지를 동일한 열 구조의 영역으로 나눈 뒤 각 영역의 열 후보와 읽기 순서 전략을 선택한다. 전체 폭 제목·표·그림 같은 spanning block과 반복 머리말·꼬리말을 위치에 맞게 결합해 단일 열, 다열, `1 → 2 → 1` 혼합 레이아웃을 원문 순서대로 투영한다.

## 사용자·엔티티 흐름

```text
process preprocess request
→ PageExtraction evidence
→ LayoutAnalyzer regions/hypotheses
→ ReadingOrderPlanner
→ ColumnProfile + ordered blocks
→ READY projection or explicit ambiguity
```

## 범위

- `LayoutRegion`, `ColumnHypothesis`, `ColumnProfile`, `ReadingOrderPlan`
- 수평 정렬·gutter·spanning geometry 기반 영역 분할
- 영역별 1열/2열/다열 후보와 후보별 점수
- 열 내부 순서 후 열·영역 결합 전략
- spanning block의 선행·중간·후행 삽입
- 반복 머리말·꼬리말 보존 및 primary order 제외
- 기존 `_strong_column_split`의 최종 ordering 책임 제거

## 수용 기준

- 단일 열, 명확한 2열, `1 → 2 → 1` fixture의 영역과 Column Profile이 gold와 일치한다.
- 선택 후보뿐 아니라 경쟁 후보와 점수가 artifact에 남는다.
- confirmed block은 읽기 순서에 정확히 한 번 포함된다.
- 원시 extractor order를 시각적 순서의 fallback으로 사용하지 않는다.
- spanning block과 반복 머리말·꼬리말이 gold 역할 및 위치와 일치한다.
- 모호한 후보는 강제로 확정하지 않고 페이지 차단 신호를 생성한다.

## 테스트 계약

- 정책 단위 테스트: gutter/region/column hypothesis, tie/ambiguity, spanning insertion
- 속성 테스트: ordered block coverage·중복 없음·영역 경계 보존
- CLI ~ entity e2e: mixed-column PDF → `PageExtraction` → Column Profile → ordered projection
- 실패 e2e: 경쟁 후보 점수 차가 기준 미만 → non-READY 및 후보 진단
- 회귀 테스트: 기존 strong two-column fixture의 의도 보존, table-like extractor order 오판 방지

## 구현 범위

허용: `layout/analyzer.py`, `layout/reading_order.py`, layout domain values, `parsers/pdf.py`, layout schemas/artifacts와 관련 테스트.

금지: 표 cell 의미 구조, OCR, 최종 multidimensional validation, retry checkpoint orchestration.

## 완료 증거

- layout fixture별 Column Profile과 block order
- 후보·점수 artifact
- 단위/계약/e2e 테스트 결과
