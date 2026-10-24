<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '../campus-stores/session'
import CampusMark from '../campus-shared/CampusMark.vue'
import CampusIcon from '../campus-shared/CampusIcon.vue'
const props = defineProps<{ portal: 'MERCHANT' | 'ADMIN'; entries: { path: string; label: string; permission?: string; icon?: string; group?: string }[] }>()
const session = useSessionStore(), route = useRoute(), menuOpen = ref(false)
const isMerchant = computed(() => props.portal === 'MERCHANT')
const home = computed(() => isMerchant.value ? '/merchant' : '/admin')
const title = computed(() => isMerchant.value ? '商家中心' : '平台管理')
const visible = computed(() => props.entries.filter(e => !e.permission || session.permissions.includes(e.permission)))
const active = computed(() => route.path === home.value ? (isMerchant.value ? '门店首页' : '管理首页') : visible.value.find(e => e.path === route.path)?.label ?? '账号与资料')
watch(() => route.path, () => { menuOpen.value = false })
</script>
<template>
  <div class="workspace-shell" :class="isMerchant ? 'merchant-shell' : 'admin-shell'">
    <aside class="workspace-sidebar">
      <RouterLink class="brand workspace-brand" :to="home"><CampusMark /><span>校园周边优惠<small>{{ title }}</small></span></RouterLink>
      <button class="workspace-menu secondary" :aria-expanded="menuOpen" aria-controls="workspace-navigation" @click="menuOpen = !menuOpen"><CampusIcon :name="menuOpen ? 'close' : 'menu'" /><span>{{ menuOpen ? '收起菜单' : '展开菜单' }}</span></button>
      <div id="workspace-navigation" class="workspace-navigation" :class="{ 'is-open': menuOpen }">
        <p class="nav-section-label">{{ isMerchant ? '门店经营' : '平台工作台' }}</p>
        <nav :aria-label="isMerchant ? '商家端导航' : '管理端导航'">
          <RouterLink :to="home"><CampusIcon name="home" /><span>{{ isMerchant ? '门店首页' : '管理首页' }}</span></RouterLink>
          <RouterLink v-for="entry in visible" :key="entry.path" :to="entry.path"><CampusIcon :name="entry.icon ?? 'file'" /><span>{{ entry.label }}</span></RouterLink>
        </nav>
        <div class="workspace-account"><RouterLink to="/account"><span class="avatar-initial">{{ session.profile?.nickname?.slice(0, 1) ?? '校' }}</span><span>{{ session.profile?.nickname ?? '当前账号' }}<small>账号与退出</small></span><CampusIcon name="chevron" :size="16" /></RouterLink></div>
      </div>
    </aside>
    <div class="workspace-body">
      <header class="workspace-header"><div class="workspace-breadcrumb"><span>{{ title }}</span><CampusIcon name="chevron" :size="14" /><strong>{{ active }}</strong></div><span class="scope-note">{{ isMerchant ? '仅限所属门店经营' : '按当前权限开放' }}</span></header>
      <main id="main-content" class="workspace-canvas"><slot /></main>
      <footer class="workspace-footer">校园周边优惠<span>{{ isMerchant ? '把好生意，留在校园周边。' : '让每一次审核都有据可查。' }}</span></footer>
    </div>
  </div>
</template>
