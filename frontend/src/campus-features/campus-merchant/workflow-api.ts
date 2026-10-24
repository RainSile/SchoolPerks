import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
import type { DownloadTicket } from '../campus-file/api'
export interface MerchantApplication {
  id: string; applicantId: string; schoolId: string; schoolName: string; name: string; category: string; address: string
  longitude: number | null; latitude: number | null; description: string; qualificationFileId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'; merchantId: string | null; reviewReason: string | null; submittedAt: string; reviewedAt: string | null
}
export interface ApplicationInput { schoolId: string; name: string; category: string; address: string; description: string; qualificationFileId: string }
export interface MerchantWorkspace { id: string; name: string; schoolId: string; category: string; address: string; status: string; version: number; description: string; businessHours: string; role: string }
export interface Member { userId: string; nickname: string; role: string; userStatus: string; createdAt: string }
export const categories = () => transport<string[]>('/merchant-categories')
export const submitApplication = (input: ApplicationInput) => request<MerchantApplication>('/merchant-applications', { method: 'POST', body: JSON.stringify(input) })
export const myApplications = (page = 1) => request<PageResponse<MerchantApplication>>(`/users/me/merchant-applications?page=${page}&size=20`)
export const myMerchants = (page = 1) => request<PageResponse<MerchantWorkspace>>(`/users/me/merchants?page=${page}&size=20`)
export const getWorkspace = (id: string) => request<MerchantWorkspace>(`/merchants/${encodeURIComponent(id)}/workspace`)
export const updateMerchant = (id: string, version: number, description: string, businessHours: string) => request<MerchantWorkspace>(`/merchants/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify({ version, description, businessHours }) })
export const listMembers = (id: string) => request<PageResponse<Member>>(`/merchants/${encodeURIComponent(id)}/members?page=1&size=100`)
export const addMember = (id: string, userId: string, role: string) => request<Member>(`/merchants/${encodeURIComponent(id)}/members`, { method: 'POST', body: JSON.stringify({ userId, role }) })
export const removeMember = (id: string, userId: string) => request<void>(`/merchants/${encodeURIComponent(id)}/members/${encodeURIComponent(userId)}`, { method: 'DELETE' })
export const qualificationDownload = (id: string) => request<DownloadTicket>(`/merchants/${encodeURIComponent(id)}/qualification-download-tickets`, { method: 'POST' })
export const categoryLabels: Record<string, string> = { DINING: '餐饮', ENTERTAINMENT: '娱乐', LIFE_SERVICE: '生活服务' }
export const statusLabels: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', SUSPENDED: '已暂停' }
