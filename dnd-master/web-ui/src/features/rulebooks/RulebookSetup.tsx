import { type FormEvent, useState } from 'react'
import { RuleSetSetup } from '../rule-sets/RuleSetSetup'
import { ScenarioSetup } from '../scenarios/ScenarioSetup'
import type { RulebookView, SetupApi } from './SetupApi'

const statusText: Record<RulebookView['status'], string> = {
  EXTRACTING: '텍스트 추출 중', PARTIAL: '부분 추출 확인 필요', INDEXING: '색인 생성 중',
  READY: '사용 준비 완료', FAILED: '처리 실패',
}

export function RulebookSetup({ api }: { api: SetupApi }) {
  const [rulebooks, setRulebooks] = useState<RulebookView[]>([])
  const [message, setMessage] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

  function replace(book: RulebookView) {
    setRulebooks(current => [...current.filter(item => item.id !== book.id), book])
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedFile) return
    setMessage('')
    try { replace(await api.uploadRulebook(selectedFile)) }
    catch (error) { setMessage(error instanceof Error ? error.message : '룰북을 업로드하지 못했습니다.') }
  }

  async function update(book: RulebookView, confirm: boolean) {
    setMessage('')
    try {
      replace(confirm ? await api.confirmPartialExtraction(book.id) : await api.refreshRulebook(book.id))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '룰북 상태를 확인하지 못했습니다.')
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
          <button type="submit">룰북 업로드</button>
        </form>
        <ul aria-label="룰북 처리 상태">
          {rulebooks.map(book => (
            <li key={book.id}>
              <strong>{book.name}</strong>: {statusText[book.status]}
              {book.warnings.length > 0 && <ul>{book.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}
              {book.status === 'PARTIAL' && <button onClick={() => void update(book, true)}>부분 추출 사용</button>}
              {(book.status === 'EXTRACTING' || book.status === 'INDEXING') &&
                <button onClick={() => void update(book, false)}>상태 새로고침</button>}
            </li>
          ))}
        </ul>
      </section>
      <ScenarioSetup api={api} onError={setMessage} />
      <RuleSetSetup api={api} rulebooks={rulebooks} onError={setMessage} />
    </main>
  )
}
