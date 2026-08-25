"""RAG preprocessing agent package."""

__version__ = "0.1.0"

from .config import AgentConfig, load_config

from .postprocessing import postprocess_chunks

__all__ = ["AgentConfig", "load_config", "__version__", "postprocess_chunks"]
