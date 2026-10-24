<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { campusQueue, campusIdentity, reviewCampus, type CampusEntry, type CampusIdentity } from './api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
const status = ref('PENDING'), rows = ref<CampusEntry[]>([]), selected = ref<CampusEntry | null>(null), identity = ref<CampusIdentity | null>(null)
const page = ref(1), total = ref(0), reason = ref(''), message = ref(''), busy = ref(false)
async function load(next = 1) { busy.value = true; selected.value = null; identity.value = null; reason.value = ''; message.value = ''; try { const data = await campusQueue(status.value, next); rows.value = data.items; page.value = data.page; total.value = data.total } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' } finally { busy.value = false } }
onMounted(() => load())
function choose(row: CampusEntry) { selected.value = row; identity.value = null; reason.value = ''; message.value = '' }
async function viewIdentity() { if (!selected.value) return; busy.value = true; message.value = ''; try { identity.value = await campusIdentity(selected.value.id) } catch (error) { message.value = error instanceof Error ? error.message : '无法读取身份资料' } finally { busy.value = false } }
async function decide(decision: string) { if (!selected.value) return; busy.value = true; message.value = ''; try { await reviewCampus(selected.value.id, decision, reason.value.trim()); await load(page.value); message.value = '校园认证审核已保存。' } catch (error) { message.value = error instanceof Error ? error.message : '审核失败' } finally { busy.value = false } }
</script>
<template><section><RouterLink class="text-link" to="/admin">← 返回管理端</RouterLink><h1>校园认证审核</h1><label for="campusReviewStatus">认证状态</label><select id="campusReviewStatus" v-model="status" :disabled="busy" @change="load()"><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select><p v-if="message" role="status">{{ message }}</p>
  <div class="review-layout"><section><p v-if="!busy && !rows.length" class="muted">当前状态暂无申请。</p><article v-for="row in rows" :key="row.id" class="review-record"><h2>{{ row.schoolName }}</h2><p>用户编号 {{ row.applicantId }}</p><button class="secondary" :disabled="busy" @click="choose(row)">查看认证申请</button></article><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" /></section>
  <article v-if="selected"><h2>{{ selected.schoolName }}</h2><p>用户编号 {{ selected.applicantId }}</p><button class="secondary" :disabled="busy" @click="viewIdentity">读取私有身份资料</button><dl v-if="identity"><dt>真实姓名</dt><dd>{{ identity.realName }}</dd><dt>学号</dt><dd>{{ identity.studentNumber }}</dd></dl><p class="muted">身份访问会记录审计，请仅用于本次校园认证。</p>
    <section v-if="selected.status === 'PENDING'"><label for="campusReviewReason">校园审核原因</label><textarea id="campusReviewReason" v-model="reason" maxlength="255" rows="3" :disabled="busy" /><div class="button-row"><button :disabled="busy || !reason.trim()" @click="decide('APPROVED')">通过校园认证</button><button class="secondary" :disabled="busy || !reason.trim()" @click="decide('REJECTED')">驳回校园认证</button></div></section>
  </article></div></section></template>
