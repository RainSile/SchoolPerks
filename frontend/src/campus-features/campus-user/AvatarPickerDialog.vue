<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import UserAvatar from '../../campus-shared/UserAvatar.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import { lockDocumentScroll } from '../../campus-shared/modal-scroll'
import BusinessImageUpload from '../campus-file/BusinessImageUpload.vue'
import type { DefaultAvatar } from './api'

const props = defineProps<{ defaults: DefaultAvatar[]; currentKey?: string | null; busy: boolean; message: string }>()
const emit = defineEmits<{ select: [selection: { fileId: string } | { defaultKey: string }]; close: [] }>()
const dialog = ref<HTMLElement>(), custom = ref(''), uploading = ref(false), feedback = ref('')
const previousFocus = document.activeElement as HTMLElement | null
let releaseScroll: (() => void) | undefined
watch(custom, id => { if (id) emit('select', { fileId: id }) })
function close() { if (!props.busy && !uploading.value) emit('close') }
function trap(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); return }
  if (event.key !== 'Tab') return
  const items = Array.from(dialog.value!.querySelectorAll<HTMLButtonElement>('button')).filter(button => !button.disabled && !button.closest('fieldset:disabled'))
  const first = items[0], last = items.at(-1)
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.value)) { event.preventDefault(); first?.focus() }
}
onMounted(async () => { releaseScroll = lockDocumentScroll(); await nextTick(); dialog.value?.focus() })
onBeforeUnmount(() => { releaseScroll?.(); void nextTick(() => { if (previousFocus?.isConnected) previousFocus.focus() }) })
</script>
<template>
  <Teleport to="body"><div class="avatar-picker-backdrop" @click.self="close">
    <section ref="dialog" class="avatar-picker-dialog" role="dialog" aria-modal="true" aria-labelledby="avatar-picker-title" tabindex="-1" @keydown="trap">
      <header><h2 id="avatar-picker-title">换个头像</h2><button type="button" aria-label="关闭头像选择" :disabled="busy || uploading" @click="close"><CampusIcon name="close" :size="18" /></button></header>
      <div class="avatar-picker-grid"><button v-for="item in defaults" :key="item.key" type="button" class="avatar-option" :aria-label="'选择' + item.label" :aria-pressed="currentKey === item.key" :disabled="busy || uploading" @click="emit('select', { defaultKey: item.key })"><UserAvatar :image-url="item.imageUrl" :nickname="item.label" :size="52" /><span v-if="currentKey === item.key" class="avatar-selected" aria-hidden="true"><CampusIcon name="check" :size="10" /></span></button><BusinessImageUpload v-model="custom" purpose="USER_AVATAR" label="布告栏上的我" compact :disabled="busy" @busy="uploading = $event" @feedback="feedback = $event" /></div>
      <p v-if="busy" role="status">正在更新名片…</p><p v-else-if="uploading" role="status">正在准备新头像…</p><p v-else-if="message || feedback" role="status">{{ message || feedback }}</p>
    </section>
  </div></Teleport>
</template>
<style scoped>
.avatar-picker-backdrop { position:fixed;inset:0;z-index:1800;background:#18274466;backdrop-filter:blur(4px);display:grid;place-items:center;padding:16px; }
.avatar-picker-dialog { width:min(100%,386px);max-height:calc(100dvh - 32px);overflow:auto;background:white;border:1px solid var(--campus-line);border-radius:20px;padding:22px;box-shadow:0 24px 70px #14295326; }
.avatar-picker-dialog header { display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:22px; }.avatar-picker-dialog h2 { margin:0;font-family:var(--campus-font-display);font-size:20px; }.avatar-picker-dialog header button { background:var(--campus-ground);color:var(--campus-muted);border-radius:50%;padding:7px;min-height:32px; }
.avatar-picker-grid { display:grid;grid-template-columns:repeat(5,52px);gap:14px;justify-content:center;padding:5px 0 8px; }.avatar-option { padding:0;border:0;border-radius:50%;position:relative;width:52px;height:52px;background:none;transition:transform .18s; }.avatar-option:hover:not(:disabled) { background:none;transform:translateY(-3px); }.avatar-option[aria-pressed=true] { outline:2px solid var(--campus-blue);outline-offset:3px; }.avatar-selected { display:grid;place-items:center;position:absolute;bottom:-1px;right:-1px;background:var(--campus-blue);color:white;border:2px solid white;border-radius:50%;width:17px;height:17px; }.avatar-picker-dialog p { font-size:12px;color:var(--campus-muted);margin:14px 0 0;text-align:center; }
@media(max-width:400px) { .avatar-picker-dialog { padding:20px 12px; }.avatar-picker-grid { gap:clamp(5px,2vw,10px); } }
@media(prefers-reduced-motion:reduce) { .avatar-option { transition:none; } }
</style>
