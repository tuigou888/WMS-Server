<template>
  <view class="transactions-page">
    <!-- 筛选栏 -->
    <view class="filter-bar">
      <picker class="filter-picker" mode="selector" :range="typeOptions" :value="typeIndex" @change="onTypeChange">
        <view class="filter-item">
          <text class="filter-value">{{ typeOptions[typeIndex] }}</text>
          <text class="filter-arrow">▾</text>
        </view>
      </picker>
      <input class="search-input" v-model="keyword" placeholder="搜索物品编码/名称" @confirm="onSearch" />
    </view>

    <!-- 列表 -->
    <scroll-view class="list-container" scroll-y @scrolltolower="loadMore" :style="{ height: listHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading && list.length === 0" class="loading">加载中...</view>

      <view v-else-if="list.length === 0" class="empty-state">
        <text>📋</text>
        <text>暂无流水记录</text>
      </view>

      <view v-else class="list">
        <view v-for="tx in list" :key="tx.id" class="tx-card">
          <view class="tx-header">
            <text class="tx-type badge" :class="typeClass(tx.transactionType)">{{ typeText(tx.transactionType) }}</text>
            <text class="tx-time">{{ formatDateTime(tx.transactionAt) }}</text>
          </view>
          <view class="tx-body">
            <view class="tx-main">
              <text class="tx-name">{{ tx.itemName }} ({{ tx.itemCode }})</text>
              <text class="tx-ref" v-if="tx.referenceNo">单据: {{ tx.referenceNo }}</text>
            </view>
            <view class="tx-qty">
              <text class="qty" :class="tx.quantity > 0 ? 'value-green' : 'value-red'">
                {{ tx.quantity > 0 ? '+' : '' }}{{ formatNum(tx.quantity) }}
              </text>
              <text class="balance">结存: {{ formatNum(tx.balanceQuantity) }}</text>
            </view>
          </view>
          <view class="tx-amount" v-if="tx.transactionType === 'out'">
            <text class="amt">成本: ¥{{ formatMoney(tx.totalCostAmount) }}</text>
            <text class="amt value-green">售价: ¥{{ formatMoney(tx.saleAmount) }}</text>
            <text class="amt value-red">利润: ¥{{ formatMoney(tx.profit) }}</text>
          </view>
          <view class="tx-cost" v-else>
            <text class="amt">单价: ¥{{ formatMoney(tx.unitCost) }}</text>
            <text class="amt">均价: ¥{{ formatMoney(tx.avgCostAfter) }}</text>
          </view>
        </view>
      </view>

      <view v-if="loadingMore" class="loading-more">加载更多...</view>
      <view v-else-if="hasMore === false && list.length > 0" class="loading-more">已加载全部</view>
    </scroll-view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum, dateTime as formatDateTime } from '@/utils/format.js'

export default {
  data() {
    return {
      list: [],
      // 后端 /inventory/transactions limit 钳制 0..500（默认 100），一次取满 500 条前端过滤
      limit: 500,
      loading: false,
      loadingMore: false,
      refreshing: false,
      hasMore: false,
      listHeight: 0,
      typeIndex: 0,
      typeOptions: ['全部', '入库', '出库', '调拨', '盘点/调整', '红冲'],
      // 与后端 TransactionType 枚举对齐的分组过滤（原 transfer/adjust/check 等值后端不存在，选了永远为空）
      typeGroups: {
        1: ['in', 'gain_in', 'return_in'],
        2: ['out', 'loss_out', 'return_out'],
        3: ['transfer_in', 'transfer_out'],
        4: ['adjust_in', 'adjust_out'],
        5: ['reverse_in', 'reverse_out'],
      },
      keyword: '',
    }
  },
  onLoad() {
    this.setListHeight()
  },
  onShow() {
    this.loadList(true)
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.loadList(true)
  },
  methods: {
    setListHeight() {
      // windowHeight 已扣除原生导航栏（本页无 tabBar），只需再减页内筛选栏高度
      const sysInfo = uni.getSystemInfoSync()
      const filterHeight = 60
      this.listHeight = sysInfo.windowHeight - filterHeight
    },
    onSearch() { this.loadList(true) },
    onTypeChange(e) {
      this.typeIndex = e.detail.value
      this.loadList(true)
    },
    async loadList(reset = false) {
      this.loading = true
      try {
        const data = await api.transactions(this.limit)
        let filtered = data
        const group = this.typeGroups[this.typeIndex]
        if (group) filtered = filtered.filter(tx => group.includes(tx.transactionType))
        if (this.keyword) {
          const kw = this.keyword.toLowerCase()
          filtered = filtered.filter(tx =>
            (tx.itemCode || '').toLowerCase().includes(kw) ||
            (tx.itemName || '').toLowerCase().includes(kw)
          )
        }
        this.list = filtered
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        this.loading = false
        this.loadingMore = false
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
    },
    loadMore() {
      // 接口为一次性最新 N 条（limit 上限 500），无游标分页
    },
    typeText(type) {
      const map = {
        in: '采购入库', out: '销售出库',
        transfer_in: '调拨入', transfer_out: '调拨出',
        adjust_in: '盘盈入', adjust_out: '盘亏出',
        return_in: '退货入', return_out: '退供出',
        loss_out: '报损出', gain_in: '报溢入',
        reverse_in: '红冲入', reverse_out: '红冲出',
      }
      return map[type] || type
    },
    typeClass(type) {
      if (['in', 'gain_in', 'return_in'].includes(type)) return 'type-in'
      if (['out', 'loss_out', 'return_out'].includes(type)) return 'type-out'
      if (type.startsWith('transfer')) return 'type-transfer'
      if (type.startsWith('adjust')) return 'type-adjust'
      if (type.startsWith('reverse')) return 'type-check'
      return ''
    },
    formatMoney,
    formatNum,
    formatDateTime,
  },
}
</script>

<style scoped>
.transactions-page { min-height: 100vh; }

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: var(--wms-card);
  border-bottom: 2rpx solid var(--wms-border);
}
.filter-picker { width: 200rpx; }
.filter-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 72rpx;
  background: var(--wms-bg);
  border-radius: 12rpx;
}
.filter-value { font-size: 26rpx; font-weight: 500; color: var(--wms-primary); }
.filter-arrow { font-size: 20rpx; color: var(--wms-ink-3); }
.search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: var(--wms-ink);
  background: var(--wms-bg);
}

/* 列表 */
.list-container { width: 100%; box-sizing: border-box; padding: 20rpx 24rpx 40rpx; }
.list { display: flex; flex-direction: column; gap: 20rpx; }
.tx-card {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.tx-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.tx-time { font-size: 22rpx; color: var(--wms-ink-3); }
.type-in { background: var(--wms-success-bg); color: var(--wms-success); }
.type-out { background: var(--wms-danger-bg); color: var(--wms-danger); }
.type-transfer { background: var(--wms-primary-bg); color: var(--wms-primary); }
.type-adjust { background: var(--wms-warning-bg); color: var(--wms-warning); }
.type-check { background: var(--wms-bg); color: var(--wms-ink-2); }
.tx-body { display: flex; justify-content: space-between; align-items: flex-start; gap: 24rpx; margin-bottom: 16rpx; }
.tx-main { flex: 1; min-width: 0; }
.tx-name { display: block; font-size: 28rpx; font-weight: 600; color: var(--wms-ink); margin-bottom: 6rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tx-ref { font-size: 22rpx; color: var(--wms-ink-3); display: block; }
.tx-qty { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; flex-shrink: 0; }
.qty { font-size: 32rpx; font-weight: 600; }
.balance { font-size: 22rpx; color: var(--wms-ink-3); }
.tx-amount, .tx-cost {
  display: flex;
  gap: 24rpx;
  font-size: 24rpx;
  color: var(--wms-ink-2);
  padding-top: 16rpx;
  border-top: 2rpx solid var(--wms-border);
}

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 60rpx;
  color: var(--wms-ink-3);
  font-size: 26rpx;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; opacity: 0.5; }
</style>