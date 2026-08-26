# RAG-008 Extraction Version Process Port

- 상태: `completed`
- 의존성: 없음
- Product Spec: `docs/specs/product-spec.md` UC-01, UC-07
- Architecture Spec: `docs/specs/architecture-spec.md` Sections 3, 4, 5.4, 5.5
- GitHub Issue: [#181](https://github.com/omegafrog/dnd-master/issues/181)

## 구현 목적

별도 Python 전처리 모듈을 Java 등 다른 서비스가 표준 CPython 프로세스로 호출할 수 있도록, 버전된 JSON stdin/stdout 포트와 `ExtractionVersion` 생명주기의 최소 수직 경로를 만든다. 첫 슬라이스는 단일 열·네이티브 텍스트 PDF를 입력받아 페이지 geometry를 검증하고, READY인 경우에만 기존 `ParsedDocument`와 chunk 파이프라인으로 투영되는 walking skeleton을 제공한다.

## 사용자·엔티티 흐름

```text
JSON preprocess request
→ ProcessPortCli
→ ExtractionVersion / PageExtraction
→ native single-column page evidence
→ basic page validation
→ READY publication
→ ParsedDocument projection
→ existing tree/chunk artifacts
```

## 범위

- `ExtractionVersion`, `PageExtraction`, 상태와 게시 불변식
- `PageGeometry`, `BoundingBox`, 최소 `LayoutBlock` 계약
- `PreprocessingApplicationPort.preprocess()`와 `get_status()`
- versioned process request/response JSON schemas
- stdin 한 건, stdout JSON 한 건, stderr 로그 규칙
- 단일 열 네이티브 PDF의 page dimensions·bbox 정규화
- READY Extraction Version의 기존 `ParsedDocument` projection
- non-READY 결과가 기존 tree/chunk exporter에 진입하지 않는 기본 게이트
- version manifest와 기본 page status artifact

## 수용 기준

- 고정된 CPython module 명령이 유효한 JSON 요청을 받아 단일 JSON 응답을 반환한다.
- 응답은 schema version, request ID, version ID, status, page summary와 artifact ref/hash를 포함한다.
- 모든 블록 bbox가 top-left origin PDF points 규약과 페이지 경계 검증을 통과한다.
- 모든 페이지가 기본 검증을 통과한 경우에만 `ParsedDocument`와 기존 chunk artifact가 생성된다.
- invalid request, unsupported schema와 parser failure는 안정적인 오류 코드와 비정상 exit code를 반환한다.
- 로그가 stdout JSON을 오염시키지 않는다.

## 테스트 계약

- 정책 단위 테스트: aggregate 상태 전이, bbox/page geometry, READY 게시 불변식
- 계약 테스트: request/response JSON schema, exit code, stdout/stderr 분리
- CLI ~ entity e2e: 단일 열 PDF 요청 → `ExtractionVersion` → READY → `ParsedDocument`와 chunks
- 차단 e2e: geometry가 유효하지 않은 페이지 → non-READY → `chunks.jsonl` 없음
- 회귀 테스트: 기존 Markdown fixture와 현재 단일 열 PDF parser/pipeline 테스트 유지

## 구현 범위

허용:

- `src/preprocessing_agent/domain/layout.py`
- `src/preprocessing_agent/ports/preprocessing.py`
- `src/preprocessing_agent/pipeline/extraction_service.py`
- `src/preprocessing_agent/adapters/process_cli.py`
- 기존 `app.py`, `pipeline.py`, `domain/models.py`, 관련 exporter/schema와 테스트의 최소 변경

금지:

- 다열·혼합 열 판별
- geometry-aware table 구조화
- OCR 또는 외부 secondary validator 구현
- Java `ProcessBuilder` adapter, HTTP/gRPC, backend 저장소 변경

## 완료 증거

- 실행 명령과 테스트 결과
- process request/response fixture
- READY 및 차단 artifact 목록
- 변경 파일과 기존 pipeline 회귀 결과
