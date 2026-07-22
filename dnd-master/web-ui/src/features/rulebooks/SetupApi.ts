export type RulebookStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'INDEXED'
  | 'FAILED'
  | 'UPLOADED'
  | 'EXTRACTED'
  | 'PARTIAL_AWAITING_CONFIRMATION'
  | 'PARTIAL_CONFIRMED'
  | 'REJECTED'

export type RulebookView = {
  rulebookId: string
  status: RulebookStatus
}

export type DocumentType = 'RULEBOOK' | 'STORYBOOK'

export type BatchRulebookStatus = 'ACCEPTED' | 'VALIDATION_FAILED'

export type BatchRulebookView = {
  knowledgeDocumentId: string | null
  documentType: DocumentType
  originalFilename: string
  status: BatchRulebookStatus
  failureReason?: string | null
}

export type KnowledgeDocumentStatus = 'UPLOADED' | 'QUEUED' | 'PROCESSING' | 'FAILED' | 'EXTRACTED' | 'INDEXED' | 'PARTIAL_AWAITING_CONFIRMATION' | 'PARTIAL_CONFIRMED' | 'REJECTED'

export type KnowledgeDocumentView = {
  knowledgeDocumentId: string
  documentType: DocumentType
  originalFilename: string
  status: KnowledgeDocumentStatus
  failureReason?: string | null
}

export type RulebookUploadDraft = {
  file: File
  documentType: DocumentType
  idempotencyKey: string
}

export interface SetupApi {
  uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string): Promise<BatchRulebookView[]>
  getRulebookStatus(rulebookId: string): Promise<RulebookView>
  retryKnowledgeDocument(knowledgeDocumentId: string): Promise<RulebookView>
  uploadScenario(file: File): Promise<{ id: string; name: string }>
  saveRuleSet(rulebookIds: string[]): Promise<void>
  listKnowledgeDocuments(ownerId: string): Promise<KnowledgeDocumentView[]>
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 400) throw new Error('지원하지 않거나 손상된 파일입니다.')
  if (response.status === 409) throw new Error('재처리할 수 없는 상태입니다.')
  if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export class HttpSetupApi implements SetupApi {
  private readonly getToken: () => string

  constructor(getToken: () => string) {
    this.getToken = getToken
  }

  private authHeaders(): Record<string, string> {
    return { Authorization: `Bearer ${this.getToken()}` }
  }

  uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string) {
    const body = new FormData()
    body.append('documents', new Blob([JSON.stringify(documents.map(document => ({
      idempotencyKey: document.idempotencyKey,
      documentType: document.documentType,
      originalFilename: document.file.name,
    })))], { type: 'application/json' }))
    documents.forEach(document => body.append('files', document.file, document.file.name))
    return request<{ documents: BatchRulebookView[] }>(`/api/v1/rulebooks?ownerPlayerId=${ownerId}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.getToken()}` },
      body,
    }).then(response => response.documents)
  }

  getRulebookStatus(rulebookId: string) {
    return request<RulebookView>(`/api/v1/rulebooks/${rulebookId}`, {
      headers: this.authHeaders(),
    })
  }

  retryKnowledgeDocument(knowledgeDocumentId: string) {
    return request<RulebookView>(`/api/v1/rulebooks/${knowledgeDocumentId}/retry`, {
      method: 'POST',
      headers: this.authHeaders(),
    })
  }

  uploadScenario(file: File) {
    const body = new FormData()
    body.append('file', file)
    return request<{ id: string; name: string }>('/api/v1/adventures/scenarios', {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.getToken()}` },
      body,
    })
  }

  saveRuleSet(rulebookIds: string[]) {
    return request<void>('/api/v1/rulebooks/rule-set', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ rulebookIds }),
    })
  }

  listKnowledgeDocuments(ownerId: string) {
    return request<{ ownerId: string; rulebooks: KnowledgeDocumentView[] }>(`/internal/v1/rulebooks?ownerId=${ownerId}`, {
      headers: this.authHeaders(),
    }).then(response => response.rulebooks)
  }
}
