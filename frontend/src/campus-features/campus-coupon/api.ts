import { transport } from '../../campus-shared/transport'
import { request, type PageResponse } from '../../campus-shared/http'
export interface CouponRules { threshold: number; amountOff: number | null; discountRate: number | null }
export interface PublicCoupon { id: string; merchantId: string; merchantName: string; schoolId: string; title: string; couponType: string; rules: CouponRules; remainingStock: number; validFrom: string; validUntil: string; campusOnly: boolean; description: string }
export interface CouponInput { title: string; couponType: string; rules: CouponRules; totalStock: number; validFrom: string; validUntil: string; campusOnly: boolean; description: string }
export interface MerchantCoupon extends CouponInput { id: string; merchantId: string; remainingStock: number; status: string; version: number; reviewStatus: string; reviewRequestId: string | null; publishedAt: string | null; reviewReason: string | null }
export interface CouponClaim { id: string; couponId: string; merchantId: string; merchantName: string; title: string; couponType: string; rules: CouponRules; status: string; claimedAt: string; validFrom: string; validUntil: string; redeemedAt: string | null; redemptionId: string | null }
export interface RedemptionTicket { code: string; qrCodeSvg: string; expiresAt: string }
export interface Redemption { id: string; claimId: string; merchantId: string; operatorId: string; redeemedAt: string }
export interface CouponReviewRequest { id: string; couponId: string; submittedBy: string; contentVersion: number; status: string; createdAt: string; reviewedAt: string | null; content: MerchantCoupon }
export const browseCoupons = (page = 1, keyword = '', schoolId = '') => transport<PageResponse<PublicCoupon>>(`/coupons?page=${page}&size=20${keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''}${schoolId ? `&schoolId=${encodeURIComponent(schoolId)}` : ''}`)
export const claimCoupon = (id: string) => request<CouponClaim>(`/coupons/${encodeURIComponent(id)}/claims`, { method: 'POST' })
export const myClaims = (page = 1) => request<PageResponse<CouponClaim>>(`/users/me/coupon-claims?page=${page}&size=20`)
export const redemptionTicket = (id: string) => request<RedemptionTicket>(`/coupon-claims/${encodeURIComponent(id)}/redemption-tickets`, { method: 'POST' })
export const merchantCoupons = (id: string, page = 1) => request<PageResponse<MerchantCoupon>>(`/merchants/${encodeURIComponent(id)}/coupons?page=${page}&size=20`)
export const createCoupon = (merchant: string, input: CouponInput) => request<MerchantCoupon>(`/merchants/${encodeURIComponent(merchant)}/coupons`, { method: 'POST', body: JSON.stringify(input) })
export const updateCoupon = (id: string, version: number, content: CouponInput) => request<MerchantCoupon>(`/coupons/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify({ version, content }) })
export const submitCouponReview = (id: string, version: number) => request(`/coupons/${encodeURIComponent(id)}/review-requests`, { method: 'POST', body: JSON.stringify({ version }) })
export const changeCouponStatus = (id: string, version: number, status: string, reason: string) => request(`/coupons/${encodeURIComponent(id)}/status-changes`, { method: 'POST', body: JSON.stringify({ version, status, reason }) })
export const couponReviewQueue = (status: string, page = 1) => request<PageResponse<CouponReviewRequest>>(`/admin/coupon-review-requests?status=${encodeURIComponent(status)}&page=${page}&size=20`)
export const reviewCoupon = (id: string, decision: string, reason: string) => request(`/admin/coupon-review-requests/${encodeURIComponent(id)}/reviews`, { method: 'POST', body: JSON.stringify({ decision, reason }) })
export const redeemCoupon = (merchantId: string, code: string) => request<Redemption>('/redemptions', { method: 'POST', body: JSON.stringify({ merchantId, code }) })
export const merchantRedemptions = (id: string, page = 1) => request<PageResponse<Redemption>>(`/merchants/${encodeURIComponent(id)}/redemptions?page=${page}&size=20`)
export const couponStatus: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下架', EXPIRED: '已到期', AVAILABLE: '待使用', REDEEMED: '已核销' }
export const reviewStatus: Record<string, string> = { NONE: '未送审', PENDING: '待审核', APPROVED: '审核通过', REJECTED: '审核驳回' }
export function describeRules(type: string, rules: CouponRules) { return type === 'AMOUNT_OFF' ? `满 ${rules.threshold} 元减 ${rules.amountOff} 元` : `满 ${rules.threshold} 元享 ${Number((Number(rules.discountRate) * 10).toFixed(2))} 折` }
