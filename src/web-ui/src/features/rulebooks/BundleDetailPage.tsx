import { useEffect, useMemo, useState } from 'react'
import { Button } from '../../components/ui/button'
import { Card, CardContent } from '../../components/ui/card'
import { Checkbox } from '../../components/ui/checkbox'
import { Select } from '../../components/ui/select'
import type { KnowledgeDocumentView, ScenarioBundleRole, ScenarioBundleView, ScenarioPackageView, SetupApi } from './SetupApi'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import { PreparationModal } from './PreparationModal'

type CatalogRulebook = { catalogRevisionId: string; displayName: string; edition: string; rulebookId: string | null; revisionNumber: number; status: string }

const roles: Array<[ScenarioBundleRole, string]> = [
  ['RULEBOOK', '룰북'],
  ['MAIN_SCENARIO', '메인 시나리오'],
  ['MAP', '지도'],
  ['HANDOUT', '핸드아웃'],
  ['APPENDIX', '부록'],
  ['REFERENCE', '참고 자료'],
  ['CHARACTER_SHEET', '캐릭터 시트'],
  ['UNDETERMINED', '미확정'],
]

const documentStatusLabel: Record<KnowledgeDocumentView['status'], string> = {
  UPLOADED: '준비 대기 중', NEEDS_INPUT: '추가 확인 필요', QUEUED: '준비 대기 중', PROCESSING: '자료 준비 중',
  FAILED: '자료 준비 실패', EXTRACTED: '자료 확인 필요', INDEXED: '사용 준비 완료',
  READY: '사용 준비 완료',
  PARTIAL_AWAITING_CONFIRMATION: '확인 필요', PARTIAL_CONFIRMED: '사용 준비 완료', REJECTED: '자료 사용 불가',
}
const sessionStatusLabel = { DRAFT: '준비 중', STARTING: '시작하는 중', STARTED: '진행 중', COMPLETED: '완료', DELETED: '삭제됨' } as const

export function BundleDetailPage({ bundleId, api, playerId, sessionApi }: { bundleId: string; api: SetupApi; playerId: string; sessionApi: Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage'> & Partial<Pick<AdventureSessionApi, 'readGmProvider' | 'switchGmProvider'>> }) {
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [packages, setPackages] = useState<ScenarioPackageView[]>([])
  const [publishedBlueprintPackageIds, setPublishedBlueprintPackageIds] = useState<Set<string>>(new Set())
  const [sessions, setSessions] = useState<AdventureSessionView[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [rolesByDocument, setRolesByDocument] = useState<Record<string, ScenarioBundleRole>>({})
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [creatingSessionFor, setCreatingSessionFor] = useState<string | null>(null)
  const [catalogRulebooks, setCatalogRulebooks] = useState<CatalogRulebook[]>([])
  const [selectedCatalogRulebookIds, setSelectedCatalogRulebookIds] = useState<Set<string>>(new Set())
  const [gmProvider, setGmProvider] = useState('codex-cli')
  const [gmModel, setGmModel] = useState('gpt-5.6-luna')
  const [gmReasoning, setGmReasoning] = useState('medium')
  const [preparing, setPreparing] = useState(false)

  useEffect(() => {
    window.localStorage.setItem('dnd-selected-bundle-id', bundleId)
    window.dispatchEvent(new Event('dnd-selected-bundle-change'))
  }, [bundleId])

  useEffect(() => {
    let active = true
    void Promise.all([
      api.getScenarioBundle?.(bundleId),
      api.listKnowledgeDocuments(playerId),
      api.listScenarioPackages?.(bundleId) ?? Promise.resolve([]),
    ]).then(async ([loadedBundle, loadedDocuments, loadedPackages]) => {
      if (!active || !loadedBundle) return
      setBundle(loadedBundle)
      setDocuments(loadedDocuments)
      setPackages(loadedPackages)
      const currentPackage = loadedPackages.find(item => item.bundleRevision === loadedBundle.currentRevision)
      setSessions(currentPackage ? await sessionApi.listByScenarioPackage(currentPackage.packageId) : [])
      setSelectedIds(new Set(loadedBundle.documents.map(document => document.knowledgeDocumentId)))
      setSelectedCatalogRulebookIds(new Set(loadedBundle.documents.filter(document => document.role === 'RULEBOOK').map(document => document.knowledgeDocumentId)))
      setRolesByDocument(Object.fromEntries(loadedBundle.documents.map(document => [document.knowledgeDocumentId, document.role])))
    }).catch(error => {
      if (active) setMessage(error instanceof Error ? error.message : '모험 자료 정보를 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [api, bundleId, playerId, sessionApi])

  useEffect(() => {
    if (!api.getPlayPreparation) {
      setPublishedBlueprintPackageIds(new Set())
      return
    }
    let active = true
    void Promise.all(packages.filter(item => item.reportStatus === 'COMPLETE').map(async item => {
      try {
        const preparation = await api.getPlayPreparation!(item.packageId)
        return preparation.characterCreationBlueprint.status === 'PUBLISHED' ? item.packageId : null
      } catch {
        return null
      }
    })).then(ids => {
      if (active) setPublishedBlueprintPackageIds(new Set(ids.filter((id): id is string => id !== null)))
    })
    return () => { active = false }
  }, [api, packages])

  useEffect(() => {
    void fetch('/api/v1/rulebook-catalog').then(response => response.ok ? response.json() : []).then((items: CatalogRulebook[]) => {
      setCatalogRulebooks(items.filter(item => item.status === 'READY' && item.rulebookId))
    }).catch(() => setCatalogRulebooks([]))
  }, [])

  const selectedDocuments = useMemo(() => documents.filter(document => selectedIds.has(document.knowledgeDocumentId)), [documents, selectedIds])

  function toggleDocument(documentId: string) {
    setSelectedIds(current => {
      const next = new Set(current)
      if (next.has(documentId)) next.delete(documentId)
      else next.add(documentId)
      return next
    })
  }

  function toggleCatalogRulebook(rulebookId: string) {
    setSelectedCatalogRulebookIds(current => {
      const next = new Set(current)
      if (next.has(rulebookId)) next.delete(rulebookId)
      else next.add(rulebookId)
      return next
    })
  }

  async function save() {
    if (!api.reviseScenarioBundle || !bundle || selectedDocuments.length === 0) return
    setSaving(true)
    setMessage('')
    try {
      const saved = await api.reviseScenarioBundle(bundle.bundleId, playerId, selectedDocuments.map(document => ({
        knowledgeDocumentId: document.knowledgeDocumentId,
        role: rolesByDocument[document.knowledgeDocumentId] ?? 'UNDETERMINED',
      })))
      setBundle(saved)
      setPackages(api.listScenarioPackages ? await api.listScenarioPackages(saved.bundleId) : [])
      setMessage(`모험 자료를 v${saved.currentRevision}로 저장했습니다.`)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 자료를 저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  async function createAdventure(packageId: string) {
    if (selectedCatalogRulebookIds.size === 0) { setMessage('공유 룰북을 하나 이상 선택하세요.'); return }
    if (!api.getPlayPreparation) return
    setCreatingSessionFor(packageId)
    setMessage('모험 세션을 준비하고 있습니다.')
    try {
      const preparation = await api.getPlayPreparation(packageId)
      if (preparation.characterCreationBlueprint.status !== 'PUBLISHED' || preparation.characterCreationBlueprint.revision == null) {
        setPublishedBlueprintPackageIds(current => {
          const next = new Set(current)
          next.delete(packageId)
          return next
        })
        setMessage('캐릭터 생성 설정을 먼저 검토하고 게시해 주세요.')
        return
      }
      const session = await sessionApi.create({
        scenarioPackageId: packageId,
        blueprintId: packageId,
        blueprintRevision: preparation.characterCreationBlueprint.revision ?? 0,
        runtimeConfiguration: {
          scenarioId: packageId,
          ruleSetId: crypto.randomUUID(),
          rulebookIds: [...selectedCatalogRulebookIds],
          // Internal runtime contract. Users choose GM connection below;
          // adapter and capabilities remain server-owned defaults.
          engineId: 'codex-cli',
          toolIds: ['search', 'move', 'dice', 'character'],
          initialScene: 'opening',
        },
      })
      if (sessionApi.readGmProvider && sessionApi.switchGmProvider) {
        const currentProvider = await sessionApi.readGmProvider(session.sessionId)
        await sessionApi.switchGmProvider(session.sessionId, currentProvider.version, { provider: gmProvider, model: gmModel, reasoning: gmReasoning })
      }
      window.location.hash = `#/sessions/${session.sessionId}/party`
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 세션을 만들지 못했습니다.')
    } finally {
      setCreatingSessionFor(null)
    }
  }

  function openCharacter(packageId: string) {
    window.location.hash = `#/scenario-packages/${packageId}/character-blueprint`
  }

  if (!bundle) return <section aria-labelledby="bundle-detail-title"><h2 id="bundle-detail-title">모험 자료 불러오는 중…</h2>{message && <p role="alert">{message}</p>}</section>

  return <section aria-labelledby="bundle-detail-title" className="bundle-detail-page">
    <div className="bundle-detail-heading">
      <div>
        <p className="eyebrow">ADVENTURE MATERIALS</p>
        <h2 id="bundle-detail-title">모험 자료 구성</h2>
        <p>{bundle.name ?? '이름 없는 모험 자료'} · 포함된 자료 {bundle.documents.length}개</p>
      </div>
      <section className="setup-panel" aria-labelledby="catalog-selection-heading"><h3 id="catalog-selection-heading">공유 룰북 revision</h3>{catalogRulebooks.length === 0 ? <p>공개된 룰북이 없습니다.</p> : <ul>{catalogRulebooks.map(rulebook => <li key={rulebook.catalogRevisionId}><label><Checkbox aria-label={`${rulebook.displayName} 선택`} checked={selectedCatalogRulebookIds.has(rulebook.rulebookId!)} onCheckedChange={() => toggleCatalogRulebook(rulebook.rulebookId!)} />{rulebook.displayName} · revision {rulebook.revisionNumber}</label></li>)}</ul>}</section>
      <Button type="button" onClick={() => void save()} disabled={saving || selectedDocuments.length === 0}>
        {saving ? '저장 중…' : '자료 변경사항 저장'}
      </Button>
    </div>
    {message && <p role="status">{message}</p>}
      <Card>
      <div className="bundle-card-heading"><h3>포함된 자료 ({selectedDocuments.length})</h3></div>
      <CardContent>
        <ul className="bundle-component-list" aria-label="모험 자료 목록">
          {documents.map(document => <li key={document.knowledgeDocumentId}>
            <Checkbox aria-label={`${document.originalFilename} 포함`} checked={selectedIds.has(document.knowledgeDocumentId)} onCheckedChange={() => toggleDocument(document.knowledgeDocumentId)} />
            <div className="bundle-component-info">
              <strong>{document.originalFilename}</strong>
            <small>{documentStatusLabel[document.status]}</small>
            </div>
            <Select aria-label={`${document.originalFilename} 역할`} value={rolesByDocument[document.knowledgeDocumentId] ?? 'UNDETERMINED'} disabled={!selectedIds.has(document.knowledgeDocumentId)} onChange={event => setRolesByDocument(current => ({ ...current, [document.knowledgeDocumentId]: event.target.value as ScenarioBundleRole }))}>
              {roles.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </li>)}
        </ul>
      </CardContent>
      </Card>
    <Card>
      <div className="bundle-card-heading"><h3>AI GM 설정</h3></div>
      <CardContent>
        <div className="runtime-options-grid">
          <label>연결 방식<select aria-label="GM provider" value={gmProvider} onChange={event => setGmProvider(event.currentTarget.value)}><option value="codex-cli">Codex OAuth</option><option value="openai">OpenAI 호환</option></select></label>
          <label>모델<input aria-label="GM model" value={gmModel} onChange={event => setGmModel(event.currentTarget.value)} /></label>
          <label>추론 수준<select aria-label="GM reasoning" value={gmReasoning} onChange={event => setGmReasoning(event.currentTarget.value)}><option value="low">low</option><option value="medium">medium</option><option value="high">high</option></select></label>
        </div>
        <p className="form-hint">도구와 실행 adapter는 게임 시스템이 자동으로 구성합니다. GM 연결 방식만 선택합니다.</p>
      </CardContent>
    </Card>
    <Card>
      <div className="bundle-card-heading"><h3>연결된 모험 세션 ({sessions.length})</h3></div>
      <CardContent>
        {sessions.length === 0 ? <p>이 자료로 생성된 모험 세션이 없습니다.</p> : <ul aria-label="자료로 만든 모험 목록">{sessions.map(session => <li key={session.sessionId}>
          <strong>진행 중인 모험</strong> · {sessionStatusLabel[session.status]} · 캐릭터 {session.party.length}/{session.characterLimit}
          {session.party.length > 0 && <ul>{session.party.map(member => <li key={member.characterSheetId}><a href={`#/character/${member.characterSheetId}`}>캐릭터 시트 열기</a> · {member.controlMode === 'DIRECT' ? '직접 조작' : 'AI 조작'}</li>)}</ul>}
          <Button type="button" variant="outline" onClick={() => { window.location.hash = `#/sessions/${session.sessionId}/party` }}>파티 구성 열기</Button>
        </li>)}</ul>}
      </CardContent>
    </Card>
    <Card>
      <div className="bundle-card-heading"><h3>모험 준비 결과</h3></div>
      <CardContent>
          <Button type="button" onClick={() => setPreparing(true)}>게임 준비</Button>
          {packages.length === 0 ? <p>아직 모험 준비가 끝나지 않았습니다.</p> : <ul aria-label="모험 준비 결과 목록">{packages.map(item => <li key={item.packageId}>v{item.bundleRevision} · {item.reportStatus} <Button type="button" onClick={() => void createAdventure(item.packageId)} disabled={item.reportStatus !== 'COMPLETE' || !publishedBlueprintPackageIds.has(item.packageId) || creatingSessionFor !== null}>{creatingSessionFor === item.packageId ? '세션 준비 중…' : '이 자료로 모험 만들기'}</Button> <Button type="button" variant="outline" onClick={() => openCharacter(item.packageId)}>캐릭터 생성 시작</Button>{item.reportStatus === 'COMPLETE' && !publishedBlueprintPackageIds.has(item.packageId) ? <small> 캐릭터 생성 설정 게시 후 모험을 만들 수 있습니다.</small> : null}</li>)}</ul>}
      </CardContent>
    </Card>
    {preparing && <PreparationModal bundleId={bundle.bundleId} revision={bundle.currentRevision} api={api} ownerId={playerId} onClose={() => setPreparing(false)} onCharacter={openCharacter} onAdventure={packageId => void createAdventure(packageId)} />}
  </section>
}
