import { type FormEvent, useEffect, useState } from 'react'
import type { AdventurePlayApi, SavedAdventure, SessionKnowledgeSet } from './AdventurePlayApi'
import type {
  KnowledgeDocumentView,
  LegacyScenarioMigrationView,
  RuntimeBindingView,
  SetupApi,
} from '../rulebooks/SetupApi'

export function SavedAdventurePanel({
  playApi,
  setupApi,
  playerId,
}: {
  playApi: AdventurePlayApi
  setupApi: SetupApi
  playerId: string
}) {
  const [items, setItems] = useState<SavedAdventure[]>([])
  const [message, setMessage] = useState('')
  const [selectedAdventureId, setSelectedAdventureId] = useState<string | null>(null)
  const [selectedAdventure, setSelectedAdventure] = useState<SessionKnowledgeSet | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<Set<string>>(new Set())
  const [sessionMessage, setSessionMessage] = useState('')
  const [legacyScenarioId, setLegacyScenarioId] = useState('')
  const [legacyScenarioFile, setLegacyScenarioFile] = useState<File | null>(null)
  const [legacyScenarioMessage, setLegacyScenarioMessage] = useState('')
  const [migratingLegacyScenario, setMigratingLegacyScenario] = useState(false)
  const [reuploadingLegacyScenario, setReuploadingLegacyScenario] = useState(false)
  const [legacyScenarioResult, setLegacyScenarioResult] = useState<LegacyScenarioMigrationView | null>(null)
  const [runtimeAdventureId, setRuntimeAdventureId] = useState('')
  const [runtimeBinding, setRuntimeBinding] = useState<RuntimeBindingView | null>(null)
  const [switchingLegacyPackage, setSwitchingLegacyPackage] = useState(false)

  useEffect(() => { void playApi.listSaved(playerId).then(setItems).catch(() => {}) }, [playApi, playerId])

  async function resume(id: string) {
    try {
      await playApi.resume(id)
      setMessage('모험을 재개했습니다.')
    } catch {
      setMessage('모험을 재개하지 못했습니다.')
    }
  }

  async function remove(id: string) {
    try {
      await playApi.deleteAdventure(id, playerId, 0)
      setItems(old => old.filter(x => x.id !== id))
      setMessage('모험을 삭제했습니다.')
    } catch {
      setMessage('모험을 삭제하지 못했습니다.')
    }
  }

  async function openSessionKnowledgeSet(adventureId: string) {
    setSessionMessage('')
    try {
      const [sessionKnowledgeSet, libraryDocuments, runtimeBindingView] = await Promise.all([
        playApi.getSessionKnowledgeSet(adventureId),
        setupApi.listKnowledgeDocuments(playerId),
        setupApi.getRuntimeBinding ? setupApi.getRuntimeBinding(adventureId, playerId) : Promise.resolve(null),
      ])
      setSelectedAdventureId(adventureId)
      setSelectedAdventure(sessionKnowledgeSet)
      setDocuments(libraryDocuments)
      setSelectedDocumentIds(new Set(sessionKnowledgeSet.knowledgeDocumentIds))
      setRuntimeAdventureId(adventureId)
      setRuntimeBinding(runtimeBindingView)
    } catch {
      setSessionMessage('세션 자료를 불러오지 못했습니다.')
    }
  }

  function toggleDocument(documentId: string) {
    setSelectedDocumentIds(current => {
      const next = new Set(current)
      if (next.has(documentId)) next.delete(documentId)
      else next.add(documentId)
      return next
    })
  }

  async function saveSessionKnowledgeSet() {
    if (!selectedAdventure) return
    setSessionMessage('')
    try {
      const saved = await playApi.saveSessionKnowledgeSet(
        selectedAdventure.adventureId,
        playerId,
        [...selectedDocumentIds],
      )
      setSelectedAdventure(saved)
      setSelectedDocumentIds(new Set(saved.knowledgeDocumentIds))
      setSessionMessage('세션 자료를 저장했습니다.')
    } catch {
      setSessionMessage('세션 자료를 저장하지 못했습니다.')
    }
  }

  function renderMigrationMessage(result: LegacyScenarioMigrationView) {
    if (result.requiresReupload) {
      return `레거시 소스 누락: ${result.sourceFilename} · 재업로드 필요 · ${result.message}`
    }
    return `${result.message}: ${result.sourceFilename} · 번들 ${result.bundleId} · 패키지 ${result.packageId} · 문서 ${result.knowledgeDocumentId}`
  }

  async function migrateLegacyScenario(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!legacyScenarioId.trim()) return
    setLegacyScenarioMessage('')
    setMigratingLegacyScenario(true)
    try {
      const result = await setupApi.migrateLegacyScenario(legacyScenarioId.trim())
      setLegacyScenarioResult(result)
      setLegacyScenarioMessage(renderMigrationMessage(result))
    } catch {
      setLegacyScenarioMessage('레거시 시나리오를 마이그레이션하지 못했습니다.')
    } finally {
      setMigratingLegacyScenario(false)
    }
  }

  async function reuploadLegacyScenario(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!legacyScenarioId.trim() || !legacyScenarioFile) return
    setLegacyScenarioMessage('')
    setReuploadingLegacyScenario(true)
    try {
      const result = await setupApi.reuploadLegacyScenario(legacyScenarioId.trim(), legacyScenarioFile)
      setLegacyScenarioResult(result)
      setLegacyScenarioMessage(renderMigrationMessage(result))
    } catch {
      setLegacyScenarioMessage('레거시 시나리오를 재업로드하지 못했습니다.')
    } finally {
      setReuploadingLegacyScenario(false)
    }
  }

  async function switchLegacyPackage() {
    if (!legacyScenarioResult?.packageId || !runtimeBinding || !runtimeAdventureId.trim() || !setupApi.switchRuntimePackage) {
      return
    }
    setSwitchingLegacyPackage(true)
    setSessionMessage('')
    try {
      const nextBinding = await setupApi.switchRuntimePackage(
        runtimeAdventureId.trim(),
        playerId,
        runtimeBinding.bindingVersion,
        legacyScenarioResult.packageId,
      )
      setRuntimeBinding(nextBinding)
      setSessionMessage('레거시 패키지를 전환했습니다.')
    } catch {
      setSessionMessage('레거시 패키지를 전환하지 못했습니다.')
    } finally {
      setSwitchingLegacyPackage(false)
    }
  }

  return (
    <section aria-labelledby="saved-heading">
      <h2 id="saved-heading">저장한 모험</h2>
      <p role="status">{message}</p>
      <section aria-labelledby="legacy-scenario-heading">
        <h3 id="legacy-scenario-heading">레거시 시나리오 마이그레이션</h3>
        <form onSubmit={migrateLegacyScenario}>
          <label>
            레거시 시나리오 ID
            <input value={legacyScenarioId} onChange={event => setLegacyScenarioId(event.currentTarget.value)} />
          </label>
          <button type="submit" disabled={migratingLegacyScenario || !legacyScenarioId.trim()}>
            {migratingLegacyScenario ? '마이그레이션 중…' : '레거시 시나리오 마이그레이션'}
          </button>
        </form>
        <form onSubmit={reuploadLegacyScenario}>
          <label>
            재업로드 파일
            <input
              type="file"
              accept=".pdf,.docx,.txt,.md,.png,.jpg,.jpeg,.tif,.tiff,.bmp"
              onChange={event => {
                setLegacyScenarioFile(event.currentTarget.files?.item(0) ?? null)
                setLegacyScenarioMessage('')
              }}
            />
          </label>
          <button type="submit" disabled={reuploadingLegacyScenario || !legacyScenarioId.trim() || legacyScenarioFile == null}>
            {reuploadingLegacyScenario ? '재업로드 중…' : '레거시 재업로드'}
          </button>
        </form>
        {legacyScenarioMessage ? <p role="status">{legacyScenarioMessage}</p> : null}
        {legacyScenarioResult?.packageId && runtimeBinding ? (
          <button type="button" disabled={switchingLegacyPackage || !runtimeAdventureId.trim()} onClick={() => void switchLegacyPackage()}>
            {switchingLegacyPackage ? '패키지 전환 중…' : '현재 모험에 패키지 전환'}
          </button>
        ) : null}
      </section>
      {items.length === 0 && <p>저장된 모험이 없습니다.</p>}
      <ul>
        {items.map(item => (
          <li key={item.id}>
            <strong>{item.title}</strong>
            <button onClick={() => void resume(item.id)}>재개</button>
            <button onClick={() => void remove(item.id)}>삭제</button>
            <button onClick={() => void openSessionKnowledgeSet(item.id)}>자료 설정</button>
          </li>
        ))}
      </ul>
      {selectedAdventureId && (
        <section aria-labelledby="session-knowledge-heading">
          <h3 id="session-knowledge-heading">세션 자료 선택</h3>
          <p>{selectedAdventureId}</p>
          <p role="status">{sessionMessage}</p>
          {documents.length === 0 ? (
            <p>선택할 수 있는 자료가 없습니다.</p>
          ) : (
            <ul aria-label="세션 자료 목록">
              {documents.map(document => {
                const available = document.status === 'INDEXED'
                const checked = selectedDocumentIds.has(document.knowledgeDocumentId)
                return (
                  <li key={document.knowledgeDocumentId}>
                    <label>
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={!available}
                        onChange={() => toggleDocument(document.knowledgeDocumentId)}
                      />
                      {document.originalFilename} ({document.documentType})
                    </label>
                    <span> - {document.status}</span>
                  </li>
                )
              })}
            </ul>
          )}
          <button type="button" onClick={() => void saveSessionKnowledgeSet()}>세션 자료 저장</button>
        </section>
      )}
    </section>
  )
}
