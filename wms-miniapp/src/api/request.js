// API 基础配置与拦截器
const BASE_URL = 'http://localhost:8088/api/v1'
const TOKEN_KEY = 'wms_token'
const USER_KEY = 'wms_user'
const WAREHOUSE_KEY = 'wms_warehouse'

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

        if (res.statusCode >= 400) {
          const msg = res.data?.message || `请求失败 (${res.statusCode})`
          reject(new RequestError(msg, res.statusCode, res))
          return
        }

        const apiRes = res.data
        if (apiRes && typeof apiRes === 'object' && 'code' in apiRes) {
          if (apiRes.code === 200) {
            resolve(apiRes.data)
          } else {
            reject(new RequestError(apiRes.message || '请求失败', apiRes.code, res))
          }
        } else {
          resolve(apiRes)
        }
      },
      fail: (err) => {
        reject(new RequestError(err.errMsg || '网络异常', 0, null))
      },
    })
  })
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
}