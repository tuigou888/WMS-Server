export function formatPrice(value) {
  if (value === null || value === undefined) return '0.00'
  const n = Number(value)
  if (isNaN(n)) return '0.00'
  return n.toFixed(2)
}

export function formatNumber(value) {
  if (value === null || value === undefined) return '0'
  return new Intl.NumberFormat().format(Number(value))
}

export function formatDateTime(value) {
  if (!value) return ''
  // 兼容 "2026-08-13T16:04" 或 Date
  const d = new Date(value)
  if (isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
