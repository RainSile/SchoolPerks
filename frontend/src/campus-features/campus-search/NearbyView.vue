<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { categories, categoryLabels } from '../campus-merchant/workflow-api'
import { config } from '../../campus-shared/config'
import AmapView from './AmapView.vue'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import * as api from './api'
const categoryOptions = ref<string[]>([])
const longitude = ref(''), latitude = ref(''), radius = ref(5000), category = ref(''), capability = ref<api.Capabilities | null>(null), result = ref<Awaited<ReturnType<typeof api.nearby>> | null>(null), busy = ref(false), message = ref('')
const locating = ref(false)
async function locate() { busy.value = true; locating.value = true; message.value = ''; try { if (!navigator.geolocation) throw new Error('当前浏览器不支持定位，可手动输入 WGS84 坐标'); const position = await new Promise<GeolocationPosition>((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: config.apiTimeoutMs, maximumAge: 0 })); longitude.value = position.coords.longitude.toFixed(7); latitude.value = position.coords.latitude.toFixed(7); await search() } catch (e) { message.value = e instanceof Error ? e.message : '定位被拒绝或暂不可用，可手动输入坐标' } finally { busy.value = false; locating.value = false } }
async function search(page = 1) { if (!longitude.value || !latitude.value) return; busy.value = true; message.value = ''; try { result.value = await api.nearby(longitude.value, latitude.value, radius.value, category.value, page) } catch (e) { result.value = null; message.value = e instanceof Error ? e.message : '附近商家查询失败' } finally { busy.value = false } }
onMounted(async () => { try { capability.value = await api.capabilities(); categoryOptions.value = await categories(); radius.value = Math.min(radius.value, capability.value.maxRadiusMeters) } catch (e) { message.value = e instanceof Error ? e.message : '服务不可用' } })
</script>
<template>
  <section class="nearby-page">
    <h1 class="nearby-heading">附近商家</h1>
    <div class="nearby-map">
      <AmapView immersive :longitude="longitude" :latitude="latitude" :points="result?.items">
        <div class="nearby-map-controls">
        <button class="nearby-locate" type="button" aria-label="使用当前位置寻找商家" :aria-busy="locating" :title="locating ? '正在获取当前位置…' : '使用当前位置寻找商家'" :class="{ 'is-locating': locating }" :disabled="busy" @click="locate"><CampusIcon name="locate" :size="23" /><span class="sr-only">使用当前位置寻找商家</span></button>
        <aside class="nearby-query-panel" aria-label="附近商家查询">
          <form class="nearby-query" @submit.prevent="search()">
            <button class="nearby-submit" type="submit" aria-label="查询附近商家" :title="busy ? '正在查找…' : '查询附近商家'" :aria-busy="busy" :disabled="busy"><CampusIcon name="search" :size="19" /></button>
            <div class="nearby-fields">
              <div><label for="nearbyLongitude">WGS84 经度</label><input id="nearbyLongitude" v-model="longitude" type="number" min="-180" max="180" step="any" placeholder="输入经度" aria-describedby="nearby-coordinate-note" :disabled="busy" required /></div>
              <div><label for="nearbyLatitude">WGS84 纬度</label><input id="nearbyLatitude" v-model="latitude" type="number" min="-90" max="90" step="any" placeholder="输入纬度" aria-describedby="nearby-coordinate-note" :disabled="busy" required /></div>
              <div><label for="nearbyRadius">搜索半径（米）</label><input id="nearbyRadius" v-model.number="radius" type="number" min="1" :max="capability?.maxRadiusMeters" :disabled="busy" required /></div>
              <div><label for="nearbyCategory">商家类型</label><select id="nearbyCategory" v-model="category" :disabled="busy"><option value="">全部</option><option v-for="value in categoryOptions" :key="value" :value="value">{{ categoryLabels[value] ?? value }}</option></select></div>
            </div>
          </form>
          <p id="nearby-coordinate-note" class="sr-only">手动输入请使用 WGS84 坐标，不直接使用高德 / 腾讯坐标。</p>
          <p v-if="message" class="nearby-message" role="status">{{ message }}</p>
        </aside>
        </div>
      </AmapView>
    </div>
    <section v-if="result" class="nearby-results" aria-label="附近商家查询结果">
      <header><h2>查询结果 <span>{{ result.total }}</span></h2><p class="muted">直线距离，不代表步行路线</p></header>
      <article v-for="(m, index) in result.items" :key="m.id" class="nearby-merchant"><span class="nearby-merchant-index">{{ (result.page - 1) * result.size + index + 1 }}</span><div><h2>{{ m.name }}</h2><p>{{ m.address }}<span class="nearby-category">{{ categoryLabels[m.category] ?? m.category }}</span></p></div><p class="nearby-distance">直线距离 {{ m.distanceMeters }} 米</p></article>
      <p v-if="!result.items.length" class="nearby-empty">附近暂无已设置有效位置的公开商家，可以扩大范围再找找。</p>
      <PaginationBar :page="result.page" :size="20" :total="result.total" :disabled="busy" @change="search" />
    </section>
  </section>
</template>
<style scoped>
.nearby-heading { margin-bottom: 18px; }
.nearby-map { height: clamp(580px, 74vh, 800px); height: clamp(580px, 74svh, 800px); border: 1px solid var(--campus-line); border-radius: 16px; overflow: hidden; box-shadow: 0 8px 28px #2345770a; }
.nearby-map-controls { position: absolute; z-index: 5; right: 20px; bottom: 48px; display: flex; flex-direction: column; align-items: flex-end; gap: 10px; max-width: calc(100% - 40px); max-height: calc(100% - 68px); pointer-events: none; }
.nearby-query-panel { width: 720px; max-width: 100%; min-height: 0; overflow-y: auto; border-radius: 10px; scrollbar-width: thin; pointer-events: auto; }
.nearby-query { display: flex; align-items: flex-end; gap: 12px; margin: 0; padding: 11px 12px; border: 1px solid #ffffffb3; border-radius: 10px; background: #fffffff5; backdrop-filter: blur(12px); box-shadow: 0 5px 24px #1b355b1c; }
.nearby-fields { flex: 1; min-width: 0; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.nearby-fields > div { min-width: 0; }
.nearby-fields label { margin: 0 0 5px; font-size: 11px; color: var(--campus-muted); font-weight: 500; }
.nearby-fields input,.nearby-fields select { font-size: 12px; min-height: 37px; padding: 7px 8px; border-radius: 6px; }
.nearby-submit { display: grid; place-items: center; flex: none; width: 39px; height: 39px; min-height: 39px; margin: 0; padding: 0; border-radius: 7px; }
.nearby-submit[aria-busy=true] .campus-icon { animation: location-breathe 1s ease-in-out infinite; }
.nearby-locate { display: grid; place-items: center; flex: none; width: 46px; height: 46px; padding: 0; border: 1px solid var(--campus-line); border-radius: 12px; background: white; color: var(--campus-blue); box-shadow: 0 3px 14px #18355224; pointer-events: auto; transition: background .18s ease, transform .18s ease, box-shadow .18s ease; }
.nearby-locate:hover:not(:disabled) { background: var(--campus-blue-soft); transform: translateY(-2px); box-shadow: 0 5px 18px #18355230; }
.nearby-locate.is-locating .campus-icon { animation: location-breathe 1s ease-in-out infinite; }
.nearby-message { margin: 8px 0 0; padding: 10px 13px; border: 1px solid var(--campus-line); border-radius: 9px; background: white; color: var(--campus-ink); font-size: 12px; box-shadow: 0 3px 14px #18355214; }
.nearby-results { margin-top: 25px; }
.nearby-results > header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 10px; }
.nearby-results > header h2 { display: flex; align-items: center; gap: 9px; font-size: 18px; }
.nearby-results > header h2 span { font-size: 12px; color: var(--campus-blue); background: var(--campus-blue-soft); padding: 2px 7px; border-radius: 5px; }
.nearby-results > header p { margin: 0; font-size: 11px; }
.nearby-merchant { display: flex; align-items: center; gap: 14px; padding: 17px 4px; border-bottom: 1px solid var(--campus-line); }
.nearby-merchant-index { width: 29px; height: 32px; display: grid; place-items: center; border: 1px solid var(--campus-line); border-radius: 7px 7px 10px 10px; background: white; color: var(--campus-blue); font-size: 12px; font-weight: 700; }
.nearby-merchant h2 { margin: 0 0 3px; font-size: 15px; }
.nearby-merchant p { margin: 0; font-size: 12px; color: var(--campus-muted); }
.nearby-category { margin-left: 9px; padding-left: 9px; border-left: 1px solid var(--campus-line); }
.nearby-merchant .nearby-distance { margin-left: auto; white-space: nowrap; color: var(--campus-blue-strong); font-size: 12px; }
.nearby-empty { padding: 20px 0; color: var(--campus-muted); font-size: 13px; }
@keyframes location-breathe { 50% { opacity: .4; transform: scale(.9); } }
@media(max-width: 600px) {
  .nearby-heading { margin-bottom: 15px; }
  .nearby-map { height: clamp(560px, 74vh, 720px); height: clamp(560px, 74svh, 720px); border-radius: 12px; }
  .nearby-map-controls { right: 12px; width: calc(100% - 24px); max-width: none; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)) 39px; gap: 10px 8px; align-items: end; padding: 10px; overflow-y: auto; border: 1px solid #ffffffb3; border-radius: 10px; background: #fffffff5; backdrop-filter: blur(12px); box-shadow: 0 5px 24px #1b355b1c; pointer-events: auto; }
  /* Share the mobile grid without duplicating either action button. */
  .nearby-query-panel,.nearby-query,.nearby-fields { display: contents; }
  .nearby-locate { grid-column: 3; grid-row: 1; width: 39px; height: 39px; border-radius: 7px; box-shadow: none; }
  .nearby-submit { grid-column: 3; grid-row: 2; }
  .nearby-message { grid-column: 1 / -1; margin: 0; font-size: 11px; }
  .nearby-results > header { display: block; }
  .nearby-merchant { flex-wrap: wrap; gap: 8px 11px; }
  .nearby-merchant .nearby-distance { width: 100%; margin-left: 40px; }
}
@media(prefers-reduced-motion: reduce) { .nearby-locate { transition: none; }.nearby-locate.is-locating .campus-icon,.nearby-submit[aria-busy=true] .campus-icon { animation: none; } }
</style>
