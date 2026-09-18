import { test, expect } from '@playwright/test'

async function mockApi(page) {
  const state = { stock: 120, records: [], unauthorized: false, fail: false }
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api', '')
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/user/login') return respond({ token: 'test-token', userId: 1, username: 'testuser' })
    if (path === '/user/register') return respond({ userId: 1 }, 201)
    if (state.unauthorized) return respond({}, 401)
    if (path === '/user/me') return respond({ userId: 1, username: 'testuser' })
    if (path === '/user/logout') return route.fulfill({ status: 204 })
    if (state.fail) return respond({}, 503)
    if (path === '/product/queryAllProduct') return respond([{ id: 1, productName: '上衣', price: 100, stock: state.stock }])
    if (path === '/inventory/records') return respond(state.records)
    if (path === '/inventory/inbound' || path === '/inventory/outbound') {
      expect(url.searchParams.has('operator')).toBe(false)
      expect(route.request().headers().authorization).toBe('Bearer test-token')
      const quantity = Number(url.searchParams.get('quantity'))
      const inbound = path.endsWith('inbound')
      state.stock += inbound ? quantity : -quantity
      state.records.unshift({ id: state.records.length + 1, productId: 1, type: inbound ? 'INBOUND' : 'OUTBOUND', quantity, operator: 'testuser', createdAt: new Date().toISOString() })
      return route.fulfill({ body: 'success' })
    }
    return respond({}, 404)
  })
  return state
}

async function login(page) {
  await page.goto('/')
  await page.getByLabel('用户名', { exact: true }).fill('testuser')
  await page.getByLabel('密码', { exact: true }).fill('Test123456')
  await page.getByRole('button', { name: '登录工作台' }).click()
  await expect(page.getByRole('heading', { name: '库存概览' })).toBeVisible()
}

test('register, switch back to login, and login', async ({ page }) => {
  await mockApi(page); await page.goto('/')
  await page.getByRole('button', { name: '立即注册' }).click()
  await page.getByLabel('用户名', { exact: true }).fill('newuser')
  await page.getByLabel('密码', { exact: true }).fill('Test123456')
  await page.getByLabel('确认密码', { exact: true }).fill('Test123456')
  await page.getByRole('button', { name: '创建账号', exact: true }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})

test('inbound, outbound, records, restore session and logout', async ({ page }) => {
  const state = await mockApi(page); await login(page)
  await page.getByRole('navigation').getByRole('button', { name: '商品库存' }).click()
  await page.getByRole('button', { name: '入库', exact: true }).click()
  await page.getByRole('spinbutton').fill('10')
  await page.getByRole('button', { name: '确认入库' }).click()
  await expect(page.locator('.stock-number')).toHaveText('130')
  await page.getByRole('button', { name: '出库', exact: true }).click()
  await page.getByRole('spinbutton').fill('10')
  await page.getByRole('button', { name: '提交出库' }).click()
  await page.getByRole('button', { name: '确认出库', exact: true }).click()
  await expect(page.locator('.stock-number')).toHaveText('120')
  expect(state.records).toHaveLength(2)
  await page.getByRole('navigation').getByRole('button', { name: '库存流水' }).click()
  await expect(page.locator('.el-table__body-wrapper')).toContainText('testuser')
  await page.reload()
  await expect(page.getByRole('heading', { name: '库存概览' })).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('storems.token'))).toBeNull()
})

test('service errors do not display zero stock, expired token returns to login', async ({ page }) => {
  const state = await mockApi(page); state.fail = true; await login(page)
  await expect(page.getByText('服务暂时不可用，请稍后重试。')).toBeVisible()
  await expect(page.locator('.stat-value').first()).toContainText('—')
  state.fail = false; state.unauthorized = true
  await page.getByRole('button', { name: '刷新数据' }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})

test('mobile login and navigation fit the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page); await login(page)
  await expect(page.getByRole('navigation').getByRole('button', { name: '商品库存' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
