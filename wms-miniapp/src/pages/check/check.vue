<template>
  <view class="check-page">
    <scroll-view class="content" scroll-y @scrolltolower="loadMore" :style="{ height: contentHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading && list.length === 0" class="loading">加载中...</view>

      <view v-else-if="list.length === 0" class="empty-state">
        <text>📋</text>
        <text>暂无盘点任务</text>
        <text class="hint">请在 Web 端创建盘点计划</text>
      </view>

      <view v-else class="list">
        <view v-for="task in list" :key="task.id" class="task-card">
          <view class="task-header">
            <view class="task-no">{{ task.stocktakeNo }}</view>
            <view class="task-status" :class="['badge', statusClass(task.status)]">{{ statusText(task.status) }}</view>
          </view>
          <view class="task-info">
            <view class="info-row">
              <text class="label">仓库</text>
              <text>{{ task.warehouseName }}</text>
            </view>
            <view class="info-row">
              <text class="label">创建时间</text>
              <text>{{ formatDate(task.createdAt) }}</text>
            </view>
            <view class="info-row">
              <text class="label">明细行数</text>
              <text>{{ (task.lines && task.lines.length) || 0 }}</text>
            </view>
            <view class="info-row">
              <text class="label">备注</text>
              <text>{{ task.remark || '' }}</text>
            </view>
          </view>
          <view class="task-actions">
            <button v-if="task.status === 'DRAFT'" class="btn-count" @tap.stop="goCount(task.id)">录入实盘</button>
            <button v-if="task.status === 'DRAFT'" class="btn-view" @tap.stop="viewDetail(task.id)">查看详情</button>
            <button v-else class="btn-view" @tap.stop="viewDetail(task.id)">查看详情</button>
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
import { date as formatDate } from '@/utils/format.js'

export default {
  data() {
    return {
      list: [],
      page: 1,
      pageSize: 20,
      loading: false,
      loadingMore: false,
      refreshing: false,
      hasMore: true,
      contentHeight: 0,
    }
  },
  onLoad() {
    this.setContentHeight()
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
    setContentHeight() {
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      this.contentHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight
    },
    async loadList(reset = false) {
      if (reset) {
        this.page = 1
        this.list = []
        this.hasMore = true
      }
      this.loading = true
      try {
        const params = { page: this.page, pageSize: this.pageSize }
        const pageData = await api.stocktakes(params)
        const data = pageData.records || []
        if (reset) this.list = []
        this.list.push(...data)
        this.hasMore = data.length >= this.pageSize
        this.page++
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
      if (!this.loadingMore && this.hasMore && !this.loading) {
        this.loadingMore = true
        this.loadList()
      }
    },
    statusText(status) {
      const map = { DRAFT: '草稿', IN_PROGRESS: '盘点中', CONFIRMED: '已确认', CANCELLED: '已取消' }
      return map[status] || status
    },
    statusClass(status) {
      const map = { DRAFT: 'badge-default', IN_PROGRESS: 'badge-info', CONFIRMED: 'badge-success', CANCELLED: 'badge-error' }
      return map[status] || 'badge-default'
    },
    goCount(id) {
      uni.navigateTo({ url: `/pages/check-count/check-count?id=${id}` })
    },
    viewDetail(id) {
      uni.navigateTo({ url: `/pages/document-detail/document-detail?id=${id}&type=check` })
    },
    formatDate,
  },
}
</script>

<style scoped>
.check-page { background: #f5f5f5; min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 40rpx; }

.task-card {
  background: #fff;
  border-radius: 20rpx;
  margin: 24rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.04);
}
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.task-no { font-size: 30rpx; font-weight: 600; color: #333; font-family: monospace; }
.task-info { display: flex; flex-direction: column; gap: 12rpx; margin-bottom: 24rpx; }
.info-row { display: flex; justify-content: space-between; font-size: 26rpx; }
.info-row .label { color: #999; }
.info-row text:last-child { color: #333; }
.task-actions { display: flex; gap: 16rpx; }
.btn-count, .btn-view {
  flex: 1;
  padding: 20rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
  border: none;
}
.btn-count { background: #52c41a; color: #fff; }
.btn-view { background: #f0f0f0; color: #333; border: 2rpx solid #d9d9d9; }

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 60rpx;
  color: #999;
  font-size: 28rpx;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; opacity: 0.5; }
.hint { font-size: 24rpx; color: #ccc; }
</style>
