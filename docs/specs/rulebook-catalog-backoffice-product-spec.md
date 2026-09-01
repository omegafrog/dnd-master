# Product Spec: Shared Rulebook Catalog and AI Endpoint Backoffice

## Problem and Context

룰북은 유저 업로드 문서가 아니라 운영자가 한 번 색인하고 모든 유저가 선택하는 공용 RAG 자료여야 한다. Storybook은 유저별 자료로 남는다. 운영자는 모델 endpoint와 health를 관리해야 한다.

## Goals

- ADMIN은 5e(2014), 5.5e(2024) catalog 룰북 revision을 업로드·색인·게시한다.
- Solo Player는 READY catalog revision 하나를 선택한다.
- 유저 업로드는 STORYBOOK만 허용하며 Docling pipeline을 거친다.
- ADMIN은 Ollama 또는 OpenAI-compatible AI endpoint를 관리하고 healthcheck한다.

## Actors

- **ADMIN**: catalog와 AI endpoint를 관리한다.
- **Solo Player**: READY catalog 룰북을 선택하고 Storybook을 업로드한다.

## Use Cases

- **RC-001** ADMIN이 edition을 지정해 룰북 PDF를 업로드하고 RAG 색인을 시작한다.
- **RC-002** ADMIN이 완료된 catalog revision을 게시한다.
- **RC-003** Solo Player가 5e 또는 5.5e READY revision을 선택한다.
- **RC-004** Solo Player가 Storybook을 업로드하고 Docling 처리 상태를 본다.
- **RC-005** ADMIN이 AI endpoint와 모델을 등록·수정하고 healthcheck한다.

## Rules

- Catalog revision은 immutable이다. 교체는 새 revision을 만든다.
- 실행 중 모험은 선택 revision을 고정한다.
- 5.5e 파일이 없으면 `UNAVAILABLE`이며 선택할 수 없다.
- Catalog RAG는 모든 유저가 읽을 수 있지만 ADMIN만 변경할 수 있다.
- Storybook은 업로더 소유이며 다른 유저가 검색할 수 없다.
- endpoint secret 값은 DB/UI에 저장하지 않는다. 환경변수 reference만 저장한다.
- healthcheck는 secret을 반환하지 않으며 endpoint, provider, 모델, latency, 오류만 기록한다.

## Acceptance Criteria

- 5e PDF가 pipeline 후 READY catalog revision으로 표시된다.
- 5.5e는 파일 등록 전 준비 중으로 표시된다.
- 일반 유저는 catalog 룰북을 선택하지만 수정할 수 없다.
- 일반 유저의 RULEBOOK upload는 거부되고 STORYBOOK upload만 처리된다.
- endpoint healthcheck가 연결 성공·실패와 latency를 표시한다.
