<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import CampusOrnament from '../../campus-shared/CampusOrnament.vue'
import * as api from './api'
const items = ref<api.Post[]>([]), schoolList = ref<{ id: string; name: string }[]>([]), keyword = ref(''), school = ref(''), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
async function load(target = 1) { busy.value = true; message.value = ''; try { const result = await api.posts(target, keyword.value, school.value); items.value = result.items; total.value = result.total; page.value = result.page } catch (e) { message.value = e instanceof Error ? e.message : '帖子加载失败'; items.value = [] } finally { busy.value = false } }
onMounted(async () => { await load(); try { schoolList.value = await api.schools() } catch {} })
</script>
<template><section class="community-page">
  <div class="section-heading"><div><h1><CampusIcon name="pushpin" :size="28" />探店社区</h1><p class="muted">听同学说说，校园周边有哪些值得去的小店。</p></div><RouterLink class="text-link" to="/account/posts">我的帖子与评论 →</RouterLink></div>
  <div class="content-with-rail"><div>
    <form class="inline-form" @submit.prevent="load()"><div><label for="postKeyword">搜索社区帖子</label><input id="postKeyword" v-model.trim="keyword" maxlength="100" placeholder="店名、口味，或一段探店体验" /></div><div><label for="postSchool">校园</label><select id="postSchool" v-model="school"><option value="">全部校园</option><option v-for="s in schoolList" :key="s.id" :value="s.id">{{ s.name }}</option></select></div><button :disabled="busy">查询帖子</button></form>
    <p v-if="message" role="status">{{ message }}</p><div class="feed-caption"><strong>同学的探店手记</strong><span>共 {{ total }} 篇公开分享</span></div>
    <div class="community-feed"><article v-for="p in items" :key="p.id" class="community-entry"><h2><CampusIcon name="book" :size="18" /><RouterLink :to="`/community/posts/${p.id}`">{{ p.title }}</RouterLink></h2><p class="post-preview">{{ p.body }}</p><div class="community-entry-meta"><span>{{ p.likeCount }} 人点赞</span><span>{{ p.commentCount }} 条评论</span><span>{{ p.imageIds.length }} 张图片</span><RouterLink :to="`/community/posts/${p.id}`">阅读分享 →</RouterLink></div></article></div><p v-if="!busy && !items.length" class="notice">暂无公开帖子，欢迎分享探店体验。</p><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
  </div><aside class="reading-rail"><CampusOrnament kind="clip" class="rail-fastener" /><h2>把你的发现，<br />留给下一位同学。</h2><p>一份认真写下的体验，比一句“绝绝子”更有帮助。</p><RouterLink class="text-link" to="/account/posts">写一篇探店手记 →</RouterLink><section><h3>可以聊些什么</h3><ul><li>去了哪里，点了什么</li><li>价格、口味和你的真实感受</li><li>到店前，值得留意的小细节</li></ul><p class="muted">帖子与评论经审核后公开，请尊重他人隐私，不发布敏感个人信息。</p></section><RouterLink class="text-link" to="/app">找找可以领取的优惠 →</RouterLink></aside></div>
</section></template>
