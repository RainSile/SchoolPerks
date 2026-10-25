import { useSessionStore } from '../../campus-stores/session'
import { config } from '../../campus-shared/config'
import { ApiError } from '../../campus-shared/http'

export async function assistantStream(path: string, key: string, body: object, signal: AbortSignal,
  receive: (event: string, data: unknown) => void): Promise<void> {
  const session = useSessionStore()
  const send = () => fetch(config.apiBaseUrl + path, { method: 'POST', signal, credentials: 'omit',
    headers: { Authorization: `Bearer ${session.accessToken}`, 'Content-Type': 'application/json', Accept: 'text/event-stream', 'Idempotency-Key': key }, body: JSON.stringify(body) })
  let response = await send()
  // Refresh only before a stream opens: never automatically replay a partially executed action.
  if (response.status === 401 && session.canRefresh()) { await session.refresh(); signal.throwIfAborted(); response = await send() }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ status: response.status, detail: '暂时无法连接助手' }))
    if (response.status === 401) session.clear()
    throw new ApiError({ ...problem, status: response.status })
  }
  if (!response.headers.get('Content-Type')?.includes('text/event-stream') || !response.body) throw new Error('助手未返回流式响应')
  const reader = response.body.getReader(), decoder = new TextDecoder()
  let buffer = '', terminal = false
  try {
    while (true) {
      signal.throwIfAborted()
      const chunk = await reader.read()
      if (chunk.done) break
      buffer = (buffer + decoder.decode(chunk.value, { stream: true })).replace(/\r\n/g, '\n')
      if (buffer.length > 262144) throw new Error('助手流式消息过长')
      let end: number
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, end); buffer = buffer.slice(end + 2)
        let event = 'message'; const lines: string[] = []
        for (const line of frame.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          if (line.startsWith('data:')) lines.push(line.slice(5).replace(/^ /, ''))
        }
        if (!lines.length) continue
        const data = JSON.parse(lines.join('\n'))
        if (event === 'error') {
          terminal = true
          if (data.status === 401) session.clear()
          throw new ApiError(data)
        }
        if (event === 'completed') terminal = true
        receive(event, data)
      }
    }
    if (!terminal) throw new Error('输出连接已中断。请查询业务结果后重试同一问题。')
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
}
