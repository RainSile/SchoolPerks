<script setup lang="ts">
import WorkspaceShell from './WorkspaceShell.vue'
const entries = [
  { path: '/admin/dashboard', permission: 'analytics:read', label: '平台经营看板', icon: 'chart' },
  { path: '/admin/merchant-applications', permission: 'merchant:review', label: '商家审核', icon: 'store' },
  { path: '/admin/campus-verifications', permission: 'campus:review', label: '校园认证审核', icon: 'book' },
  { path: '/admin/coupon-review-requests', permission: 'coupon:review', label: '优惠内容审核', icon: 'ticket' },
  { path: '/admin/community', permission: 'community:moderate', label: '社区管理', icon: 'community' },
  { path: '/admin/search-index', permission: 'search:manage', label: '搜索维护', icon: 'search' },
  { path: '/admin/rbac', permission: 'rbac:manage', label: '角色与账号权限', icon: 'team' },
  { path: '/admin/audit-events', permission: 'audit:read', label: '操作审计', icon: 'file' },
  { path: '/admin/operation-audit-events', permission: 'audit:read', label: '操作结果审计', icon: 'clock' },
]
</script>
<template><WorkspaceShell portal="ADMIN" :entries="entries"><slot /></WorkspaceShell></template>
