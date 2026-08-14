import unittest

from tools.storybook_indexing.indexer import index_markdown, split_markdown_sections


class StorybookIndexerTest(unittest.TestCase):
    def test_preserves_heading_path_and_contextual_header(self):
        markdown = """# Stormwreck Isle
Intro text.

## Seagrow Caves
The party enters the cave.

### Runara
She warns the adventurers.
"""

        chunks = index_markdown(
            markdown,
            source="stormwreck.pdf",
            title="Stormwreck Isle",
            splitter=lambda text, metadata: [text],
        )

        self.assertEqual(3, len(chunks))
        intro, cave, runara = chunks
        self.assertEqual(["Stormwreck Isle"], intro["section_path"])
        self.assertEqual(["Stormwreck Isle", "Seagrow Caves"], cave["section_path"])
        self.assertEqual("The party enters the cave.", cave["content"])
        self.assertIn("Document: Stormwreck Isle", cave["contextual_content"])
        self.assertIn("Section: Stormwreck Isle > Seagrow Caves", cave["contextual_content"])
        self.assertEqual(
            ["Stormwreck Isle", "Seagrow Caves", "Runara"], runara["section_path"]
        )

    def test_ignores_preamble_and_emits_deterministic_chunk_ids(self):
        markdown = """Cover text.

## Starting the Adventure
Read this aloud.
"""

        sections = split_markdown_sections(markdown)
        self.assertEqual(["Starting the Adventure"], sections[1].path)

        first = index_markdown(markdown, "brew.pdf", "A Most Potent Brew", lambda text, _: [text])
        second = index_markdown(markdown, "brew.pdf", "A Most Potent Brew", lambda text, _: [text])

        self.assertEqual(first[0]["chunk_id"], second[0]["chunk_id"])
        self.assertEqual("Cover text.", first[0]["content"])
        self.assertEqual([], first[0]["section_path"])
