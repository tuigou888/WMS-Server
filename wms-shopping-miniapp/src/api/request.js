// API 基础配置与请求封装（小程序 / H5 通用）
// 部署时可用环境变量 VITE_API_BASE（H5 构建期注入）或本地存储 wms_api_base 覆盖
const ENV_BASE = import.meta.env.VITE_API_BASE || ''
const STORED_BASE = uni.getStorageSync('wms_api_base')
const BASE_URL = STORED_BASE || ENV_BASE || (import.meta.env.DEV ? 'http://localhost:8088/api/v1' : '')
const TOKEN_KEY = 'wms_token'
const USER_KEY = 'wms_user'
const IDEMPOTENCY_CACHE_KEY = 'wms_idempotency_pending'

function sortForHash(value) {
  if (Array.isArray(value)) return value.map(sortForHash)
  if (value && typeof value === 'object') return Object.keys(value).sort().reduce((out, key) => { out[key] = sortForHash(value[key]); return out }, {})
  return value
}

function requestIdentity(method, url, data) {
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) return null
  const input = `${method.toUpperCase()}:${url}:${JSON.stringify(sortForHash(data == null ? null : data)) || 'null'}`
  let a = 2166136261, b = 2246822519
  for (let i = 0; i < input.length; i++) { const code = input.charCodeAt(i); a = Math.imul(a ^ code, 16777619); b = Math.imul(b ^ code, 3266489917) }
  const scope = `${method.toUpperCase()}:${url}:${(a >>> 0).toString(36)}${(b >>> 0).toString(36)}`
  const pending = uni.getStorageSync(IDEMPOTENCY_CACHE_KEY) || {}
  let key = pending[scope]
  if (!key) { key = `wms-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`; pending[scope] = key; uni.setStorageSync(IDEMPOTENCY_CACHE_KEY, pending) }
  return { scope, key }
}

function clearRequestIdentity(identity) {
  if (!identity) return
  const pending = uni.getStorageSync(IDEMPOTENCY_CACHE_KEY) || {}
  delete pending[identity.scope]
  uni.setStorageSync(IDEMPOTENCY_CACHE_KEY, pending)
}

export function getBaseUrl() { return BASE_URL }

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
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

export function request(options) {
  const { url, method = 'GET', data, header = {}, responseType } = options
  if (!BASE_URL) return Promise.reject(new RequestError('未配置生产 API 地址，请使用 VITE_API_BASE 重新构建', -2))
  const token = getToken()
  const opts = {
    url: BASE_URL + url,
    method,
    data,
    header: { 'Content-Type': 'application/json', ...header },
    responseType: responseType || 'json',
    timeout: 15000,
  }
  const identity = requestIdentity(method, url, data)
  if (identity && !opts.header['Idempotency-Key']) opts.header['Idempotency-Key'] = identity.key
  if (token) opts.header.Authorization = `Bearer ${token}`
  return new Promise((resolve, reject) => {
    uni.request({
      ...opts,
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuth()
          redirectToLogin()
          return reject(new RequestError('登录已过期，请重新登录', 401, res))
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const msg = (res.data && res.data.message) || `请求失败（${res.statusCode}）`
          return reject(new RequestError(msg, res.statusCode, res.data))
        }
        const body = res.data
        // 后端统一为 ApiResponse：{ code, message, data } —— 兼容直接返回
        if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
          if (body.code != null && body.code !== 200 && body.code !== 0) {
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

export const http = { get: (u, o) => request({ ...o, url: u, method: 'GET' }),
  post: (u, d, o) => request({ ...o, url: u, method: 'POST', data: d }),
  put: (u, d, o) => request({ ...o, url: u, method: 'PUT', data: d }),
  delete: (u, o) => request({ ...o, url: u, method: 'DELETE' }) }
