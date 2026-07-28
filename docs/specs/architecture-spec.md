# Architecture Spec

## 1. Design Scope

| Item | Decision |
| --- | --- |
| Product Spec | `docs/specs/product-spec.md` |
| Contexts | Document Knowledge, Scenario Preparation, AI Game Master |
| Use case | Compile character-creation context and blueprint |
| Services | `rule-knowledge-service`, `adventure-service`, `ai-game-master-service` |
| Data | `rulebook_registration`, `rulebook_vector_index`, `rulebook_vector_chunk`, Scenario Package blueprint revisions |

## 2. Product Mapping

| Requirement | Architecture contract |
| --- | --- |
| RULEBOOK must be searchable | Reuse `rulebook_vector_chunk` and pgvector |
| Independent retrieval | One search per DocumentType, then merge |
| Similarity lower bound | Per-type threshold; no global top-k |
| Manual/select/fixed fields | Blueprint compiler input-mode classification |
| Evidence/conflicts | Grounding validation and blueprint diagnostics |
| Resolution stays separate | Separate query intent, port, and budget |

## 3. Domain Flow

1. Load Scenario Source Bundle and extraction versions.
2. Build a `CHARACTER_CREATION` query.
3. Search RULEBOOK, STORYBOOK, and HANDOUT independently.
4. Apply each type's similarity threshold.
5. Deduplicate and pack evidence within a token budget.
6. Request AI character tags.
7. Reject ungrounded candidates.
8. Compile MANUAL_INPUT, SELECT_OPTION, and FIXED_VALUE fields.
9. Mark cross-source conflicts for review.
10. Persist blueprint revision; player resolves and publishes.

### Commands

| Command | Owner | Input | Output |
| --- | --- | --- | --- |
| `SearchCharacterContext` | Document Knowledge | owner, scope, intent, thresholds, budget | scored evidence |
| `ExtractCharacterInputTags` | AI Game Master | evidence, schema/prompt versions | candidate tags |
| `CompileCharacterBlueprint` | Scenario Preparation | grounded candidates | blueprint revision |
| `ResolveBlueprintField` | Scenario Preparation | expected revision, field, value | new revision |
| `AddBlueprintChild` | Scenario Preparation | expected revision, parent, key, label | new revision |
| `PublishBlueprint` | Scenario Preparation | package id | immutable revision |

### Policies

- `SearchPerDocumentType`: searches run independently.
- `SimilarityThresholdPolicy`: discard hits below the type threshold.
- `EvidenceBudgetPolicy`: deduplicate and pack by token budget; never starve a source with global top-k.
- `GroundingPolicy`: candidate evidence, locator, and quote must match retrieved text.
- `ManualFallbackPolicy`: missing/rejected candidates become manual input.
- `ConflictPolicy`: conflicting values become `CONFLICT_REVIEW`, never silent union.

## 4. DDD Architecture

| Context | Responsibility | Owned model/data |
| --- | --- | --- |
| Document Knowledge | extraction, chunking, embedding, scoped vector search | Knowledge Document, Evidence, vector tables |
| Scenario Preparation | validation and versioned blueprint | Scenario Package, CharacterCreationBlueprint |
| AI Game Master | untrusted tag proposals | candidate DTO only |

### Aggregates and Services

| Component | Responsibility | Invariant |
| --- | --- | --- |
| `ScenarioPackage` | own compiled package and blueprint revision | published revision immutable |
| `CharacterCreationBlueprint` | fields, tags, values, options, diagnostics | revision match; unresolved conflicts block publish |
| `CharacterContextMergePolicy` | threshold, dedupe, diversity, budget | source types independently searched |
| `CharacterCreationBlueprintCompiler` | classify, fallback, conflict detection | ungrounded values never marked extracted |

### State

`Field: UNRESOLVED -> MANUAL_INPUT | SELECT_OPTION | FIXED_VALUE | CONFLICT_REVIEW -> RESOLVED`

`Blueprint: NEEDS_REVIEW -> READY -> PUBLISHED`

## 5. Program Design

### Components

| Component | Responsibility | Must not do |
| --- | --- | --- |
| `CharacterContextSearchPort` | expose typed character evidence | call AI or compile |
| `CharacterContextSearchApplicationService` | embed and independently search types | global result limit |
| `CharacterContextMergePolicy` | threshold, dedupe, diversity, budget | drop a type because another has more hits |
| `CrossContextHttpCharacterContextSearchGateway` | translate adventure/knowledge contract | own vector persistence |
| `ScenarioCompilationWorker` | search → AI tags → compiler orchestration | arbitrary character `.limit(3)` |
| `CharacterInputTagExtractionPort` | AI boundary and schema versioning | persist unverified output |
| `CharacterCreationBlueprintCompiler` | input modes and conflicts | retrieve documents |

### Contracts

`CharacterContextQuery` contains owner, bundle scope, document IDs/types/versions, `CHARACTER_CREATION`, per-type thresholds, and token budget.

`CharacterContextEvidence` contains document ID/type, extraction version, locator, text, similarity, and source-span metadata.

`CharacterInputTagCandidate` retains key, parent key, label, required, input mode, options, suggestions, confidence, evidence, source quote, and source type.

### Application Flow

1. Worker loads bundle scope.
2. Search port requests character evidence.
3. Knowledge service embeds the query and runs independent type-scoped vector searches.
4. Each search applies its threshold; an empty type does not fail other types.
5. Merge policy deduplicates locators, prefers diverse sections, and packs to budget.
6. Worker sends evidence to the existing tag extraction port.
7. Gateway rejects candidates whose evidence/quote does not match.
8. Compiler creates fields, fallback fields, and conflict diagnostics.
9. Package repository persists the blueprint revision.

## 6. Current Structure and Required Delta

Current seams:

- RULEBOOK chunking/embeddings: `RulebookIndexingPolicy`, `RulebookIndexingApplicationService`, `rulebook_vector_chunk`.
- Rule/story vector search: separate application/repository adapters over pgvector.
- Source excerpt assembly: `CrossContextHttpScenarioSourceExcerptGateway`.
- Compilation orchestration: `ScenarioCompilationWorker`.
- AI boundary: `CharacterInputTagExtractionPort`, `CrossContextHttpCharacterInputTagExtractionGateway`, `CharacterInputTagController`.
- Blueprint compilation: `CharacterCreationBlueprintCompiler.compileAgent()`.

Required delta:

1. Add a character-context search contract and knowledge-side endpoint/service.
2. Use indexed RULEBOOK vector chunks instead of fixed source-preview slices for retrieval.
3. Add independent per-type threshold search and merge policy.
4. Separate resolution and character evidence budgets.
5. Remove global character `.limit(3)`.
6. Preserve score/type/version/locator through the adapter boundary.
7. Support fixed values, manual edits, and conflict review in blueprint state.

## 7. Persistence and Versioning

- No authoritative AI storage is needed; AI output remains a candidate.
- Existing vector chunks remain owned by Document Knowledge.
- Values, tags, diagnostics, and manual changes persist with blueprint revisions.
- Published revisions are immutable.
- Thresholds, embedding model, prompt/schema versions, and token budget belong in the compile fingerprint.

## 8. Failure and Recovery

- No threshold-qualified evidence: field becomes manual input.
- One type has no results: continue with other types.
- Scope/version mismatch: reject candidate.
- AI timeout/failure: retry; partial output may only use manual fallback.
- Cross-source conflict: persist diagnostics and block publication until resolved.
- Budget overflow: remove low-score/redundant evidence, never by global source-starving top-k.

## 9. Tests

- Search service: thresholds, scope/version filtering, empty-type isolation.
- Merge policy: no starvation, dedupe, diversity, token budget.
- Worker: story hits do not remove rulebook hits; resolution and character budgets are independent.
- Gateway: typed evidence mapping and grounding rejection.
- Compiler: manual/select/fixed values, conflicts, fallback, manual additions.
- PostgreSQL/pgvector integration: character query returns RULEBOOK chunks.
- API/system: indexed RULEBOOK + STORYBOOK → compile → review → publish → character sheet.

## 10. Decisions and Risks

- Preserve existing rule/story adapters; unify only behind the character-context contract first.
- Use per-type thresholds plus token budget, not arbitrary global counts.
- Calibrate thresholds per embedding model and document type.
- Add diversity ranking after baseline threshold retrieval.
- Keep development evidence metadata removable from presentation without changing the domain contract.
