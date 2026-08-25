from pathlib import Path
from preprocessing_agent.pipeline import PreprocessingPipeline
def _fixture(path: Path) -> None:
    path.write_text(
        """# D&D Basic Rules
Chapter 1
Section 1 Combat Rules
Advantage means a character rolls two d20s and uses the higher result. This rule preserves the original source span and gives a stable retrieval key.
Section 2 Fire Bolt
Casting Time: 1 action. Range: 120 feet. Duration: instantaneous. A bright mote of fire streaks toward a creature.
""",
        encoding="utf-8",
    )
def test_pipeline_runs_fixture_and_exports_all_public_artifacts(tmp_path):
    source = tmp_path / "fixture.md"
    _fixture(source)
    result = PreprocessingPipeline.from_config(
        {"name": "dnd", "pipeline_version": "test", "chunking": {"min_tokens": 1, "target_tokens": 20, "max_tokens": 40}},
        output_dir=tmp_path / "out",
    ).run(source=source)
    assert result.chunks
    assert any(chunk.canonical_key.endswith("fire_bolt") for chunk in result.chunks)
    assert result.manifest["source_sha256"]
    assert {name for name in ("chunks.jsonl", "issues.jsonl", "document_tree.json", "manifest.json") if (tmp_path / "out" / name).exists()} == {
        "chunks.jsonl", "issues.jsonl", "document_tree.json", "manifest.json"
    }
    assert result.chunks == PreprocessingPipeline.from_config(
        {"name": "dnd", "pipeline_version": "test", "chunking": {"min_tokens": 1, "target_tokens": 20, "max_tokens": 40}},
        output_dir=tmp_path / "out-2",
    ).run(source=source).chunks
