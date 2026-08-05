# 032-6 — Bundle Map Compilation and Activation

- Status: `completed`
- Issue: [#120](https://github.com/omegafrog/dnd-master/issues/120)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-2](032-2-atomic-commit-and-sse-projection.md)
- Spec: Product UC-003, BR-020/021/022, FR-003/004/005; Architecture §§3.1–3.2, 5.4

## Outcome

Bundle compilation detects source maps, stores immutable map definitions, connects them to story conditions, and activates the correct player-safe map without blocking mapless play.

## Vertical Scope

- Expose immutable source Asset/Source Span identity from Document Knowledge.
- Detect `MAP` documents/assets during scenario compilation.
- Add versioned `MapDefinition`: asset ref, grid origin/cell size/rotation/distance, walls/doors/obstacles, source provenance, confidence/safety status.
- Add `StoryMapBinding`: stage/location/entry condition → map definition ID.
- Extend story plan generation/revision schema with map bindings.
- Instantiate/activate runtime Combat Map when committed scene satisfies condition.
- Provide text fallback for missing/unsafe/unusable maps.

## Policy Unit Tests

- map definitions reference locked document/extraction versions only.
- fixed turn-number bindings are rejected; semantic scene/location/condition accepted.
- unsafe/low-confidence map cannot auto-activate.
- no-map package remains playable.

## Integration and Contract Tests

- PDF/image asset → package map definition persistence.
- story plan generator map-binding structured contract.
- runtime activation idempotency and source version checks.
- player map response excludes GM-only source metadata.

## UI ~ Entity E2E

Compile bundle containing map → start adventure → enter bound scene → separate map window loads correct source image; mapless scene continues text UI.

## Implementation Scope

- rule-knowledge asset contracts
- adventure scenario compilation/domain/migrations
- story-plan generator contracts
- Combat Map preparation adapter
- public tactical-map read/projection shell and UI activation

## Out of Scope

Drag movement, fog, hidden tokens, user calibration editor.

## Completion

- Map detection-to-activation flow passes.
- Status becomes `completed`; 032-7 waits for 032-4 too.

## Execution

- Added immutable source-pinned `MapDefinition`, `StoryMapBinding`, grid metadata, and safety policy.
- Scenario compilation detects MAP/MAP_BINDING excerpts, rejects unlocked extraction versions, and persists map JSON.
- Runtime activation selects only safe high-confidence maps; missing or unsafe maps use text fallback.
- Added migration `V30__scenario_package_maps.sql` and focused/full adventure-service tests.
