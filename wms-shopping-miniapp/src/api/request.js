// API 基础配置与请求封装（小程序 / H5 通用）
// 优先级：本地存储 wms_api_base（设置页可改，重进小程序生效）> 构建期 VITE_API_BASE > DEV 默认本地地址
const ENV_BASE = import.meta.env.VITE_API_BASE || ''
const TOKEN_KEY = 'wms_token'
const USER_KEY = 'wms_user'
const IDEMPOTENCY_CACHE_KEY = 'wms_idempotency_pending'

export function getBaseUrl() {
  return uni.getStorageSync('wms_api_base') || ENV_BASE || (import.meta.env.DEV ? 'http://localhost:8088/api/v1' : '')
}

function sortForHash(value) {
  if (Array.isArray(value)) return value.map(sortForHash)
  if (value && typeof value === 'object') return Object.keys(value).sort().reduce((out, key) => { out[key] = sortForHash(value[key]); return out }, {})
  return value
}

// 幂等 pending 表：内存缓存 + 惰性落盘，避免每个写请求同步双 IO
let idempotencyCache = null
function loadPending() {
  if (idempotencyCache === null) idempotencyCache = uni.getStorageSync(IDEMPOTENCY_CACHE_KEY) || {}
  return idempotencyCache
}
function savePending() { uni.setStorageSync(IDEMPOTENCY_CACHE_KEY, idempotencyCache || {}) }

function requestIdentity(method, url, data) {
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) return null
  const input = `${method.toUpperCase()}:${url}:${JSON.stringify(sortForHash(data == null ? null : data)) || 'null'}`
  let a = 2166136261, b = 2246822519
  for (let i = 0; i < input.length; i++) { const code = input.charCodeAt(i); a = Math.imul(a ^ code, 16777619); b = Math.imul(b ^ code, 3266489917) }
  const scope = `${method.toUpperCase()}:${url}:${(a >>> 0).toString(36)}${(b >>> 0).toString(36)}`
  const pending = loadPending()
  // 上限保护：只增不减会撑爆 storage——按插入序淘汰最旧条目（而非整表清空，避免抹掉在途请求的幂等键）
  const keys = Object.keys(pending)
  while (keys.length >= 50) { delete pending[keys.shift()] }
  let key = pending[scope]
  if (!key) { key = `wms-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`; pending[scope] = key; savePending() }
  return { scope, key }
}

function clearRequestIdentity(identity) {
  if (!identity) return
  const pending = loadPending()
  if (pending[identity.scope] === identity.key) { delete pending[identity.scope]; savePending() }
}

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
  uni.removeStorageSync(IDEMPOTENCY_CACHE_KEY)
  idempotencyCache = null
}

export function getUser() {
  const s = uni.getStorageSync(USER_KEY)
  return s ? JSON.parse(s) : null
}

export function setUser(user) {
  uni.setStorageSync(USER_KEY, JSON.stringify(user))
}

class RequestError extends Error {
  constructor(message, code, response) {
    super(message)
    this.name = 'RequestError'
    this.code = code
    this.response = response
  }
}

const LOGIN_ROUTES = ['/pages/login/login']

function redirectToLogin() {
  if (getCurrentPages().length === 0) return
  const current = getCurrentPages()[getCurrentPages().length - 1]
  if (current && LOGIN_ROUTES.some(r => current.route.includes(r))) return
  uni.reLaunch({ url: '/pages/login/login' })
}

// 同一 token 批次的 401 只清一次登出，防页面并发多请求重复 reLaunch（对齐 wms-web client.js 约定）
let lastUnauthorizedAuth = null

export function request(options) {
  const { url, method = 'GET', data, header = {}, responseType } = options
  const BASE_URL = getBaseUrl()
  if (!BASE_URL) return Promise.reject(new RequestError('未配置生产 API 地址，请使用 VITE_API_BASE 重新构建', -2))
  const token = getToken()
  const opts = {
    url: BASE_URL + url,
    method,
    data,
    header: { 'Content-Type': 'application/json', ...header },
    timeout: 15000,
  }
  // wx.request 的 responseType 只接受 text/arraybuffer，传 'json' 属非法枚举，会导致部分基础库上 success 回调不触发
  if (responseType === 'arraybuffer') opts.responseType = 'arraybuffer'
  const identity = requestIdentity(method, url, data)
  if (identity && !opts.header['Idempotency-Key']) opts.header['Idempotency-Key'] = identity.key
  if (token) opts.header.Authorization = `Bearer ${token}`
  return new Promise((resolve, reject) => {
    uni.request({
      ...opts,
      success: (res) => {
        if (res.statusCode === 401) {
          const currentToken = token
          if (lastUnauthorizedAuth !== currentToken) {
            lastUnauthorizedAuth = currentToken
            clearAuth()
            redirectToLogin()
          }
          return reject(new RequestError('登录已过期，请重新登录', 401, res))
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          // 后端明确拒绝 = 业务终态，释放幂等键（网络中断时保留键以支持安全重试）
          clearRequestIdentity(identity)
          const msg = (res.data && res.data.message) || `请求失败（${res.statusCode}）`
          return reject(new RequestError(msg, res.statusCode, res.data))
        }
        const body = res.data
        // 后端统一为 ApiResponse：{ code, message, data } —— 兼容直接返回
        if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
          if (body.code != null && body.code !== 200 && body.code !== 0) {
            clearRequestIdentity(identity)
            return reject(new RequestError(body.message || '业务异常', body.code, body))
          }
          clearRequestIdentity(identity)
          return resolve(body.data)
        }
        clearRequestIdentity(identity)
        resolve(body)
      },
      fail: (err) => reject(new RequestError(err.errMsg || '网络错误', -1, err)),
    })
  })
}

// get 第二参是查询参数对象：必须落到 data 才会被 wx.request 转成 query string
// （此前展开进 options 顶层被 request 解构丢弃，导致搜索/分页/状态筛选/价格口径等所有 GET 参数静默失效）
export const http = { get: (u, params) => request({ url: u, method: 'GET', data: params }),
  post: (u, d, o) => request({ ...o, url: u, method: 'POST', data: d }),
  put: (u, d, o) => request({ ...o, url: u, method: 'PUT', data: d }),
  delete: (u, o) => request({ ...o, url: u, method: 'DELETE' }) }
