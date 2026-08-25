# D&D Basic Rules 50-case gold annotation

This fixture is pinned to the 449-chunk run at
`/tmp/dnd-basic-rules-consistency-1/chunks.jsonl`. Every case is answerable
and uses an exported `chunk_id` verified against the chunk's source span; the
fixture intentionally does not infer gold IDs from a retrieval ranking.

## Replacements

The original E046--E050 questions are outside this Basic Rules corpus: it has
no Bard, Paladin, Warlock, Dragonborn, or Tiefling character-option rules.
Their IDs are retained to keep the fifty-case evaluation contract stable, but
their questions are replaced with source-backed rules:

| ID | Replacement topic | Verified source page |
| --- | --- | ---: |
| E046 | advantage and disadvantage cancel each other | 60 |
| E047 | passive-check formula and advantage/disadvantage adjustment | 61 |
| E048 | three-quarters cover bonus | 77 |
| E049 | total-cover targeting rule | 77 |
| E050 | ability-score maximum from an ability-score increase | 12 |

E001--E045 retain the questions supplied by the user. `gold_chunk_ids` and
the sole `evidence_groups` member identify the minimal source chunk required
for each answer; multi-part questions were checked against the complete
`embedding_text` and its listed PDF source spans.
