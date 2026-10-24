import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'

export interface Offer { id: string; merchantId: string; title: string; description: string; kind: string; price: string; stockAvailable: number; stockTotal: number; startsAt: string; endsAt: string; groupSize: number; groupDurationSeconds: number; status: string; version: number }
export interface Order { id: string; orderNo: string; merchantId: string; offerId: string; groupId: string | null; amount: string; currency: string; offerSnapshot: { title: string; description: string; kind: string; price: string }; status: string; expiresAt: string; paidAt: string | null; createdAt: string }
export interface Group { id: string; offerId: string; targetSize: number; reservedCount: number; paidCount: number; status: string; expiresAt: string }
export interface Payment { id: string; orderId: string; status: string; checkoutUrl: string | null; expiresAt: string }
export interface Refund { id: string; orderId: string; amount: string; status: string; attempts: number; lastErrorCode: string | null }
export interface Capabilities { enabled: boolean; paymentAvailable: boolean; paymentEnvironment: string }
export interface OfferInput { title: string; description: string; kind: string; price: string; stock: number; startsAt: string; endsAt: string; groupSize: number; groupDurationSeconds: number }
const encode = encodeURIComponent
export const capabilities = () => transport<Capabilities>('/trade-capabilities')
export const offers = (page = 1) => transport<PageResponse<Offer>>(`/trade-offers?page=${page}&size=20`)
export const groups = (id: string, page = 1) => transport<PageResponse<Group>>(`/trade-offers/${encode(id)}/group-purchases?page=${page}&size=20`)
export const group = (id: string) => request<Group>(`/group-purchases/${encode(id)}`)
export const createOrder = (offerId: string, groupId: string | null, key: string) => request<Order>('/orders', { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ offerId, groupId }) })
export const orders = (page = 1) => request<PageResponse<Order>>(`/orders?page=${page}&size=20`)
export const cancel = (id: string) => request<Order>(`/orders/${encode(id)}/cancellations`, { method: 'POST' })
export const pay = (id: string) => request<Payment>(`/orders/${encode(id)}/payments`, { method: 'POST' })
export const refund = (id: string) => request<Refund>(`/orders/${encode(id)}/refunds`, { method: 'POST' })
export const refunds = (id: string) => request<Refund[]>(`/orders/${encode(id)}/refunds`)
export const retryRefund = (id: string, refundId: string) => request<Refund>(`/orders/${encode(id)}/refunds/${encode(refundId)}/retries`, { method: 'POST' })
export const redemptionCode = (id: string) => request<{ orderId: string; code: string; expiresAt: string }>(`/orders/${encode(id)}/redemption-codes`, { method: 'POST' })
export const merchantOffers = (id: string, page = 1) => request<PageResponse<Offer>>(`/merchants/${encode(id)}/trade-offers?page=${page}&size=20`)
export const createOffer = (id: string, input: OfferInput) => request<Offer>(`/merchants/${encode(id)}/trade-offers`, { method: 'POST', body: JSON.stringify(input) })
export const changeOffer = (o: Offer, status: string) => request<Offer>(`/trade-offers/${encode(o.id)}`, { method: 'PATCH', body: JSON.stringify({ status, version: o.version }) })
export const merchantOrders = (id: string, page = 1) => request<PageResponse<Order>>(`/merchants/${encode(id)}/orders?page=${page}&size=20`)
export const redeem = (merchantId: string, orderId: string, code: string) => request<Order>(`/merchants/${encode(merchantId)}/order-redemptions`, { method: 'POST', body: JSON.stringify({ orderId, code }) })
export const kindLabels: Record<string, string> = { REGULAR: '普通套餐', SECKILL: '限时秒杀', GROUP: '拼团套餐' }
export const stateLabels: Record<string, string> = { PENDING_PAYMENT: '待支付', GROUP_PENDING: '已支付，等待成团', PAID: '已付款，可到店使用', CANCELLED: '已取消', REDEEMED: '已核销', REFUND_PENDING: '退款处理中', REFUNDED: '已退款', OPEN: '拼团中', SUCCEEDED: '已成团', FAILED: '失败，等待重试', PENDING: '等待处理', PROCESSING: '处理中', DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下架' }
export const orderEvents = ['ORDER_CREATED', 'ORDER_PAID', 'ORDER_CANCELLED', 'ORDER_GROUP_SUCCEEDED', 'ORDER_REFUND_PENDING', 'ORDER_REFUNDED', 'ORDER_REDEEMED']
export const refundStateLabels: Record<string, string> = { PENDING: '等待处理', PROCESSING: '处理中', SUCCEEDED: '已退款', FAILED: '失败，等待重试' }
/** Provider URLs are never injected as HTML. Only the two official sandbox hosts may be opened. */
export function sandboxCheckoutUrl(value: string | null): string {
  if (!value) throw new Error('支付地址不可用')
  const url = new URL(value)
  if (url.protocol !== 'https:' || url.username || url.password || url.port || !['openapi.alipaydev.com', 'openapi-sandbox.dl.alipaydev.com'].includes(url.hostname) || url.pathname !== '/gateway.do') throw new Error('支付地址未通过沙箱校验')
  return url.href
}
