export const config = Object.freeze({
  appTitle: import.meta.env.VITE_APP_TITLE || '校园周边优惠',
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, ''),
  apiTimeoutMs: Number(import.meta.env.VITE_API_TIMEOUT_MS || 10000),
  marketingPollIntervalMs: Number(import.meta.env.VITE_MARKETING_POLL_INTERVAL_MS || 3000),
})
