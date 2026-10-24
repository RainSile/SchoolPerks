<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../campus-stores/session'
import CampusMark from '../campus-shared/CampusMark.vue'
import CampusIcon from '../campus-shared/CampusIcon.vue'
import CampusFooter from '../campus-shared/CampusFooter.vue'
const session = useSessionStore(), route = useRoute(), router = useRouter()
const query = ref(''), menuOpen = ref(false)
const links = [
  { path: '/app', label: '优惠首页', icon: 'ticket' }, { path: '/trade', label: '套餐与拼团', icon: 'team' },
  { path: '/community', label: '探店社区', icon: 'community' }, { path: '/search', label: '搜索', icon: 'search' }, { path: '/nearby', label: '附近商家', icon: 'map' },
]
watch(() => route.path, () => { menuOpen.value = false })
function search() { if (query.value.trim()) void router.push({ path: '/search', query: { q: query.value.trim() } }) }
</script>
<template><div class="portal-user">
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <header class="site-header">
    <div class="site-header-inner">
      <RouterLink class="brand" to="/app"><CampusMark /><span>校园周边优惠<small>Campus Perks</small></span></RouterLink>
      <form class="header-search" role="search" @submit.prevent="search"><label class="sr-only" for="siteSearch">站内搜索</label><CampusIcon name="search" :size="18" /><input id="siteSearch" v-model="query" placeholder="搜优惠、商家、探店…" maxlength="100" /><button aria-label="提交站内搜索"><CampusIcon name="arrow" :size="18" /></button></form>
      <RouterLink v-if="session.authenticated" class="header-account" to="/account"><span class="avatar-initial">{{ session.profile?.nickname?.slice(0, 1) ?? '同' }}</span><span>{{ session.profile?.nickname ?? '同学' }}</span></RouterLink><RouterLink v-else class="login-link" to="/login">登录 / 注册<CampusIcon name="arrow" :size="16" /></RouterLink>
      <button class="user-menu secondary" :aria-expanded="menuOpen" aria-controls="user-navigation" @click="menuOpen = !menuOpen" aria-label="切换用户端菜单"><CampusIcon :name="menuOpen ? 'close' : 'menu'" /></button>
    </div>
    <div class="site-nav-row"><nav id="user-navigation" aria-label="用户端导航" :class="{ 'is-open': menuOpen }">
      <RouterLink v-for="link in links" :key="link.path" :to="link.path"><CampusIcon :name="link.icon" :size="16" />{{ link.label }}</RouterLink>
      <span v-if="session.authenticated" class="nav-divider" aria-hidden="true" />
      <template v-if="session.authenticated"><RouterLink to="/account/coupons">我的券包</RouterLink><RouterLink to="/account/orders">我的订单</RouterLink><RouterLink to="/account/posts">我的帖子</RouterLink><RouterLink to="/account">我的账号</RouterLink></template><RouterLink v-else class="nav-login" to="/login">登录</RouterLink>
    </nav><span class="campus-nav-note">下课之后，也有好去处。</span></div>
  </header>
  <main id="main-content" class="user-canvas"><slot /></main>
  <CampusFooter />
</div></template>
