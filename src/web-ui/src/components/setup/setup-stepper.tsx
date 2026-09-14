import { Check } from 'lucide-react'

export type SetupStep = 'details' | 'materials' | 'complete'

const steps: Array<{ value: SetupStep; number: number; label: string }> = [
  { value: 'details', number: 1, label: '기본 정보' },
  { value: 'materials', number: 2, label: '자료 추가' },
  { value: 'complete', number: 3, label: '완료' },
]

export function SetupStepper({ step }: { step: SetupStep }) {
  const currentIndex = steps.findIndex(item => item.value === step)
  return <ol className="setup-stepper" aria-label="새 모험 만들기 단계">
    {steps.map((item, index) => {
      const complete = index < currentIndex || step === 'complete'
      const active = index === currentIndex && step !== 'complete'
      return <li key={item.value} className={`${complete ? 'setup-step-complete' : ''}${active ? ' setup-step-active' : ''}`} aria-current={active ? 'step' : undefined}>
        <span className="setup-step-marker" aria-hidden="true">{complete ? <Check size={14} /> : item.number}</span>
        <span className="setup-step-label">{item.label}</span>
      </li>
    })}
  </ol>
}
