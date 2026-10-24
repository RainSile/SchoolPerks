<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import * as api from './api'
const route = useRoute(), router = useRouter(), session = useSessionStore()
const post = ref<api.Post | null>(null), rows = ref<api.Comment[]>([]), images = ref<api.ImageTicket[]>([]), liked = ref(false), body = ref(''), reason = ref(''), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
let requestKey = crypto.randomUUID()
watch(body, () => { requestKey = crypto.randomUUID() })
async function load(target = 1) {
  busy.value = true; message.value = ''; post.value = null; rows.value = []; images.value = []
  try { const id = String(route.params.id), p = await api.detail(id), c = await api.comments(id, target); post.value = p; rows.value = c.items; page.value = c.page; total.value = c.total; if (session.authenticated) liked.value = (await api.liked(id)).liked }
  catch (e) { message.value = e instanceof Error ? e.message : '帖子已隐藏或无法读取' }
  finally { busy.value = false }
}
async function authenticated() { if (session.authenticated) return true; await router.push({ path: '/login', query: { redirect: route.path } }); return false }
async function action(operation: 'like' | 'comment' | 'report' | 'images') {
  if (!post.value || !(await authenticated())) return; busy.value = true; message.value = ''
  try {
    if (operation === 'like') { const value = await api.like(post.value.id, !liked.value); liked.value = value.liked; post.value.likeCount = value.count }
    else if (operation === 'comment') { await api.comment(post.value.id, body.value, requestKey); body.value = ''; requestKey = crypto.randomUUID(); message.value = '评论已提交，审核通过后公开。' }
    else if (operation === 'report') { await api.report(post.value.id, reason.value); reason.value = ''; message.value = '举报已登记，我们会核查处理。' }
    else images.value = await api.imageTickets(post.value.id)
  } catch (e) { message.value = e instanceof Error ? e.message : '操作失败，请刷新确认状态' }
  finally { busy.value = false }
}
onMounted(() => load()); watch(() => route.params.id, () => void load())
</script>
<template><section><div class="section-heading"><RouterLink class="text-link" to="/community">← 返回社区</RouterLink><button class="secondary" :disabled="busy" @click="load(page)">刷新帖子</button></div><p v-if="message" role="status">{{ message }}</p>
  <article v-if="post" class="workspace-card"><h1 class="authored-title">{{ post.title }}</h1><p class="post-body">{{ post.body }}</p><p class="muted">{{ post.likeCount }} 人点赞 · {{ post.commentCount }} 条评论</p><div class="button-row"><button :disabled="busy" @click="action('like')">{{ liked ? '取消点赞' : '点赞' }}</button><button v-if="post.imageIds.length" class="secondary" :disabled="busy" @click="action('images')">查看探店图片（{{ post.imageIds.length }}）</button></div>
    <div class="post-images"><img v-for="img in images" :key="img.fileId" :src="img.url" alt="已审核的探店图片" referrerpolicy="no-referrer" /></div>
    <h2 class="spaced">评论</h2><article v-for="c in rows" :key="c.id"><p class="post-body">{{ c.body }}</p><p class="muted">{{ new Date(c.createdAt).toLocaleString() }}</p></article><p v-if="!rows.length">暂无已审核评论。</p><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
    <form @submit.prevent="action('comment')"><label for="communityComment">分享评论</label><textarea id="communityComment" v-model="body" maxlength="2000" required rows="3" :disabled="busy" /><button type="submit" :disabled="busy">提交评论审核</button></form>
    <details class="spaced"><summary>举报不当内容</summary><form @submit.prevent="action('report')"><label for="postReportReason">举报原因</label><textarea id="postReportReason" v-model.trim="reason" maxlength="500" required rows="2" :disabled="busy" /><button class="secondary" type="submit" :disabled="busy">提交举报</button></form></details>
  </article>
</section></template>
