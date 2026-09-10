import { useEffect, useState } from 'react'
import { Check, LoaderCircle, TriangleAlert } from 'lucide-react'
import { Button } from '../../components/ui/button'
import { Progress } from '../../components/ui/progress'
import { Select } from '../../components/ui/select'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { PlayPreparationView, ScenarioBundleView, ScenarioCompilationView, ScenarioPackageView, SetupApi } from './SetupApi'

const runningStatuses = new Set<ScenarioCompilationView['status']>(['REQUESTED', 'QUEUED', 'RUNNING', 'PROCESSING', 'WAITING_RETRY'])

function progressValue(status: ScenarioCompilationView['status']) {
  if (status === 'REQUESTED' || status === 'QUEUED') return 18
  if (status === 'WAITING_RETRY') return 42
  if (status === 'RUNNING' || status === 'PROCESSING') return 72
  if (status === 'PUBLISHED' || status === 'COMPLETED') return 100
  return 0
}

function storageKey(bundle: ScenarioBundleView) {
  return `dnd-preparation:${bundle.bundleId}:${bundle.currentRevision}`
}

export function PreparationFlow({
  api,
  playerId,
  bundle,
  sessionApi,
  onError,
  onAdventureCreated,
}: {
  api: SetupApi
  playerId: string
  bundle: ScenarioBundleView
  sessionApi?: Pick<AdventureSessionApi, 'create'>
  onError: (message: string) => void
  onAdventureCreated?: (session: AdventureSessionView) => void
}) {
  const [compilation, setCompilation] = useState<ScenarioCompilationView | null>(null)
  const [scenarioPackage, setScenarioPackage] = useState<ScenarioPackageView | null>(null)
  const [playPreparation, setPlayPreparation] = useState<PlayPreparationView | null>(null)
  const [failure, setFailure] = useState('')
  const [partySize, setPartySize] = useState(1)
  const [retryNonce, setRetryNonce] = useState(0)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    if (!api.startScenarioCompilation || !api.getScenarioCompilation || !api.getScenarioPackage) {
      setFailure('이 환경에서는 자동 게임 준비를 사용할 수 없습니다.')
      return
    }

    let active = true
    const run = async () => {
      setFailure('')
      setScenarioPackage(null)
      setPlayPreparation(null)
      const key = storageKey(bundle)
      try {
        let current: ScenarioCompilationView | null = null
        const savedId = retryNonce === 0 ? window.localStorage.getItem(key) : null
        if (savedId) {
          try {
            current = await api.getScenarioCompilation!(savedId)
          } catch {
            window.localStorage.removeItem(key)
          }
        }
        if (!current || current.status === 'FAILED' || current.status === 'BLOCKED') {
          const inputFingerprint = `scenario-bundle:${bundle.bundleId}:revision:${bundle.currentRevision}:setup:${Date.now()}`
          current = await api.startScenarioCompilation!(bundle.bundleId, playerId, inputFingerprint, {})
          window.localStorage.setItem(key, current.compilationId)
        }
        if (!active) return
        setCompilation(current)

        while (active && runningStatuses.has(current.status)) {
          await new Promise(resolve => window.setTimeout(resolve, 650))
          current = await api.getScenarioCompilation!(current.compilationId)
          if (active) setCompilation(current)
        }
        if (!active) return

        if ((current.status === 'PUBLISHED' || current.status === 'COMPLETED') && current.packageId) {
          const packageView = await api.getScenarioPackage!(current.packageId)
          if (!active) return
          setScenarioPackage(packageView)
          setPartySize(currentSize => Math.min(Math.max(1, currentSize), packageView.characterLimit.maximumCharacters))
          return
        }

        const message = current.failureReason || current.diagnostics?.map(item => item.message).join(', ') || '게임 준비를 완료하지 못했습니다.'
        setFailure(message)
        onError(message)
      } catch (error) {
        if (!active) return
        const message = error instanceof Error ? error.message : '게임 준비를 완료하지 못했습니다.'
        setFailure(message)
        onError(message)
      }
    }

    void run()
    return () => { active = false }
  }, [api, bundle, onError, playerId, retryNonce])

  useEffect(() => {
    if (!scenarioPackage || !api.getPlayPreparation) return
    let active = true
    void api.getPlayPreparation(scenarioPackage.packageId)
      .then(preparation => { if (active) setPlayPreparation(preparation) })
      .catch(error => {
        if (!active) return
        const message = error instanceof Error ? error.message : '캐릭터 설정을 확인하지 못했습니다.'
        setFailure(message)
        onError(message)
      })
    return () => { active = false }
  }, [api, onError, scenarioPackage])

  async function createAdventure() {
    if (!scenarioPackage || !playPreparation || !sessionApi?.create) return
    const blueprint = playPreparation.characterCreationBlueprint
    if (blueprint.status !== 'PUBLISHED' || blueprint.revision == null) {
      window.location.hash = `#/scenario-packages/${scenarioPackage.packageId}/character-blueprint`
      return
    }
    setCreating(true)
    try {
      const session = await sessionApi.create({
        scenarioPackageId: scenarioPackage.packageId,
        blueprintId: scenarioPackage.packageId,
        blueprintRevision: blueprint.revision,
        partySize: Math.min(partySize, scenarioPackage.characterLimit.maximumCharacters),
      })
      if (onAdventureCreated) onAdventureCreated(session)
      else window.location.hash = `#/sessions/${session.sessionId}/party`
    } catch (error) {
      const message = error instanceof Error ? error.message : '모험을 만들지 못했습니다.'
      setFailure(message)
      onError(message)
    } finally {
      setCreating(false)
    }
  }

  if (failure) return <div className="setup-preparation-state setup-preparation-error" role="alert">
    <TriangleAlert size={20} aria-hidden="true" />
    <div><strong>준비를 완료하지 못했습니다</strong><p>{failure}</p></div>
    <Button variant="outline" onClick={() => { window.localStorage.removeItem(storageKey(bundle)); setRetryNonce(value => value + 1) }}>다시 시도</Button>
  </div>

  if (!scenarioPackage) {
    const value = compilation ? progressValue(compilation.status) : 8
    return <div className="setup-preparation-state" role="status" aria-live="polite">
      <LoaderCircle className="setup-preparation-spinner" size={20} aria-hidden="true" />
      <div className="setup-preparation-copy"><strong>모험을 준비하고 있습니다</strong><p>자료를 정리하고 게임에서 사용할 내용을 준비하는 중입니다. 내부 처리 단계는 자동으로 진행됩니다.</p><Progress value={value} aria-label="모험 준비 진행률" /></div>
    </div>
  }

  const blueprintReady = playPreparation?.characterCreationBlueprint.status === 'PUBLISHED' && playPreparation.characterCreationBlueprint.revision != null
  const maxParty = scenarioPackage.characterLimit.maximumCharacters
  return <div className="setup-preparation-complete" role="status">
    <div className="setup-preparation-complete-mark"><Check size={18} aria-hidden="true" /></div>
    <div className="setup-preparation-complete-copy">
      <p className="eyebrow">PREPARATION READY</p>
      <h3>모험 자료 준비가 끝났습니다</h3>
      <p>{blueprintReady ? '파티 인원을 정하고 모험을 만들 수 있습니다.' : '마지막으로 캐릭터 생성 설정을 확인하면 모험을 시작할 수 있습니다.'}</p>
      {blueprintReady ? <label className="setup-party-size">파티 인원<Select aria-label="파티 인원" value={partySize} onChange={event => setPartySize(Number(event.currentTarget.value))}>{Array.from({ length: maxParty }, (_, index) => index + 1).map(size => <option key={size} value={size}>{size}명</option>)}</Select></label> : null}
    </div>
    <div className="setup-preparation-actions">
      <Button onClick={() => void createAdventure()} disabled={!playPreparation || creating}>{creating ? '모험 만드는 중…' : blueprintReady ? '모험 만들기' : '캐릭터 설정 계속'}</Button>
    </div>
  </div>
}
