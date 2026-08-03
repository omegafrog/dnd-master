import type { CharacterInputNodeView } from '../rulebooks/SetupApi'

type Props = {
  nodes: CharacterInputNodeView[]
  values: Record<string, string>
  onChange: (id: string, value: string) => void
  onResolve?: (id: string) => void
  onAddChild?: (parentId: string) => void
  canResolve?: boolean
  abilityScoreMethod?: string
}

export function CharacterInputTree({ nodes, values, onChange, onResolve, onAddChild, canResolve = false, abilityScoreMethod: inheritedAbilityScoreMethod }: Props) {
  const flatten = (items: CharacterInputNodeView[]): CharacterInputNodeView[] => items.flatMap(item => [item, ...flatten(item.children)])
  const method = flatten(nodes).find(node => node.key === 'ability_score_method')
  const abilityScoreMethod = inheritedAbilityScoreMethod ?? (method ? values[method.id] ?? method.value ?? '' : '')
  const groups = new Map<string, CharacterInputNodeView[]>()
  nodes.forEach(node => {
    const group = inputGroup(node.key)
    groups.set(group, [...(groups.get(group) ?? []), node])
  })
  return <div aria-label="캐릭터 입력 태그 트리" className="character-sheet-sections">{[...groups.entries()].map(([group, groupNodes], index) => (
    <details key={group} className="character-sheet-section" open={index === 0}>
      <summary>{group}</summary>
      {groupNodes.map(node => <Node key={node.id} node={node} values={values} onChange={onChange} onResolve={onResolve} onAddChild={onAddChild} canResolve={canResolve} abilityScoreMethod={abilityScoreMethod} />)}
    </details>
  ))}</div>
}

function inputGroup(key: string) {
  if (key.startsWith('starting_ability_scores') || key.includes('saving') || key.includes('proficiency') || key.includes('armor') || key.includes('initiative') || key.includes('hit_') || key === 'speed' || key === 'skills') return '능력치·규칙';
  if (key.startsWith('equipment') || key.startsWith('magic') || key.includes('attacks')) return '장비·마법';
  if (key === 'background' || key === 'alignment' || key.startsWith('personality') || key === 'ideals' || key === 'bonds' || key === 'flaws') return '배경·성격';
  if (key === 'proficiency_bonus' || key === 'passive_wisdom' || key === 'features_traits') return '파생 수치';
  return '기본 정보';
}

function Node({ node, values, onChange, onResolve, onAddChild, canResolve, abilityScoreMethod }: Omit<Props, 'nodes'> & { node: CharacterInputNodeView, abilityScoreMethod: string }) {
  const value = values[node.id] ?? node.value ?? ''
  const mode = node.inputMode
  const isAbilityScore = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma', 'str', 'dex', 'con', 'int', 'wis', 'cha'].includes(node.key)
  const standardArray = ['15', '14', '13', '12', '10', '8']
  const storyProposal = node.diagnostics.some(item => item.includes('스토리북 제안'))
  const ruleExtension = node.diagnostics.some(item => item.includes('추가 룰북 속성 후보'))
  const originLabel = storyProposal ? '스토리북 제안' : ruleExtension ? '룰북 확장 후보' : ''
  const fixedValue = mode === 'FIXED_VALUE'
  const optionDetails = node.optionDetails ?? []
  const selectedValues = value.split(',').filter(Boolean)
  const selectedDetails = optionDetails.filter(detail => selectedValues.includes(detail.value))
  return <fieldset className="character-input-card" aria-label={node.label} data-node-id={node.id} data-origin={storyProposal ? 'STORYBOOK_PROPOSAL' : ruleExtension ? 'RULEBOOK_EXTENSION' : 'BASE'}>
    <legend>{node.label}</legend>
    {originLabel ? <small aria-label={`${node.label} 출처`}>{originLabel}</small> : null}
    {storyProposal ? <p>이 항목은 시나리오 전용 제안입니다. 값을 선택하거나 입력해 저장하면 적용되고, 저장하지 않으면 베이스 본을 유지합니다.</p> : null}
    {ruleExtension ? <p>추가 룰북에서 발견한 속성입니다. 검토 후 저장할 때만 캐릭터 생성 본에 포함됩니다.</p> : null}
    {fixedValue ? <output aria-label={node.label}>{value || '—'}</output>
      : mode === 'SINGLE_SELECT' ? <select aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)}><option value="">선택하세요</option>{node.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
      : mode === 'MULTI_SELECT' ? <select multiple aria-label={node.label} value={value.split(',').filter(Boolean)} onChange={event => onChange(node.id, Array.from(event.currentTarget.selectedOptions, option => option.value).join(','))}>{node.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
        : isAbilityScore && abilityScoreMethod === 'STANDARD_ARRAY' ? <select aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)}><option value="">배정</option>{standardArray.map(score => <option key={score} value={score}>{score}</option>)}</select>
          : isAbilityScore ? <input type="number" min={abilityScoreMethod === 'POINT_BUY' ? 8 : 1} max={abilityScoreMethod === 'POINT_BUY' ? 15 : 30} aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)} />
            : <input type="text" aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)} />}
    {selectedDetails.length > 0 ? <div className="character-input-selected-detail" aria-label="선택한 항목 설명">{selectedDetails.map(detail => <p key={detail.value}><strong>{detail.label || detail.value}</strong>{detail.description ? ` · ${detail.description}` : ''}</p>)}</div> : null}
    {optionDetails.length > 0 ? <details className="character-input-help"><summary>선택지 설명 보기 ({optionDetails.length})</summary><div className="character-option-grid">{optionDetails.map(detail => <article key={detail.value}><strong>{detail.label || detail.value}</strong><p>{detail.description || '설명 없음'}</p></article>)}</div></details> : null}
    {node.key === 'ability_score_method' ? <small>표준 배열: 15, 14, 13, 12, 10, 8 · 포인트바이: 27점 · 주사위: 4d6 중 최저값 제외</small> : null}
    {node.suggestions.length > 0 ? <small>추천 또는 제안 값: {node.suggestions.join(', ')}</small> : null}
    {node.sourceQuote ? <small>원문 근거: {node.sourceQuote}</small> : null}
    {node.sourceEvidence.map(item => <small key={`${item.knowledgeDocumentId}-${item.locator}`}>근거: {item.knowledgeDocumentId} v{item.extractionVersion} · {item.locator}</small>)}
    {node.diagnostics.map(item => <small key={item}>{item}</small>)}
    {node.status === 'PARTIALLY_EXTRACTED' && node.allowUserAddChild && onAddChild ? <button type="button" onClick={() => onAddChild(node.id)}>필드 추가</button> : null}
    {canResolve && !fixedValue && onResolve && !(node.status === 'PARTIALLY_EXTRACTED' && !node.value && node.sourceEvidence.length === 0 && node.children.length > 0) ? <button type="button" onClick={() => onResolve(node.id)} disabled={!value}>{storyProposal || ruleExtension ? '제안 적용' : '검토값 저장'}</button> : null}
    <CharacterInputTree nodes={node.children} values={values} onChange={onChange} onResolve={onResolve} onAddChild={onAddChild} canResolve={canResolve} abilityScoreMethod={abilityScoreMethod} />
  </fieldset>
}
