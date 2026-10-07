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
        self.assertEqual(126, len(reviewed))
        self.assertEqual(126, len({entry["nodeId"] for entry in reviewed}))
        self.assertTrue(all(entry["ownerPlanNumbers"] and entry["rationaleKo"].strip() for entry in reviewed))
        by_node = {entry["nodeId"]: entry for entry in reviewed}
        for node_id, expected in {
            "node-2667": [369, 370, 372],
            "node-2685": [368, 370],
            "node-2700": [370, 372],
            "node-2743": [368, 370, 371],
            "node-2770": [368, 369, 370, 372],
            "node-2867": [369, 370, 371, 372],
            "node-2913": [368, 369, 370, 371],
            "node-2958": [368, 369, 370, 371],
            "node-2990": [368, 369, 370, 371, 372],
            "node-3077": [369, 370, 371, 372],
            "node-3088": [368, 369, 370],
            "node-3136": [368, 369, 370, 371, 372],
            "node-3147": [368, 369, 370, 371, 372],
            "node-3167": [369],
            "node-3172": [368, 369, 370, 371],
            "node-3222": [368, 370],
            "node-3244": [369, 370, 372],
            "node-3327": [368, 370, 372],
            "node-3338": [368, 370],
            "node-3350": [368, 370],
            "node-3362": [368, 369, 370],
            "node-3375": [368, 370],
            "node-3389": [368, 370],
            "node-3398": [368, 370],
            "node-3410": [369, 370, 372],
            "node-3421": [369, 370, 372],
            "node-3435": [368, 369, 370],
            "node-3446": [368],
            "node-3450": [368, 370, 372],
            "node-3457": [368, 370, 372],
            "node-3462": [368, 369, 370],
            "node-3479": [368, 370],
            "node-3487": [368, 370, 371, 372],
            "node-3497": [368, 369, 370, 372],
            "node-3506": [368, 369, 370, 372],
            "node-3524": [369, 370, 372],
            "node-3535": [368, 369, 370],
            "node-3541": [370],
            "node-3546": [368, 370, 372],
            "node-3557": [368, 370, 372],
            "node-3564": [368, 369, 370, 372],
            "node-3575": [368, 369, 370, 372],
            "node-3586": [368, 370],
            "node-3594": [368, 369, 370, 371, 372],
        }.items():
            self.assertEqual(expected, by_node[node_id]["ownerPlanNumbers"], node_id)

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
