# RAG-010 Heading and Table Structure

- 상태: `planned`
- 의존성: RAG-009
- Product Spec: UC-04; BR-07 ~ BR-09
- Architecture Spec: Sections 3.5, 3.6, 11.5
- GitHub Issue: [#183](https://github.com/omegafrog/dnd-master/issues/183)

## 구현 목적

제목과 표를 일반 본문 문자열로 평탄화하지 않고 페이지 geometry에 연결된 구조로 보존한다. 제목은 시각적 위계와 열·영역 관계에 따라 관련 본문에 연결하고, 표는 헤더·행·셀·병합 및 불확실 셀을 좌표와 함께 제공해 downstream chunking과 근거 추적이 표 구조를 잃지 않도록 한다.

## 사용자·엔티티 흐름

```text
ordered layout regions
→ heading association + table detection
→ structured heading/table blocks
→ page artifact and ParsedDocument projection
→ existing content classification/chunk flow
```

## 범위

- heading hierarchy·association evidence
- 같은 열/명확한 spanning section 아래 내용만 제목에 연결
- table bbox, header rows, data rows, cell bbox
- multi-row/multi-column headers와 merged/uncertain cell 표기
- 표를 primary reading order의 하나의 구조 블록으로 유지
- downstream projection에서 source coordinates와 table identity 보존

## 수용 기준

- 글자 크기만 같은 본문과 실제 제목을 fixture 기준으로 구분한다.
- 제목이 다른 열의 무관한 본문과 연결되지 않는다.
- 표 헤더·행·셀 좌표가 원본 page geometry 안에 있고 표 bbox에 포함된다.
- multi-row header와 merged/uncertain cell이 명시적으로 표현된다.
- 표 내용이 단순 prose block 하나로만 저장되지 않는다.
- 구조가 모호한 표는 후보 해석과 review finding을 남긴다.

## 테스트 계약

- 정책 단위 테스트: heading association, header/row/cell grouping, merged/uncertain rules
- 계약 테스트: table/heading JSON schema와 source-coordinate round trip
- CLI ~ entity e2e: heading + full-width table + 2열 본문 PDF → structured page artifact
- 실패 e2e: irregular table → uncertain cells/finding → non-READY signal
- 회귀 테스트: 기존 content classifier와 tree builder가 projection을 소비

## 구현 범위

허용: layout table/heading domain values and services, projector, layout schema/artifact, golden fixtures와 테스트.

금지: OCR provider, 전체 페이지 render validator, 자동 retry orchestration, chunk-size 변경.

## 완료 증거

- heading/table fixture artifact
- cell bbox 및 uncertainty 사례
- 단위/계약/e2e 결과
