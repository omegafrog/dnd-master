# RAG-008: Extraction Version Process Port

별도 Python 전처리 모듈을 다른 서비스가 표준 CPython 프로세스로 호출할 수 있도록 JSON stdin/stdout 포트와 `ExtractionVersion` walking skeleton을 구현한다.


GitHub: https://github.com/omegafrog/dnd-master/issues/181

Depends on: none

## Scope

- ExtractionVersion/PageExtraction lifecycle and basic READY gate
- versioned process request/response schemas
- single-column native PDF geometry normalization
- READY-only ParsedDocument projection and existing chunk flow

## Acceptance

- valid request produces one JSON stdout response and Java-consumable artifact refs
- invalid geometry or processing failure cannot publish chunks
- stderr-only logs do not corrupt the response contract
- policy unit, process contract, and CLI-to-entity e2e tests pass
