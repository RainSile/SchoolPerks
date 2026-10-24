import { request, type PageResponse } from '../../campus-shared/http'
import type { MerchantApplication } from '../campus-merchant/workflow-api'
import type { DownloadTicket } from '../campus-file/api'
export interface ReviewRecord { id: string; resourceId: string; reviewerId: string; decision: string; reason: string; createdAt: string }
export interface CampusEntry { id: string; applicantId: string; schoolId: string; schoolName: string; status: string; submittedAt: string }
export interface CampusIdentity { realName: string; studentNumber: string }
export interface MerchantState { id: string; name: string; status: string; version: number }
export interface AuditEvent { id: string; actorId: string; action: string; resourceType: string; resourceId: string; details: Record<string, unknown>; createdAt: string }
export const merchantQueue = (status: string, page = 1) => request<PageResponse<MerchantApplication>>(`/admin/merchant-applications?status=${encodeURIComponent(status)}&page=${page}&size=20`)
export const reviewMerchant = (id: string, decision: string, reason: string) => request<ReviewRecord>(`/admin/merchant-applications/${encodeURIComponent(id)}/reviews`, { method: 'POST', body: JSON.stringify({ decision, reason }) })
export const reviewQualification = (id: string) => request<DownloadTicket>(`/admin/merchant-applications/${encodeURIComponent(id)}/qualification-download-tickets`, { method: 'POST' })
export const getMerchantState = (id: string) => request<MerchantState>(`/admin/merchants/${encodeURIComponent(id)}`)
export const changeMerchantState = (id: string, version: number, status: string, reason: string) => request(`/admin/merchants/${encodeURIComponent(id)}/status-changes`, { method: 'POST', body: JSON.stringify({ version, status, reason }) })
export const campusQueue = (status: string, page = 1) => request<PageResponse<CampusEntry>>(`/admin/campus-verifications?status=${encodeURIComponent(status)}&page=${page}&size=20`)
export const campusIdentity = (id: string) => request<CampusIdentity>(`/admin/campus-verifications/${encodeURIComponent(id)}/identity`)
export const reviewCampus = (id: string, decision: string, reason: string) => request<ReviewRecord>(`/admin/campus-verifications/${encodeURIComponent(id)}/reviews`, { method: 'POST', body: JSON.stringify({ decision, reason }) })
export const merchantHistory = (page = 1) => request<PageResponse<ReviewRecord>>(`/admin/merchant-reviews?page=${page}&size=20`)
export const auditEvents = (page = 1, resourceId = '') => request<PageResponse<AuditEvent>>(`/admin/audit-events?page=${page}&size=20${resourceId ? `&resourceId=${encodeURIComponent(resourceId)}` : ''}`)
