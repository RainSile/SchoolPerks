<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../../campus-shared/http'
import { getStorageCapabilities, ownFiles, completeUpload, type StorageCapabilities, type UploadTicket, type UploadMetadata } from '../campus-file/api'
const props = defineProps<{ modelValue: string[]; max: number; disabled?: boolean }>(), emit = defineEmits<{ 'update:modelValue': [string[]] }>()
const capabilities = ref<StorageCapabilities | null>(null), available = ref<UploadMetadata[]>([]), busy = ref(false), message = ref(''), selected = ref('')
async function load() { try { capabilities.value = await getStorageCapabilities(); available.value = (await ownFiles()).items.filter(f => f.purpose === 'COMMUNITY_IMAGE' && f.status === 'READY') } catch (e) { message.value = e instanceof Error ? e.message : '图片服务不可用' } }
function add(id: string) { if (!id || props.modelValue.includes(id) || props.modelValue.length >= props.max) return; emit('update:modelValue', [...props.modelValue, id]); selected.value = '' }
async function upload(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; if (!file) return; busy.value = true; message.value = ''
  try {
    if (!capabilities.value?.available || !['image/png', 'image/jpeg'].includes(file.type) || file.size > capabilities.value.maxBytes) throw new Error('仅支持符合大小限制的 PNG/JPEG 图片')
    const ticket = await request<UploadTicket>('/uploads', { method: 'POST', body: JSON.stringify({ fileName: file.name, contentType: file.type, size: file.size, purpose: 'COMMUNITY_IMAGE' }) })
    const response = await fetch(ticket.uploadUrl, { method: 'PUT', headers: { 'Content-Type': ticket.contentType }, body: file })
    if (!response.ok) throw new Error('图片上传失败，请重试')
    const metadata = await completeUpload(ticket.id); add(metadata.id); await load()
  } catch (e) { message.value = e instanceof Error ? e.message : '图片上传失败' }
  finally { busy.value = false; input.value = '' }
}
onMounted(load)
</script>
<template><fieldset :disabled="disabled || busy"><legend>探店图片（{{ modelValue.length }}/{{ max }}）</legend><p v-if="!capabilities?.available" class="muted">图片上传服务尚未配置，可先发布文字内容。</p><label for="communityImageUpload">上传 PNG/JPEG 图片</label><input id="communityImageUpload" type="file" accept="image/png,image/jpeg" :disabled="!capabilities?.available || modelValue.length >= max" @change="upload" /><label for="communityReadyImage">选择本人已完成上传的图片</label><select id="communityReadyImage" v-model="selected" @change="add(selected)"><option value="">请选择</option><option v-for="f in available" :key="f.id" :value="f.id">{{ f.fileName }}</option></select><p v-if="message" role="status">{{ message }}</p><ul><li v-for="(id, i) in modelValue" :key="id">图片 {{ i + 1 }} <button type="button" class="secondary" @click="emit('update:modelValue', modelValue.filter(x => x !== id))">移除图片 {{ i + 1 }}</button></li></ul></fieldset></template>
