export type AccountType = 'USER' | 'MERCHANT' | 'ADMIN'
export const portalHome = (type: AccountType) => ({ USER: '/app', MERCHANT: '/merchant', ADMIN: '/admin' })[type]
export function destinationPortal(path: string): AccountType | 'SHARED' | null {
  if (['/account', '/account/password', '/forbidden', '/login', '/register', '/forgot-password'].includes(path)) return 'SHARED'
  if (path === '/admin' || path.startsWith('/admin/')) return 'ADMIN'
  if (path === '/merchant/applications' || path === '/merchant/applications/new') return 'USER'
  if (path === '/merchant' || path.startsWith('/merchant/')) return 'MERCHANT'
  if (['/app', '/trade', '/community', '/search', '/nearby', '/account/coupons', '/account/orders', '/account/posts', '/account/campus-verification', '/account/merchant-applications', '/account/merchant-application'].includes(path)) return 'USER'
  if (/^\/community\/posts\/[1-9][0-9]{0,17}$/.test(path)) return 'USER'
  return null
}
export function loginDestination(value: unknown, type: AccountType): string {
  if (typeof value !== 'string') return portalHome(type)
  const portal = destinationPortal(value)
  return !['/login', '/register', '/forgot-password'].includes(value) && (portal === type || portal === 'SHARED') ? value : portalHome(type)
}
