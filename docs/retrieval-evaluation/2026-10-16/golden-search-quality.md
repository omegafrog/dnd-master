# Golden search quality experiments

## Scope and evaluation contract

- Source: 1,785 saved chunks from Basic Roleplaying, Cairn Player, and Cairn Warden.
- Initial gold set: 12 source-checked questions. Follow-up experiments expanded this to 21 questions, 30 required evidence facts, and 63 independently written paraphrases across the same three books. Labels remain provisional and await user approval.
- A question counts as complete only when the selected text contains every required source quote. Finding a parent chunk that contains the quote elsewhere does not count.
- Tables remain whole unless a row-specific experiment is explicitly named.
- No LLM or GPU was used. Embeddings used local `qwen3-embedding:0.6b` on CPU.
- Input hashes: chunks `5178d7fecd844a63bdfa026ea04e364122e44c6e41ee3bd819f912f08c42ddb8`; gold labels `d2459ddbb14a39b13dea408b40d9563325b013d6ff7dec05f615ebe8950544d4`; paraphrases `0620a99c3a5bffe1afeea1101a0f6c4a449d91c2206c7cfe6320793b812249ad`.

## Results

| Attempt | Exact evidence in top 10 / top 20 paraphrase queries | Context size | Decision |
|---|---:|---:|---|
| Existing whole chunks, dense only | 29/36 / 29/36 | 5,798 / 11,248 average characters | Baseline |
| 180-word prose pieces, whole tables, dense only | 30/36 / 30/36 | 3,865 / 7,636 | Keep as an evaluation candidate; not applied to the product |
| 180-word prose pieces, table rows, dense only | 28/36 / 29/36 | 4,624 / 8,185 | Reject row splitting; keep tables whole |
| 180-word pieces plus full-corpus term-rarity retrieval, rank-fused with dense results | 30/36 / **33/36** | 4,223 / 8,705 | Best measured paraphrase recall; not applied |
| 80-word prose pieces, whole tables, dense only | 29/36 / 29/36 | See run artifact | Reject; smaller did not improve exact evidence retrieval |
| 80-word pieces plus full-corpus term-rarity retrieval, rank-fused with dense results | 30/36 / 32/36 | See run artifact | Reject in favor of 180-word pieces |

The 180-word dense plus term-rarity result reached 33/36 (91.7%) at top 20 across all three books: Basic Roleplaying 12/12, Cairn Player 9/12, Cairn Warden 12/12. This is the only tested combination above 90% for the paraphrase set. The Cairn Player misses are all three paraphrases of the player-attribute question; the exact source sentence is embedded in a short inventory-list chunk and did not enter the fused top 20.

For the 12 original questions, candidate-local term-rarity reranking of existing whole-chunk dense results reached 11/12 (91.7%) at top 10, with Basic Roleplaying 3/4, Cairn Player 4/4, and Cairn Warden 4/4. It left paraphrase performance at 29/36. Full-corpus term retrieval plus dense rank fusion reached 12/12 original questions at top 10, but increased average delivered text from 6,517 to 13,987 characters. This is too much context growth for the measured gain.

The 80-word result illustrates why parent-chunk scores are insufficient: its earlier parent-level experiment scored 34/36, while this source-range evaluation of the text actually retrieved scored 29/36 for dense search and 32/36 after term retrieval fusion.

## Generalization check: leave one rulebook out

The 33/36 result is not enough to justify adoption. A second, generic term-rarity ranker was evaluated with leave-one-book-out selection: choose its reciprocal-rank-fusion weight using two books, then measure the third. The tested term weights were 0, 0.25, 0.5, 1, 2, and 4; the dense and term ranks used an RRF constant of 60. The lexical ranker used word frequency and inverse document frequency over the 180-word prose pieces and whole-table views, without book-, page-, or question-specific rules.

| Held-out book | Weight selected on other two books | Held-out result | Dense-only child baseline |
|---|---:|---:|---:|
| Basic Roleplaying | 0 | 9/12 | 9/12 |
| Cairn Player | 0 | 9/12 | 9/12 |
| Cairn Warden | 0 | 9/12 | 9/12 |

This independent split did not reproduce a gain: for every held-out book, the best setting on the other two was to omit lexical ranking. Therefore the earlier 33/36 result is an exploratory in-sample result, not evidence of a generally better search method. Do not apply it to production. The 90% target remains unverified on held-out rulebooks.

## Experiment: prefix each prose piece with its parent chunk's first line

To test whether short heading context improves semantic matching without splitting tables, each 180-word prose piece was prefixed with its parent's first non-empty line when that line was at most 100 characters and 12 words and did not end in sentence punctuation. Tables remained whole. This was a generic rule applied across all three books; it did not use the question or gold answer.

| Measure | 180-word pieces, no prefix | First-line prefix |
|---|---:|---:|
| Paraphrase questions complete at top 10 | 30/36 | 28/36 |
| Paraphrase questions complete at top 20 | 30/36 | 29/36 |
| Original questions complete at top 20 | 12/12 | 11/12 |
| Mean delivered characters, paraphrases at top 20 | 7,636 | 6,136 |
| Embedding time | 221.56 s | 107.48 s |

The prefix rule did not improve evidence recall and reduced original-query coverage. It is rejected. The timing is from the embedding calls only, using local `qwen3-embedding:0.6b` on CPU; ranking evaluation adds time and uses a slow pure-Python cosine loop.

## Experiment: combine parent and child dense rankings

To test small-piece retrieval with fallback to the original chunk, parent and child dense ranks were fused and one representation per source chunk was retained. On the original 36 paraphrases, the best tested weights reached 30/36, the same as child-only retrieval. Leave-one-book-out weight selection chose child weight 0 for Basic Roleplaying and yielded 8/12 there, below the child-only 9/12; Cairn Player and Cairn Warden matched their child-only results at 9/12 and 12/12. Mean context at the best all-data setting was about 7,679 characters versus 7,636 for child-only. No measured gain; reject.

## Expanded page sample across three books

The 12-question evaluation was too small to support a 90% claim. Nine additional questions were drafted from different pages, three per book. Each quote was checked against the selected original PDF page and its extracted chunk provenance. The questions cover rules, descriptions, and tables; the Alchemist cost question requires both the cost row and its daily-rate/GP heading. These labels are provisional and still need user review.

The expanded set has 21 questions and 63 independently worded query variants across Basic Roleplaying, Cairn Player, and Cairn Warden. It was evaluated against the same saved 1,785 chunks; no PDFs were re-extracted.

| Search view | Query variants complete at top 10 | Complete at top 20 | Mean delivered text at top 20 |
|---|---:|---:|---:|
| Existing whole chunks, dense | 54/63 (85.7%) | 54/63 (85.7%) | 10,366 chars |
| 180-word prose pieces, whole tables, dense | 51/63 (81.0%) | 54/63 (85.7%) | 7,213 chars |
| 180-word prose pieces, table rows | 49/63 (77.8%) | 54/63 (85.7%) | 7,683 chars |
| Paragraph pieces + whole tables, title prefix | 28/36 on the original subset | 29/36 on the original subset | 6,136 chars on that subset |

The first two rows show the trade-off: paragraph pieces cut delivered context by about 30% at top 20 but do not raise the complete-answer rate. Splitting table rows did not improve top-20 recall and reduced top-10 recall. Keep tables whole.

A generic full-corpus term-frequency/inverse-document-frequency ranker fused with dense paragraph-piece ranks (RRF constant 60, term weight 1) reached 47/63 at top 10 and 49/63 at top 20, below dense paragraph pieces alone (51/63 and 54/63). At top 20 it delivered 12,259 characters on average, versus 7,213. Reject this term-ranking variant.

The expanded sample does not confirm the earlier 33/36 in-sample result: that result came from the original 12 questions only. On the broader provisional sample, no tested method reaches 90% across paraphrased questions. The candidate gold files are in `expanded-gold-v1/`; all 9 new cases passed the original-PDF page and extracted-chunk provenance checks, but remain unapproved.

At top 20, exact paraphrase success by book for paragraph pieces was Basic Roleplaying 17/21, Cairn Player 18/21, and Cairn Warden 19/21. The nine failed query variants have their expected quote in the corresponding chunk's `embedding_text`; therefore these measured misses are retrieval-rank failures, not missing source text in those chunks.

On the 21 original question wordings, paragraph pieces with whole tables reached 20/21 complete questions (95.2%) and 29/30 required facts at top 20, versus 19/21 and 27/30 for whole-chunk dense retrieval. That result is encouraging but not enough to claim the goal: paraphrase coverage stayed at 54/63 (85.7%), and the labels are provisional.

## Experiment: split bullets and numbered items into separate retrieval pieces

The misses suggested that list entries can be buried in nearby text, so a generic line-start marker rule was tested on all three books: bullet glyphs and numbered-list markers began separate pieces; other prose was still packed to at most 180 words; tables stayed whole. This made 5,546 retrieval views from 1,785 chunks, including 2,991 list-item views (2,640 views in the paragraph-and-whole-table candidate). It required 5,392 unique embedding inputs.

| Measure | 180-word prose pieces, whole tables | List-item boundary split |
|---|---:|---:|
| Paraphrase questions complete at top 10 | 51/63 (81.0%) | 40/63 (63.5%) |
| Paraphrase questions complete at top 20 | 54/63 (85.7%) | 44/63 (69.8%) |
| Original questions complete at top 20 | 20/21 (95.2%) | 18/21 (85.7%) |
| Mean delivered text at top 20 | 7,213 chars | 3,532 chars |

The marker split reduced the context length but also split evidence needed to answer questions. It is rejected; do not apply it. The test used the local embedding model on CPU, no LLM or GPU. Its ranking calculation took several minutes in the current pure-Python evaluator, so future broad comparisons should optimize the evaluator before adding more candidate views.

## Experiment mechanics

The term-based retrieval experiment tokenized the saved candidate texts, weighted query terms by their rarity in the current book collection, retrieved the top 20 text matches, and combined those ranks with the top 20 dense ranks using reciprocal-rank fusion. It has no rulebook names, page numbers, or question-specific exceptions. It uses no BM25 implementation, language model, or additional embedding call.

The 180-word run artifacts are in `/tmp/dnd-preprocessing-e2e-20261016/retrieval-eval/multi-rulebook-v1/range-aware-v2/`. The 80-word source-range run artifacts are in `/tmp/dnd-preprocessing-e2e-20261016/retrieval-eval/multi-rulebook-v1/range-aware-80-v1/`.

## Follow-up: retrieval depth and page diversity

After rejecting table-row title prefixes, a no-code comparison varied only the number of whole chunks returned by dense retrieval. It used the same 1,785 saved chunks, all 63 paraphrase queries, and the three books; no PDF extraction or new embeddings were needed for this replay. The original production gateway currently requests 30 candidates.

| Whole chunks returned | Complete paraphrase queries | Required facts | Mean delivered characters |
|---:|---:|---:|---:|
| 20 | 54/63 (85.7%) | 78/90 | 10,366 |
| 30 | 55/63 (87.3%) | 79/90 | 16,121 |
| 40 | 57/63 (90.5%) | 81/90 | 22,366 |

Per-book query coverage at 30 versus 40 was Basic Roleplaying 17/21 → 18/21, Cairn Player 18/21 → 19/21, and Cairn Warden 20/21 → 20/21. The aggregate 90.5% therefore does not mean every book reached 90%; Basic Roleplaying remained at 85.7%. The gain also adds about 6,245 characters per query on average, and the offline test returns all selected chunks while the product gateway reranks and caps the final set at 30. This is a promising candidate-pool change, not a proven production improvement; do not apply the limit change until its effect on reranking input size and latency is checked.

A second no-code test limited returned chunks by source page to reduce repeated material: at top 40, a maximum of 3 chunks per page reached 56/63 (88.9%) and a maximum of 2 reached 51/63 (81.0%), compared with 57/63 without a page cap. The page cap did not meaningfully reduce context size and hurt evidence coverage, so reject it.

Filtering the saved all-book top-100 rankings by the gold case's source book was also simulated, since the product can receive a user-selected document scope. At top 20 this changed coverage from 54/63 to 55/63, but one query had fewer than 20 same-book candidates in the saved top-100 list. At top 30 it showed 57/63, but 17 queries had fewer than 30 same-book candidates, so that result is incomplete and cannot be treated as a valid top-30 comparison. The available data does not show a meaningful gain from source-book filtering alone.

A four-book replay was attempted with the saved D&D Basic Rules 2018 extraction added to the three-book corpus. CPU-only embedding was requested, but the first batch of 24 out of 2,541 distinct inputs took about 2 minutes 40 seconds. The run was stopped before ranking; it produced no score and no production change. This is recorded as a cost-limited, inconclusive attempt rather than a quality result.

An existing independent D&D Basic Rules 2018 run made it possible to compare that fourth book without new embedding work. Using its 12 source-reviewed questions and saved rankings, whole-chunk retrieval completed 9/12 questions at top 5 and 11/12 at top 20. Paragraph pieces with whole tables reached 11/12 at top 5 and top 20. Paragraph pieces plus table-row views reached 12/12 at top 5 and top 20. However, that same paragraph-plus-row method reached only 49/63 at top 10 and 54/63 at top 20 on the other three books under the stricter exact-quote metric. This single-book gain does not generalize, so the row method remains rejected. The D&D run uses chunk-evidence labels rather than the exact-quote labels used for the three-book set; the rates are reported separately and should not be pooled.

The paragraph-only approach (about 180 words per retrieval piece, tables left intact) is more consistent across the available runs. On the original 21 questions from the three-book corpus, exact-quote completion at top 20 rose from 19/21 (27/30 required facts) with whole chunks to 20/21 (29/30) with paragraph pieces; mean retrieved text fell from 11,712 to 8,360 characters. At top 5 it was tied at 17/21, and at top 10 it was slightly lower (17/21 versus 18/21), so the gain depends on allowing 20 retrieval pieces. In the independent D&D run, paragraph pieces with whole tables improved the saved chunk-evidence result at top 5 from 9/12 to 11/12 and matched whole chunks at top 20 (11/12). A descriptive sum would be 31/33 at top 20, but this must not be reported as one pooled score because the three-book labels require exact quoted spans while D&D uses reviewed chunk evidence groups. This remains a promising candidate; the production splitter change below has not yet been scored for retrieval accuracy.

The table-row title-prefix idea remains rejected and was never applied to production code. No production retrieval settings changed in these follow-up experiments.

## Prose chunk limit implementation and time/quality sample (2026-10-16)

The Python splitter now applies a separate 350-token maximum to rule and narrative chunks, while tables keep the previous 500-token limit and are still split only at row boundaries. Atomic entries such as spells and monster data retain the existing limit. The default and D&D rulebook profiles both set this value. The Java publication contract now uses `rag-preprocessing-v2`, so existing rulebook indexes must be reprocessed to use the new chunk layout.

Applying the production `ChunkSplitter` to all saved chunks from the three books gives these corpus sizes:

| Prose token maximum | Resulting chunks | Increase over current |
|---:|---:|---:|
| Baseline (500) | 1,785 | — |
| 450 | 1,907 | 6.8% |
| 400 | 1,945 | 9.0% |
| **350** | **1,999** | **12.0%** |
| 300 | 2,062 | 15.5% |
| 250 | 2,221 | 24.4% |
| 220 | 2,374 | 33.0% |
| 180 | 2,645 | 48.2% |

Search accuracy was compared on a fixed cross-book sample: every page referenced by the 12 source-verified questions, plus six deterministically selected non-answer pages per book (27 pages total; 91 original chunks). The same 12 questions and 36 paraphrases were used for each limit. Three limits were embedded in the first run (500/300/180, 243 unique inputs, 239.69 seconds); three more in the second (450/400/350, 213 unique inputs, 228.42 seconds). Both runs used CPU-only `qwen3-embedding:0.6b`.

At top 20, 500/450/400/350/300 each found all 12 original questions and all 36 paraphrases. The 180 limit found all 12 original questions but only 35/36 paraphrases. At top 5, the 350 limit found 11/12 original questions and 30/36 paraphrases; 180 found 12/12 and 31/36. For the 36 paraphrases at top 10, every limit found 35/36, while mean retrieved text fell from 8,777 characters at 500 to 6,920 at 350 and 5,162 at 180. The 350 limit was the provisional compromise from this sample; the full-corpus comparison below also tested 300.

This first run is a time/quality sample, not a full-book accuracy claim: its candidate pool contains only 27 pages. A full-corpus search evaluation followed below.

## Full-corpus evaluation of the 350-token policy (2026-10-16)

The saved full corpus from Basic Roleplaying, Cairn Player, and Cairn Warden was re-split with the production splitter: 1,785 input chunks became 1,999 chunks (163 rule, 109 whole-table, and 1,727 other chunks). All 1,999 were embedded and searched for 21 source-checked questions plus 63 paraphrases. These questions cover 30 required evidence facts. Labels remain provisional and are not user-approved. Scoring checks the quoted source evidence inside the returned text, not merely whether a related source chunk was retrieved.

| Limit | Original questions complete (top 20) | Paraphrases complete (top 20) | Required facts found (paraphrases, top 20) | Mean returned text (paraphrases, top 20) |
|---|---:|---:|---:|---:|
| Existing 500-token chunks | 19/21 | 54/63 | 78/90 | 10,366 characters |
| **350-token chunks** | **19/21** | **54/63** | **78/90** | **9,505 characters** |

At top 20, the 350-token run matched the full-corpus 500-token baseline on exact-evidence recall while returning about 8% less text for paraphrase queries and using 12% more chunks. On original questions it returned about 8% less text as well. Raising retrieval depth to 40 increased paraphrase completion to 56/63 and evidence facts to 80/90, but nearly doubled mean returned text to 19,984 characters. Per-book top-20 paraphrase results were 17/21 for Basic Roleplaying, 18/21 for Cairn Player, and 19/21 for Cairn Warden.

The 2,052 distinct chunk/query texts were embedded in 74.31 seconds with local `qwen3-embedding:0.6b` on the RTX 3060 GPU (`num_thread=16`). This is an offline evaluation timing, not an end-to-end production reindex measurement. Reproduction artifacts, including rankings and the embedding cache, are under `/tmp/dnd-preprocessing-e2e-20261016/retrieval-eval/prose-limit-350-full-v1/`; the splitter output and input hashes are in the neighboring `prose-limit-350-v2/manifest.json`.

This supports the 350-token limit as a context-size compromise, not as a search-accuracy improvement: top-20 accuracy tied the prior 500-token index. The full-corpus search includes all saved chunks from the three PDFs, but relevance was tested only with these 21 questions and 63 paraphrases; it does not mean every page had a gold question. The gold labels still need user approval.

## Full-corpus evaluation of the 300-token policy

The same three-book input was split at a 300-token prose maximum, producing 2,062 chunks, including 109 whole tables. The same 21 questions, 63 paraphrases, and exact quoted-evidence scoring were used. The full search candidate pool contained every saved chunk from the three books.

| Prose maximum | Original questions complete (top 20) | Paraphrases complete (top 20) | Required facts found (paraphrases, top 20) | Mean returned text (paraphrases, top 20) |
|---|---:|---:|---:|---:|
| 500-token baseline | 19/21 | 54/63 | 78/90 | 10,366 characters |
| 350 tokens | 19/21 | 54/63 | 78/90 | 9,505 characters |
| **300 tokens** | **19/21** | **54/63** | **78/90** | **9,329 characters** |

At top 20, 300 and 350 returned the same successful and failed questions. The 300 limit reduced returned text only 1.9% beyond 350 while increasing chunk count by 3.2%. At top 10, 300 completed one fewer paraphrase than 350 (53/63 versus 54/63); at top 40, it completed one more (57/63 versus 56/63) while still returning slightly less text. These small differences do not establish a consistent accuracy gain for 300.

The run reused 1,685 identical cached vectors from the 350 evaluation and computed 430 new vectors in 22.78 seconds on the RTX 3060 GPU. This is not a from-scratch reindex timing. The saved input export lacks source spans, so source-coordinate preservation was not evaluated in this run. The output and rankings are under `/tmp/dnd-preprocessing-e2e-20261016/retrieval-eval/prose-limit-300-full-v1/`.

The 300-token limit is now set in the working configuration for this trial. Results still favor 350 as the balanced choice: 300 adds 3.2% more chunks for only 1.9% less returned context, ties top-20 exact-evidence recall, and is slightly worse at top 10. Keep the gold-label and three-book coverage limitations above in mind.

## Adoption decision and next boundary

No retrieval-service or search-query change was applied. Replacing whole-chunk indexing with paragraph-piece indexing, adding source-range-preserving child retrieval, and adding a corpus-wide text retrieval path touches preprocessing, indexing, persistence, and authorized search. That is a cross-service change, and those candidates did not improve paraphrase evidence recall on the expanded set. Raising the dense candidate count to 40 crossed 90% in aggregate, but costs more context, stays below 90% on Basic Roleplaying, and has not been replayed through the product reranker; it remains an unadopted candidate. The 350-token limit remains the better measured balance than 300; 300 is present in the working configuration as the requested trial. The gold set still needs user approval and broader book coverage.

The active Python extraction path previously used a 500-token maximum for all strategies. A separate prose limit of 350 tokens is now implemented and the Java policy version is bumped to `rag-preprocessing-v2`; existing indexed rulebooks must be reprocessed to use it. The unresolved Cairn Player case should be checked against the source and extraction before changing the labels; the current source quote is present in the saved chunk, so current evidence points to retrieval ranking and context grouping rather than missing source text. All results remain provisional and the labels await user approval.
