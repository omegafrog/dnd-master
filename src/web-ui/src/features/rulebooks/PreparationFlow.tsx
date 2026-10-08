import { useEffect, useRef, useState } from 'react'
import { Check, LoaderCircle, TriangleAlert } from 'lucide-react'
import { Button } from '../../components/ui/button'
import { Progress } from '../../components/ui/progress'
import { Select } from '../../components/ui/select'
import { diagnosticEvent } from '../../shared/developmentDiagnostics'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { PlayPreparationView, ScenarioBundleView, ScenarioCompilationView, ScenarioPackageView, SetupApi } from './SetupApi'

const runningStatuses = new Set<ScenarioCompilationView['status']>(['REQUESTED', 'QUEUED', 'RUNNING', 'PROCESSING', 'WAITING_RETRY'])

function progressFor(status: ScenarioCompilationView['status']) {
  if (status === 'REQUESTED') return 12
  if (status === 'QUEUED') return 18
  if (status === 'WAITING_RETRY') return 38
  if (status === 'RUNNING' || status === 'PROCESSING') return 28
  if (status === 'PUBLISHED' || status === 'COMPLETED') return 88
  return 0
}

function preparationStep(status: ScenarioCompilationView['status'] | null, packageLoaded: boolean) {
  if (packageLoaded) return '플레이 설정과 캐릭터 생성 조건을 확인하고 있습니다.'
  if (status === 'REQUESTED' || status === 'QUEUED') return '준비 작업을 시작하고 순서를 기다리고 있습니다.'
  if (status === 'WAITING_RETRY') return '확인이 필요한 항목을 정리하고 다시 시도하고 있습니다.'
  if (status === 'RUNNING' || status === 'PROCESSING') return '자료를 읽어 모험 장면, 목표, 위협과 전투 정보를 구성하고 있습니다.'
  if (status === 'PUBLISHED' || status === 'COMPLETED') return '완성된 모험 자료를 불러오고 있습니다.'
  return '저장된 준비 작업을 확인하고 있습니다.'
}

// eslint-disable-next-line react-refresh/only-export-components -- Pure progress arithmetic is tested independently.
export function advancePreparationProgress(value: number) {
  return Math.min(88, value + Math.max(0.12, (88 - value) * 0.012))
}

function storageKey(bundle: ScenarioBundleView) {
  return `dnd-preparation:${bundle.bundleId}:${bundle.currentRevision}`
}

function primaryStorybookId(bundle: ScenarioBundleView): string | null {
  const storybooks = bundle.documents.filter(document => document.documentType === 'STORYBOOK')
  return storybooks.find(document => document.role === 'MAIN_SCENARIO')?.knowledgeDocumentId
    ?? (storybooks.length === 1 ? storybooks[0].knowledgeDocumentId : null)
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
  const [progress, setProgress] = useState(6)
  const [scenarioPackage, setScenarioPackage] = useState<ScenarioPackageView | null>(null)
  const [playPreparation, setPlayPreparation] = useState<PlayPreparationView | null>(null)
  const [failure, setFailure] = useState('')
  const [partySize, setPartySize] = useState(1)
  const [retryNonce, setRetryNonce] = useState(0)
  const [creating, setCreating] = useState(false)
  const startRequests = useRef(new Map<string, Promise<ScenarioCompilationView>>())
  const setupAttemptId = useRef(globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`)

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
        diagnosticEvent('preparation', { stage: 'resume_lookup', bundleId: bundle.bundleId, revision: bundle.currentRevision, found: Boolean(savedId) })
        if (savedId) {
          try {
            current = await api.getScenarioCompilation!(savedId)
            diagnosticEvent('preparation', { stage: 'resume_loaded', bundleId: bundle.bundleId, compilationId: current.compilationId, status: current.status })
          } catch {
            window.localStorage.removeItem(key)
            diagnosticEvent('preparation', { stage: 'resume_missing', bundleId: bundle.bundleId })
          }
        }
        if (!current || current.status === 'FAILED' || current.status === 'BLOCKED') {
          const requestKey = `${bundle.bundleId}:${bundle.currentRevision}:retry:${retryNonce}`
          const inputFingerprint = `scenario-bundle:${bundle.bundleId}:revision:${bundle.currentRevision}:setup:${setupAttemptId.current}:retry:${retryNonce}`
          let request = startRequests.current.get(requestKey)
          if (!request) {
            diagnosticEvent('preparation', { stage: 'compilation_requested', bundleId: bundle.bundleId, retry: retryNonce })
            request = api.startScenarioCompilation!(bundle.bundleId, playerId, inputFingerprint, {
              primaryStorybookId: primaryStorybookId(bundle),
            })
            startRequests.current.set(requestKey, request)
          }
          current = await request
          window.localStorage.setItem(key, current.compilationId)
        }
        if (!active) return
        setCompilation(current)
        setProgress(value => Math.max(value, progressFor(current!.status)))
        let lastLoggedStatus = current.status
        diagnosticEvent('preparation', { stage: 'compilation_status', bundleId: bundle.bundleId, compilationId: current.compilationId, status: current.status })

        while (active && runningStatuses.has(current.status)) {
          await new Promise(resolve => window.setTimeout(resolve, 650))
          current = await api.getScenarioCompilation!(current.compilationId)
          if (active) {
            setCompilation(current)
            setProgress(value => Math.max(value, progressFor(current!.status)))
            if (current.status !== lastLoggedStatus) {
              lastLoggedStatus = current.status
              diagnosticEvent('preparation', { stage: 'compilation_status', bundleId: bundle.bundleId, compilationId: current.compilationId, status: current.status })
            }
          }
        }
        if (!active) return

        if ((current.status === 'PUBLISHED' || current.status === 'COMPLETED') && current.packageId) {
          diagnosticEvent('preparation', { stage: 'package_loading', bundleId: bundle.bundleId, compilationId: current.compilationId, packageId: current.packageId })
          const packageView = await api.getScenarioPackage!(current.packageId)
          if (!active) return
          setScenarioPackage(packageView)
          setProgress(92)
          diagnosticEvent('preparation', { stage: 'package_loaded', bundleId: bundle.bundleId, packageId: packageView.packageId, units: packageView.units.length })
          setPartySize(currentSize => Math.min(Math.max(1, currentSize), packageView.characterLimit.maximumCharacters))
          return
        }

        const message = current.failureReason || current.diagnostics?.map(item => item.message).join(', ') || '게임 준비를 완료하지 못했습니다.'
        diagnosticEvent('preparation', { stage: 'compilation_failed', bundleId: bundle.bundleId, compilationId: current.compilationId, status: current.status })
        setFailure(message)
        onError(message)
      } catch (error) {
        if (!active) return
        const message = error instanceof Error ? error.message : '게임 준비를 완료하지 못했습니다.'
        diagnosticEvent('preparation', { stage: 'request_failed', bundleId: bundle.bundleId, failureClass: error instanceof Error ? error.constructor.name : 'UnknownError' })
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
    diagnosticEvent('preparation', { stage: 'character_setup_loading', bundleId: bundle.bundleId, packageId: scenarioPackage.packageId })
    void api.getPlayPreparation(scenarioPackage.packageId)
      .then(preparation => {
        if (!active) return
        setPlayPreparation(preparation)
        setProgress(100)
        diagnosticEvent('preparation', { stage: 'ready', bundleId: bundle.bundleId, packageId: scenarioPackage.packageId, blueprintStatus: preparation.characterCreationBlueprint.status ?? null })
      })
      .catch(error => {
        if (!active) return
        const message = error instanceof Error ? error.message : '캐릭터 설정을 확인하지 못했습니다.'
        diagnosticEvent('preparation', { stage: 'character_setup_failed', bundleId: bundle.bundleId, packageId: scenarioPackage.packageId, failureClass: error instanceof Error ? error.constructor.name : 'UnknownError' })
        setFailure(message)
        onError(message)
      })
    return () => { active = false }
  }, [api, bundle.bundleId, onError, scenarioPackage])

  const compilationId = compilation?.compilationId
  const compilationStatus = compilation?.status
  useEffect(() => {
    if (!compilationId || !compilationStatus || !runningStatuses.has(compilationStatus)) return
    const timer = window.setInterval(() => setProgress(advancePreparationProgress), 500)
    const startedAt = Date.now()
    const logTimer = window.setInterval(() => diagnosticEvent('preparation', {
      stage: 'compilation_waiting', bundleId: bundle.bundleId, compilationId,
      status: compilationStatus, elapsedMs: Date.now() - startedAt,
    }), 15_000)
    return () => { window.clearInterval(timer); window.clearInterval(logTimer) }
  }, [bundle.bundleId, compilationId, compilationStatus])

  async function createAdventure() {
    if (!scenarioPackage || !playPreparation || !sessionApi?.create) return
    const blueprint = playPreparation.characterCreationBlueprint
    if (blueprint.status !== 'PUBLISHED' || blueprint.revision == null) {
      window.location.hash = `#/scenario-packages/${scenarioPackage.packageId}/character-blueprint`
      return
    }
    setCreating(true)
    diagnosticEvent('preparation', { stage: 'adventure_creation_requested', bundleId: bundle.bundleId, packageId: scenarioPackage.packageId, partySize })
    try {
      const session = await sessionApi.create({
        scenarioPackageId: scenarioPackage.packageId,
        blueprintId: scenarioPackage.packageId,
        blueprintRevision: blueprint.revision,
        partySize: Math.min(partySize, scenarioPackage.characterLimit.maximumCharacters),
      })
      diagnosticEvent('preparation', { stage: 'adventure_created', bundleId: bundle.bundleId, packageId: scenarioPackage.packageId, sessionId: session.sessionId })
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

  if (!scenarioPackage || !playPreparation) {
    const value = progress
    return <div className="setup-preparation-state" role="status" aria-live="polite">
      <LoaderCircle className="setup-preparation-spinner" size={20} aria-hidden="true" />
      <div className="setup-preparation-copy"><strong>모험을 준비하고 있습니다</strong><p>{preparationStep(compilation?.status ?? null, Boolean(scenarioPackage))}</p><Progress value={value} aria-label="모험 준비 진행률" /></div>
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
