#!/usr/bin/env python3
"""Compare current chunk vectors with paragraph and header-aware table-row vectors."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import time
import urllib.request
from collections import defaultdict
from pathlib import Path


def _table_views(chunk: dict[str, object]) -> list[str]:
    lines = [line.strip() for line in str(chunk.get("embedding_text", "")).splitlines() if line.strip()]
    if not lines:
        return []
    first = lines[0]
    if ": " in first:
        header, first_row = first.split(": ", 1)
        rows = ([first_row] if first_row.strip() else []) + lines[1:]
    else:
        header, rows = first.rstrip("."), lines[1:]
    if not rows:
        return [f"Table columns: {header}"]
    return [f"Table columns: {header}\nTable row: {row}" for row in rows]


def _prose_views(chunk: dict[str, object], target_words: int = 180) -> list[str]:
    paragraphs = [part.strip() for part in re.split(r"\n\s*\n", str(chunk.get("embedding_text", ""))) if part.strip()]
    if not paragraphs:
        return []
    result: list[str] = []
    current: list[str] = []
    current_words = 0
    for paragraph in paragraphs:
        words = paragraph.split()
        if len(words) > target_words:
            if current:
                result.append("\n\n".join(current))
                current, current_words = [], 0
            sentences = [part.strip() for part in re.split(r"(?<=[.!?])\s+", paragraph) if part.strip()]
            pending: list[str] = []
            pending_words = 0
            for sentence in sentences:
                sentence_words = sentence.split()
                if pending and pending_words + len(sentence_words) > target_words:
                    result.append(" ".join(pending))
                    pending, pending_words = [], 0
                if len(sentence_words) > target_words:
                    if pending:
                        result.append(" ".join(pending))
                        pending, pending_words = [], 0
                    for offset in range(0, len(sentence_words), target_words):
                        result.append(" ".join(sentence_words[offset:offset + target_words]))
                else:
                    pending.append(sentence)
                    pending_words += len(sentence_words)
            if pending:
                result.append(" ".join(pending))
            continue
        if current and current_words + len(words) > target_words:
            result.append("\n\n".join(current))
            current, current_words = [], 0
        current.append(paragraph)
        current_words += len(words)
    if current:
        result.append("\n\n".join(current))
    return result


def _views(chunks: list[dict[str, object]]) -> tuple[list[tuple[str, str, str, str]], list[tuple[str, str, str, str]]]:
    """Return child views as (parent chunk, child id, kind, text)."""
    prose: list[tuple[str, str, str, str]] = []
    tables: list[tuple[str, str, str, str]] = []
    for chunk in chunks:
        parent = str(chunk["chunk_id"])
        if chunk.get("content_type") == "table":
            rows = _table_views(chunk)
            tables.extend((parent, f"{parent}::table-row-{index}", "table_row", text)
                          for index, text in enumerate(rows, 1))
        else:
            parts = _prose_views(chunk)
            prose.extend((parent, f"{parent}::paragraph-{index}", "paragraph", text)
                         for index, text in enumerate(parts or [str(chunk["embedding_text"])], 1))
    return prose, tables


def _cosine(a: list[float], b: list[float]) -> float:
    denom = math.sqrt(sum(x * x for x in a) * sum(y * y for y in b))
    return sum(x * y for x, y in zip(a, b)) / denom if denom else 0.0


def _score_variant(vectors: dict[str, list[float]], views: list[tuple[str, str, str, str]], queries: list[dict[str, object]], k: int = 20) -> dict[str, list[dict[str, object]]]:
    query_results = {}
    for query in queries:
        q = vectors[str(query["question"])]
        ranked_views = sorted(
            ((parent, child_id, kind, text, _cosine(q, vectors[text]))
             for parent, child_id, kind, text in views),
            key=lambda item: (-item[4], item[1]),
        )[:k]
        query_id = str(query.get("query_id", query["case_id"]))
        query_results[query_id] = [
            {"rank": i, "chunk_id": parent, "child_id": child_id, "kind": kind,
             "text": text, "score": round(score, 6)}
            for i, (parent, child_id, kind, text, score) in enumerate(ranked_views, 1)
        ]
    return query_results


def _score_parent_variant(vectors: dict[str, list[float]], views: list[tuple[str, str, str, str]],
                          queries: list[dict[str, object]], k: int = 20) -> dict[str, list[dict[str, object]]]:
    """Keep the legacy score: each parent is ranked by its best child vector."""
    output = {}
    for query in queries:
        q = vectors[str(query["question"])]
        best: dict[str, float] = defaultdict(lambda: -1.0)
        for parent, _child_id, _kind, text in views:
            best[parent] = max(best[parent], _cosine(q, vectors[text]))
        ranked = sorted(best.items(), key=lambda item: (-item[1], item[0]))[:k]
        query_id = str(query.get("query_id", query["case_id"]))
        output[query_id] = [
            {"rank": index, "chunk_id": parent, "score": round(score, 6)}
            for index, (parent, score) in enumerate(ranked, 1)
        ]
    return output


def _normalized(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip().casefold()


def _evidence_metrics(results: dict[str, list[dict[str, object]]], queries: list[dict[str, object]],
                      labels_by_case: dict[str, dict[str, object]], chunks_by_id: dict[str, dict[str, object]],
                      k: int, context: str) -> dict[str, object]:
    complete_facts = total_facts = complete_questions = 0
    context_chars = 0
    for query in queries:
        label = labels_by_case[str(query["case_id"])]
        ranked = results[str(query.get("query_id", query["case_id"]))][:k]
        if context == "retrieved_children":
            delivered = "\n\n".join(str(row["text"]) for row in ranked)
        elif context == "parent_embedding_text":
            seen = set()
            pieces = []
            for row in ranked:
                parent = str(row["chunk_id"])
                if parent not in seen:
                    seen.add(parent)
                    pieces.append(str(chunks_by_id[parent]["embedding_text"]))
            delivered = "\n\n".join(pieces)
        else:
            raise ValueError(f"unknown context mode: {context}")
        delivered_normalized = _normalized(delivered)
        context_chars += len(delivered)
        fact_hits = []
        for fact in label["required_evidence_facts"]:
            hit = any(all(_normalized(str(span["quote"])) in delivered_normalized
                          for span in alternative["spans"])
                      for alternative in fact["acceptable_spans"])
            fact_hits.append(hit)
        total_facts += len(fact_hits)
        complete_facts += sum(fact_hits)
        complete_questions += bool(fact_hits) and all(fact_hits)
    query_count = len(queries)
    return {
        "all_required_facts_found": {"found": complete_facts, "total": total_facts,
                                     "rate": complete_facts / total_facts if total_facts else 0.0},
        "all_facts_per_question_found": {"count": complete_questions, "total": query_count,
                                         "rate": complete_questions / query_count if query_count else 0.0},
        "mean_context_characters": context_chars / query_count if query_count else 0.0,
    }


def _self_check() -> None:
    table = {"chunk_id": "t1", "content_type": "table", "embedding_text": "Range, Damage.\n80/320, 1d6."}
    assert _table_views(table) == ["Table columns: Range, Damage\nTable row: 80/320, 1d6."]
    prose = {"chunk_id": "p1", "content_type": "prose", "embedding_text": "First paragraph.\n\nSecond paragraph."}
    assert _prose_views(prose) == ["First paragraph.\n\nSecond paragraph."]
    test_chunks = {"parent": {"source_text": "Unrelated material. Required first fact. Required second fact."}}
    test_labels = {"case": {"required_evidence_facts": [
        {"fact_id": "one", "acceptable_spans": [{"spans": [{"quote": "Required first fact."}]}]},
        {"fact_id": "two", "acceptable_spans": [
            {"spans": [{"quote": "Required second fact."}]},
            {"spans": [{"quote": "Alternate verified wording."}]},
        ]},
    ]}}
    test_queries = [{"case_id": "case", "query_id": "q"}]
    unrelated = {"q": [{"chunk_id": "parent", "text": "Unrelated material."}]}
    partial = {"q": [{"chunk_id": "parent", "text": "Required first fact."}]}
    complete = {"q": [{"chunk_id": "parent", "text": "Required first fact. Required second fact."}]}
    alternative = {"q": [{"chunk_id": "parent", "text": "Required first fact. Alternate verified wording."}]}
    assert _evidence_metrics(unrelated, test_queries, test_labels, test_chunks, 1, "retrieved_children")["all_facts_per_question_found"]["count"] == 0
    assert _evidence_metrics(partial, test_queries, test_labels, test_chunks, 1, "retrieved_children")["all_required_facts_found"]["found"] == 1
    assert _evidence_metrics(complete, test_queries, test_labels, test_chunks, 1, "retrieved_children")["all_facts_per_question_found"]["count"] == 1
    assert _evidence_metrics(alternative, test_queries, test_labels, test_chunks, 1, "retrieved_children")["all_facts_per_question_found"]["count"] == 1


def _metrics(results: dict[str, list[dict[str, object]]], queries: list[dict[str, object]], labels_by_case: dict[str, dict[str, object]]) -> dict[str, object]:
    cutoffs = (1, 3, 5, 10, 20)
    output = {}
    for k in cutoffs:
        complete = group_hits = total_groups = 0
        for query in queries:
            label = labels_by_case[str(query["case_id"])]
            query_id = str(query.get("query_id", query["case_id"]))
            ranked = {str(row["chunk_id"]) for row in results[query_id][:k]}
            groups = label["answer_evidence_groups"]
            hits = [bool(ranked.intersection(group["acceptable_chunk_ids"])) for group in groups]
            complete += bool(hits) and all(hits)
            group_hits += sum(hits)
            total_groups += len(hits)
        output[str(k)] = {
            "all_question_facts_found": {"count": complete, "total": len(queries), "rate": complete / len(queries)},
            "evidence_group_coverage": {"found": group_hits, "total": total_groups,
                                        "rate": group_hits / total_groups if total_groups else 0.0},
        }
    return output


def _case_stability(results: dict[str, list[dict[str, object]]], queries: list[dict[str, object]], labels_by_case: dict[str, dict[str, object]]) -> dict[str, object]:
    output = {}
    for k in (1, 3, 5, 10, 20):
        hits_by_case: dict[str, list[bool]] = defaultdict(list)
        for query in queries:
            label = labels_by_case[str(query["case_id"])]
            ranked = {str(row["chunk_id"]) for row in results[str(query["query_id"])][:k]}
            hits_by_case[str(query["case_id"])].append(all(
                ranked.intersection(group["acceptable_chunk_ids"]) for group in label["answer_evidence_groups"]
            ))
        stable = sum(bool(hits) and all(hits) for hits in hits_by_case.values())
        output[str(k)] = {
            "all_paraphrases_found": {"count": stable, "total": len(hits_by_case), "rate": stable / len(hits_by_case)},
            "per_case_successes": {case: sum(hits) for case, hits in sorted(hits_by_case.items())},
        }
    return output


def _embed_all(texts: list[str], model: str, endpoint: str) -> tuple[dict[str, list[float]], float]:
    unique = list(dict.fromkeys(texts))
    vectors: dict[str, list[float]] = {}
    started = time.monotonic()
    for offset in range(0, len(unique), 24):
        batch = unique[offset:offset + 24]
        body = json.dumps({"model": model, "input": batch, "keep_alive": "10m", "options": {"num_gpu": 0}}).encode()
        request = urllib.request.Request(endpoint, data=body, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=180) as response:
            payload = json.loads(response.read())
        values = payload.get("embeddings")
        if not isinstance(values, list) or len(values) != len(batch):
            raise RuntimeError(f"embedding count mismatch expected={len(batch)} actual={len(values) if isinstance(values, list) else 'invalid'}")
        for text, vector in zip(batch, values):
            parsed = [float(value) for value in vector]
            if not parsed or any(not math.isfinite(value) for value in parsed):
                raise RuntimeError("embedding response contains an invalid vector")
            vectors[text] = parsed
        print(f"embedded {min(offset + len(batch), len(unique))}/{len(unique)}", flush=True)
    return vectors, time.monotonic() - started


def main() -> int:
    _self_check()
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--paraphrases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", default="qwen3-embedding:0.6b")
    parser.add_argument("--endpoint", default="http://127.0.0.1:11434/api/embed")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    chunks = [json.loads(line) for line in (args.run / "chunks.jsonl").read_text().splitlines() if line.strip()]
    labels = [json.loads(line) for line in args.labels.read_text().splitlines() if line.strip()]
    paraphrase_rows = [json.loads(line) for line in args.paraphrases.read_text().splitlines() if line.strip()]
    labels_by_case = {str(label["case_id"]): label for label in labels}
    assert len(labels_by_case) == len(labels)
    assert {str(row["case_id"]) for row in paraphrase_rows} == set(labels_by_case)
    assert all(len(row["paraphrases"]) == 3 for row in paraphrase_rows)
    original_queries = [{"case_id": str(label["case_id"]), "question": str(label["question"])} for label in labels]
    paraphrase_queries = [
        {"case_id": str(row["case_id"]), "query_id": f"{row['case_id']}__p{index}", "question": str(question)}
        for row in paraphrase_rows for index, question in enumerate(row["paraphrases"], 1)
    ]
    all_queries = original_queries + paraphrase_queries
    prose, table_rows = _views(chunks)
    parents = [(str(chunk["chunk_id"]), str(chunk["chunk_id"]), "parent", str(chunk["embedding_text"])) for chunk in chunks]
    chunk_by_id = {str(chunk["chunk_id"]): chunk for chunk in chunks}
    for chunk in chunks:
        assert str(chunk["chunk_id"]) in chunk_by_id
    for parent, _child_id, kind, text in table_rows:
        assert kind == "table_row" and text.startswith("Table columns:") and parent in chunk_by_id
    proposed = prose + table_rows
    variants = {"baseline": parents, "paragraph_children": prose + [v for v in parents if chunk_by_id[v[0]].get("content_type") == "table"],
                "table_row_children": table_rows + [v for v in parents if chunk_by_id[v[0]].get("content_type") != "table"],
                "paragraph_and_table_row_children": proposed}
    for name, views in variants.items():
        covered = {parent for parent, _, _, _ in views}
        assert covered == set(chunk_by_id), f"{name} does not map every original chunk"
    assert all("required_evidence_facts" in label for label in labels), "labels need source-range evidence facts"
    text_inputs = [text for views in variants.values() for _, _, _, text in views]
    text_inputs.extend(str(query["question"]) for query in all_queries)
    unique_count = len(set(text_inputs))
    print(json.dumps({"stage": "prepared", "chunkCount": len(chunks), "proseChildCount": len(prose),
                      "tableRowChildCount": len(table_rows), "uniqueEmbeddingInputs": unique_count,
                      "model": args.model, "outcome": "dry_run" if args.dry_run else "started"}, ensure_ascii=False), flush=True)
    if args.dry_run:
        return 0
    vectors, elapsed = _embed_all(text_inputs, args.model, args.endpoint)
    output = {"status": "completed", "model": args.model, "chunk_count": len(chunks),
              "prose_child_count": len(prose), "table_row_child_count": len(table_rows),
              "unique_embedding_inputs": unique_count, "embedding_elapsed_seconds": round(elapsed, 2),
              "source_chunks_sha256": hashlib.sha256((args.run / "chunks.jsonl").read_bytes()).hexdigest(),
              "label_file": str(args.labels), "label_status": "source-verified provisional; not user-approved",
              "paraphrase_file": str(args.paraphrases), "paraphrase_query_count": len(paraphrase_queries),
              "variants": {}}
    detail_output = {}
    for name, views in variants.items():
        original_view_results = _score_variant(vectors, views, original_queries)
        paraphrase_view_results = _score_variant(vectors, views, paraphrase_queries)
        original_results = _score_parent_variant(vectors, views, original_queries)
        paraphrase_results = _score_parent_variant(vectors, views, paraphrase_queries)
        output["variants"][name] = {
            "view_count": len(views),
            "parent_chunk_metrics": {
                "original_query_metrics": _metrics(original_results, original_queries, labels_by_case),
                "paraphrase_query_metrics": _metrics(paraphrase_results, paraphrase_queries, labels_by_case),
                "paraphrase_case_stability": _case_stability(paraphrase_results, paraphrase_queries, labels_by_case),
            },
            "evidence_context_metrics": {
                context: {
                    "original_queries": {
                        str(k): _evidence_metrics(original_view_results, original_queries, labels_by_case, chunk_by_id, k, context)
                        for k in (1, 3, 5, 10, 20)
                    },
                    "paraphrase_queries": {
                        str(k): _evidence_metrics(paraphrase_view_results, paraphrase_queries, labels_by_case, chunk_by_id, k, context)
                        for k in (1, 3, 5, 10, 20)
                    },
                }
                for context in ("retrieved_children", "parent_embedding_text")
            },
        }
        detail_output[name] = {
            "original_queries": original_results,
            "paraphrase_queries": paraphrase_results,
            "original_child_rankings": original_view_results,
            "paraphrase_child_rankings": paraphrase_view_results,
            "metrics": output["variants"][name],
        }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "comparison.json").write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    (args.output / "rankings.json").write_text(json.dumps(detail_output, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"stage": "complete", "output": str(args.output), "elapsedSeconds": round(elapsed, 2),
                      "variants": output["variants"], "outcome": "success"}, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
