import { request, type PageResponse } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
export interface Post { id: string; schoolId: string; merchantId: string | null; title: string; body: string; imageIds: string[]; status: string; version: number; likeCount: number; commentCount: number; createdAt: string; publishedAt: string | null; reviewReason: string | null }
export interface Content { title: string; body: string; imageIds: string[] }
export interface Comment { id: string; postId: string; body: string; status: string; version: number; createdAt: string }
export interface Review { id: string; postId: string; contentVersion: number; snapshot: Post; status: string; reason: string | null; createdAt: string }
export interface Report { id: string; postId: string; reason: string; status: string; version: number; resolution: string | null }
export interface ImageTicket { fileId: string; url: string; expiresAt: string }
export interface Capabilities { enabled: boolean; persistence: string; mongoAvailable: boolean; automaticModerationAvailable: boolean; maxImages: number; maxBodyLength: number; maxCommentLength: number }
const e = encodeURIComponent
export const capabilities = () => transport<Capabilities>('/community-capabilities')
export const schools = () => transport<{ id: string; name: string }[]>('/schools')
export const merchants = () => transport<PageResponse<{ id: string; name: string }>>('/merchants?page=1&size=100')
export const posts = (page = 1, keyword = '', schoolId = '') => transport<PageResponse<Post>>(`/posts?page=${page}&size=20&keyword=${e(keyword)}${schoolId ? `&schoolId=${e(schoolId)}` : ''}`)
export const detail = (id: string) => transport<Post>(`/posts/${e(id)}`)
export const mine = (page = 1) => request<PageResponse<Post>>(`/users/me/posts?page=${page}&size=20`)
export const create = (schoolId: string, merchantId: string | null, content: Content) => request<Post>('/posts', { method: 'POST', body: JSON.stringify({ schoolId, merchantId, content }) })
export const update = (id: string, version: number, content: Content) => request<Post>(`/posts/${e(id)}`, { method: 'PATCH', body: JSON.stringify({ version, content }) })
export const remove = (id: string) => request<void>(`/posts/${e(id)}`, { method: 'DELETE' })
export const submit = (post: Post) => request<Review>(`/posts/${e(post.id)}/review-requests`, { method: 'POST', body: JSON.stringify({ version: post.version }) })
export const comments = (id: string, page = 1) => transport<PageResponse<Comment>>(`/posts/${e(id)}/comments?page=${page}&size=20`)
export const comment = (id: string, body: string, key: string) => request<Comment>(`/posts/${e(id)}/comments`, { method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ body }) })
export const myComments = (page = 1) => request<PageResponse<Comment>>(`/users/me/post-comments?page=${page}&size=20`)
export const removeComment = (id: string) => request<void>(`/post-comments/${e(id)}`, { method: 'DELETE' })
export const like = (id: string, value: boolean) => request<{ liked: boolean; count: number }>(`/posts/${e(id)}/likes/me`, { method: value ? 'PUT' : 'DELETE' })
export const liked = (id: string) => request<{ liked: boolean; count: number }>(`/posts/${e(id)}/likes/me`)
export const report = (id: string, reason: string) => request<Report>(`/posts/${e(id)}/reports`, { method: 'POST', body: JSON.stringify({ reason }) })
export const imageTickets = (id: string) => request<ImageTicket[]>(`/posts/${e(id)}/image-download-tickets`, { method: 'POST' })
export const reviewQueue = (page = 1, status = 'PENDING') => request<PageResponse<Review>>(`/admin/post-review-requests?page=${page}&size=20&status=${e(status)}`)
export const decide = (id: string, decision: string, reason: string) => request<Review>(`/admin/post-review-requests/${e(id)}/reviews`, { method: 'POST', body: JSON.stringify({ decision, reason }) })
export const hide = (id: string, version: number, reason: string) => request<Post>(`/admin/posts/${e(id)}`, { method: 'PATCH', body: JSON.stringify({ version, reason }) })
export const commentQueue = (page = 1, status = 'PENDING') => request<PageResponse<Comment>>(`/admin/post-comments?page=${page}&size=20&status=${e(status)}`)
export const moderateComment = (c: Comment, status: string, reason: string) => request<Comment>(`/admin/post-comments/${e(c.id)}`, { method: 'PATCH', body: JSON.stringify({ version: c.version, status, reason }) })
export const reports = (page = 1, status = 'OPEN') => request<PageResponse<Report>>(`/admin/post-reports?page=${page}&size=20&status=${e(status)}`)
export const resolve = (r: Report, status: string, reason: string) => request<Report>(`/admin/post-reports/${e(r.id)}`, { method: 'PATCH', body: JSON.stringify({ version: r.version, status, reason }) })
export const states: Record<string, string> = { DRAFT: '草稿', PENDING: '待审核', PUBLISHED: '已公开', REJECTED: '已驳回', HIDDEN: '已隐藏', DELETED: '已删除', APPROVED: '已通过', OPEN: '待处理', RESOLVED: '已处理', DISMISSED: '已驳回举报' }
