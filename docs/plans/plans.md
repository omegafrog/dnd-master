# Layout-Aware PDF Preprocessing Plan Index

| Plan | Issue | Status | Dependencies | Scope |
|---|---|---|---|---|
| [RAG-008 Extraction Version Process Port](rag-008-extraction-version-process-port.md) | [#181](https://github.com/omegafrog/dnd-master/issues/181) | `completed` | none | JSON process port, ExtractionVersion lifecycle, single-column walking skeleton |
| [RAG-009 Regional Column Reading Order](rag-009-regional-column-reading-order.md) | [#182](https://github.com/omegafrog/dnd-master/issues/182) | `completed` | RAG-008 | layout regions, column hypotheses/profile, spanning-block reading order |
| [RAG-010 Heading and Table Structure](rag-010-heading-table-structure.md) | [#183](https://github.com/omegafrog/dnd-master/issues/183) | `ready-for-agent` | RAG-009 | heading association, table headers/rows/cells, uncertainty |
| [RAG-011 Native OCR Hybrid Extraction](rag-011-native-ocr-hybrid-extraction.md) | [#184](https://github.com/omegafrog/dnd-master/issues/184) | `ready-for-agent` | RAG-009 | native/OCR/hybrid ports, provenance and capability gate |
| [RAG-012 Layout Validation Publication Gate](rag-012-layout-validation-publication-gate.md) | [#185](https://github.com/omegafrog/dnd-master/issues/185) | `planned` | RAG-010, RAG-011 | render validation, multidimensional confidence, page/version gate |
| [RAG-013 Page Retry Diagnostics Recovery](rag-013-page-retry-diagnostics-recovery.md) | [#186](https://github.com/omegafrog/dnd-master/issues/186) | `planned` | RAG-012 | page checkpoints, bounded retry, status/retry process operations, diagnostics |
