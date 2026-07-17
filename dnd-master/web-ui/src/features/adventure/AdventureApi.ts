export interface AdventureApi {
  streamMessage(adventureId: string, message: string): AsyncIterable<string>
}

export class HttpAdventureApi implements AdventureApi {
  async *streamMessage(adventureId: string, message: string): AsyncIterable<string> {
    const response = await fetch(`/api/public/adventures/${adventureId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ message }),
    })
    if (!response.ok || !response.body) throw new Error('모험 응답 스트림을 시작하지 못했습니다.')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) return
      yield decoder.decode(value, { stream: true })
    }
  }
}
