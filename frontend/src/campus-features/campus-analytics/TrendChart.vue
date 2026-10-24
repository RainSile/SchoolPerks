<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { Daily } from './api'
echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ rows: Daily[]; money?: boolean }>()
const element = ref<HTMLElement>()
let chart: echarts.ECharts | undefined, observer: ResizeObserver | undefined
function draw() {
  if (!chart) return
  const names = props.money ? ['沙箱支付额', '成功退款额'] : ['领券数', '优惠券核销次数']
  chart.setOption({
    animation: false, color: ['#2055ce', '#a9421d'],
    tooltip: { trigger: 'axis', renderMode: 'richText', confine: true },
    legend: { data: names }, grid: { left: 65, right: 22, top: 45, bottom: 40 },
    xAxis: { type: 'category', data: props.rows.map(r => r.date) },
    yAxis: { type: 'value', minInterval: props.money ? undefined : 1 },
    series: names.map((name, i) => ({
      name, type: 'line', smooth: false,
      data: props.rows.map(r => props.money ? Number(i ? r.refundAmount : r.paidAmount) : i ? r.couponRedemptionCount : r.claimCount),
    })),
  }, true)
}
onMounted(() => {
  if (!element.value) return
  chart = echarts.init(element.value)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(element.value)
  draw()
})
watch(() => [props.rows, props.money], draw)
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="element" class="trend-chart" role="img" :aria-label="money ? '沙箱支付与退款每日趋势，完整数值见下方每日明细' : '领券与核销每日趋势，完整数值见下方每日明细'" /></template>
<style scoped>.trend-chart { height: 240px; width: 100%; min-width: 0; }@media(max-width: 520px) { .trend-chart { height: 200px; } }</style>
