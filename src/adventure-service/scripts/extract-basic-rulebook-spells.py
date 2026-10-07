#!/usr/bin/env python3
"""Rebuild the metadata-only 2014 Basic Rules spell inventory from D&D Beyond."""

from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.request import Request, urlopen

CHAPTER = "https://www.dndbeyond.com/sources/dnd/basic-rules-2014/spells"
OUTPUT = Path(__file__).parents[1] / "src/main/resources/com/dndmaster/adventure/domain/scenario/basic-rulebook-2014-spells.tsv"
HEADERS = {"User-Agent": "Mozilla/5.0"}


def fetch(url):
    with urlopen(Request(url, headers=HEADERS), timeout=30) as response:
        return response.read().decode("utf-8", "replace")


class ChapterParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.heading = False
        self.name = []
        self.path = None
        self.spells = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "h3":
            self.heading, self.name, self.path = True, [], None
        elif self.heading and tag == "a" and attrs.get("href", "").startswith("/spells/"):
            self.path = attrs["href"]

    def handle_data(self, data):
        if self.heading:
            self.name.append(data)

    def handle_endtag(self, tag):
        if tag == "h3" and self.heading:
            if self.path:
                self.spells.append(("".join(self.name).strip(), self.path))
            self.heading = False


class SpellParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.item = None
        self.item_depth = 0
        self.target = None
        self.buffer = []
        self.values = {}
        self.in_description = False
        self.description_depth = 0
        self.description = []

    def handle_starttag(self, tag, attrs):
        if tag != "div":
            return
        classes = dict(attrs).get("class", "")
        if classes.startswith("ddb-statblock-item ddb-statblock-item-"):
            self.item = classes.split()[-1].removeprefix("ddb-statblock-item-")
            self.item_depth = 1
        elif self.item:
            self.item_depth += 1
            if classes == "ddb-statblock-item-label":
                self.target, self.buffer = "label", []
            elif classes == "ddb-statblock-item-value":
                self.target, self.buffer = "value", []
        if classes == "more-info-content":
            self.in_description, self.description_depth, self.description = True, 1, []
        elif self.in_description:
            self.description_depth += 1

    def handle_data(self, data):
        if self.target:
            self.buffer.append(data)
        if self.in_description:
            self.description.append(data)

    def handle_endtag(self, tag):
        if tag != "div":
            return
        if self.target:
            key = " ".join(self.values.get(self.item, {}).get("label", "").split()) if self.target == "value" else None
            value = " ".join("".join(self.buffer).split())
            if self.target == "label":
                self.values.setdefault(self.item, {})["label"] = value
            elif key:
                self.values[key] = value
            self.target = None
        if self.item:
            self.item_depth -= 1
            if self.item_depth == 0:
                self.item = None
        if self.in_description:
            self.description_depth -= 1
            if self.description_depth == 0:
                self.in_description = False


def classify(spell):
    stat, description = spell["stat"], spell["description"].casefold()
    owners = {368}
    evidence = {368: "기본 효과와 비용"}
    area_or_targets = re.search(
        r"\b(cone|cube|cylinder|line|sphere|radius|each creature|choose (?:up to )?(?:one|two|three|four|five|six|seven|eight|nine|ten|a number of)[^.!?]*creatures?)\b",
        (stat.get("Range/Area", "") + " " + description).casefold(),
    )
    if area_or_targets:
        owners.add(369)
        evidence[369] = "여러 대상 또는 공간 지정"
    duration = stat.get("Duration", "").casefold()
    casting_time = stat.get("Casting Time", "").casefold()
    if casting_time not in {"", "1 action"} or duration not in {"", "instantaneous"} or any(
        term in description for term in ("concentration", "at higher levels", "trigger")
    ):
        owners.add(370)
        evidence[370] = "시전 시점·지속·집중·발동·높은 등급 주문 슬롯"
    effect = stat.get("Damage/Effect", "").casefold()
    if "summon" in effect or re.search(r"\b(summon|conjure|animate|create a creature|control a creature|raise the dead)\b", description):
        owners.add(371)
        evidence[371] = "소환 또는 생물 조종"
    if any(term in effect for term in (
        "utility", "detection", "communication", "shapechanging", "creation", "teleportation",
        "movement", "exploration", "foreknowledge", "social", "deception",
    )) or any(term in description for term in (
        "learns the location", "reveals the location", "communicate with", "speak with",
        "transforms you", "changes your appearance",
    )):
        owners.add(372)
        evidence[372] = "비전투 변화·생성·정보 확인"
    return sorted(owners), "; ".join(f"#{owner}: {evidence[owner]}" for owner in sorted(owners))


def extract():
    parser = ChapterParser()
    parser.feed(fetch(CHAPTER))
    if len(parser.spells) != 304:
        raise SystemExit(f"expected 304 chapter entries; found {len(parser.spells)}")

    def read_spell(entry):
        name, path = entry
        page = fetch("https://www.dndbeyond.com" + path)
        details = SpellParser()
        details.feed(page)
        if not {"Level", "Casting Time", "Range/Area", "Components", "Duration", "School", "Attack/Save", "Damage/Effect"} <= details.values.keys():
            raise SystemExit(f"official details are incomplete: {name}")
        return {"name": name, "url": "https://www.dndbeyond.com" + path, "stat": details.values,
                "description": " ".join("".join(details.description).split())}

    with ThreadPoolExecutor(max_workers=8) as pool:
        spells = sorted(pool.map(read_spell, parser.spells), key=lambda spell: spell["name"].casefold())
    lines = [
        "# source=D&D 5e Basic Rules (2014); extraction=1; chapter=" + CHAPTER,
        "id\tname\tsourceUrl\tlevel\tcastingTime\trangeArea\tcomponents\tduration\tschool\tattackSave\tdamageEffect\townerPlans\townerEvidence",
    ]
    for spell in spells:
        stat = spell["stat"]
        owners, evidence = classify(spell)
        fields = [
            "spell-" + spell["url"].rsplit("/", 1)[-1], spell["name"], spell["url"], stat["Level"],
            stat["Casting Time"], stat["Range/Area"], stat["Components"], stat["Duration"], stat["School"],
            stat["Attack/Save"], stat["Damage/Effect"], ",".join(map(str, owners)), evidence,
        ]
        lines.append("\t".join(value.replace("\t", " ").replace("\n", " ").strip() for value in fields))
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="replace the checked-in metadata inventory")
    args = parser.parse_args()
    content = extract()
    if args.write:
        OUTPUT.write_text(content, encoding="utf-8")
    elif OUTPUT.read_text(encoding="utf-8") != content:
        raise SystemExit("inventory differs from the official 2014 Basic Rules pages; run with --write")
    else:
        print("verified 304 official spell entries")
