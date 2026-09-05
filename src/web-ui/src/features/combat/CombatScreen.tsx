import { useState } from 'react'
import type { CombatApi, CombatSnapshot } from './CombatApi'

export function CombatScreen({ snapshot, api }: { snapshot: CombatSnapshot; api?: CombatApi }) {
  const [feedback, setFeedback] = useState<string | null>(null)
  const [movementPath, setMovementPath] = useState('')
  const current = snapshot.initiative.find(item => item.participantId === snapshot.currentParticipantId)
  const humanTurn = current?.controller === 'PLAYER'
  const runAction = async () => {
    if (!api) return
    try {
      const result = await api.submitAction(snapshot.adventureId, {
        characterSheetId: snapshot.currentParticipantId,
        action: 'attack',
      }, snapshot.version)
      setFeedback(result.judgment ?? result.status)
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '행동을 실행할 수 없습니다.')
    }
  }
  const endTurn = async () => {
    if (!api) return
    try {
      const result = await api.endTurn(snapshot.adventureId, snapshot.currentParticipantId, snapshot.version)
      setFeedback(result.status)
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '턴을 종료할 수 없습니다.')
    }
  }
  const runMovement = async () => {
    if (!api) return
    const path = movementPath.split(';').map(value => value.trim()).filter(Boolean).map(value => {
      const [x, y] = value.split(',').map(Number)
      return { x, y }
    })
    try {
      const request = {
        characterSheetId: snapshot.currentParticipantId,
        action: 'MOVE' as const, movementPath: path,
      }
      const result = api.submitMovement
        ? await api.submitMovement(snapshot.adventureId, request, snapshot.version)
        : await api.submitAction(snapshot.adventureId, request, snapshot.version)
      setFeedback(result.judgment ?? result.status)
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '이동할 수 없습니다.')
    }
  }
  return <section className="combat-screen" aria-labelledby="combat-title">
    <header className="combat-header">
      <p className="eyebrow">COMBAT MODE</p>
      <h1 id="combat-title">전투 · Round {snapshot.round}</h1>
      <p role="status">현재 턴: {snapshot.initiative.find(item => item.participantId === snapshot.currentParticipantId)?.displayName ?? '알 수 없음'}</p>
    </header>
    <section aria-labelledby="initiative-title">
      <h2 id="initiative-title">Initiative</h2>
      <ol>{snapshot.initiative.map(item => <li key={item.participantId} aria-current={item.participantId === snapshot.currentParticipantId ? 'step' : undefined}>
        <span>{item.displayName}</span> <span>{item.initiative}</span>{item.publicCondition && <small> · {item.publicCondition}</small>}
      </li>)}</ol>
    </section>
    <section aria-labelledby="resources-title">
      <h2 id="resources-title">내 턴 자원</h2>
      <p>이동 {snapshot.resources.movement}ft · Action {snapshot.resources.actionAvailable ? '가능' : '사용'}</p>
      <p>Bonus Action {snapshot.resources.bonusActionAvailable ? '가능' : '사용'} · Reaction {snapshot.resources.reactionAvailable ? '가능' : '사용'}</p>
    </section>
    {snapshot.narrativePositions && snapshot.narrativePositions.length > 0 && <section aria-labelledby="narrative-position-title">
      <h2 id="narrative-position-title">서술 전장</h2>
      {snapshot.narrativePositions.map(position => <p key={`${position.subjectId}-${position.targetId}`}>
        상대적 위치: {position.rangeBand} · 엄폐: {position.cover}
      </p>)}
    </section>}
    {humanTurn && <section aria-labelledby="actions-title">
      <h2 id="actions-title">행동</h2>
      <button type="button" onClick={() => void runAction()} disabled={!snapshot.resources.actionAvailable}>공격 실행</button>
      <label>이동 경로 (x,y; x,y)
        <input aria-label="이동 경로" value={movementPath} onChange={event => setMovementPath(event.target.value)} placeholder="0,0;1,0" />
      </label>
      <button type="button" onClick={() => void runMovement()} disabled={!snapshot.resources.movement || !movementPath}>이동 실행</button>
      <button type="button" onClick={() => void endTurn()}>턴 종료</button>
      {feedback && <p role="status">{feedback}</p>}
    </section>}
  </section>
}
