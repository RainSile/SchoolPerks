<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import { lockDocumentScroll } from '../../campus-shared/modal-scroll'

const props = defineProps<{ file: File }>()
const emit = defineEmits<{ confirm: [file: File]; cancel: [] }>()
const dialog = ref<HTMLElement>(), frame = ref<HTMLElement>(), image = ref<HTMLImageElement>()
const source = URL.createObjectURL(props.file), ready = ref(false), exporting = ref(false), error = ref('')
const zoom = ref(1), x = ref(0), y = ref(0), width = ref(0), height = ref(0), frameSize = ref(280)
const side = computed(() => Math.min(width.value, height.value) / zoom.value)
const scale = computed(() => frameSize.value / side.value)
const imageStyle = computed(() => ({ width: width.value * scale.value + 'px', height: height.value * scale.value + 'px', left: -x.value * scale.value + 'px', top: -y.value * scale.value + 'px' }))
let drag: { id: number; x: number; y: number; originX: number; originY: number } | null = null
let observer: ResizeObserver | undefined, alive = true
const previousFocus = document.activeElement as HTMLElement | null
let releaseScroll: (() => void) | undefined
function clamp() {
  x.value = Math.max(0, Math.min(x.value, width.value - side.value))
  y.value = Math.max(0, Math.min(y.value, height.value - side.value))
}
function reset() {
  zoom.value = 1
  x.value = (width.value - Math.min(width.value, height.value)) / 2
  y.value = (height.value - Math.min(width.value, height.value)) / 2
}
function loaded() {
  width.value = image.value!.naturalWidth; height.value = image.value!.naturalHeight
  ready.value = true; reset()
}
watch(zoom, (_value, old) => {
  const oldSide = Math.min(width.value, height.value) / old
  x.value += (oldSide - side.value) / 2; y.value += (oldSide - side.value) / 2; clamp()
}, { flush: 'sync' })
function start(event: PointerEvent) {
  if (!ready.value || exporting.value || (event.pointerType === 'mouse' && event.button !== 0)) return
  frame.value?.setPointerCapture(event.pointerId)
  drag = { id: event.pointerId, x: event.clientX, y: event.clientY, originX: x.value, originY: y.value }
}
function move(event: PointerEvent) {
  if (!drag || drag.id !== event.pointerId) return
  x.value = drag.originX - (event.clientX - drag.x) / scale.value
  y.value = drag.originY - (event.clientY - drag.y) / scale.value; clamp()
}
function keyMove(event: KeyboardEvent) {
  if (!ready.value || exporting.value || !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return
  event.preventDefault()
  const step = (event.shiftKey ? 30 : 10) / scale.value
  x.value += event.key === 'ArrowLeft' ? step : event.key === 'ArrowRight' ? -step : 0
  y.value += event.key === 'ArrowUp' ? step : event.key === 'ArrowDown' ? -step : 0; clamp()
}
function close() { if (!exporting.value) emit('cancel') }
function trap(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); return }
  if (event.key !== 'Tab') return
  const items = Array.from(dialog.value!.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), [tabindex="0"]'))
  const first = items[0], last = items.at(-1)
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.value)) { event.preventDefault(); first?.focus() }
}
async function confirm() {
  if (!ready.value || exporting.value || !image.value) return
  exporting.value = true; error.value = ''
  try {
    // Resample in stages instead of collapsing a large camera image into a tiny preview in one pass.
    const outputSize = Math.max(1, Math.min(512, Math.floor(side.value)))
    const outputType = props.file.type === 'image/png' ? 'image/png' : 'image/jpeg'
    let canvas = document.createElement('canvas')
    canvas.width = canvas.height = Math.max(outputSize, Math.min(2048, Math.floor(side.value)))
    let context = canvas.getContext('2d')!
    context.imageSmoothingEnabled = true; context.imageSmoothingQuality = 'high'
    if (outputType === 'image/jpeg') { context.fillStyle = '#ffffff'; context.fillRect(0, 0, canvas.width, canvas.height) }
    context.drawImage(image.value, x.value, y.value, side.value, side.value, 0, 0, canvas.width, canvas.height)
    while (canvas.width > outputSize) {
      const smaller = document.createElement('canvas')
      smaller.width = smaller.height = Math.max(outputSize, Math.floor(canvas.width / 2))
      context = smaller.getContext('2d')!; context.imageSmoothingEnabled = true; context.imageSmoothingQuality = 'high'
      context.drawImage(canvas, 0, 0, smaller.width, smaller.height); canvas = smaller
    }
    const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, outputType, .94))
    if (!blob) throw new Error('没能生成头像，请换一张照片试试。')
    if (alive) emit('confirm', new File([blob], 'noticeboard-avatar.' + (outputType === 'image/png' ? 'png' : 'jpg'), { type: outputType }))
  } catch (e) { if (alive) error.value = e instanceof Error ? e.message : '裁剪失败，请重试。' }
  finally { if (alive) exporting.value = false }
}
onMounted(async () => {
  releaseScroll = lockDocumentScroll()
  await nextTick(); dialog.value?.focus()
  observer = new ResizeObserver(entries => { frameSize.value = entries[0]!.contentRect.width })
  if (frame.value) observer.observe(frame.value)
})
onBeforeUnmount(() => {
  alive = false; observer?.disconnect(); URL.revokeObjectURL(source)
  releaseScroll?.(); void nextTick(() => { if (previousFocus?.isConnected) previousFocus.focus() })
})
</script>

<template>
  <Teleport to="body">
    <div class="avatar-crop-backdrop" @click.self="close">
      <section ref="dialog" class="avatar-crop-dialog" role="dialog" aria-modal="true" aria-labelledby="avatar-crop-title" tabindex="-1" @keydown="trap">
        <header><div><span class="crop-eyebrow">我的布告栏名片</span><h2 id="avatar-crop-title">给头像找个好位置</h2></div><button type="button" class="crop-close" aria-label="取消裁剪" :disabled="exporting" @click="close"><CampusIcon name="close" /></button></header>
        <p id="avatar-crop-help" class="muted">拖动照片、调整大小，圆圈里就是大家看到的你。</p>
        <div ref="frame" class="avatar-crop-frame" role="group" aria-label="头像裁剪区域" aria-describedby="avatar-crop-help" tabindex="0" @pointerdown="start" @pointermove="move" @pointerup="drag = null" @pointercancel="drag = null" @lostpointercapture="drag = null" @keydown="keyMove">
          <img ref="image" :src="source" :style="imageStyle" alt="待裁剪的照片" draggable="false" @load="loaded" @error="error = '这张图片无法读取，请换一张JPEG或PNG照片。'" />
          <div class="avatar-crop-mask" aria-hidden="true" /><span class="crop-crosshair" aria-hidden="true" />
        </div>
        <label class="crop-zoom">缩放照片<input v-model.number="zoom" type="range" min="1" max="3" step="0.01" :disabled="!ready || exporting" /><span>{{ Math.round(zoom * 100) }}%</span></label>
        <p class="crop-keyboard-hint">也可以聚焦照片后，用方向键微调位置。</p>
        <p v-if="error" role="alert">{{ error }}</p>
        <footer><button class="secondary" type="button" :disabled="!ready || exporting" @click="reset">重新居中</button><button type="button" :disabled="!ready || exporting" @click="confirm">{{ exporting ? '正在准备…' : '裁好，就用这张' }}<CampusIcon name="check" :size="16" /></button></footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.avatar-crop-backdrop { position:fixed;inset:0;z-index:2000;background:#18274480;backdrop-filter:blur(5px);display:grid;place-items:center;padding:16px; }
.avatar-crop-dialog { width:min(100%,420px);max-height:calc(100dvh - 32px);overflow:auto;padding:24px;background:var(--campus-surface);border:1px solid var(--campus-line);border-radius:20px;box-shadow:0 24px 80px #14295333; }
.avatar-crop-dialog header { display:flex;justify-content:space-between;gap:12px;align-items:center; }.avatar-crop-dialog h2 { margin:3px 0 0;font-family:var(--campus-font-display);font-size:22px; }
.crop-eyebrow { font-size:11px;color:var(--campus-blue);letter-spacing:.12em; }.crop-close { padding:8px;background:var(--campus-blue-soft);color:var(--campus-blue);border-radius:50%;flex:none; }.crop-close:hover:not(:disabled) { color:white; }
.avatar-crop-dialog > p { font-size:12px;line-height:1.7; }.avatar-crop-frame { width:min(100%,280px);aspect-ratio:1;position:relative;overflow:hidden;margin:20px auto;background:#e7edf6;cursor:grab;touch-action:none;user-select:none;border-radius:12px; }.avatar-crop-frame:active { cursor:grabbing; }
.avatar-crop-frame img { position:absolute;max-width:none;max-height:none;display:block;pointer-events:none;image-rendering:auto; }.avatar-crop-mask { position:absolute;inset:0;border-radius:50%;box-shadow:0 0 0 100px #1526408c,inset 0 0 0 2px #fff;pointer-events:none; }.crop-crosshair { position:absolute;inset:33.333%;border:1px solid #ffffff66;pointer-events:none; }
.crop-zoom { display:flex;align-items:center;gap:12px;font-size:12px;margin:12px 0 0; }.crop-zoom input { padding:0;flex:1;min-width:0;accent-color:var(--campus-blue); }.crop-zoom span { min-width:38px;text-align:right;font-variant-numeric:tabular-nums; }.crop-keyboard-hint { color:var(--campus-muted);font-size:11px; }
.avatar-crop-dialog footer { display:flex;justify-content:space-between;gap:10px;margin-top:20px; }.avatar-crop-dialog footer button { font-size:13px;padding:9px 12px; }
@media(max-width:380px) { .avatar-crop-dialog { padding:18px; }.avatar-crop-dialog h2 { font-size:20px; } }
</style>
