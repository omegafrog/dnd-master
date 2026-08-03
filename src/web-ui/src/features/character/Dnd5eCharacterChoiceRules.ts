export type ChoiceRequirement = {
  id: string
  label: string
  count: number
  options: string[]
}

export type BackgroundRule = {
  feature: { name: string; description: string }
  choiceRequirements: ChoiceRequirement[]
}

export const languageOptions = [
  '드워프어', '엘프어', '거인어', '노움어', '고블린어', '하플링어', '오크어', '심연어',
  '천상어', '용언', '심층어', '지옥어', '태고어', '실반어', '지하공용어',
]

export const artisanToolOptions = [
  '연금술 도구', '양조 도구', '서예 도구', '목수 도구', '지도 제작 도구', '구두 수선 도구',
  '요리 도구', '유리 세공 도구', '보석 세공 도구', '가죽 세공 도구', '석공 도구', '화가 도구',
  '도예 도구', '대장장이 도구', '땜장이 도구', '직조 도구', '목공 도구',
]

export const instrumentOptions = ['백파이프', '북', '덜시머', '플루트', '류트', '리라', '호른', '팬 플루트', '숌', '비올']
export const gamingSetOptions = ['주사위 세트', '드래곤 체스 세트', '카드 세트', '삼룡패 세트']

const choice = (id: string, label: string, count: number, options: string[]): ChoiceRequirement => ({ id, label, count, options })

const backgroundRules: Record<string, BackgroundRule> = {
  수행사제: { feature: { name: '신앙의 안식처', description: '같은 신앙의 사원과 신도에게서 기본적인 도움과 보호를 받을 수 있습니다.' }, choiceRequirements: [choice('background-languages', '배경 추가 언어', 2, languageOptions)] },
  사기꾼: { feature: { name: '거짓 신분', description: '문서와 연줄을 갖춘 별도의 신분을 유지합니다.' }, choiceRequirements: [choice('background-tools', '배경 도구 숙련', 2, ['변장 도구', '위조 도구'])] },
  범죄자: { feature: { name: '범죄 조직 연락책', description: '범죄 조직의 연락망을 통해 메시지와 정보를 전달할 수 있습니다.' }, choiceRequirements: [choice('background-gaming-set', '게임 세트 숙련', 1, gamingSetOptions)] },
  연예인: { feature: { name: '인기 공연', description: '공연할 장소에서 숙식과 명성을 얻을 수 있습니다.' }, choiceRequirements: [choice('background-instrument', '악기 숙련', 1, instrumentOptions)] },
  '민중 영웅': { feature: { name: '민중의 환대', description: '평범한 사람들이 숨겨 주거나 쉬게 해 주고 위험에서 보호하려 합니다.' }, choiceRequirements: [choice('background-artisan-tool', '장인 도구 숙련', 1, artisanToolOptions)] },
  '길드 장인': { feature: { name: '길드 회원권', description: '길드의 지원과 숙소, 사회적 지위를 이용할 수 있습니다.' }, choiceRequirements: [choice('background-artisan-tool', '장인 도구 숙련', 1, artisanToolOptions), choice('background-language', '배경 추가 언어', 1, languageOptions)] },
  은둔자: { feature: { name: '발견', description: '고독한 연구 중 세상에 영향을 줄 수 있는 중요한 발견을 했습니다.' }, choiceRequirements: [choice('background-language', '배경 추가 언어', 1, languageOptions)] },
  귀족: { feature: { name: '특권 계층', description: '상류층 사회에서 환영받고 일반인에게 존중받습니다.' }, choiceRequirements: [choice('background-language', '배경 추가 언어', 1, languageOptions), choice('background-gaming-set', '게임 세트 숙련', 1, gamingSetOptions)] },
  이방인: { feature: { name: '방랑자', description: '지형과 정착지를 잘 기억하며 식량과 물을 구할 수 있습니다.' }, choiceRequirements: [choice('background-instrument', '악기 숙련', 1, instrumentOptions), choice('background-language', '배경 추가 언어', 1, languageOptions)] },
  현자: { feature: { name: '연구자', description: '모르는 지식이 있을 때 그 정보를 찾을 장소나 사람을 압니다.' }, choiceRequirements: [choice('background-languages', '배경 추가 언어', 2, languageOptions)] },
  선원: { feature: { name: '선박 통행', description: '필요할 때 자신과 동료를 태울 배편을 구할 수 있습니다.' }, choiceRequirements: [choice('background-tools', '배경 도구 숙련', 2, ['항해 도구', '차량(수상)'])] },
  군인: { feature: { name: '군 계급', description: '같은 군대 소속 인원에게 계급에 따른 영향력을 행사할 수 있습니다.' }, choiceRequirements: [choice('background-gaming-set', '게임 세트 숙련', 1, gamingSetOptions), choice('background-tools', '차량 숙련', 1, ['차량(육상)'])] },
  부랑아: { feature: { name: '도시의 비밀', description: '도시의 골목과 지름길을 이용해 빠르게 이동할 수 있습니다.' }, choiceRequirements: [choice('background-tools', '배경 도구 숙련', 2, ['변장 도구', '도둑 도구'])] },
}

export function raceChoiceRequirements(race: string, subrace: string): ChoiceRequirement[] {
  const requirements: ChoiceRequirement[] = []
  if (race === '인간') requirements.push(choice('race-language', '종족 추가 언어', 1, languageOptions))
  if (subrace === '하이 엘프') requirements.push(choice('subrace-language', '하위 종족 추가 언어', 1, languageOptions))
  return requirements
}

export function classChoiceRequirements(characterClass: string): ChoiceRequirement[] {
  switch (characterClass) {
    case '바드': return [choice('class-instruments', '클래스 악기 숙련', 3, instrumentOptions)]
    case '몽크': return [choice('class-tool', '클래스 도구 또는 악기 숙련', 1, [...artisanToolOptions, ...instrumentOptions])]
    default: return []
  }
}

export function backgroundRule(background: string): BackgroundRule | undefined {
  return backgroundRules[background]
}

export function choicesComplete(requirements: ChoiceRequirement[], selections: Record<string, string[]>): boolean {
  return requirements.every(requirement => {
    const selected = selections[requirement.id] ?? []
    return selected.length === requirement.count && selected.every(value => requirement.options.includes(value))
  })
}

export function selectedChoiceValues(requirements: ChoiceRequirement[], selections: Record<string, string[]>): string[] {
  return requirements.flatMap(requirement => selections[requirement.id] ?? [])
}
