import { transport } from '../../campus-shared/transport'
import { request } from '../../campus-shared/http'
export interface UserProfile {
  accountType: import('../../campus-router/portal').AccountType; homePath: string
  id: string; nickname: string; maskedPhone: string; schoolId: string | null; schoolName: string | null
  campusVerificationStatus: string; permissions: string[]
}
export interface SessionTokens {
  sessionId: string; tokenType: 'Bearer'; accessToken: string; accessExpiresAt: string
  refreshToken: string; refreshExpiresAt: string
}
export interface Capabilities { enabled: boolean; smsAvailable: boolean; codeLength: number; resendAfterSeconds: number; smsSimulated: boolean; passwordMinLength: number; passwordMaxBytes: number }
export interface Challenge { id: string; expiresAt: string; status: string }
export interface School { id: string; name: string }
export interface CampusVerification {
  id: string; schoolId: string; schoolName: string; status: string; submittedAt: string
  reviewedAt: string | null; reviewReason: string | null
}
export const getCapabilities = () => transport<Capabilities>('/authentication-capabilities')
export const createChallenge = (phone: string, purpose: 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD' = 'LOGIN') => transport<Challenge>('/verification-challenges', {
  method: 'POST', body: JSON.stringify({ phone, purpose }),
})
export const createSession = (phone: string, challengeId: string, code: string) => transport<SessionTokens>('/sessions', {
  method: 'POST', body: JSON.stringify({ phone, challengeId, code }),
})
export const passwordLogin = (phone: string, password: string) => transport<SessionTokens>('/password-sessions', { method: 'POST', body: JSON.stringify({ phone, password }) })
export const register = (body: { phone: string; challengeId: string; code: string; nickname: string; password: string }) => transport<SessionTokens>('/registrations', { method: 'POST', body: JSON.stringify(body) })
export const resetPassword = (body: { phone: string; challengeId: string; code: string; password: string }) => transport<void>('/password-resets', { method: 'POST', body: JSON.stringify(body) })
export const changePassword = (currentPassword: string, password: string) => request<void>('/users/me/password', { method: 'PUT', body: JSON.stringify({ currentPassword, password }) })
export const updateProfile = (nickname: string) => request<UserProfile>('/users/me', { method: 'PATCH', body: JSON.stringify({ nickname }) })
export const getSchools = () => transport<School[]>('/schools')
export const getVerifications = () => request<CampusVerification[]>('/users/me/campus-verifications')
export const applyVerification = (schoolId: string, realName: string, studentNumber: string) => request<CampusVerification>('/users/me/campus-verifications', {
  method: 'POST', body: JSON.stringify({ schoolId, realName, studentNumber }),
})
