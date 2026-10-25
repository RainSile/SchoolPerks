<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from './campus-stores/session'
import UserLayout from './campus-layouts/UserLayout.vue'
import MerchantLayout from './campus-layouts/MerchantLayout.vue'
import AdminLayout from './campus-layouts/AdminLayout.vue'
import AssistantBubble from './campus-features/campus-assistant/AssistantBubble.vue'
const session = useSessionStore(), route = useRoute(), surface = ref<HTMLElement>()
const layout = computed(() => session.profile?.accountType === 'ADMIN' ? AdminLayout : session.profile?.accountType === 'MERCHANT' ? MerchantLayout : UserLayout)
let transition: Animation | undefined
watch(() => route.path, async () => {
  transition?.cancel()
  await nextTick()
  if (surface.value && !window.matchMedia('(prefers-reduced-motion: reduce)').matches)
    transition = surface.value.animate([{ opacity: .7, transform: 'translateY(5px)' }, { opacity: 1, transform: 'none' }], { duration: 180, easing: 'cubic-bezier(.16,1,.3,1)' })
})
onBeforeUnmount(() => transition?.cancel())
</script>
<template><component :is="layout"><div ref="surface" class="route-surface"><RouterView /></div></component><AssistantBubble :placement="route.path === '/nearby' ? 'left' : 'right'" /></template>
