<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request, type PageResponse } from '../../campus-shared/http'
import type { AccountType } from '../../campus-router/portal'
interface Role { id: string; code: string; name: string; accountType: AccountType; version: number; permissions: string[] }
interface Account { id: string; nickname: string; accountType: AccountType; status: string; version: number; roleIds: string[] }
const roles = ref<Role[]>([]), accounts = ref<Account[]>([]), catalogue = ref<{ code: string; name: string }[]>([])
const message = ref(''), busy = ref(false), page = ref(1), total = ref(0)
const rolePage = ref(1), roleTotal = ref(0)
const role = ref<Role | null>(null), account = ref<Account | null>(null)
const code = ref(''), name = ref(''), type = ref<AccountType>('ADMIN'), permissions = ref<string[]>([])
const accountType = ref<AccountType>('USER'), accountStatus = ref('ACTIVE'), grants = ref<string[]>([])
async function load() {
  const [r, a, p] = await Promise.all([request<PageResponse<Role>>(`/admin/roles?page=${rolePage.value}&size=20`), request<PageResponse<Account>>(`/admin/accounts?page=${page.value}&size=20`), request<{ code: string; name: string }[]>('/admin/permissions')])
  roles.value = r.items; roleTotal.value = r.total; accounts.value = a.items; total.value = a.total; catalogue.value = p
}
function editRole(r: Role | null = null) { role.value = r; code.value = r?.code ?? ''; name.value = r?.name ?? ''; type.value = r?.accountType ?? 'ADMIN'; permissions.value = [...(r?.permissions ?? [])] }
function editAccount(a: Account) { account.value = a; accountType.value = a.accountType; accountStatus.value = a.status; grants.value = [...a.roleIds] }
async function run(work: () => Promise<void>) { if (busy.value) return; busy.value = true; message.value = ''; try { await work(); await load(); message.value = '已保存。受影响账号需重新登录。' } catch (e) { message.value = e instanceof Error ? e.message : '操作失败' } finally { busy.value = false } }
async function saveRole() { await run(async () => { const data = { name: name.value.trim(), permissions: type.value === 'ADMIN' ? permissions.value : [] }; if (role.value) await request(`/admin/roles/${role.value.id}`, { method: 'PATCH', body: JSON.stringify({ ...data, version: role.value.version }) }); else await request('/admin/roles', { method: 'POST', body: JSON.stringify({ ...data, code: code.value, accountType: type.value }) }); editRole() }) }
async function deleteRole(r: Role) { if (confirm(`删除角色“${r.name}”？`)) await run(async () => { await request(`/admin/roles/${r.id}?version=${r.version}`, { method: 'DELETE' }); editRole() }) }
async function saveAccount() { const a = account.value; if (!a) return; await run(async () => { editAccount(await request<Account>(`/admin/accounts/${a.id}`, { method: 'PATCH', body: JSON.stringify({ version: a.version, accountType: accountType.value, status: accountStatus.value }) })) }) }
async function saveGrants() { const a = account.value; if (!a) return; await run(async () => { editAccount(await request<Account>(`/admin/accounts/${a.id}/roles`, { method: 'PUT', body: JSON.stringify({ version: a.version, roleIds: grants.value }) })) }) }
async function turn(delta: number) { page.value += delta; try { await load() } catch (e) { message.value = e instanceof Error ? e.message : '加载失败' } }
async function turnRoles(delta: number) { rolePage.value += delta; try { await load() } catch (e) { message.value = e instanceof Error ? e.message : '加载失败' } }
onMounted(async () => { try { await load() } catch (e) { message.value = e instanceof Error ? e.message : '加载失败' } })
</script>
<template>
  <h1>角色与账号权限</h1><p class="muted">账号所属端唯一。门店内的店主、店长和收银员在门店成员管理中维护。</p><p v-if="message" role="status" class="notice">{{ message }}</p>
  <div class="record-grid"><section class="card"><h2>角色目录</h2><button @click="editRole()">新建角色</button><article v-for="r in roles" :key="r.id" class="record"><strong>{{ r.name }}</strong><p>{{ r.code }} · {{ r.accountType }}</p><button @click="editRole(r)">编辑</button> <button :disabled="busy" @click="deleteRole(r)">删除</button></article><div class="actions"><button :disabled="busy || rolePage <= 1" @click="turnRoles(-1)">上一页角色</button><span>{{ rolePage }}</span><button :disabled="busy || rolePage * 20 >= roleTotal" @click="turnRoles(1)">下一页角色</button></div></section>
    <form class="card" @submit.prevent="saveRole"><h2>{{ role ? '修改角色' : '新建角色' }}</h2><label>角色代码<input v-model="code" :disabled="!!role" pattern="[A-Z][A-Z0-9_]{2,63}" maxlength="64" required /></label><label>角色名称<input v-model="name" maxlength="64" required /></label><label>所属端<select v-model="type" :disabled="!!role"><option value="USER">用户端</option><option value="MERCHANT">商家端</option><option value="ADMIN">管理端</option></select></label>
      <fieldset v-if="type === 'ADMIN'"><legend>操作权限</legend><label v-for="p in catalogue" :key="p.code" class="checkbox-row"><input v-model="permissions" type="checkbox" :value="p.code" />{{ p.name }} <small>{{ p.code }}</small></label></fieldset><p v-else class="muted">用户按本人归属、商家按真实门店关系授权，不能赋予其他端的权限。</p><button :disabled="busy">保存角色</button>
    </form></div>
  <h2>账号授权</h2><div class="record-grid"><section class="card"><article v-for="a in accounts" :key="a.id" class="record"><strong>{{ a.nickname }}</strong><p>ID {{ a.id }} · {{ a.accountType }} · {{ a.status }}</p><button @click="editAccount(a)">管理授权</button></article><div class="actions"><button :disabled="page <= 1" @click="turn(-1)">上一页</button><span>{{ page }}</span><button :disabled="page * 20 >= total" @click="turn(1)">下一页</button></div></section>
    <section v-if="account" class="card"><h3>{{ account.nickname }}</h3><p class="muted">不能修改自身授权。变更所属端会清除旧角色并撤销旧会话。</p><label>所属端<select v-model="accountType"><option value="USER">用户端</option><option value="MERCHANT">商家端</option><option value="ADMIN">管理端</option></select></label><label>状态<select v-model="accountStatus"><option value="ACTIVE">正常</option><option value="DISABLED">停用</option></select></label><button :disabled="busy" @click="saveAccount">保存账号状态</button>
      <fieldset><legend>同端角色</legend><p class="muted">下方显示当前角色页，翻页保留已选角色。</p><template v-for="r in roles" :key="r.id"><label v-if="r.accountType === account.accountType" class="checkbox-row"><input v-model="grants" type="checkbox" :value="r.id" />{{ r.name }}</label></template></fieldset><button :disabled="busy" @click="saveGrants">保存角色分配</button>
    </section></div>
</template>
