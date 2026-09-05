import type { CombatSnapshot } from './CombatApi'

export function CombatScreen({ snapshot }: { snapshot: CombatSnapshot }) {
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
  </section>
}
