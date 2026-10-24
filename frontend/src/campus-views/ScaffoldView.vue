<script setup lang="ts">
import { ref } from 'vue'
import { getPlatform } from '../campus-features/campus-platform/api'

defineProps<{ title: string; description: string; modules: string[] }>()
const status = ref('尚未检查')
const loading = ref(false)
async function checkConnection() {
  loading.value = true
  try {
    const data = await getPlatform()
    status.value = data.stage === 'SCAFFOLD' ? '连接成功，后端处于框架阶段' : '连接成功'
  } catch (error) {
    status.value = error instanceof Error ? error.message : '连接失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="intro">
    <p class="eyebrow">{{ description }}</p>
    <h1>{{ title }}</h1>
    <p>{{ modules.includes('校园认证') ? '登录账号后可提交校园认证，更多周边服务将陆续开放。' : '以下业务模块将按开发计划逐步接入。' }}</p>
  </section>
  <section class="module-grid" aria-label="功能模块">
    <article v-for="(module, index) in modules" :key="module">
      <span class="module-index">{{ String(index + 1).padStart(2, '0') }}</span>
      <h2>{{ module }}</h2>
      <RouterLink v-if="module === '校园认证'" class="text-link" to="/account/campus-verification">前往认证</RouterLink>
      <p v-else class="muted">待实现</p>
    </article>
  </section>
  <section class="connection">
    <div>
      <h2>后端连接检查</h2>
      <p aria-live="polite">{{ status }}</p>
    </div>
    <button :disabled="loading" @click="checkConnection">{{ loading ? '检查中…' : '检查连接' }}</button>
  </section>
</template>
