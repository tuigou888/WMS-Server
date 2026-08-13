import { http, getUser, setUser, clearAuth, setToken, getToken } from './request'

export const auth = {
  login: ({ username, password }) => http.post('/auth/login', { username, password }),
  wxLogin: (code) => http.post('/auth/wx-login', { code }),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),
}

export const products = {
  list: (params) => http.get('/market/products', params),
  detail: (id) => http.get(`/market/products/${id}`),
  categories: () => http.get('/market/categories'),
  warehouses: () => http.get('/market/warehouses'),
}

export const cart = {
  list: () => http.get('/market/cart'),
  add: (data) => http.post('/market/cart', data),
  update: (id, data) => http.put(`/market/cart/${id}`, data),
  remove: (ids) => http.delete('/market/cart', { data: { ids } }),
  clear: () => http.delete('/market/cart', {}),
}

export const customers = {
  list: () => http.get('/market/customers'),
  save: (data) => http.post('/market/customers', data),
  update: (id, data) => http.put(`/market/customers/${id}`, data),
  remove: (id) => http.delete(`/market/customers/${id}`),
}

export const orders = {
  create: (data) => http.post('/market/orders', data),
  list: (params) => http.get('/market/orders', params),
  detail: (id) => http.get(`/market/orders/${id}`),
  pay: (id) => http.post(`/market/orders/${id}/pay`),
  cancel: (id) => http.post(`/market/orders/${id}/cancel`),
  receive: (id) => http.post(`/market/orders/${id}/receive`),
}
