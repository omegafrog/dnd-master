import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PackageBlueprintReviewPage } from './PackageBlueprintReviewPage'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { PlayPreparationView, StorybookProposalView } from '../rulebooks/SetupApi'

function proposal(overrides: Partial<StorybookProposalView> = {}): StorybookProposalView {
  return {
    proposalId: 'proposal-internal-1',
    key: 'alignment',
    label: '스토리 속 성향',
    description: '이야기 속 인물은 질서 선 성향으로 묘사됩니다.',
    sourceDocument: { knowledgeDocumentId: 'document-internal-1', originalFilename: '모험의 기록.pdf', extractionVersion: 3 },
    sourceQuote: '그는 마을을 지키는 질서 선의 수호자였다.',
    evidence: [{ locator: '4쪽', excerpt: '그는 마을을 지키는 질서 선의 수호자였다.' }],
    decisionState: 'UNDECIDED',
    readinessState: 'READY',
    ...overrides,
  }
}

function preparation(overrides: Partial<PlayPreparationView['characterCreationBlueprint']> = {}): PlayPreparationView {
  return {
    scenarioPackageId: 'package-internal-1',
    bundleId: 'bundle-internal-1',
    bundleRevision: 4,
    status: 'READY',
    blockers: [],
    characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' },
    characterCreationBlueprint: {
      available: true,
      summary: null,
      rulebookDocumentCount: 1,
      storybookDocumentCount: 1,
      diagnostics: [],
      revision: 8,
      status: 'NEEDS_REVIEW',
      roots: [],
      baseSchema: {
        edition: 'DND 5판 2014',
        fields: [{
          key: 'race',
          label: '종족',
          options: ['엘프', '인간'],
          required: true,
          sourceType: 'RULEBOOK',
          inputStatus: 'EXTRACTED',
          inputMode: 'SINGLE_SELECT',
          suggestions: [],
          sourceQuote: '종족을 선택합니다.',
          evidence: [],
          optionDetails: [],
          diagnostics: [],
        }],
      },
      storybookProposals: [proposal()],
      storybookExtractionState: 'PROPOSALS_AVAILABLE',
      ...overrides,
    },
  }
}

function renderReview(
  getPlayPreparation: () => Promise<PlayPreparationView>,
  sessionApi: Pick<AdventureSessionApi, 'create'> = { create: vi.fn().mockResolvedValue({ sessionId: 'session-default' }) },
  onSessionCreated: (sessionId: string) => void = vi.fn(),
) {
  return render(
    <PackageBlueprintReviewPage
      packageId="package-1"
      setupApi={{ getPlayPreparation }}
      sessionApi={sessionApi}
      onSessionCreated={onSessionCreated}
    />,
  )
}

describe('PackageBlueprintReviewPage', () => {
  it('shows a loading state while the review is being requested', () => {
    renderReview(() => new Promise(() => undefined))

    expect(screen.getByRole('status')).toHaveTextContent('캐릭터 생성 설정을 불러오는 중')
  })

  it('separates a read-only rulebook schema from storybook evidence cards', async () => {
    renderReview(async () => preparation())

    const baseSchema = await screen.findByRole('region', { name: '룰북 기본 스키마' })
    expect(within(baseSchema).getByText('DND 5판 2014')).toBeInTheDocument()
    expect(within(baseSchema).getByText('종족')).toBeInTheDocument()
    expect(within(baseSchema).queryByRole('textbox')).not.toBeInTheDocument()
    expect(within(baseSchema).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(baseSchema).queryByRole('button')).not.toBeInTheDocument()

    expect(screen.getByRole('heading', { name: '스토리북 제안' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '스토리 속 성향' })).toBeInTheDocument()
    expect(screen.getAllByText('모험의 기록.pdf')).not.toHaveLength(0)
    expect(screen.getAllByText('그는 마을을 지키는 질서 선의 수호자였다.')).not.toHaveLength(0)
    expect(screen.getByText('검토 전')).toBeInTheDocument()
    expect(screen.queryByText('proposal-internal-1')).not.toBeInTheDocument()
    expect(screen.queryByText('UNDECIDED')).not.toBeInTheDocument()
  })

  it('shows a successful empty state when storybook analysis finds no proposals', async () => {
    renderReview(async () => preparation({ storybookProposals: [], storybookExtractionState: 'NO_PROPOSALS' }))

    expect(await screen.findByRole('heading', { name: '스토리북에서 추가할 내용이 없습니다' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('스토리북에서 추가할 내용이 없습니다')
    expect(screen.getByText('분석이 완료되었습니다.')).toBeInTheDocument()
  })

  it('shows an insufficient-evidence state instead of treating unsupported extraction as success', async () => {
    renderReview(async () => preparation({ storybookProposals: [], storybookExtractionState: 'INSUFFICIENT_EVIDENCE' }))

    expect(await screen.findByRole('heading', { name: '스토리북 근거가 충분하지 않습니다' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('근거를 확인할 수 있는 제안이 없습니다')
  })

  it('shows partial extraction awaiting confirmation instead of a successful empty state', async () => {
    renderReview(async () => preparation({ storybookProposals: [], storybookExtractionState: 'EXTRACTION_PARTIAL_AWAITING_CONFIRMATION' }))

    expect(await screen.findByRole('heading', { name: '스토리북 분석이 일부 완료되어 확인이 필요합니다' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('일부 원문만 분석되었습니다')
    expect(screen.queryByText('분석이 완료되었습니다.')).not.toBeInTheDocument()
  })

  it('shows the storybook failure state without rendering proposal cards', async () => {
    renderReview(async () => preparation({
      diagnostics: ['스토리북 문서의 본문을 읽지 못했습니다.'],
      storybookProposals: [],
      storybookExtractionState: 'EXTRACTION_FAILED',
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent('스토리북 분석에 실패했습니다')
    expect(screen.getByText('스토리북 문서의 본문을 읽지 못했습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '스토리 속 성향' })).not.toBeInTheDocument()
  })

  it('marks proposals without evidence as needing confirmation', async () => {
    renderReview(async () => preparation({
      storybookExtractionState: 'INSUFFICIENT_EVIDENCE',
      storybookProposals: [proposal({
        sourceQuote: '',
        evidence: [],
        readinessState: 'INSUFFICIENT_EVIDENCE',
      })],
    }))

    expect(await screen.findByText('근거 확인 필요')).toBeInTheDocument()
    expect(screen.getByText('사용할 수 있는 원문 근거가 아직 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '사용하기' })).not.toBeInTheDocument()
  })

  it('disables use but still allows exclusion when the proposal has no evidence', async () => {
    render(
      <PackageBlueprintReviewPage
        packageId="package-1"
        setupApi={{
          getPlayPreparation: async () => preparation({
            storybookExtractionState: 'INSUFFICIENT_EVIDENCE',
            storybookProposals: [proposal({ sourceQuote: '', evidence: [], readinessState: 'INSUFFICIENT_EVIDENCE' })],
          }),
          useStorybookProposal: vi.fn(),
          excludeStorybookProposal: vi.fn(),
        }}
        sessionApi={{ create: vi.fn() }}
        onSessionCreated={vi.fn()}
      />,
    )

    const useButton = await screen.findByRole('button', { name: '사용하기' })
    expect(useButton).toBeDisabled()
    expect(screen.getByRole('button', { name: '제외하기' })).toBeEnabled()
  })

  it('uses proposal identity for unique card ids and deterministic evidence keys', async () => {
    renderReview(async () => preparation({
      storybookProposals: [
        proposal(),
        proposal({
          proposalId: 'proposal-internal-2',
          evidence: [
            { locator: '4쪽', excerpt: '같은 근거' },
            { locator: '4쪽', excerpt: '같은 근거' },
          ],
        }),
      ],
    }))

    const headings = await screen.findAllByRole('heading', { name: '스토리 속 성향' })
    expect(new Set(headings.map(heading => heading.id))).toEqual(new Set(['proposal-proposal-internal-1', 'proposal-proposal-internal-2']))
    const cards = screen.getAllByRole('article')
    expect(within(cards[1]).getAllByText('같은 근거')).toHaveLength(2)
  })

  it('offers character creation for an already-published blueprint', async () => {
    const create = vi.fn().mockResolvedValue({ sessionId: 'session-created-1' })
    const onSessionCreated = vi.fn()
    renderReview(async () => preparation({ status: 'PUBLISHED' }), { create }, onSessionCreated)

    const button = await screen.findByRole('button', { name: '캐릭터 생성 시작' })
    await userEvent.click(button)

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      scenarioPackageId: 'package-1',
      blueprintId: 'package-1',
      blueprintRevision: 8,
    }))
    await waitFor(() => expect(onSessionCreated).toHaveBeenCalledWith('session-created-1'))
  })

  it('persists a proposal decision and refetches the review at the returned revision', async () => {
    const getPlayPreparation = vi.fn()
      .mockResolvedValueOnce(preparation())
      .mockResolvedValueOnce(preparation({
        revision: 9,
        storybookProposals: [proposal({ decisionState: 'APPLIED' })],
      }))
    const useStorybookProposal = vi.fn().mockResolvedValue(undefined)
    render(
      <PackageBlueprintReviewPage
        packageId="package-1"
        setupApi={{ getPlayPreparation, useStorybookProposal }}
        sessionApi={{ create: vi.fn() }}
        onSessionCreated={vi.fn()}
      />,
    )

    await userEvent.click(await screen.findByRole('button', { name: '사용하기' }))

    await waitFor(() => expect(useStorybookProposal).toHaveBeenCalledWith('package-1', 'proposal-internal-1', 8))
    await waitFor(() => expect(getPlayPreparation).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('사용 예정')).toBeInTheDocument()
  })
})
