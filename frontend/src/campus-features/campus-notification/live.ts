import { onUnmounted, ref } from 'vue'
import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
import { config } from '../../campus-shared/config'
import { useSessionStore } from '../../campus-stores/session'
export interface Notice { id: string; eventId: string; type: string; payload: { claimId?: string; redemptionId?: string; orderId?: string; merchantId?: string }; createdAt: string; readAt: string | null }
interface Capabilities { enabled: boolean; streamPath: string; pingIntervalSeconds: number; reconnectDelaySeconds: number }
export const myNotifications = (page = 1) => request<PageResponse<Notice>>(`/users/me/notifications?page=${page}&size=20`)
export const readNotification = (id: string) => request<Notice>(`/users/me/notifications/${encodeURIComponent(id)}/read-receipt`, { method: 'PUT' })
/** Only this page's lifecycle owns the socket. Credentials are sent in the first frame, never in a URL. */
export function useLiveNotifications(onChange: (notice?: Notice) => Promise<void>, eventTypes: string[] = ['COUPON_REDEEMED']) {
  const session = useSessionStore(), state = ref('尚未连接')
  let socket: WebSocket | null = null, reconnect: ReturnType<typeof setTimeout> | undefined, ping: ReturnType<typeof setInterval> | undefined, disposed = false, capabilities: Capabilities | null = null
  const seen = new Set<string>()
  async function connect() {
    if (disposed) return
    try {
      await session.checkCurrent()
      if (disposed || !session.authenticated) return
      capabilities ??= await transport<Capabilities>('/notification-capabilities')
      if (disposed) return
      if (!capabilities.enabled) { state.value = '实时通知尚未启用，可手动刷新'; return }
      // Support same-origin proxy and an explicitly configured absolute API base.
      const api = new URL(config.apiBaseUrl, location.href), url = new URL(capabilities.streamPath, api.origin)
      url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
      const current = new WebSocket(url)
      socket = current; state.value = '连接中'
      current.onopen = () => { if (disposed) { current.close(); return } current.send(JSON.stringify({ type: 'AUTHENTICATE', accessToken: session.accessToken })) }
      current.onmessage = event => {
        try { const message = JSON.parse(event.data)
          if (message.type === 'READY') {
            state.value = '实时通知已连接'; void onChange().catch(() => {})
            ping = setInterval(() => { if (current.readyState === WebSocket.OPEN) current.send(JSON.stringify({ type: 'PING' })) }, capabilities!.pingIntervalSeconds * 1000)
          } else if (eventTypes.includes(message.type) && typeof message.eventId === 'string' && !seen.has(message.eventId)) {
            seen.add(message.eventId); if (seen.size > 200) seen.delete(seen.values().next().value!)
            void onChange(message as Notice).catch(() => { state.value = '已收到通知，可手动刷新券状态' })
          }
        } catch { state.value = '通知格式异常，可手动刷新' }
      }
      current.onclose = () => { clearInterval(ping); if (socket === current) socket = null; if (!disposed) { state.value = '连接已断开，正在重连'; reconnect = setTimeout(() => void connect(), capabilities!.reconnectDelaySeconds * 1000) } }
      current.onerror = () => { state.value = '实时连接暂不可用，可手动刷新' }
    } catch { if (!disposed) { state.value = '实时连接暂不可用，可手动刷新'; if (capabilities && session.authenticated) reconnect = setTimeout(() => void connect(), capabilities.reconnectDelaySeconds * 1000) } }
  }
  onUnmounted(() => { disposed = true; clearTimeout(reconnect); clearInterval(ping); socket?.close(); socket = null; seen.clear() })
  return { state, connect }
}
