export const money = (value) => `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const number = (value) => Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
export const dateTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
