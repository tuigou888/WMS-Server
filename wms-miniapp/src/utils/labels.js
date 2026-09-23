const permissionLabels = {
  'inventory:read': '库存查看', 'inventory:write': '库存维护', 'inventory:scan': '扫码出入库',
  'document:read': '单据查看', 'document:write': '单据创建', 'document:execute': '单据执行', 'document:review': '单据审核',
  'transfer:read': '调拨查看', 'transfer:write': '调拨创建', 'transfer:execute': '调拨执行', 'transfer:review': '调拨审核',
  'stocktake:read': '盘点查看', 'stocktake:write': '盘点创建', 'stocktake:execute': '盘点执行', 'stocktake:review': '盘点审核',
  'adjustment:read': '报损报溢查看', 'adjustment:write': '报损报溢创建', 'adjustment:execute': '报损报溢执行', 'adjustment:review': '报损报溢审核',
  'purchase-request:read': '采购申请查看', 'purchase-request:write': '采购申请创建', 'purchase-request:review': '采购申请审核',
  'item:read': '物品档案查看', 'item:write': '物品档案维护', 'partner:read': '往来单位查看', 'partner:write': '往来单位维护',
  'warehouse:manage': '仓库管理', 'user:manage': '用户管理', 'log:view': '操作日志查看', 'report:view': '报表查看',
  'qrcode:read': '二维码查看', 'excel:read': '电子表格导出', 'excel:write': '电子表格导入', 'ocr:use': '文字识别', 'location:read': '库位查看',
  'market:buy': '商城购买', 'market:read': '商城信息查看', 'product:read': '商城商品查看', 'product:write': '商城商品维护',
  'order:read': '商城订单查看', 'order:review': '商城订单审核', 'order:execute': '商城订单履约', 'customer:read': '客户信息查看', 'customer:write': '客户信息维护',
}

const actionLabels = {
  LOGIN: '登录', LOGOUT: '退出登录', CREATE: '创建', PAY: '支付', AUDIT: '审核', SHIP: '发货', REFUND: '退款', UPDATE: '更新', DELETE: '删除', REVIEW: '审核',
  COMPLETE: '执行完成', CANCEL: '取消', REVERSE: '红冲', UNCOMPLETE: '反审', QUERY: '查询', IMPORT: '导入', EXPORT: '导出',
}

export const permissionLabel = (value) => permissionLabels[value] || value || '未设置'
export const actionLabel = (value) => actionLabels[value] || value || '未记录'
