export type SubclassOption = { id: string; label: string; description: string; features: string[] }

const subclasses: Record<string, SubclassOption[]> = {
  클레릭: [
    { id: '생명 권역', label: '생명 권역', description: '치유와 생명 보호에 집중하는 신성 권역입니다.', features: ['중갑 숙련', '생명의 제자'] },
    { id: '지식 권역', label: '지식 권역', description: '학문, 언어와 통찰을 중시하는 신성 권역입니다.', features: ['지식의 축복'] },
    { id: '빛 권역', label: '빛 권역', description: '빛과 화염으로 어둠을 몰아내는 신성 권역입니다.', features: ['보너스 소마법', '수호의 섬광'] },
    { id: '자연 권역', label: '자연 권역', description: '자연의 힘과 생명체를 수호하는 신성 권역입니다.', features: ['자연의 수행자', '보너스 숙련'] },
    { id: '폭풍 권역', label: '폭풍 권역', description: '번개와 천둥, 바다의 힘을 다루는 신성 권역입니다.', features: ['보너스 숙련', '폭풍의 분노'] },
    { id: '속임수 권역', label: '속임수 권역', description: '은신과 기만, 환영을 활용하는 신성 권역입니다.', features: ['사기꾼의 축복'] },
    { id: '전쟁 권역', label: '전쟁 권역', description: '무기와 전투를 통해 신의 뜻을 수행하는 권역입니다.', features: ['보너스 숙련', '전쟁 사제'] },
  ],
  소서러: [
    { id: '용의 혈통', label: '용의 혈통', description: '용의 마력이 혈통에 흐르는 마법적 기원입니다.', features: ['용의 선조', '용의 회복력'] },
    { id: '야생 마법', label: '야생 마법', description: '예측하기 어려운 원초적 마법이 몸 안에서 폭발합니다.', features: ['야생 마법 쇄도', '혼돈의 물결'] },
  ],
  워락: [
    { id: '대마족', label: '대마족', description: '하계의 강력한 존재와 맺은 계약입니다.', features: ['어둠의 축복'] },
    { id: '대고대자', label: '대고대자', description: '현실 너머의 불가해한 존재와 맺은 계약입니다.', features: ['깨어난 정신'] },
    { id: '대요정', label: '대요정', description: '강력한 요정 군주와 맺은 계약입니다.', features: ['요정의 존재감'] },
  ],
}

export function subclassesFor(characterClass: string): SubclassOption[] { return subclasses[characterClass] ?? [] }
