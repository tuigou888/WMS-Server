import { h } from 'vue'
import { AppstoreOutlined, DashboardOutlined, DatabaseOutlined, FileTextOutlined, InboxOutlined, LogoutOutlined, PieChartOutlined, QrcodeOutlined, SettingOutlined, SwapOutlined, TeamOutlined, UnorderedListOutlined, WarningOutlined, ShopOutlined } from '@ant-design/icons-vue'
import { hasPerm } from './permission'

export function buildMenu(user) {
  return [
    ...(hasPerm(user, 'report:view') ? [{ key: '/dashboard', icon: h(DashboardOutlined), label: '仪表盘' }] : []),
    ...((hasPerm(user, 'item:read') || hasPerm(user, 'partner:read')) ? [{ type: 'group', label: '基础资料', children: [
      ...(hasPerm(user, 'item:read') ? [{ key: '/items', icon: h(AppstoreOutlined), label: '物品档案' }] : []),
      ...(hasPerm(user, 'partner:read') ? [{ key: '/partners', icon: h(TeamOutlined), label: '供应商 / 客户' }] : []),
    ] }] : []),
    ...((hasPerm(user, 'inventory:write') || hasPerm(user, 'document:read') || hasPerm(user, 'adjustment:read') || hasPerm(user, 'transfer:read') || hasPerm(user, 'stocktake:read') || hasPerm(user, 'inventory:read')) ? [{ type: 'group', label: '仓储业务', children: [
      ...(hasPerm(user, 'inventory:write') ? [{ key: '/stock-in', icon: h(InboxOutlined), label: '扫码入库' }] : []),
      ...(hasPerm(user, 'inventory:write') ? [{ key: '/stock-out', icon: h(LogoutOutlined), label: '扫码出库' }] : []),
      ...(hasPerm(user, 'document:read') ? [{ key: '/documents', icon: h(FileTextOutlined), label: '入库 / 出库单' }] : []),
      ...(hasPerm(user, 'adjustment:read') ? [{ key: '/adjustments', icon: h(WarningOutlined), label: '报损 / 报溢' }] : []),
      ...(hasPerm(user, 'transfer:read') ? [{ key: '/transfers', icon: h(SwapOutlined), label: '库存调拨' }] : []),
      ...(hasPerm(user, 'stocktake:read') ? [{ key: '/stocktakes', icon: h(UnorderedListOutlined), label: '库存盘点' }] : []),
      ...(hasPerm(user, 'inventory:read') ? [{ key: '/inventory', icon: h(DatabaseOutlined), label: '库存管理' }] : []),
    ] }] : []),
    ...(hasPerm(user, 'purchase-request:read') ? [{ type: 'group', label: '采购协同', children: [
      { key: '/purchase-requests', icon: h(FileTextOutlined), label: '采购申请' },
    ] }] : []),
    ...(hasPerm(user, 'report:view') ? [{ type: 'group', label: '数据中心', children: [
      { key: '/reports', icon: h(PieChartOutlined), label: '报表中心' },
      { key: '/reports2', icon: h(PieChartOutlined), label: '库龄与收发存' },
      ...(hasPerm(user, 'qrcode:read') ? [{ key: '/tools', icon: h(QrcodeOutlined), label: '二维码与 Excel' }] : []),
    ] }] : []),
    ...((hasPerm(user, 'user:manage') || hasPerm(user, 'log:view')) ? [{ type: 'group', label: '系统设置', children: [
      ...(hasPerm(user, 'user:manage') ? [{ key: '/users', icon: h(SettingOutlined), label: '用户与权限' }] : []),
      ...(hasPerm(user, 'log:view') ? [{ key: '/logs', icon: h(UnorderedListOutlined), label: '操作日志' }] : []),
    ] }] : []),
    ...((hasPerm(user, 'product:read') || hasPerm(user, 'order:read') || hasPerm(user, 'customer:read')) ? [{ type: 'group', label: '商城管理', children: [
      ...(hasPerm(user, 'product:read') ? [{ key: '/market/products', icon: h(ShopOutlined), label: '商城商品' }] : []),
      ...(hasPerm(user, 'order:read') ? [{ key: '/market/orders', icon: h(FileTextOutlined), label: '商城订单' }] : []),
      ...(hasPerm(user, 'customer:read') ? [{ key: '/market/customers', icon: h(TeamOutlined), label: '商城客户' }] : []),
    ] }] : []),
  ]
}
