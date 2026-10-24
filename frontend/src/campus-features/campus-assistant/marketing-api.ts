import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
import type { PublicCoupon } from '../campus-coupon/api'
export interface MarketingCapabilities { enabled: boolean; maxBriefLength: number; maxTitleLength: number; maxBodyLength: number; dailyChatLimit: number; dailyChatUsed: number; tones: string[] }
export interface MarketingDraft { id: string; merchantId: string; createdBy: string; brief: string; tone: string; couponId: string | null; status: 'GENERATING' | 'DRAFT' | 'FAILED' | 'ARCHIVED'; title: string | null; body: string | null; version: number; attempt: number; errorCode: string | null; sourceCurrent: boolean; sources: { type: string; id: string; title: string }[]; retryAfter: string | null; createdAt: string; updatedAt: string }
export interface MarketingInput { brief: string; tone: string; couponId: string | null }
const base = (merchant: string) => '/merchants/' + encodeURIComponent(merchant) + '/marketing-drafts'
export const marketingCapabilities = (merchant: string) => request<MarketingCapabilities>(base(merchant) + '/capabilities')
export const marketingDrafts = (merchant: string, page = 1) => request<PageResponse<MarketingDraft>>(base(merchant) + '?page=' + page + '&size=20')
export const marketingCoupons = (merchant: string) => transport<PageResponse<PublicCoupon>>('/coupons?page=1&size=100&merchantId=' + encodeURIComponent(merchant))
export const generateMarketingDraft = (merchant: string, key: string, body: MarketingInput, signal?: AbortSignal) => request<MarketingDraft>(base(merchant), { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(body), signal })
export const retryMarketingDraft = (merchant: string, row: MarketingDraft, signal?: AbortSignal) => request<MarketingDraft>(base(merchant) + '/' + row.id + '/generation-attempts', { method: 'POST', body: JSON.stringify({ version: row.version }), signal })
export const updateMarketingDraft = (merchant: string, row: MarketingDraft, title: string, body: string) => request<MarketingDraft>(base(merchant) + '/' + row.id, { method: 'PATCH', body: JSON.stringify({ version: row.version, title, body }) })
export const archiveMarketingDraft = (merchant: string, row: MarketingDraft) => request<MarketingDraft>(base(merchant) + '/' + row.id + '/status-changes', { method: 'POST', body: JSON.stringify({ version: row.version, status: 'ARCHIVED' }) })
