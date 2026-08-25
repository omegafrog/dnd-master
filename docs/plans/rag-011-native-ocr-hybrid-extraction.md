# RAG-011 Native OCR Hybrid Extraction

- 상태: `planned`
- 의존성: RAG-009
- Product Spec: UC-01, UC-05; BR-10 ~ BR-12
- Architecture Spec: Sections 2.6, 4.1, 5.11, 5.12
- GitHub Issue: [#184](https://github.com/omegafrog/dnd-master/issues/184)

## 구현 목적

네이티브 텍스트가 없는 스캔 페이지나 일부 영역만 이미지인 혼합 페이지를 버리지 않도록 PDF native extraction, page rendering, OCR을 port 뒤에 격리한다. 네이티브 결과가 렌더링과 일치하는 영역은 보존하고 필요한 영역에만 OCR을 적용하며, 모든 블록에 추출 방식과 텍스트 confidence를 남긴다.

## 사용자·엔티티 흐름

```text
process preprocess request
→ native evidence + page render
→ page/region extraction-method decision
→ targeted OCR when required
→ reconciled LayoutBlocks with provenance/confidence
→ layout ordering pipeline
```

## 범위

- `NativePdfPort`, `PageRenderPort`, `OcrPort` contracts
- adapter capability preflight와 stable failure translation
- page classification: text-native, image-only, mixed, ambiguous
- region-scoped OCR 요청과 word/block bbox normalization
- native/OCR reconciliation과 `native|ocr|hybrid` provenance
- OCR confidence와 구조 confidence의 분리
- mandatory adapter 부재 시 explicit page review signal

## 수용 기준

- native page는 OCR로 전면 대체되지 않는다.
- image-only page는 OCR adapter가 있을 때 좌표와 confidence를 가진 block을 생성한다.
- mixed page는 필요한 영역만 OCR하고 native 영역의 text/hash를 유지한다.
- adapter native objects는 domain/port 경계를 넘지 않는다.
- OCR 또는 render capability가 필수인데 없으면 성공으로 우회하지 않는다.
- 모든 block method와 text confidence가 artifact에 존재한다.

## 테스트 계약

- 정책 단위 테스트: extraction-method selection, reconciliation, capability gate
- adapter 계약 테스트: native/render/OCR normalized evidence와 오류 변환
- CLI ~ entity e2e: native, image-only, mixed PDF 각각의 page classification과 block provenance
- 차단 e2e: OCR unavailable image page → `NEEDS_REVIEW`, chunks 없음
- 회귀 테스트: injected native extractor seam과 기존 PDF tests 유지

## 구현 범위

허용: external ports, PDF/render/OCR adapters, extraction service, page/block model, config/preflight, fixtures와 테스트.

금지: 특정 Java runtime 연동, provider가 domain model에 노출되는 설계, 전체 validator/retry checkpoint 구현.

## 완료 증거

- capability matrix
- native/image/mixed fixture 결과
- adapter/CLI e2e 테스트 결과
