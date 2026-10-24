<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import * as api from './api'
const jobs = ref<api.Rebuild[]>([]), capability = ref<api.Capabilities | null>(null), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
const states: Record<string, string> = { PENDING: '等待处理', RUNNING: '正在重建', COMPLETED: '已完成', FAILED: '失败，需重试' }
async function load(target = page.value) { busy.value = true; message.value = ''; try { const result = await api.rebuilds(target); jobs.value = result.items; total.value = result.total; page.value = result.page; capability.value = await api.capabilities() } catch (e) { message.value = e instanceof Error ? e.message : '重建任务加载失败' } finally { busy.value = false } }
async function start(id?: string) { busy.value = true; try { if (id) await api.retry(id); else await api.rebuild(); await load(); message.value = '任务已登记，后台将分批处理，请刷新查看进度。' } catch (e) { message.value = e instanceof Error ? e.message : '操作失败' } finally { busy.value = false } }
onMounted(() => load())
</script>
<template><section><div class="section-heading"><h1>搜索索引维护</h1><button class="secondary" :disabled="busy" @click="load()">刷新重建进度</button></div><p v-if="capability">当前后端 {{ capability.backend }} · 查询窗口 {{ capability.maxWindow }} 条</p><p class="muted">重建会重新读取当前可公开内容，也会处理已隐藏内容的索引记录。完成表示扫描任务完成，不代表所有并发业务事件已投递。</p><button :disabled="busy || jobs.some(j => ['PENDING','RUNNING'].includes(j.status))" @click="start()">创建索引重建任务</button><p v-if="message" role="status">{{ message }}</p><article v-for="j in jobs" :key="j.id" class="workspace-card"><h2>{{ states[j.status] }}</h2><p>任务 {{ j.id }}</p><p>已处理 {{ j.processed }} 个资源 · 游标 {{ j.cursorType || '尚未开始' }} {{ j.cursorId }}</p><p v-if="j.errorCode">错误：{{ j.errorCode }}</p><button v-if="j.status === 'FAILED'" class="secondary" :disabled="busy" @click="start(j.id)">重试重建任务</button></article><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" /></section></template>
