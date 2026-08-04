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
    <scroll-view class="list-container" scroll-y :style="{ height: listHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
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
              <text class="stat">数量: {{ formatNum(doc.totalQuantity) }}</text>
              <text class="stat value-green" v-if="doc.totalAmount !== null">金额: ¥{{ formatMoney(doc.totalAmount) }}</text>
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
        this.hasMore = true
      }
      this.loading = true
      try {
        let data = []
        let kind = 'document'
        if (this.typeIndex === 3) {
          data = await api.transfers({})
          kind = 'transfer'
        } else if (this.typeIndex === 4) {
          data = await api.stocktakes({})
          kind = 'stocktake'
        } else {
          data = await api.documents({})
          if (this.typeIndex === 1) {
            data = data.filter(d => IN_TYPES.includes(d.type))
          } else if (this.typeIndex === 2) {
            data = data.filter(d => OUT_TYPES.includes(d.type))
          }
        }
        if (!Array.isArray(data)) data = data.records || []
        const statusMap = { 1: 'DRAFT', 2: 'APPROVED', 3: 'COMPLETED', 4: 'CANCELLED' }
        if (this.statusIndex > 0 && statusMap[this.statusIndex]) {
          data = data.filter(d => d.status === statusMap[this.statusIndex])
        }
        this.list = data.map(d => this.enrichDoc(d, kind))
        this.hasMore = false
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        this.loading = false
        this.loadingMore = false
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
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
.doc-list-page { background: #f5f5f5; min-height: 100vh; }

.filter-bar {
  display: flex;
  gap: 8px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.filter-picker { flex: 1; }
.filter-item {
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 13px;
  color: #666;
  text-align: center;
}

.list-container { width: 100%; box-sizing: border-box; }
.list { padding: 8px 16px 20px; display: flex; flex-direction: column; gap: 8px; }
.list-item {
  display: flex;
  justify-content: space-between;
  background: #fff;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  text-decoration: none;
}
.list-item-hover { background: #fafafa; }
.item-main { flex: 1; min-width: 0; }
.item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; flex-wrap: wrap; }
.doc-no { font-size: 14px; font-weight: 600; color: #333; font-family: monospace; }
.item-meta { display: flex; gap: 10px; font-size: 11px; color: #999; flex-wrap: wrap; margin-bottom: 4px; }
.meta { background: #f0f0f0; padding: 1px 6px; border-radius: 3px; }
.item-stats { display: flex; gap: 12px; font-size: 12px; }
.stat { color: #666; }
.arrow { font-size: 12px; color: #999; margin-left: 12px; }

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 30px;
  color: #999;
  font-size: 14px;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.empty-state text:first-child { font-size: 48px; opacity: 0.5; }
</style>