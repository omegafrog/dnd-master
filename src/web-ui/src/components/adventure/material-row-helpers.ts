import type { KnowledgeDocumentView, ScenarioBundleRole } from '../../features/rulebooks/SetupApi'

export type MaterialStatusKind = 'ready' | 'processing' | 'review' | 'failed'

export const materialRoleLabels: Record<ScenarioBundleRole, string> = {
  RULEBOOK: '룰북',
  MAIN_SCENARIO: '메인 시나리오',
  MAP: '지도',
  HANDOUT: '핸드아웃',
  APPENDIX: '부록',
  REFERENCE: '참고 자료',
  CHARACTER_SHEET: '캐릭터 시트',
  UNDETERMINED: '미확정',
}

export function materialStatus(document: KnowledgeDocumentView): { kind: MaterialStatusKind; label: string } {
  if (['INDEXED', 'READY', 'PARTIAL_CONFIRMED'].includes(document.status)) return { kind: 'ready', label: '준비됨' }
  if (['FAILED', 'REJECTED'].includes(document.status)) return { kind: 'failed', label: '사용 불가' }
  if (['NEEDS_REVIEW', 'NEEDS_INPUT', 'PARTIAL_AWAITING_CONFIRMATION'].includes(document.status)) return { kind: 'review', label: '확인 필요' }
  return { kind: 'processing', label: '준비 중' }
}
