import { createRouter, createWebHistory } from 'vue-router'
import { getStorage } from '../utils/storage'
import { hasPerm } from '../utils/permission'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../pages/LoginPage.vue'), meta: { title: '登录' } },
  {
    path: '/', component: () => import('../components/AppShell.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../pages/DashboardPage.vue'), meta: { title: '仪表盘', perm: 'report:view' } },
      { path: 'items', name: 'items', component: () => import('../pages/ItemsPage.vue'), meta: { title: '物品档案', perm: 'item:read' } },
      { path: 'partners', name: 'partners', component: () => import('../pages/PartnersPage.vue'), meta: { title: '供应商 / 客户', perm: 'partner:read' } },
      { path: 'stock-in', name: 'stock-in', component: () => import('../pages/MovementPage.vue'), props: { type: 'in' }, meta: { title: '扫码入库', perm: 'inventory:write' } },
      { path: 'stock-out', name: 'stock-out', component: () => import('../pages/MovementPage.vue'), props: { type: 'out' }, meta: { title: '扫码出库', perm: 'inventory:write' } },
      { path: 'documents', name: 'documents', component: () => import('../pages/DocumentsPage.vue'), meta: { title: '入库 / 出库单', perm: 'document:read' } },
      { path: 'adjustments', name: 'adjustments', component: () => import('../pages/AdjustmentsPage.vue'), meta: { title: '报损 / 报溢', perm: 'adjustment:read' } },
      { path: 'transfers', name: 'transfers', component: () => import('../pages/TransfersPage.vue'), meta: { title: '库存调拨', perm: 'transfer:read' } },
      { path: 'stocktakes', name: 'stocktakes', component: () => import('../pages/StocktakesPage.vue'), meta: { title: '库存盘点', perm: 'stocktake:read' } },
      { path: 'inventory', name: 'inventory', component: () => import('../pages/InventoryPage.vue'), meta: { title: '库存管理', perm: 'inventory:read' } },
      { path: 'purchase-requests', name: 'purchase-requests', component: () => import('../pages/PurchaseRequestsPage.vue'), meta: { title: '采购申请', perm: 'purchase-request:read' } },
      { path: 'reports', name: 'reports', component: () => import('../pages/ReportsPage.vue'), meta: { title: '报表中心', perm: 'report:view' } },
      { path: 'reports2', name: 'reports2', component: () => import('../pages/ReportsPage2.vue'), meta: { title: '库龄与收发存', perm: 'report:view' } },
      { path: 'tools', name: 'tools', component: () => import('../pages/ToolsPage.vue'), meta: { title: '二维码与 Excel', perm: 'qrcode:read' } },
      { path: 'users', name: 'users', component: () => import('../pages/UsersPage.vue'), meta: { title: '用户与权限', perm: 'user:manage' } },
      { path: 'logs', name: 'logs', component: () => import('../pages/LogsPage.vue'), meta: { title: '操作日志', perm: 'log:view' } },
      { path: 'market/products', name: 'market-products', component: () => import('../pages/MallProductsPage.vue'), meta: { title: '商城商品', perm: 'product:read' } },
      { path: 'market/orders', name: 'market-orders', component: () => import('../pages/MallOrdersPage.vue'), meta: { title: '商城订单', perm: 'order:read' } },
      { path: 'market/customers', name: 'market-customers', component: () => import('../pages/MallCustomersPage.vue'), meta: { title: '商城客户', perm: 'customer:read' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  const token = getStorage('wms_token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/dashboard'
  if (to.meta.perm && auth.user && !hasPerm(auth.user, to.meta.perm)) return '/dashboard'
  return true
})

export default router
