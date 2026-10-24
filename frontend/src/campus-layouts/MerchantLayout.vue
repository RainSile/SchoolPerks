<script setup lang="ts">
import WorkspaceShell from './WorkspaceShell.vue'
const entries = [
  { path: '/merchant/dashboard', permission: 'merchant:analytics:read', label: '经营看板', icon: 'chart' },
  { path: '/merchant/coupons', permission: 'merchant:read', label: '优惠管理', icon: 'ticket' },
  { path: '/merchant/marketing', permission: 'merchant:read', label: 'AI 营销草稿', icon: 'book' },
  { path: '/merchant/trade', permission: 'merchant:read', label: '套餐与订单', icon: 'store' },
  { path: '/merchant/redemptions', permission: 'merchant:read', label: '到店核销', icon: 'check' },
  { path: '/merchant/location', permission: 'merchant:read', label: '门店位置', icon: 'pin' },
]
</script>
<template><WorkspaceShell portal="MERCHANT" :entries="entries"><slot /></WorkspaceShell></template>
