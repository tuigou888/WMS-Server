<template>
  <view class="inventory-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索物品编码/名称" @confirm="search" />
      <button class="search-btn" @tap="search">搜索</button>
    </view>

    <!-- 筛选 -->
    <view class="filter-bar">
      <picker class="filter-picker" mode="selector" :range="warehouseNames" :value="warehouseIndex" @change="onWarehouseChange">
        <view class="filter-item">{{ warehouseNames[warehouseIndex] || '全部仓库' }}</view>
      </picker>
      <picker class="filter-picker" mode="selector" :range="['全部', '有库存', '预警', '零库存']" :value="statusIndex" @change="onStatusChange">
        <view class="filter-item">{{ ['全部', '有库存', '预警', '零库存'][statusIndex] }}</view>
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
              <text class="item-code">{{ inv.itemCode }}</text>
              <text class="item-name">{{ inv.itemName }}</text>
            </view>
            <view class="item-meta">
              <text class="meta">{{ inv.categoryName || '' }}</text>
              <text class="meta">{{ inv.unit }}</text>
              <text class="meta" v-if="inv.warehouseName">📍 {{ inv.warehouseName }}</text>
            </view>
          </view>
          <view class="item-stats">
            <view class="stat">
              <text class="stat-label">库存</text>
              <text class="stat-value">{{ formatNum(inv.quantity) }} {{ inv.unit }}</text>
            </view>
            <view class="stat">
              <text class="stat-label">金额</text>
              <text class="stat-value value-green">¥{{ formatMoney(inv.totalAmount) }}</text>
            </view>
            <view class="stat">
              <text class="stat-label">成本</text>
              <text class="stat-value">¥{{ formatMoney(inv.avgCost) }}</text>
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
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      const searchHeight = 90
      this.listHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight - searchHeight
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
.inventory-page { background: #f5f5f5; min-height: 100vh; }

.search-bar {
  display: flex;
  gap: 16rpx;
  padding: 24rpx 32rpx;
  background: #fff;
  border-bottom: 2rpx solid #f0f0f0;
}
.search-input {
  flex: 1;
  padding: 20rpx 28rpx;
  border: 2rpx solid #d9d9d9;
  border-radius: 12rpx;
  font-size: 30rpx;
  background: #fafafa;
}
.search-btn {
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 0 40rpx;
  font-size: 30rpx;
}

.filter-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 32rpx;
  background: #fff;
  border-bottom: 2rpx solid #f0f0f0;
}
.filter-picker { flex: 1; }
.filter-item {
  padding: 16rpx 24rpx;
  background: #fafafa;
  border: 2rpx solid #d9d9d9;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: #666;
  text-align: center;
}

.list-container { width: 100%; box-sizing: border-box; }
.list { padding: 16rpx 32rpx 40rpx; display: flex; flex-direction: column; gap: 16rpx; }
.list-item {
  display: flex;
  justify-content: space-between;
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.04);
  text-decoration: none;
}
.list-item-hover { background: #fafafa; }
.item-main { flex: 1; min-width: 0; }
.item-header { display: flex; align-items: baseline; gap: 16rpx; margin-bottom: 8rpx; }
.item-code { font-size: 24rpx; color: #1677ff; background: #e6f7ff; padding: 2rpx 12rpx; border-radius: 6rpx; white-space: nowrap; }
.item-name { font-size: 30rpx; font-weight: 500; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { display: flex; gap: 20rpx; font-size: 22rpx; color: #999; flex-wrap: wrap; }
.meta { background: #f0f0f0; padding: 2rpx 12rpx; border-radius: 6rpx; }
.item-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; margin-left: 24rpx; }
.stat { display: flex; flex-direction: column; align-items: flex-end; }
.stat-label { font-size: 20rpx; color: #999; }
.stat-value { font-size: 26rpx; font-weight: 600; }

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 60rpx;
  color: #999;
  font-size: 28rpx;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; opacity: 0.5; }
</style>
