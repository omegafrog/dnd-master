import { useEffect, useState } from 'react'
import { CalendarDays, ChevronRight, Play, ScrollText, Trash2 } from 'lucide-react'
import { Button } from '../../components/ui/button'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { AdventurePlayApi, SavedAdventure } from './AdventurePlayApi'
import type {
  SetupApi,
  ScenarioBundleView,
} from '../rulebooks/SetupApi'

type IncompleteAdventure = {
  id: string
  bundleId: string
  title: string
  revision: number
}

export function SavedAdventurePanel({
  playApi,
  setupApi,
  sessionApi,
  playerId,
  onResumed,
  forceList = false,
}: {
  playApi: AdventurePlayApi
  setupApi: SetupApi
  sessionApi?: Pick<AdventureSessionApi, 'listByScenarioPackage' | 'delete'>
  playerId: string
  onResumed?: (adventureId: string) => void
  forceList?: boolean
}) {
  const [items, setItems] = useState<SavedAdventure[]>([])
  const [incomplete, setIncomplete] = useState<IncompleteAdventure[]>([])
  const [loaded, setLoaded] = useState(false)
  const [message, setMessage] = useState('')
  const [deletingBundleId, setDeletingBundleId] = useState<string | null>(null)

  const load = () => {
    setMessage('')
    setLoaded(false)
    void Promise.all([
      playApi.listSaved(playerId),
      setupApi.listScenarioBundles?.() ?? Promise.resolve<ScenarioBundleView[]>([]),
    ]).then(([nextItems, bundles]) => {
      setItems(nextItems)
      // Before a session is created, the durable record is the scenario
      // bundle. Keep it visible so an interrupted setup can be continued.
      const savedBundleIds = new Set(nextItems.map(item => item.scenarioBundleId).filter(Boolean))
      setIncomplete(bundles.filter(bundle => !savedBundleIds.has(bundle.bundleId)).map(bundle => ({
        id: `bundle:${bundle.bundleId}`,
        bundleId: bundle.bundleId,
        title: bundle.name ?? '이름 없는 모험',
        revision: bundle.currentRevision,
      })))

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

  function openAdventure(item: SavedAdventure) {
    window.location.hash = item.sessionId
      ? `#/sessions/${encodeURIComponent(item.sessionId)}/party`
      : `#/adventures/${encodeURIComponent(item.id)}?tab=materials`
  }

  async function removeIncomplete(item: IncompleteAdventure) {
    if (!setupApi.deleteScenarioBundle || deletingBundleId) return
    setDeletingBundleId(item.bundleId)
    setMessage('연결된 세션과 모험 자료를 삭제하는 중입니다.')
    try {
      if (sessionApi && setupApi.listScenarioPackages) {
        const packages = await setupApi.listScenarioPackages(item.bundleId)
        const sessionResults = await Promise.all(packages.map(item => sessionApi.listByScenarioPackage(item.packageId)))
        const sessions = [...new Map(sessionResults.flat().map(session => [session.sessionId, session])).values()]
          .filter(session => session.status !== 'COMPLETED' && session.status !== 'DELETED')
        const outcomes = await Promise.allSettled(sessions.map(session => sessionApi.delete(session.sessionId, session.version)))
        const failedCount = outcomes.filter(outcome => outcome.status === 'rejected').length
        if (failedCount > 0) throw new Error(`연결된 세션 ${failedCount}개를 삭제하지 못했습니다.`)
      }
      await setupApi.deleteScenarioBundle(item.bundleId)
      setIncomplete(old => old.filter(value => value.id !== item.id))
      setMessage('중단한 모험 자료를 삭제했습니다.')
    } catch {
      setMessage('중단한 모험 자료를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setDeletingBundleId(null)
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

  if (!loaded) {
    return <section className="saved-adventure-routing" aria-busy="true"><p role="status">모험을 여는 중입니다.</p></section>
  }

  return (
    <section className="saved-adventure-page" aria-labelledby="saved-heading">
      <div className="page-heading"><div><p className="eyebrow">모험 기록</p><h1 id="saved-heading">모험 목록</h1><p>준비 중인 모험을 열고, 자료와 세션 기록을 확인하세요.</p></div><span className="page-heading-actions"><a className="ui-button ui-button-outline" href="#/setup?mode=create"><PlusIcon />새 모험</a><Button variant="outline" onClick={load}><CalendarDays size={15} aria-hidden="true" />목록 새로고침</Button></span></div>
      <p className="workspace-inline-notice" role="status">{message}</p>
      {items.length === 0 && incomplete.length === 0 && <div className="workspace-empty"><span className="workspace-empty-icon" aria-hidden="true"><ScrollText size={20} /></span><h2>새로운 모험을 시작하세요</h2><p>새 모험을 만들고 자료를 추가하면 모험 목록에 표시됩니다.</p><a className="text-link" href="#/setup?mode=create">새로운 모험 시작</a></div>}
      <ul className="adventure-list" aria-label="저장된 모험 목록">
        {incomplete.map(item => (
          <li key={item.id} className="adventure-list-row">
            <span className="adventure-row-mark" aria-hidden="true"><ScrollText size={18} /></span>
            <span className="adventure-row-main"><strong>{item.title}</strong><small><span className="file-status file-status-processing">준비 중</span> · 자료 v{item.revision}</small></span>
            <span className="adventure-row-status">준비 중인 모험</span>
            <span className="adventure-row-actions">
              <a className="ui-button ui-button-outline" href={`#/bundles/${encodeURIComponent(item.bundleId)}`}>준비 이어하기<ChevronRight size={14} aria-hidden="true" /></a>
              {setupApi.deleteScenarioBundle && <Button type="button" variant="ghost" className="adventure-delete-action" aria-label={`${item.title} 삭제`} disabled={deletingBundleId !== null} onClick={() => void removeIncomplete(item)}><Trash2 size={14} aria-hidden="true" />{deletingBundleId === item.bundleId ? '삭제 중…' : '삭제'}</Button>}
            </span>
          </li>
        ))}
        {items.map(item => (
          <li key={item.id} className="adventure-list-row">
            <span className="adventure-row-mark" aria-hidden="true"><ScrollText size={18} /></span>
            <span className="adventure-row-main"><strong>{item.title}</strong><small><span className={`file-status file-status-${item.resumable ? 'ready' : 'processing'}`}>{item.resumable ? '진행 중' : '완료됨'}</span>{item.updatedAt ? ` · ${formatDate(item.updatedAt)}` : ''}</small></span>
            <span className="adventure-row-status">{item.statusLabel}</span>
            <span className="adventure-row-actions">
              {item.resumable && <Button onClick={() => item.sessionId ? openAdventure(item) : void resume(item.id)}><Play size={14} aria-hidden="true" />재개</Button>}
              <Button variant="ghost" className="adventure-delete-action" aria-label={`${item.title} 삭제`} onClick={() => void remove(item)}><Trash2 size={14} aria-hidden="true" />삭제</Button>
              <a className="ui-button ui-button-outline" href={item.sessionId ? `#/sessions/${encodeURIComponent(item.sessionId)}/party` : `#/adventures/${encodeURIComponent(item.id)}?tab=materials`}>열기<ChevronRight size={14} aria-hidden="true" /></a>
            </span>
          </li>
        ))}
      </ul>
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
