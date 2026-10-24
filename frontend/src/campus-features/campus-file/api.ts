import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
export interface StorageCapabilities { enabled: boolean; available: boolean; maxBytes: number; allowedTypes: string[]; uploadTtlSeconds: number; downloadTtlSeconds: number }
export interface UploadMetadata { id: string; fileName: string; contentType: string; size: number; purpose: string; status: 'PENDING' | 'READY'; createdAt: string; expiresAt: string; completedAt: string | null }
export interface UploadTicket { id: string; uploadUrl: string; contentType: string; expiresAt: string }
export interface DownloadTicket { url: string; expiresAt: string }
export const getStorageCapabilities = () => transport<StorageCapabilities>('/storage-capabilities')
export const ownFiles = () => request<PageResponse<UploadMetadata>>('/uploads?page=1&size=100')
export const createUpload = (file: File) => request<UploadTicket>('/uploads', { method: 'POST', body: JSON.stringify({ fileName: file.name, contentType: file.type, size: file.size, purpose: 'MERCHANT_QUALIFICATION' }) })
export const completeUpload = (id: string) => request<UploadMetadata>(`/uploads/${encodeURIComponent(id)}/completion`, { method: 'PUT' })
