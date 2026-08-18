import type { Ability } from './Dnd5eRules'

export const STANDARD_ARRAY = [15, 14, 13, 12, 10, 8] as const

export type RaceOption = {
  id: string; label: string; description: string; languages: string[]; traits: string[]
  subraces: Array<{ id: string; label: string; description: string; traits: string[] }>
}
export const raceOptions: RaceOption[] = [
  { id: '드워프', label: '드워프', description: '강인한 체질과 독 저항을 지닌 종족입니다.', languages: ['공용어', '드워프어'], traits: ['암시야', '드워프의 회복력', '석공 지식'], subraces: [
    { id: '언덕 드워프', label: '언덕 드워프', description: '지혜와 생명력이 뛰어납니다.', traits: ['드워프의 강인함'] },
    { id: '산 드워프', label: '산 드워프', description: '힘과 갑옷 훈련에 강점이 있습니다.', traits: ['드워프 갑옷 훈련'] },
  ] },
  { id: '엘프', label: '엘프', description: '민첩하고 예리한 감각과 요정 혈통을 지닌 종족입니다.', languages: ['공용어', '엘프어'], traits: ['암시야', '예리한 감각', '요정 혈통', '명상'], subraces: [
    { id: '하이 엘프', label: '하이 엘프', description: '지능과 비전 마법에 강점이 있습니다.', traits: ['엘프 무기 훈련', '소마법', '추가 언어'] },
    { id: '우드 엘프', label: '우드 엘프', description: '지혜와 빠른 이동에 강점이 있습니다.', traits: ['엘프 무기 훈련', '빠른 발', '야생의 가면'] },
  ] },
  { id: '하플링', label: '하플링', description: '작고 민첩하며 용감하고 운이 좋은 종족입니다.', languages: ['공용어', '하플링어'], traits: ['행운', '용감함', '민첩한 몸놀림'], subraces: [
    { id: '라이트풋 하플링', label: '라이트풋 하플링', description: '사교성과 은신에 강점이 있습니다.', traits: ['자연스러운 은신'] },
    { id: '스타우트 하플링', label: '스타우트 하플링', description: '체력과 독 저항에 강점이 있습니다.', traits: ['스타우트 회복력'] },
  ] },
  { id: '인간', label: '인간', description: '모든 능력치에 고르게 보너스를 받는 다재다능한 종족입니다.', languages: ['공용어', '선택 언어 1개'], traits: [], subraces: [] },
  { id: '드래곤본', label: '드래곤본', description: '강력한 드래곤 혈통과 숨결 무기를 지닌 자랑스러운 종족입니다.', languages: ['공용어', '드래곤어'], traits: ['혈통', '숨결 무기', '피해 저항'], subraces: [] },
  { id: '노움', label: '노움', description: '작은 몸집과 뛰어난 지능, 마법에 대한 타고난 친화력을 지닌 종족입니다.', languages: ['공용어', '노움어'], traits: ['암시야', '노움의 교활함'], subraces: [
    { id: '숲 노움', label: '숲 노움', description: '자연과 작은 동물, 환영 마법에 친숙합니다.', traits: ['자연의 환영', '작은 동물과의 대화'] },
    { id: '바위 노움', label: '바위 노움', description: '장치와 공학, 튼튼한 신체에 강점이 있습니다.', traits: ['기계공 지식', '시계태엽 장인'] },
  ] },
  { id: '하프 엘프', label: '하프 엘프', description: '인간의 호기심과 엘프의 감각을 함께 지닌 적응력 높은 종족입니다.', languages: ['공용어', '엘프어', '선택 언어 1개'], traits: ['암시야', '요정 혈통', '다재다능함'], subraces: [] },
  { id: '하프 오크', label: '하프 오크', description: '인간과 오크의 혈통을 이어받아 강인함과 끈질긴 생명력을 지닌 종족입니다.', languages: ['공용어', '오크어'], traits: ['암시야', '위협적', '끈질긴 생명력', '야만적 공격'], subraces: [] },
  { id: '티플링', label: '티플링', description: '악마적 혈통과 불꽃의 힘을 지닌 종족으로, 그 혈통을 스스로 선택할 수 있습니다.', languages: ['공용어', '지옥어'], traits: ['암시야', '지옥 저항', '지옥의 유산'], subraces: [] },
].filter(race => ['드워프', '엘프', '하플링', '인간'].includes(race.id)) as RaceOption[]

export type ClassOption = {
  id: string; label: string; description: string; hitDie: string; savingThrows: Ability[]
  skillChoices: string[]; skillChoiceCount: number; features: string[]; subclassLevel: number
  canCastSpells: boolean; cantrips: string[]; firstLevelSpells: string[]
}
const allSkills = ['곡예', '손속임', '은신', '비전학', '수사', '역사학', '자연학', '종교학', '동물 조련', '통찰', '의학', '감지', '생존', '기만', '위협', '공연', '설득', '운동']
export const classOptions: ClassOption[] = [
  { id: '바바리안', label: '바바리안', description: '분노와 강인함으로 전장을 버티는 전사입니다.', hitDie: 'd12', savingThrows: ['strength', 'constitution'], skillChoiceCount: 2, skillChoices: ['동물 조련', '운동', '위협', '자연', '지각', '생존'], features: ['분노', '비무장 방어'], subclassLevel: 3, canCastSpells: false, cantrips: [], firstLevelSpells: [] },
  { id: '바드', label: '바드', description: '마법과 재능, 영감으로 일행을 돕는 만능가입니다.', hitDie: 'd8', savingThrows: ['dexterity', 'charisma'], skillChoiceCount: 3, skillChoices: allSkills, features: ['주문시전', '바드의 고양감'], subclassLevel: 3, canCastSpells: true, cantrips: ['마법사의 손', '빛', '악의에 찬 조롱', '수선'], firstLevelSpells: ['치유의 단어', '요정 불꽃', '수면', '천둥파도', '매혹', '불협화음'] },
  { id: '클레릭', label: '클레릭', description: '신성한 힘으로 아군을 돕고 적을 물리치는 주문 시전자입니다.', hitDie: 'd8', savingThrows: ['wisdom', 'charisma'], skillChoiceCount: 2, skillChoices: ['역사학', '통찰', '의학', '설득', '종교학'], features: ['주문시전', '신성 권역'], subclassLevel: 1, canCastSpells: true, cantrips: ['단순마술', '빈사 안정', '빛', '신성한 불길', '안내', '저항'], firstLevelSpells: ['축복', '치유의 단어', '상처 치료', '신앙의 방패', '명령', '유도 화살', '마법 탐지', '상처 가해', '성역화'] },
  { id: '드루이드', label: '드루이드', description: '자연의 마법과 변신 능력을 사용하는 수호자입니다.', hitDie: 'd8', savingThrows: ['intelligence', 'wisdom'], skillChoiceCount: 2, skillChoices: ['비전학', '동물 조련', '통찰', '의학', '자연', '지각', '종교', '생존'], features: ['드루이드어', '주문시전'], subclassLevel: 2, canCastSpells: true, cantrips: ['가이던스', '가시 채찍', '생산의 불꽃', '셸릴리'], firstLevelSpells: ['얽힘', '치유의 단어', '상처 치료', '요정 불꽃', '동물과의 대화', '천둥파도'] },
  { id: '파이터', label: '파이터', description: '무기와 방어구를 폭넓게 다루는 전투 전문가입니다.', hitDie: 'd10', savingThrows: ['strength', 'constitution'], skillChoiceCount: 2, skillChoices: ['곡예', '동물 조련', '운동', '역사', '통찰', '위협', '지각', '생존'], features: ['전투 방식', '재기의 바람'], subclassLevel: 3, canCastSpells: false, cantrips: [], firstLevelSpells: [] },
  { id: '몽크', label: '몽크', description: '기와 수련으로 몸 자체를 무기로 삼는 전사입니다.', hitDie: 'd8', savingThrows: ['strength', 'dexterity'], skillChoiceCount: 2, skillChoices: ['곡예', '운동', '역사', '통찰', '종교', '은신'], features: ['비무장 방어', '무술'], subclassLevel: 3, canCastSpells: false, cantrips: [], firstLevelSpells: [] },
  { id: '팔라딘', label: '팔라딘', description: '맹세와 신성한 힘으로 싸우는 중갑 전사입니다.', hitDie: 'd10', savingThrows: ['wisdom', 'charisma'], skillChoiceCount: 2, skillChoices: ['운동', '통찰', '위협', '의학', '설득', '종교'], features: ['신성한 감각', '치유의 손길'], subclassLevel: 3, canCastSpells: false, cantrips: [], firstLevelSpells: [] },
  { id: '레인저', label: '레인저', description: '야생 생존과 사냥, 원거리 전투에 능한 전사입니다.', hitDie: 'd10', savingThrows: ['strength', 'dexterity'], skillChoiceCount: 3, skillChoices: ['동물 조련', '운동', '통찰', '수사', '자연', '지각', '은신', '생존'], features: ['주적', '자연 탐험가'], subclassLevel: 3, canCastSpells: false, cantrips: [], firstLevelSpells: [] },
  { id: '로그', label: '로그', description: '기술, 잠입, 기습 공격에 능한 전문가입니다.', hitDie: 'd8', savingThrows: ['dexterity', 'intelligence'], skillChoiceCount: 4, skillChoices: ['곡예', '운동', '기만', '통찰', '위협', '수사', '감지', '공연', '설득', '손속임', '은신'], features: ['숙달', '암습 공격', '도둑의 속어'], subclassLevel: 3, canCastSpells: false, cantrips: [], firstLevelSpells: [] },
  { id: '소서러', label: '소서러', description: '타고난 마력을 직관적으로 사용하는 주문 시전자입니다.', hitDie: 'd6', savingThrows: ['constitution', 'charisma'], skillChoiceCount: 2, skillChoices: ['비전학', '기만', '통찰', '위협', '설득', '종교'], features: ['주문시전', '마법의 기원'], subclassLevel: 1, canCastSpells: true, cantrips: ['마법사의 손', '빛', '불화살', '서리 광선', '충격의 손아귀'], firstLevelSpells: ['마법 화살', '방패', '수면', '천둥파도', '마법 갑주', '혼돈의 화살'] },
  { id: '워락', label: '워락', description: '초월적 후원자와의 계약으로 마법을 얻은 시전자입니다.', hitDie: 'd8', savingThrows: ['wisdom', 'charisma'], skillChoiceCount: 2, skillChoices: ['비전학', '기만', '역사', '위협', '수사', '자연', '종교'], features: ['다른 세상의 후원자', '계약 마법'], subclassLevel: 1, canCastSpells: true, cantrips: ['섬뜩한 방출', '마법사의 손', '사소한 환영', '독 분사'], firstLevelSpells: ['아가티스의 갑옷', '헥스', '지옥의 책망', '매혹', '마녀 화살', '보이지 않는 하인'] },
  { id: '위저드', label: '위저드', description: '주문책을 연구해 폭넓은 비전 마법을 사용하는 학자입니다.', hitDie: 'd6', savingThrows: ['intelligence', 'wisdom'], skillChoiceCount: 2, skillChoices: ['비전학', '역사학', '통찰', '수사', '의학', '종교학'], features: ['주문시전', '비전 회복'], subclassLevel: 2, canCastSpells: true, cantrips: ['독 분사', '마법사의 손', '빛', '산성 거품', '서리 광선', '요술', '전격의 손아귀', '춤추는 빛', '하급 환영', '화염 화살'], firstLevelSpells: ['마법 갑주', '마법 탐지', '마법 화살', '방패', '수면', '식별', '언어 변환', '인간형 매혹', '자기 위장', '조용한 영상', '천둥파도', '타오르는 손길'] },
].filter(option => ['로그', '위저드', '클레릭', '파이터'].includes(option.id)) as ClassOption[]

export type BackgroundOption = { id: string; label: string; description: string; skills: string[]; equipment: string[]; personality: string[]; ideals: string[]; bonds: string[]; flaws: string[] }
const bg = (id: string, description: string, skills: string[], equipment: string[]): BackgroundOption => ({ id, label: id, description, skills, equipment, personality: ['상황보다 사람의 태도를 먼저 살핀다.'], ideals: ['신념: 내가 중요하게 여기는 가치를 지킨다.'], bonds: ['소중한 사람이나 공동체를 지키려 한다.'], flaws: ['자신의 약점을 인정하는 데 서툴다.'] })
export const backgroundOptions: BackgroundOption[] = [
  bg('복사', '신전이나 종교 공동체에서 봉사하고 신앙을 배운 인물입니다.', ['통찰', '종교학'], ['성표', '기도서', '향 5개', '평상복', '15gp']),
  bg('사기꾼', '거짓 신분과 속임수로 살아온 인물입니다.', ['기만', '손속임'], ['고급 의복', '변장 도구', '사기 도구', '15gp']),
  bg('범죄자', '범죄 조직이나 암시장에서 살아남는 법을 익힌 인물입니다.', ['기만', '은신'], ['쇠지렛대', '어두운 후드 평상복', '15gp']),
  bg('연예인', '공연과 이야기로 사람들의 관심을 끌어온 인물입니다.', ['곡예', '공연'], ['악기', '추종자의 선물', '의상', '15gp']),
  bg('시골 영웅', '평범한 사람들 사이에서 용기와 행동으로 이름을 알린 인물입니다.', ['동물 조련', '생존'], ['장인 도구', '삽', '쇠솥', '평상복', '10gp']),
  bg('길드 장인', '길드와 장인 사회에서 기술과 거래를 익힌 인물입니다.', ['통찰', '설득'], ['장인 도구', '추천서', '여행자 의복', '15gp']),
  bg('은둔자', '외딴곳에서 고독과 성찰의 시간을 보낸 인물입니다.', ['의학', '종교'], ['두루마리 통', '겨울 담요', '약초학 도구', '평상복', '5gp']),
  bg('귀족', '특권과 예법, 가문 정치에 익숙한 인물입니다.', ['역사', '설득'], ['고급 의복', '인장 반지', '혈통 문서', '25gp']),
  bg('이방인', '문명 밖의 황야와 먼 지역에서 살아온 인물입니다.', ['운동', '생존'], ['지팡이', '사냥 덫', '여행자 의복', '10gp']),
  bg('학자', '학문과 연구를 통해 지식을 축적한 인물입니다.', ['비전학', '역사학'], ['잉크병', '깃펜', '작은 칼', '편지', '평상복', '10gp']),
  bg('선원', '배와 항구에서 노동하고 항해한 인물입니다.', ['운동', '지각'], ['밧줄', '행운의 부적', '평상복', '10gp']),
  bg('군인', '군대나 용병대에서 훈련과 전투를 경험한 인물입니다.', ['운동', '위협'], ['계급장', '전리품', '주사위 세트', '평상복', '10gp']),
  bg('부랑아', '도시의 거리와 뒷골목에서 스스로 살아남은 인물입니다.', ['손속임', '은신'], ['작은 칼', '도시 지도', '애완 쥐', '평상복', '10gp']),
]

export const personalityHelp = {
  personality: '인격 특성은 평소의 말투와 행동 습관입니다. 선택한 배경의 제안 중 하나를 고릅니다.',
  ideal: '이상은 캐릭터가 중요하게 여기는 신념이나 가치입니다. 선택한 배경의 제안 중 하나를 고릅니다.',
  bond: '유대는 지키거나 되찾고 싶은 사람, 장소, 물건 또는 약속입니다. 선택한 배경의 제안 중 하나를 고릅니다.',
  flaw: '단점은 갈등이나 실수를 일으킬 수 있는 약점입니다. 선택한 배경의 제안 중 하나를 고릅니다.',
}
