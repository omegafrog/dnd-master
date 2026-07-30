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
  return <div aria-label="캐릭터 입력 태그 트리">{nodes.map(node => <Node key={node.id} node={node} values={values} onChange={onChange} onResolve={onResolve} onAddChild={onAddChild} canResolve={canResolve} abilityScoreMethod={abilityScoreMethod} />)}</div>
}

function Node({ node, values, onChange, onResolve, onAddChild, canResolve, abilityScoreMethod }: Omit<Props, 'nodes'> & { node: CharacterInputNodeView, abilityScoreMethod: string }) {
  const value = values[node.id] ?? node.value ?? ''
  const mode = node.inputMode
  const isAbilityScore = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma', 'str', 'dex', 'con', 'int', 'wis', 'cha'].includes(node.key)
  const standardArray = ['15', '14', '13', '12', '10', '8']
  return <fieldset aria-label={node.label} data-node-id={node.id}>
    <legend>{node.label}</legend>
    {mode === 'SINGLE_SELECT' ? <select aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)}><option value="">선택하세요</option>{node.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
      : mode === 'MULTI_SELECT' ? <select multiple aria-label={node.label} value={value.split(',').filter(Boolean)} onChange={event => onChange(node.id, Array.from(event.currentTarget.selectedOptions, option => option.value).join(','))}>{node.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
        : isAbilityScore && abilityScoreMethod === 'STANDARD_ARRAY' ? <select aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)}><option value="">배정</option>{standardArray.map(score => <option key={score} value={score}>{score}</option>)}</select>
          : isAbilityScore ? <input type="number" min={abilityScoreMethod === 'POINT_BUY' ? 8 : 1} max={abilityScoreMethod === 'POINT_BUY' ? 15 : 30} aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)} />
            : <input type="text" aria-label={node.label} value={value} onChange={event => onChange(node.id, event.currentTarget.value)} />}
    {node.key === 'ability_score_method' ? <small>표준 배열: 15, 14, 13, 12, 10, 8 · 포인트바이: 27점 · 주사위: 4d6 중 최저값 제외</small> : null}
    {node.suggestions.length > 0 ? <small>추천: {node.suggestions.join(', ')}</small> : null}
    {node.sourceQuote ? <small>원문 근거: {node.sourceQuote}</small> : null}
    {node.sourceEvidence.map(item => <small key={`${item.knowledgeDocumentId}-${item.locator}`}>근거: {item.knowledgeDocumentId} v{item.extractionVersion} · {item.locator}</small>)}
    {node.diagnostics.map(item => <small key={item}>{item}</small>)}
    {node.status === 'PARTIALLY_EXTRACTED' && node.allowUserAddChild && onAddChild ? <button type="button" onClick={() => onAddChild(node.id)}>필드 추가</button> : null}
    {canResolve && onResolve && !(node.status === 'PARTIALLY_EXTRACTED' && !node.value && node.sourceEvidence.length === 0 && node.children.length > 0) ? <button type="button" onClick={() => onResolve(node.id)} disabled={!value}>검토값 저장</button> : null}
    <CharacterInputTree nodes={node.children} values={values} onChange={onChange} onResolve={onResolve} onAddChild={onAddChild} canResolve={canResolve} abilityScoreMethod={abilityScoreMethod} />
  </fieldset>
}
