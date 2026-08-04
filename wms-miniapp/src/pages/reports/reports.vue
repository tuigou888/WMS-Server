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
        <view class="card" v-if="alerts && alerts.length > 0">
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
          <text class="section-title">近期流水 (TOP 8)</text>
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
          <text class="section-title">分类金额 (TOP 8)</text>
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
          <text class="section-title">高价值物品 (TOP 8)</text>
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
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      this.contentHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight
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

        // 处理 profit 数据生成月度趋势
        if (profit.length > 0) {
          const monthly = {}
          profit.forEach(tx => {
            const month = tx.transactionAt ? tx.transactionAt.slice(0, 7) : '未知'
            if (!monthly[month]) monthly[month] = { cost: 0, sale: 0, profit: 0 }
            monthly[month].cost += parseFloat(tx.totalCostAmount) || 0
            monthly[month].sale += parseFloat(tx.saleAmount) || 0
            monthly[month].profit += parseFloat(tx.profit) || 0
          })
          this.monthlyProfit = Object.entries(monthly)
            .map(([month, v]) => ({ month, ...v }))
            .sort((a, b) => b.month.localeCompare(a.month))
            .slice(0, 6)
        }

        // 近期流水
        this.recentTransactions = profit.slice(0, 20)

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
.reports-page { background: #f5f5f5; min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 20px; }

.card {
  background: #fff;
  border-radius: 10px;
  margin: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.section-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.view-all { font-size: 13px; color: #1677ff; }

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.stat-label { font-size: 11px; color: #999; }
.stat-value { font-size: 16px; font-weight: 600; color: #333; margin-top: 4px; }

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
.hint { font-size: 10px; color: #999; }

.tx-list, .profit-list, .top-items { display: flex; flex-direction: column; gap: 8px; }
.tx-row, .profit-row, .top-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
  font-size: 12px;
}
.tx-type { font-size: 10px; padding: 1px 6px; border-radius: 8px; font-weight: 500; min-width: 44px; text-align: center; }
.type-in { background: #f6ffed; color: #52c41a; }
.type-out { background: #fff1f0; color: #ff4d4f; }
.type-transfer { background: #e6f7ff; color: #1677ff; }
.type-adjust { background: #fffbe6; color: #faad14; }
.type-check { background: #f9f0ff; color: #722ed1; }
.tx-item { flex: 1; font-size: 12px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tx-qty { font-weight: 600; }
.tx-time { color: #999; font-size: 11px; }
.profit-month { min-width: 60px; font-weight: 500; }
.profit-cost, .profit-sale { color: #666; font-size: 12px; }
.profit-profit { font-weight: 600; }
.top-rank { width: 28px; text-align: center; font-weight: 600; color: #1677ff; background: #e6f7ff; border-radius: 4px; }
.top-info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.top-name { font-size: 13px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.top-unit { font-size: 10px; color: #999; }
.top-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px; }

.category-list { display: flex; flex-direction: column; gap: 8px; }
.category-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.cat-name { width: 80px; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-bar { flex: 1; height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.cat-fill { height: 100%; background: #1677ff; border-radius: 4px; transition: width 0.3s; }
.cat-value { width: 70px; text-align: right; color: #666; font-family: monospace; }

.loading { text-align: center; padding: 40px; color: #999; }
</style>