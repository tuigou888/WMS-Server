<template>
  <view class="doc-detail-page">
    <scroll-view class="content" scroll-y :style="{ height: contentHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading" class="loading">加载中...</view>

      <view v-else-if="isStocktake">
        <!-- 盘点单详情 -->
        <view class="card">
          <view class="header-row">
            <text class="doc-no">{{ doc.stocktakeNo }}</text>
            <text class="doc-status" :class="['badge', statusClass(doc.status)]">{{ statusText(doc.status) }}</text>
          </view>
          <view class="header-info">
            <view class="info-row">
              <text class="label">仓库</text>
              <text>{{ doc.warehouseName }}</text>
            </view>
            <view class="info-row">
              <text class="label">创建时间</text>
              <text>{{ formatDate(doc.createdAt) }}</text>
            </view>
            <view class="info-row" v-if="doc.remark">
              <text class="label">备注</text>
              <text>{{ doc.remark }}</text>
            </view>
          </view>
        </view>
        <view class="card">
          <text class="section-title">明细 ({{ lines.length }})</text>
          <view class="lines-list">
            <view v-for="line in lines" :key="line.id" class="line-item">
              <view class="line-header">
                <text class="line-code">{{ line.itemCode }}</text>
                <text class="line-name">{{ line.itemName }}</text>
              </view>
              <view class="line-meta">
                <text class="meta" v-if="line.locationCode">库位: {{ line.locationCode }}</text>
                <text class="meta" v-if="line.batchNo">批次: {{ line.batchNo }}</text>
              </view>
              <view class="line-qty-row">
                <text class="qty-label">账面: {{ formatNum(line.bookQuantity) }}</text>
                <text class="qty-label" v-if="line.actualQuantity !== null && line.actualQuantity !== undefined">实盘: {{ formatNum(line.actualQuantity) }}</text>
                <text class="qty-diff" v-if="line.differenceQuantity !== null && line.differenceQuantity !== undefined" :class="line.differenceQuantity > 0 ? 'value-green' : line.differenceQuantity < 0 ? 'value-red' : ''">
                  差异: {{ line.differenceQuantity > 0 ? '+' : '' }}{{ formatNum(line.differenceQuantity) }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-else-if="isTransfer">
        <!-- 调拨单详情 -->
        <view class="card">
          <view class="header-row">
            <text class="doc-no">{{ doc.transferNo }}</text>
            <text class="doc-status" :class="['badge', statusClass(doc.status)]">{{ statusText(doc.status) }}</text>
          </view>
          <view class="header-info">
            <view class="info-row">
              <text class="label">调出仓库</text>
              <text>{{ doc.sourceWarehouseName }}</text>
            </view>
            <view class="info-row">
              <text class="label">调入仓库</text>
              <text>{{ doc.targetWarehouseName }}</text>
            </view>
            <view class="info-row">
              <text class="label">创建时间</text>
              <text>{{ formatDate(doc.createdAt) }}</text>
            </view>
            <view class="info-row" v-if="doc.reviewer">
              <text class="label">审核人</text>
              <text>{{ doc.reviewer }}</text>
            </view>
            <view class="info-row" v-if="doc.remark">
              <text class="label">备注</text>
              <text>{{ doc.remark }}</text>
            </view>
          </view>
        </view>
        <view class="card">
          <text class="section-title">明细 ({{ lines.length }})</text>
          <view class="lines-list">
            <view v-for="line in lines" :key="line.id" class="line-item">
              <view class="line-header">
                <text class="line-code">{{ line.itemCode }}</text>
                <text class="line-name">{{ line.itemName }}</text>
              </view>
              <view class="line-meta">
                <text class="meta">调出: {{ line.sourceLocationCode }}</text>
                <text class="meta">调入: {{ line.targetLocationCode }}</text>
                <text class="meta" v-if="line.batchNo">批次: {{ line.batchNo }}</text>
              </view>
              <view class="line-qty-row">
                <text class="qty-label">数量: {{ formatNum(line.quantity) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-else>
        <!-- 单据头部 -->
        <view class="card">
          <view class="header-row">
            <text class="doc-no">{{ doc.documentNo }}</text>
            <text class="doc-status" :class="['badge', statusClass(doc.status)]">{{ statusText(doc.status) }}</text>
          </view>
          <view class="header-info">
            <view class="info-row">
              <text class="label">单据类型</text>
              <text>{{ doc.typeText }}</text>
            </view>
            <view class="info-row">
              <text class="label">业务日期</text>
              <text>{{ formatDate(doc.businessDate) }}</text>
            </view>
            <view class="info-row" v-if="doc.partnerName">
              <text class="label">往来单位</text>
              <text>{{ doc.partnerName }}</text>
            </view>
            <view class="info-row">
              <text class="label">仓库</text>
              <text>{{ doc.warehouseName }}</text>
            </view>
            <view class="info-row" v-if="doc.reviewer">
              <text class="label">审核人</text>
              <text>{{ doc.reviewer }}</text>
            </view>
            <view class="info-row" v-if="doc.remark">
              <text class="label">备注</text>
              <text>{{ doc.remark }}</text>
            </view>
          </view>
        </view>

        <!-- 汇总信息 -->
        <view class="card">
          <text class="section-title">金额汇总</text>
          <view class="summary-grid">
            <view class="summary-item">
              <text class="summary-label">总数量</text>
              <text class="summary-value">{{ formatNum(doc.totalQuantity) }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">{{ doc.type === 'IN' || doc.type === 'RETURN_IN' ? '入库金额' : '出库金额' }}</text>
              <text class="summary-value value-green">¥{{ formatMoney(doc.totalAmount) }}</text>
            </view>
          </view>
        </view>

        <!-- 明细列表 -->
        <view class="card">
          <text class="section-title">明细 ({{ lines.length }})</text>
          <view class="lines-list">
            <view v-for="line in lines" :key="line.id" class="line-item">
              <view class="line-header">
                <text class="line-code">{{ line.itemCode }}</text>
                <text class="line-name">{{ line.itemName }}</text>
              </view>
              <view class="line-meta">
                <text class="meta" v-if="line.warehouseName">📍 {{ line.warehouseName }}</text>
                <text class="meta" v-if="line.locationCode">📦 {{ line.locationCode }}</text>
                <text class="meta" v-if="line.batchNo">批次: {{ line.batchNo }}</text>
              </view>
              <view class="line-qty-row">
                <text class="qty-label">数量: {{ formatNum(line.quantity) }}</text>
                <text class="qty-label">单价: ¥{{ formatMoney(line.unitPrice) }}</text>
                <text class="qty-label value-green">金额: ¥{{ formatMoney(line.lineAmount) }}</text>
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

const TYPE_MAP = { IN: '采购入库', OUT: '销售出库', RETURN_IN: '退货入库', RETURN_OUT: '退回供应商' }

export default {
  props: {
    id: { type: [String, Number], required: true },
    type: { type: String, default: 'IN' },
  },
  data() {
    return {
      doc: null,
      lines: [],
      loading: true,
      refreshing: false,
      contentHeight: 0,
      isStocktake: false,
      isTransfer: false,
    }
  },
  onLoad() {
    this.setContentHeight()
    this.loadDetail()
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.loadDetail()
  },
  methods: {
    setContentHeight() {
      const sysInfo = uni.getSystemInfoSync()
      const navBarHeight = sysInfo.statusBarHeight + 44
      this.contentHeight = sysInfo.windowHeight - navBarHeight
    },
    async loadDetail() {
      this.loading = true
      try {
        this.isStocktake = this.type === 'stocktakes'
        this.isTransfer = this.type === 'transfers'
        let data
        if (this.isStocktake) {
          data = await api.get(`/stocktakes/${this.id}`)
        } else if (this.isTransfer) {
          data = uni.getStorageSync('wms_transfer_detail')
          if (!data || String(data.id) !== String(this.id)) {
            throw new Error('未找到调拨单详情')
          }
        } else {
          data = await api.get(`/documents/${this.id}`)
        }
        this.doc = data
        if (this.isStocktake || this.isTransfer) {
          this.lines = data.lines || []
        } else {
          this.doc.typeText = TYPE_MAP[data.type] || data.type
          const lines = data.lines || []
          this.lines = lines.map(l => ({
            ...l,
            lineAmount: (parseFloat(l.quantity) || 0) * (parseFloat(l.unitPrice) || 0),
          }))
          this.doc.totalQuantity = lines.reduce((s, l) => s + (parseFloat(l.quantity) || 0), 0)
          this.doc.totalAmount = lines.reduce((s, l) => s + (parseFloat(l.quantity) || 0) * (parseFloat(l.unitPrice) || 0), 0)
        }
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1500)
      } finally {
        this.loading = false
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
    },
    onRefresh() {
      this.refreshing = true
      this.loadDetail()
    },
    statusText(status) {
      const map = { DRAFT: '草稿', APPROVED: '已审核', COMPLETED: '已执行', CANCELLED: '已取消', CONFIRMED: '已确认' }
      return map[status] || status
    },
    statusClass(status) {
      const map = { DRAFT: 'badge-default', APPROVED: 'badge-info', COMPLETED: 'badge-success', CANCELLED: 'badge-error', CONFIRMED: 'badge-success' }
      return map[status] || 'badge-default'
    },
    formatMoney,
    formatNum,
    formatDate,
  },
}
</script>

<style scoped>
.doc-detail-page { background: #f5f5f5; min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 20px; }

.card {
  background: #fff;
  border-radius: 10px;
  margin: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.doc-no { font-size: 16px; font-weight: 600; color: #333; font-family: monospace; }
.header-info { display: flex; flex-direction: column; gap: 8px; }
.info-row { display: flex; justify-content: space-between; font-size: 13px; }
.info-row .label { color: #999; }
.info-row text:last-child { color: #333; text-align: right; max-width: 65%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.section-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 12px; }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 12px;
  background: #fafafa;
  border-radius: 8px;
}
.summary-label { font-size: 12px; color: #999; }
.summary-value { font-size: 18px; font-weight: 600; color: #333; margin-top: 4px; }

.lines-list { display: flex; flex-direction: column; gap: 10px; }
.line-item {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
}
.line-header { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.line-code { font-size: 12px; color: #1677ff; background: #e6f7ff; padding: 1px 6px; border-radius: 3px; }
.line-name { font-size: 14px; font-weight: 500; color: #333; }
.line-meta { display: flex; gap: 10px; font-size: 11px; color: #999; margin-bottom: 8px; flex-wrap: wrap; }
.meta { background: #f0f0f0; padding: 1px 6px; border-radius: 3px; }
.line-qty-row { display: flex; gap: 16px; font-size: 12px; flex-wrap: wrap; }
.qty-label { color: #666; }
.qty-diff { font-weight: 600; }

.loading { text-align: center; padding: 40px; color: #999; }
</style>