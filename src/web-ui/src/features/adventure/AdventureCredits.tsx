import type { AdventureConversationEntry } from './AdventureApi'
import type { RuntimePartyCharacter } from './SessionRuntime'

type AdventureCreditBeat = { choice: string; outcome: string }

export function AdventureCredits({ entries, party, adventureId }: {
  entries: AdventureConversationEntry[]
  party: RuntimePartyCharacter[]
  adventureId: string
}) {
  const opening = entries.find(entry => isGameMaster(entry) && isStoryText(entry.content))?.content.trim() ?? ''
  const beats = summarizeBeats(entries)
  const partyNames = [...new Set(party.map(member => member.name.trim()).filter(name => name && name !== '캐릭터'))]

  return <main className="adventure-credits" aria-labelledby="adventure-credits-title">
    <a className="adventure-credits-back" href={`#/adventures/${encodeURIComponent(adventureId)}?tab=sessions`}>모험 기록으로 돌아가기</a>
    <header className="adventure-credits-heading">
      <p className="eyebrow">모험 완료</p>
      <h1 id="adventure-credits-title">모험의 막이 내렸습니다</h1>
      <span className="adventure-credits-rule" aria-hidden="true" />
    </header>

    <section className="adventure-credits-story" aria-labelledby="adventure-credits-story-title">
      <p className="eyebrow">마지막 장면</p>
      <h2 id="adventure-credits-story-title">이번 모험의 기록</h2>
      {opening && <p className="adventure-credits-opening">{opening}</p>}
      {beats.length > 0 ? <ol aria-label="모험 여정 요약">{beats.map((beat, index) => <li key={`${index}-${beat.choice}`}>
        <span className="adventure-credits-number">{String(index + 1).padStart(2, '0')}</span>
        <div><p>{beat.outcome}</p><p className="adventure-credits-choice"><span>모험가의 선택</span>{beat.choice}</p></div>
      </li>)}</ol> : <p>저장된 대화 기록에서 여정의 장면을 찾지 못했습니다.</p>}
    </section>

    <footer className="adventure-credits-cast" aria-labelledby="adventure-credits-cast-title">
      <p className="eyebrow">함께한 이들</p>
      <h2 id="adventure-credits-cast-title">모험의 출연진</h2>
      <ul>{partyNames.length > 0 ? partyNames.map(name => <li key={name}>{name}</li>) : <li>모험가 일행</li>}</ul>
      <p className="adventure-credits-role"><span>게임 마스터</span><strong>AI 게임 마스터</strong></p>
      <p className="adventure-credits-end">끝</p>
    </footer>
  </main>
}

function summarizeBeats(entries: AdventureConversationEntry[]): AdventureCreditBeat[] {
  const ordered = [...entries].sort((left, right) => left.sequence - right.sequence)
  const beats: AdventureCreditBeat[] = []
  for (let index = 0; index < ordered.length; index += 1) {
    const action = ordered[index]
    if (!isPlayer(action) || !action.content.trim()) continue
    const outcomes: string[] = []
    for (let next = index + 1; next < ordered.length && !isPlayer(ordered[next]); next += 1) {
      const entry = ordered[next]
      if (isGameMaster(entry) && isStoryText(entry.content)) outcomes.push(entry.content.trim())
    }
    if (outcomes.length === 0) continue
    const outcome = outcomes.reduce((longest, candidate) => candidate.length > longest.length ? candidate : longest)
    beats.push({ choice: summarizeChoice(action.content), outcome })
  }
  return beats
}

function summarizeChoice(value: string) {
  const choice = value.trim()
  if (/^attack$/i.test(choice)) return '거대 쥐를 향해 공격했습니다.'
  return choice
}

function isPlayer(entry: AdventureConversationEntry) {
  return entry.speaker === 'PLAYER'
}

function isGameMaster(entry: AdventureConversationEntry) {
  return entry.speaker === 'AI_GAME_MASTER'
}

function isStoryText(value: string) {
  const content = value.trim()
  return content.length > 0 && !/^(확정 전투 결과:|판정 결과:)/.test(content)
}
