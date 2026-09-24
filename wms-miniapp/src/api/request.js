// API 基础配置与拦截器
// 部署时可注入环境变量 VITE_API_BASE（H5 构建）或修改 VITE_MAIN_BASE（小程序本地存储覆盖，优先级最高），默认本地联调地址
const ENV_BASE = import.meta.env.VITE_API_BASE || ''
const STORED_BASE = uni.getStorageSync('wms_api_base')
const BASE_URL = STORED_BASE || ENV_BASE || (import.meta.env.DEV ? 'http://localhost:8088/api/v1' : '')
const IS_LOCAL_API = /^https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?(?:\/|$)/.test(BASE_URL)
const TOKEN_KEY = 'wms_token'
const USER_KEY = 'wms_user'
const WAREHOUSE_KEY = 'wms_warehouse'
const IDEMPOTENCY_CACHE_KEY = 'wms_idempotency_pending'

function requestIdentity(method, url, data) {
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) return null
  const body = JSON.stringify(sortForHash(data == null ? null : data)) || 'null'
  const input = `${method.toUpperCase()}:${url}:${body}`
  let a = 2166136261, b = 2246822519
  for (let i = 0; i < input.length; i++) {
    const code = input.charCodeAt(i)
    a = Math.imul(a ^ code, 16777619)
    b = Math.imul(b ^ code, 3266489917)
  }
  const scope = `${method.toUpperCase()}:${url}:${(a >>> 0).toString(36)}${(b >>> 0).toString(36)}`
  const pending = uni.getStorageSync(IDEMPOTENCY_CACHE_KEY) || {}
  let key = pending[scope]
  if (!key) {
    key = `wms-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`
    pending[scope] = key
    uni.setStorageSync(IDEMPOTENCY_CACHE_KEY, pending)
  }
  return { scope, key }
}

function sortForHash(value) {
  if (Array.isArray(value)) return value.map(sortForHash)
  if (value && typeof value === 'object') return Object.keys(value).sort().reduce((out, key) => { out[key] = sortForHash(value[key]); return out }, {})
  return value
}

function clearRequestIdentity(identity) {
  if (!identity) return
  const pending = uni.getStorageSync(IDEMPOTENCY_CACHE_KEY) || {}
  delete pending[identity.scope]
  uni.setStorageSync(IDEMPOTENCY_CACHE_KEY, pending)
}

class RequestError extends Error {
  constructor(message, code, response) {
    super(message)
    this.name = 'RequestError'
    this.code = code
    this.response = response
  }
}

function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

function clearToken() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

function getUser() {
  const userStr = uni.getStorageSync(USER_KEY)
  return userStr ? JSON.parse(userStr) : null
}

function setUser(user) {
  uni.setStorageSync(USER_KEY, JSON.stringify(user))
}

function getWarehouseId() {
  return uni.getStorageSync(WAREHOUSE_KEY) || null
}

function setWarehouseId(id) {
  uni.setStorageSync(WAREHOUSE_KEY, id)
}

async function request(options) {
  const { url, method = 'GET', data, header = {}, responseType } = options
  if (!BASE_URL) return Promise.reject(new RequestError('未配置生产 API 地址，请使用 VITE_API_BASE 重新构建', -2))
  const token = getToken()

  const requestOptions = {
    url: BASE_URL + url,
    method,
    data,
    header: {
      'Content-Type': 'application/json',
      ...header,
    },
    responseType: responseType || 'json',
    timeout: 15000,
  }
  const identity = requestIdentity(method, url, data)
  if (identity && !requestOptions.header['Idempotency-Key']) requestOptions.header['Idempotency-Key'] = identity.key

  if (token) {
    requestOptions.header.Authorization = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      ...requestOptions,
      success: (res) => {
        if (res.statusCode === 401) {
          clearToken()
          if (getCurrentPages().length > 0) {
            const currentRoute = getCurrentPages()[getCurrentPages().length - 1].route
            if (!currentRoute.includes('login')) {
              uni.reLaunch({ url: '/pages/login/login' })
            }
          }
          reject(new RequestError('登录已过期，请重新登录', 401, res))
          return
        }

        const apiRes = normalizeResponseData(res.data)

        if (res.statusCode >= 400) {
          const msg = apiRes?.message || `请求失败 (${res.statusCode})`
          reject(new RequestError(msg, res.statusCode, res))
          return
        }

        if (apiRes && typeof apiRes === 'object' && 'code' in apiRes) {
          if (apiRes.code === 200) {
            clearRequestIdentity(identity)
            resolve(apiRes.data)
          } else {
            reject(new RequestError(apiRes.message || '请求失败', apiRes.code, res))
          }
        } else {
          clearRequestIdentity(identity)
          resolve(apiRes)
        }
      },
      fail: (err) => {
        reject(new RequestError(err.errMsg || '网络异常', 0, null))
      },
    })
  })
}

function normalizeResponseData(data) {
  if (typeof data !== 'string') return data
  const text = data.trim()
  if (!text) return data
  try {
    return JSON.parse(text)
  } catch (_) {
    return data
  }
}

const api = {
  get: (url, params) => request({ url, method: 'GET', data: params }),
  post: (url, data) => request({ url, method: 'POST', data }),
  put: (url, data) => request({ url, method: 'PUT', data }),
  delete: (url) => request({ url, method: 'DELETE' }),
  download: (url, params) => request({ url, method: 'GET', data: params, responseType: 'arraybuffer' }),

  // 认证
  login: (data) => api.post('/auth/login', data),
  wxLogin: (code) => api.post('/auth/wx-login', { code }),
  wxBind: (data) => api.post('/auth/wx-bind', data),
  me: () => api.get('/auth/me'),
  logout: () => api.post('/auth/logout'),

  // 物品
  items: (params) => api.get('/items', params),
  item: (id) => api.get(`/items/${id}`),
  itemByCode: (code) => api.get(`/items/code/${encodeURIComponent(code)}`),
  categories: () => api.get('/items/categories'),
  createItem: (data) => api.post('/items', data),
  updateItem: (id, data) => api.put(`/items/${id}`, data),
  deleteItem: (id) => api.delete(`/items/${id}`),

  // 库存
  inventory: (params) => api.get('/inventory', params),
  inventoryByItem: (itemId) => api.get(`/inventory/${itemId}`),
  warehouses: (includeDisabled) => api.get('/inventory/warehouses', { includeDisabled }),
  transactions: (limit) => api.get('/inventory/transactions', { limit }),
  locations: (warehouseId) => api.get('/locations', { warehouseId }),

  // 扫码入库/出库
  stockIn: (data) => api.post('/stock/in/scan', data),
  stockOut: (data) => api.post('/stock/out/scan', data),

  // 单据
  documents: (params) => api.get('/documents', params),
  document: (id) => api.get(`/documents/${id}`),
  createDocument: (data) => api.post('/documents', data),
  reviewDocument: (id, data) => api.post(`/documents/${id}/review`, data),
  completeDocument: (id) => api.post(`/documents/${id}/complete`),
  cancelDocument: (id) => api.post(`/documents/${id}/cancel`),
  uncompleteDocument: (id) => api.post(`/documents/${id}/uncomplete`),
  reverseDocument: (id) => api.post(`/documents/${id}/reverse`),

  // 调拨
  transfers: (params) => api.get('/transfers', params),
  createTransfer: (data) => api.post('/transfers', data),
  reviewTransfer: (id, data) => api.post(`/transfers/${id}/review`, data),
  completeTransfer: (id) => api.post(`/transfers/${id}/complete`),

  // 盘点
  stocktakes: (params) => api.get('/stocktakes', params),
  createStocktake: (data) => api.post('/stocktakes', data),
  countStocktake: (id, data) => api.post(`/stocktakes/${id}/count`, data),
  reviewStocktake: (id, data) => api.post(`/stocktakes/${id}/review`, data),
  completeStocktake: (id) => api.post(`/stocktakes/${id}/complete`),

  // 报损报溢
  adjustments: (params) => api.get('/adjustments', params),
  createAdjustment: (data) => api.post('/adjustments', data),
  reviewAdjustment: (id, data) => api.post(`/adjustments/${id}/review`, data),
  completeAdjustment: (id) => api.post(`/adjustments/${id}/complete`),

  // 往来单位
  partners: (type) => api.get('/partners', { type }),
  createPartner: (data) => api.post('/partners', data),
  updatePartner: (id, data) => api.put(`/partners/${id}`, data),
  deletePartner: (id) => api.delete(`/partners/${id}`),

  // 采购申请
  purchaseRequests: (params) => api.get('/purchase-requests', params),
  createPurchaseRequest: (data) => api.post('/purchase-requests', data),
  reviewPurchaseRequest: (id, data) => api.post(`/purchase-requests/${id}/review`, data),
  cancelPurchaseRequest: (id) => api.post(`/purchase-requests/${id}/cancel`),

  // 报表
  dashboard: () => api.get('/reports/dashboard'),
  alerts: () => api.get('/reports/stock-alert'),
  profit: () => api.get('/reports/profit'),
  anomalies: () => api.get('/reports/anomalies'),
  inventoryAge: () => api.get('/reports/inventory-age'),
  inOutSummary: (period) => api.get('/reports/in-out-summary', { period }),

  // 二维码
  qrcode: (code) => api.get(`/qrcodes/items/${encodeURIComponent(code)}`),
  qrcodePng: (code) => api.download(`/qrcodes/items/${encodeURIComponent(code)}/png`),

  // Excel
  exportItems: () => api.download('/excel/items/export'),
  importItems: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request({ url: '/excel/items/import', method: 'POST', data: formData, header: { 'Content-Type': 'multipart/form-data' } })
  },

  // OCR
  ocrRecognize: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request({ url: '/ocr/recognize', method: 'POST', data: formData, header: { 'Content-Type': 'multipart/form-data' } })
  },

  // 操作日志
  logs: (params) => api.get('/logs', params),
}

export {
  api,
  getToken,
  setToken,
  clearToken,
  getUser,
  setUser,
  getWarehouseId,
  setWarehouseId,
  RequestError,
  BASE_URL,
  IS_LOCAL_API,
}
