// 格式化工具（对齐 wms-web/src/utils/format.js）

export function money(val, decimals = 2) {
  if (val === null || val === undefined || val === '') return '0.00'
  const num = Number(val)
  if (isNaN(num)) return '0.00'
  return num.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

export function num(val, decimals = 4) {
  if (val === null || val === undefined || val === '') return '0'
  const n = Number(val)
  if (isNaN(n)) return '0'
  return n.toFixed(decimals).replace(/\.?0+$/, '')
}

export function dateTime(val) {
  if (!val) return ''
  return formatBeijing(val, true)
}

export function date(val) {
  if (!val) return ''
  return formatBeijing(val, false)
}

export function time(val) {
  if (!val) return ''
  const text = formatBeijing(val, true)
  if (!text) return ''
  return text.slice(11)
}

function formatBeijing(val, withSeconds) {
  const text = String(val).trim().replace(' ', 'T')
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
  const d = new Date(hasTimezone ? text : `${text}+08:00`)
  if (isNaN(d.getTime())) return ''
  const beijing = new Date(d.getTime() + 8 * 60 * 60 * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  const result = `${beijing.getUTCFullYear()}-${pad(beijing.getUTCMonth() + 1)}-${pad(beijing.getUTCDate())} ${pad(beijing.getUTCHours())}:${pad(beijing.getUTCMinutes())}:${pad(beijing.getUTCSeconds())}`
  return withSeconds ? result : result.slice(0, 10)
}
