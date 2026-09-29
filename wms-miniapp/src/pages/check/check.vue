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
        <view v-for="task in list" :key="task.id" class="card task-card">
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
            <button v-if="task.status === 'DRAFT'" class="btn-primary btn-flex" @tap.stop="goCount(task.id)">录入实盘</button>
            <button v-if="task.status === 'DRAFT'" class="btn-secondary btn-flex" @tap.stop="viewDetail(task.id)">查看详情</button>
            <button v-else class="btn-secondary btn-flex" @tap.stop="viewDetail(task.id)">查看详情</button>
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
      reqSeq: 0,
      contentHeight: 0,
    }
  },
  onLoad() {
    this.setContentHeight()
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
      // windowHeight 已扣除原生导航栏（本页无 tabBar），无需再减
      this.contentHeight = uni.getSystemInfoSync().windowHeight
    },
    async loadList(reset = false) {
      // reset 允许打断在途翻页，过期响应直接丢弃
      if (reset) {
        this.reqSeq++
        this.page = 1
        this.list = []
        this.hasMore = true
      } else if (this.loading) {
        return
      }
      const seq = this.reqSeq
      this.loading = true
      try {
        const params = { page: this.page, pageSize: this.pageSize }
        const pageData = await api.stocktakes(params)
        if (seq !== this.reqSeq) return
        const data = pageData.records || []
        this.list.push(...data)
        this.hasMore = data.length >= this.pageSize
        this.page++
      } catch (e) {
        if (seq !== this.reqSeq) return
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        if (seq === this.reqSeq) {
          this.loading = false
          this.loadingMore = false
          this.refreshing = false
          uni.stopPullDownRefresh()
        }
      }
    },
    loadMore() {
      if (!this.loadingMore && this.hasMore && !this.loading) {
        this.loadingMore = true
        this.loadList()
      }
    },
    statusText(status) {
      const map = { DRAFT: '草稿', APPROVED: '已审核', REJECTED: '已驳回', COMPLETED: '已完成' }
      return map[status] || status
    },
    statusClass(status) {
      const map = { DRAFT: 'badge-default', APPROVED: 'badge-info', REJECTED: 'badge-error', COMPLETED: 'badge-success' }
      return map[status] || 'badge-default'
    },
    goCount(id) {
      uni.navigateTo({ url: `/pages/check-count/check-count?id=${id}` })
    },
    viewDetail(id) {
      uni.navigateTo({ url: `/pages/document-detail/document-detail?id=${id}&type=stocktakes` })
    },
    formatDate,
  },
}
</script>

<style scoped>
.check-page { min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 40rpx; }

/* 单据号 chip：蓝浅底 + 主蓝 + 等宽字体 */
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.task-no {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-family: monospace;
  letter-spacing: 1rpx;
}
/* 信息区：分隔线拉开信息层级 */
.task-info {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid var(--wms-border);
  margin-bottom: 24rpx;
}
.info-row { display: flex; justify-content: space-between; font-size: 26rpx; }
.info-row .label { color: var(--wms-ink-3); margin-bottom: 0; }
.info-row text:last-child { color: var(--wms-ink); }

.task-actions { display: flex; gap: 16rpx; }
.task-actions button {
  flex: 1;
  margin: 0;
  padding-left: 0;
  padding-right: 0;
  font-size: 28rpx;
  line-height: 1.6;
}

.loading, .loading-more { text-align: center; padding: 60rpx; color: var(--wms-ink-3); font-size: 28rpx; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; }
.hint { font-size: 24rpx; color: var(--wms-ink-3); }
</style>
