from pathlib import Path

from preprocessing_agent import __version__
from preprocessing_agent.config import AgentConfig, load_config


def test_package_import_exposes_version() -> None:
    assert __version__


def test_load_config_reads_yaml_profile(tmp_path: Path) -> None:
    config_path = tmp_path / "profile.yaml"
    config_path.write_text(
        "name: test-profile\n"
        "pipeline_version: 0.1.0\n"
        "chunking:\n"
        "  target_tokens: 350\n",
        encoding="utf-8",
    )

    config = load_config(config_path)

    assert isinstance(config, AgentConfig)
    assert config.name == "test-profile"
    assert config.pipeline_version == "0.1.0"
    assert config.chunking["target_tokens"] == 350


def test_load_config_rejects_non_mapping(tmp_path: Path) -> None:
    config_path = tmp_path / "invalid.yaml"
    config_path.write_text("- not-a-mapping\n", encoding="utf-8")

    try:
        load_config(config_path)
    except ValueError as exc:
        assert "mapping" in str(exc)
    else:
        raise AssertionError("load_config should reject a non-mapping YAML document")
