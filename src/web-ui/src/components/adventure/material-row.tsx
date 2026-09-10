import type { ReactNode } from 'react'
import { AlertTriangle, BookOpen, Check, FileText, ScrollText } from 'lucide-react'
import { Checkbox } from '../ui/checkbox'
import { Progress } from '../ui/progress'
import { Select } from '../ui/select'
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

export function MaterialStatus({ document }: { document: KnowledgeDocumentView }) {
  const state = materialStatus(document)
  return <span className={`file-status file-status-${state.kind}`}><MaterialStatusIcon kind={state.kind} />{state.label}</span>
}

export function MaterialRow({
  document,
  selected,
  selectable = true,
  onSelectedChange,
  role,
  onRoleChange,
  actions,
}: {
  document: KnowledgeDocumentView
  selected?: boolean
  selectable?: boolean
  onSelectedChange?: (selected: boolean) => void
  role?: ScenarioBundleRole
  onRoleChange?: (role: ScenarioBundleRole) => void
  actions?: ReactNode
}) {
  const showSelection = selected !== undefined && onSelectedChange !== undefined
  const showRole = role !== undefined && onRoleChange !== undefined
  return <li className={`file-row material-row${selected ? ' material-row-selected' : ''}`}>
    {showSelection ? <span className="material-row-select"><Checkbox aria-label={`${document.originalFilename} 모험 자료 선택`} checked={selected} disabled={!selectable} onCheckedChange={checked => onSelectedChange?.(Boolean(checked))} /></span> : null}
    <span className="file-row-icon" aria-hidden="true"><DocumentIcon documentType={document.documentType} /></span>
    <span className="file-row-main">
      <strong>{document.originalFilename}</strong>
      <small>{document.documentType === 'RULEBOOK' ? '룰북' : role ? materialRoleLabels[role] : '시나리오 자료'} · {document.format}</small>
      {document.progress && materialStatus(document).kind === 'processing' ? <span className="material-row-progress"><Progress value={document.progress.percent} aria-label={`${document.originalFilename} 자료 준비 진행률`} /></span> : null}
      {document.failureReason ? <small className="material-row-problem">{document.failureReason}</small> : null}
    </span>
    {showRole ? <Select className="material-role-select" aria-label={`${document.originalFilename} 역할`} value={role} disabled={!selectable} onChange={event => onRoleChange?.(event.currentTarget.value as ScenarioBundleRole)}>
      {Object.entries(materialRoleLabels).filter(([value]) => value !== 'RULEBOOK').map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </Select> : null}
    <MaterialStatus document={document} />
    {actions ? <span className="file-row-actions material-row-actions">{actions}</span> : null}
  </li>
}

function DocumentIcon({ documentType }: { documentType: KnowledgeDocumentView['documentType'] }) {
  return documentType === 'RULEBOOK' ? <BookOpen size={18} /> : documentType === 'STORYBOOK' ? <ScrollText size={18} /> : <FileText size={18} />
}

function MaterialStatusIcon({ kind }: { kind: MaterialStatusKind }) {
  if (kind === 'ready') return <Check size={14} aria-hidden="true" />
  if (kind === 'review' || kind === 'failed') return <AlertTriangle size={14} aria-hidden="true" />
  return <span className="status-dot" aria-hidden="true" />
}
