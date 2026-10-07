#!/usr/bin/env python3
"""Build the metadata-only inventory from the approved Basic Rules PDF nodes."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

PDF_SHA256 = "7a0c5d8bf52d15092f156d78418aa3d43307e271f810d2f06bf2f0258e9288a3"
NODES_SHA256 = "02b47f9b07f27ddf4d95542f0aa50ad200b0999c670e3dea8e098c4579c44991"
EXPECTED_COUNT = 126
OUTPUT = Path(__file__).parents[1] / "src/main/resources/com/dndmaster/adventure/domain/scenario/basic-rulebook-2018-spells.tsv"
OWNERS = OUTPUT.with_name("basic-rulebook-2018-spell-owners.json")
FIELD_NAMES = ("Casting Time", "Range", "Components", "Duration")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def spell_fields(node: dict) -> dict[str, str]:
    marker = re.compile(rf"({'|'.join(FIELD_NAMES)}):\s*")
    found: dict[str, str] = {}
    current = None
    for paragraph in node.get("children", []):
        text = paragraph.get("text", "").strip()
        matches = list(marker.finditer(text))
        if matches:
            for index, match in enumerate(matches):
                end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
                value = " ".join(text[match.end():end].split()).strip()
                current = match.group(1)
                if value:
                    found[current] = " ".join(filter(None, (found.get(current), value))).strip()
            if "Duration" in found:
                break
            continue
        if current is None:
            continue
        previous_value = found.get(current, "") if current else ""
        if current and (not previous_value or re.search(r"\b(a|an|the|which|within|of|to|and|or|for|at)$", previous_value, re.I)):
            if len(text) <= 120 and "." not in text:
                found[current] = " ".join(filter(None, (previous_value, text))).strip()
                if "Duration" in found:
                    break
                continue
        break
    return found


def row_for(node: dict, owner_mapping: dict[str, dict]) -> str:
    children = node.get("children", [])
    metadata = spell_fields(node)
    first = children[0].get("text", "") if children else ""
    first_match = re.search(r"(?:(\d+(?:st|nd|rd|th)-level)\s+)?(Abjuration|Conjuration|Divination|Enchantment|Evocation|Illusion|Necromancy|Transmutation)(?:\s+cantrip)?", first, re.I)
    level = first_match.group(1) if first_match and first_match.group(1) else "Cantrip"
    school = first_match.group(2).title() if first_match else "원문 확인 필요"
    if level.endswith("-level"):
        level = level[:-6]
    values = {name: metadata.get(name, "원문 확인 필요") for name in FIELD_NAMES}
    values["Duration"] = values["Duration"].replace("Concentration, up to ", "집중, 최대 ")
    values["Casting Time"] = values["Casting Time"].replace("1 action", "행동 1회").replace("1 bonus action", "추가 행동 1회").replace("1 reaction", "반응 1회")
    values["Range"] = values["Range"].replace(" feet", " 피트").replace(" foot", " 피트")
    owner = owner_mapping.get(node["id"])
    traits = owner["ownerPlanNumbers"] if owner else []
    evidence = owner["rationaleKo"] if owner else "미검토: 이 주문의 담당 계획과 판단 근거를 아직 확인하지 않음"
    slug = re.sub(r"[^a-z0-9]+", "-", node["text"].lower()).strip("-")
    locator = f"page={node['page']};node={node['id']}"
    row = [f"spell-{node['id']}-{slug}", node["text"], locator, level, values["Casting Time"],
           values["Range"], values["Components"], values["Duration"], school,
           "원문 확인 필요", "원문 확인 필요", ",".join(map(str, traits)), evidence]
    return "\t".join(row)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--nodes", type=Path, required=True)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if sha256(args.pdf) != PDF_SHA256 or sha256(args.nodes) != NODES_SHA256:
        print("approved Basic Rules PDF or companion nodes do not match their recorded hashes", file=sys.stderr)
        return 1
    document = json.loads(args.nodes.read_text(encoding="utf-8"))
    nodes = document.get("nodes", [])
    start = next((i for i, node in enumerate(nodes) if node.get("type") == "HEADING" and node.get("text") == "Spell Descriptions"), None)
    end = next((i for i, node in enumerate(nodes) if start is not None and i > start
                and node.get("type") == "HEADING" and node.get("text") == "Chapter 12: Monsters"), None)
    if start is None or end is None:
        print("spell chapter boundaries were not found", file=sys.stderr)
        return 1
    headings = [node for node in nodes[start + 1:end] if node.get("type") == "HEADING"]
    artifacts = [node for node in headings if re.match(r"^(Components|Casting Time):", node.get("text", ""))]
    spells = [node for node in headings if node not in artifacts]
    if len(headings) != 135 or len(artifacts) != 9 or len(spells) != EXPECTED_COUNT:
        print(f"unexpected spell chapter structure: {len(headings)} headings, {len(artifacts)} OCR labels, {len(spells)} candidates", file=sys.stderr)
        return 1
    if len({node["id"] for node in spells}) != EXPECTED_COUNT:
        print("spell heading node IDs are not unique", file=sys.stderr)
        return 1
    owner_manifest = json.loads(OWNERS.read_text(encoding="utf-8"))
    reviewed = owner_manifest.get("reviewedEntries", [])
    if (owner_manifest.get("sourceSha256") != PDF_SHA256
            or owner_manifest.get("inventoryCount") != EXPECTED_COUNT
            or owner_manifest.get("reviewedCount") != len(reviewed)
            or [entry.get("nodeId") for entry in reviewed] != [node["id"] for node in spells[:len(reviewed)]]):
        print("owner manifest does not match the reviewed prefix of the approved inventory", file=sys.stderr)
        return 1
    owner_mapping = {}
    for entry in reviewed:
        plans = entry.get("ownerPlanNumbers", [])
        if (not plans or any(plan not in (368, 369, 370, 371, 372) for plan in plans)
                or not entry.get("rationaleKo", "").strip()):
            print(f"invalid reviewed owner mapping for {entry.get('nodeId')}", file=sys.stderr)
            return 1
        owner_mapping[entry["nodeId"]] = entry
    lines = ["# source=DnD_BasicRules_2018.pdf; sha256=" + PDF_SHA256 + "; node-sha256=" + NODES_SHA256,
             "id\tname\tsourceLocator\tlevel\tcastingTime\trangeArea\tcomponents\tduration\tschool\tattackSave\tdamageEffect\townerPlans\townerEvidence"]
    lines.extend(row_for(node, owner_mapping) for node in spells)
    generated = "\n".join(lines) + "\n"
    if args.write:
        OUTPUT.write_text(generated, encoding="utf-8")
        print(f"wrote {len(spells)} spell metadata rows to {OUTPUT}")
        return 0
    if OUTPUT.read_text(encoding="utf-8") != generated:
        print("PDF-derived spell inventory differs; rerun with --write after reviewing the source", file=sys.stderr)
        return 1
    print(f"verified {len(spells)} PDF-derived spell metadata rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
