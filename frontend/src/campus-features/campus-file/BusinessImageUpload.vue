<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { createUpload, completeUpload, getStorageCapabilities, type StorageCapabilities } from './api'
const props = defineProps<{ modelValue: string; purpose: 'STUDENT_CARD' | 'USER_AVATAR'; label: string; retentionDays?: number }>()
const emit = defineEmits<{ 'update:modelValue': [id: string]; busy: [value: boolean] }>()
const capabilities = ref<StorageCapabilities | null>(null), busy = ref(false), message = ref(''), preview = ref('')
const abort = new AbortController()
let alive = true
onUnmounted(() => { alive = false; abort.abort(); if (preview.value) URL.revokeObjectURL(preview.value) })
onMounted(async () => { try { capabilities.value = await getStorageCapabilities() } catch { message.value = '图片存储暂不可用，请稍后重试。' } })
async function upload(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0], caps = capabilities.value
  if (!file || !caps?.available || busy.value) return
  const max = props.purpose === 'USER_AVATAR' ? Math.min(caps.maxBytes, caps.avatarMaxBytes ?? caps.maxBytes) : caps.maxBytes
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size <= 0 || file.size > max) {
    message.value = '请选择符合大小限制的JPEG或PNG图片。'; input.value = ''; return
  }
  busy.value = true; emit('busy', true); emit('update:modelValue', ''); message.value = ''
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = URL.createObjectURL(file)
  try {
    const ticket = await createUpload(file, props.purpose)
    const response = await fetch(ticket.uploadUrl, { method: 'PUT', headers: { 'Content-Type': ticket.contentType }, body: file, credentials: 'omit', signal: abort.signal })
    if (!response.ok) throw new Error('图片上传失败，请重试')
    const ready = await completeUpload(ticket.id)
    if (alive) { emit('update:modelValue', ready.id); message.value = '图片已上传并完成核验。' }
  } catch (error) { if (alive) message.value = error instanceof Error ? error.message : '图片上传失败' }
  finally { if (alive) { busy.value = false; emit('busy', false); input.value = '' } }
}
</script>
<template><fieldset class="business-image-upload" :disabled="busy">
  <legend>{{ label }}</legend>
  <div class="image-upload-actions">
    <label v-if="purpose === 'STUDENT_CARD'" class="upload-choice">拍摄学生证首页<input type="file" accept="image/jpeg,image/png" capture="environment" :disabled="!capabilities?.available" @change="upload" /></label>
    <label class="upload-choice">从相册选择<input type="file" accept="image/jpeg,image/png" :disabled="!capabilities?.available" @change="upload" /></label>
  </div>
  <img v-if="preview" :src="preview" :alt="label + '本地预览'" class="image-upload-preview" />
  <p class="muted" v-if="capabilities">支持JPEG/PNG，最大 {{ Math.floor((purpose === 'USER_AVATAR' ? (capabilities.avatarMaxBytes ?? capabilities.maxBytes) : capabilities.maxBytes) / 1024 / 1024) }} MB。</p>
  <p v-if="purpose === 'STUDENT_CARD'" class="muted">请拍清姓名、学校和学号。照片仅本人和授权审核员可查看，审核完成后{{ retentionDays ? retentionDays + '天' : '按平台配置期限' }}自动清除；认证结果保留。</p>
  <p v-if="capabilities && !capabilities.available" role="status">云端图片存储暂不可用。</p>
  <p role="status" v-if="busy">正在上传并核验…</p><p role="status" v-else-if="message">{{ message }}</p>
</fieldset></template>
<style scoped>
.business-image-upload { margin:16px 0;padding:16px;border:1px dashed var(--border);border-radius:10px; }
.image-upload-actions { display:flex;gap:12px;flex-wrap:wrap; }.upload-choice { max-width:100%;font-weight:600; }
.upload-choice input { display:block;max-width:100%;margin-top:8px; }
.image-upload-preview { display:block;max-width:100%;max-height:240px;object-fit:contain;margin:12px 0;border-radius:8px; }
</style>
