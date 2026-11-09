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
.user-avatar { display:inline-flex; flex:none; align-items:center; justify-content:center; overflow:hidden; border-radius:50%; background:var(--campus-blue-soft); color:var(--campus-blue); font-weight:700; box-shadow:inset 0 0 0 1px #d4dff7; vertical-align:middle; isolation:isolate; mask-image:radial-gradient(white,white); }
.user-avatar img { display:block;width:100%;height:100%;object-fit:cover;image-rendering:auto;border-radius:inherit; }
</style>
