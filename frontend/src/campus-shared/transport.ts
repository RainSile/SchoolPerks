import { config } from './config'
export interface ProblemDetail { type?: string; title?: string; status: number; detail?: string; code?: string }
export class ApiError extends Error {
  constructor(readonly problem: ProblemDetail) { super(problem.detail || problem.title || `请求失败 (${problem.status})`) }
}
/** Credential-free transport shared by login/refresh and the authenticated request wrapper. */
export async function transport<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(`${config.apiBaseUrl}${path}`, {
    ...options, headers, signal: options.signal ?? AbortSignal.timeout(config.apiTimeoutMs),
  })
  if (!response.ok) {
    const parsed = await response.json().catch(() => ({})) as Partial<ProblemDetail>
    throw new ApiError({ ...parsed, status: response.status })
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
