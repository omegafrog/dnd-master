import json

import pytest

from preprocessing_agent.eval.structure import StructureEvaluationError, evaluate_structure


HASH = "a" * 64


def test_structure_scores_missing_blocks_heading_and_empty_table_cells(tmp_path):
    version = {"source_sha256": HASH, "pages": [{
        "page_number": 1, "page_classification": "text-native",
        "blocks": [{"block_id": "p1-b1", "text": "Heading", "bbox": [0, 0, 10, 10]},
                   {"block_id": "p1-b2", "text": "Body", "bbox": [0, 12, 10, 20]}],
        "heading_associations": [{"heading_block_id": "p1-b1", "level": 2}],
        "layout": {"ordered_block_ids": ["p1-b1", "p1-b2"]},
        "tables": [{"bbox": [20, 0, 40, 20], "rows": [[{"text": "A"}, {"text": ""}]]}],
    }]}
    gold = {"source_sha256": HASH, "page_number": 1, "page_type": "text-native",
            "blocks": [{"id": "h", "text": "Heading", "bbox": [0, 0, 10, 10]},
                       {"id": "b", "text": "Body", "bbox": [0, 12, 10, 20]},
                       {"id": "missing", "text": "Missing", "bbox": [50, 50, 60, 60]}],
            "reading_order": ["h", "b"], "headings": [{"id": "heading-h", "block_id": "h", "level": 1}],
            "tables": [{"bbox": [20, 0, 40, 20], "rows": [["A", ""]]}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")
    report = evaluate_structure(version_path, gold_path)
    assert report["blocks"] == {"precision": 1., "recall": 2 / 3, "matched": 2,
                                "matched_predicted": 2, "predicted": 2, "gold": 3}
    assert report["headings"]["level_accuracy"] == 0
    assert report["tables"]["cell_exact_rate"] == 1
    assert report["reading_order_pairwise_agreement"] == 1


def test_structure_rejects_source_hash_mismatch_and_duplicate_pages(tmp_path):
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps({"source_sha256": HASH, "pages": []}))
    row = {"source_sha256": "b" * 64, "page_number": 1}
    gold_path.write_text(json.dumps(row) + "\n")
    with pytest.raises(StructureEvaluationError, match="SHA mismatch"):
        evaluate_structure(version_path, gold_path)
    row["source_sha256"] = HASH
    gold_path.write_text(json.dumps(row) + "\n" + json.dumps(row) + "\n")
    with pytest.raises(StructureEvaluationError, match="duplicate"):
        evaluate_structure(version_path, gold_path)


def test_unannotated_categories_are_null(tmp_path):
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps({"source_sha256": HASH, "pages": [{"page_number": 1, "blocks": [{"block_id": "x", "text": "x", "bbox": [0, 0, 1, 1]}]}]}))
    gold_path.write_text(json.dumps({"source_sha256": HASH, "page_number": 1}) + "\n")
    report = evaluate_structure(version_path, gold_path)
    assert report["blocks"]["precision"] is None
    assert report["tables"]["recall"] is None


def test_structure_handles_unselected_columns_and_counts_missing_table_cells(tmp_path):
    version = {"source_sha256": HASH, "pages": [{
        "page_number": 1, "page_classification": "text-native", "blocks": [],
        "layout": {"regions": [{"region_id": "r1", "bbox": [0, 0, 100, 100]}],
                   "profiles": [{"region_id": "r1", "selected": None}]},
        "tables": [{"bbox": [0, 0, 100, 50], "rows": [{"cells": [
            {"row_index": 0, "column_index": 0, "text": "A"},
            {"row_index": 0, "column_index": 2, "text": " "},
        ]}]}],
    }]}
    gold = {"source_sha256": HASH, "page_number": 1,
            "prose_regions": [{"bbox": [0, 0, 100, 100], "columns": [[0, 0, 45, 100], [55, 0, 100, 100]]}],
            "tables": [{"bbox": [0, 0, 100, 50], "rows": [["A", "", ""]]}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["columns"]["count_accuracy"] == 0
    assert report["tables"]["cell_exact_rate"] == 1
    assert report["reading_order_block_coverage"] is None


def test_table_cell_score_ignores_caption_and_fully_empty_separator_rows(tmp_path):
    version = {"source_sha256": HASH, "pages": [{
        "page_number": 1, "blocks": [],
        "tables": [{"bbox": [0, 0, 100, 100], "rows": [
            {"cells": [{"row_index": 0, "column_index": 0, "text": "1"},
                       {"row_index": 0, "column_index": 1, "text": "Oak"}]},
            {"cells": [{"row_index": 1, "column_index": 0, "text": " "},
                       {"row_index": 1, "column_index": 1, "text": ""}]},
            {"cells": [{"row_index": 2, "column_index": 0, "text": "2"},
                       {"row_index": 2, "column_index": 1, "text": "Elm"}]},
        ]}],
    }]}
    gold = {"source_sha256": HASH, "page_number": 1, "tables": [{
        "bbox": [0, 0, 100, 100], "caption": "Trees (d10)",
        "rows": [["1", "Oak"], ["", ""], ["2", "Elm"]],
    }]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["tables"]["cell_exact_rate"] == 1
    assert report["tables"]["grid_shape_exact_rate"] == 0
    assert report["tables"]["shape_diagnostics"] == [{
        "page_number": 1, "table_index": 0, "caption": "Trees (d10)",
        "expected_rows": 2, "actual_rows": 3, "actual_nonempty_rows": 2, "empty_rows": 1,
        "missing_rows": 0, "extra_rows": 1,
        "expected_columns": 2, "actual_columns": 2, "shape_matches": False,
    }]


def test_heading_parent_accuracy_uses_exported_document_tree(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {
        "level": 0, "block_ids": [], "children": [{"level": 1, "block_ids": ["p1-b1"],
            "children": [{"level": 2, "block_ids": ["p1-b2"], "children": []}]}]}},
        "pages": [{"page_number": 1, "blocks": [
            {"block_id": "p1-b1", "text": "Chapter", "bbox": [0, 0, 20, 10]},
            {"block_id": "p1-b2", "text": "Section", "bbox": [0, 12, 20, 22]}],
            "heading_associations": [{"heading_block_id": "p1-b1", "level": 1},
                                     {"heading_block_id": "p1-b2", "level": 2}]}]}
    gold = {"source_sha256": HASH, "page_number": 1,
            "blocks": [{"id": "g1", "text": "Chapter", "bbox": [0, 0, 20, 10]},
                       {"id": "g2", "text": "Section", "bbox": [0, 12, 20, 22]}],
            "headings": [{"id": "h1", "block_id": "g1", "level": 1, "parent_id": None},
                         {"id": "h2", "block_id": "g2", "level": 2, "parent_id": "h1"}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["headings"]["level_accuracy"] == 1
    assert report["headings"]["parent_accuracy"] == 1


def test_unresolved_heading_relations_are_reported_as_abstentions(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {"level": 0, "block_ids": [], "children": [
        {"level": 2, "block_ids": ["p1-b1"], "children": [], "hierarchy_resolved": False},
        {"level": 3, "block_ids": ["p1-b2"], "children": [], "hierarchy_resolved": False}]}},
        "pages": [{"page_number": 1, "blocks": [
            {"block_id": "p1-b1", "text": "Heading", "bbox": [0, 0, 20, 10]},
            {"block_id": "p1-b2", "text": "Subheading", "bbox": [0, 12, 30, 22]}],
            "heading_associations": [{"heading_block_id": "p1-b1", "level": 2, "hierarchy_resolved": False},
                                     {"heading_block_id": "p1-b2", "level": 3, "hierarchy_resolved": False}]}]}
    gold = {"source_sha256": HASH, "page_number": 1,
            "blocks": [{"id": "g1", "text": "Heading", "bbox": [0, 0, 20, 10]},
                       {"id": "g2", "text": "Subheading", "bbox": [0, 12, 30, 22]}],
            "headings": [{"id": "h1", "block_id": "g1", "level": 2},
                         {"id": "h2", "block_id": "g2", "level": 3, "parent_id": "h1"}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["headings"]["level_accuracy"] is None
    assert report["headings"]["level_resolution_coverage"] == 0
    assert report["headings"]["parent_accuracy"] is None
    assert report["headings"]["parent_resolution_coverage"] == 0


def test_heading_anchor_matches_title_inside_a_combined_heading_and_body_block(tmp_path):
    version = {"source_sha256": HASH, "pages": [{
        "page_number": 1,
        "blocks": [{"block_id": "p1-b1", "text": "Purpose Consider the dungeon’s original purpose.",
                    "bbox": [10, 10, 90, 40]}],
        "heading_associations": [{"heading_block_id": "p1-b1", "level": 3}],
    }]}
    gold = {"source_sha256": HASH, "page_number": 1,
            "blocks": [{"id": "combined", "text": "Purpose Consider the dungeon’s original purpose.",
                        "bbox": [10, 10, 90, 40]}],
            "headings": [{"id": "purpose", "anchor": {"text": "Purpose", "bbox": [10, 10, 31, 20]}, "level": 3}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["headings"]["precision"] == 1
    assert report["headings"]["recall"] == 1
    assert report["headings"]["level_accuracy"] == 1


def test_block_alignment_accepts_one_gold_paragraph_split_across_extracted_blocks(tmp_path):
    version = {"source_sha256": HASH, "pages": [{"page_number": 1,
        "blocks": [{"block_id": "p1-a", "text": "The party enters", "bbox": [0, 0, 40, 10]},
                   {"block_id": "p1-b", "text": "the old tower.", "bbox": [0, 11, 40, 20]}],
        "layout": {"ordered_block_ids": ["p1-a", "p1-b"]}}]}
    gold = {"source_sha256": HASH, "page_number": 1,
            "blocks": [{"id": "paragraph", "text": "The party enters the old tower.", "bbox": [0, 0, 40, 20]}],
            "reading_order": ["paragraph"]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["blocks"]["recall"] == 1
    assert report["reading_order_block_coverage"] == 1


def test_cross_page_section_continuation_uses_context_anchor_outside_sample(tmp_path, caplog, monkeypatch):
    version = {"source_sha256": HASH, "document_tree": {"root": {"node_id": "root", "title": "", "block_ids": [],
        "children": [{"node_id": "step-age", "title": "Step Three: Age", "block_ids": ["p6-heading", "p7-rules"], "children": []}]}},
        "pages": [
            {"page_number": 6, "blocks": [{"block_id": "p6-heading", "text": "Step Three: Age", "bbox": [10, 10, 80, 20]}]},
            {"page_number": 7, "blocks": [{"block_id": "p7-rules", "text": "For every year added", "bbox": [10, 10, 100, 30]}]},
        ]}
    gold = {"source_sha256": HASH, "page_number": 7,
        "blocks": [{"id": "age-rules", "text": "For every year added", "bbox": [10, 10, 100, 30]}],
        "section_continuations": [{"id": "age-page-break", "section_relation": "SAME_SECTION", "content_continuity": "NONE", "source": {"page_number": 6,
            "anchor": {"text": "Step Three: Age", "bbox": [10, 10, 80, 20]}},
            "destination": {"page_number": 7, "block_id": "age-rules"}}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "1")
    caplog.set_level(20)

    report = evaluate_structure(version_path, gold_path)

    assert report["section_continuations"]["confusion_counts"]["SAME_SECTION"]["SAME_SECTION"] == 1
    assert report["section_continuations"]["gold_relation_counts"] == {"SAME_SECTION": 1}
    assert "pageBoundaryRelationCount=1" in caplog.text
    assert "pageBoundaryRelationEvaluatedCount=1" in caplog.text


def test_cross_page_continuation_matches_a_text_anchor_inside_a_predicted_block(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {"node_id": "root", "title": "", "block_ids": [],
        "children": [{"node_id": "section", "title": "Age", "block_ids": ["p1-title", "p2-rule"], "children": []}]}},
        "pages": [
            {"page_number": 1, "blocks": [{"block_id": "p1-title", "text": "Age", "bbox": [0, 0, 20, 10]}]},
            {"page_number": 2, "blocks": [{"block_id": "p2-rule", "text": "For each year added to the starting age", "bbox": [0, 0, 100, 10]}]},
        ]}
    gold = {"source_sha256": HASH, "page_number": 1,
            "blocks": [{"id": "title", "text": "Age", "bbox": [0, 0, 20, 10]}],
            "section_continuations": [{"section_relation": "SAME_SECTION", "content_continuity": "NONE", "source": {"page_number": 1, "block_id": "title"},
                "destination": {"page_number": 2, "anchor": {"text": "For each year added", "bbox": [0, 0, 45, 10]}}}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)

    assert report["section_continuations"]["confusion_counts"]["SAME_SECTION"]["SAME_SECTION"] == 1


def test_cross_page_continuation_compares_resolved_section_membership_and_old_gold_stays_compatible(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {"node_id": "root", "block_ids": [], "children": [
        {"node_id": "section", "title": "Creating a Character", "block_ids": ["p6-heading", "p7-rules"], "children": []}]}},
        "pages": [
            {"page_number": 6, "blocks": [{"block_id": "p6-heading", "text": "Step Three: Age", "bbox": [10, 10, 80, 20]}]},
            {"page_number": 7, "blocks": [{"block_id": "p7-rules", "text": "For every year added", "bbox": [10, 10, 100, 30]}]},
        ]}
    gold = {"source_sha256": HASH, "page_number": 7,
        "blocks": [{"id": "age-rules", "text": "For every year added", "bbox": [10, 10, 100, 30]}],
        "section_continuations": [{"section_relation": "SAME_SECTION", "content_continuity": "NONE", "source": {"page_number": 6,
            "anchor": {"text": "Step Three: Age", "bbox": [10, 10, 80, 20]}},
            "destination": {"page_number": 7, "block_id": "age-rules"}}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    report = evaluate_structure(version_path, gold_path)
    assert report["section_continuations"]["confusion_counts"]["SAME_SECTION"]["SAME_SECTION"] == 1

    gold.pop("section_continuations")
    gold_path.write_text(json.dumps(gold) + "\n")
    report = evaluate_structure(version_path, gold_path)
    assert report["section_continuations"]["annotated_boundary_count"] is None


def test_different_section_boundary_is_counted_as_a_negative_example(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {"node_id": "root", "title": "", "block_ids": [],
        "children": [
            {"node_id": "section-a", "title": "First", "block_ids": ["p1-a"], "children": []},
            {"node_id": "section-b", "title": "Second", "block_ids": ["p2-b"], "children": []},
        ]}}, "pages": [
            {"page_number": 1, "blocks": [{"block_id": "p1-a", "text": "First", "bbox": [0, 0, 20, 10]}]},
            {"page_number": 2, "blocks": [{"block_id": "p2-b", "text": "Second", "bbox": [0, 0, 20, 10]}]},
        ]}
    gold = [
        {"source_sha256": HASH, "page_number": 1,
         "blocks": [{"id": "a", "text": "First", "bbox": [0, 0, 20, 10]}],
         "section_continuations": [{"source": {"page_number": 1, "block_id": "a"},
             "destination": {"page_number": 2, "block_id": "b"}, "section_relation": "DIFFERENT_SECTION", "content_continuity": "NONE"}]},
        {"source_sha256": HASH, "page_number": 2,
         "blocks": [{"id": "b", "text": "Second", "bbox": [0, 0, 20, 10]}]},
    ]
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text("".join(json.dumps(row) + "\n" for row in gold))

    report = evaluate_structure(version_path, gold_path)

    assert report["section_continuations"]["gold_relation_counts"] == {"DIFFERENT_SECTION": 1}
    assert report["section_continuations"]["confusion_counts"]["DIFFERENT_SECTION"]["DIFFERENT_SECTION"] == 1
    assert report["section_continuations"]["decision_coverage"] == 1


def test_shared_ancestor_is_not_enough_to_claim_same_section(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {"node_id": "root", "block_ids": [], "children": [
        {"node_id": "chapter", "title": "Chapter", "block_ids": [], "children": [
            {"node_id": "section-a", "title": "First subsection", "block_ids": ["p1"], "children": []},
            {"node_id": "section-b", "title": "Second subsection", "block_ids": ["p2"], "children": []},
        ]}]}}, "pages": [
        {"page_number": 1, "blocks": [{"block_id": "p1", "text": "First", "bbox": [0, 0, 20, 10]}]},
        {"page_number": 2, "blocks": [{"block_id": "p2", "text": "Second", "bbox": [0, 0, 20, 10]}]},
    ]}
    gold = [{"source_sha256": HASH, "page_number": 1,
        "blocks": [{"id": "a", "text": "First", "bbox": [0, 0, 20, 10]}],
        "section_continuations": [{"source": {"page_number": 1, "block_id": "a"},
            "destination": {"page_number": 2, "anchor": {"text": "Second", "bbox": [0, 0, 20, 10]}},
            "section_relation": "DIFFERENT_SECTION", "content_continuity": "NONE"}]}]
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text("".join(json.dumps(row) + "\n" for row in gold))

    report = evaluate_structure(version_path, gold_path)["section_continuations"]

    assert report["confusion_counts"]["DIFFERENT_SECTION"]["DIFFERENT_SECTION"] == 1
    assert report["decision_coverage"] == 1


def test_equal_but_broad_heading_path_is_unresolved(tmp_path):
    version = {"source_sha256": HASH, "document_tree": {"root": {"node_id": "root", "block_ids": [], "children": [
        {"node_id": "chapter", "title": "Chapter", "block_ids": ["p1", "p2"], "children": [
            {"node_id": "subsection", "title": "Subsection", "block_ids": [], "children": []}
        ]}]}}, "pages": [
        {"page_number": 1, "blocks": [{"block_id": "p1", "text": "First", "bbox": [0, 0, 20, 10]}]},
        {"page_number": 2, "blocks": [{"block_id": "p2", "text": "Second", "bbox": [0, 0, 20, 10]}]},
    ]}
    gold = [{"source_sha256": HASH, "page_number": 1,
        "blocks": [{"id": "a", "text": "First", "bbox": [0, 0, 20, 10]}],
        "section_continuations": [{"source": {"page_number": 1, "block_id": "a"},
            "destination": {"page_number": 2, "anchor": {"text": "Second", "bbox": [0, 0, 20, 10]}},
            "section_relation": "UNRESOLVED", "content_continuity": "NONE"}]}]
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text("".join(json.dumps(row) + "\n" for row in gold))

    report = evaluate_structure(version_path, gold_path)["section_continuations"]

    assert report["confusion_counts"]["UNRESOLVED"]["UNRESOLVED"] == 1
    assert report["unresolved_gold_abstention_rate"] == 1
    assert report["decision_coverage"] is None


def test_old_combined_relation_field_is_rejected_instead_of_misread(tmp_path):
    version = {"source_sha256": HASH, "pages": [
        {"page_number": 1, "blocks": [{"block_id": "p1", "text": "A", "bbox": [0, 0, 10, 10]}]},
        {"page_number": 2, "blocks": [{"block_id": "p2", "text": "B", "bbox": [0, 0, 10, 10]}]},
    ]}
    gold = {"source_sha256": HASH, "page_number": 1,
        "section_continuations": [{"source": {"page_number": 1, "anchor": {"text": "A", "bbox": [0, 0, 10, 10]}},
            "destination": {"page_number": 2, "anchor": {"text": "B", "bbox": [0, 0, 10, 10]}},
            "relation_types": ["SAME_SECTION"]}]}
    version_path, gold_path = tmp_path / "version.json", tmp_path / "gold.jsonl"
    version_path.write_text(json.dumps(version)); gold_path.write_text(json.dumps(gold) + "\n")

    with pytest.raises(StructureEvaluationError, match="relation_types is obsolete"):
        evaluate_structure(version_path, gold_path)
