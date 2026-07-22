import { useEffect, useState } from 'react'
import type { AdventurePlayApi, SavedAdventure, SessionKnowledgeSet } from './AdventurePlayApi'
import type { KnowledgeDocumentView, SetupApi } from '../rulebooks/SetupApi'

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
      const [sessionKnowledgeSet, libraryDocuments] = await Promise.all([
        playApi.getSessionKnowledgeSet(adventureId),
        setupApi.listKnowledgeDocuments(playerId),
      ])
      setSelectedAdventureId(adventureId)
      setSelectedAdventure(sessionKnowledgeSet)
      setDocuments(libraryDocuments)
      setSelectedDocumentIds(new Set(sessionKnowledgeSet.knowledgeDocumentIds))
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

  return (
    <section aria-labelledby="saved-heading">
      <h2 id="saved-heading">저장한 모험</h2>
      <p role="status">{message}</p>
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
