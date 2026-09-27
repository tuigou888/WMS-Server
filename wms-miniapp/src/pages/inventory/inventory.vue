<template>
  <view class="inventory-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索物品编码/名称" @confirm="search" />
      <button class="search-btn btn-primary" @tap="search">搜索</button>
    </view>

    <!-- 筛选 -->
    <view class="filter-bar">
      <picker class="filter-picker" mode="selector" :range="warehouseNames" :value="warehouseIndex" @change="onWarehouseChange">
        <view class="filter-item">
          <text class="filter-value">{{ warehouseNames[warehouseIndex] || '全部仓库' }}</text>
          <text class="filter-arrow">▾</text>
        </view>
      </picker>
      <picker class="filter-picker" mode="selector" :range="['全部', '有库存', '预警', '零库存']" :value="statusIndex" @change="onStatusChange">
        <view class="filter-item">
          <text class="filter-value">{{ ['全部', '有库存', '预警', '零库存'][statusIndex] }}</text>
          <text class="filter-arrow">▾</text>
        </view>
      </picker>
    </view>

    <!-- 列表 -->
    <scroll-view class="list-container" scroll-y @scrolltolower="loadMore" :style="{ height: listHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading && list.length === 0" class="loading">加载中...</view>

      <view v-else-if="list.length === 0" class="empty-state">
        <text>📦</text>
        <text>暂无库存数据</text>
      </view>

      <view v-else class="list">
        <navigator v-for="inv in list" :key="inv.id" :url="`/pages/item-detail/item-detail?id=${inv.itemId}`" class="list-item" hover-class="list-item-hover">
          <view class="item-main">
            <view class="item-header">
              <text class="item-name">{{ inv.itemName }}</text>
              <text class="item-code">{{ inv.itemCode }}</text>
            </view>
            <view class="item-meta">
              <text class="meta" v-if="inv.categoryName">{{ inv.categoryName }}</text>
              <text class="meta" v-if="inv.warehouseName">📍 {{ inv.warehouseName }}</text>
            </view>
          </view>
          <view class="item-stats">
            <view class="qty">
              <text class="qty-value">{{ formatNum(inv.quantity) }}</text>
              <text class="qty-unit">{{ inv.unit }}</text>
            </view>
            <view class="stat-row">
              <view class="stat">
                <text class="stat-label">库存金额</text>
                <text class="stat-value value-green">¥{{ formatMoney(inv.totalAmount) }}</text>
              </view>
              <view class="stat">
                <text class="stat-label">成本单价</text>
                <text class="stat-value">¥{{ formatMoney(inv.avgCost) }}</text>
              </view>
            </view>
          </view>
        </navigator>
      </view>

      <view v-if="loadingMore" class="loading-more">加载更多...</view>
      <view v-else-if="hasMore === false && list.length > 0" class="loading-more">已加载全部</view>
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
      keyword: '',
      list: [],
      page: 1,
      pageSize: 20,
      loading: false,
      loadingMore: false,
      refreshing: false,
      hasMore: true,
      listHeight: 0,
      warehouseIndex: 0,
      statusIndex: 0,
      warehouseNames: ['全部仓库'],
      warehouses: [],
    }
  },
  onLoad() {
    this.setListHeight()
    this.loadWarehouses()
    this.search()
  },
  onShow() {
    this.search()
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.search(true)
  },
  methods: {
    setListHeight() {
      // windowHeight 已扣除原生导航栏与 tabBar，只需再减页内搜索栏高度
      const sysInfo = uni.getSystemInfoSync()
      const searchHeight = 90
      this.listHeight = sysInfo.windowHeight - searchHeight
    },
    async loadWarehouses() {
      try {
        const list = await api.warehouses(true)
        this.warehouses = list
        this.warehouseNames = ['全部仓库', ...list.map(w => w.name)]
      } catch (e) {
        console.warn('加载仓库失败:', e)
      }
    },
    onWarehouseChange(e) {
      this.warehouseIndex = e.detail.value
      this.search(true)
    },
    onStatusChange(e) {
      this.statusIndex = e.detail.value
      this.search(true)
    },
    async search(reset = false) {
      if (reset) {
        this.page = 1
        this.list = []
        this.hasMore = true
      }
      this.loading = true
      try {
        const params = {
          page: this.page,
          pageSize: this.pageSize,
        }
        if (this.keyword) params.keyword = this.keyword
        if (this.warehouseIndex > 0) params.warehouseId = this.warehouses[this.warehouseIndex - 1].id

        const pageData = await api.inventory(params)
        const data = pageData.records || []
        if (reset) this.list = []
        this.list.push(...data)
        this.hasMore = data.length === this.pageSize && this.list.length < pageData.total
        this.page++
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        this.loading = false
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
    },
    loadMore() {
      if (!this.loadingMore && this.hasMore && !this.loading) {
        this.loadingMore = true
        this.search().finally(() => { this.loadingMore = false })
      }
    },
    formatMoney,
    formatNum,
  },
}
</script>

<style scoped>
.inventory-page { min-height: 100vh; }

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--wms-card);
}
.search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: var(--wms-ink);
  background: var(--wms-bg);
}
.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  height: 72rpx;
  padding: 0 36rpx;
  font-size: 28rpx;
  border-radius: 12rpx;
  box-shadow: 0 2rpx 8rpx rgba(22, 119, 255, 0.25);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 24rpx 16rpx;
  background: var(--wms-card);
  border-bottom: 2rpx solid var(--wms-border);
}
.filter-picker { flex: 1; }
.filter-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 64rpx;
  background: var(--wms-bg);
  border-radius: 12rpx;
}
.filter-value { font-size: 26rpx; font-weight: 500; color: var(--wms-primary); }
.filter-arrow { font-size: 20rpx; color: var(--wms-ink-3); }

/* 列表 */
.list-container { width: 100%; box-sizing: border-box; }
.list { padding: 20rpx 24rpx 40rpx; display: flex; flex-direction: column; gap: 20rpx; }
.list-item {
  display: flex;
  justify-content: space-between;
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
  text-decoration: none;
}
.list-item-hover { background: var(--wms-bg); }
.item-main { flex: 1; min-width: 0; }
.item-header { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.item-name { flex: 1; min-width: 0; font-size: 30rpx; font-weight: 600; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-code {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  white-space: nowrap;
}
.item-meta { display: flex; gap: 20rpx; font-size: 22rpx; color: var(--wms-ink-3); flex-wrap: wrap; }
.meta { line-height: 1.5; }
.item-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 12rpx; margin-left: 24rpx; }
.qty { display: flex; align-items: baseline; gap: 6rpx; }
.qty-value { font-size: 36rpx; font-weight: 600; color: var(--wms-ink); }
.qty-unit { font-size: 22rpx; color: var(--wms-ink-3); }
.stat-row { display: flex; gap: 28rpx; }
.stat { display: flex; flex-direction: column; align-items: flex-end; gap: 2rpx; }
.stat-label { font-size: 22rpx; color: var(--wms-ink-3); }
.stat-value { font-size: 26rpx; font-weight: 600; color: var(--wms-ink-2); }
.stat-value.value-green { color: var(--wms-success); }

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 60rpx;
  color: var(--wms-ink-3);
  font-size: 26rpx;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; opacity: 0.5; }
</style>
