<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { campusQueue, campusIdentity, reviewCampus, type CampusEntry, type CampusIdentity } from './api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import { request } from '../../campus-shared/http'
const status = ref('PENDING'), rows = ref<CampusEntry[]>([]), selected = ref<CampusEntry | null>(null), identity = ref<CampusIdentity | null>(null)
const page = ref(1), total = ref(0), reason = ref(''), message = ref(''), busy = ref(false)
const studentCard = ref<{ url: string; expiresAt: string } | null>(null)
async function viewPhoto() { if (!selected.value) return; busy.value = true; message.value = ''; try { studentCard.value = await request(`/admin/campus-verifications/${encodeURIComponent(selected.value.id)}/student-card-download-tickets`, { method: 'POST' }) } catch (error) { message.value = error instanceof Error ? error.message : '学生证照片无法读取' } finally { busy.value = false } }
async function load(next = 1) { busy.value = true; selected.value = null; identity.value = null; reason.value = ''; message.value = ''; try { const data = await campusQueue(status.value, next); rows.value = data.items; page.value = data.page; total.value = data.total } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' } finally { busy.value = false } }
onMounted(() => load())
function choose(row: CampusEntry) { selected.value = row; identity.value = null; studentCard.value = null; reason.value = ''; message.value = '' }
async function viewIdentity() { if (!selected.value) return; busy.value = true; message.value = ''; try { identity.value = await campusIdentity(selected.value.id) } catch (error) { message.value = error instanceof Error ? error.message : '无法读取身份资料' } finally { busy.value = false } }
async function decide(decision: string) { if (!selected.value) return; busy.value = true; message.value = ''; try { await reviewCampus(selected.value.id, decision, reason.value.trim()); await load(page.value); message.value = '校园认证审核已保存。' } catch (error) { message.value = error instanceof Error ? error.message : '审核失败' } finally { busy.value = false } }
</script>
<template><section><RouterLink class="text-link" to="/admin">← 返回管理端</RouterLink><h1>校园认证审核</h1><label for="campusReviewStatus">认证状态</label><select id="campusReviewStatus" v-model="status" :disabled="busy" @change="load()"><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select><p v-if="message" role="status">{{ message }}</p>
  <div class="review-layout"><section><p v-if="!busy && !rows.length" class="muted">当前状态暂无申请。</p><article v-for="row in rows" :key="row.id" class="review-record"><h2>{{ row.schoolName }}</h2><p>用户编号 {{ row.applicantId }}</p><button class="secondary" :disabled="busy" @click="choose(row)">查看认证申请</button></article><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" /></section>
  <article v-if="selected"><h2>{{ selected.schoolName }}</h2><p>用户编号 {{ selected.applicantId }}</p><button class="secondary" :disabled="busy" @click="viewIdentity">读取私有身份资料</button><dl v-if="identity"><dt>真实姓名</dt><dd>{{ identity.realName }}</dd><dt>学号</dt><dd>{{ identity.studentNumber }}</dd></dl><p class="muted">身份访问会记录审计，请仅用于本次校园认证。</p>
    <section v-if="selected.status === 'PENDING'"><label for="campusReviewReason">校园审核原因</label><textarea id="campusReviewReason" v-model="reason" maxlength="255" rows="3" :disabled="busy" /><div class="button-row"><button :disabled="busy || !reason.trim() || !selected.evidenceAvailable" @click="decide('APPROVED')">通过校园认证</button><button class="secondary" :disabled="busy || !reason.trim()" @click="decide('REJECTED')">驳回校园认证</button></div></section>
    <section class="student-card-evidence"><h3>学生证首页</h3><button v-if="selected.evidenceAvailable" class="secondary" :disabled="busy" @click="viewPhoto">查看私有学生证照片</button><p v-else class="notice">没有可用照片，可能为旧申请或材料已按期限清理。缺少照片的待审申请不能直接通过。</p><img v-if="studentCard" :src="studentCard.url" alt="仅供本次审核的学生证首页" referrerpolicy="no-referrer" /><p class="muted">查看操作会记录审计。审核后照片按平台保留期限自动清除。</p></section>
  </article></div></section></template>
<style scoped>.student-card-evidence img { display:block;max-width:100%;max-height:440px;object-fit:contain;margin:12px 0; }</style>
