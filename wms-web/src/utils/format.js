export const money = (value) => `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const number = (value) => Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })

const BEIJING_OFFSET = 8 * 60 * 60 * 1000
const hasTimezone = (value) => /(?:Z|[+-]\d{2}:?\d{2})$/i.test(String(value))
const parseBeijingTime = (value) => {
  const text = String(value).trim().replace(' ', 'T')
  const date = new Date(hasTimezone(text) ? text : `${text}+08:00`)
  return Number.isNaN(date.getTime()) ? null : new Date(date.getTime() + BEIJING_OFFSET)
}

const parts = (value, withSeconds = true) => {
  const date = parseBeijingTime(value)
  if (!date) return null
  const pad = (v) => String(v).padStart(2, '0')
  return withSeconds
    ? `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`
    : `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

export const dateTime = (value) => value ? (parts(value) || '-') : '-'
export const date = (value) => value ? (parts(value, false) || '-') : '-'
