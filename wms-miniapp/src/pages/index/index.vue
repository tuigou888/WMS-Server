<template>
  <view class="index-page">
    <scroll-view class="content" scroll-y @scrolltolower="loadMore" :style="{ height: contentHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <!-- 欢迎区域 -->
      <view class="welcome-card">
        <view class="welcome-header">
          <text class="greeting">{{ greeting }}{{ userStore.user?.displayName || userStore.user?.username }}</text>
          <text class="role-badge" :class="['badge', userStore.isAdmin ? 'badge-info' : 'badge-success']">
            {{ userStore.isAdmin ? '管理员' : '仓库操作员' }}
          </text>
        </view>
        <view class="warehouse-selector" @tap="showWarehousePicker">
          <view class="warehouse-info">
            <text class="label">当前仓库</text>
            <text class="value">{{ currentWarehouse?.name || '请选择仓库' }}</text>
          </view>
          <text class="arrow">▶</text>
        </view>
      </view>

      <!-- 今日看板 -->
      <view v-if="dashboard" class="dashboard-card">
        <text class="section-title">今日概览</text>
        <view class="dashboard-grid">
          <view class="stat-item">
            <text class="stat-label">物品种类</text>
            <text class="stat-value">{{ dashboard.stockItemCount || 0 }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">库存总量</text>
            <text class="stat-value">{{ formatNum(dashboard.totalQuantity) }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">库存总值</text>
            <text class="stat-value value-green">¥{{ formatMoney(dashboard.totalAmount) }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">今日入库</text>
            <text class="stat-value value-green">¥{{ formatMoney(dashboard.todayInboundAmount) }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">今日出库</text>
            <text class="stat-value value-red">¥{{ formatMoney(dashboard.todayOutboundAmount) }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">预警物品</text>
            <text class="stat-value value-red">{{ dashboard.alertCount || 0 }}</text>
          </view>
        </view>
      </view>

      <!-- 功能菜单 -->
      <view class="menu-section">
        <text class="section-title">常用功能</text>
        <view class="menu-grid">
          <navigator v-for="item in menus" :key="item.key" :url="item.url" class="menu-item" hover-class="menu-item-hover">
            <view class="menu-icon">{{ item.icon }}</view>
            <text class="menu-name">{{ item.name }}</text>
          </navigator>
        </view>
      </view>

      <!-- 预警提示 -->
      <view v-if="alerts && alerts.length > 0" class="alert-card">
        <view class="alert-header">
          <text class="section-title">库存预警</text>
          <navigator url="/pages/reports/reports" class="view-all">查看全部</navigator>
        </view>
        <view class="alert-list">
          <view v-for="a in alerts.slice(0, 3)" :key="a.itemId" class="alert-item">
            <view class="alert-main">
              <text class="alert-name">{{ a.itemName }} ({{ a.itemCode }})</text>
              <text class="alert-badge" :class="['badge', a.priority === 'HIGH' ? 'badge-error' : a.priority === 'MEDIUM' ? 'badge-warning' : 'badge-info']">
                {{ a.priority === 'HIGH' ? '严重' : a.priority === 'MEDIUM' ? '预警' : '关注' }}
              </text>
            </view>
            <view class="alert-detail">
              <text>库存: {{ formatNum(a.currentStock) }} {{ a.unit }}</text>
              <text class="value-red">安全库存: {{ formatNum(a.safetyStock) }}</text>
              <text class="value-red">缺口: {{ formatNum(a.shortage) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部版本信息 -->
      <view class="footer">版本 1.0.0 | 下拉刷新数据</view>
    </scroll-view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user.js'
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum } from '@/utils/format.js'

export default {
  data() {
    return {
      dashboard: null,
      alerts: null,
      refreshing: false,
      contentHeight: 0,
      menus: [
        { key: 'stock-in', name: '扫码入库', icon: '📥', url: '/pages/stock-in/stock-in' },
        { key: 'stock-out', name: '扫码出库', icon: '📤', url: '/pages/stock-out/stock-out' },
        { key: 'scan', name: '扫码查询', icon: '🔍', url: '/pages/scan/scan' },
        { key: 'inventory', name: '库存查询', icon: '📦', url: '/pages/inventory/inventory' },
        { key: 'check', name: '盘点任务', icon: '📋', url: '/pages/check/check' },
        { key: 'item-list', name: '物品查询', icon: '🏷️', url: '/pages/item-list/item-list' },
      ],
    }
  },
  computed: {
    userStore() { return useUserStore() },
    greeting() {
      const hour = new Date().getHours()
      if (hour < 6) return '凌晨好，'
      if (hour < 9) return '早上好，'
      if (hour < 12) return '上午好，'
      if (hour < 14) return '中午好，'
      if (hour < 18) return '下午好，'
      return '晚上好，'
    },
    currentWarehouse() {
      return this.userStore.warehouses.find(w => w.id === this.userStore.warehouseId)
    },
  },
  onLoad() {
    this.setContentHeight()
    this.loadData()
  },
  onShow() {
    if (this.userStore.isLoggedIn) {
      this.loadData()
    }
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.loadData(true)
  },
  methods: {
    setContentHeight() {
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      this.contentHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight
    },
    async loadData(skipCache = false) {
      if (!this.userStore.isLoggedIn) return
      try {
        const [dashboard, alerts] = await Promise.all([
          api.dashboard().catch(() => null),
          api.alerts().catch(() => []),
        ])
        this.dashboard = dashboard
        this.alerts = alerts
      } catch (e) {
        console.error('加载首页数据失败:', e)
      } finally {
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
    },
    onRefresh() {
      this.refreshing = true
      this.loadData(true)
    },
    loadMore() {},
    showWarehousePicker() {
      const items = this.userStore.warehouses.map(w => w.name)
      if (items.length === 0) {
        uni.showToast({ title: '暂无仓库数据', icon: 'none' })
        return
      }
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          const selected = this.userStore.warehouses[res.tapIndex]
          this.userStore.setWarehouse(selected.id)
        },
      })
    },
    formatMoney,
    formatNum,
  },
}
</script>

<style scoped>
.index-page { height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 20px; }

.welcome-card {
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
  border-radius: 12px;
  margin: 12px;
  padding: 20px;
  color: #fff;
}
.welcome-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.greeting { font-size: 18px; font-weight: 500; }
.role-badge { font-size: 11px; padding: 2px 8px; }

.warehouse-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255,255,255,0.15);
  border-radius: 8px;
  padding: 12px 16px;
}
.warehouse-info { display: flex; flex-direction: column; }
.warehouse-info .label { font-size: 12px; color: rgba(255,255,255,0.8); }
.warehouse-info .value { font-size: 16px; font-weight: 500; }
.arrow { font-size: 12px; color: rgba(255,255,255,0.6); }

.dashboard-card {
  background: #fff;
  border-radius: 12px;
  margin: 12px;
  padding: 16px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 8px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.stat-label { font-size: 12px; color: #999; }
.stat-value { font-size: 18px; font-weight: 600; color: #333; margin-top: 4px; }

.menu-section {
  background: #fff;
  border-radius: 12px;
  margin: 12px;
  padding: 16px;
}
.menu-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 8px;
}
.menu-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 12px;
  background: #fafafa;
  border-radius: 10px;
  text-decoration: none;
}
.menu-item-hover { background: #e6f7ff; }
.menu-icon { font-size: 28px; margin-bottom: 8px; }
.menu-name { font-size: 13px; color: #333; text-align: center; }

.alert-card {
  background: #fff;
  border-radius: 12px;
  margin: 12px;
  padding: 16px;
}
.alert-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.view-all { font-size: 13px; color: #1677ff; }
.alert-list { display: flex; flex-direction: column; gap: 10px; }
.alert-item {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
}
.alert-main { display: flex; align-items: center; gap: 8px; flex: 1; }
.alert-name { font-size: 13px; color: #333; }
.alert-detail { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px; }
.footer { text-align: center; padding: 20px; font-size: 12px; color: #999; }
</style>