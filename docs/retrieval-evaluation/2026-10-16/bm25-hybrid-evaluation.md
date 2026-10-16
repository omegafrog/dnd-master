# BM25 and vector retrieval comparison

## Result

The same 84-query evaluation set and provisional evidence labels were used for all three retrieval methods. Each method returned 20 chunks from the 2,480-chunk corpus.

| Retrieval method | Queries with all required evidence | Required evidence spans found |
| --- | ---: | ---: |
| Vector similarity | 75/84 (89.3%) | 109/120 (90.8%) |
| BM25 only | 67/84 (79.8%) | 100/120 (83.3%) |
| Vector + BM25 reciprocal-rank fusion | 75/84 (89.3%) | 111/120 (92.5%) |

BM25 alone underperformed vector retrieval. Reciprocal-rank fusion kept the number of fully supported queries unchanged and recovered two additional evidence spans overall.

## Method

- Corpus: 2,480 chunks from the existing multi-rulebook retrieval evaluation.
- Queries: 21 original questions and 63 paraphrases.
- BM25: lowercase alphanumeric word tokenization; `k1=1.5`, `b=0.75`.
- Hybrid: reciprocal-rank fusion of the complete vector and BM25 rankings with `k=60`.
- Output: top 20 chunks per query.
- Scoring: exact normalized matching against each provisional label's required evidence spans. A query counts as complete only when every required evidence fact is found.
- Retrieval scoring for all 84 questions took 2.62 seconds after the corpus and vector data were loaded; one-time index preparation is excluded.

Labels are provisional and not user-approved. This was an offline evaluation, not a test of the application's live search endpoint. The tokenizer and fusion constant were not tuned on a held-out set, so the small hybrid gain should be treated as preliminary.
