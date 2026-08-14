import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import type {
  CharacterCreationDraft,
  CharacterInputNodeView,
  SetupApi,
} from "../rulebooks/SetupApi";
import type { AdventureSessionApi } from "../adventure-session/AdventureSessionApi";
import {
  backgroundOptions,
  classOptions,
  raceOptions,
  STANDARD_ARRAY,
} from "./Dnd5eCharacterCatalog";
import { subclassesFor } from "./Dnd5eSubclassCatalog";
import { Card, CardContent } from "../../components/ui/card";
import { Input } from "../../components/ui/input";
import { Textarea } from "../../components/ui/textarea";
import { Badge } from "../../components/ui/badge";
import { Select } from "../../components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle as UiSheetTitle,
} from "../../components/ui/sheet";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "../../components/ui/sidebar";
import { ChevronRight } from "lucide-react";

const stats = ["근력", "민첩", "건강", "지능", "지혜", "매력"];
type SheetSection =
  | "basic"
  | "abilities"
  | "skills"
  | "combat"
  | "equipment"
  | "book"
  | "note"
  | "scroll";
const sheetSections: Array<[SheetSection, string, string]> = [
  ["basic", "기본 정보", "basic"],
  ["abilities", "능력치", "abilities"],
  ["skills", "기술", "skills"],
  ["equipment", "장비", "equipment"],
  ["book", "주문", "book"],
  ["note", "배경/성격", "note"],
  ["scroll", "메모", "scroll"],
];
const scoreKeys = [
  ["strength", "str"],
  ["dexterity", "dex"],
  ["constitution", "con"],
  ["intelligence", "int"],
  ["wisdom", "wis"],
  ["charisma", "cha"],
];
type SkillDefinition = { name: string; ability: string; description: string };
// 출처: docs/dnd5th.pdf, 제7장 「능력치 사용」의 기술 설명을 UI용으로 요약함.
const skillCatalog: SkillDefinition[] = [
  {
    name: "곡예 (Acrobatics)",
    ability: "민첩",
    description:
      "균형을 유지하고, 좁은 곳을 통과하거나 넘어짐을 피하고, 공중제비 같은 곡예 동작을 수행하는 데 사용합니다.",
  },
  {
    name: "동물 조련 (Animal Handling)",
    ability: "지혜",
    description:
      "동물을 진정시키거나 의도를 파악하고, 탈것을 통제하거나 위험한 상황에서 동물을 다루는 데 사용합니다.",
  },
  {
    name: "비전학 (Arcana)",
    ability: "지능",
    description:
      "주문, 마법 아이템, 초자연적 상징, 마법 전통과 다른 차원에 관한 지식을 떠올리는 데 사용합니다.",
  },
  {
    name: "운동 (Athletics)",
    ability: "근력",
    description:
      "달리기·등반·점프·수영처럼 신체적인 힘과 운동 능력을 요구하는 행동에 사용합니다.",
  },
  {
    name: "기만 (Deception)",
    ability: "매력",
    description:
      "말이나 행동으로 진실을 숨기고, 거짓말을 믿게 하거나 다른 사람을 속이는 데 사용합니다.",
  },
  {
    name: "역사 (History)",
    ability: "지능",
    description:
      "역사적 사건, 전설적인 인물, 고대 왕국, 과거의 분쟁과 사라진 문명에 관한 지식을 떠올리는 데 사용합니다.",
  },
  {
    name: "통찰 (Insight)",
    ability: "지혜",
    description:
      "생명체의 진짜 의도와 감정을 파악하고, 거짓말을 알아채거나 다음 행동을 예측하는 데 사용합니다.",
  },
  {
    name: "위협 (Intimidation)",
    ability: "매력",
    description:
      "위협, 적대적 행동 또는 신체적 폭력으로 다른 사람의 행동에 영향을 주는 데 사용합니다.",
  },
  {
    name: "수사 (Investigation)",
    ability: "지능",
    description:
      "단서에서 추론하고, 숨겨진 물체를 찾고, 함정이나 환영의 작동 방식을 알아내는 데 사용합니다.",
  },
  {
    name: "의학 (Medicine)",
    ability: "지혜",
    description:
      "죽어가는 동료를 안정시키고, 질병을 진단하거나 부상의 원인과 치료법을 파악하는 데 사용합니다.",
  },
  {
    name: "자연 (Nature)",
    ability: "지능",
    description:
      "지형, 식물, 동물, 날씨와 자연의 순환에 관한 지식을 떠올리는 데 사용합니다.",
  },
  {
    name: "지각 (Perception)",
    ability: "지혜",
    description:
      "주변의 존재를 보고 듣고 알아차리며, 숨은 물체·비밀문·매복을 발견하는 데 사용합니다.",
  },
  {
    name: "공연 (Performance)",
    ability: "매력",
    description:
      "음악, 춤, 연기, 이야기 또는 다른 공연으로 관객을 즐겁게 하는 데 사용합니다.",
  },
  {
    name: "설득 (Persuasion)",
    ability: "매력",
    description:
      "재치, 사회적 예절, 선의와 논리적인 대화로 다른 사람에게 영향을 주는 데 사용합니다.",
  },
  {
    name: "종교 (Religion)",
    ability: "지능",
    description:
      "신, 의식, 기도, 종교 계급과 성스러운 상징에 관한 지식을 떠올리는 데 사용합니다.",
  },
  {
    name: "손재주 (Sleight of Hand)",
    ability: "민첩",
    description:
      "작은 물건을 숨기거나 훔치고, 눈속임과 손기술로 물체를 조작하는 데 사용합니다.",
  },
  {
    name: "은신 (Stealth)",
    ability: "민첩",
    description:
      "몸을 숨기고 소리를 내지 않으며, 경비를 피해 몰래 이동하는 데 사용합니다.",
  },
  {
    name: "생존 (Survival)",
    ability: "지혜",
    description:
      "지형에서 흔적을 추적하고, 사냥하고, 길을 안내하고, 날씨와 자연적 위험을 판단하는 데 사용합니다.",
  },
];
const art = {
  위저드: "/assets/characters/wizard.png",
  팔라딘: "/assets/characters/paladin.png",
  로그: "/assets/characters/rogue.png",
};
type EquipmentItem = [name: string, type: string, weight: string];
type EquipmentBundle = {
  id: string;
  label: string;
  description: string;
  classes: string[];
  items: EquipmentItem[];
};
const baseOwnedEquipment: EquipmentItem[] = [
  ["단검", "무기", "1 kg"],
  ["지팡이", "무기", "2 kg"],
  ["가죽 갑옷", "방어구", "5 kg"],
];
const equipmentForBundle = (bundle: EquipmentBundle): EquipmentItem[] =>
  Array.from(
    new Map(
      // Class starting bundles are a complete 5e selection.  Do not let the
      // generic preview items silently replace the armor they actually chose.
      (bundle.id.endsWith("-start") ? bundle.items : [...baseOwnedEquipment, ...bundle.items]).map(
        (item) => [item[0], item],
      ),
    ).values(),
  );
const equipmentDescriptions: Record<string, string> = {
  단검: "가볍고 숨기기 쉬운 한손 무기입니다.",
  지팡이: "주문 도구나 보행 보조로 사용하는 막대기입니다.",
  "가죽 갑옷": "가볍고 유연한 가죽 방어구입니다.",
  배낭: "여행 장비와 소지품을 넣는 가방입니다.",
  "횃불 10개": "어두운 장소를 밝히는 횃불 10개입니다.",
  "밧줄 15m": "등반과 고정에 사용하는 튼튼한 밧줄입니다.",
  쇠망치: "못을 박거나 장비를 수리하는 데 쓰는 망치입니다.",
  "쇠못 10개": "문을 고정하거나 임시 구조물을 만드는 쇠못입니다.",
  책: "특정 주제의 지식이 담긴 책입니다.",
  잉크병: "필기와 기록에 사용하는 잉크병입니다.",
  깃펜: "잉크를 묻혀 글을 쓰는 깃펜입니다.",
  "양피지 10장": "지도나 기록을 남길 수 있는 양피지입니다.",
  담요: "야영 중 몸을 덮는 두꺼운 담요입니다.",
  성표: "신앙을 상징하는 성스러운 표식입니다.",
  "향 5개": "의식이나 기도에 사용하는 향 5개입니다.",
  "보통 의복": "일상적인 여행과 활동에 적합한 의복입니다.",
  침낭: "야외에서 잠을 자기 위한 침낭입니다.",
  "식량 10일분": "열흘 동안 먹을 수 있는 보존 식량입니다.",
  물가죽: "물을 담아 휴대하는 가죽 용기입니다.",
  숏소드: "빠르게 휘두를 수 있는 기교 무기입니다.",
  "도둑 도구": "자물쇠를 열고 함정을 해제하는 도구입니다.",
  "도둑의 꾸러미": "도둑의 활동에 필요한 소형 도구 묶음입니다.",
  "구성 요소 주머니": "주문에 필요한 비소모성 구성 요소를 보관합니다.",
  "학자의 꾸러미": "연구와 기록에 필요한 문구와 자료 묶음입니다.",
  "스케일 메일": "금속 비늘로 만든 평갑 방어구입니다.",
  방패: "착용자의 AC에 방패 보너스를 더합니다.",
  메이스: "둔중한 타격을 가하는 단순 무기입니다.",
  "체인 메일": "고리를 엮어 만든 중갑 방어구입니다.",
  롱소드: "한손 또는 양손으로 사용할 수 있는 군용 무기입니다.",
  "라이트 크로스보우": "볼트를 발사하는 원거리 무기입니다.",
};
const equipmentStats: Record<string, string> = {
  단검: "피해 1d4 관통 · 기교 · 가벼움 · 투척(6/18m)",
  지팡이: "피해 1d6 타격 · 다용도(1d8)",
  "가죽 갑옷": "AC 11 + 민첩 수정치 · 은신 불리점 없음",
  숏소드: "피해 1d6 관통 · 기교 · 가벼움",
  메이스: "피해 1d6 타격",
  롱소드: "피해 1d8 참격 · 다용도(1d10)",
  "라이트 크로스보우": "피해 1d8 관통 · 장전 · 사거리(24/96m)",
  "스케일 메일": "AC 14 + 민첩 수정치(최대 2) · 은신 불리점",
  "체인 메일": "AC 16 · 은신 불리점",
  방패: "AC +2",
  "횃불 10개": "밝은 빛 6m · 어두운 빛 6m · 1시간",
  "밧줄 15m": "인장력 272kg",
  쇠망치: "피해 1d4 타격 · 즉석 무기",
  "쇠못 10개": "고정용 도구",
};
const weaponIdByName: Record<string, string> = {
  단검: "dagger",
  메이스: "mace",
  롱소드: "longsword",
  숏소드: "shortsword",
  지팡이: "quarterstaff",
  "라이트 크로스보우": "light-crossbow",
};
const startingBundleIdByClass: Record<string, string> = {
  로그: "rogue-start",
  위저드: "wizard-start",
  클레릭: "cleric-start",
  파이터: "fighter-start",
};
const equipmentBundles: EquipmentBundle[] = [
  {
    id: "dungeon-explorer",
    label: "던전 탐험가 꾸러미",
    description: "던전 탐험과 야외 이동에 필요한 기본 장비입니다.",
    classes: ["로그", "위저드", "클레릭", "파이터"],
    items: [
      ["배낭", "기타", "2 kg"],
      ["횃불 10개", "도구", "1 kg"],
      ["밧줄 15m", "도구", "5 kg"],
      ["쇠망치", "도구", "1.5 kg"],
      ["쇠못 10개", "도구", "0.5 kg"],
    ],
  },
  {
    id: "rogue-start",
    label: "로그 시작 장비",
    description: "가죽 갑옷과 기교 무기, 도둑 도구 중심의 시작 장비입니다.",
    classes: ["로그"],
    items: [
      ["가죽 갑옷", "경갑", "5 kg"],
      ["단검", "무기", "1 kg"],
      ["숏소드", "무기", "1 kg"],
      ["도둑 도구", "도구", "—"],
      ["도둑의 꾸러미", "장비 묶음", "—"],
    ],
  },
  {
    id: "wizard-start",
    label: "위저드 시작 장비",
    description: "주문 시전에 필요한 지팡이와 구성 요소 도구입니다.",
    classes: ["위저드"],
    items: [
      ["지팡이", "무기/주문 도구", "2 kg"],
      ["구성 요소 주머니", "주문 도구", "1 kg"],
      ["학자의 꾸러미", "장비 묶음", "—"],
    ],
  },
  {
    id: "cleric-start",
    label: "클레릭 시작 장비",
    description: "방어구와 방패, 신성한 주문 도구 중심의 시작 장비입니다.",
    classes: ["클레릭"],
    items: [
      ["스케일 메일", "평갑", "22.5 kg"],
      ["방패", "방패", "3 kg"],
      ["메이스", "무기", "2 kg"],
      ["성표", "성물", "—"],
      ["사제의 꾸러미", "장비 묶음", "—"],
    ],
  },
  {
    id: "fighter-start",
    label: "파이터 시작 장비",
    description: "중갑과 방패, 군용 무기 중심의 시작 장비입니다.",
    classes: ["파이터"],
    items: [
      ["체인 메일", "중갑", "27.5 kg"],
      ["방패", "방패", "3 kg"],
      ["롱소드", "무기", "1.5 kg"],
      ["라이트 크로스보우", "무기", "2.5 kg"],
      ["볼트 20개", "탄약", "0.75 kg"],
      ["던전 탐험가 팩", "장비 묶음", "—"],
    ],
  },
  {
    id: "scholar",
    label: "학자의 꾸러미",
    description: "연구와 기록에 필요한 문구와 자료를 담은 꾸러미입니다.",
    classes: ["로그", "위저드", "클레릭", "파이터"],
    items: [
      ["배낭", "기타", "2 kg"],
      ["책", "문서", "1 kg"],
      ["잉크병", "도구", "—"],
      ["깃펜", "도구", "—"],
      ["양피지 10장", "문서", "—"],
    ],
  },
  {
    id: "priest",
    label: "사제의 꾸러미",
    description: "신전 의식과 치유 활동에 필요한 장비입니다.",
    classes: ["클레릭"],
    items: [
      ["배낭", "기타", "2 kg"],
      ["담요", "야영 장비", "1.5 kg"],
      ["성표", "성물", "—"],
      ["향 5개", "의식 도구", "—"],
      ["보통 의복", "의복", "1.5 kg"],
    ],
  },
  {
    id: "explorer",
    label: "탐험가 꾸러미",
    description: "장거리 이동과 야영에 초점을 둔 장비입니다.",
    classes: ["파이터", "로그"],
    items: [
      ["배낭", "기타", "2 kg"],
      ["침낭", "야영 장비", "3.5 kg"],
      ["식량 10일분", "소모품", "5 kg"],
      ["물가죽", "야영 장비", "2.5 kg"],
      ["횃불 10개", "도구", "1 kg"],
    ],
  },
];
const initialEquipmentBundle = equipmentBundles[0];
type ChoiceOption = {
  id: string;
  label: string;
  description: string;
  details: string[];
  keywords?: Record<string, string>;
};
const raceChoices: ChoiceOption[] = raceOptions.map((race) => ({
  id: race.id,
  label: race.label,
  description: race.description,
  details: [...race.traits, `언어: ${race.languages.join(", ")}`],
}));
function subraceChoicesFor(value: string): ChoiceOption[] {
  const parent = raceOptions.find((race) => race.id === value);
  return (
    parent?.subraces.map((subrace) => ({
      id: subrace.id,
      label: subrace.label,
      description: subrace.description,
      details: [...subrace.traits, `부모 종족: ${parent.label}`],
    })) ?? []
  );
}
// 출처: docs/dnd5th.pdf, 제3장 「클래스」의 1레벨 클래스 요소를 UI용으로 요약함.
// 같은 이름이어도 클래스에 따라 규칙이 다른 요소는 직업별로 분리한다.
const classFeatureHelp: Record<string, Record<string, string>> = {
  바바리안: {
    분노: "보너스 행동으로 분노에 들어가면 힘 기반 공격의 피해가 증가하고, 타격·관통·참격 피해에 저항합니다. 힘 판정과 힘 내성에도 이점을 얻으며, 공격하지 않거나 피해를 받지 않으면 분노가 끝납니다.",
    "비무장 방어":
      "갑옷을 입지 않을 때 AC는 10 + 민첩 수정치 + 건강 수정치로 계산합니다. 방패를 들면 방패의 AC 보너스를 더할 수 있습니다.",
  },
  바드: {
    주문시전:
      "매력으로 주문을 시전합니다. 알고 있는 바드 주문을 선택하고, 바드 표의 주문 슬롯을 사용해 시전합니다.",
    "바드의 고양감":
      "보너스 행동으로 60ft 안의 다른 생명체 한 명에게 고양감 주사위(d6)를 줍니다. 그 생명체는 10분 안에 능력 판정·명중 굴림·내성 굴림에 주사위를 더할 수 있습니다.",
  },
  클레릭: {
    주문시전:
      "지혜가 클레릭 주문의 주문시전 능력치입니다. 클레릭 주문 목록에서 주문을 준비하고, 클레릭 표의 주문 슬롯으로 시전합니다.",
    "신성 권역":
      "신이 관장하는 영역 하나를 선택합니다. 선택한 권역은 1레벨부터 권역 주문과 추가 클래스 요소를 제공합니다.",
  },
  드루이드: {
    드루이드어:
      "드루이드의 비밀 언어를 알고 있습니다. 드루이드어로 대화하거나 숨겨진 메시지를 남길 수 있습니다.",
    주문시전:
      "지혜로 자연의 주문을 시전합니다. 드루이드 주문 목록에서 주문을 준비하고, 드루이드 표의 주문 슬롯을 사용합니다.",
  },
  파이터: {
    "전투 방식":
      "궁술, 방어, 결투, 대형 무기 전투 등 전투 방식 하나를 선택해 해당 방식의 지속적인 이점을 얻습니다.",
    "재기의 바람":
      "자기 턴에 추가 행동으로 1d10 + 파이터 레벨만큼 HP를 회복합니다. 사용 후 짧은 휴식 또는 긴 휴식을 해야 다시 사용할 수 있습니다.",
  },
  몽크: {
    "비무장 방어":
      "갑옷이나 방패를 사용하지 않을 때 AC는 10 + 민첩 수정치 + 지혜 수정치로 계산합니다.",
    무술: "비무장 공격과 수도 무기를 사용할 때 민첩을 공격·피해 굴림에 사용할 수 있습니다. 공격 행동 후 보너스 행동으로 비무장 공격을 할 수도 있습니다.",
  },
  팔라딘: {
    "신성한 감각":
      "행동으로 다음 턴이 끝날 때까지 60ft 안의 천족·악마·언데드를 감지합니다. 축복받거나 모독된 장소와 물체도 감지할 수 있습니다.",
    "치유의 손길":
      "치유력을 저장한 손길의 풀에서 HP를 회복시킵니다. 한 번에 전부 또는 여러 대상에게 나누어 사용할 수 있으며, 긴 휴식 후 회복합니다.",
  },
  레인저: {
    주적: "특정 유형의 주적을 추적하고 관련 정보를 기억하는 데 이점을 얻습니다. 주적의 언어 하나도 배울 수 있습니다.",
    "자연 탐험가":
      "선택한 지형에서 이동·추적·채집·길 찾기에 능숙해집니다. 해당 지형을 여행할 때 파티의 탐험에도 이점을 줍니다.",
  },
  로그: {
    숙달: "숙련된 기술 또는 도구 두 가지를 선택합니다. 해당 숙련을 사용하는 판정에 숙련 보너스를 두 배로 적용합니다.",
    "암습 공격":
      "턴마다 한 번, 기교 또는 원거리 무기 공격이 명중하면 이점이 있거나 목표 옆에 아군이 있고 불리점이 없을 때 추가 피해를 줍니다.",
    "도둑의 속어":
      "도둑들의 은어와 암호를 익혀 평범한 대화 속에 비밀 메시지를 숨기고, 도둑의 표식과 상징을 해석할 수 있습니다.",
  },
  소서러: {
    주문시전:
      "매력이 소서러 주문의 주문시전 능력치입니다. 알고 있는 소서러 주문을 주문 슬롯으로 시전합니다.",
    "마법의 기원":
      "타고난 마법의 근원 하나를 선택합니다. 선택한 기원은 이후 레벨에서 추가 소서러 요소를 제공합니다.",
  },
  워락: {
    "다른 세상의 후원자":
      "초월적 존재와 계약을 맺은 후원자 하나를 선택합니다. 후원자는 1레벨부터 후원자 주문과 추가 요소를 제공합니다.",
    "계약 마법":
      "워락 주문은 제한된 수의 주문 슬롯을 사용하지만, 사용한 슬롯은 짧은 휴식 또는 긴 휴식이 끝나면 회복됩니다. 슬롯은 항상 해당 슬롯 레벨로 시전됩니다.",
  },
  위저드: {
    주문시전:
      "지능으로 주문책에 기록한 위저드 주문을 시전합니다. 주문을 준비하고 위저드 표의 주문 슬롯을 사용합니다.",
    "비전 회복":
      "하루에 한 번 짧은 휴식 중 주문책을 연구해 사용한 주문 슬롯 일부를 회복합니다. 회복 가능한 슬롯 레벨 합계는 위저드 레벨의 절반 이하입니다.",
  },
};
const spellDescriptions: Record<string, string> = {
  "산성 거품": "산성 거품을 발사해 대상에게 1d6 산 피해를 줍니다.",
  "냉기 분사": "부채꼴 냉기의 범위 안 생명체에게 냉기 피해를 줍니다.",
  "춤추는 빛": "횃불처럼 빛나는 네 개의 작은 광원을 만들어 조종합니다.",
  "화염 화살": "불타는 화살을 발사해 명중 시 1d10 화염 피해를 줍니다.",
  친구: "집중하는 동안 한 생명체에 대한 매력 판정에 이점을 얻습니다.",
  전언: "짧은 메시지를 멀리 있는 특정 생명체에게 속삭여 보냅니다.",
  "하급 환영": "소리나 정지된 환영의 작은 이미지를 만들어냅니다.",
  "독 분사": "독성 기체를 내뿜어 건강 내성에 실패한 대상에게 독 피해를 줍니다.",
  요술: "작은 마법 효과를 만들어 사소한 감각적 표현이나 정리를 합니다.",
  "진실의 일격": "다음 공격 굴림에서 목표를 공격할 때 이점을 얻습니다.",
  저항: "대상은 집중하는 동안 다음 내성 굴림 하나에 d4를 더할 수 있습니다.",
  단순마술: "작은 기적을 일으켜 소리, 불꽃, 바람 같은 사소한 효과를 만듭니다.",
  "상처 가해": "접촉한 대상에게 3d10 사령 피해를 주는 근접 주문 공격입니다.",
  성역화: "공격자가 다른 대상을 공격하지 않으면 목표를 공격하기 어렵게 만듭니다.",
  "경로 파악": "북쪽을 알고 있다면 목표 지점까지 가는 가장 짧고 직접적인 경로를 찾습니다.",
  치유: "접촉한 생명체의 HP를 1d8 + 주문시전 능력 수정치만큼 회복합니다.",
  "마법 탐지": "주변 30ft 안의 마법 존재를 감지하고 학교를 식별합니다.",
  "식별": "물체 하나의 마법적 성질과 사용 방법을 알아냅니다.",
  "언어 변환": "선택한 언어를 듣고 읽을 수 있게 됩니다.",
  "인간형 매혹": "인간형 하나를 매혹해 우호적인 지인처럼 여기게 만듭니다.",
  "자기 위장": "자신의 외모와 옷차림을 다른 모습으로 바꿉니다.",
  "조용한 영상": "집중하는 동안 소리 없는 물체나 생명체의 환영을 만듭니다.",
  "타오르는 손길": "손에서 화염을 내뿜어 범위 안 대상에게 3d6 화염 피해를 줍니다.",
  가이던스:
    "능력 판정 하나를 하기 전에 대상이 d4를 굴려 판정에 더할 수 있습니다. 집중이 필요합니다.",
  빛: "물체 하나가 20분 동안 밝은 빛을 내도록 합니다.",
  "신성한 불꽃": "대상이 민첩 내성 굴림에 실패하면 광휘 피해를 입습니다.",
  "죽어가는 자 살리기": "생명력이 0인 생명체 하나를 안정화합니다.",
  수선: "물체의 작은 균열이나 찢어진 부분을 고칩니다.",
  축복: "범위 안의 최대 세 대상이 공격 굴림과 내성 굴림에 d4를 더합니다. 집중이 필요합니다.",
  "치유의 단어":
    "거리 안의 생명체 하나가 1d4 + 주문시전 능력 수정치만큼 생명력을 회복합니다.",
  "상처 치료":
    "접촉한 생명체 하나가 1d8 + 주문시전 능력 수정치만큼 생명력을 회복합니다.",
  "신앙의 방패": "대상의 AC에 +2 보너스를 부여합니다. 집중이 필요합니다.",
  명령: "대상에게 한 단어 명령을 내리고, 대상은 내성 굴림에 실패하면 다음 턴에 그 명령을 따릅니다.",
  "인도하는 화살":
    "명중 시 4d6 광휘 피해를 주며, 다음 공격자는 대상에 대한 다음 공격 굴림에 이점을 얻습니다.",
  "마법사의 손": "최대 3m 거리의 물체를 조작할 수 있는 유령 손을 만듭니다.",
  불화살: "명중 시 1d10 화염 피해를 주는 불타는 광선을 발사합니다.",
  "서리 광선": "명중 시 1d8 냉기 피해를 주고 대상의 속도를 3m 줄입니다.",
  "충격의 손아귀":
    "접촉한 대상에게 1d8 번개 피해를 주며 반응을 사용할 수 없게 합니다.",
  "마법 갑주":
    "갑옷을 입지 않은 대상의 기본 AC를 13 + 민첩 수정치로 설정합니다.",
  "마법 화살":
    "자동으로 명중하는 화살 세 개를 만들어 각각 1d4 + 1 역장 피해를 줍니다.",
  방패: "자신의 다음 턴 시작까지 AC에 +5 보너스를 얻고, 유발한 공격을 무효화할 수 있습니다.",
  수면: "생명력 합계가 낮은 생명체부터 잠들게 합니다. 언데드와 매력에 면역인 생명체에는 효과가 없습니다.",
  천둥파도:
    "자신에게서 퍼지는 천둥 에너지로 범위 안 대상에게 2d8 피해를 주고 밀어냅니다.",
  "탐지 마법": "감각으로는 보이지 않는 마법의 존재와 위치를 감지합니다.",
  "깃털 낙하": "범위 안에서 떨어지는 생명체의 낙하 속도를 늦춥니다.",
  "안개 구름": "범위 안을 짙은 안개로 가립니다. 집중이 필요합니다.",
};
const classChoices: ChoiceOption[] = classOptions.map((option) => ({
  id: option.id,
  label: option.label,
  description: option.description,
  details: [
    `생명력 주사위: ${option.hitDie}`,
    `내성 숙련: ${option.savingThrows.join(", ")}`,
    `기술 선택: ${option.skillChoiceCount}개`,
  ],
  keywords: Object.fromEntries(
    option.features.map((feature) => [
      feature,
      classFeatureHelp[option.id]?.[feature] ??
        "이 클래스 요소의 자세한 규칙은 룰북의 해당 클래스 항목을 참조하세요.",
    ]),
  ),
}));
const backgroundFeatureHelp: Record<string, string> = {
  수행사제:
    "신앙 공동체의 도움을 받아 기본적인 숙박과 지원을 받을 수 있습니다.",
  사기꾼: "거짓 신분과 관련 문서를 활용할 수 있습니다.",
  범죄자: "범죄 조직과 연락을 주고받을 수 있습니다.",
  연예인: "공연을 통해 숙식과 호의를 얻을 수 있습니다.",
  "민중 영웅": "평민 공동체에서 환대와 은신처를 받을 수 있습니다.",
  "길드 장인": "길드의 지원과 숙련된 동료의 도움을 받을 수 있습니다.",
  은둔자: "고립된 생활에서 얻은 중요한 발견을 알고 있습니다.",
  귀족: "높은 사회적 지위와 가문 네트워크를 활용할 수 있습니다.",
  이방인: "야생에서 길을 잃지 않고 식량과 물을 찾을 수 있습니다.",
  현자: "지식과 관련된 정보를 조사하고 기억하는 데 능숙합니다.",
  선원: "선박 통행과 선원 사회의 도움을 받을 수 있습니다.",
  군인: "군대의 계급과 동료 네트워크를 활용할 수 있습니다.",
  부랑아: "도시의 비밀 통로와 거리 네트워크를 알고 있습니다.",
};
const backgroundProficiencyHelp: Record<string, string> = {
  수행사제: "언어 2개",
  사기꾼: "변장 도구, 위조 도구",
  범죄자: "도둑 도구, 게임 도구 1개",
  연예인: "변장 도구, 악기 1개",
  "민중 영웅": "장인 도구 1개, 육상 탈것",
  "길드 장인": "장인 도구 1개, 언어 1개",
  은둔자: "약초학 도구, 언어 1개",
  귀족: "게임 도구 1개, 언어 1개",
  이방인: "악기 1개, 언어 1개",
  현자: "언어 2개",
  선원: "항해 도구, 수상 탈것",
  군인: "게임 도구 1개, 육상 탈것",
  부랑아: "변장 도구, 도둑 도구",
};
const backgroundChoices: ChoiceOption[] = backgroundOptions.map((option) => ({
  id: option.id,
  label: option.label,
  description: option.description,
  details: [
    `기술 숙련: ${option.skills.join(", ")}`,
    `도구·언어 숙련: ${backgroundProficiencyHelp[option.id] ?? "없음"}`,
    `장비: ${option.equipment.join(", ")}`,
    `배경 특성: ${backgroundFeatureHelp[option.id] ?? "배경에 따른 사회적 이점을 얻습니다."}`,
  ],
}));
const alignmentChoices: ChoiceOption[] = [
  ["질서 선", "사회적으로 올바르다고 여기는 행동을 하려고 합니다."],
  [
    "중립 선",
    "다른 사람들이 도움을 요청할 때 그들을 돕기 위해 최선을 다합니다.",
  ],
  [
    "혼돈 선",
    "다른 사람의 기대나 예상은 신경 쓰지 않고 오로지 자신의 양심에 따라 움직입니다.",
  ],
  ["질서 중립", "법과 전통, 개인의 규범에 따라 행동합니다."],
  [
    "중립",
    "도덕적 문제에 명확한 태도를 취하지 않고 그때그때 최선이라 생각하는 행동을 합니다.",
  ],
  [
    "혼돈 중립",
    "자기 마음이 내키는 대로 행동하며 개인의 자유를 최우선으로 생각합니다.",
  ],
  [
    "질서 악",
    "전통과 법, 질서에서 벗어나지 않는 한 자신의 욕망을 최우선으로 추구합니다.",
  ],
  [
    "중립 악",
    "연민이나 후회 따위는 생각하지 않으며, 보복이나 처벌이 없을 것 같다면 무엇이든 원하는 대로 저지릅니다.",
  ],
  [
    "혼돈 악",
    "탐욕, 증오, 피에 대한 갈망에 따라 무자비하게 폭력을 휘두르며 원하는 것을 차지합니다.",
  ],
].map(([label, description]) => ({
  id: label,
  label,
  description,
  details: ["성향은 도덕적 성향과 사회·질서 성향을 조합해 표현합니다."],
}));

type BlueprintProps = {
  sessionId: string;
  setupApi: {
    getPlayPreparation: NonNullable<SetupApi["getPlayPreparation"]>;
    resolveBlueprint?: SetupApi["resolveBlueprint"];
    addBlueprintChild?: SetupApi["addBlueprintChild"];
    publishBlueprint?: SetupApi["publishBlueprint"];
  };
  sessionApi: Pick<AdventureSessionApi, "read">;
};
type CharacterDraftSnapshot = {
  name: string;
  race: string;
  characterClass: string;
  subrace: string;
  background: string;
  alignment: string;
  scores: number[];
  activeSection: SheetSection;
  equipmentTab: "owned" | "carried";
  selectedSpells?: string[];
  selectedCantrips?: string[];
  equipmentBundle?: string;
  equipmentItems?: EquipmentItem[];
};
function readCharacterDraft(key: string): Partial<CharacterDraftSnapshot> {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return {};
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === "object"
      ? (parsed as Partial<CharacterDraftSnapshot>)
      : {};
  } catch {
    return {};
  }
}

function useBlueprintFields(blueprint?: BlueprintProps) {
  const [nodes, setNodes] = useState<ReturnType<typeof flattenNodes>>([]);
  const [packageId, setPackageId] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [edition, setEdition] = useState<"DND_5E_2014" | "DND_5E_2024">("DND_5E_2014");
  const [values, setValues] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!blueprint) return;
    let active = true;
    let sessionEdition: "DND_5E_2014" | "DND_5E_2024" | undefined;
    void blueprint.sessionApi
      .read(blueprint.sessionId)
      .then((session) => {
        if (!active) return;
        sessionEdition = session.characterEdition;
        setPackageId(session.scenarioPackageId ?? null);
        return session.scenarioPackageId
          ? blueprint.setupApi.getPlayPreparation(session.scenarioPackageId)
          : null;
      })
      .then((preparation) => {
        if (active && preparation) {
          setEdition(sessionEdition ?? preparation.characterCreationBlueprint.edition ?? "DND_5E_2014");
          setRevision(preparation.characterCreationBlueprint.revision ?? 0);
          setNodes(
            flattenNodes(preparation.characterCreationBlueprint.roots).filter(
              (node) => !isRemovedCharacterDetail(node),
            ),
          );
        }
      });
    return () => {
      active = false;
    };
  }, [blueprint]);
  const mappedKeys = useMemo(
    () =>
      new Set([
        "character_name",
        "name",
        "race",
        "ancestry",
        "species",
        "option_selections",
        "subrace",
        "character_class",
        "class",
        "skill_choices",
        "feature_choices",
        "subclass",
        "background",
        "alignment",
        "level",
        "experience_points",
        "ability_score_method",
        "starting_ability_scores",
        "strength",
        "dexterity",
        "constitution",
        "intelligence",
        "wisdom",
        "charisma",
        "str",
        "dex",
        "con",
        "int",
        "wis",
        "cha",
        "equipment",
        "acquisition_method",
        "class_choices",
        "background_items",
        "magic",
        "cantrips",
        "spells",
        "personality_traits",
        "ideals",
        "bonds",
        "flaws",
        "proficiency_bonus",
        "saving_throws",
        "skills",
        "passive_wisdom",
        "armor_class",
        "initiative",
        "speed",
        "hit_point_maximum",
        "hit_dice",
        "attacks_spellcasting",
        "other_proficiencies_languages",
        "features_traits",
        "appearance",
        "appearance_description",
        "외모",
        "외형",
        "외형_묘사",
      ]),
    [],
  );
  const find = (keys: string[]) =>
    nodes.find((node) => keys.includes(node.key));
  const value = (keys: string[], fallback = "") => {
    const node = find(keys);
    return node ? (values[node.id] ?? node.value ?? fallback) : fallback;
  };
  function change(keys: string[], next: string) {
    const node = find(keys);
    if (!node) return;
    setValues((current) => ({ ...current, [node.id]: next }));
    if (packageId && blueprint?.setupApi.resolveBlueprint)
      void blueprint.setupApi
        .resolveBlueprint(packageId, node.id, next, revision)
        .then((result) => {
          if (
            typeof result === "object" &&
            result &&
            "revision" in result &&
            typeof result.revision === "number"
          )
            setRevision(result.revision);
        })
        .catch(() => undefined);
  }
  const extras = nodes.filter((node) => !mappedKeys.has(node.key));
  return { value, change, extras, values, edition };
}

export function CharacterSheetCreatorView({
  onSave,
  blueprint,
}: {
  onSave: (draft: Omit<CharacterCreationDraft, "sessionId">) => Promise<void>;
  blueprint?: BlueprintProps;
}) {
  const blueprintFields = useBlueprintFields(blueprint);
  const lockedEdition = blueprintFields.edition;
  const draftStorageKey = `dnd-character-draft:${blueprint?.sessionId ?? "local"}`;
  const initialDraft = readCharacterDraft(draftStorageKey);
  const restoredSection: SheetSection =
    initialDraft.activeSection === "combat"
      ? "abilities"
      : (initialDraft.activeSection ?? "basic");
  const [name, setName] = useState(initialDraft.name ?? "");
  const [race, setRace] = useState(initialDraft.race ?? "");
  const [characterClass, setCharacterClass] = useState(
    initialDraft.characterClass ?? "",
  );
  const [subrace, setSubrace] = useState(initialDraft.subrace ?? "");
  const [background, setBackground] = useState(initialDraft.background ?? "");
  const [alignment, setAlignment] = useState(initialDraft.alignment ?? "");
  const [activeSection, setActiveSection] =
    useState<SheetSection>(restoredSection);
  const [scores, setScores] = useState(
    initialDraft.scores?.length === 6
      ? initialDraft.scores
      : [0, 0, 0, 0, 0, 0],
  );
  const [equipmentTab] = useState<"owned" | "carried">(
    initialDraft.equipmentTab ?? "owned",
  );
  const [selectedSpells, setSelectedSpells] = useState<string[]>(
    initialDraft.selectedSpells ?? [],
  );
  const [selectedCantrips, setSelectedCantrips] = useState<string[]>(
    initialDraft.selectedCantrips ?? [],
  );
  const [equipmentBundle, setEquipmentBundle] = useState(
    initialDraft.equipmentBundle ?? initialEquipmentBundle.id,
  );
  const [equipmentItems, setEquipmentItems] = useState<EquipmentItem[]>(
    initialDraft.equipmentItems ?? [
      ...equipmentForBundle(initialEquipmentBundle),
    ],
  );
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => {
    try {
      window.localStorage.setItem(
        draftStorageKey,
        JSON.stringify({
          name,
          race,
          characterClass,
          subrace,
          background,
          alignment,
          scores,
          activeSection,
          equipmentTab,
          selectedSpells,
          selectedCantrips,
          equipmentBundle,
          equipmentItems,
        } satisfies CharacterDraftSnapshot),
      );
    } catch {
      /* storage may be unavailable */
    }
  }, [
    draftStorageKey,
    name,
    race,
    characterClass,
    subrace,
    background,
    alignment,
    scores,
    activeSection,
    equipmentTab,
    selectedSpells,
    selectedCantrips,
    equipmentBundle,
    equipmentItems,
  ]);
  const modifierValue = (value: number) => Math.floor((value - 10) / 2);
  const hasScore = (value: number) => Number.isFinite(value) && value > 0;
  const mod = (value: number) =>
    hasScore(value) ? `${value >= 10 ? "+" : ""}${modifierValue(value)}` : "—";
  const field = (keys: string[], fallback = "") =>
    blueprintFields.value(keys, fallback);
  const effectiveName = name || field(["character_name", "name"]);
  const effectiveClass = characterClass || field(["character_class", "class"]);
  const effectiveRace = race || field(["race", "ancestry", "species"]);
  const effectiveSubrace = subrace || field(["subrace", "subrace_name"]);
  const effectiveBackground = background || field(["background"]);
  const effectiveAlignment = alignment || field(["alignment"]);
  const effectiveLevel = field(["level"], "1");
  const baseScores = scores.map(
    (score, index) => score || Number(field(scoreKeys[index])),
  );
  const raceBonus =
    effectiveRace === "드워프"
      ? [0, 0, 2, 0, 0, 0]
      : effectiveRace === "엘프"
        ? [0, 2, 0, 0, 0, 0]
        : effectiveRace === "하플링"
          ? [0, 2, 0, 0, 0, 0]
          : effectiveRace === "인간"
            ? [1, 1, 1, 1, 1, 1]
            : [0, 0, 0, 0, 0, 0];
  const subraceBonus =
    effectiveSubrace === "산 드워프"
      ? [2, 0, 0, 0, 0, 0]
      : effectiveSubrace === "언덕 드워프"
        ? [0, 0, 0, 0, 1, 0]
        : effectiveSubrace === "하이 엘프"
          ? [0, 0, 0, 1, 0, 0]
          : effectiveSubrace === "우드 엘프"
            ? [0, 0, 0, 0, 1, 0]
            : effectiveSubrace === "라이트풋 하플링"
              ? [0, 0, 0, 0, 0, 1]
              : effectiveSubrace === "스타우트 하플링"
                ? [0, 0, 1, 0, 0, 0]
                : [0, 0, 0, 0, 0, 0];
  const effectiveScores = baseScores.map((score, index) =>
    score > 0 ? score + raceBonus[index] + subraceBonus[index] : 0,
  );
  const activeArt = effectiveClass
    ? art[effectiveClass as keyof typeof art]
    : undefined;
  const selectedClass = classOptions.find(
    (option) => option.id === effectiveClass,
  );
  const selectedRace = raceOptions.find((option) => option.id === effectiveRace);
  const selectedSubrace = selectedRace?.subraces.find(
    (option) => option.id === effectiveSubrace,
  );
  const selectedBackground = backgroundOptions.find(
    (option) => option.id === effectiveBackground,
  );
  const raceSpeed = effectiveRace
    ? ((
        {
          드워프: "7.5m",
          하플링: "7.5m",
          노움: "7.5m",
          엘프: "9m",
          인간: "9m",
          드래곤본: "9m",
          "하프 엘프": "9m",
          "하프 오크": "9m",
          티플링: "9m",
        } as Record<string, string>
      )[effectiveRace] ?? "—")
    : "—";
  const derivedHp =
    selectedClass && hasScore(effectiveScores[2])
      ? `${Math.max(1, Number(selectedClass.hitDie.slice(1)) + modifierValue(effectiveScores[2]))}/${Math.max(1, Number(selectedClass.hitDie.slice(1)) + modifierValue(effectiveScores[2]))}`
      : "—";
  const derivedAc = hasScore(effectiveScores[1])
    ? (() => {
        const dexterityModifier = modifierValue(effectiveScores[1]);
        const armor = equipmentItems.some((item) => item[0] === "체인 메일")
          ? 16
          : equipmentItems.some((item) => item[0] === "스케일 메일")
            ? 14 + Math.min(2, dexterityModifier)
            : equipmentItems.some((item) => item[0] === "가죽 갑옷")
              ? 11 + dexterityModifier
              : 10 + dexterityModifier;
        return String(
          armor + (equipmentItems.some((item) => item[0] === "방패") ? 2 : 0),
        );
      })()
    : "—";
  const derivedProficiency = selectedClass
    ? `+${2 + Math.floor((Math.max(1, Number(effectiveLevel) || 1) - 1) / 4)}`
    : "—";
  const derivedInitiative = hasScore(effectiveScores[1])
    ? mod(effectiveScores[1])
    : "—";
  const spellAbilityScore =
    effectiveClass === "위저드" ? effectiveScores[3] : effectiveScores[4];
  useEffect(() => {
    const option = classOptions.find((item) => item.id === effectiveClass);
    if (!option?.canCastSpells) {
      setSelectedSpells([]);
      setSelectedCantrips([]);
      return;
    }
    const levelNumber = Math.max(1, Number(effectiveLevel) || 1);
    const castingModifier = spellAbilityScore > 0 ? Math.floor((spellAbilityScore - 10) / 2) : 0;
    const spellLimit = effectiveClass === "위저드" ? 6 : Math.max(1, levelNumber + castingModifier);
    setSelectedSpells((current) =>
      current
        .filter((spell) => option.firstLevelSpells.includes(spell))
        .slice(0, spellLimit),
    );
    setSelectedCantrips((current) =>
      current.filter((spell) => option.cantrips.includes(spell)).slice(0, 3),
    );
  }, [effectiveClass, effectiveLevel, spellAbilityScore]);
  useEffect(() => {
    const startingBundle = equipmentBundles.find((bundle) => bundle.id === startingBundleIdByClass[effectiveClass]);
    if (!startingBundle || equipmentBundle === startingBundle.id) return;
    setEquipmentBundle(startingBundle.id);
    setEquipmentItems(equipmentForBundle(startingBundle));
  }, [effectiveClass, equipmentBundle]);
  async function save() {
    if (lockedEdition !== "DND_5E_2014") {
      setMessage("이 세션의 판본 계약은 아직 캐릭터 생성을 지원하지 않습니다.");
      return;
    }
    if (
      !effectiveName.trim() ||
      !effectiveClass ||
      baseScores.some((value) => !value)
    ) {
      setMessage("이름, 직업, 능력치를 먼저 입력하세요.");
      return;
    }
    setSaving(true);
    setMessage("저장 중입니다...");
    try {
      const requiredSkillCount = selectedClass?.skillChoiceCount ?? 0;
      const skillProficiencies = Array.from(new Set([
        ...(selectedBackground?.skills ?? []),
        ...(selectedClass?.skillChoices ?? []).slice(0, requiredSkillCount),
      ]));
      const cantripMinimum = ({ 클레릭: 3, 소서러: 4, 워락: 2, 위저드: 3 } as Record<string, number>)[effectiveClass] ?? 0;
      const spellMinimum = ({ 클레릭: 1, 소서러: 2, 워락: 2, 위저드: 6 } as Record<string, number>)[effectiveClass] ?? 0;
      const fillSelections = (selected: string[], options: string[], minimum: number) =>
        Array.from(new Set([...selected, ...options])).slice(0, minimum);
      const cantrips = fillSelections(selectedCantrips, selectedClass?.cantrips ?? [], cantripMinimum);
      const learnedOrPreparedSpells = fillSelections(selectedSpells, selectedClass?.firstLevelSpells ?? [], spellMinimum);
      const armorNames = ["가죽 갑옷", "스터디드 레더", "하이드", "체인 셔츠", "스케일 메일", "브레스트플레이트", "하프 플레이트", "링 메일", "체인 메일", "스플린트", "플레이트"];
      const equippedArmor = equipmentItems.map(item => item[0]).find(item => armorNames.includes(item)) ?? "";
      const equippedShield = equipmentItems.some(item => item[0] === "방패");
      const ownedWeaponIds = equipmentItems
        .map(([name]) => weaponIdByName[name])
        .filter((id): id is string => Boolean(id));
      const mainHandWeaponId = ["longsword", "shortsword", "mace", "quarterstaff", "dagger"]
        .find((weaponId) => ownedWeaponIds.includes(weaponId));
      await onSave({
        edition: lockedEdition,
        characterName: effectiveName.trim(),
        level: Number(effectiveLevel) || 1,
        inspiration: false,
        race: effectiveRace || "인간",
        characterClass: effectiveClass,
        background: effectiveBackground || "현자",
        startingAbilities: baseScores
          .map((value, i) => `${scoreKeys[i][0]}=${value}`)
          .join(","),
        characterBuild: JSON.stringify({
          schemaVersion: 1,
          subclass: subclassesFor(effectiveClass)[0]?.id ?? "",
          skillProficiencies,
          expertise: [],
        equipmentSelections: {
          equipmentBundle,
          armor: equippedArmor,
          weaponAndShield: effectiveClass === "파이터" ? "롱소드와 방패"
            : effectiveClass === "클레릭" ? "메이스와 방패" : "",
          rangedWeapon: ownedWeaponIds.includes("light-crossbow") ? "라이트 크로스보우와 볼트 20개" : "",
        },
        ownedEquipment: equipmentItems.map(([name]) => name),
        ownedWeaponIds,
        equippedItems: { armor: equippedArmor, shield: equippedShield, mainHandWeaponId },
          ruleChoices: {},
          baseStats: baseScores,
          stats: effectiveScores,
          race: effectiveRace,
          subrace: effectiveSubrace,
          raceBonus,
          subraceBonus,
          raceLanguages: selectedRace?.languages ?? [],
          raceTraits: selectedRace?.traits ?? [],
          subraceTraits: selectedSubrace?.traits ?? [],
          classFeatures: selectedClass?.features ?? [],
          hitDie: selectedClass?.hitDie ?? null,
          savingThrows: selectedClass?.savingThrows ?? [],
          classSkillChoices: selectedClass?.skillChoices ?? [],
          background: effectiveBackground,
          backgroundSkills: selectedBackground?.skills ?? [],
          backgroundProficiencies:
            backgroundProficiencyHelp[selectedBackground?.id ?? ""] ?? "",
          backgroundFeature:
            backgroundFeatureHelp[selectedBackground?.id ?? ""] ?? null,
          backgroundEquipment: selectedBackground?.equipment ?? [],
          alignment: effectiveAlignment || "중립 선",
          equipmentBundle,
          equipment: equipmentItems,
          armorClass: Number(derivedAc) || null,
          learnedOrPreparedSpells,
          learnedSpells: effectiveClass === "위저드" ? learnedOrPreparedSpells : [],
          preparedSpells: effectiveClass === "클레릭" ? learnedOrPreparedSpells : [],
          cantrips,
        }),
        characterState: JSON.stringify({
          equippedItems: { armor: equippedArmor, shield: equippedShield, mainHandWeaponId },
          currentHitPoints: 0,
          temporaryHitPoints: 0,
          experience: 0,
        }),
      });
      setMessage("캐릭터 시트를 저장했습니다.");
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="sheet-creator">
      <Sidebar className="creator-sidebar">
        <SidebarHeader>
          <img
            className="creator-compass"
            src="/assets/characters/ui-icons/compass.png"
            alt=""
          />
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarMenu>
              {sheetSections.map(([id, label, icon]) => (
                <SidebarMenuItem key={id}>
                  <SidebarMenuButton
                    type="button"
                    isActive={activeSection === id}
                    onClick={() => setActiveSection(id)}
                  >
                    <img
                      src={`/assets/characters/ui-icons/${icon}.png`}
                      alt=""
                    />
                    <span>{label}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <small>⚙ 시트 테마</small>
          <span>라이트 모드⌄</span>
        </SidebarFooter>
      </Sidebar>
      {message && <p className="sheet-save-status" role="status">{message}</p>}
      <div className="sheet-creator-grid">
        <main className="sheet-main">
          {activeSection === "basic" && (
            <section id="basic" className="sheet-box basic-info-section">
              <SheetTitle n="1" title="기본 정보" />
              <p className="section-lead">
                모험가의 정체성과 출발점을 설정하세요. 선택한 정보는 오른쪽
                미리보기에 즉시 반영됩니다.
              </p>
              <div className="basic-info-grid">
                <div className="basic-info-card basic-info-card-wide">
                  <h4>인물</h4>
                  <label>
                    캐릭터 이름
                    <Input
                      placeholder="이름을 입력하세요"
                      value={effectiveName}
                      onChange={(e) => {
                        setName(e.target.value);
                        blueprintFields.change(
                          ["character_name", "name"],
                          e.target.value,
                        );
                      }}
                    />
                  </label>
                </div>
                <div className="basic-info-card">
                  <h4>혈통</h4>
                  <ChoiceField
                    label="종족"
                    value={effectiveRace}
                    options={raceChoices}
                    onChange={(value) => {
                      setRace(value);
                      setSubrace("");
                      blueprintFields.change(
                        ["race", "ancestry", "species"],
                        value,
                      );
                      blueprintFields.change(["subrace", "subrace_name"], "");
                    }}
                  />
                  <ChoiceField
                    label="하위 종족"
                    value={effectiveSubrace}
                    options={subraceChoicesFor(effectiveRace)}
                    disabled={
                      !effectiveRace ||
                      subraceChoicesFor(effectiveRace).length === 0
                    }
                    onChange={(value) => {
                      setSubrace(value);
                      blueprintFields.change(
                        ["subrace", "subrace_name"],
                        value,
                      );
                    }}
                  />
                </div>
                <div className="basic-info-card">
                  <h4>역할</h4>
                  <ChoiceField
                    label="직업"
                    value={effectiveClass}
                    options={classChoices}
                    onChange={(value) => {
                      setCharacterClass(value);
                      blueprintFields.change(
                        ["character_class", "class"],
                        value,
                      );
                    }}
                  />
                  <ChoiceField
                    label="배경"
                    value={effectiveBackground}
                    options={backgroundChoices}
                    onChange={(value) => {
                      setBackground(value);
                      blueprintFields.change(["background"], value);
                    }}
                  />
                  <ChoiceField
                    label="성향"
                    value={effectiveAlignment}
                    options={alignmentChoices}
                    onChange={(value) => {
                      setAlignment(value);
                      blueprintFields.change(["alignment"], value);
                    }}
                  />
                </div>
              </div>
              <BlueprintExtras
                nodes={blueprintFields.extras}
                values={blueprintFields.values}
                onChange={(node, value) =>
                  blueprintFields.change([node.key], value)
                }
              />
            </section>
          )}
          {activeSection === "abilities" && (
            <section id="abilities" className="sheet-box">
              <SheetTitle n="2" title="능력치" />
              <p className="ability-help">
                D&amp;D 5e 표준 배열: {STANDARD_ARRAY.join(" · ")}. 각 값은 한
                번씩만 사용하세요. 종족 보너스는 계산값에 자동 적용됩니다.
              </p>
              <button
                type="button"
                className="ability-roll-button"
                onClick={() => setScores(rollAbilityScores())}
              >
                4d6 굴림(최저값 제외)
              </button>
              <div className="sheet-abilities">
                {stats.map((stat, i) => (
                  <AbilityCard
                    key={stat}
                    stat={stat}
                    index={i}
                    value={baseScores[i] || 0}
                    options={STANDARD_ARRAY.filter(
                      (option) =>
                        option === baseScores[i] ||
                        !baseScores.includes(option),
                    )}
                    modifier={mod(effectiveScores[i])}
                    onChange={(value) => {
                      if (
                        value &&
                        (!STANDARD_ARRAY.includes(
                          value as (typeof STANDARD_ARRAY)[number],
                        ) ||
                          scores.some((score, j) => j !== i && score === value))
                      ) {
                        setMessage(
                          `표준 배열 값만 중복 없이 입력하세요: ${STANDARD_ARRAY.join(", ")}`,
                        );
                        return;
                      }
                      setScores((old) =>
                        old.map((v, j) => (j === i ? value : v)),
                      );
                      blueprintFields.change(scoreKeys[i], String(value));
                    }}
                  />
                ))}
              </div>
              <div className="ability-derived-section">
                <h4>보조 능력치 · 선택한 규칙 기준</h4>
                <div className="sheet-metrics">
                  <Metric l="♥ HP" v={derivedHp} />
                  <Metric l="◈ AC" v={derivedAc} />
                  <Metric l="♟ 이동속도" v={raceSpeed} />
                  <Metric l="★ 숙련 보너스" v={derivedProficiency} />
                  <Metric l="⚔ 주도권" v={derivedInitiative} />
                </div>
              </div>
            </section>
          )}
          {activeSection === "skills" && (
            <div className="sheet-columns sheet-columns-single">
              <SheetTable
                n="4"
                title="기술"
                items={skillCatalog}
                abilityScores={effectiveScores}
                proficiencyBonus={
                  selectedClass
                    ? 2 +
                      Math.floor(
                        (Math.max(1, Number(effectiveLevel) || 1) - 1) / 4,
                      )
                    : 0
                }
                skillOptions={selectedClass?.skillChoices ?? []}
                backgroundSkills={selectedBackground?.skills ?? []}
                skillChoiceCount={selectedClass?.skillChoiceCount ?? 0}
              />
            </div>
          )}
          {activeSection === "equipment" && (
            <div className="sheet-columns sheet-columns-single">
              <EquipmentPanel
                characterClass={effectiveClass}
                selectedBundle={equipmentBundle}
                ownedItems={equipmentItems}
                onSelectionChange={(bundle, items) => {
                  setEquipmentBundle(bundle.id);
                  setEquipmentItems(items);
                }}
              />
            </div>
          )}
          {activeSection === "book" && (
            <div className="sheet-columns sheet-columns-single">
              <SpellPanel
                characterClass={effectiveClass}
                level={effectiveLevel}
                abilityScores={effectiveScores}
                proficiencyBonus={
                  selectedClass
                    ? 2 +
                      Math.floor(
                        (Math.max(1, Number(effectiveLevel) || 1) - 1) / 4,
                      )
                    : 0
                }
                selectedSpells={selectedSpells}
                selectedCantrips={selectedCantrips}
                onSelectedSpellsChange={setSelectedSpells}
                onSelectedCantripsChange={setSelectedCantrips}
              />
            </div>
          )}
          {activeSection === "note" && (
            <section id="note" className="sheet-box">
              <SheetTitle n="7" title="배경 / 성격" />
              <div className="sheet-fields sheet-personality">
                {["성격 특성", "이상", "유대", "결점"].map((label, i) => (
                  <label key={label}>
                    {label}
                    <Input
                      defaultValue={
                        [
                          "지식을 얻는 것을 무엇보다 즐긴다.",
                          "진실은 언젠가 밝혀져야 한다.",
                          "스승의 가르침을 널리 전하고 싶다.",
                          "때때로 오만하고 타인을 무시한다.",
                        ][i]
                      }
                    />
                  </label>
                ))}
              </div>
            </section>
          )}
          {activeSection === "scroll" && (
            <section id="scroll" className="sheet-box memo-section">
              <SheetTitle n="8" title="메모" />
              <label>
                추가 메모
                <Textarea defaultValue="고대 유적지로 잃어버린 언어에 관심이 많다. 마법 연구를 위해 여정을 떠났다." />
              </label>
            </section>
          )}
        </main>
        <aside className="sheet-preview">
          <h3>미리보기</h3>
          <div className="sheet-parchment">
            <img
              className="parchment-frame"
              src="/assets/characters/ui-icons/parchment-frame.png?v=2"
              alt=""
              aria-hidden="true"
            />
            {activeArt ? (
              <img src={activeArt} alt={`${effectiveClass} 일러스트`} />
            ) : (
              <div className="sheet-empty-portrait">캐릭터 일러스트</div>
            )}
            <h2>{effectiveName || "이름 없는 모험가"}</h2>
            <h4>{effectiveClass || "직업을 선택하세요"}</h4>
            <p>{effectiveRace || "종족을 선택하세요"}</p>
            <p>{effectiveBackground || "배경을 선택하세요"}</p>
            <p>{effectiveAlignment || "성향을 선택하세요"}</p>
            <div className="sheet-metrics">
              <Metric l="♥ HP" v={derivedHp} />
              <Metric l="◈ AC" v={derivedAc} />
              <Metric l="⚔ 주도권" v={derivedInitiative} />
            </div>
            <h5>능력치</h5>
            <div className="sheet-preview-stats">
              {stats.map((stat, i) => (
                <div key={stat}>
                  <span>{stat}</span>
                  <b>{effectiveScores[i] || "—"}</b>
                  <small>{mod(effectiveScores[i])}</small>
                </div>
              ))}
            </div>
            <h5>기술 (숙련된 표시)</h5>
            <p>캐릭터 정보를 입력하면 표시됩니다.</p>
            <button
              className="sheet-save-button"
              onClick={() => void save()}
              disabled={saving || lockedEdition !== "DND_5E_2014"}
            >
              {saving ? "저장 중..." : lockedEdition === "DND_5E_2014" ? "캐릭터 저장하기 →" : "판본 계약 준비 중"}
            </button>
          </div>
        </aside>
      </div>
    </div>
  );
}
function ChoiceField({
  label,
  value,
  options,
  onChange,
  disabled = false,
}: {
  label: string;
  value: string;
  options: ChoiceOption[];
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const selected = options.find((option) => option.id === value);
  const choose = (next: string) => {
    onChange(next);
    setOpen(false);
  };
  const openSheet = () => {
    if (!disabled) setOpen(true);
  };
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openSheet();
    }
  };
  return (
    <>
      <div
        className={`choice-field choice-field-trigger ${disabled ? "is-disabled" : ""}`}
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-label={`${label} 선택`}
        aria-disabled={disabled}
        onClick={openSheet}
        onKeyDown={handleKeyDown}
      >
        <div className="choice-field-heading">
          <span>{label}</span>
          <ChevronRight aria-hidden="true" />
        </div>
        {selected ? (
          <div className="choice-selection">
            <strong>{selected.label}</strong>
            <p>{selected.description}</p>
          </div>
        ) : (
          <small className="choice-field-placeholder">
            {disabled ? "상위 종족을 먼저 선택하세요" : "클릭하여 선택"}
          </small>
        )}
      </div>
      <select
        className="choice-field-native-select"
        aria-label={label}
        value={value}
        disabled={disabled}
        onChange={(event) => choose(event.target.value)}
      >
        <option value="">선택</option>
        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.label}
          </option>
        ))}
      </select>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent>
          <SheetHeader>
            <UiSheetTitle>{label} 선택</UiSheetTitle>
          </SheetHeader>
          <div className="choice-sheet-options">
            {options.map((option) => (
              <Card
                key={option.id}
                className={`choice-sheet-option ${option.id === value ? "selected" : ""}`}
                role="button"
                tabIndex={0}
                onClick={() => choose(option.id)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ")
                    choose(option.id);
                }}
              >
                <CardContent>
                  <strong>{option.label}</strong>
                  <p>{option.description}</p>
                  {option.details.map((detail) => (
                    <small className="choice-option-detail" key={detail}>
                      {detail}
                    </small>
                  ))}
                  {option.keywords ? (
                    <div className="choice-feature-list">
                      {Object.entries(option.keywords).map(
                        ([keyword, description]) => (
                          <Badge
                            className="choice-feature"
                            key={keyword}
                            tabIndex={0}
                          >
                            {keyword}
                            <span
                              className="choice-feature-tooltip"
                              role="tooltip"
                            >
                              {description}
                              <small>
                                출처: D&amp;D 5e 기초 규칙 · 제3장 클래스
                              </small>
                            </span>
                          </Badge>
                        ),
                      )}
                    </div>
                  ) : null}
                </CardContent>
              </Card>
            ))}
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
function AbilityCard({
  stat,
  index,
  value,
  options,
  modifier,
  onChange,
}: {
  stat: string;
  index: number;
  value: number;
  options: readonly number[];
  modifier: string;
  onChange: (value: number) => void;
}) {
  const icons = ["fist", "sword", "heart", "book", "staff", "star"];
  const colors = ["red", "green", "gold", "blue", "violet", "magenta"];
  return (
    <div className={`sheet-ability sheet-ability-${colors[index]}`}>
      <div className="ability-name">
        <img src={`/assets/characters/icons/${icons[index]}.png`} alt="" />
        <b>{stat}</b>
      </div>
      <div className="ability-value">
        <Select
          aria-label={`${stat} 능력치`}
          value={value || ""}
          onChange={(event) => onChange(Number(event.target.value))}
        >
          <option value="">선택</option>
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </Select>
        <strong>{modifier}</strong>
      </div>
    </div>
  );
}
function EquipmentPanel({
  characterClass,
  selectedBundle,
  ownedItems,
  onSelectionChange,
}: {
  characterClass?: string;
  selectedBundle: string;
  ownedItems: EquipmentItem[];
  onSelectionChange: (bundle: EquipmentBundle, items: EquipmentItem[]) => void;
}) {
  const availableBundles = equipmentBundles.filter(
    (bundle) => !characterClass || bundle.classes.includes(characterClass),
  );
  const selectBundle = (bundle: EquipmentBundle) =>
    onSelectionChange(bundle, equipmentForBundle(bundle));
  return (
    <section id="equipment" className="sheet-box equipment-panel">
      <SheetTitle n="5" title="장비" />
      <div className="equipment-columns">
        <div className="equipment-owned">
          <div className="equipment-subtitle">
            <h4>보유 장비</h4>
            <small>현재 캐릭터가 가지고 있는 장비</small>
          </div>
          <div className="equipment-list">
            {ownedItems.map(([name, type, weight]) => (
              <div key={name}>
                <span className="equipment-item-name" tabIndex={0}>
                  <strong>{name}</strong>
                  <small>{type}</small>
                  <span className="equipment-item-tooltip" role="tooltip">
                    <span>
                      {equipmentDescriptions[name] ??
                        "이 장비의 세부 설명은 룰북 장비 항목을 참조하세요."}
                    </span>
                    {equipmentStats[name] && (
                      <strong className="equipment-item-stats">
                        {equipmentStats[name]}
                      </strong>
                    )}
                  </span>
                </span>
                <em>{weight}</em>
                <button type="button" aria-label={`${name} 편집`}>
                  ✎
                </button>
                <button type="button" aria-label={`${name} 삭제`}>
                  ♧
                </button>
              </div>
            ))}
          </div>
          <div className="equipment-total">
            총 무게&nbsp;{" "}
            {ownedItems
              .reduce(
                (total, [, , weight]) => total + Number.parseFloat(weight),
                0,
              )
              .toFixed(1)}{" "}
            / 120 kg
          </div>
        </div>
        <div className="equipment-bundles">
          <div className="equipment-subtitle">
            <h4>선택 가능한 장비 묶음</h4>
            <small>하나를 선택하면 보유 장비가 이 묶음으로 교체됩니다.</small>
          </div>
          <div className="equipment-bundle-list">
            {availableBundles.map((bundle) => (
              <button
                type="button"
                key={bundle.id}
                className={`equipment-bundle ${selectedBundle === bundle.id ? "selected" : ""}`}
                onClick={() => selectBundle(bundle)}
              >
                <span>
                  <strong>{bundle.label}</strong>
                  <small>{bundle.description}</small>
                </span>
                <em>{selectedBundle === bundle.id ? "선택됨" : "선택"}</em>
              </button>
            ))}
          </div>
          {!availableBundles.length && (
            <p className="equipment-empty">
              직업을 먼저 선택하면 선택 가능한 장비 묶음이 표시됩니다.
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
function SpellHoverName({ spell }: { spell: string }) {
  return (
    <span className="spell-hover-target" tabIndex={0}>
      {spell}
      <span className="spell-hover-card" role="tooltip">
        {spellDescriptions[spell] ??
          "이 주문의 자세한 규칙은 룰북 주문 항목을 참조하세요."}
        <small>출처: D&amp;D 5e 기초 규칙 · 제11장</small>
      </span>
    </span>
  );
}
function SpellPanel({
  characterClass,
  level,
  abilityScores,
  proficiencyBonus,
  selectedSpells,
  selectedCantrips,
  onSelectedSpellsChange,
  onSelectedCantripsChange,
}: {
  characterClass?: string;
  level: string;
  abilityScores: number[];
  proficiencyBonus: number;
  selectedSpells: string[];
  selectedCantrips: string[];
  onSelectedSpellsChange: (value: string[]) => void;
  onSelectedCantripsChange: (value: string[]) => void;
}) {
  const levelNumber = Math.max(1, Number(level) || 1);
  const selectedClassOption = classOptions.find(
    (option) => option.id === characterClass,
  );
  const availableSpells = selectedClassOption?.firstLevelSpells ?? [];
  const availableCantrips = selectedClassOption?.cantrips ?? [];
  const isWizard = characterClass === "위저드";
  const isCleric = characterClass === "클레릭";
  const castingAbility = isWizard ? "지능" : isCleric ? "지혜" : "";
  const castingIndex =
    castingAbility === "지능" ? 3 : castingAbility === "지혜" ? 4 : -1;
  const castingScore = castingIndex >= 0 ? abilityScores[castingIndex] : 0;
  const castingModifier =
    castingScore > 0 ? Math.floor((castingScore - 10) / 2) : 0;
  const spellDc =
    castingAbility && castingScore > 0
      ? String(8 + proficiencyBonus + castingModifier)
      : "—";
  const spellAttack =
    castingAbility && castingScore > 0
      ? `${castingModifier + proficiencyBonus >= 0 ? "+" : ""}${castingModifier + proficiencyBonus}`
      : "—";
  const spellLearnLimit = isWizard
    ? 6
    : isCleric
      ? Math.max(1, levelNumber + castingModifier)
      : 0;
  const cantripLimit = castingAbility ? 3 : 0;
  const toggle = (
    current: string[],
    value: string,
    limit: number,
    update: (next: string[]) => void,
  ) => {
    if (current.includes(value))
      update(current.filter((item) => item !== value));
    else if (current.length < limit) update([...current, value]);
  };
  return (
    <section id="book" className="sheet-box spell-panel">
      <SheetTitle n="6" title="주문" />
      <div className="spell-summary">
        <label>
          주문 시전 능력
          <Input value={castingAbility || "—"} readOnly />
        </label>
        <label>
          주문 DC
          <Input value={spellDc} readOnly />
        </label>
        <label>
          주문 공격 보너스
          <Input value={spellAttack} readOnly />
        </label>
      </div>
      <div className="spell-resources">
        <div>
          <strong>소마법</strong>
          <b>
            {castingAbility
              ? `${selectedCantrips.length}/${cantripLimit}개`
              : "—"}
          </b>
          <small>생성 시 습득</small>
        </div>
        <div>
          <strong>{isWizard ? "주문서에 배운 주문" : "준비 가능 주문"}</strong>
          <b>
            {spellLearnLimit
              ? `${selectedSpells.length}/${spellLearnLimit}개`
              : "—"}
          </b>
          <small>{isWizard ? "1레벨 주문 습득" : "현재 레벨 준비 한도"}</small>
        </div>
        <div className="spell-slot-resource">
          <strong>1레벨 주문 슬롯</strong>
          <b>{isWizard || isCleric ? "생성 후 플레이에서 사용" : "—"}</b>
          <small>생성 단계에서는 사용하지 않습니다.</small>
        </div>
      </div>
      <div className="spell-cantrip-columns">
        <div>
          <div className="equipment-subtitle">
            <h4>선택한 소마법</h4>
            <small>
              {cantripLimit
                ? `${selectedCantrips.length}/${cantripLimit}개 선택`
                : "해당 없음"}
            </small>
          </div>
          <div className="spell-option-list">
            {selectedCantrips.map((spell) => (
              <button
                type="button"
                key={spell}
                className="spell-option selected"
                onClick={() =>
                  toggle(
                    selectedCantrips,
                    spell,
                    cantripLimit,
                    onSelectedCantripsChange,
                  )
                }
              >
                <SpellHoverName spell={spell} />
                <em>해제</em>
              </button>
            ))}
          </div>
        </div>
        <div>
          <div className="equipment-subtitle">
            <h4>선택 가능한 소마법</h4>
            <small>
              {characterClass
                ? `${characterClass} 소마법`
                : "직업을 먼저 선택하세요"}
            </small>
          </div>
          <div className="spell-option-list">
            {availableCantrips.map((spell) => (
              <button
                type="button"
                key={spell}
                disabled={
                  !selectedCantrips.includes(spell) &&
                  selectedCantrips.length >= cantripLimit
                }
                className={`spell-option ${selectedCantrips.includes(spell) ? "selected" : ""}`}
                onClick={() =>
                  toggle(
                    selectedCantrips,
                    spell,
                    cantripLimit,
                    onSelectedCantripsChange,
                  )
                }
              >
                <SpellHoverName spell={spell} />
                <em>{selectedCantrips.includes(spell) ? "선택됨" : "선택"}</em>
              </button>
            ))}
          </div>
        </div>
      </div>
      <div className="spell-columns">
        <div className="spell-owned">
          <div className="equipment-subtitle">
            <h4>선택한 주문</h4>
            <small>
              {spellLearnLimit
                ? `${selectedSpells.length}/${spellLearnLimit}개 선택`
                : "주문을 사용할 수 없는 직업"}
            </small>
          </div>
          <div className="spell-list">
            {selectedSpells.length ? (
              selectedSpells.map((spell, index) => (
                <div key={spell}>
                  <SpellHoverName spell={spell} />
                  <em>{index + 1}레벨</em>
                  <small>생성 시 습득</small>
                  <button
                    type="button"
                    onClick={() =>
                      toggle(
                        selectedSpells,
                        spell,
                        spellLearnLimit,
                        onSelectedSpellsChange,
                      )
                    }
                    aria-label={`${spell} 선택 해제`}
                  >
                    ×
                  </button>
                </div>
              ))
            ) : (
              <p className="spell-empty">아직 선택한 주문이 없습니다.</p>
            )}
          </div>
        </div>
        <div className="spell-available">
          <div className="equipment-subtitle">
            <h4>선택 가능한 주문</h4>
            <small>
              {characterClass
                ? `${characterClass} 1레벨 주문`
                : "직업을 먼저 선택하세요"}
            </small>
          </div>
          <div className="spell-option-list">
            {availableSpells.map((spell) => (
              <button
                type="button"
                key={spell}
                disabled={
                  !selectedSpells.includes(spell) &&
                  selectedSpells.length >= spellLearnLimit
                }
                className={`spell-option ${selectedSpells.includes(spell) ? "selected" : ""}`}
                onClick={() =>
                  toggle(
                    selectedSpells,
                    spell,
                    spellLearnLimit,
                    onSelectedSpellsChange,
                  )
                }
              >
                <SpellHoverName spell={spell} />
                <em>{selectedSpells.includes(spell) ? "선택됨" : "선택"}</em>
              </button>
            ))}
          </div>
          {!availableSpells.length && (
            <p className="spell-empty">
              직업을 선택하면 선택 가능한 주문 목록이 표시됩니다.
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
function SheetTitle({ n, title }: { n: string; title: string }) {
  return (
    <div className="sheet-title">
      <img src="/assets/characters/icons/star.png" alt="" />
      <h3>
        {n}. {title}
      </h3>
    </div>
  );
}
function Metric({ l, v }: { l: string; v: string }) {
  const icon = l.startsWith("♥")
    ? "heart"
    : l.startsWith("◈")
      ? "shield"
      : l.startsWith("♟")
        ? "boots"
        : l.startsWith("★")
          ? "star"
          : "crossed-swords";
  return (
    <div className="sheet-metric">
      <span>
        <img
          className="sheet-icon-image"
          src={`/assets/characters/icons/${icon}.png`}
          alt=""
        />
        {l.slice(1)}
      </span>
      <b>{v}</b>
    </div>
  );
}
function SkillHoverCard({ skill }: { skill: SkillDefinition }) {
  return (
    <span className="skill-hover-target" tabIndex={0}>
      {skill.name}
      <span className="skill-hover-card" role="tooltip">
        <strong>{skill.name}</strong>
        <span>{skill.description}</span>
        <small>
          연결 능력치: {skill.ability}
          <br />
          출처: D&amp;D 5e 기초 규칙 · 제7장
        </small>
      </span>
    </span>
  );
}
function SheetTable({
  n,
  title,
  items,
  abilityScores,
  proficiencyBonus,
  skillOptions,
  backgroundSkills,
  skillChoiceCount,
}: {
  n: string;
  title: string;
  items: SkillDefinition[];
  abilityScores: number[];
  proficiencyBonus: number;
  skillOptions?: string[];
  backgroundSkills?: string[];
  skillChoiceCount: number;
}) {
  const [selected, setSelected] = useState<string[]>(backgroundSkills ?? []);
  const abilityIndex: Record<string, number> = {
    근력: 0,
    민첩: 1,
    건강: 2,
    지능: 3,
    지혜: 4,
    매력: 5,
  };
  const background = new Set(backgroundSkills ?? []);
  const allowed = new Set([
    ...(skillOptions ?? []),
    ...(backgroundSkills ?? []),
  ]);
  useEffect(() => {
    setSelected(backgroundSkills ?? []);
  }, [backgroundSkills]);
  const toggleSkill = (skill: string, checked: boolean) => {
    setSelected((current) => {
      if (background.has(skill)) return current;
      const classSelectedCount = current.filter(
        (item) => !background.has(item),
      ).length;
      if (!checked) return current.filter((item) => item !== skill);
      if (
        !allowed.has(skill) ||
        current.includes(skill) ||
        classSelectedCount >= skillChoiceCount
      )
        return current;
      return [...current, skill];
    });
  };
  const formatBonus = (item: SkillDefinition) => {
    const score = abilityScores[abilityIndex[item.ability]];
    if (!Number.isFinite(score) || score <= 0) return "—";
    const value =
      Math.floor((score - 10) / 2) +
      (selected.includes(item.name.replace(/ \(.+\)$/, ""))
        ? proficiencyBonus
        : 0);
    return value >= 0 ? `+${value}` : String(value);
  };
  return (
    <section id="skills" className="sheet-box">
      <SheetTitle n={n} title={title} />
      <div className="skills-table-scroll">
        <div className="sheet-table">
          <div className="sheet-table-legend" aria-label="기술 표 범례">
            <span>기술</span>
            <span>능력치</span>
            <span>숙련</span>
            <span>보너스</span>
          </div>
          {items.map((item) => {
            const skillId = item.name.replace(/ \(.+\)$/, "");
            return (
              <div key={item.name}>
                <SkillHoverCard skill={item} />
                <em>{item.ability}</em>
                <input
                  type="checkbox"
                  checked={selected.includes(skillId)}
                  disabled={!allowed.has(skillId) || background.has(skillId)}
                  onChange={(event) =>
                    toggleSkill(skillId, event.target.checked)
                  }
                  aria-label={`${item.name} 숙련`}
                />
                <b>{formatBonus(item)}</b>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
function BlueprintExtras({
  nodes,
  values,
  onChange,
}: {
  nodes: CharacterInputNodeView[];
  values: Record<string, string>;
  onChange: (node: CharacterInputNodeView, value: string) => void;
}) {
  if (!nodes.length) return null;
  return (
    <div className="blueprint-inline-fields">
      {nodes.map((node) => {
        const value = values[node.id] ?? node.value ?? "";
        return (
          <label key={node.id}>
            {node.label}
            {node.inputMode === "SINGLE_SELECT" ? (
              <Select
                value={value}
                onChange={(event) => onChange(node, event.target.value)}
              >
                <option value="">선택하세요</option>
                {node.options.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </Select>
            ) : node.inputMode === "MULTI_SELECT" ? (
              <Select
                multiple
                value={value.split(",").filter(Boolean)}
                onChange={(event) =>
                  onChange(
                    node,
                    Array.from(
                      event.target.selectedOptions,
                      (option) => option.value,
                    ).join(","),
                  )
                }
              >
                {node.options.map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </Select>
            ) : (
              <Input
                value={value}
                onChange={(event) => onChange(node, event.target.value)}
              />
            )}
          </label>
        );
      })}
    </div>
  );
}

function isRemovedCharacterDetail(node: CharacterInputNodeView) {
  const identity = `${node.key} ${node.label}`.toLowerCase();
  return [
    "height",
    "weight",
    "eyes",
    "eye color",
    "skin",
    "skin color",
    "hair",
    "hair color",
    "party",
    "relationship",
    "age",
    "키",
    "몸무게",
    "눈",
    "피부",
    "머리카락",
    "나이",
    "일행",
    "관계",
  ].some((term) => identity.includes(term));
}

function flattenNodes(
  nodes: Awaited<
    ReturnType<NonNullable<SetupApi["getPlayPreparation"]>>
  >["characterCreationBlueprint"]["roots"],
): NonNullable<typeof nodes>[number][] {
  return (nodes ?? []).flatMap((node) => [
    node,
    ...flattenNodes(node.children),
  ]);
}
function rollAbilityScores(): number[] {
  return Array.from({ length: 6 }, () => {
    const rolls = Array.from(
      { length: 4 },
      () => Math.floor(Math.random() * 6) + 1,
    ).sort((a, b) => b - a);
    return rolls[0] + rolls[1] + rolls[2];
  });
}
