// 订单/商品状态文案与颜色映射（与后端枚举对齐）
const ORDER_STATUS = {
  PENDING: { text: '待付款', color: 'warning' },
  AUDITED: { text: '待发货', color: 'info' },
  SHIPPED: { text: '已发货', color: 'success' },
  COMPLETED: { text: '已完成', color: 'default' },
  CANCELLED: { text: '已取消', color: 'error' },
  REJECTED: { text: '已拒绝', color: 'error' },
}

const PAY_STATUS = {
  UNPAID: { text: '未支付', color: 'warning' },
  PAID: { text: '已支付', color: 'success' },
  REFUNDED: { text: '已退款', color: 'error' },
}

const PRODUCT_STATUS = {
  SHELF_ON: { text: '在售', color: 'success' },
  SHELF_OFF: { text: '已下架', color: 'default' },
  DRAFT: { text: '草稿', color: 'default' },
}

export function orderStatus(s) { return ORDER_STATUS[s] || { text: s || '未知', color: 'default' } }
export function payStatus(s) { return PAY_STATUS[s] || { text: s || '未知', color: 'default' } }
export function productStatus(s) { return PRODUCT_STATUS[s] || { text: s || '未知', color: 'default' } }
