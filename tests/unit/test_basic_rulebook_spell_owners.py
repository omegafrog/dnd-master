import csv
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCE = ROOT / "src/adventure-service/src/main/resources/com/dndmaster/adventure/domain/scenario/basic-rulebook-2018-spells.tsv"
MANIFEST = ROOT / "src/adventure-service/src/main/resources/com/dndmaster/adventure/domain/scenario/basic-rulebook-2018-spell-owners.json"


class BasicRulebookSpellOwnersTest(unittest.TestCase):
    def test_only_source_reviewed_entries_have_explicit_plan_owners(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        reviewed = manifest["reviewedEntries"]
        self.assertEqual(40, len(reviewed))
        self.assertEqual(40, len({entry["nodeId"] for entry in reviewed}))
        self.assertTrue(all(entry["ownerPlanNumbers"] and entry["rationaleKo"].strip() for entry in reviewed))

        with RESOURCE.open(encoding="utf-8", newline="") as source:
            rows = list(csv.DictReader((line for line in source if not line.startswith("#")), delimiter="\t"))
        by_node = {row["sourceLocator"].split("node=")[1]: row for row in rows}
        self.assertEqual(126, len(rows))
        for entry in reviewed:
            row = by_node[entry["nodeId"]]
            self.assertEqual(",".join(map(str, entry["ownerPlanNumbers"])), row["ownerPlans"])
            self.assertEqual(entry["rationaleKo"], row["ownerEvidence"])
        self.assertTrue(all(not by_node[node]["ownerPlans"] for node in by_node.keys() - {e["nodeId"] for e in reviewed}))


if __name__ == "__main__":
    unittest.main()
