import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CharacterCreationPage } from "./CharacterCreationPage";

const preparation = {
  scenarioPackageId: "package-1",
  bundleId: "bundle-1",
  bundleRevision: 1,
  status: "READY",
  blockers: [],
  characterLimit: { maximumCharacters: 2, source: null, sourceQuote: "" },
  characterCreationBlueprint: {
    available: true,
    summary: "published",
    rulebookDocumentCount: 1,
    storybookDocumentCount: 0,
    diagnostics: [],
    revision: 4,
    status: "PUBLISHED",
    fields: [],
  },
};

const session = {
  sessionId: "session-1",
  scenarioPackageId: "package-1",
  blueprintRevision: 4,
  characterLimit: 2,
  version: 0,
  status: "DRAFT",
  party: [],
  adventureId: null,
  runtimeConfiguration: null,
};

function renderPage() {
  const createCharacterSheet = vi.fn().mockResolvedValue({
    characterSheetId: "sheet-1",
    adventureId: "adventure-1",
    edition: "DND_5E_2014",
    characterName: "아리아",
    level: 1,
    inspiration: false,
    version: 0,
  });
  const setupApi = {
    getPlayPreparation: vi.fn().mockResolvedValue(preparation),
    createCharacterSheet,
  };
  const sessionApi = {
    read: vi.fn().mockResolvedValue(session),
    addMember: vi.fn(),
  };
  const view = render(
      <CharacterCreationPage
        sessionId="session-1"
        ownerPlayerId="player-1"
        setupApi={setupApi}
      sessionApi={sessionApi}
    />,
  );
  return { createCharacterSheet, addMember: sessionApi.addMember, unmount: view.unmount };
}

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("CharacterCreationPage", () => {
  it("uses the four classes and four races from the bundled Basic Rules", async () => {
    renderPage();
    const classSelect = await screen.findByLabelText("직업");
    const raceSelect = screen.getByLabelText("종족");

    expect(Array.from(classSelect.querySelectorAll("option")).map((option) => option.value).sort()).toEqual([
      "",
      "로그",
      "위저드",
      "클레릭",
      "파이터",
    ].sort());
    expect(Array.from(raceSelect.querySelectorAll("option")).map((option) => option.value)).toEqual([
      "",
      "드워프",
      "엘프",
      "하플링",
      "인간",
    ]);
    expect(screen.queryByLabelText("캐릭터 레벨")).toBeNull();
    expect(screen.queryByLabelText("키")).toBeNull();
    expect(screen.queryByLabelText("몸무게")).toBeNull();
    expect(screen.queryByLabelText("눈")).toBeNull();
    expect(screen.queryByLabelText("피부")).toBeNull();
    expect(screen.queryByLabelText("머리카락")).toBeNull();
    expect(screen.queryByLabelText("party")).toBeNull();
    expect(screen.queryByLabelText("일행과의 관계")).toBeNull();
  });

  it("limits subraces to the selected race and disables them for humans", async () => {
    const user = userEvent.setup();
    renderPage();
    const race = await screen.findByLabelText("종족");
    const subrace = screen.getByLabelText("하위 종족") as HTMLSelectElement;

    await user.selectOptions(race, "엘프");
    expect(Array.from(subrace.options).map((option) => option.value)).toContain("하이 엘프");
    expect(Array.from(subrace.options).map((option) => option.value)).not.toContain("언덕 드워프");
    await user.selectOptions(race, "인간");
    expect(subrace.disabled).toBe(true);
  });

  it("uses the standard array without allowing duplicate values", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "능력치" }));
    await user.selectOptions(screen.getByLabelText("근력 능력치"), "15");
    expect(screen.getByLabelText("민첩 능력치").querySelector('option[value="15"]')).toBeNull();
  });

  it("replaces the owned equipment when a different bundle is selected", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "장비" }));
    await user.click(screen.getByRole("button", { name: /로그 시작 장비/ }));
    expect(screen.getByText("숏소드")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: /던전 탐험가 꾸러미/ }));
    expect(screen.queryByText("숏소드")).toBeNull();
    expect(screen.getByText("횃불 10개")).toBeTruthy();
  });

  it("shows creation-time spell choices and computed spell metadata", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.selectOptions(await screen.findByLabelText("직업"), "위저드");
    await user.click(screen.getByRole("button", { name: "주문" }));
    expect(screen.getByText("선택 가능한 주문")).toBeTruthy();
    expect(screen.getByText("마법 갑주")).toBeTruthy();
    expect(screen.getByText("선택 가능한 소마법")).toBeTruthy();
    expect(screen.getByText("생성 후 플레이에서 사용")).toBeTruthy();
  });

  it("restores the in-progress selections after a provider remount", async () => {
    const user = userEvent.setup();
    const first = renderPage();
    await user.selectOptions(await screen.findByLabelText("종족"), "엘프");
    await user.selectOptions(screen.getByLabelText("직업"), "로그");

    first.unmount();
    renderPage();

    expect((await screen.findByLabelText("종족") as HTMLSelectElement).value).toBe("엘프");
    expect((screen.getByLabelText("직업") as HTMLSelectElement).value).toBe("로그");
  });

  it("saves final ability scores and rule-derived generation data", async () => {
    const user = userEvent.setup();
    const { createCharacterSheet, addMember } = renderPage();
    await user.type(await screen.findByPlaceholderText("이름을 입력하세요"), "아리아");
    await user.selectOptions(screen.getByLabelText("종족"), "인간");
    await user.selectOptions(screen.getByLabelText("직업"), "위저드");
    await user.click(screen.getByRole("button", { name: "능력치" }));
    for (const [label, value] of [
      ["근력 능력치", "15"],
      ["민첩 능력치", "14"],
      ["건강 능력치", "13"],
      ["지능 능력치", "12"],
      ["지혜 능력치", "10"],
      ["매력 능력치", "8"],
    ]) {
      await user.selectOptions(screen.getByLabelText(label), value);
    }
    await user.click(screen.getByRole("button", { name: "캐릭터 저장하기 →" }));

    const draft = createCharacterSheet.mock.calls[0][0];
    expect(draft.ownerPlayerId).toBe("player-1");
    expect(addMember).toHaveBeenCalledWith("session-1", 0, expect.objectContaining({ characterSheetId: "sheet-1", controlMode: "DIRECT" }));
    const build = JSON.parse(draft.characterBuild) as { baseStats: number[]; stats: number[]; raceBonus: number[]; learnedSpells: string[]; schemaVersion: number; skillProficiencies: string[]; equipmentSelections: Record<string, string>; equippedItems: { armor: string; shield: boolean } };
    expect(build.baseStats).toEqual([15, 14, 13, 12, 10, 8]);
    expect(build.stats).toEqual([16, 15, 14, 13, 11, 9]);
    expect(build.raceBonus).toEqual([1, 1, 1, 1, 1, 1]);
    expect(build.schemaVersion).toBe(1);
    expect(build.skillProficiencies.length).toBeGreaterThanOrEqual(2);
    expect(build.equipmentSelections).toEqual({ equipmentBundle: "dungeon-explorer" });
    expect(build.equippedItems).toEqual({ armor: "가죽 갑옷", shield: false });
    expect(build.learnedSpells).toHaveLength(6);
  });
});
