<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { createUpload, completeUpload, getStorageCapabilities, type StorageCapabilities } from './api'
import AvatarCropDialog from './AvatarCropDialog.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
const props = defineProps<{ modelValue: string; purpose: 'STUDENT_CARD' | 'USER_AVATAR'; label: string; retentionDays?: number; compact?: boolean; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [id: string]; busy: [value: boolean]; feedback: [message: string] }>()
const capabilities = ref<StorageCapabilities | null>(null), busy = ref(false), message = ref(''), preview = ref('')
const cropFile = ref<File | null>(null), chooser = ref<HTMLInputElement>()
watch(message, value => emit('feedback', value))
const abort = new AbortController()
let alive = true
onUnmounted(() => { alive = false; abort.abort(); if (preview.value) URL.revokeObjectURL(preview.value) })
onMounted(async () => { try { capabilities.value = await getStorageCapabilities() } catch { message.value = '图片存储暂不可用，请稍后重试。' } })
async function upload(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0], caps = capabilities.value
  if (!file || !caps?.available || busy.value) return
  // Avatar source may be larger than the upload limit: only the cropped bytes are sent.
  const max = caps.maxBytes
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size <= 0 || file.size > max) {
    message.value = '请选择符合大小限制的JPEG或PNG图片。'; input.value = ''; return
  }
  message.value = ''; input.value = ''
  if (props.purpose === 'USER_AVATAR') { cropFile.value = file; emit('busy', true); return }
  await sendFile(file)
}
function cancelCrop() { cropFile.value = null; emit('busy', false) }
async function cropped(file: File) { cropFile.value = null; await sendFile(file) }
async function sendFile(file: File) {
  const caps = capabilities.value
  if (!caps?.available || busy.value) { emit('busy', false); return }
  const max = props.purpose === 'USER_AVATAR' ? Math.min(caps.maxBytes, caps.avatarMaxBytes ?? caps.maxBytes) : caps.maxBytes
  if (file.size > max) { message.value = '裁好的图片仍超出大小限制，请换一张照片。'; emit('busy', false); return }
  busy.value = true; emit('busy', true); emit('update:modelValue', ''); message.value = ''
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = URL.createObjectURL(file)
  try {
    const ticket = await createUpload(file, props.purpose)
    const response = await fetch(ticket.uploadUrl, { method: 'PUT', headers: { 'Content-Type': ticket.contentType }, body: file, credentials: 'omit', signal: abort.signal })
    if (!response.ok) throw new Error('图片上传失败，请重试')
    const ready = await completeUpload(ticket.id)
    if (alive) { emit('update:modelValue', ready.id); message.value = props.purpose === 'USER_AVATAR' ? '新头像准备好了，贴到布告栏名片上吧。' : '图片已上传并完成核验。' }
  } catch (error) { if (alive) message.value = error instanceof Error ? error.message : '图片上传失败' }
  finally { if (alive) { busy.value = false; emit('busy', false) } }
}
</script>
<template><fieldset class="business-image-upload" :class="{ 'avatar-image-upload': purpose === 'USER_AVATAR', 'compact-avatar-upload': compact }" :disabled="busy || disabled" :aria-label="compact ? label : undefined">
  <legend v-if="!compact">{{ label }}</legend>
  <div class="image-upload-actions">
    <label v-if="purpose === 'STUDENT_CARD'" class="upload-choice">拍摄学生证首页<input type="file" accept="image/jpeg,image/png" capture="environment" :disabled="!capabilities?.available" @change="upload" /></label>
    <template v-if="purpose === 'USER_AVATAR'"><button type="button" class="secondary avatar-photo-choice" aria-label="挑张照片做头像" :disabled="!capabilities?.available || !!cropFile" @click="chooser?.click()"><CampusIcon v-if="compact" name="plus" :size="25" /><template v-else><CampusIcon name="user" :size="18" />挑张照片做头像<CampusIcon name="arrow" :size="15" /></template></button><input ref="chooser" class="avatar-file-input" aria-label="选择头像照片" type="file" accept="image/jpeg,image/png" :disabled="!capabilities?.available || !!cropFile" @change="upload" /></template>
    <label v-else class="upload-choice">从相册选择<input type="file" accept="image/jpeg,image/png" :disabled="!capabilities?.available" @change="upload" /></label>
  </div>
  <template v-if="!compact"><img v-if="preview" :src="preview" :alt="label + '本地预览'" class="image-upload-preview" :class="{ 'avatar-upload-preview': purpose === 'USER_AVATAR' }" />
  <p v-if="purpose === 'USER_AVATAR'" class="muted">给同学们留个印象。选好照片后，可以拖动、缩放裁剪。</p>
  <p class="muted" v-if="capabilities">支持JPEG/PNG，{{ purpose === 'USER_AVATAR' ? '原照片' : '最大' }} {{ Math.floor(capabilities.maxBytes / 1024 / 1024) }} MB{{ purpose === 'USER_AVATAR' ? '以内，裁剪后再上传。' : '。' }}</p>
  <p v-if="purpose === 'STUDENT_CARD'" class="muted">请拍清姓名、学校和学号。照片仅本人和授权审核员可查看，审核完成后{{ retentionDays ? retentionDays + '天' : '按平台配置期限' }}自动清除；认证结果保留。</p>
  <p v-if="capabilities && !capabilities.available" role="status">云端图片存储暂不可用。</p>
  <p role="status" v-if="busy">正在上传并核验…</p><p role="status" v-else-if="message">{{ message }}</p></template>
</fieldset><AvatarCropDialog v-if="cropFile" :file="cropFile" @cancel="cancelCrop" @confirm="cropped" /></template>
<style scoped>
.business-image-upload { margin:16px 0;padding:16px;border:1px dashed var(--campus-control);border-radius:10px;min-width:0; }
.image-upload-actions { display:flex;gap:12px;flex-wrap:wrap; }.upload-choice { max-width:100%;font-weight:600; }
.upload-choice input { display:block;max-width:100%;margin-top:8px; }
.image-upload-preview { display:block;max-width:100%;max-height:240px;object-fit:contain;margin:12px 0;border-radius:8px; }
.avatar-image-upload { background:var(--campus-blue-soft);border-style:solid;border-color:var(--campus-line); }.avatar-image-upload p { font-size:12px; }.avatar-photo-choice { width:100%;justify-content:flex-start; }.avatar-photo-choice > :last-child { margin-left:auto; }.avatar-file-input { display:none; }
.avatar-upload-preview { width:112px;height:112px;object-fit:cover;border-radius:50%;image-rendering:auto;box-shadow:0 0 0 4px white; }
.compact-avatar-upload { margin:0;padding:0;border:0;background:none; }.compact-avatar-upload .avatar-photo-choice { border:1px dashed var(--campus-control);border-radius:50%;width:52px;height:52px;min-height:0;padding:0;justify-content:center;color:var(--campus-blue);background:var(--campus-blue-soft); }.compact-avatar-upload .avatar-photo-choice > :last-child { margin:0; }.compact-avatar-upload .avatar-photo-choice:hover:not(:disabled) { border-color:var(--campus-blue);background:#dfeaff; }
</style>
