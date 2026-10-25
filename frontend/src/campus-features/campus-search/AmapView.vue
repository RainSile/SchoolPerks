<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { transport } from '../../campus-shared/transport'
import { config } from '../../campus-shared/config'
import { loadAmap, type MapInstance } from './amap-sdk'
interface Point { id: string; name: string; longitude: number | string; latitude: number | string }
const props = withDefaults(defineProps<{ longitude?: string; latitude?: string; points?: Point[]; immersive?: boolean }>(), { immersive: false })
const container = ref<HTMLElement | null>(null), configured = ref(false), loading = ref(false), shown = ref(false), message = ref('')
let map: MapInstance | null = null, markers: unknown[] = [], cap: { configured: boolean; jsKey: string; proxyPath: string } | null = null, alive = true, generation = 0
async function draw() {
  const api = window.AMap, current = ++generation
  if (!api || !map) return
  map.remove(markers); markers = []
  const points: { name: string; longitude: number; latitude: number }[] = (props.points ?? []).map(p => ({ name: p.name, longitude: Number(p.longitude), latitude: Number(p.latitude) }))
  if (props.longitude && props.latitude) points.unshift({ name: '查询位置', longitude: Number(props.longitude), latitude: Number(props.latitude) })
  const valid = points.filter(p => Number.isFinite(p.longitude) && Number.isFinite(p.latitude) && Math.abs(p.longitude) <= 180 && Math.abs(p.latitude) <= 90)
  if (!valid.length) return
  await new Promise<void>(resolve => { const timer = setTimeout(() => { if (alive && current === generation) { generation++; message.value = '坐标转换超时，请稍后重试。' } resolve() }, config.apiTimeoutMs); api.convertFrom(valid.map(p => [p.longitude, p.latitude]), 'gps', (status, result) => {
    clearTimeout(timer)
    if (!alive || current !== generation || !map) { resolve(); return }
    if (status !== 'complete' || !result.locations?.length) { message.value = '坐标转换暂不可用，列表中的WGS84直线距离仍可查看。'; resolve(); return }
    markers = result.locations.map((location, index) => new api.Marker({ position: [location.getLng(), location.getLat()], title: valid[index]?.name ?? '' }))
    map.add(markers); map.setCenter(result.locations[0]); if (markers.length > 1) map.setFitView(); resolve()
  }) })
}
async function show() {
  if (loading.value || shown.value) return; loading.value = true; message.value = ''
  try { if (!cap) throw new Error('地图暂不可用'); const api = await loadAmap(cap); if (!alive || !container.value) return; shown.value = true; await nextTick(); if (!alive || !container.value) return; map = new api.Map(container.value, { zoom: 15, viewMode: '2D' }); await draw() }
  catch (e) { map?.destroy(); map = null; shown.value = false; if (alive) message.value = e instanceof Error ? e.message : '地图暂不可用' } finally { if (alive) loading.value = false }
}
watch(() => [props.longitude, props.latitude, props.points], () => { if (shown.value) void draw() }, { deep: true })
async function initialize() {
  if (loading.value) return
  message.value = ''
  try {
    cap = await transport('/map-capabilities')
    if (!alive) return
    configured.value = !!cap?.configured
    if (!configured.value) { message.value = '地图服务尚未配置。'; return }
    await nextTick()
    if (alive) await show()
  } catch { if (alive) message.value = '地图服务连接失败，请稍后重试。' }
}
onMounted(initialize)
onBeforeUnmount(() => { alive = false; generation++; map?.destroy() })
</script>
<template>
  <section class="amap-view" :class="{ 'workspace-card': !immersive, 'amap-view--immersive': immersive }" aria-label="地图位置">
    <template v-if="!immersive"><h2>地图位置</h2><p class="muted">进入页面后自动加载高德地图。定位需要浏览器授权，也可输入坐标查询附近商家。</p></template>
    <div v-if="loading || message" class="amap-feedback" :class="{ 'amap-feedback--notice': shown }">
      <p v-if="loading" role="status">正在加载地图…</p>
      <p v-if="message" role="status">{{ message }}</p>
      <button v-if="message && !shown && !loading" type="button" @click="initialize">重试加载地图</button>
    </div>
    <div v-if="configured" ref="container" class="amap-canvas" :class="{ 'amap-hidden': !shown }" aria-label="高德地图位置展示" />
    <slot />
  </section>
</template>
<style scoped>
.amap-canvas { width: 100%; height: 360px; border-radius: 12px; overflow: hidden; margin-top: 12px; }
.amap-hidden { height: 1px; visibility: hidden; }
.amap-view--immersive { position: relative; height: 100%; isolation: isolate; overflow: hidden; background: var(--campus-blue-soft); }
.amap-view--immersive .amap-canvas { position: relative; z-index: 0; height: 100%; margin: 0; border-radius: 0; }
.amap-view--immersive .amap-feedback { position: absolute; z-index: 2; inset: 24px 20px auto; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; padding: 24px; color: var(--campus-muted); text-align: center; pointer-events: none; }
.amap-view--immersive .amap-feedback button { pointer-events: auto; }
.amap-view--immersive .amap-feedback--notice { inset: 22px 22px auto; align-items: flex-start; padding: 0; font-size: 12px; }
.amap-view--immersive .amap-feedback--notice p { padding: 8px 12px; background: #fffffff2; border-radius: 8px; box-shadow: 0 2px 12px #243c5a12; }
@media(max-width: 600px) {
  .amap-canvas { height: 300px; }
  .amap-hidden { height: 1px; }
  .amap-view--immersive .amap-feedback { inset: 16px 12px auto; padding: 18px; }
  .amap-view--immersive .amap-feedback--notice { inset: 16px 12px auto; padding: 0; }
}
</style>
