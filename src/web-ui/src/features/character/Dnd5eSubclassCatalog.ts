export type SubclassOption = { id: string; label: string; description: string; features: string[] }

const subclasses: Record<string, SubclassOption[]> = {
  클레릭: [
    { id: '생명 권역', label: '생명 권역', description: '생명과 건강을 수호하고 병들고 상처받은 이를 치료하는 권역입니다.', features: ['추가 숙련', '생명의 사도'] },
  ],
  파이터: [
    { id: '챔피언', label: '챔피언', description: '육체 능력과 훈련을 결합해 파괴적인 타격을 만드는 무예 아키타입입니다.', features: ['향상된 치명타', '뛰어난 운동능력'] },
  ],
  로그: [
    { id: '시프', label: '시프', description: '도둑질과 손속임, 은신을 전문으로 하는 로그 아키타입입니다.', features: ['빠른 손놀림', '오르내리기'] },
  ],
  위저드: [
    { id: '방출학파', label: '방출학파', description: '냉기·불꽃·천둥·번개 같은 강력한 원소 효과를 다루는 비전 전통입니다.', features: ['방출계 전공'] },
    ...['방호학파', '변환학파', '사령학파', '예지학파', '조형학파', '환영학파', '환혹학파'].map(id => ({
      id, label: id, description: 'dnd5th.pdf에는 학파 이름만 수록되어 있으며 상세 규칙은 제공되지 않습니다.', features: [],
    })),
  ],
}

export function subclassesFor(characterClass: string): SubclassOption[] { return subclasses[characterClass] ?? [] }
