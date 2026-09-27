<template>
  <view class="reports-page">
    <scroll-view class="content" scroll-y :style="{ height: contentHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading" class="loading">加载中...</view>

      <view v-else>
        <!-- 看板卡片 -->
        <view class="card" v-if="dashboard">
          <text class="section-title">仓库看板</text>
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

        <!-- 库存预警 -->
        <view class="card alert-card" v-if="alerts && alerts.length > 0">
          <view class="section-header">
            <text class="section-title">库存预警 ({{ alerts.length }})</text>
            <navigator url="/pages/inventory/inventory" class="view-all">查看库存</navigator>
          </view>
          <view class="alert-list">
            <view v-for="a in alerts.slice(0, 10)" :key="a.itemId" class="alert-item">
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
                <text v-if="a.dailyAvgOut" class="hint">日均出库: {{ formatNum(a.dailyAvgOut) }} | 建议补货: {{ formatNum(a.suggestedOrder) }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 近期流水 -->
        <view class="card" v-if="recentTransactions && recentTransactions.length > 0">
          <text class="section-title">近期流水（前 8 条）</text>
          <view class="tx-list">
            <view v-for="tx in recentTransactions.slice(0, 8)" :key="tx.id" class="tx-row">
              <text class="tx-type" :class="typeClass(tx.transactionType)">{{ typeText(tx.transactionType) }}</text>
              <text class="tx-item">{{ tx.itemName }}</text>
              <text class="tx-qty" :class="tx.quantity > 0 ? 'value-green' : 'value-red'">{{ tx.quantity > 0 ? '+' : '' }}{{ formatNum(tx.quantity) }}</text>
              <text class="tx-time">{{ formatDate(tx.transactionAt) }}</text>
            </view>
          </view>
        </view>

        <!-- 分类分布 -->
        <view class="card" v-if="categoryDistribution && categoryDistribution.length > 0">
          <text class="section-title">分类分布 (数量)</text>
          <view class="category-list">
            <view v-for="c in categoryDistribution.slice(0, 8)" :key="c.name" class="category-row">
              <text class="cat-name">{{ c.name }}</text>
              <view class="cat-bar">
                <view class="cat-fill" :style="{ width: catPercent(c.value) + '%' }"></view>
              </view>
              <text class="cat-value">{{ formatNum(c.value) }}</text>
            </view>
          </view>
        </view>

        <!-- 金额分布 -->
        <view class="card" v-if="valueByCategory && valueByCategory.length > 0">
          <text class="section-title">分类金额（前 8 名）</text>
          <view class="category-list">
            <view v-for="c in valueByCategory.slice(0, 8)" :key="c.name" class="category-row">
              <text class="cat-name">{{ c.name }}</text>
              <view class="cat-bar">
                <view class="cat-fill" :style="{ width: valuePercent(c.value) + '%' }"></view>
              </view>
              <text class="cat-value">¥{{ formatMoney(c.value) }}</text>
            </view>
          </view>
        </view>

        <!-- 利润趋势 -->
        <view class="card" v-if="monthlyProfit && monthlyProfit.length > 0">
          <text class="section-title">月度利润趋势 (近 6 月)</text>
          <view class="profit-list">
            <view v-for="m in monthlyProfit" :key="m.month" class="profit-row">
              <text class="profit-month">{{ m.month }}</text>
              <text class="profit-cost">成本: ¥{{ formatMoney(m.cost) }}</text>
              <text class="profit-sale">售价: ¥{{ formatMoney(m.sale) }}</text>
              <text class="profit-profit" :class="m.profit >= 0 ? 'value-green' : 'value-red'">利润: ¥{{ formatMoney(m.profit) }}</text>
            </view>
          </view>
        </view>

        <!-- 高价值物品 -->
        <view class="card" v-if="topItemsByValue && topItemsByValue.length > 0">
          <text class="section-title">高价值物品（前 8 名）</text>
          <view class="top-items">
            <view v-for="item in topItemsByValue" :key="item.itemCode" class="top-item">
              <text class="top-rank">#{{ item.rank }}</text>
              <view class="top-info">
                <text class="top-name">{{ item.itemName }} ({{ item.itemCode }})</text>
                <text class="top-unit">{{ item.unit }}</text>
              </view>
              <view class="top-stats">
                <text>库存: {{ formatNum(item.quantity) }}</text>
                <text class="value-green">价值: ¥{{ formatMoney(item.value) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum, date as formatDate } from '@/utils/format.js'

export default {
  data() {
    return {
      dashboard: null,
      alerts: null,
      recentTransactions: null,
      categoryDistribution: null,
      valueByCategory: null,
      monthlyProfit: null,
      topItemsByValue: null,
      loading: true,
      refreshing: false,
      contentHeight: 0,
    }
  },
  onLoad() {
    this.setContentHeight()
    this.loadAll()
  },
  onShow() {
    this.loadAll()
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.loadAll()
  },
  methods: {
    setContentHeight() {
      // windowHeight 已扣除原生导航栏（本页无 tabBar），无需再减
      this.contentHeight = uni.getSystemInfoSync().windowHeight
    },
    async loadAll() {
      this.loading = true
      try {
        const [dashboard, alerts, profit, anomalies, inventoryAge, inOutSummary] = await Promise.all([
          api.dashboard().catch(() => null),
          api.alerts().catch(() => []),
          api.profit().catch(() => []),
          api.anomalies().catch(() => []),
          api.inventoryAge().catch(() => []),
          api.inOutSummary().catch(() => []),
        ])

        this.dashboard = dashboard
        this.alerts = alerts

        this.monthlyProfit = dashboard?.monthlyProfit || []

        // 近期流水
        this.recentTransactions = dashboard?.recentTransactions || profit.records || []

        // 分类分布
        this.categoryDistribution = dashboard?.categoryDistribution || []
        this.valueByCategory = dashboard?.valueByCategory || []

        // 高价值物品
        this.topItemsByValue = (dashboard?.topItemsByValue || []).map((item, idx) => ({ ...item, rank: idx + 1 }))

      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        this.loading = false
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
    },
    onRefresh() {
      this.refreshing = true
      this.loadAll()
    },
    typeText(type) {
      const map = { in: '入库', out: '出库', transfer: '调拨', adjust: '调整', check: '盘点' }
      return map[type] || type
    },
    typeClass(type) {
      const map = { in: 'type-in', out: 'type-out', transfer: 'type-transfer', adjust: 'type-adjust', check: 'type-check' }
      return map[type] || ''
    },
    catPercent(val) {
      const max = Math.max(...(this.categoryDistribution?.map(c => c.value) || [1]))
      return (val / max) * 100
    },
    valuePercent(val) {
      const max = Math.max(...(this.valueByCategory?.map(c => c.value) || [1]))
      return (val / max) * 100
    },
    formatMoney,
    formatNum,
    formatDate,
  },
}
</script>

<style scoped>
.reports-page { min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 40rpx; }

/* 卡片与区块标题直接复用全局 .card / .section-title，此处不再重复定义 */
/* 预警卡顶部安全橙条 */
.alert-card { border-top: 6rpx solid var(--wms-accent); }

.section-header { display: flex; justify-content: space-between; align-items: center; }
.view-all { font-size: 26rpx; color: var(--wms-primary); }

/* 看板：数字大号加粗 */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 12rpx;
  background: var(--wms-bg);
  border-radius: 16rpx;
}
.stat-label { font-size: 22rpx; color: var(--wms-ink-3); }
.stat-value { font-size: 32rpx; font-weight: 700; color: var(--wms-ink); margin-top: 8rpx; font-family: monospace; }
.stat-value.value-green { color: var(--wms-success); }
.stat-value.value-red { color: var(--wms-danger); }

/* 预警条目：安全橙浅底强调 */
.alert-list { display: flex; flex-direction: column; gap: 20rpx; }
.alert-item {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 24rpx;
  background: var(--wms-warning-bg);
  border: 2rpx solid var(--wms-warning);
  border-radius: 16rpx;
}
.alert-main { display: flex; align-items: center; gap: 16rpx; flex: 1; flex-wrap: wrap; }
.alert-name { font-size: 26rpx; color: var(--wms-ink); font-weight: 500; }
.alert-detail { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; font-size: 24rpx; color: var(--wms-ink-2); }
.hint { font-size: 20rpx; color: var(--wms-ink-3); }

/* 表格类列表：去底色，行分隔线 */
.tx-list, .profit-list, .top-items { display: flex; flex-direction: column; }
.tx-row, .profit-row, .top-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  font-size: 24rpx;
  border-bottom: 2rpx solid var(--wms-border);
}
.tx-row:last-child, .profit-row:last-child, .top-item:last-child { border-bottom: none; }
.tx-type { font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 8rpx; font-weight: 600; min-width: 88rpx; text-align: center; }
.type-in { background: var(--wms-success-bg); color: var(--wms-success); }
.type-out { background: var(--wms-danger-bg); color: var(--wms-danger); }
.type-transfer { background: var(--wms-primary-bg); color: var(--wms-primary); }
.type-adjust { background: var(--wms-warning-bg); color: var(--wms-warning); }
.type-check { background: var(--wms-bg); color: var(--wms-ink-2); }
.tx-item { flex: 1; font-size: 24rpx; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tx-qty { font-weight: 600; }
.tx-time { color: var(--wms-ink-3); font-size: 22rpx; }
.profit-month { min-width: 120rpx; font-weight: 600; color: var(--wms-ink); }
.profit-cost, .profit-sale { color: var(--wms-ink-2); font-size: 24rpx; }
.profit-profit { font-weight: 600; }
.top-rank { width: 56rpx; padding: 4rpx 0; text-align: center; font-weight: 700; color: var(--wms-primary); background: var(--wms-primary-bg); border-radius: 8rpx; }
.top-info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.top-name { font-size: 26rpx; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-unit { font-size: 20rpx; color: var(--wms-ink-3); }
.top-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; font-size: 24rpx; }
.top-stats text:first-child { color: var(--wms-ink-2); }
.top-stats text:last-child { font-weight: 600; }

/* 分布条形图：主蓝进度条 */
.category-list { display: flex; flex-direction: column; gap: 20rpx; }
.category-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  font-size: 24rpx;
}
.cat-name { width: 160rpx; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-bar { flex: 1; height: 14rpx; background: var(--wms-bg); border-radius: 7rpx; overflow: hidden; }
.cat-fill { height: 100%; background: var(--wms-primary); border-radius: 7rpx; transition: width 0.3s; }
.cat-value { width: 150rpx; text-align: right; color: var(--wms-ink); font-weight: 600; font-family: monospace; }

.loading { text-align: center; padding: 80rpx; color: var(--wms-ink-3); }
</style>
