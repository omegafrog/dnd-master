import { type FormEvent, useCallback, useEffect, useState } from 'react'
import type { IdentitySession } from '../auth/IdentityApi'
import type { PreprocessingPageView } from '../rulebooks/SetupApi'

type Catalog = { catalogRevisionId: string; edition: 'DND_5E_2014' | 'DND_5E_2024'; displayName: string; rulebookId?: string | null; revisionNumber: number; status: string; published: boolean }
type Review = { rulebookId: string; status: string; contentHash: string; candidateExtractionVersion: string; preprocessingPages: PreprocessingPageView[] }
const headers = (session: IdentitySession) => ({ Authorization: `Bearer ${session.accessToken}` })

export function BackofficePage({ session }: { session: IdentitySession }) {
  const [catalog, setCatalog] = useState<Catalog[]>([])
  const [message, setMessage] = useState('')
  const [review, setReview] = useState<Review | null>(null)
  const [revisionId, setRevisionId] = useState<string | null>(null)
  const [pageNumber, setPageNumber] = useState<number | null>(null)
  const [selections, setSelections] = useState<Record<string, number>>({})
  const [sourceUrl, setSourceUrl] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const refresh = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/backoffice/rulebook-catalog', { headers: headers(session) })
      if (!response.ok) throw new Error(await response.text())
      setCatalog(await response.json())
    } catch { setMessage('룰북 목록을 불러오지 못했습니다.') }
  }, [session])
  useEffect(() => { void refresh() }, [refresh])
  useEffect(() => () => { if (sourceUrl) URL.revokeObjectURL?.(sourceUrl) }, [sourceUrl])

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      const response = await fetch(`/api/v1/backoffice/rulebook-catalog?edition=${form.get('edition')}`, { method: 'POST', headers: headers(session), body: form })
      if (!response.ok) throw new Error(await response.text())
      setMessage('룰북 처리를 시작했습니다. 검증 완료 후 공개할 수 있습니다.')
      await refresh()
    } catch (error) { setMessage(error instanceof Error ? error.message : '업로드 실패') }
  }
  async function publish(id: string) {
    const response = await fetch(`/api/v1/backoffice/rulebook-catalog/${id}/publish`, { method: 'POST', headers: headers(session) })
    setMessage(response.ok ? '룰북을 공개했습니다.' : await response.text())
    await refresh()
  }
  async function openReview(id: string) {
    const response = await fetch(`/api/v1/backoffice/rulebook-catalog/${id}/review`, { headers: headers(session) })
    if (!response.ok) { setMessage(await response.text()); return }
    setReview(await response.json())
    setRevisionId(id); setPageNumber(null); setSelections({}); setConfirmed(false); setSourceUrl(null)
  }
  async function openSource() {
    if (!revisionId) return
    const response = await fetch(`/api/v1/backoffice/rulebook-catalog/${revisionId}/source`, { headers: headers(session) })
    if (!response.ok) { setMessage(await response.text()); return }
    setSourceUrl(URL.createObjectURL(await response.blob()))
  }
  async function retryPage() {
    if (!review || !revisionId || pageNumber == null) return
    const response = await fetch(`/api/v1/backoffice/rulebook-catalog/${revisionId}/retry-pages`, {
      method: 'POST', headers: { ...headers(session), 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestId: crypto.randomUUID(), candidateExtractionVersion: review.candidateExtractionVersion,
        pages: [pageNumber], layoutSelections: { [pageNumber]: selections }, confirmedAgainstSource: confirmed }),
    })
    if (!response.ok) { setMessage(await response.text()); return }
    const updated: Review = await response.json()
    setReview(updated); setSelections({}); setConfirmed(false)
    setMessage(updated.status === 'NEEDS_REVIEW' ? '검토가 더 필요합니다. 남은 오류를 확인하세요.' : '선택한 페이지를 다시 검증했습니다.')
    await refresh()
  }
  const page = review?.preprocessingPages.find(item => item.pageNumber === pageNumber)
  const regions = page?.layoutReview?.regions ?? []
  const canConfirm = !!sourceUrl && confirmed && regions.length > 0 && regions.every(region => selections[region.regionId] !== undefined)

  return <section className="setup-page"><div className="page-heading"><div><p className="eyebrow">룰북 관리</p><h1>룰북 관리</h1></div></div><p role="status">{message}</p>
    <section className="setup-panel"><h2>공용 룰북 목록</h2><form onSubmit={upload}><select name="edition" defaultValue="DND_5E_2014"><option value="DND_5E_2014">D&D 5e (2014)</option><option value="DND_5E_2024">D&D 5.5e (2024)</option></select><input name="file" type="file" accept=".pdf" required /><button>PDF 업로드·색인</button></form><ul>{catalog.map(item => <li key={item.catalogRevisionId}>{item.displayName} {item.revisionNumber}판 · {item.status === 'READY' ? '검증 완료' : item.status === 'QUEUED' ? '처리 대기' : item.status} {item.published ? '· 공개됨' : item.status === 'READY' && item.rulebookId ? <button onClick={() => void publish(item.catalogRevisionId)}>공개</button> : null} {item.rulebookId && <button type="button" onClick={() => void openReview(item.catalogRevisionId)}>페이지 검토</button>}</li>)}</ul></section>
    {review && <section className="setup-panel" aria-label="룰북 페이지 검토"><h2>페이지 검토</h2><p>원본 해시: {review.contentHash} · 추출 후보 버전: {review.candidateExtractionVersion}</p><button type="button" onClick={() => void openSource()}>원본 PDF 열기</button>
      <div>{review.preprocessingPages.filter(item => item.status === 'NEEDS_REVIEW').map(item => <button key={item.pageNumber} type="button" onClick={() => { setPageNumber(item.pageNumber); setSelections({}); setConfirmed(false) }}>{item.pageNumber}쪽</button>)}</div>
      {page && <div><h3>{page.pageNumber}쪽</h3><p>{page.findings.join(', ')}</p>{sourceUrl && <iframe title={`${page.pageNumber}쪽 원본 PDF`} src={`${sourceUrl}#page=${page.pageNumber}`} style={{ width: '100%', height: 520 }} />}
        {regions.map(region => <label key={region.regionId}>영역 {region.regionId}의 읽기 순서 후보 <select value={selections[region.regionId] ?? ''} onChange={event => setSelections(current => ({ ...current, [region.regionId]: Number(event.target.value) }))}><option value="" disabled>후보 선택</option>{region.candidates.map(candidate => <option key={candidate.candidateIndex} value={candidate.candidateIndex}>{candidate.candidateIndex + 1}번 · {candidate.columnCount}열 · 판정 점수 {candidate.score.toFixed(3)}</option>)}</select></label>)}
        <details><summary>추출된 글자와 위치 확인</summary><ul>{page.layoutReview?.blocks.map(block => <li key={block.blockId}>{block.text} · {block.bbox.join(', ')}</li>)}</ul></details>
        <label><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} />원본 PDF와 선택한 후보를 대조했습니다.</label>
        <button type="button" disabled={!canConfirm} onClick={() => void retryPage()}>선택한 페이지 재검증</button>
      </div>}
    </section>}
  </section>
}
