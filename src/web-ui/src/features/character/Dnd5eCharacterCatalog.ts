import type { Ability } from './Dnd5eRules'

export const STANDARD_ARRAY = [15, 14, 13, 12, 10, 8] as const

export type RaceOption = { id: string; label: string; description: string; subraces: Array<{ id: string; label: string; description: string }> }
export const raceOptions: RaceOption[] = [
  { id: '드워프', label: '드워프', description: '강인한 체질과 독에 대한 저항력을 지닌 종족입니다.', subraces: [
    { id: '언덕 드워프', label: '언덕 드워프', description: '지혜와 생명력이 뛰어납니다.' },
    { id: '산 드워프', label: '산 드워프', description: '힘과 갑옷 훈련에 강점이 있습니다.' },
  ] },
  { id: '엘프', label: '엘프', description: '민첩하고 예리한 감각과 요정 혈통을 지닌 종족입니다.', subraces: [
    { id: '하이 엘프', label: '하이 엘프', description: '지능과 비전 마법에 강점이 있습니다.' },
    { id: '우드 엘프', label: '우드 엘프', description: '지혜와 빠른 이동에 강점이 있습니다.' },
  ] },
  { id: '하플링', label: '하플링', description: '작고 민첩하며 용감하고 운이 좋은 종족입니다.', subraces: [
    { id: '라이트풋 하플링', label: '라이트풋 하플링', description: '사교성과 은신에 강점이 있습니다.' },
    { id: '스타우트 하플링', label: '스타우트 하플링', description: '체력과 독 저항에 강점이 있습니다.' },
  ] },
  { id: '인간', label: '인간', description: '모든 능력치에 고르게 보너스를 받는 다재다능한 종족입니다.', subraces: [] },
]

export type ClassOption = {
  id: string; label: string; description: string; hitDie: string; savingThrows: Ability[]
  skillChoices: string[]; skillChoiceCount: number; features: string[]; equipmentChoices: string[]
  canCastSpells: boolean; spellChoices: string[]; subclassLevel: number
}
export const classOptions: ClassOption[] = [
  { id: '파이터', label: '파이터', description: '무기와 방어구를 폭넓게 다루는 전투 전문가입니다.', hitDie: 'd10', savingThrows: ['strength', 'constitution'], skillChoiceCount: 2, skillChoices: ['곡예', '동물 조련', '운동', '역사', '통찰', '위협', '지각', '생존'], features: ['전투 방식', '재기의 바람'], equipmentChoices: ['체인 메일', '가죽 갑옷 + 장궁 + 화살 20개', '군용 무기 + 방패', '군용 무기 2개'], canCastSpells: false, spellChoices: [], subclassLevel: 3 },
  { id: '로그', label: '로그', description: '기술, 잠입, 기습 공격에 능한 전문가입니다.', hitDie: 'd8', savingThrows: ['dexterity', 'intelligence'], skillChoiceCount: 4, skillChoices: ['곡예', '운동', '기만', '통찰', '위협', '수사', '지각', '공연', '설득', '손재주', '은신'], features: ['숙달', '암습 공격', '도둑의 속어'], equipmentChoices: ['레이피어', '숏소드', '단궁 + 화살 20개', '숏소드 1개 추가', '도둑 꾸러미', '탐험가 꾸러미'], canCastSpells: false, spellChoices: [], subclassLevel: 3 },
  { id: '클레릭', label: '클레릭', description: '신성한 힘으로 아군을 돕고 적을 물리치는 주문 시전자입니다.', hitDie: 'd8', savingThrows: ['wisdom', 'charisma'], skillChoiceCount: 2, skillChoices: ['역사', '통찰', '의학', '설득', '종교'], features: ['주문시전', '신성 권역'], equipmentChoices: ['메이스', '워해머(숙련 시)', '스케일 메일', '가죽 갑옷', '체인 메일(숙련 시)', '라이트 크로스보우 + 볼트 20개', '단순 무기', '사제 꾸러미', '탐험가 꾸러미', '방패', '성표'], canCastSpells: true, spellChoices: ['가이던스', '빛', '신성한 불꽃', '죽어가는 자 살리기', '축복', '치유의 단어', '상처 치료', '신앙의 방패', '명령'], subclassLevel: 1 },
  { id: '위저드', label: '위저드', description: '주문책을 연구해 폭넓은 비전 마법을 사용하는 학자입니다.', hitDie: 'd6', savingThrows: ['intelligence', 'wisdom'], skillChoiceCount: 2, skillChoices: ['비전학', '역사', '통찰', '수사', '의학', '종교'], features: ['주문시전', '비전 회복'], equipmentChoices: ['쿼터스태프', '대거', '구성요소 주머니', '비전 매개체', '학자 꾸러미', '탐험가 꾸러미', '주문책'], canCastSpells: true, spellChoices: ['마법사의 손', '빛', '불화살', '서리 광선', '마법 갑주', '마법 화살', '방패', '수면', '천둥파도', '탐지 마법'], subclassLevel: 2 },
]

export type BackgroundOption = { id: string; label: string; description: string; skills: string[]; equipment: string[]; personality: string[]; ideals: string[]; bonds: string[]; flaws: string[] }
export const backgroundOptions: BackgroundOption[] = [
  { id: '수행사제', label: '수행사제', description: '신전이나 종교 공동체에서 봉사하고 신앙을 배운 인물입니다.', skills: ['통찰', '종교'], equipment: ['성표', '기도서 또는 기도 바퀴', '향 5개', '법의', '평상복', '15gp'], personality: ['고대 영웅의 행동을 본받으려 한다.', '모든 사건에서 징조와 의미를 찾는다.'], ideals: ['전통: 오래된 의식과 전승을 보존해야 한다.', '자선: 도움이 필요한 이를 돕는다.'], bonds: ['내가 섬긴 신전을 지키기 위해 무엇이든 한다.'], flaws: ['다른 신앙을 가진 이를 너무 쉽게 판단한다.'] },
  { id: '범죄자', label: '범죄자', description: '범죄 조직이나 암시장에서 살아남는 법을 익힌 인물입니다.', skills: ['기만', '은신'], equipment: ['쇠지렛대', '어두운 후드 달린 평상복', '15gp'], personality: ['문제가 생기면 항상 탈출구부터 찾는다.'], ideals: ['자유: 누구도 나를 지배할 수 없다.'], bonds: ['오래된 동료에게 갚아야 할 빚이 있다.'], flaws: ['돈이 걸리면 위험을 과소평가한다.'] },
  { id: '민중 영웅', label: '민중 영웅', description: '평범한 사람들 사이에서 용기와 행동으로 이름을 알린 인물입니다.', skills: ['동물 조련', '생존'], equipment: ['장인 도구 한 종류', '삽', '쇠솥', '평상복', '10gp'], personality: ['사람은 누구나 존중받을 가치가 있다고 믿는다.'], ideals: ['공정: 누구도 법 위에 있어서는 안 된다.'], bonds: ['나를 키워 준 공동체를 지킨다.'], flaws: ['폭군을 보면 생각보다 행동이 먼저 나간다.'] },
  { id: '현자', label: '현자', description: '학문과 연구를 통해 지식을 축적한 인물입니다.', skills: ['비전학', '역사'], equipment: ['검은 잉크병', '깃펜', '작은 칼', '죽은 동료의 편지', '평상복', '10gp'], personality: ['모르는 것이 나오면 설명하지 않고는 못 배긴다.'], ideals: ['지식: 이해로 가는 길이 곧 힘이다.'], bonds: ['평생 찾던 질문의 답을 발견해야 한다.'], flaws: ['확실한 증거가 있어도 자신의 이론을 쉽게 버리지 않는다.'] },
]

export const personalityHelp = {
  personality: '인격 특성은 캐릭터가 평소 어떻게 말하고 행동하는지를 나타냅니다. 배경 예시에서 고르거나 직접 적을 수 있으며 필수는 아닙니다.',
  ideal: '이상은 캐릭터가 중요하게 여기는 신념이나 가치입니다. 선택 사항입니다.',
  bond: '유대는 캐릭터가 지키거나 되찾고 싶어 하는 사람, 장소, 물건 또는 약속입니다. 선택 사항입니다.',
  flaw: '단점은 갈등이나 실수를 일으킬 수 있는 약점입니다. 역할극을 돕기 위한 선택 사항입니다.',
}
