import axios from 'axios'

const client = axios.create({ baseURL: '/api/v1', timeout: 15000 })
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('wms_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
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
    return data.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('wms_token')
      localStorage.removeItem('wms_user')
      if (!window.location.pathname.includes('login')) window.location.reload()
    }
    return Promise.reject(new Error(error.response?.data?.message || (error.response?.status === 401 ? '登录已失效，请重新登录' : error.message) || '网络异常'))
  },
)
export default client
