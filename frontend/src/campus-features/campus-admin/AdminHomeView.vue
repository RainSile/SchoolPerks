<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '../../campus-stores/session'
const session = useSessionStore()
const groups = [
  { title: '审核与内容', description: '核对事实，处理每一份校园生活的申请与分享。', entries: [
    { path: '/admin/merchant-applications', permission: 'merchant:review', title: '商家入驻审核', body: '核验私有资质，处理入驻及门店状态。', action: '进入商家审核 →' },
    { path: '/admin/campus-verifications', permission: 'campus:review', title: '校园认证审核', body: '查看授权身份资料并记录审核决定。', action: '进入校园审核 →' },
    { path: '/admin/coupon-review-requests', permission: 'coupon:review', title: '优惠内容审核', body: '核对规则与有效期，通过后由商家确认发布。', action: '进入优惠审核 →' },
    { path: '/admin/community', permission: 'community:moderate', title: '社区内容审核', body: '审核探店帖子、评论和举报，隐藏违规内容。', action: '进入社区审核 →' },
  ] },
  { title: '运营与维护', description: '看清业务状态，让配置、授权与操作都有迹可循。', entries: [
    { path: '/admin/dashboard', permission: 'analytics:read', title: '平台经营看板', body: '查看沙箱支付、退款、领券和核销趋势及统计口径。', action: '进入经营看板 →' },
    { path: '/admin/search-index', permission: 'search:manage', title: '搜索索引维护', body: '创建重建任务，查看进度和失败原因。', action: '维护搜索索引 →' },
    { path: '/admin/rbac', permission: 'rbac:manage', title: '角色与账号权限', body: '维护同端角色与授权，变更后撤销旧会话。', action: '进入权限管理 →' },
    { path: '/admin/audit-events', permission: 'audit:read', title: '操作审计', body: '查询关键业务操作的事务记录。', action: '查看审计记录 →' },
    { path: '/admin/operation-audit-events', permission: 'audit:read', title: '操作结果审计', body: '查看已标注接口的成功、拒绝和失败结果。', action: '查看操作结果 →' },
  ] },
]
const visible = computed(() => groups.map(group => ({ ...group, entries: group.entries.filter(e => session.permissions.includes(e.permission)) })).filter(group => group.entries.length))
</script>
<template><section class="admin-home"><h1>平台运营与审核</h1><p class="muted">各工作台按账号当前权限开放。</p><div class="admin-work-groups"><section v-for="group in visible" :key="group.title"><header><h2>{{ group.title }}</h2><p class="muted">{{ group.description }}</p></header><div class="admin-work-list"><article v-for="entry in group.entries" :key="entry.path"><div><h3>{{ entry.title }}</h3><p>{{ entry.body }}</p></div><RouterLink class="text-link" :to="entry.path">{{ entry.action }}</RouterLink></article></div></section></div><p v-if="!visible.length" class="notice">当前账号暂无可用工作台，请联系有权限的管理员分配角色。</p></section></template>
