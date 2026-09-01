# Architecture Spec: Shared Rulebook Catalog and AI Endpoint Backoffice

## Boundaries

`Document Knowledge`는 `CatalogRulebook`과 `Storybook KnowledgeDocument`를 분리한다. Catalog는 revision, availability, publication을 소유한다. Storybook은 기존 owner scope를 유지한다. `AI Endpoint Registry`는 endpoint metadata와 secret reference를 소유한다.

## Aggregates

| Aggregate | Invariants |
|---|---|
| CatalogRulebook | edition별 revision은 immutable; READY revision만 선택 가능 |
| CatalogRevision | source → extraction → indexing → ready/failed; published revision은 삭제·변경 불가 |
| AiEndpoint | ADMIN만 변경; secret reference만 보관; health 결과는 비밀값 미포함 |

## Flow

```text
ADMIN upload catalog PDF
→ CatalogRevision QUEUED
→ Docling extraction + structured chunks
→ embedding/index
→ READY
→ publish
→ player selects revision
→ session locks catalog revision
```

```text
Player uploads Storybook
→ Docling extraction + structured chunks
→ owner-scoped embedding/index
→ READY
```

## Integration Contracts

- Catalog lookup returns published READY revision plus edition and document version.
- Rule evidence authorization accepts an explicitly selected published catalog revision or owned Storybook; never arbitrary document IDs.
- Endpoint adapter accepts provider, base URL, model IDs, timeout and resolved secret at call time.
- Healthcheck uses provider-native model list or minimal embedding/chat probe without persisting prompts or secrets.

## Slices

1. Catalog persistence, revision lifecycle, 5e bootstrap, shared retrieval authorization.
2. Backoffice API and ADMIN authorization.
3. Backoffice UI, catalog picker, Storybook-only upload UI.
4. AI endpoint registry, resolver, healthcheck, agent routing.
