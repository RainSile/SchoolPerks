<script setup lang="ts">
import { ref, watch } from 'vue'
const props = withDefaults(defineProps<{ imageUrl?: string; nickname?: string; size?: number }>(), { nickname: '同学', size: 40 })
const failed = ref(false)
watch(() => props.imageUrl, () => { failed.value = false })
</script>
<template><span class="user-avatar" :style="{ width: size + 'px', height: size + 'px' }">
  <img v-if="imageUrl && !failed" :src="imageUrl" :alt="nickname + '的头像'" referrerpolicy="no-referrer" @error="failed = true" />
  <span v-else aria-hidden="true">{{ Array.from(nickname)[0] || '同' }}</span>
</span></template>
<style scoped>
.user-avatar { display:inline-flex; flex:none; align-items:center; justify-content:center; overflow:hidden; border-radius:50%; background:#e9f0ff; color:#2454da; font-weight:700; border:1px solid #d4dff7; vertical-align:middle; }
.user-avatar img { width:100%;height:100%;object-fit:cover; }
</style>
