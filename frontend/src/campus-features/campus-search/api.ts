import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
export interface Segment { text: string; matched: boolean }
export interface Hit { id: string; resourceType: string; schoolId: string; merchantId: string | null; title: string; excerpt: string; titleSegments: Segment[]; excerptSegments: Segment[] }
export interface Results { backend: string; items: Hit[]; total: number; page: number; size: number; windowLimited: boolean }
export interface Capabilities { enabled: boolean; backend: string; elasticsearchConfigured: boolean; mapSdkAvailable: boolean; coordinateSystem: string; maxWindow: number; maxRadiusMeters: number }
export interface Rebuild { id: string; status: string; processed: number; cursorType: string; cursorId: string; errorCode: string | null; createdAt: string; completedAt: string | null }
export interface Nearby { id: string; name: string; category: string; address: string; longitude: number; latitude: number; coordinateSystem: string; distanceMeters: number }
export interface Location { merchantId: string; longitude: number; latitude: number; coordinateSystem: string; version: number }
export const capabilities = () => transport<Capabilities>('/search-capabilities')
export const search = (keyword: string, type: string, schoolId: string, page = 1) => transport<Results>(`/search-results?keyword=${encodeURIComponent(keyword)}&page=${page}&size=20${type ? `&resourceType=${encodeURIComponent(type)}` : ''}${schoolId ? `&schoolId=${encodeURIComponent(schoolId)}` : ''}`)
export const detail = (hit: Hit) => transport<{ name?: string; title?: string; address?: string; description?: string; price?: string; couponType?: string; rules?: { threshold: number; amountOff: number | null; discountRate: number | null } }>(`/${{ MERCHANT: 'merchants', COUPON: 'coupons', OFFER: 'trade-offers' }[hit.resourceType]}/${encodeURIComponent(hit.id)}`)
export const nearby = (longitude: string, latitude: string, radius: number, category: string, page = 1) => transport<PageResponse<Nearby>>(`/nearby-merchants?longitude=${encodeURIComponent(longitude)}&latitude=${encodeURIComponent(latitude)}&coordinateSystem=WGS84&radiusMeters=${radius}&page=${page}&size=20${category ? `&category=${encodeURIComponent(category)}` : ''}`)
export const location = (id: string) => request<Location>(`/merchants/${encodeURIComponent(id)}/location`)
export const saveLocation = (id: string, input: { longitude: string; latitude: string; coordinateSystem: string; version: number }) => request<Location>(`/merchants/${encodeURIComponent(id)}/location`, { method: 'PUT', body: JSON.stringify(input) })
export const rebuilds = (page = 1) => request<PageResponse<Rebuild>>(`/admin/search-index-rebuilds?page=${page}&size=20`)
export const rebuild = () => request<Rebuild>('/admin/search-index-rebuilds', { method: 'POST' })
export const retry = (id: string) => request<Rebuild>(`/admin/search-index-rebuilds/${encodeURIComponent(id)}/retries`, { method: 'POST' })
export const typeLabels: Record<string, string> = { MERCHANT: '商家', COUPON: '优惠', OFFER: '套餐', POST: '帖子' }
