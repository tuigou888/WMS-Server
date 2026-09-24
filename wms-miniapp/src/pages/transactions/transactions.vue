<template>
  <view class="transactions-page">
    <!-- 筛选栏 -->
    <view class="filter-bar">
      <picker class="filter-picker" mode="selector" :range="typeOptions" :value="typeIndex" @change="onTypeChange">
        <view class="filter-item">{{ typeOptions[typeIndex] }}</view>
      </picker>
      <input class="search-input" v-model="keyword" placeholder="搜索物品编码/名称" @confirm="search" />
    </view>

    <!-- 列表 -->
    <scroll-view class="list-container" scroll-y @scrolltolower="loadMore" :style="{ height: listHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading && list.length === 0" class="loading">加载中...</view>

      <view v-else-if="list.length === 0" class="empty-state">
        <text>📋</text>
        <text>暂无流水记录</text>
      </view>

      <view v-else class="list">
        <view v-for="tx in list" :key="tx.id" class="tx-item">
          <view class="tx-header">
            <text class="tx-type" :class="typeClass(tx.transactionType)">{{ typeText(tx.transactionType) }}</text>
            <text class="tx-time">{{ formatDateTime(tx.transactionAt) }}</text>
          </view>
          <view class="tx-main">
            <text class="tx-item">{{ tx.itemName }} ({{ tx.itemCode }})</text>
            <text class="tx-ref" v-if="tx.referenceNo">单据: {{ tx.referenceNo }}</text>
          </view>
          <view class="tx-qty">
            <text class="qty" :class="tx.quantity > 0 ? 'value-green' : 'value-red'">
              {{ tx.quantity > 0 ? '+' : '' }}{{ formatNum(tx.quantity) }}
            </text>
            <text class="balance">结存: {{ formatNum(tx.balanceQuantity) }}</text>
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
      limit: 100,
      loading: false,
      loadingMore: false,
      refreshing: false,
      hasMore: true,
      listHeight: 0,
      typeIndex: 0,
      typeOptions: ['全部', '入库', '出库', '调拨', '调整', '盘点'],
      keyword: '',
    }
  },
  onLoad() {
    this.setListHeight()
    this.loadList()
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
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      const filterHeight = 60
      this.listHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight - filterHeight
    },
    onTypeChange(e) {
      this.typeIndex = e.detail.value
      this.loadList(true)
    },
    async loadList(reset = false) {
      this.loading = true
      try {
        const data = await api.transactions(this.limit)
        let filtered = data
        if (this.typeIndex > 0) {
          const typeMap = ['', 'in', 'out', 'transfer', 'adjust', 'check']
          filtered = data.filter(tx => tx.transactionType === typeMap[this.typeIndex])
        }
        if (this.keyword) {
          const kw = this.keyword.toLowerCase()
          filtered = filtered.filter(tx =>
            tx.itemCode.toLowerCase().includes(kw) ||
            tx.itemName.toLowerCase().includes(kw)
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
      // 后端已返回全部，前端只做筛选
    },
    typeText(type) {
      const map = { in: '入库', out: '出库', transfer: '调拨', adjust: '调整', check: '盘点' }
      return map[type] || type
    },
    typeClass(type) {
      const map = { in: 'type-in', out: 'type-out', transfer: 'type-transfer', adjust: 'type-adjust', check: 'type-check' }
      return map[type] || ''
    },
    formatMoney,
    formatNum,
    formatDateTime,
  },
}
</script>

<style scoped>
.transactions-page { background: #f5f5f5; min-height: 100vh; }

.filter-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 32rpx;
  background: #fff;
  border-bottom: 2rpx solid #f0f0f0;
}
.filter-picker { width: 200rpx; }
.filter-item {
  padding: 16rpx 24rpx;
  background: #fafafa;
  border: 2rpx solid #d9d9d9;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: #666;
  text-align: center;
}
.search-input {
  flex: 1;
  padding: 16rpx 24rpx;
  border: 2rpx solid #d9d9d9;
  border-radius: 12rpx;
  font-size: 26rpx;
  background: #fafafa;
}

.list-container { width: 100%; box-sizing: border-box; padding: 16rpx 32rpx 40rpx; }
.list { display: flex; flex-direction: column; gap: 16rpx; }
.tx-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.04);
}
.tx-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.tx-type {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  font-weight: 500;
}
.type-in { background: #f6ffed; color: #52c41a; border: 2rpx solid #b7eb8f; }
.type-out { background: #fff1f0; color: #ff4d4f; border: 2rpx solid #ffa39e; }
.type-transfer { background: #e6f7ff; color: #1677ff; border: 2rpx solid #91d5ff; }
.type-adjust { background: #fffbe6; color: #faad14; border: 2rpx solid #ffe58f; }
.type-check { background: #f9f0ff; color: #722ed1; border: 2rpx solid #d3adf7; }
.tx-time { font-size: 22rpx; color: #999; }
.tx-main { margin-bottom: 16rpx; }
.tx-item { font-size: 28rpx; font-weight: 500; color: #333; display: block; margin-bottom: 4rpx; }
.tx-ref { font-size: 22rpx; color: #999; display: block; }
.tx-qty { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; font-size: 26rpx; }
.qty { font-weight: 600; }
.balance { font-size: 24rpx; color: #999; }
.tx-amount, .tx-cost { display: flex; gap: 24rpx; font-size: 24rpx; }
.amt { color: #666; }

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 60rpx;
  color: #999;
  font-size: 28rpx;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; opacity: 0.5; }
</style>