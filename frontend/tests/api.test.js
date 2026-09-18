import { test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { request, getList, session } from '../src/api.js'

const storage = new Map()
globalThis.sessionStorage = {
  getItem: key => storage.get(key) || null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: key => storage.delete(key),
}
globalThis.window = new EventTarget()
beforeEach(() => storage.clear())

test('successful requests send the active Bearer token', async () => {
  session.set('example-token')
  globalThis.fetch = async (path, options) => {
    assert.equal(path, '/api/user/me')
    assert.equal(options.headers.Authorization, 'Bearer example-token')
    return Response.json({ userId: 1, username: 'testuser' })
  }
  assert.equal((await request('/user/me')).username, 'testuser')
})

test('expired session clears token and emits event', async () => {
  session.set('expired-token')
  let count = 0
  const listener = () => count++
  window.addEventListener('session-expired', listener)
  globalThis.fetch = async () => new Response('', { status: 401 })
  await assert.rejects(request('/inventory/records'), error => error.status === 401)
  assert.equal(session.get(), null)
  assert.equal(count, 1)
  window.removeEventListener('session-expired', listener)
})

test('null list response is never treated as empty inventory', async () => {
  globalThis.fetch = async () => Response.json(null)
  await assert.rejects(getList('/product/queryAllProduct'), /有效列表/)
})

test('business failure with HTTP 200 is not reported as success', async () => {
  globalThis.fetch = async () => new Response('insufficient stock')
  await assert.rejects(request('/inventory/outbound', { method: 'POST' }), /库存不足/)
})

test('service failure and uncertain write have clear messages', async () => {
  globalThis.fetch = async () => new Response('', { status: 503 })
  await assert.rejects(request('/product/queryAllProduct'), /服务暂时不可用/)
  globalThis.fetch = async () => { throw new TypeError('network failed') }
  await assert.rejects(request('/inventory/inbound', { method: 'POST' }), /不要重复提交/)
})

test('logout accepts 204 and anonymous login does not send old credentials', async () => {
  session.set('old-token')
  globalThis.fetch = async (_, options) => {
    assert.equal(options.headers.Authorization, undefined)
    return Response.json({ token: 'new-token' })
  }
  await request('/user/login', { method: 'POST', anonymous: true, body: {} })
  globalThis.fetch = async () => new Response(null, { status: 204 })
  assert.equal(await request('/user/logout', { method: 'POST' }), null)
})
