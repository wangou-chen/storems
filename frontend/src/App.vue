<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box, Grid, List, Search, Refresh, Plus, Minus, Right, SwitchButton, User, Lock, ArrowRight, TrendCharts, Document } from '@element-plus/icons-vue'
import { request, getList, session } from './api'
import UserManagement from './UserManagement.vue'

const user = ref(null)
const restoring = ref(Boolean(session.get()))
const restoreError = ref('')
const authMode = ref('login')
const auth = reactive({ username: '', password: '', confirm: '' })
const authBusy = ref(false)
const authError = ref('')
const tab = ref('overview')
const products = ref([])
const records = ref([])
const loading = ref(false)
const loadError = ref('')
const updatedAt = ref(null)
const search = ref('')
const recordSearch = ref('')
const recordType = ref('')
const recordDates = ref([])
const productPage = ref(1)
const recordPage = ref(1)
const pageSize = 8
const dialog = ref(false)
const operation = reactive({ type: 'INBOUND', product: null, quantity: 1 })
const submitting = ref(false)
const operationError = ref('')
const logoutBusy = ref(false)
let generation = 0

const title = computed(() => ({ overview: '库存概览', products: '商品库存', records: '库存流水', users: '用户管理' })[tab.value])
const totalStock = computed(() => products.value.reduce((sum, p) => sum + Number(p.stock), 0))
const unavailable = computed(() => products.value.filter(p => Number(p.stock) === 0).length)
const productNames = computed(() => new Map(products.value.map(p => [Number(p.id), p.productName])))
const filteredProducts = computed(() => products.value.filter(p => `${p.productName} ${p.id}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const visibleProducts = computed(() => filteredProducts.value.slice((productPage.value - 1) * pageSize, productPage.value * pageSize))
const filteredRecords = computed(() => records.value.filter(r => {
  const keyword = recordSearch.value.trim().toLowerCase()
  const matches = `${productNames.value.get(Number(r.productId)) || ''} ${r.productId} ${r.operator || ''}`.toLowerCase().includes(keyword)
  const timestamp = new Date(r.createdAt).getTime()
  const dates = recordDates.value
  const inRange = !dates?.length || (timestamp >= new Date(dates[0]).setHours(0, 0, 0, 0) && timestamp <= new Date(dates[1]).setHours(23, 59, 59, 999))
  return matches && (!recordType.value || r.type === recordType.value) && inRange
}))
const visibleRecords = computed(() => filteredRecords.value.slice((recordPage.value - 1) * pageSize, recordPage.value * pageSize))
const todayCount = computed(() => records.value.filter(r => new Date(r.createdAt).toDateString() === new Date().toDateString()).length)
const topProducts = computed(() => [...products.value].sort((a, b) => b.stock - a.stock).slice(0, 5))
const maxStock = computed(() => Math.max(...topProducts.value.map(p => Number(p.stock)), 1))
const dateText = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const formatNumber = value => Number(value).toLocaleString('zh-CN')
const price = value => Number(value).toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' })

function clearSession() {
  generation++
  session.clear(); user.value = null; products.value = []; records.value = []
  updatedAt.value = null; dialog.value = false; restoreError.value = ''; auth.password = ''; auth.confirm = ''
}
function expired() { clearSession(); authMode.value = 'login'; authError.value = '登录已失效，请重新登录。' }

async function refresh() {
  if (loading.value) return
  const current = generation
  loading.value = true; loadError.value = ''
  try {
    const results = await Promise.allSettled([
      getList('/product/queryAllProduct'), getList('/inventory/records'),
    ])
    const labels = ['商品查询', '库存流水查询']
    const failures = results.flatMap((result, index) => result.status === 'rejected'
      ? [`${labels[index]}失败：${result.reason.message}`] : [])
    if (failures.length) throw new Error(failures.join('；'))
    const [nextProducts, nextRecords] = results.map(result => result.value)
    if (nextProducts.some(p => p.stock == null || !Number.isFinite(Number(p.stock)))) throw new Error('商品库存数据不完整，请稍后重试。')
    if (current !== generation) return
    products.value = nextProducts; records.value = [...nextRecords].sort((a, b) => b.id - a.id)
    updatedAt.value = new Date(); productPage.value = 1; recordPage.value = 1
  } catch (error) { if (current === generation) loadError.value = error.message }
  finally { loading.value = false }
}

async function restore() {
  restoring.value = true; restoreError.value = ''
  try {
    const me = await request('/user/me')
    if (!me?.username || !me?.userId) throw new Error('用户信息异常，请重新登录。')
    user.value = me; await refresh()
  } catch (error) { if (session.get()) restoreError.value = error.message }
  finally { restoring.value = false }
}

async function authenticate() {
  if (authBusy.value) return
  authError.value = ''
  const username = auth.username.trim()
  if (!/^[a-zA-Z0-9_]{3,50}$/.test(username)) { authError.value = '用户名需为 3–50 位字母、数字或下划线。'; return }
  if (auth.password.length < 8 || new TextEncoder().encode(auth.password).length > 72 || !auth.password.trim()) { authError.value = '密码至少 8 位，且不超过 72 个 UTF-8 字节。'; return }
  if (authMode.value === 'register' && auth.password !== auth.confirm) { authError.value = '两次输入的密码不一致。'; return }
  authBusy.value = true
  try {
    const body = { username, password: auth.password }
    if (authMode.value === 'register') {
      await request('/user/register', { method: 'POST', body, anonymous: true })
      ElMessage.success('注册成功，请使用新账号登录。')
      authMode.value = 'login'; auth.password = ''; auth.confirm = ''
    } else {
      const result = await request('/user/login', { method: 'POST', body, anonymous: true })
      if (!result?.token || !result?.username) throw new Error('登录响应异常，请重试。')
      session.set(result.token); user.value = { userId: result.userId, username: result.username, role: result.role }
      auth.password = ''; tab.value = 'overview'; await refresh()
    }
  } catch (error) { authError.value = error.message }
  finally { authBusy.value = false }
}

async function logout() {
  if (logoutBusy.value) return
  logoutBusy.value = true
  try { await request('/user/logout', { method: 'POST' }); clearSession(); ElMessage.success('已安全退出。') }
  catch (error) { if (error.status !== 401) ElMessage.error('退出未完成，请稍后重试。') }
  finally { logoutBusy.value = false }
}

function openOperation(type, product) {
  operation.type = type; operation.product = product; operation.quantity = 1
  operationError.value = ''; dialog.value = true
}

async function submitOperation() {
  if (submitting.value) return
  operationError.value = ''
  const quantity = Number(operation.quantity)
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 2147483647) { operationError.value = '请输入有效的正整数。'; return }
  if (operation.type === 'OUTBOUND' && quantity > operation.product.stock) { operationError.value = '出库数量不能超过当前显示库存，请刷新后重试。'; return }
  if (operation.type === 'INBOUND' && quantity + Number(operation.product.stock) > 2147483647) { operationError.value = '入库后数量超出库存范围。'; return }
  submitting.value = true
  try {
    if (operation.type === 'OUTBOUND') {
      await ElMessageBox.confirm(`确认将「${operation.product.productName}」出库 ${quantity} 件？`, '确认出库', { confirmButtonText: '确认出库', cancelButtonText: '取消', type: 'warning' })
    }
    const action = operation.type === 'INBOUND' ? 'inbound' : 'outbound'
    const params = new URLSearchParams({ productId: operation.product.id, quantity })
    const result = await request(`/inventory/${action}?${params}`, { method: 'POST' })
    if (result !== 'success') throw new Error('未收到明确的操作结果，请刷新数据核对。')
    dialog.value = false; ElMessage.success(`${operation.type === 'INBOUND' ? '入库' : '出库'}成功，已生成流水。`)
    await refresh()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      operationError.value = error.message
      // A transport failure may occur after a write: close the form and refresh before retrying.
      if (error.status === 0 || error.status >= 500) {
        dialog.value = false; ElMessage.warning(error.message); await refresh()
      }
    }
  } finally { submitting.value = false }
}

onMounted(() => { window.addEventListener('session-expired', expired); if (session.get()) restore() })
onUnmounted(() => window.removeEventListener('session-expired', expired))
</script>

<template>
  <div v-if="restoring || restoreError" class="restore-screen">
    <div class="brand-mark"><el-icon><Box /></el-icon></div>
    <h2>{{ restoring ? '正在恢复工作台…' : '暂时无法连接工作台' }}</h2>
    <p>{{ restoreError || '正在确认你的登录状态' }}</p>
    <el-button v-if="restoreError" type="primary" @click="restore">重新连接</el-button>
  </div>

  <main v-else-if="!user" class="auth-shell">
    <section class="auth-story">
      <a class="brand" href="#"><span class="brand-mark"><el-icon><Box /></el-icon></span><span>仓序<span class="brand-en">STOREMS</span></span></a>
      <div class="auth-story-content"><span class="eyebrow">让每一件商品，都有迹可循</span><h1>库存有数。<br />管理有序。</h1><p>从每一次入库，到每一笔出库。<br />在一个清晰的工作台里，掌握仓库的日常。</p>
        <div class="warehouse-art" aria-hidden="true"><div class="rack"><i /><i /><i /><i /><i /><i /></div><span class="art-label">INVENTORY, IN ORDER.</span><div class="art-dot" /></div>
      </div><span class="auth-foot">仓序 · 仓库管理系统</span>
    </section>
    <section class="auth-panel"><div class="auth-card"><span class="eyebrow dark">你的仓库工作台</span><h2>{{ authMode === 'login' ? '欢迎回来' : '创建新账号' }}</h2><p class="muted">{{ authMode === 'login' ? '登录后，开始管理今天的库存。' : '注册后即可登录，进行库存管理。' }}</p>
      <form @submit.prevent="authenticate">
        <label for="username">用户名</label><el-input id="username" v-model="auth.username" :prefix-icon="User" placeholder="请输入用户名" autocomplete="username" maxlength="50" size="large" />
        <label for="password">密码</label><el-input id="password" v-model="auth.password" :prefix-icon="Lock" type="password" show-password placeholder="请输入至少 8 位密码" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" size="large" />
        <template v-if="authMode === 'register'"><label for="confirm">确认密码</label><el-input id="confirm" v-model="auth.confirm" type="password" show-password placeholder="请再次输入密码" autocomplete="new-password" size="large" /></template>
        <el-alert v-if="authError" :title="authError" type="error" :closable="false" show-icon class="form-alert" />
        <el-button native-type="submit" type="primary" size="large" :loading="authBusy" class="auth-submit">{{ authMode === 'login' ? '登录工作台' : '创建账号' }}<el-icon class="button-icon"><Right /></el-icon></el-button>
      </form>
      <p class="auth-switch">{{ authMode === 'login' ? '还没有账号？' : '已有账号？' }} <button type="button" :disabled="authBusy" @click="authMode = authMode === 'login' ? 'register' : 'login'; authError = ''; auth.password = ''; auth.confirm = ''">{{ authMode === 'login' ? '立即注册' : '返回登录' }}</button></p>
      <div class="auth-note"><el-icon><Lock /></el-icon>登录凭证有效期为 2 小时，请在使用后退出登录。</div>
    </div></section>
  </main>

  <div v-else class="workspace">
    <aside class="sidebar"><div class="brand"><span class="brand-mark"><el-icon><Box /></el-icon></span><span>仓序<span class="brand-en">STOREMS</span></span></div><div class="sidebar-caption">仓库工作台</div>
      <nav aria-label="主导航"><button v-for="item in [{ id: 'overview', label: '库存概览', icon: Grid }, { id: 'products', label: '商品库存', icon: Box }, { id: 'records', label: '库存流水', icon: List }]" :key="item.id" :class="{ active: tab === item.id }" @click="tab = item.id"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}<span v-if="tab === item.id" class="nav-dot" /></button><button v-if="user.role === 'ADMIN'" :class="{ active: tab === 'users' }" @click="tab = 'users'"><el-icon><User /></el-icon>用户管理<span v-if="tab === 'users'" class="nav-dot" /></button></nav>
      <div class="sidebar-note"><span class="note-symbol">↗</span><p>每一次流转<br />都有清晰记录</p><span>从库存到流水，井然有序。</span></div><div class="sidebar-bottom"><span class="status-dot" />库存管理 · 工作空间</div>
    </aside>
    <div class="main-shell"><header class="topbar"><div class="breadcrumb">工作台 <el-icon><ArrowRight /></el-icon><strong>{{ title }}</strong></div><div class="account"><span class="avatar">{{ user.username.slice(0, 1).toUpperCase() }}</span><span>{{ user.username }}</span><el-button text :icon="SwitchButton" :loading="logoutBusy" @click="logout">退出登录</el-button></div></header>
      <main class="main-content"><div class="page-heading"><div><div class="eyebrow dark">{{ tab === 'overview' ? dateText : 'WAREHOUSE MANAGEMENT' }}</div><h1>{{ title }}</h1><p>{{ tab === 'overview' ? '掌握库存全貌，让每一次流转心中有数。' : tab === 'products' ? '查看当前库存，快速完成商品出入库。' : '追溯每一笔库存变动，让操作有据可查。' }}</p></div><el-button :icon="Refresh" :loading="loading" @click="refresh">刷新数据</el-button></div>
        <el-alert v-if="loadError" :title="loadError" :description="updatedAt ? '当前保留上次成功加载的数据，出入库已暂停，请刷新重试。' : '尚未获取有效数据，请点击刷新重试。'" type="error" show-icon :closable="false" class="data-alert" />

        <UserManagement v-if="tab === 'users' && user.role === 'ADMIN'" :current-user="user" />
        <template v-if="tab === 'overview'">
          <section class="stats-grid" aria-label="库存统计"><article v-for="(stat, index) in [{ label: '商品种类', value: products.length, unit: '种', icon: Box, hint: '当前在库商品档案' }, { label: '库存总量', value: totalStock, unit: '件', icon: TrendCharts, hint: '全部商品库存合计' }, { label: '今日操作', value: todayCount, unit: '笔', icon: Document, hint: '今日出入库流水' }, { label: '零库存商品', value: unavailable, unit: '种', icon: List, hint: '可在商品页安排补货' }]" :key="stat.label" class="stat-card" :class="{ featured: index === 1 }"><div class="stat-label">{{ stat.label }}<el-icon><component :is="stat.icon" /></el-icon></div><div class="stat-value">{{ updatedAt ? formatNumber(stat.value) : '—' }}<span>{{ stat.unit }}</span></div><div class="stat-hint">{{ stat.hint }}</div></article></section>
          <div class="overview-grid"><section class="panel stock-distribution"><div class="panel-heading"><div><h2>库存分布</h2><p>库存数量最多的 5 种商品</p></div><span class="small-label">单位 / 件</span></div><div v-if="!updatedAt" class="blank-state">{{ loading ? '正在加载库存…' : '等待加载数据' }}</div><el-empty v-else-if="!topProducts.length" description="暂无商品" :image-size="70" /><div v-else class="stock-bars"><div v-for="p in topProducts" :key="p.id" class="stock-bar"><div><span>{{ p.productName }}</span><strong>{{ formatNumber(p.stock) }}</strong></div><div class="bar-track"><span :style="{ width: `${Number(p.stock) / maxStock * 100}%` }" /></div></div></div><button class="text-link" @click="tab = 'products'">查看全部商品 <el-icon><Right /></el-icon></button></section>
            <section class="quick-panel"><span class="eyebrow">日常操作</span><h2>货物进出，<br />一步到位。</h2><p>选择商品并填写数量，<br />系统自动记录操作人和时间。</p><el-button @click="tab = 'products'" :icon="Box">前往商品库存 <el-icon><Right /></el-icon></el-button><div class="quick-decoration" aria-hidden="true">↗</div></section></div>
          <section class="panel"><div class="panel-heading"><div><h2>最近流水</h2><p>最新的库存变动记录</p></div><button class="text-link" @click="tab = 'records'">全部流水 <el-icon><Right /></el-icon></button></div><el-table :data="records.slice(0, 5)" :empty-text="updatedAt ? '暂无出入库记录' : '尚未加载数据'"><el-table-column label="商品" min-width="150"><template #default="{ row }"><span class="product-name">{{ productNames.get(Number(row.productId)) || `商品 #${row.productId}` }}</span></template></el-table-column><el-table-column label="类型" width="100"><template #default="{ row }"><span :class="['type-badge', row.type === 'INBOUND' ? 'in' : 'out']">{{ row.type === 'INBOUND' ? '入库' : '出库' }}</span></template></el-table-column><el-table-column label="数量" width="100"><template #default="{ row }"><strong :class="row.type === 'INBOUND' ? 'positive' : 'negative'">{{ row.type === 'INBOUND' ? '+' : '−' }}{{ row.quantity }}</strong></template></el-table-column><el-table-column prop="operator" label="操作人" min-width="110" /><el-table-column label="操作时间" min-width="180"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column></el-table></section>
        </template>

        <section v-if="tab === 'products'" class="panel list-panel"><div class="list-toolbar"><div><h2>全部商品 <span class="count-badge">{{ updatedAt ? products.length : '—' }}</span></h2><p>库存数量以最近一次刷新为准</p></div><el-input v-model="search" :prefix-icon="Search" placeholder="搜索商品名称或 ID" clearable class="search-input" @input="productPage = 1" /></div><el-table :data="visibleProducts" v-loading="loading" :empty-text="updatedAt ? '没有匹配的商品' : '尚未加载数据'"><el-table-column label="商品" min-width="190"><template #default="{ row }"><div class="product-cell"><span class="product-icon"><el-icon><Box /></el-icon></span><div><strong>{{ row.productName }}</strong><small>ID · {{ String(row.id).padStart(4, '0') }}</small></div></div></template></el-table-column><el-table-column label="单价" min-width="110"><template #default="{ row }">{{ price(row.price) }}</template></el-table-column><el-table-column label="当前库存" min-width="110"><template #default="{ row }"><strong class="stock-number">{{ formatNumber(row.stock) }}</strong><span class="unit"> 件</span></template></el-table-column><el-table-column label="库存状态" min-width="110"><template #default="{ row }"><span :class="['stock-status', Number(row.stock) === 0 ? 'empty' : 'available']"><i />{{ Number(row.stock) === 0 ? '暂无库存' : '有库存' }}</span></template></el-table-column><el-table-column label="操作" min-width="185" align="right"><template #default="{ row }"><el-button :icon="Plus" :disabled="loading || !!loadError" @click="openOperation('INBOUND', row)">入库</el-button><el-button :icon="Minus" :disabled="loading || !!loadError || Number(row.stock) === 0" @click="openOperation('OUTBOUND', row)">出库</el-button></template></el-table-column></el-table><div class="pagination"><span>共 {{ filteredProducts.length }} 种商品</span><el-pagination v-model:current-page="productPage" :page-size="pageSize" :total="filteredProducts.length" layout="prev, pager, next" /></div></section>

        <section v-if="tab === 'records'" class="panel list-panel"><div class="list-toolbar"><div><h2>出入库明细</h2><p>操作人由登录身份自动记录</p></div><span class="small-label">共 {{ records.length }} 条记录</span></div><div class="record-filters"><el-input v-model="recordSearch" :prefix-icon="Search" placeholder="搜索商品 / 操作人" clearable @input="recordPage = 1" /><el-select v-model="recordType" placeholder="全部类型" clearable @change="recordPage = 1"><el-option label="入库" value="INBOUND" /><el-option label="出库" value="OUTBOUND" /></el-select><el-date-picker v-model="recordDates" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" @change="recordPage = 1" /></div><el-table :data="visibleRecords" v-loading="loading" :empty-text="updatedAt ? '没有匹配的流水' : '尚未加载数据'"><el-table-column prop="id" label="流水号" width="90" /><el-table-column label="商品" min-width="150"><template #default="{ row }"><strong>{{ productNames.get(Number(row.productId)) || `商品 #${row.productId}` }}</strong><span class="unit"> #{{ row.productId }}</span></template></el-table-column><el-table-column label="类型" width="90"><template #default="{ row }"><span :class="['type-badge', row.type === 'INBOUND' ? 'in' : 'out']">{{ row.type === 'INBOUND' ? '入库' : '出库' }}</span></template></el-table-column><el-table-column label="数量" width="90"><template #default="{ row }"><strong :class="row.type === 'INBOUND' ? 'positive' : 'negative'">{{ row.type === 'INBOUND' ? '+' : '−' }}{{ row.quantity }}</strong></template></el-table-column><el-table-column prop="operator" label="操作人" min-width="120" /><el-table-column label="操作时间" min-width="185"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column></el-table><div class="pagination"><span>筛选结果 {{ filteredRecords.length }} 条</span><el-pagination v-model:current-page="recordPage" :page-size="pageSize" :total="filteredRecords.length" layout="prev, pager, next" /></div></section>
        <footer class="page-footer"><span>仓序 STOREMS · 让管理井然有序</span><span>{{ updatedAt ? `最近更新 ${updatedAt.toLocaleTimeString('zh-CN', { hour12: false })}` : '等待数据同步' }}</span></footer>
      </main>
    </div>
    <el-dialog v-model="dialog" :title="operation.type === 'INBOUND' ? '商品入库' : '商品出库'" width="min(460px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting"><div v-if="operation.product" class="operation-product"><span class="product-icon"><el-icon><Box /></el-icon></span><div><strong>{{ operation.product.productName }}</strong><p>当前库存 {{ formatNumber(operation.product.stock) }} 件</p></div></div><form @submit.prevent="submitOperation"><label class="quantity-label" for="quantity">{{ operation.type === 'INBOUND' ? '入库数量' : '出库数量' }}</label><el-input-number id="quantity" v-model="operation.quantity" :min="1" :max="operation.type === 'OUTBOUND' ? Number(operation.product?.stock) : 2147483647 - Number(operation.product?.stock || 0)" :precision="0" :disabled="submitting" size="large" /><p class="muted operation-user">操作人：{{ user.username }} · 自动记录</p><el-alert v-if="operationError" :title="operationError" type="error" :closable="false" show-icon /><div class="dialog-actions"><el-button :disabled="submitting" @click="dialog = false">取消</el-button><el-button type="primary" native-type="submit" :loading="submitting">{{ operation.type === 'INBOUND' ? '确认入库' : '提交出库' }}</el-button></div></form></el-dialog>
  </div>
</template>
