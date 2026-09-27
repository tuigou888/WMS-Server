<template>
  <view class="doc-list-page">
    <!-- 筛选栏 -->
    <view class="filter-bar">
      <picker class="filter-picker" mode="selector" :range="typeOptions" :value="typeIndex" @change="onTypeChange">
        <view class="filter-item">{{ typeOptions[typeIndex] }}</view>
      </picker>
      <picker class="filter-picker" mode="selector" :range="statusOptions" :value="statusIndex" @change="onStatusChange">
        <view class="filter-item">{{ statusOptions[statusIndex] }}</view>
      </picker>
    </view>

    <!-- 列表 -->
    <scroll-view class="list-container" scroll-y :style="{ height: listHeight + 'px' }" @refresh="onRefresh" @scrolltolower="loadMore" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading && list.length === 0" class="loading">加载中...</view>

      <view v-else-if="list.length === 0" class="empty-state">
        <text>📄</text>
        <text>暂无单据</text>
      </view>

      <view v-else class="list">
        <navigator v-for="doc in list" :key="doc.id" :url="detailUrl(doc)" class="list-item" hover-class="list-item-hover">
          <view class="item-main">
            <view class="item-header">
              <text class="doc-no">{{ doc.documentNo }}</text>
              <text class="doc-status" :class="['badge', statusClass(doc.status)]">{{ statusText(doc.status) }}</text>
            </view>
            <view class="item-meta">
              <text class="meta">{{ doc.typeText }}</text>
              <text class="meta" v-if="doc.partnerName">{{ doc.partnerName }}</text>
              <text class="meta">{{ formatDate(doc.businessDate) }}</text>
            </view>
            <view class="item-stats">
              <text class="stat">数量: <text class="stat-num">{{ formatNum(doc.totalQuantity) }}</text></text>
              <text class="stat" v-if="doc.totalAmount !== null">金额: <text class="stat-num" :class="doc.type === 'IN' || doc.type === 'RETURN_IN' ? 'value-green' : 'value-red'">¥{{ formatMoney(doc.totalAmount) }}</text></text>
            </view>
          </view>
          <text class="arrow">▶</text>
        </navigator>
      </view>

      <view v-if="loadingMore" class="loading-more">加载更多...</view>
    </scroll-view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum, date as formatDate } from '@/utils/format.js'

const TYPE_MAP = { IN: '采购入库', OUT: '销售出库', RETURN_IN: '退货入库', RETURN_OUT: '退回供应商' }
const IN_TYPES = ['IN', 'RETURN_IN']
const OUT_TYPES = ['OUT', 'RETURN_OUT']

export default {
  data() {
    return {
      list: [],
      loading: false,
      loadingMore: false,
      refreshing: false,
      hasMore: true,
      page: 1,
      pageSize: 20,
      listHeight: 0,
      typeIndex: 0,
      statusIndex: 0,
      typeOptions: ['全部类型', '入库单', '出库单', '调拨单', '盘点单'],
      statusOptions: ['全部状态', '草稿', '已审核', '已执行', '已取消'],
      typeOptionsCache: {},
    }
  },
  onLoad() {
    this.setListHeight()
    this.loadList(true)
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
    onTypeChange(e) {
      this.typeIndex = e.detail.value
      this.loadList(true)
    },
    onStatusChange(e) {
      this.statusIndex = e.detail.value
      this.loadList(true)
    },
    statusText(status) {
      const map = { DRAFT: '草稿', APPROVED: '已审核', COMPLETED: '已执行', CANCELLED: '已取消', CONFIRMED: '已确认' }
      return map[status] || status
    },
    statusClass(status) {
      const map = { DRAFT: 'badge-default', APPROVED: 'badge-info', COMPLETED: 'badge-success', CANCELLED: 'badge-error', CONFIRMED: 'badge-success' }
      return map[status] || 'badge-default'
    },
    enrichDoc(doc, kind) {
      if (kind === 'stocktake') {
        doc.typeText = '库存盘点'
        const lines = doc.lines || []
        doc.totalQuantity = lines.reduce((s, l) => s + (parseFloat(l.bookQuantity) || 0), 0)
        doc.totalAmount = null
        return doc
      }
      if (kind === 'transfer') {
        doc.typeText = '库存调拨'
        const lines = doc.lines || []
        doc.totalQuantity = lines.reduce((s, l) => s + (parseFloat(l.quantity) || 0), 0)
        doc.totalAmount = null
        return doc
      }
      doc.typeText = TYPE_MAP[doc.type] || doc.type
      const lines = doc.lines || []
      doc.totalQuantity = lines.reduce((s, l) => s + (parseFloat(l.quantity) || 0), 0)
      doc.totalAmount = lines.reduce((s, l) => s + (parseFloat(l.quantity) || 0) * (parseFloat(l.unitPrice) || 0), 0)
      return doc
    },
    detailUrl(doc) {
      const kind = this.typeIndex === 3 ? 'transfers' : this.typeIndex === 4 ? 'stocktakes' : doc.type
      if (kind === 'transfers') {
        uni.setStorageSync('wms_transfer_detail', doc)
      }
      return `/pages/document-detail/document-detail?id=${doc.id}&type=${kind}`
    },
    async loadList(reset = false) {
      if (reset) {
        this.page = 1
        this.list = []
        this.hasMore = true
      }
      if (this.loading || this.loadingMore || !this.hasMore) return
      this.loadingMore = this.list.length > 0
      this.loading = true
      try {
        let pageData = { records: [] }
        let kind = 'document'
        const params = { page: this.page, pageSize: this.pageSize }
        if (this.typeIndex === 3) {
          pageData = await api.transfers(params)
          kind = 'transfer'
        } else if (this.typeIndex === 4) {
          pageData = await api.stocktakes(params)
          kind = 'stocktake'
        } else {
          pageData = await api.documents(params)
        }
        let data = pageData.records || []
        if (this.typeIndex === 1) data = data.filter(d => IN_TYPES.includes(d.type))
        else if (this.typeIndex === 2) data = data.filter(d => OUT_TYPES.includes(d.type))
        const statusMap = { 1: 'DRAFT', 2: 'APPROVED', 3: 'COMPLETED', 4: 'CANCELLED' }
        if (this.statusIndex > 0 && statusMap[this.statusIndex]) {
          data = data.filter(d => d.status === statusMap[this.statusIndex])
        }
        this.list.push(...data.map(d => this.enrichDoc(d, kind)))
        this.hasMore = (pageData.records || []).length === this.pageSize
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
      if (!this.loading && !this.loadingMore && this.hasMore) this.loadList(false)
    },
    onRefresh() {
      this.refreshing = true
      this.loadList(true)
    },
    formatMoney,
    formatNum,
    formatDate,
  },
}
</script>

<style scoped>
.doc-list-page { min-height: 100vh; }

/* 筛选栏：白底横条，选中值主蓝高亮 */
.filter-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--wms-card);
  border-bottom: 2rpx solid var(--wms-border);
}
.filter-picker { flex: 1; }
.filter-item {
  padding: 14rpx 24rpx;
  background: var(--wms-bg);
  border: 2rpx solid var(--wms-border);
  border-radius: 12rpx;
  font-size: 26rpx;
  color: var(--wms-primary);
  font-weight: 500;
  text-align: center;
}

.list-container { width: 100%; box-sizing: border-box; }
.list { padding: 20rpx 24rpx 40rpx; display: flex; flex-direction: column; gap: 20rpx; }
.list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
  text-decoration: none;
}
.list-item-hover { background: var(--wms-bg); }
.item-main { flex: 1; min-width: 0; }
.item-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 12rpx; flex-wrap: wrap; }
/* 单据号 chip：蓝浅底 + 主蓝 + 等宽字体 */
.doc-no {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-family: monospace;
  letter-spacing: 1rpx;
}
.item-meta { display: flex; gap: 12rpx; font-size: 22rpx; color: var(--wms-ink-2); flex-wrap: wrap; margin-bottom: 12rpx; }
.meta { background: var(--wms-bg); padding: 2rpx 12rpx; border-radius: 6rpx; }
.item-stats { display: flex; gap: 24rpx; font-size: 24rpx; }
.stat { color: var(--wms-ink-3); }
/* 关键数字突出：加粗放大，入库绿/出库红 */
.stat-num { color: var(--wms-ink); font-weight: 600; font-size: 26rpx; }
.stat-num.value-green { color: var(--wms-success); }
.stat-num.value-red { color: var(--wms-danger); }
.arrow { font-size: 22rpx; color: var(--wms-ink-3); margin-left: 20rpx; }

.loading, .loading-more { text-align: center; padding: 60rpx; color: var(--wms-ink-3); font-size: 28rpx; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; }
</style>
