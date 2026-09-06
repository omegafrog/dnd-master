import { useState, type ReactNode } from 'react'
import type { CombatApi, CombatParticipant, CombatSnapshot } from './CombatApi'

export function CombatScreen({ snapshot, api, map, onCommandCommitted }: { snapshot: CombatSnapshot; api?: CombatApi; map?: ReactNode; onCommandCommitted?: () => void }) {
  const [feedback, setFeedback] = useState<string | null>(null)
  const [movementPath, setMovementPath] = useState('')
  const [actionText, setActionText] = useState('')
  const [targetId, setTargetId] = useState('')
  const [combatLog, setCombatLog] = useState<string[]>([])
  const [gmNarration, setGmNarration] = useState<string[]>([])
  const current = snapshot.initiative.find(item => item.participantId === snapshot.currentParticipantId)
  const humanTurn = current?.controller === 'PLAYER'
  const send = async (operation: () => Promise<{ status: string; judgment?: string; narration?: string }>) => {
    try { const result = await operation(); setFeedback(result.judgment ?? result.status); onCommandCommitted?.(); return result }
    catch (error) { setFeedback(error instanceof Error ? error.message : '전투 행동을 처리할 수 없습니다.'); return null }
  }
  const enemies = snapshot.initiative.filter(item => item.controller === 'AI')
  const runAction = () => {
    if (!api) return
    if (!targetId) return setFeedback('공격할 적을 선택하세요.')
    return send(() => api.submitAction(snapshot.adventureId, { characterSheetId: snapshot.currentParticipantId, action: 'attack', targetCharacterSheetId: targetId }, snapshot.version))
  }
  const endTurn = () => api && send(() => api.endTurn(snapshot.adventureId, snapshot.currentParticipantId, snapshot.version))
  const resolveReaction = (choice: 'USE' | 'PASS') => api?.resolveReaction && snapshot.pendingReaction && send(() => api.resolveReaction!(snapshot.adventureId, snapshot.pendingReaction!.reactionId, choice, snapshot.version))
  const retryFailedOperation = () => api?.retry && snapshot.processingFailure && send(() => api.retry!(snapshot.adventureId, snapshot.processingFailure!.operationId, snapshot.version))
  const runMovement = async () => {
    if (!api) return
    try {
      const path = movementPath.split(';').map(value => value.trim()).filter(Boolean).map(value => {
        const coordinates = value.split(',').map(Number)
        if (coordinates.length !== 2 || coordinates.some(coordinate => !Number.isInteger(coordinate) || coordinate < 0)) throw new Error('이동 경로는 0 이상의 x,y 좌표여야 합니다.')
        return { x: coordinates[0], y: coordinates[1] }
      })
      if (path.length < 2) return setFeedback('이동 경로에 출발지와 목적지를 입력하세요.')
      const narrativePosition = snapshot.narrativePositions?.find(position => position.subjectId === snapshot.currentParticipantId)
      const request = { characterSheetId: snapshot.currentParticipantId, action: 'MOVE' as const, movementPath: path, ...(narrativePosition ? { narrativePosition, movementDistance: (path.length - 1) * 5 } : {}) }
      await send(() => api.submitMovement ? api.submitMovement(snapshot.adventureId, request, snapshot.version) : api.submitAction(snapshot.adventureId, request, snapshot.version))
    } catch (error) { setFeedback(error instanceof Error ? error.message : '이동할 수 없습니다.') }
  }
  const submitActionText = async () => {
    if (!api?.submitFreeForm || !actionText.trim()) return
    const result = await send(() => api.submitFreeForm!(snapshot.adventureId, snapshot.currentParticipantId, actionText.trim(), snapshot.version))
    if (!result) return
    setCombatLog(log => [...log, result.judgment ?? result.status])
    if (result.narration) setGmNarration(narration => [...narration, result.narration!])
    setActionText('')
  }
  if (snapshot.status === 'ENDED') return <section className="combat-screen combat-ended" aria-labelledby="combat-title"><header className="combat-command-header"><p className="eyebrow">전투 종료</p><h1 id="combat-title">전투 종료 요약</h1></header>{snapshot.finalSummary && <><p>{snapshot.finalSummary.summary}</p><p>상세 전투 기록은 종료 후 제공되지 않습니다.</p></>}</section>
  return <section className="combat-screen" aria-labelledby="combat-title">
    <header className="combat-command-header"><div><p className="eyebrow">전투 진행</p><h1 id="combat-title">전투 · {snapshot.round}라운드</h1></div><p className="combat-current-turn" role="status"><span>현재 차례</span><strong>{current?.displayName ?? '알 수 없음'}</strong><small>{humanTurn ? '행동을 선택하세요.' : '상대가 행동 중입니다.'}</small></p></header>
    <div className="combat-workspace">
      <aside className="combat-initiative" aria-labelledby="initiative-title"><header><div><p className="eyebrow">차례</p><h2 id="initiative-title">전투 순서</h2></div><span>{snapshot.initiative.length}명</span></header><ol>{snapshot.initiative.map(item => <InitiativeEntry key={item.participantId} item={item} current={item.participantId === snapshot.currentParticipantId} playerNumber={item.controller === 'PLAYER' ? snapshot.initiative.filter(entry => entry.controller === 'PLAYER').indexOf(item) + 1 : 0} />)}</ol></aside>
      <main className="combat-battlefield" aria-label="전장">{map ? <div className="combat-map-frame">{map}</div> : <div className="combat-map-empty"><strong>전장 위치 정보가 없습니다.</strong><span>게임 마스터의 서술을 기준으로 전투를 진행합니다.</span></div>}{snapshot.narrativePositions?.length ? <section className="combat-position-summary" aria-labelledby="narrative-position-title"><h2 id="narrative-position-title">전장 상황</h2>{snapshot.narrativePositions.map(position => <p key={`${position.subjectId}-${position.targetId}`}>거리: {position.rangeBand} · 엄폐: {position.cover}</p>)}</section> : null}</main>
      <aside className="combat-actions" aria-labelledby="actions-title"><header><p className="eyebrow">내 차례</p><h2 id="actions-title">행동 선택</h2></header><dl className="combat-resources"><div><dt>이동</dt><dd>{snapshot.resources.movement}ft</dd></div><div><dt>행동</dt><dd>{snapshot.resources.actionAvailable ? '가능' : '사용'}</dd></div><div><dt>추가 행동</dt><dd>{snapshot.resources.bonusActionAvailable ? '가능' : '사용'}</dd></div><div><dt>반응 행동</dt><dd>{snapshot.resources.reactionAvailable ? '가능' : '사용'}</dd></div></dl>
        {snapshot.pendingReaction && <section className="combat-alert" aria-labelledby="reaction-title" role="alert"><h3 id="reaction-title">반응 행동 선택</h3><p>{snapshot.pendingReaction.trigger}</p><div><button type="button" onClick={() => void resolveReaction('USE')}>사용</button><button type="button" onClick={() => void resolveReaction('PASS')}>넘기기</button></div></section>}
        {snapshot.processingFailure && <section className="combat-alert combat-error" aria-labelledby="processing-failure-title" role="alert"><h3 id="processing-failure-title">전투 처리 실패</h3><p>작업 {snapshot.processingFailure.operationId}</p><p>{snapshot.processingFailure.failure} · 시도 {snapshot.processingFailure.attempts}/3</p>{api?.retry && <button type="button" onClick={() => void retryFailedOperation()}>다시 시도</button>}</section>}
        {humanTurn && <div className="combat-action-controls"><label>공격 대상<select aria-label="공격 대상" value={targetId} onChange={event => setTargetId(event.target.value)}><option value="">적 선택</option>{enemies.map(enemy => <option key={enemy.participantId} value={enemy.participantId}>{enemy.displayName}</option>)}</select></label><button className="combat-primary-action" type="button" onClick={() => void runAction()} disabled={!snapshot.resources.actionAvailable}>공격</button><details><summary>좌표로 이동</summary><label>이동 경로 (x,y; x,y)<input aria-label="이동 경로" value={movementPath} onChange={event => setMovementPath(event.target.value)} placeholder="0,0;1,0" /></label><button type="button" onClick={() => void runMovement()} disabled={!snapshot.resources.movement || !movementPath}>이동 실행</button></details><label className="combat-action-text">원하는 행동을 적으세요<textarea aria-label="행동 선언" value={actionText} onChange={event => setActionText(event.target.value)} placeholder="주변을 살피며 엄폐물 뒤로 이동한다." /></label><button type="button" onClick={() => void submitActionText()} disabled={!api?.submitFreeForm || !actionText.trim()}>행동 보내기</button><button className="combat-end-turn" type="button" onClick={() => void endTurn()}>턴 종료</button></div>}{feedback && <p className="combat-feedback" role="status">{feedback}</p>}</aside>
    </div>
    <section className="combat-history" aria-labelledby="combat-history-title"><header><p className="eyebrow">진행 기록</p><h2 id="combat-history-title">판정과 서술</h2></header><div><section><h3>판정 결과</h3><ol aria-label="판정 결과">{combatLog.length ? combatLog.map((entry, index) => <li key={`${index}-${entry}`}>{entry}</li>) : <li>아직 기록이 없습니다.</li>}</ol></section><section><h3>게임 마스터 서술</h3><ol aria-label="게임 마스터 서술">{gmNarration.length ? gmNarration.map((entry, index) => <li key={`${index}-${entry}`}>{entry}</li>) : <li>행동 결과가 여기에 표시됩니다.</li>}</ol></section></div></section>
  </section>
}

function InitiativeEntry({ item, current, playerNumber }: { item: CombatParticipant; current: boolean; playerNumber: number }) {
  const displayName = item.controller === 'PLAYER' && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(item.displayName) ? `플레이어 ${playerNumber}` : item.displayName
  return <li className={`${item.controller === 'PLAYER' ? 'ally' : 'enemy'}${current ? ' current' : ''}`} aria-current={current ? 'step' : undefined}><span className="combat-token" aria-hidden="true">{item.controller === 'PLAYER' ? '●' : '▲'}</span><span className="combat-participant-name">{displayName}{item.publicCondition && <small>{item.publicCondition === 'healthy' ? '정상' : item.publicCondition === 'enemy' ? '적' : item.publicCondition}</small>}</span><strong>{item.initiative}</strong></li>
}
