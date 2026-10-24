import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useSessionStore } from '../campus-stores/session'
import { destinationPortal, portalHome } from './portal'
export { loginDestination } from './portal'
declare module 'vue-router' { interface RouteMeta { requiresAuth?: boolean; permission?: string; anyPermission?: string[] } }
const routes: RouteRecordRaw[] = [
  { path: '/merchant/dashboard', component: () => import('../campus-features/campus-analytics/DashboardView.vue'), meta: { requiresAuth: true, permission: 'merchant:analytics:read' } },
  { path: '/admin/dashboard', component: () => import('../campus-features/campus-analytics/DashboardView.vue'), meta: { requiresAuth: true, permission: 'analytics:read' } },
  { path: '/admin/operation-audit-events', component: () => import('../campus-features/campus-admin/OperationAuditView.vue'), meta: { requiresAuth: true, permission: 'audit:read' } },
  { path: '/', redirect: '/app' },
  { path: '/login', component: () => import('../campus-features/campus-user/LoginView.vue') },
  { path: '/register', component: () => import('../campus-features/campus-user/LoginView.vue') },
  { path: '/forgot-password', component: () => import('../campus-features/campus-user/LoginView.vue') },
  { path: '/account', meta: { requiresAuth: true }, component: () => import('../campus-features/campus-user/AccountView.vue') },
  { path: '/account/password', meta: { requiresAuth: true }, component: () => import('../campus-features/campus-user/PasswordView.vue') },
  { path: '/account/campus-verification', meta: { requiresAuth: true }, component: () => import('../campus-features/campus-user/CampusVerificationView.vue') },
  { path: '/forbidden', component: () => import('../campus-views/ForbiddenView.vue') },
  { path: '/app', component: () => import('../campus-features/campus-coupon/CouponCatalogView.vue') },
  { path: '/trade', component: () => import('../campus-features/campus-trade/TradeCatalogView.vue') },
  { path: '/community', component: () => import('../campus-features/campus-community/CommunityView.vue') },
  { path: '/community/posts/:id', component: () => import('../campus-features/campus-community/PostDetailView.vue') },
  { path: '/account/posts', component: () => import('../campus-features/campus-community/MyPostsView.vue'), meta: { requiresAuth: true } },
  { path: '/search', component: () => import('../campus-features/campus-search/SearchView.vue') },
  { path: '/nearby', component: () => import('../campus-features/campus-search/NearbyView.vue') },
  { path: '/merchant/location', component: () => import('../campus-features/campus-search/MerchantLocationView.vue'), meta: { requiresAuth: true, permission: 'merchant:read' } },
  { path: '/merchant/marketing', component: () => import('../campus-features/campus-assistant/MarketingDraftsView.vue'), meta: { requiresAuth: true, permission: 'merchant:read' } },
  { path: '/admin/community', component: () => import('../campus-features/campus-community/CommunityModerationView.vue'), meta: { requiresAuth: true, permission: 'community:moderate' } },
  { path: '/admin/search-index', component: () => import('../campus-features/campus-search/SearchIndexView.vue'), meta: { requiresAuth: true, permission: 'search:manage' } },
  { path: '/account/orders', component: () => import('../campus-features/campus-trade/MyOrdersView.vue'), meta: { requiresAuth: true } },
  { path: '/merchant/trade', component: () => import('../campus-features/campus-trade/MerchantTradeView.vue'), meta: { requiresAuth: true, permission: 'merchant:read' } },
  { path: '/account/coupons', component: () => import('../campus-features/campus-coupon/MyCouponsView.vue'), meta: { requiresAuth: true } },
  { path: '/merchant/coupons', component: () => import('../campus-features/campus-coupon/MerchantCouponsView.vue'), meta: { requiresAuth: true, permission: 'merchant:read' } },
  { path: '/merchant/redemptions', component: () => import('../campus-features/campus-coupon/MerchantRedemptionsView.vue'), meta: { requiresAuth: true, permission: 'merchant:read' } },
  { path: '/admin/coupon-review-requests', component: () => import('../campus-features/campus-admin/CouponReviewView.vue'), meta: { requiresAuth: true, permission: 'coupon:review' } },
  { path: '/merchant/applications/new', component: () => import('../campus-features/campus-merchant/MerchantApplicationView.vue'), meta: { requiresAuth: true } },
  { path: '/merchant/applications', component: () => import('../campus-features/campus-merchant/MerchantApplicationsView.vue'), meta: { requiresAuth: true } },
  { path: '/merchant', component: () => import('../campus-features/campus-merchant/MerchantWorkspaceView.vue'), meta: { requiresAuth: true, permission: 'merchant:read' } },
  { path: '/admin/rbac', component: () => import('../campus-features/campus-admin/RbacView.vue'), meta: { requiresAuth: true, permission: 'rbac:manage' } },
  { path: '/account/merchant-applications', component: () => import('../campus-features/campus-merchant/MerchantApplicationsView.vue'), meta: { requiresAuth: true } },
  { path: '/account/merchant-application', component: () => import('../campus-features/campus-merchant/MerchantApplicationView.vue'), meta: { requiresAuth: true } },
  { path: '/admin', component: () => import('../campus-features/campus-admin/AdminHomeView.vue'), meta: { requiresAuth: true } },
  { path: '/admin/merchant-applications', component: () => import('../campus-features/campus-admin/MerchantReviewView.vue'), meta: { requiresAuth: true, permission: 'merchant:review' } },
  { path: '/admin/campus-verifications', component: () => import('../campus-features/campus-admin/CampusReviewView.vue'), meta: { requiresAuth: true, permission: 'campus:review' } },
  { path: '/admin/audit-events', component: () => import('../campus-features/campus-admin/AuditView.vue'), meta: { requiresAuth: true, permission: 'audit:read' } },
  { path: '/:pathMatch(.*)*', component: () => import('../campus-views/NotFoundView.vue') },
]
export const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async to => {
  const session = useSessionStore()
  try {
    if (to.meta.requiresAuth) await session.checkCurrent()
    else await session.initialize()
  } catch {
    if (to.meta.requiresAuth) return { path: '/login', query: { redirect: to.fullPath, reason: 'session-check' } }
  }
  if (to.meta.requiresAuth && !session.authenticated) return { path: '/login', query: { redirect: to.fullPath } }
  if (session.authenticated && session.profile) {
    const type = session.profile.accountType
    if (!['USER', 'MERCHANT', 'ADMIN'].includes(type)) { session.clear(); return '/login' }
    const portal = destinationPortal(to.path)
    if (['/login', '/register', '/forgot-password'].includes(to.path) || (portal && portal !== 'SHARED' && portal !== type)) return portalHome(type)
  }
  if (to.meta.anyPermission && !to.meta.anyPermission.some(p => session.permissions.includes(p))) return '/forbidden'
  if (to.meta.permission && !session.permissions.includes(to.meta.permission)) return '/forbidden'
  return true
})
