import { type FormEvent, useState } from 'react'
import type { RulebookView, SetupApi } from './SetupApi'
import { ScenarioSetup } from '../scenarios/ScenarioSetup'

const statusText: Record<RulebookView['status'], string> = {
  PENDING: '처리 중', INDEXED: '사용 준비 완료', FAILED: '처리 실패',
}

export function RulebookSetup({ api, playerId }: { api: SetupApi; playerId: string }) {
  const [rulebooks, setRulebooks] = useState<RulebookView[]>([])
  const [message, setMessage] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [ruleSetMessage, setRuleSetMessage] = useState('')

  function replace(book: RulebookView) {
    setRulebooks(current => [...current.filter(item => item.rulebookId !== book.rulebookId), book])
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedFile) return
    setMessage('')
    setUploading(true)
    try {
      replace(await api.uploadRulebook(selectedFile, playerId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '룰북을 업로드하지 못했습니다.')
    } finally {
      setUploading(false)
    }
  }

  async function refresh(book: RulebookView) {
    setMessage('')
    try {
      replace(await api.getRulebookStatus(book.rulebookId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '룰북 상태를 확인하지 못했습니다.')
    }
  }

  function toggleSelected(rulebookId: string) {
    setSelectedIds(prev => {
      const next = new Set(prev)
      if (next.has(rulebookId)) next.delete(rulebookId)
      else next.add(rulebookId)
      return next
    })
  }

  async function saveRuleSet() {
    setRuleSetMessage('')
    try {
      await api.saveRuleSet([...selectedIds])
      setRuleSetMessage('룰 세트가 저장되었습니다.')
    } catch (error) {
      setRuleSetMessage(error instanceof Error ? error.message : '룰 세트를 저장하지 못했습니다.')
    }
  }

  return (
    <main>
      <h1>자료와 모험 설정</h1>
      <p role="status" aria-live="polite">{message}</p>
      <section aria-labelledby="rulebook-heading">
        <h2 id="rulebook-heading">룰북</h2>
        <form onSubmit={upload}>
          <label>룰북 파일<input name="rulebook" type="file" accept=".pdf,.docx,.txt"
            onChange={event => setSelectedFile(event.currentTarget.files?.[0] ?? null)} /></label>
          <button type="submit" disabled={uploading}>{uploading ? '업로드 중…' : '룰북 업로드'}</button>
        </form>
        <ul aria-label="룰북 처리 상태">
          {rulebooks.map(book => (
            <li key={book.rulebookId}>
              <label>
                <input type="checkbox"
                  checked={selectedIds.has(book.rulebookId)}
                  onChange={() => toggleSelected(book.rulebookId)}
                  disabled={book.status !== 'INDEXED'} />
                {book.rulebookId}
              </label>: {statusText[book.status]}
              {(book.status === 'PENDING') &&
                <button onClick={() => void refresh(book)}>상태 새로고침</button>}
            </li>
          ))}
        </ul>
        {rulebooks.some(b => b.status === 'INDEXED') && (
          <button onClick={() => void saveRuleSet()}>룰 세트 저장</button>
        )}
        {ruleSetMessage && <p role="status">{ruleSetMessage}</p>}
      </section>
      <ScenarioSetup api={api} onError={setMessage} />
    </main>
  )
}
