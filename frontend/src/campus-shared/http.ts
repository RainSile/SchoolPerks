import { useSessionStore } from '../campus-stores/session'
import { ApiError, transport } from './transport'
export { ApiError } from './transport'
export type { ProblemDetail } from './transport'
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const session = useSessionStore()
  const send = () => {
    const headers = new Headers(options.headers)
    if (session.accessToken) headers.set('Authorization', `Bearer ${session.accessToken}`)
    return transport<T>(path, { ...options, headers })
  }
  try { return await send() }
  catch (error) {
    if (!(error instanceof ApiError) || error.problem.status !== 401) throw error
    if (!session.canRefresh()) { session.clear(); throw error }
    await session.refresh()
    try { return await send() }
    catch (retry) {
      if (retry instanceof ApiError && retry.problem.status === 401) session.clear()
      throw retry
    }
  }
}
export interface PageResponse<T> { items: T[]; total: number; page: number; size: number }
