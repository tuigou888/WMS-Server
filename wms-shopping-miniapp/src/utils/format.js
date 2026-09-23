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
  const text = String(value).trim().replace(' ', 'T')
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  const d = new Date(hasTimezone ? text : `${text}+08:00`)
  if (isNaN(d.getTime())) return String(value)
  const beijing = new Date(d.getTime() + 8 * 60 * 60 * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${beijing.getUTCFullYear()}-${pad(beijing.getUTCMonth() + 1)}-${pad(beijing.getUTCDate())} ${pad(beijing.getUTCHours())}:${pad(beijing.getUTCMinutes())}`
}

export function formatDate(value) {
  if (!value) return ''
  const text = String(value).trim().replace(' ', 'T')
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  const d = new Date(hasTimezone ? text : `${text}+08:00`)
  if (isNaN(d.getTime())) return String(value)
  const beijing = new Date(d.getTime() + 8 * 60 * 60 * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${beijing.getUTCFullYear()}-${pad(beijing.getUTCMonth() + 1)}-${pad(beijing.getUTCDate())}`
}
