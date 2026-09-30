import client from './client'

// 后端 /items 将 pageSize 钳制到 ≤100（ItemController#list），物品下拉等需要全量数据的场景按页取完；上限 20 页（2000 条）防御性兜底
async function allItems() {
  const first = await api.items({ page: 1, pageSize: 100 })
  const records = [...first.records]
  const pages = Math.min(Math.ceil(Number(first.total || 0) / (first.pageSize || 100)), 20)
  for (let page = 2; page <= pages; page++) records.push(...(await api.items({ page, pageSize: 100 })).records)
  return records
}

export const api = {
  login: (data) => client.post('/auth/login', data), me: () => client.get('/auth/me'), logout: () => client.post('/auth/logout'), users: () => client.get('/auth/users'), createUser: (data) => client.post('/auth/users', data), updateUser: (id, data) => client.put(`/auth/users/${id}`, data), permissions: () => client.get('/auth/permissions'),
  dashboard: () => client.get('/reports/dashboard'), alerts: () => client.get('/reports/stock-alert'), profit: (params) => client.get('/reports/profit', { params }),
  items: (params) => client.get('/items', { params }), allItems, item: (id) => client.get(`/items/${id}`), createItem: (data) => client.post('/items', data), updateItem: (id, data) => client.put(`/items/${id}`, data), deleteItem: (id) => client.delete(`/items/${id}`), categories: () => client.get('/items/categories'),
  inventory: (params) => client.get('/inventory', { params }), transactions: (limit = 500) => client.get('/inventory/transactions', { params: { limit } }), warehouses: (includeDisabled = false) => client.get('/warehouses', { params: includeDisabled ? { includeDisabled: true } : {} }), createWarehouse: (data) => client.post('/warehouses', data),
  stockIn: (data) => client.post('/stock/in/scan', data), stockOut: (data) => client.post('/stock/out/scan', data),
  partners: (type) => client.get('/partners', { params: type ? { type } : {} }), createPartner: (data) => client.post('/partners', data), updatePartner: (id, data) => client.put(`/partners/${id}`, data), deletePartner: (id) => client.delete(`/partners/${id}`),
  documents: (params) => client.get('/documents', { params }), document: (id) => client.get(`/documents/${id}`), createDocument: (data) => client.post('/documents', data), reviewDocument: (id, data) => client.post(`/documents/${id}/review`, data), completeDocument: (id) => client.post(`/documents/${id}/complete`), cancelDocument: (id) => client.post(`/documents/${id}/cancel`), uncompleteDocument: (id) => client.post(`/documents/${id}/uncomplete`), reverseDocument: (id) => client.post(`/documents/${id}/reverse`),
  adjustments: (params) => client.get('/adjustments', { params }), createAdjustment: (data) => client.post('/adjustments', data), reviewAdjustment: (id, data) => client.post(`/adjustments/${id}/review`, data), completeAdjustment: (id) => client.post(`/adjustments/${id}/complete`),
  purchaseRequests: (params) => client.get('/purchase-requests', { params }), createPurchaseRequest: (data) => client.post('/purchase-requests', data), reviewPurchaseRequest: (id, data) => client.post(`/purchase-requests/${id}/review`, data), cancelPurchaseRequest: (id) => client.post(`/purchase-requests/${id}/cancel`),
  inventoryAge: () => client.get('/reports/inventory-age'), inOutSummary: (period) => client.get('/reports/in-out-summary', { params: period ? { period } : {} }),
  transfers: (params) => client.get('/transfers', { params }), createTransfer: (data) => client.post('/transfers', data), reviewTransfer: (id, data) => client.post(`/transfers/${id}/review`, data), completeTransfer: (id) => client.post(`/transfers/${id}/complete`),
  stocktakes: (params) => client.get('/stocktakes', { params }), createStocktake: (data) => client.post('/stocktakes', data), countStocktake: (id, data) => client.post(`/stocktakes/${id}/count`, data), reviewStocktake: (id, data) => client.post(`/stocktakes/${id}/review`, data), completeStocktake: (id) => client.post(`/stocktakes/${id}/complete`),
  qrcode: (code) => client.get(`/qrcodes/items/${encodeURIComponent(code)}`), exportItems: () => client.get('/excel/items/export', { responseType: 'blob' }), importItems: (file) => { const data = new FormData(); data.append('file', file); return client.post('/excel/items/import', data) },
  anomalies: () => client.get('/reports/anomalies'), ocrRecognize: (file) => { const data = new FormData(); data.append('file', file); return client.post('/ocr/recognize', data) },
  operationLogs: (params) => client.get('/logs', { params }),
  marketProducts: (params) => client.get('/admin/market/products', { params }), createMarketProduct: (data) => client.post('/admin/market/products', data), updateMarketProduct: (id, data) => client.put(`/admin/market/products/${id}`, data), shelfMarketProduct: (id, status) => client.post(`/admin/market/products/${id}/shelf`, { status }), deleteMarketProduct: (id) => client.delete(`/admin/market/products/${id}`),
  marketCategories: () => client.get('/admin/market/categories'), createMarketCategory: (data) => client.post('/admin/market/categories', data), updateMarketCategory: (id, data) => client.put(`/admin/market/categories/${id}`, data), deleteMarketCategory: (id) => client.delete(`/admin/market/categories/${id}`),
  marketOrders: (params) => client.get('/admin/market/orders', { params }), marketOrder: (id) => client.get(`/admin/market/orders/${id}`), auditMarketOrder: (id, data) => client.post(`/admin/market/orders/${id}/audit`, data), shipMarketOrder: (id, data) => client.post(`/admin/market/orders/${id}/ship`, data), completeMarketOrder: (id) => client.post(`/admin/market/orders/${id}/complete`), cancelMarketOrder: (id, reason) => client.post(`/admin/market/orders/${id}/cancel`, { reason }), refundMarketOrder: (id, reason) => client.post(`/admin/market/orders/${id}/refund`, { reason }),
  marketCustomers: (params) => client.get('/admin/market/customers', { params }), createMarketCustomer: (data) => client.post('/admin/market/customers', data), updateMarketCustomer: (id, data) => client.put(`/admin/market/customers/${id}`, data), deleteMarketCustomer: (id) => client.delete(`/admin/market/customers/${id}`),
  marketStats: () => client.get('/admin/market/stats'),
}
