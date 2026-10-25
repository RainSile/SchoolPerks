# 前端路由逐项清点与验收差额

日期：2026-10-26。S 层源清点：从 TypeScript AST 读取实际路由，包含别名和错误页。**路由清点不代表每个页面、按钮或响应式尺寸已完成实测。** 本轮F 层脚本与 E 层旅程的操作证据见测试用例和结果；未被这些证据覆盖的操作保留待补测。

实际 F 层导航轨迹已观察 38/38 条业务/基础路由；另 1 条 AI 营销路由排除。导航经过不等于该页面全部操作通过，角色/表单/状态断言仍以相应脚本为准。

| 路由 | 组件 / 重定向 | 声明权限 | 实际导航证据（F脚本 / 宽度） |
| --- | --- | --- | --- |
| `/merchant/dashboard` | ../campus-features/campus-analytics/DashboardView.vue | merchant:analytics:read | analytics / 1280；analytics / 390 |
| `/admin/dashboard` | ../campus-features/campus-analytics/DashboardView.vue | analytics:read | analytics / 390 |
| `/admin/operation-audit-events` | ../campus-features/campus-admin/OperationAuditView.vue | audit:read | analytics / 390 |
| `/` | /app | 登录/所属端由守卫判定；公开页按路由 | rbac-business / 390 |
| `/login` | ../campus-features/campus-user/LoginView.vue | 登录/所属端由守卫判定；公开页按路由 | auth / 390；password-map / 390；rbac-business / 1280 |
| `/register` | ../campus-features/campus-user/LoginView.vue | 登录/所属端由守卫判定；公开页按路由 | password-map / 390 |
| `/forgot-password` | ../campus-features/campus-user/LoginView.vue | 登录/所属端由守卫判定；公开页按路由 | password-map / 390 |
| `/account` | ../campus-features/campus-user/AccountView.vue | 登录/所属端由守卫判定；公开页按路由 | auth / 390；password-map / 390 |
| `/account/password` | ../campus-features/campus-user/PasswordView.vue | 登录/所属端由守卫判定；公开页按路由 | password-map / 390 |
| `/account/campus-verification` | ../campus-features/campus-user/CampusVerificationView.vue | 登录/所属端由守卫判定；公开页按路由 | auth / 390 |
| `/forbidden` | ../campus-views/ForbiddenView.vue | 登录/所属端由守卫判定；公开页按路由 | auth / 390；analytics / 390 |
| `/app` | ../campus-features/campus-coupon/CouponCatalogView.vue | 登录/所属端由守卫判定；公开页按路由 | auth / 390；password-map / 390；rbac-business / 1280；rbac-business / 390；merchant / 1280；coupon / 1280；coupon / 390；community / 390 |
| `/trade` | ../campus-features/campus-trade/TradeCatalogView.vue | 登录/所属端由守卫判定；公开页按路由 | trade / 1280 |
| `/community` | ../campus-features/campus-community/CommunityView.vue | 登录/所属端由守卫判定；公开页按路由 | community / 1280 |
| `/community/posts/:id` | ../campus-features/campus-community/PostDetailView.vue | 登录/所属端由守卫判定；公开页按路由 | community / 1280 |
| `/account/posts` | ../campus-features/campus-community/MyPostsView.vue | 登录/所属端由守卫判定；公开页按路由 | community / 1280 |
| `/search` | ../campus-features/campus-search/SearchView.vue | 登录/所属端由守卫判定；公开页按路由 | community / 390 |
| `/nearby` | ../campus-features/campus-search/NearbyView.vue | 登录/所属端由守卫判定；公开页按路由 | password-map / 390；community / 390 |
| `/merchant/location` | ../campus-features/campus-search/MerchantLocationView.vue | merchant:read | community / 390 |
| `/merchant/marketing` | ../campus-features/campus-assistant/MarketingDraftsView.vue | merchant:read | 按约定排除 AI 营销 |
| `/admin/community` | ../campus-features/campus-community/CommunityModerationView.vue | community:moderate | community / 1280；community / 390 |
| `/admin/search-index` | ../campus-features/campus-search/SearchIndexView.vue | search:manage | community / 390 |
| `/account/orders` | ../campus-features/campus-trade/MyOrdersView.vue | 登录/所属端由守卫判定；公开页按路由 | rbac-business / 1280；trade / 1280 |
| `/merchant/trade` | ../campus-features/campus-trade/MerchantTradeView.vue | merchant:read | rbac-business / 1280；trade / 1280；trade / 390 |
| `/account/coupons` | ../campus-features/campus-coupon/MyCouponsView.vue | 登录/所属端由守卫判定；公开页按路由 | coupon / 1280 |
| `/merchant/coupons` | ../campus-features/campus-coupon/MerchantCouponsView.vue | merchant:read | coupon / 1280 |
| `/merchant/redemptions` | ../campus-features/campus-coupon/MerchantRedemptionsView.vue | merchant:read | coupon / 1280 |
| `/admin/coupon-review-requests` | ../campus-features/campus-admin/CouponReviewView.vue | coupon:review | coupon / 1280；coupon / 390 |
| `/merchant/applications/new` | ../campus-features/campus-merchant/MerchantApplicationView.vue | 登录/所属端由守卫判定；公开页按路由 | merchant / 1280 |
| `/merchant/applications` | ../campus-features/campus-merchant/MerchantApplicationsView.vue | 登录/所属端由守卫判定；公开页按路由 | merchant / 1280 |
| `/merchant` | ../campus-features/campus-merchant/MerchantWorkspaceView.vue | merchant:read | password-map / 390；rbac-business / 1280；merchant / 1280 |
| `/admin/rbac` | ../campus-features/campus-admin/RbacView.vue | rbac:manage | rbac-business / 1280 |
| `/account/merchant-applications` | ../campus-features/campus-merchant/MerchantApplicationsView.vue | 登录/所属端由守卫判定；公开页按路由 | rbac-business / 390 |
| `/account/merchant-application` | ../campus-features/campus-merchant/MerchantApplicationView.vue | 登录/所属端由守卫判定；公开页按路由 | rbac-business / 390 |
| `/admin` | ../campus-features/campus-admin/AdminHomeView.vue | 登录/所属端由守卫判定；公开页按路由 | auth / 390；password-map / 390；rbac-business / 1280；coupon / 1280 |
| `/admin/merchant-applications` | ../campus-features/campus-admin/MerchantReviewView.vue | merchant:review | auth / 390；merchant / 1280 |
| `/admin/campus-verifications` | ../campus-features/campus-admin/CampusReviewView.vue | campus:review | merchant / 1280 |
| `/admin/audit-events` | ../campus-features/campus-admin/AuditView.vue | audit:read | merchant / 1280 |
| `/:pathMatch(.*)*` | ../campus-views/NotFoundView.vue | 登录/所属端由守卫判定；公开页按路由 | rbac-business / 390 |

后续逐页补测需分别登记：入口/直链、角色、加载/空/错误态、字段约束与提交、重复点击、筛选/分页、键盘、刷新/后退、桌面/手机、对应 REST 响应和 SQL 不变量。不能以页面能打开代替这些维度。
