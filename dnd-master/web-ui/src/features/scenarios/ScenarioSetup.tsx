import { type FormEvent, useState } from 'react'
import type { SetupApi } from '../rulebooks/SetupApi'

export function ScenarioSetup({ api, onError }: { api: SetupApi; onError: (message: string) => void }) {
  const [scenarioName, setScenarioName] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedFile) return
    setUploading(true)
    try {
      const scenario = await api.uploadScenario(selectedFile)
      setScenarioName(scenario.name)
    } catch (error) {
      onError(error instanceof Error ? error.message : '시나리오를 등록하지 못했습니다.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <section aria-labelledby="scenario-heading">
      <h2 id="scenario-heading">모험 시나리오</h2>
      <form onSubmit={upload}>
        <label>시나리오 파일<input name="scenario" type="file"
          onChange={event => setSelectedFile(event.currentTarget.files?.[0] ?? null)} /></label>
        <button type="submit" disabled={uploading}>{uploading ? '등록 중…' : '시나리오 등록'}</button>
      </form>
      {scenarioName && <p>등록 완료: {scenarioName}</p>}
    </section>
  )
}
