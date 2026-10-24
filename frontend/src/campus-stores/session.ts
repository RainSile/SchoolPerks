import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError, transport } from '../campus-shared/transport'
import type { SessionTokens, UserProfile } from '../campus-features/campus-user/api'

const REFRESH_KEY = 'campus.refresh-token'
// Access token remains in memory; sessionStorage holds only the opaque refresh token.
// Never decode a JWT to grant roles, store credentials in URLs, or log response bodies.
export const useSessionStore = defineStore('campus-session', () => {
  const accessToken = ref<string | null>(null)
  const profile = ref<UserProfile | null>(null)
  const permissions = computed(() => profile.value?.permissions ?? [])
  const authenticated = computed(() => Boolean(accessToken.value && profile.value))
  let epoch = 0
  let refreshFlight: Promise<void> | null = null
  let initialized = false
  let initializeFlight: Promise<void> | null = null
  const canRefresh = () => Boolean(sessionStorage.getItem(REFRESH_KEY))
  function clear() {
    epoch++
    accessToken.value = null
    profile.value = null
    sessionStorage.removeItem(REFRESH_KEY)
  }
  async function loadProfile() {
    const generation = epoch
    const value = await transport<UserProfile>('/users/me', { headers: { Authorization: `Bearer ${accessToken.value}` } })
    if (generation === epoch) profile.value = value
  }
  async function accept(tokens: SessionTokens) {
    clear()
    accessToken.value = tokens.accessToken
    sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    initialized = true
    try { await loadProfile() } catch (error) { clear(); throw error }
  }
  async function refresh(): Promise<void> {
    if (refreshFlight) return refreshFlight
    const token = sessionStorage.getItem(REFRESH_KEY)
    if (!token) throw new ApiError({ status: 401, detail: '请重新登录' })
    const generation = epoch
    const work = (async () => {
      try {
        const tokens = await transport<SessionTokens>('/session-refreshes', { method: 'POST', body: JSON.stringify({ refreshToken: token }) })
        if (generation !== epoch) throw new ApiError({ status: 401, detail: '登录状态已变更，请重试' })
        accessToken.value = tokens.accessToken
        sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken)
        await loadProfile()
      } catch (error) {
        if (generation === epoch && error instanceof ApiError && error.problem.status === 401) clear()
        throw error
      }
    })()
    refreshFlight = work
    try { await work } finally { if (refreshFlight === work) refreshFlight = null }
  }
  async function initialize() {
    if (initialized) return
    if (initializeFlight) return initializeFlight
    initializeFlight = (async () => {
      try { if (canRefresh()) await refresh(); initialized = true }
      catch (error) {
        if (error instanceof ApiError && error.problem.status === 401) initialized = true
        else throw error
      }
    })()
    try { await initializeFlight } finally { initializeFlight = null }
  }
  async function checkCurrent() {
    await initialize()
    if (!accessToken.value) return
    try { await loadProfile() }
    catch (error) {
      if (error instanceof ApiError && error.problem.status === 401 && canRefresh()) await refresh()
      else throw error
    }
  }
  async function logout() {
    if (accessToken.value) {
      try {
        await transport<void>('/sessions/current', { method: 'DELETE', headers: { Authorization: `Bearer ${accessToken.value}` } })
      } catch (error) {
        if (!(error instanceof ApiError) || error.problem.status !== 401) throw error
        // An inactive/expired access token still requires revoking a valid refresh family.
        if (canRefresh()) {
          try {
            await refresh()
            await transport<void>('/sessions/current', { method: 'DELETE', headers: { Authorization: `Bearer ${accessToken.value}` } })
          } catch (retry) {
            if (!(retry instanceof ApiError) || retry.problem.status !== 401) throw retry
          }
        }
      }
    }
    clear()
  }
  return { accessToken, profile, permissions, authenticated, canRefresh, clear, accept, refresh, initialize, checkCurrent, loadProfile, logout }
})
