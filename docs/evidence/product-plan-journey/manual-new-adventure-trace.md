# Manual new-adventure trace

Date: 2026-09-03
Session: `399f5227-67c4-46ae-a3c0-0a793944e071`
Operation ID: `ae7acd46-c649-48b6-b3a5-0a9c606631df`

## Execution

The adventure session was created from the existing published scenario package. No indexing was performed. A headed Chromium browser was launched and the story-plan generation was started through the player UI.

## Observed attempt sequence

```text
attempt 1: initial story-plan generation
  candidate rejected: COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED (3 occurrences)

attempt 2: scoped projection repair
  repaired candidate parsing failed
  cause: document type must not be blank
  violation: REPAIRED_CANDIDATE_PARSE_FAILED

attempt 3: full regeneration
  started with the previous candidate violations

attempt 4: projection repair
  repair scope:
    repairable=false
    blockers included:
      stages[3].failureCondition
      stages
      stages[3].combatSkeleton.participants[*].name
      stages[1].combatSkeleton.participants[*].name
      stages[4].combatSkeleton.participants[*].name

  projection repair was still invoked after repairable=false.
```

## Exact parser failure

```text
Caused by: java.lang.IllegalArgumentException: document type must not be blank
at com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence.required
at com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService.readMergedStages
```

## Final interpretation

The repair response contained an evidence item with a blank `documentType`. That caused repaired-candidate parsing to fail. The resulting `REPAIRED_CANDIDATE_PARSE_FAILED` violation was retained in the accumulated violation list. A later repair-scope calculation therefore included the broad `stages` path and reported `repairable=false`, while the service still called the repair gateway.

The session ended without a persisted Story Plan (`STORY_PLAN_NOT_FOUND`).

## Trace completeness

The backend process output for this run was attached to the interactive `start-dev.sh` PTY and was not redirected to a file. Therefore the complete prompt and complete AI response are not recoverable from this run. The next run must start the development server with stdout/stderr redirected to a dedicated log file to preserve `story_plan_trace` and `story_plan_candidate_trace` entries.
