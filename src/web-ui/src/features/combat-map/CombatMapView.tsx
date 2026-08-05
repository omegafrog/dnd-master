import { useEffect, useState } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState } from '../saved-adventures/AdventurePlayApi'
import { actionCandidate, moveCandidate, type MapInteractionCandidate } from './MapInteractionCandidate'

export function CombatMapView({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [selectedToken, setSelectedToken] = useState<string | null>(null)
  const [candidate, setCandidate] = useState<MapInteractionCandidate | null>(null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [locationMode, setLocationMode] = useState(false)

  useEffect(() => {
    void api.getCombatMap(adventureId).then(setMap).catch(() => setMap(null))
  }, [adventureId, api])

  function chooseCell(cell: { x: number; y: number }) {
    if (!map || !selectedToken) return
    const token = map.tokens?.find(item => item.id === selectedToken)
    if (!token || (token.x === cell.x && token.y === cell.y)) {
      if (locationMode && selectedToken && map.mapId) {
        setCandidate(actionCandidate(map.mapId, map.version ?? 0, selectedToken, 'LOCATION', cell))
      }
      return
    }
    setCandidate(moveCandidate(map.mapId ?? '', map.version ?? 0, token.id, { x: token.x, y: token.y }, cell))
  }

  async function confirm() {
    if (!candidate) return
    if (submitting) return
    setSubmitting(true)
    try {
      if (!api.submitMapAction) throw new Error('맵 행동 API를 사용할 수 없습니다.')
      await api.submitMapAction(adventureId, {
        mapId: candidate.mapId, mapVersion: candidate.mapVersion, tokenId: candidate.tokenId,
        action: candidate.action, path: candidate.from && candidate.to ? gridPath(candidate.from, candidate.to) : undefined,
        targetId: candidate.targetId, location: candidate.location ?? candidate.to,
      }, undefined, map?.sessionVersion ?? map?.version ?? 0)
      const refreshed = await api.getCombatMap(adventureId)
      setMap(refreshed); setCandidate(null); setSelectedToken(null); setMessage('맵 행동을 GM 턴으로 전송했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '맵 행동을 처리하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const grid = map?.grid ?? { width: 0, height: 0 }
  return (
    <section className="adventure-tool map-panel" aria-labelledby="map-heading">
      <h2 id="map-heading">플레이어 전투 맵</h2>
      <p>모험 ID: {adventureId}</p>
      <p role="status">{map ? `현재 맵 상태: ${map.status}` : '전투 맵을 불러오는 중…'}</p>
      {map?.mapId && map.tokens ? (
        <div className="tactical-map-window">
          <button type="button" aria-pressed={locationMode} onClick={() => setLocationMode(current => !current)}>위치 선택</button>
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0}>
            {Array.from({ length: grid.width * grid.height }, (_, index) => {
              const cell = { x: index % grid.width, y: Math.floor(index / grid.width) }
              const token = map.tokens?.find(item => item.x === cell.x && item.y === cell.y)
              const blocked = map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
              const door = map.doors?.find(item => item.x === cell.x && item.y === cell.y)
              const visible = map.current?.some(item => item.x === cell.x && item.y === cell.y) ?? true
              const explored = map.explored?.some(item => item.x === cell.x && item.y === cell.y) ?? visible
              return <button key={`${cell.x}-${cell.y}`} type="button" aria-label={visible && token ? `${token.type} ${token.x},${token.y}` : visible ? `격자 ${cell.x},${cell.y}` : explored ? `탐험한 격자 ${cell.x},${cell.y}` : '미탐험 영역'} data-visibility={visible ? 'current' : explored ? 'explored' : 'hidden'} data-last-seen={token?.lastSeen ? 'true' : 'false'} disabled={blocked || !visible} draggable={token?.type === 'PLAYER'} onDragStart={() => { if (token?.type === 'PLAYER') setSelectedToken(token.id) }} onClick={() => { if (token?.type === 'PLAYER') setSelectedToken(token.id); else chooseCell(cell) }} onDragOver={event => event.preventDefault()} onDrop={() => chooseCell(cell)}>
                {visible && token ? `${token.type} (${token.x},${token.y})` : door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '장애물' : visible ? `${cell.x},${cell.y}` : explored ? '안개' : ''}
              </button>
            })}
          </div>
          <aside aria-label="맵 범례" className="map-legend">{[
            ['PLAYER', '●', '플레이어 캐릭터'], ['FRIENDLY_NPC', '◆', '우호 NPC'], ['NEUTRAL_NPC', '◇', '중립 NPC'],
            ['ENEMY', '▲', '적대 몬스터'], ['BOSS', '★', '보스'], ['TRAP', '⚠', '발견된 함정'], ['OBJECT', '■', '상호작용 오브젝트'],
          ].map(([type, icon, label]) => <span key={type} className={`legend-token legend-${type.toLowerCase()}`}><span aria-hidden="true">{icon}</span><span>{label}</span></span>)}</aside>
        </div>
      ) : map && <p role="note">현재 장면에 사용할 안전한 맵이 없습니다. 텍스트로 계속 진행합니다.</p>}
      {map?.tokens?.filter(token => token.type !== 'PLAYER' && !token.lastSeen).map(token => <button key={`target-${token.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'TARGET', { x: token.x, y: token.y }, token.id)) }}>대상 선택: {token.type}</button>)}
      {map?.objects?.map(object => <button key={`object-${object.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'INTERACT', { x: object.x, y: object.y }, object.id)) }}>상호작용: {object.type}</button>)}
      {candidate && <div role="dialog" aria-label="맵 행동 확인"><p>{candidate.action === 'MOVE' && candidate.from && candidate.to ? `이동: (${candidate.from.x},${candidate.from.y}) → (${candidate.to.x},${candidate.to.y})` : `맵 행동: ${candidate.action}`}</p><button type="button" disabled={submitting} onClick={() => void confirm()}>확인</button><button type="button" disabled={submitting} onClick={() => { setCandidate(null); setSelectedToken(null) }}>취소</button></div>}
      <p role="status">{message}</p>
    </section>
  )
}

function gridPath(from: { x: number; y: number }, to: { x: number; y: number }) {
  const path = [{ ...from }]
  let current = { ...from }
  while (current.x !== to.x || current.y !== to.y) {
    current = { x: current.x + Math.sign(to.x - current.x), y: current.y + Math.sign(to.y - current.y) }
    path.push({ ...current })
  }
  return path
}
