# Storybook indexing

Docling recovers PDF layout into Markdown. LlamaIndex splits only inside each
recovered heading section. JSON separates `content` from `contextual_content`.

```bash
uv venv .venv-docling
uv pip install --python .venv-docling/bin/python -r tools/storybook_indexing/requirements.txt
.venv-docling/bin/python -m tools.storybook_indexing.indexer \
  docs/assets/892902-A_Most_Potent_Brew.pdf \
  --output output/a-most-potent-brew.index.json
```

Use `contextual_content` for embedding. Use `content` or its parent section for
LLM context. Each chunk carries document title, source, and heading path.
