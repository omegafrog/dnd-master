import { useEffect, useState } from 'react'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type {
  CharacterInputNodeView,
  BlueprintPublicationView,
  CharacterCreationBlueprintView,
  PlayPreparationView,
  RulebookBaseSchemaView,
  SetupApi,
  StorybookProposalView,
} from '../rulebooks/SetupApi'

type PackageSetupApi = {
  getPlayPreparation: NonNullable<SetupApi['getPlayPreparation']>
  generateBlueprintDraft?: SetupApi['generateBlueprintDraft']
  useStorybookProposal?: SetupApi['useStorybookProposal']
  excludeStorybookProposal?: SetupApi['excludeStorybookProposal']
  publishBlueprint?: SetupApi['publishBlueprint']
}

type CatalogRulebook = {
  catalogRevisionId: string
  displayName: string
  edition: string
  rulebookId: string | null
  status: string
  extractionVersion: number
}

export function PackageBlueprintReviewPage({
  packageId,
  setupApi,
  sessionApi,
  onSessionCreated,
}: {
  packageId: string
  setupApi: PackageSetupApi
  sessionApi: Pick<AdventureSessionApi, 'create'>
  onSessionCreated: (sessionId: string) => void
}) {
  const [preparation, setPreparation] = useState<PlayPreparationView | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [catalogRulebooks, setCatalogRulebooks] = useState<CatalogRulebook[]>([])
  const [selectedCatalogRulebookId, setSelectedCatalogRulebookId] = useState('')
  const [generatingDraft, setGeneratingDraft] = useState(false)
  const [creatingSession, setCreatingSession] = useState(false)
  const [pendingProposalId, setPendingProposalId] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [confirmationResult, setConfirmationResult] = useState<BlueprintPublicationView | null>(null)
  const [retrySession, setRetrySession] = useState(false)

  useEffect(() => {
    let active = true
    setPreparation(null)
    setLoadError(null)
    void setupApi.getPlayPreparation(packageId)
      .then(next => { if (active) setPreparation(next) })
      .catch(error => {
        if (!active) return
        setLoadError(error instanceof Error ? error.message : '캐릭터 생성 설정을 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [packageId, reloadToken, setupApi])

  useEffect(() => {
    if (!setupApi.generateBlueprintDraft) return
    let active = true
    void fetch('/api/v1/rulebook-catalog')
      .then(response => response.ok ? response.json() : [])
      .then((items: CatalogRulebook[]) => {
        if (!active) return
        const ready = items.filter(item => item.status === 'READY' && item.rulebookId && item.extractionVersion > 0)
        setCatalogRulebooks(ready)
        if (ready.length === 1) setSelectedCatalogRulebookId(ready[0].rulebookId!)
      })
      .catch(() => { if (active) setCatalogRulebooks([]) })
    return () => { active = false }
  }, [setupApi.generateBlueprintDraft])

  async function generateDraft() {
    const selected = catalogRulebooks.find(item => item.rulebookId === selectedCatalogRulebookId)
    if (!selected || !setupApi.generateBlueprintDraft) {
      setMessage('기본 내용에 사용할 공개 룰북을 선택하세요.')
      return
    }
    setGeneratingDraft(true)
    try {
      await setupApi.generateBlueprintDraft(packageId, selected.rulebookId!, selected.extractionVersion)
      setReloadToken(token => token + 1)
      setMessage('룰북 기본 내용과 스토리북 제안을 다시 분석했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '룰북과 스토리북을 분석하지 못했습니다.')
    } finally {
      setGeneratingDraft(false)
    }
  }

  function retry() {
    setMessage('')
    setReloadToken(token => token + 1)
  }

  async function decideProposal(proposalId: string, decision: 'APPLIED' | 'EXCLUDED') {
    const command = decision === 'APPLIED' ? setupApi.useStorybookProposal : setupApi.excludeStorybookProposal
    if (!command || blueprint.revision == null) return
    setPendingProposalId(proposalId)
    try {
      await command(packageId, proposalId, blueprint.revision)
      setMessage(decision === 'APPLIED' ? '제안을 사용하기로 저장했습니다.' : '제안을 제외하기로 저장했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '제안 결정을 저장하지 못했습니다.')
    } finally {
      setPendingProposalId(null)
      setReloadToken(token => token + 1)
    }
  }

  if (!preparation) {
    return (
      <section className="character-settings-review-page" aria-labelledby="package-blueprint-review-heading">
        <h1 id="package-blueprint-review-heading">캐릭터 생성 설정 검토</h1>
        {loadError ? (
          <div className="character-review-state character-review-state-error" role="alert">
            <h2>검토 정보를 불러오지 못했습니다</h2>
            <p>{loadError}</p>
            <button type="button" onClick={retry}>다시 불러오기</button>
          </div>
        ) : (
          <p role="status" aria-live="polite">캐릭터 생성 설정을 불러오는 중…</p>
        )}
      </section>
    )
  }

  const blueprint = preparation.characterCreationBlueprint
  const summary = blueprint.appliedSettingsSummary
  const unresolved = summary?.unresolvedProposalCount ?? (blueprint.storybookProposals ?? [])
    .filter(proposal => proposal.decisionState === 'UNDECIDED' || proposal.decisionState === 'NEEDS_EVIDENCE').length
  const baseSchema = blueprint.baseSchema
  const baseSchemaValid = blueprint.baseSchemaValid ?? Boolean(
    baseSchema && baseSchema.fields.length > 0
      && baseSchema.fields.every(field => field.diagnostics.length === 0
        && field.inputStatus !== 'MANUAL_INPUT_REQUIRED'
        && field.inputStatus !== 'CONFLICT_REVIEW'),
  )
  const canConfirm = blueprint.status !== 'PUBLISHED' && unresolved === 0 && baseSchemaValid

  async function createSession() {
    if (!blueprint.available || blueprint.status !== 'PUBLISHED' || blueprint.revision == null) return
    setCreatingSession(true)
    setRetrySession(false)
    try {
      const session = await sessionApi.create({
        scenarioPackageId: packageId,
        blueprintId: packageId,
        blueprintRevision: blueprint.revision,
      })
      onSessionCreated(session.sessionId)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '세션 초안을 생성하지 못했습니다.')
      setRetrySession(true)
      setReloadToken(token => token + 1)
    } finally {
      setCreatingSession(false)
    }
  }

  async function confirmSettings() {
    if (!setupApi.publishBlueprint || !canConfirm) return
    setConfirming(true)
    try {
      const result = await setupApi.publishBlueprint(packageId)
      setConfirmationResult(result)
      setMessage('캐릭터 생성에 사용할 설정을 확정했습니다.')
      setReloadToken(token => token + 1)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '설정을 확정하지 못했습니다.')
      setReloadToken(token => token + 1)
    } finally {
      setConfirming(false)
    }
  }

  return (
    <section className="character-settings-review-page" aria-labelledby="package-blueprint-review-heading">
      <header className="character-review-header">
        <p className="eyebrow">CHARACTER SETTINGS REVIEW</p>
        <h1 id="package-blueprint-review-heading">캐릭터 생성 설정 검토</h1>
        <p>이 화면은 실제 캐릭터 값을 입력하는 곳이 아닙니다. 룰북의 기본 내용을 읽고, 스토리북에서 발견된 제안을 확인하는 작업 공간입니다.</p>
        <a href="#/setup">새 모험 준비 시작</a>
      </header>

      {message && <p role="status" aria-live="polite">{message}</p>}
      {preparation.blockers.length > 0 && (
        <ul className="character-review-diagnostics" aria-label="검토를 막는 사유">
          {preparation.blockers.map(blocker => <li key={blocker}>{blocker}</li>)}
        </ul>
      )}
      {setupApi.generateBlueprintDraft && catalogRulebooks.length > 0 && (
        <section className="character-review-preparation" aria-label="룰북 기본 내용 준비">
          <h2>룰북 기본 내용 준비</h2>
          <p>검토할 룰북 판본을 선택하면 기본 내용과 스토리북 제안을 다시 분석합니다.</p>
          <div className="character-review-preparation-controls">
            <label>
              룰북 판본
              <select aria-label="룰북 판본" value={selectedCatalogRulebookId} onChange={event => setSelectedCatalogRulebookId(event.target.value)}>
                <option value="">선택하세요</option>
                {catalogRulebooks.map(rulebook => <option key={rulebook.catalogRevisionId} value={rulebook.rulebookId!}>{rulebook.displayName} · {rulebook.edition}</option>)}
              </select>
            </label>
            <button type="button" onClick={() => void generateDraft()} disabled={generatingDraft || !selectedCatalogRulebookId}>
              {generatingDraft ? '분석 중…' : '다시 분석하기'}
            </button>
          </div>
        </section>
      )}

      {!blueprint.baseSchema ? (
        <div className="character-review-state character-review-state-error" role="alert">
          <h2>룰북 기본 내용을 표시할 수 없습니다</h2>
          <p>룰북 기본 스키마를 불러오지 못했습니다. 다시 불러온 뒤에도 문제가 계속되면 자료 준비 상태를 확인하세요.</p>
          <button type="button" onClick={retry}>다시 불러오기</button>
        </div>
      ) : (
        <>
          <div className="character-review-layout">
            <BaseSchemaPanel schema={blueprint.baseSchema} roots={blueprint.roots ?? blueprint.characterSheetTree ?? []} />
            <StorybookProposalPanel
              blueprint={blueprint}
              canUse={blueprint.status !== 'PUBLISHED' && Boolean(setupApi.useStorybookProposal)}
              canExclude={blueprint.status !== 'PUBLISHED' && Boolean(setupApi.excludeStorybookProposal)}
              pendingProposalId={pendingProposalId}
              onDecision={decideProposal}
            />
          </div>
          <AppliedSettingsSummary blueprint={blueprint} />
          {confirmationResult && (
            <section className="character-review-confirmation-result" aria-labelledby="confirmation-result-heading" role="status">
              <h2 id="confirmation-result-heading">설정이 확정되었습니다</h2>
              <p>게시된 설정 revision: {confirmationResult.publishedRevision}</p>
              <ul>
                <li>룰북 기본 내용: 포함</li>
                <li>사용 예정 제안: {confirmationResult.appliedSettingsSummary.appliedProposalIds.length}개</li>
                <li>제외 예정 제안: {confirmationResult.appliedSettingsSummary.excludedProposalIds.length}개</li>
              </ul>
            </section>
          )}
          {setupApi.publishBlueprint && blueprint.status !== 'PUBLISHED' && (
            <section className="character-review-confirmation" aria-label="캐릭터 생성 설정 확정">
              <h2>캐릭터 생성에 사용할 설정 확정</h2>
              {!baseSchemaValid && <p role="alert">룰북 기본 내용을 먼저 확인해야 합니다.</p>}
              {unresolved > 0 && <p role="status">결정이 필요한 제안이 {unresolved}개 있습니다.</p>}
              <button type="button" onClick={() => void confirmSettings()} disabled={confirming || !canConfirm}>
                {confirming ? '확정 중…' : '캐릭터 생성에 사용할 설정 확정'}
              </button>
            </section>
          )}
          {blueprint.status === 'PUBLISHED' && (
            <section className="character-review-next-action" aria-label="캐릭터 생성">
              <h2>캐릭터 생성으로 이동</h2>
              <p>게시된 설정을 사용해 캐릭터 생성을 시작할 수 있습니다.</p>
              <button type="button" onClick={() => void createSession()} disabled={creatingSession}>
                {creatingSession ? '캐릭터 생성 준비 중…' : '캐릭터 생성 시작'}
              </button>
              {retrySession && <button type="button" onClick={() => void createSession()} disabled={creatingSession}>다시 시도</button>}
            </section>
          )}
        </>
      )}
    </section>
  )
}

function BaseSchemaPanel({ schema, roots }: { schema: RulebookBaseSchemaView; roots: CharacterInputNodeView[] }) {
  const fieldsByKey = new Map(schema.fields.map(field => [field.key, field]))
  const fieldGroups = schemaFieldGroups(schema.fields)
  const groupedFieldKeys = new Set(fieldGroups.flatMap(group => group.fields.map(field => field.key)))
  const tree = roots
    .map(node => baseSchemaNode(node, fieldsByKey, '', groupedFieldKeys))
    .filter((node): node is SchemaTreeNode => node !== null)
  const ungroupedFields = schema.fields.filter(field => !groupedFieldKeys.has(field.key))

  return (
    <section className="character-review-panel character-review-base-schema" aria-labelledby="base-schema-heading">
      <div className="character-review-panel-heading">
        <div>
          <p className="eyebrow">RULEBOOK FOUNDATION</p>
          <h2 id="base-schema-heading">룰북 기본 스키마</h2>
        </div>
        <span className="character-review-read-only">읽기 전용</span>
      </div>
      <p>캐릭터 생성에서 사용할 기본 구조입니다. 이 화면에서는 내용을 수정하지 않습니다.</p>
      <dl className="character-review-meta">
        <dt>룰북 판본</dt>
        <dd>{schema.edition}</dd>
        <dt>기본 항목</dt>
        <dd>{schema.fields.length}개</dd>
      </dl>
      {schema.fields.length === 0 ? (
        <p className="character-review-muted">표시할 기본 항목이 없습니다.</p>
      ) : tree.length > 0 ? (
        <div className="character-review-schema-tree" aria-label="룰북 기본 계층">
          {tree.map(node => <SchemaTreeItem key={node.node.id} item={node} />)}
        </div>
      ) : (
        <ul className="character-review-field-list" aria-label="룰북 기본 항목">
          {ungroupedFields.map(field => (
            <li key={field.key}>
              <div className="character-review-field-heading">
                <h3>{field.label}</h3>
                <span>{fieldStatusLabel(field)}</span>
              </div>
              <p>{fieldDescription(field)}</p>
              {field.options.length > 0 && <p><strong>선택지:</strong> {field.options.join(', ')}</p>}
            </li>
          ))}
        </ul>
      )}
      {fieldGroups.map(group => <SchemaFieldGroup key={group.key} group={group} />)}
    </section>
  )
}

type SchemaTreeNode = {
  node: CharacterInputNodeView
  field: RulebookBaseSchemaView['fields'][number] | undefined
  children: SchemaTreeNode[]
}

function baseSchemaNode(
  node: CharacterInputNodeView,
  fieldsByKey: Map<string, RulebookBaseSchemaView['fields'][number]>,
  parentPath: string,
  groupedFieldKeys: Set<string>,
): SchemaTreeNode | null {
  const path = parentPath ? `${parentPath}.${node.key}` : node.key
  const children = node.children
    .map(child => baseSchemaNode(child, fieldsByKey, path, groupedFieldKeys))
    .filter((child): child is SchemaTreeNode => child !== null)
  const candidate = fieldsByKey.get(path)
  const field = candidate && !groupedFieldKeys.has(candidate.key) ? candidate : undefined
  if (!field && children.length === 0) return null
  return { node, field, children }
}

type SchemaFieldGroup = {
  key: 'roleplay' | 'derived'
  label: string
  fields: SchemaField[]
}

function schemaFieldGroups(fields: SchemaField[]): SchemaFieldGroup[] {
  const roleplayKeys = new Set(['personality_traits', 'ideals', 'bonds', 'flaws'])
  const derivedKeys = new Set([
    'proficiency_bonus', 'saving_throws', 'skills', 'passive_wisdom', 'armor_class', 'initiative', 'speed',
    'hit_point_maximum', 'hit_dice', 'attacks_spellcasting', 'other_proficiencies_languages', 'features_traits',
  ])
  const roleplay = fields.filter(field => roleplayKeys.has(field.key))
  const derived = fields.filter(field => derivedKeys.has(field.key))
  return [
    roleplay.length > 0 ? { key: 'roleplay' as const, label: '배경·성격', fields: roleplay } : null,
    derived.length > 0 ? { key: 'derived' as const, label: '자동 계산·부여', fields: derived } : null,
  ].filter((group): group is SchemaFieldGroup => group !== null)
}

function SchemaFieldGroup({ group }: { group: SchemaFieldGroup }) {
  return (
    <details className="character-review-schema-group character-review-flat-schema-group">
      <summary>
        <span>{group.label}</span>
        <small>{group.fields.length}개</small>
      </summary>
      <ul className="character-review-field-list">
        {group.fields.map(field => (
          <li key={field.key}>
            <div className="character-review-field-heading">
              <h3>{field.label}</h3>
              <span>{fieldStatusLabel(field)}</span>
            </div>
            <p>{fieldDescription(field)}</p>
            {field.options.length > 0 && <p><strong>선택지:</strong> {field.options.join(', ')}</p>}
          </li>
        ))}
      </ul>
    </details>
  )
}

function SchemaTreeItem({ item }: { item: SchemaTreeNode }) {
  const content = item.field ? <SchemaFieldDetails field={item.field} /> : null
  if (item.children.length === 0) {
    return <div className="character-review-schema-leaf"><SchemaNodeHeading node={item.node} field={item.field} />{content}</div>
  }

  return (
    <details className="character-review-schema-group">
      <summary><SchemaNodeHeading node={item.node} field={item.field} /></summary>
      <div className="character-review-schema-group-content">
        {content}
        <div className="character-review-schema-children">
          {item.children.map(child => <SchemaTreeItem key={child.node.id} item={child} />)}
        </div>
      </div>
    </details>
  )
}

function SchemaNodeHeading({ node, field }: { node: CharacterInputNodeView; field: RulebookBaseSchemaView['fields'][number] | undefined }) {
  return (
    <div className="character-review-field-heading">
      <h3>{node.label}</h3>
      <span>{field ? fieldStatusLabel(field) : '하위 항목'}</span>
    </div>
  )
}

function SchemaFieldDetails({ field }: { field: RulebookBaseSchemaView['fields'][number] }) {
  return (
    <div className="character-review-schema-details">
      <p>{fieldDescription(field)}</p>
      {field.options.length > 0 && <p><strong>선택지:</strong> {field.options.join(', ')}</p>}
    </div>
  )
}

function StorybookProposalPanel({
  blueprint,
  canUse,
  canExclude,
  pendingProposalId,
  onDecision,
}: {
  blueprint: CharacterCreationBlueprintView
  canUse: boolean
  canExclude: boolean
  pendingProposalId: string | null
  onDecision: (proposalId: string, decision: 'APPLIED' | 'EXCLUDED') => void
}) {
  const proposals = blueprint.storybookProposals ?? []
  const extractionState = blueprint.storybookExtractionState
  const evidenceNeeded = proposals.filter(proposal => proposal.readinessState === 'INSUFFICIENT_EVIDENCE').length

  return (
    <section className="character-review-panel character-review-proposals" aria-labelledby="storybook-proposals-heading">
      <div className="character-review-panel-heading">
        <div>
          <p className="eyebrow">STORYBOOK REVIEW</p>
          <h2 id="storybook-proposals-heading">스토리북 제안</h2>
        </div>
        <span className="character-review-count">{proposals.length}개</span>
      </div>
      <p>스토리북에서 발견된 추가 내용입니다. 각 카드에서 내용과 출처를 확인할 수 있습니다.</p>

      {extractionState === 'EXTRACTION_PARTIAL_AWAITING_CONFIRMATION' && (
        <div className="character-review-state character-review-state-warning" role="status">
          <h3>스토리북 분석이 일부 완료되어 확인이 필요합니다</h3>
          <p>일부 원문만 분석되었습니다. 자료를 확인한 뒤 다시 분석해야 제안을 확정할 수 있습니다.</p>
        </div>
      )}

      {extractionState === 'EXTRACTION_FAILED' ? (
        <div className="character-review-state character-review-state-error" role="alert">
          <h3>스토리북 분석에 실패했습니다</h3>
          <p>스토리북 제안을 표시할 수 없습니다. 자료 상태를 확인한 뒤 다시 분석해 주세요.</p>
          {blueprint.diagnostics.length > 0 && <ul aria-label="스토리북 분석 실패 원인">{blueprint.diagnostics.map(item => <li key={item}>{item}</li>)}</ul>}
        </div>
      ) : extractionState === 'EXTRACTION_PARTIAL_AWAITING_CONFIRMATION' && proposals.length === 0 ? null : extractionState === 'INSUFFICIENT_EVIDENCE' && proposals.length === 0 ? (
        <div className="character-review-state character-review-state-error" role="status">
          <h3>스토리북 근거가 충분하지 않습니다</h3>
          <p>근거를 확인할 수 있는 제안이 없습니다. 원문이 준비된 뒤 다시 분석해 주세요.</p>
        </div>
      ) : proposals.length === 0 ? (
        <div className="character-review-state character-review-state-empty" role="status">
          <h3>스토리북에서 추가할 내용이 없습니다</h3>
          <p>분석이 완료되었습니다.</p>
        </div>
      ) : (
        <>
          {evidenceNeeded > 0 && (
            <p className="character-review-inline-warning" role="status">{evidenceNeeded}개 제안은 근거 확인이 필요합니다.</p>
          )}
          <div className="character-review-source-groups">
            {groupBySource(proposals).map(group => (
              <section className="character-review-source-group" key={group.label} aria-labelledby={sourceGroupId(group.label)}>
                <h3 id={sourceGroupId(group.label)}>{group.label}</h3>
                <div className="character-review-proposal-list">
                  {group.proposals.map(item => (
                    <StorybookProposalCard
                      key={item.proposalId}
                      proposal={item}
                      canUse={canUse}
                      canExclude={canExclude}
                      pending={pendingProposalId === item.proposalId}
                      onDecision={onDecision}
                    />
                  ))}
                </div>
              </section>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

function StorybookProposalCard({
  proposal,
  canUse,
  canExclude,
  pending,
  onDecision,
}: {
  proposal: StorybookProposalView
  canUse: boolean
  canExclude: boolean
  pending: boolean
  onDecision: (proposalId: string, decision: 'APPLIED' | 'EXCLUDED') => void
}) {
  const evidenceUnavailable = proposal.readinessState === 'INSUFFICIENT_EVIDENCE'
  const needsEvidence = evidenceUnavailable && proposal.decisionState !== 'EXCLUDED'
  const status = needsEvidence ? '근거 확인 필요' : decisionLabel(proposal.decisionState)
  return (
    <article className="character-review-proposal-card" aria-labelledby={`proposal-${proposal.proposalId}`}>
      <div className="character-review-proposal-heading">
        <h4 id={`proposal-${proposal.proposalId}`}>{proposal.label}</h4>
        <p className="character-review-proposal-status"><span>현재 상태:</span> <strong>{status}</strong></p>
      </div>
      <dl className="character-review-proposal-details">
        <dt>내용</dt>
        <dd>{proposal.description || '설명이 제공되지 않았습니다.'}</dd>
        <dt>출처</dt>
        <dd>{proposal.sourceDocument?.originalFilename ?? '출처 문서 확인 필요'}</dd>
      </dl>
      <details>
        <summary>원문 근거 보기</summary>
        {evidenceUnavailable ? (
          <p className="character-review-evidence-missing">사용할 수 있는 원문 근거가 아직 없습니다.</p>
        ) : (
          <div className="character-review-evidence">
            {proposal.sourceQuote && <blockquote>{proposal.sourceQuote}</blockquote>}
            {proposal.evidence.length > 0 ? (
              <ul aria-label={`${proposal.label} 근거 목록`}>
                {proposal.evidence.map((evidence, index) => <li key={`${proposal.proposalId}-evidence-${index}-${evidence.locator}`}><span>{evidence.locator}</span><p>{evidence.excerpt}</p></li>)}
              </ul>
            ) : <p className="character-review-muted">추가 원문 근거가 없습니다.</p>}
          </div>
        )}
      </details>
      {(canUse || canExclude) && (
        <div className="character-review-proposal-actions">
          {canUse && <button type="button" onClick={() => onDecision(proposal.proposalId, 'APPLIED')} disabled={pending || evidenceUnavailable}>
            {pending ? '저장 중…' : '사용하기'}
          </button>}
          {canExclude && <button type="button" onClick={() => onDecision(proposal.proposalId, 'EXCLUDED')} disabled={pending}>
              제외하기
            </button>}
        </div>
      )}
    </article>
  )
}

function AppliedSettingsSummary({ blueprint }: { blueprint: CharacterCreationBlueprintView }) {
  const proposals = blueprint.storybookProposals ?? []
  const applied = blueprint.appliedSettingsSummary?.appliedProposalIds.length
    ?? proposals.filter(proposal => proposal.decisionState === 'APPLIED').length
  const excluded = blueprint.appliedSettingsSummary?.excludedProposalIds.length
    ?? proposals.filter(proposal => proposal.decisionState === 'EXCLUDED').length
  const unresolved = blueprint.appliedSettingsSummary?.unresolvedProposalCount
    ?? proposals.filter(proposal => proposal.decisionState === 'UNDECIDED' || proposal.decisionState === 'NEEDS_EVIDENCE').length
  return (
    <section className="character-review-summary" aria-labelledby="applied-settings-heading">
      <div>
        <p className="eyebrow">NEXT DECISION</p>
        <h2 id="applied-settings-heading">적용 예정 설정 요약</h2>
      </div>
      <ul>
        <li><span>룰북 기본 내용</span><strong>포함</strong></li>
        <li><span>사용 예정 제안</span><strong>{applied}개</strong></li>
        <li><span>제외 예정 제안</span><strong>{excluded}개</strong></li>
        <li><span>결정이 필요한 제안</span><strong>{unresolved}개</strong></li>
      </ul>
      <p>모든 제안 결정을 마치면 룰북 기본 내용과 사용 예정 제안을 확정할 수 있습니다.</p>
    </section>
  )
}

function groupBySource(proposals: StorybookProposalView[]) {
  const groups = new Map<string, StorybookProposalView[]>()
  proposals.forEach(proposal => {
    const label = proposal.sourceDocument?.originalFilename ?? '출처 문서 확인 필요'
    groups.set(label, [...(groups.get(label) ?? []), proposal])
  })
  return Array.from(groups, ([label, items]) => ({ label, proposals: items }))
}

function inputModeLabel(inputMode: string | undefined) {
  if (inputMode === 'SINGLE_SELECT') return '하나의 선택지를 고르는 항목'
  if (inputMode === 'MULTI_SELECT') return '여러 선택지를 고르는 항목'
  if (inputMode === 'FIXED_VALUE') return '선택 결과로 자동 계산되거나 부여되는 항목'
  return '값을 입력하는 항목'
}

type SchemaField = RulebookBaseSchemaView['fields'][number]

function fieldDescription(field: SchemaField) {
  if (['personality_traits', 'ideals', 'bonds', 'flaws'].includes(field.key)) {
    return '선택한 배경의 룰북 추천값을 참고하거나 직접 작성할 수 있는 항목'
  }
  return inputModeLabel(field.inputMode)
}

function fieldStatusLabel(field: SchemaField) {
  if (field.inputMode === 'FIXED_VALUE') return '자동 계산·부여'
  if (['personality_traits', 'ideals', 'bonds', 'flaws'].includes(field.key)) return '배경별 추천값'
  return field.required ? '필수 입력' : '선택 입력'
}

function decisionLabel(decisionState: StorybookProposalView['decisionState']) {
  if (decisionState === 'APPLIED') return '사용 예정'
  if (decisionState === 'EXCLUDED') return '제외 예정'
  if (decisionState === 'NEEDS_EVIDENCE') return '근거 확인 필요'
  return '검토 전'
}

function slugify(value: string) {
  return value.replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '') || 'item'
}

function sourceGroupId(value: string) {
  const codePoints = Array.from(value).map(character => character.codePointAt(0)!.toString(16)).join('-')
  return `source-${slugify(value)}-${codePoints || 'item'}`
}
