const TOKEN_KEY = 'storems.token'
export const session = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: value => sessionStorage.setItem(TOKEN_KEY, value),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
}

export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.status = status }
}

const messages = {
  'insufficient stock': '库存不足，请刷新库存后重试。',
  'product not found': '商品不存在，请刷新商品列表。',
  'quantity must be greater than 0': '操作数量必须是正整数。',
  'update stock failed': '库存更新失败，请刷新库存核对结果。',
  'failed': '操作未成功，请稍后重试。',
}

export async function request(path, { method = 'GET', body, anonymous = false } = {}) {
  const headers = {}
  const token = session.get()
  if (!anonymous && token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 15000)
  try {
    const response = await fetch(`/api${path}`, {
      method, headers, signal: controller.signal,
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    })
    const text = await response.text()
    let data = text
    try { data = text ? JSON.parse(text) : null } catch { /* Stock API returns text. */ }
    if (!response.ok) {
      if (response.status === 401 && !anonymous && session.get() === token) {
        session.clear()
        window.dispatchEvent(new Event('session-expired'))
      }
      const message = response.status === 401
        ? anonymous ? '用户名或密码错误。' : '登录已失效，请重新登录。'
        : response.status === 409 ? '用户名已存在，请换一个用户名。'
        : response.status === 403 ? '没有管理员权限，无法执行此操作。'
        : response.status === 400 ? '提交的信息不符合要求，请检查后重试。'
        : data?.code === 'PRODUCT_SERVICE_UNAVAILABLE' ? '商品服务暂时不可用，请稍后重试。'
        : response.status >= 500 ? '服务暂时不可用，请稍后重试。'
        : '请求未成功，请稍后重试。'
      throw new ApiError(message, response.status)
    }
    if (typeof data === 'string' && data !== 'success') {
      throw new ApiError(messages[data] || '操作未成功，请刷新数据后核对。', 200)
    }
    return data
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(method === 'GET'
      ? '无法连接服务，请检查网络或稍后重试。'
      : '未能确认操作结果，请先刷新数据核对，不要重复提交。')
  } finally { clearTimeout(timeout) }
}

export async function getList(path) {
  const data = await request(path)
  if (!Array.isArray(data)) throw new ApiError('服务未返回有效列表，请稍后刷新。')
  return data
}
