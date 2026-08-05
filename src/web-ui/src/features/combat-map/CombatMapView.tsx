import { useEffect, useState } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState } from '../saved-adventures/AdventurePlayApi'
import { moveCandidate, type MapInteractionCandidate } from './MapInteractionCandidate'

export function CombatMapView({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [selectedToken, setSelectedToken] = useState<string | null>(null)
  const [candidate, setCandidate] = useState<MapInteractionCandidate | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    void api.getCombatMap(adventureId).then(setMap).catch(() => setMap(null))
  }, [adventureId, api])

  function chooseCell(cell: { x: number; y: number }) {
    if (!map || !selectedToken) return
    const token = map.tokens?.find(item => item.id === selectedToken)
    if (!token || (token.x === cell.x && token.y === cell.y)) return
    setCandidate(moveCandidate(map.mapId ?? '', map.version ?? 0, token.id, { x: token.x, y: token.y }, cell))
  }

  async function confirm() {
    if (!candidate) return
    try {
      if (!api.submitMapAction) throw new Error('맵 행동 API를 사용할 수 없습니다.')
      await api.submitMapAction(adventureId, {
        mapId: candidate.mapId, mapVersion: candidate.mapVersion, tokenId: candidate.tokenId,
        action: 'MOVE', path: [candidate.from, candidate.to], location: candidate.to,
      })
      setCandidate(null); setSelectedToken(null); setMessage('맵 행동을 GM 턴으로 전송했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '맵 행동을 처리하지 못했습니다.')
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
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0}>
            {Array.from({ length: grid.width * grid.height }, (_, index) => {
              const cell = { x: index % grid.width, y: Math.floor(index / grid.width) }
              const token = map.tokens?.find(item => item.x === cell.x && item.y === cell.y)
              const blocked = map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
              return <button key={`${cell.x}-${cell.y}`} type="button" aria-label={`격자 ${cell.x},${cell.y}`} disabled={blocked} onClick={() => chooseCell(cell)} onDragOver={event => event.preventDefault()} onDrop={() => chooseCell(cell)}>
                {token ? <span role="button" draggable={token.type === 'PLAYER'} onDragStart={() => { if (token.type === 'PLAYER') setSelectedToken(token.id) }} onClick={event => { event.stopPropagation(); if (token.type === 'PLAYER') setSelectedToken(token.id) }} data-token-type={token.type}>{token.type} ({token.x},{token.y})</span> : blocked ? '장애물' : `${cell.x},${cell.y}`}
              </button>
            })}
          </div>
          <aside aria-label="맵 범례"><span>PLAYER: 플레이어 파티</span><span>NPC: 인물</span><span>ENEMY: 적</span></aside>
        </div>
      ) : map && <p role="note">현재 장면에 사용할 안전한 맵이 없습니다. 텍스트로 계속 진행합니다.</p>}
      {candidate && <div role="dialog" aria-label="맵 행동 확인"><p>이동: ({candidate.from.x},{candidate.from.y}) → ({candidate.to.x},{candidate.to.y})</p><button type="button" onClick={() => void confirm()}>확인</button><button type="button" onClick={() => { setCandidate(null); setSelectedToken(null) }}>취소</button></div>}
      <p role="status">{message}</p>
    </section>
  )
}
