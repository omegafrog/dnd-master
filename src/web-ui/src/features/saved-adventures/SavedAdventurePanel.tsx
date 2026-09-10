import { useEffect, useState } from 'react'
import { CalendarDays, ChevronRight, MoreHorizontal, Play, ScrollText, Trash2 } from 'lucide-react'
import { Button } from '../../components/ui/button'
import { Separator } from '../../components/ui/separator'
import type { AdventurePlayApi, SavedAdventure, SessionKnowledgeSet } from './AdventurePlayApi'
import type {
  KnowledgeDocumentView,
  SetupApi,
} from '../rulebooks/SetupApi'

export function SavedAdventurePanel({
  playApi,
  setupApi,
  playerId,
  onResumed,
  forceList = false,
}: {
  playApi: AdventurePlayApi
  setupApi: SetupApi
  playerId: string
  onResumed?: (adventureId: string) => void
  forceList?: boolean
}) {
  const [items, setItems] = useState<SavedAdventure[]>([])
  const [loaded, setLoaded] = useState(false)
  const [message, setMessage] = useState('')
  const [selectedAdventureId, setSelectedAdventureId] = useState<string | null>(null)
  const [selectedAdventure, setSelectedAdventure] = useState<SessionKnowledgeSet | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<Set<string>>(new Set())
  const [sessionMessage, setSessionMessage] = useState('')

  const load = () => {
    setMessage('')
    setLoaded(false)
    void playApi.listSaved(playerId).then(nextItems => {
      setItems(nextItems)

      if (!forceList) {
        if (nextItems.length === 0) {
          window.location.hash = '#/setup?mode=create'
          return
        }
        if (nextItems.length === 1) {
          window.location.hash = `#/adventures/${encodeURIComponent(nextItems[0].id)}?tab=materials`
          return
        }
      }

      setLoaded(true)
    }).catch(() => {
      setLoaded(true)
      setMessage('모험 목록을 불러오지 못했습니다. 다시 시도해 주세요.')
    })
  }
  useEffect(load, [playApi, playerId, forceList])

  async function resume(id: string) {
    try {
      await playApi.resume(id)
      setMessage('모험을 재개했습니다.')
      onResumed?.(id)
    } catch {
      setMessage('모험을 재개하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    }
  }

  async function remove(item: SavedAdventure) {
    try {
      await playApi.deleteAdventure(item.id, playerId, item.version)
      setItems(old => old.filter(x => x.id !== item.id))
      setMessage('모험을 삭제했습니다.')
    } catch {
      setMessage('모험을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
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
      setSessionMessage('세션 자료를 불러오지 못했습니다. 다시 시도해 주세요.')
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
      setSessionMessage('세션 자료를 저장하지 못했습니다. 다시 시도해 주세요.')
    }
  }

  if (!loaded) {
    return <section className="saved-adventure-routing" aria-busy="true"><p role="status">모험을 여는 중입니다.</p></section>
  }

  return (
    <section className="saved-adventure-page" aria-labelledby="saved-heading">
      <div className="page-heading"><div><p className="eyebrow">모험 기록</p><h1 id="saved-heading">모험 목록</h1><p>준비 중인 모험을 열고, 자료와 세션 기록을 확인하세요.</p></div><span className="page-heading-actions"><a className="ui-button ui-button-outline" href="#/setup?mode=create"><PlusIcon />새 모험</a><Button variant="outline" onClick={load}><CalendarDays size={15} aria-hidden="true" />목록 새로고침</Button></span></div>
      <p className="workspace-inline-notice" role="status">{message}</p>
      {items.length === 0 && <div className="workspace-empty"><span className="workspace-empty-icon" aria-hidden="true"><ScrollText size={20} /></span><h2>저장된 모험이 없습니다</h2><p>새 모험을 만들고 자료를 추가하면 이곳에 표시됩니다.</p><a className="text-link" href="#/setup?mode=create">새 모험 만들기</a></div>}
      <ul className="adventure-list" aria-label="저장된 모험 목록">
        {items.map(item => (
          <li key={item.id} className="adventure-list-row">
            <span className="adventure-row-mark" aria-hidden="true"><ScrollText size={18} /></span>
            <span className="adventure-row-main"><strong>{item.title}</strong><small><span className={`file-status file-status-${item.resumable ? 'ready' : 'processing'}`}>{item.resumable ? '진행 중' : '완료됨'}</span>{item.updatedAt ? ` · ${formatDate(item.updatedAt)}` : ''}</small></span>
            <span className="adventure-row-status">{item.statusLabel}</span>
            <span className="adventure-row-actions">
              {item.resumable && <Button onClick={() => void resume(item.id)}><Play size={14} aria-hidden="true" />재개</Button>}
              <Button variant="ghost" className="adventure-delete-action" aria-label={`${item.title} 삭제`} onClick={() => void remove(item)}><Trash2 size={14} aria-hidden="true" />삭제</Button>
              <a className="ui-button ui-button-outline" href={`#/adventures/${encodeURIComponent(item.id)}?tab=materials`}>열기<ChevronRight size={14} aria-hidden="true" /></a>
              <Button variant="ghost" size="icon" aria-label={`${item.title} 더보기`} onClick={() => void openSessionKnowledgeSet(item.id)}><MoreHorizontal size={17} aria-hidden="true" /></Button>
              <Button variant="ghost" className="adventure-config-action" onClick={() => void openSessionKnowledgeSet(item.id)}>자료 설정</Button>
            </span>
          </li>
        ))}
      </ul>
      {selectedAdventureId && (
        <section className="session-knowledge-panel" aria-labelledby="session-knowledge-heading">
          <div className="tab-panel-heading"><div><p className="eyebrow">세션 자료</p><h2 id="session-knowledge-heading">사용할 자료 선택</h2></div></div>
          <p role="status">{sessionMessage}</p>
          {documents.length === 0 ? <p>선택할 수 있는 자료가 없습니다.</p> : <ul aria-label="세션 자료 목록" className="session-knowledge-list">{documents.map(document => { const available = ['INDEXED', 'EXTRACTED', 'READY'].includes(document.status); const checked = selectedDocumentIds.has(document.knowledgeDocumentId); return <li key={document.knowledgeDocumentId}><label><input type="checkbox" checked={checked} disabled={!available} onChange={() => toggleDocument(document.knowledgeDocumentId)} />{document.originalFilename}</label><span>{available ? '사용 가능' : '준비 중'}</span></li> })}</ul>}
          <Separator />
          <Button type="button" onClick={() => void saveSessionKnowledgeSet()}>세션 자료 저장</Button>
        </section>
      )}
    </section>
  )
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('ko-KR')
}

function PlusIcon() {
  return <span aria-hidden="true">+</span>
}
