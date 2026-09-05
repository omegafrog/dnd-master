import { useState } from 'react'
import type { CombatApi, CombatSnapshot } from './CombatApi'

export function CombatScreen({ snapshot, api }: { snapshot: CombatSnapshot; api?: CombatApi }) {
  const [feedback, setFeedback] = useState<string | null>(null)
  const [movementPath, setMovementPath] = useState('')
  const [freeFormInput, setFreeFormInput] = useState('')
  const [combatLog, setCombatLog] = useState<string[]>([])
  const [gmNarration, setGmNarration] = useState<string[]>([])
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
  const resolveReaction = async (choice: 'USE' | 'PASS') => {
    if (!api?.resolveReaction || !snapshot.pendingReaction) return
    try {
      const result = await api.resolveReaction(snapshot.adventureId, snapshot.pendingReaction.reactionId, choice, snapshot.version)
      setFeedback(result.status)
    } catch (error) { setFeedback(error instanceof Error ? error.message : 'Reaction을 처리할 수 없습니다.') }
  }
  const runMovement = async () => {
    if (!api) return
    try {
      const path = movementPath.split(';').map(value => value.trim()).filter(Boolean).map(value => {
        const coordinates = value.split(',').map(Number)
        if (coordinates.length !== 2 || coordinates.some(coordinate => !Number.isInteger(coordinate) || coordinate < 0)) {
          throw new Error('이동 경로는 0 이상의 x,y 좌표여야 합니다.')
        }
        return { x: coordinates[0], y: coordinates[1] }
      })
      if (path.length < 2) {
        setFeedback('이동 경로에 출발지와 목적지를 입력하세요.')
        return
      }
      const narrativePosition = snapshot.narrativePositions?.find(position => position.subjectId === snapshot.currentParticipantId)
      const request = {
        characterSheetId: snapshot.currentParticipantId,
        action: 'MOVE' as const, movementPath: path,
        ...(narrativePosition ? { narrativePosition, movementDistance: (path.length - 1) * 5 } : {}),
      }
      const result = api.submitMovement
        ? await api.submitMovement(snapshot.adventureId, request, snapshot.version)
        : await api.submitAction(snapshot.adventureId, request, snapshot.version)
      setFeedback(result.judgment ?? result.status)
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '이동할 수 없습니다.')
    }
  }
  const runFreeForm = async () => {
    if (!api?.submitFreeForm || !freeFormInput.trim()) return
    try {
      const result = await api.submitFreeForm(snapshot.adventureId, snapshot.currentParticipantId, freeFormInput.trim(), snapshot.version)
      setCombatLog(log => [...log, result.judgment ?? result.status])
      if (result.narration) setGmNarration(narration => [...narration, result.narration!])
      setFreeFormInput('')
      setFeedback(result.judgment ?? result.status)
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : '자유 행동을 처리할 수 없습니다.')
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
    {snapshot.pendingReaction && <section aria-labelledby="reaction-title" role="alert">
      <h2 id="reaction-title">Reaction 대기</h2><p>{snapshot.pendingReaction.trigger}</p>
      <button type="button" onClick={() => void resolveReaction('USE')}>Use</button>
      <button type="button" onClick={() => void resolveReaction('PASS')}>Pass</button>
    </section>}
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
      <section aria-labelledby="free-form-title">
        <h3 id="free-form-title">자유 행동 선언</h3>
        <label>정형 목록에 없는 행동
          <textarea aria-label="자유 행동 선언" value={freeFormInput} onChange={event => setFreeFormInput(event.target.value)} />
        </label>
        <button type="button" onClick={() => void runFreeForm()} disabled={!api?.submitFreeForm || !freeFormInput.trim()}>자유 행동 보내기</button>
      </section>
      <button type="button" onClick={() => void endTurn()}>턴 종료</button>
      {feedback && <p role="status">{feedback}</p>}
    </section>}
    <section aria-labelledby="combat-log-title">
      <h2 id="combat-log-title">Combat Log</h2>
      <ol aria-label="Combat Log">{combatLog.map((entry, index) => <li key={`${index}-${entry}`}>{entry}</li>)}</ol>
    </section>
    <section aria-labelledby="gm-narration-title">
      <h2 id="gm-narration-title">GM Narration</h2>
      <ol aria-label="GM Narration">{gmNarration.map((entry, index) => <li key={`${index}-${entry}`}>{entry}</li>)}</ol>
    </section>
  </section>
}
