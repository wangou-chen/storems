<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request, getList, session } from './api'
const props = defineProps({ currentUser: Object })
const rows = ref([]), loading = ref(false), error = ref(''), search = ref(''), page = ref(1)
const dialog = ref(false), busy = ref(false), target = ref(null), username = ref(''), password = ref(''), confirm = ref(''), formError = ref('')
const filtered = computed(() => rows.value.filter(row => row.username.toLowerCase().includes(search.value.trim().toLowerCase())))
const visible = computed(() => filtered.value.slice((page.value - 1) * 8, page.value * 8))
async function load() {
  loading.value = true; error.value = ''
  try { rows.value = await getList('/user/admin/users'); page.value = 1 }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
function open(row = null) {
  target.value = row; username.value = ''; password.value = ''; confirm.value = ''; formError.value = ''; dialog.value = true
}
async function save() {
  if (busy.value) return
  formError.value = ''
  if (!target.value && !/^[a-zA-Z0-9_]{3,50}$/.test(username.value.trim())) { formError.value = '用户名需为 3–50 位字母、数字或下划线。'; return }
  if (!password.value.trim() || password.value.length < 8 || new TextEncoder().encode(password.value).length > 72) { formError.value = '密码至少 8 位，最多 72 个 UTF-8 字节。'; return }
  if (password.value !== confirm.value) { formError.value = '两次密码不一致。'; return }
  busy.value = true
  try {
    if (target.value) {
      await request(`/user/admin/users/${target.value.id}/password`, { method: 'PUT', body: { password: password.value } })
      ElMessage.success('密码已重置，该用户所有登录凭证已撤销。')
      if (Number(target.value.id) === Number(props.currentUser.userId)) {
        session.clear(); window.dispatchEvent(new Event('session-expired')); return
      }
    } else {
      await request('/user/admin/users', { method: 'POST', body: { username: username.value.trim(), password: password.value } })
      ElMessage.success('操作员账号已创建。')
    }
    dialog.value = false; password.value = ''; confirm.value = ''; await load()
  } catch (e) { formError.value = e.message }
  finally { busy.value = false }
}
async function toggle(row) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm(`${row.enabled ? '禁用后，该用户会立即失去登录权限。' : '启用后，该用户可重新登录。'}确认${row.enabled ? '禁用' : '启用'} ${row.username}？`, '变更账号状态', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    await request(`/user/admin/users/${row.id}/enabled`, { method: 'PUT', body: { enabled: !row.enabled } })
    ElMessage.success('账号状态已更新。'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) }
  finally { busy.value = false }
}
async function removeUser(row) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm(`确认删除账号「${row.username}」？该账号将无法登录，历史流水保留；用户名可供重新注册，新账号不会继承旧登录凭证。`, '删除用户', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', closeOnClickModal: false })
    await request(`/user/admin/users/${row.id}`, { method: 'DELETE' })
    ElMessage.success('用户已删除，用户名可重新注册。'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <section class="panel list-panel">
    <div class="list-toolbar"><div><h2>用户管理</h2><p>管理操作员账号和登录权限，历史流水会保留。</p></div><div><el-button :loading="loading" @click="load">刷新用户</el-button><el-button type="primary" :disabled="busy" @click="open()">创建用户</el-button></div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="data-alert" />
    <el-input v-model="search" placeholder="搜索用户名" clearable class="search-input" style="margin-bottom:20px" @input="page = 1" />
    <el-table :data="visible" v-loading="loading" empty-text="暂无匹配用户">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="角色" min-width="100"><template #default="{ row }">{{ row.role === 'ADMIN' ? '管理员' : '操作员' }}</template></el-table-column>
      <el-table-column label="状态" min-width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template></el-table-column>
      <el-table-column label="注册时间" min-width="180"><template #default="{ row }">{{ row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) : '—' }}</template></el-table-column>
      <el-table-column label="操作" min-width="280"><template #default="{ row }"><el-button :disabled="busy || loading || !!error || (row.role === 'ADMIN' && Number(row.id) !== Number(currentUser.userId))" @click="open(row)">重置密码</el-button><el-button :disabled="busy || loading || !!error || row.role === 'ADMIN'" @click="toggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button><el-button type="danger" plain :disabled="busy || loading || !!error || row.role === 'ADMIN' || Number(row.id) === Number(currentUser.userId)" @click="removeUser(row)">删除</el-button></template></el-table-column>
    </el-table>
    <div class="pagination"><span>共 {{ filtered.length }} 个用户</span><el-pagination v-model:current-page="page" :page-size="8" :total="filtered.length" layout="prev, pager, next" /></div>
    <el-dialog v-model="dialog" :title="target ? `重置密码 · ${target.username}` : '创建操作员账号'" width="min(460px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @closed="password = ''; confirm = ''">
      <form @submit.prevent="save">
        <template v-if="!target"><label class="quantity-label" for="new-username">用户名</label><el-input id="new-username" v-model="username" autocomplete="off" maxlength="50" /></template>
        <label class="quantity-label" for="new-password" style="margin-top:18px">新密码</label><el-input id="new-password" v-model="password" type="password" show-password autocomplete="new-password" />
        <label class="quantity-label" for="confirm-new-password" style="margin-top:18px">确认新密码</label><el-input id="confirm-new-password" v-model="confirm" type="password" show-password autocomplete="new-password" />
        <p v-if="target" class="muted" style="margin-top:16px">保存后撤销该账号所有登录凭证。修改自己的密码后需重新登录。</p>
        <el-alert v-if="formError" :title="formError" type="error" :closable="false" style="margin-top:16px" />
        <div class="dialog-actions"><el-button :disabled="busy" @click="dialog = false">取消</el-button><el-button type="primary" native-type="submit" :loading="busy">保存</el-button></div>
      </form>
    </el-dialog>
  </section>
</template>
