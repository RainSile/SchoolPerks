<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { createUpload, completeUpload, getStorageCapabilities, ownFiles, type StorageCapabilities, type UploadMetadata } from './api'
defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [id: string] }>()
const capabilities = ref<StorageCapabilities | null>(null), files = ref<UploadMetadata[]>([]), message = ref(''), busy = ref(false)
const abort = new AbortController()
let alive = true
onUnmounted(() => { alive = false; abort.abort() })
onMounted(async () => {
  try { const [caps, list] = await Promise.all([getStorageCapabilities(), ownFiles()]); if (alive) { capabilities.value = caps; files.value = list.items.filter(f => f.status === 'READY' && f.purpose === 'MERCHANT_QUALIFICATION') } }
  catch (error) { if (alive) message.value = error instanceof Error ? error.message : '资质文件加载失败' }
})
async function upload(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0], caps = capabilities.value
  if (!file || !caps?.enabled || !caps.available) return
  if (!caps.allowedTypes.includes(file.type) || file.size > caps.maxBytes || file.size <= 0) { message.value = '文件类型或大小不符合要求。'; input.value = ''; return }
  busy.value = true; message.value = ''
  try {
    const ticket = await createUpload(file)
    // This request goes directly to private object storage; never attach the platform Bearer token.
    const response = await fetch(ticket.uploadUrl, { method: 'PUT', headers: { 'Content-Type': ticket.contentType }, body: file, signal: abort.signal, credentials: 'omit' })
    if (!response.ok) throw new Error('上传失败，请重新选择文件后重试')
    const ready = await completeUpload(ticket.id)
    if (alive) { files.value.unshift(ready); emit('update:modelValue', ready.id); message.value = '资质文件已上传并完成核验。' }
  } catch (error) { if (alive) message.value = error instanceof Error ? error.message : '文件上传失败' }
  finally { if (alive) { busy.value = false; input.value = '' } }
}
</script>
<template>
  <fieldset class="file-fieldset" :disabled="busy">
    <legend>私有资质文件</legend>
    <p v-if="capabilities && !capabilities.available" role="status" class="notice">私有文件存储暂不可用，无法上传新资质。</p>
    <label for="existingQualification">使用本人已核验的资质</label>
    <select id="existingQualification" :value="modelValue" @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)">
      <option value="">请选择或上传资质</option><option v-for="file in files" :key="file.id" :value="file.id">{{ file.fileName }}</option>
    </select>
    <label for="qualificationFile">上传资质文件</label>
    <input id="qualificationFile" type="file" :accept="capabilities?.allowedTypes.join(',')" :disabled="busy || !capabilities?.available || !capabilities.enabled" @change="upload" />
    <p class="muted" v-if="capabilities">支持 {{ capabilities.allowedTypes.join('、') }}，最大 {{ Math.floor(capabilities.maxBytes / 1024 / 1024) }} MB。文件保持私有。</p>
    <p v-if="busy" role="status">正在上传并核验文件…</p><p v-if="message" role="status">{{ message }}</p>
  </fieldset>
</template>
