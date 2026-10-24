import { request } from '../../campus-shared/http'
export interface Daily {
  date: string; claimCount: number; couponRedemptionCount: number; createdOrderCount: number
  paidOrderCount: number; orderRedemptionCount: number; refundCount: number; paidAmount: string; refundAmount: string
}
export interface Dashboard {
  scope: string; merchantId: string | null; merchantName: string | null; from: string; to: string; zone: string
  startsAt: string; endsAt: string; calculatedAt: string; cacheExpiresAt: string; paymentEnvironment: string; currency: string
  metrics: Omit<Daily, 'date'> & { cohortRedemptionCount: number; cohortRedemptionRate: string | null; participatingUserCount: number; netCashFlow: string }
  daily: Daily[]; topCoupons: { couponId: string; title: string; claimCount: number; cohortRedemptionCount: number }[]
  orderStatuses: { status: string; count: number }[]; newAccounts: number | null; newMerchants: number | null
  pageViewUvAvailable: boolean; pageViewUv: number | null; allowedZones: string[]; maxDays: number; definitions: string[]
}
export function dashboard(merchant: string | null, dates: { from: string; to: string; zone: string }, refresh: boolean, signal: AbortSignal) {
  const query = new URLSearchParams()
  if (dates.from) query.set('from', dates.from)
  if (dates.to) query.set('to', dates.to)
  if (dates.zone) query.set('zone', dates.zone)
  if (refresh) query.set('refresh', 'true')
  return request<Dashboard>(`${merchant === null ? '/admin/dashboard' : '/merchants/' + encodeURIComponent(merchant) + '/dashboard'}?${query}`, { signal })
}
