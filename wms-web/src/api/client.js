import axios from 'axios'
import { getStorage, removeStorage } from '../utils/storage'

const client = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api/v1', timeout: 15000 })
const IDEMPOTENCY_CACHE_KEY = 'wms_idempotency_pending'

function stable(value) {
  if (Array.isArray(value)) return value.map(stable)
  if (value && typeof value === 'object' && !(value instanceof FormData)) return Object.keys(value).sort().reduce((out, key) => { out[key] = stable(value[key]); return out }, {})
  return value
}

function requestIdentity(config) {
  const method = (config.method || 'get').toUpperCase()
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) return null
  const input = `${method}:${config.url}:${JSON.stringify(stable(config.data == null ? null : config.data)) || 'null'}`
  let a = 2166136261, b = 2246822519
  for (let i = 0; i < input.length; i++) { const code = input.charCodeAt(i); a = Math.imul(a ^ code, 16777619); b = Math.imul(b ^ code, 3266489917) }
  const scope = `${method}:${config.url}:${(a >>> 0).toString(36)}${(b >>> 0).toString(36)}`
  const pending = JSON.parse(sessionStorage.getItem(IDEMPOTENCY_CACHE_KEY) || '{}')
  let key = pending[scope]
  if (!key) { key = `wms-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`; pending[scope] = key; sessionStorage.setItem(IDEMPOTENCY_CACHE_KEY, JSON.stringify(pending)) }
  return { scope, key }
}

function clearRequestIdentity(identity) {
  if (!identity) return
  const pending = JSON.parse(sessionStorage.getItem(IDEMPOTENCY_CACHE_KEY) || '{}')
  delete pending[identity.scope]
  sessionStorage.setItem(IDEMPOTENCY_CACHE_KEY, JSON.stringify(pending))
}

let onUnauthorized = () => {}

// L6：401 并发风暴防重——token 过期时页面往往并发多请求、同时收到一串 401，
// 若每个都清 token + 触发跳转，回跳地址会被反复覆写、弹窗/跳转执行 N 次。
// 记录首个 401 请求的 Authorization：同一 token 批次的后续 401 直接跳过；
// 换了 token（重新登录后）再遇 401 则开启新一轮处理，不会漏踢。
let handlingUnauthorized = false
let lastUnauthorizedAuth = ''

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

client.interceptors.request.use((config) => {
  const token = getStorage('wms_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  config.__idempotencyIdentity = requestIdentity(config)
  if (config.__idempotencyIdentity) config.headers['Idempotency-Key'] = config.headers['Idempotency-Key'] || config.__idempotencyIdentity.key
  return config
})

function parseBlobError(blob) {
  return new Promise((resolve) => {
    if (blob.type && blob.type.includes('json')) {
      const reader = new FileReader()
      reader.onload = () => {
        try { resolve(JSON.parse(reader.result)) } catch { resolve(null) }
      }
      reader.onerror = () => resolve(null)
      reader.readAsText(blob)
    } else {
      resolve(null)
    }
  })
}

client.interceptors.response.use(
  async ({ data, config }) => {
    if (config.responseType === 'blob') {
      if (data instanceof Blob && data.size > 0) {
        const err = await parseBlobError(data)
        if (err && err.message) return Promise.reject(new Error(err.message))
      }
      return data
    }
    if (data.code !== 200) return Promise.reject(new Error(data.message || '请求失败'))
    clearRequestIdentity(config.__idempotencyIdentity)
    return data.data
  },
  (error) => {
    if (error.response?.status === 401) {
      const auth = String(error.config?.headers?.Authorization || '')
      if (!handlingUnauthorized || auth !== lastUnauthorizedAuth) {
        handlingUnauthorized = true
        lastUnauthorizedAuth = auth
        removeStorage('wms_token')
        removeStorage('wms_user')
        onUnauthorized()
      }
    }
    return Promise.reject(new Error(error.response?.data?.message || (error.response?.status === 401 ? '登录已失效，请重新登录' : error.message) || '网络异常'))
  },
)
export default client
