<script setup lang="ts">
defineProps<{ page: number; size: number; total: number; disabled?: boolean }>()
const emit = defineEmits<{ change: [page: number] }>()
</script>
<template><nav class="pagination" aria-label="分页"><button class="secondary" :disabled="disabled || page <= 1" @click="emit('change', page - 1)">上一页</button><span aria-live="polite">第 {{ page }} 页 · 共 {{ total }} 条</span><button class="secondary" :disabled="disabled || page * size >= total" @click="emit('change', page + 1)">下一页</button></nav></template>
