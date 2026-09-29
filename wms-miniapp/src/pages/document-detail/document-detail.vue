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
              <text class="summary-value" :class="doc.type === 'IN' || doc.type === 'RETURN_IN' ? 'value-green' : 'value-red'">¥{{ formatMoney(doc.totalAmount) }}</text>
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
                <text class="qty-label" :class="doc.type === 'IN' || doc.type === 'RETURN_IN' ? 'value-green' : 'value-red'">金额: ¥{{ formatMoney(line.lineAmount) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 操作栏：按单据类型 + 状态机 + 当前用户权限渲染 -->
      <view class="card" v-if="actions.length">
        <text class="section-title">操作</text>
        <view class="action-buttons">
          <button v-for="a in actions" :key="a.key" :class="a.cls" @tap="runAction(a)">{{ a.text }}</button>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { useUserStore } from '@/store/user.js'
import { money as formatMoney, num as formatNum, date as formatDate } from '@/utils/format.js'

const TYPE_MAP = { IN: '采购入库', OUT: '销售出库', RETURN_IN: '退货入库', RETURN_OUT: '退回供应商' }

export default {
  props: {
    id: { type: [String, Number], required: true },
    type: { type: String, default: 'IN' },
  },
  computed: {
    userStore() { return useUserStore() },
    // 移动端单据操作入口（此前 request.js 中相关 API 零调用）：WAREHOUSE 持有 *:execute 可执行已审核单据，审核类仅 ADMIN/AUDITOR
    actions() {
      if (!this.doc || this.loading) return []
      const s = this.doc.status
      const has = (perm) => this.userStore.hasPerm(perm)
      const out = []
      if (this.isStocktake) {
        if (s === 'DRAFT') {
          out.push({ key: 'count', text: '去录入实盘', cls: 'btn-secondary' })
          if (has('stocktake:review')) {
            out.push({ key: 'approve', text: '审核通过', cls: 'btn-primary' })
            out.push({ key: 'reject', text: '审核驳回', cls: 'btn-danger' })
          }
        } else if (s === 'APPROVED' && has('stocktake:execute')) {
          out.push({ key: 'complete', text: '执行盘点', cls: 'btn-primary' })
        }
      } else if (this.isTransfer) {
        if (s === 'DRAFT' && has('transfer:review')) {
          out.push({ key: 'approve', text: '审核通过', cls: 'btn-primary' })
          out.push({ key: 'reject', text: '审核驳回', cls: 'btn-danger' })
        } else if (s === 'APPROVED' && has('transfer:execute')) {
          out.push({ key: 'complete', text: '执行调拨', cls: 'btn-primary' })
        }
      } else {
        if (s === 'DRAFT' && has('document:review')) {
          out.push({ key: 'approve', text: '审核通过', cls: 'btn-primary' })
          out.push({ key: 'reject', text: '审核驳回', cls: 'btn-danger' })
          out.push({ key: 'cancel', text: '取消单据', cls: 'btn-danger' })
        } else if (s === 'APPROVED' && has('document:execute')) {
          out.push({ key: 'complete', text: '执行单据', cls: 'btn-primary' })
        } else if (s === 'COMPLETED' && has('document:review') && !this.doc.reversalOfDocumentId) {
          // 红冲单（reversalOfDocumentId 非空）不可再反审/红冲
          out.push({ key: 'uncomplete', text: '反审', cls: 'btn-secondary' })
          out.push({ key: 'reverse', text: '红冲', cls: 'btn-danger' })
        }
      }
      return out
    },
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
      // windowHeight 已扣除原生导航栏（本页无 tabBar），无需再减
      this.contentHeight = uni.getSystemInfoSync().windowHeight
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
          // L3：优先走详情接口（此前仅靠列表页 storage 缓存，刷新/直达详情会失败）；缓存仅作接口异常时兜底
          data = await api.get(`/transfers/${this.id}`).catch(() => {
            const cached = uni.getStorageSync('wms_transfer_detail')
            if (!cached || String(cached.id) !== String(this.id)) throw new Error('未找到调拨单详情')
            return cached
          })
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
    runAction(a) {
      if (a.key === 'count') {
        uni.navigateTo({ url: `/pages/check-count/check-count?id=${this.id}` })
        return
      }
      const kind = this.isStocktake ? 'stocktakes' : this.isTransfer ? 'transfers' : 'documents'
      const prompts = {
        approve: { title: '审核通过', content: '确认审核通过该单据？', editable: false },
        reject: { title: '审核驳回', content: '驳回后单据终止流转，请填写驳回原因', editable: true },
        cancel: { title: '取消单据', content: '取消后单据作废，确认取消？', editable: false },
        complete: {
          title: a.text,
          content: this.isStocktake
            ? '执行盘点将按差异生成库存调整并变动库存，确认执行？'
            : '执行单据将实际增减库存，确认执行？',
          editable: false,
        },
        uncomplete: { title: '反审', content: '反审将生成反向流水冲销原库存影响，单据退回已审核，确认反审？', editable: false },
        reverse: { title: '红冲', content: '将生成一张反向红冲单据（直接置为已审核），原单保留，确认红冲？', editable: false },
      }
      const p = prompts[a.key]
      uni.showModal({
        title: p.title,
        content: p.content,
        editable: p.editable,
        placeholderText: p.editable ? '驳回原因（必填）' : '',
        success: async (r) => {
          if (!r || !r.confirm) return
          const remark = (r.content || '').trim()
          if (a.key === 'reject' && !remark) {
            uni.showToast({ title: '请填写驳回原因', icon: 'none' })
            return
          }
          try {
            if (a.key === 'approve' || a.key === 'reject') {
              await api.post(`/${kind}/${this.id}/review`, { action: a.key === 'approve' ? 'APPROVE' : 'REJECT', remark: remark || null })
            } else {
              await api.post(`/${kind}/${this.id}/${a.key}`)
            }
            uni.showToast({ title: '操作成功', icon: 'success' })
            this.loadDetail()
          } catch (e) {
            uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
          }
        },
      })
    },
    statusText(status) {
      const map = { DRAFT: '草稿', APPROVED: '已审核', COMPLETED: '已执行', CANCELLED: '已取消', REJECTED: '已驳回', CONFIRMED: '已确认' }
      return map[status] || status
    },
    statusClass(status) {
      const map = { DRAFT: 'badge-default', APPROVED: 'badge-info', COMPLETED: 'badge-success', CANCELLED: 'badge-error', REJECTED: 'badge-error', CONFIRMED: 'badge-success' }
      return map[status] || 'badge-default'
    },
    formatMoney,
    formatNum,
    formatDate,
  },
}
</script>

<style scoped>
.doc-detail-page { min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 40rpx; }

/* 卡片与区块标题直接复用全局 .card / .section-title，此处不再重复定义 */
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
/* 单据号 chip：蓝浅底 + 主蓝 + 等宽字体 */
.doc-no {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-family: monospace;
  letter-spacing: 1rpx;
}
.header-info { display: flex; flex-direction: column; gap: 16rpx; padding-top: 20rpx; border-top: 2rpx solid var(--wms-border); }
.info-row { display: flex; justify-content: space-between; font-size: 26rpx; }
.info-row .label { color: var(--wms-ink-3); margin-bottom: 0; }
.info-row text:last-child { color: var(--wms-ink); text-align: right; max-width: 65%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 金额汇总：数字大号加粗 */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}
.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 28rpx 24rpx;
  background: var(--wms-bg);
  border-radius: 16rpx;
}
.summary-label { font-size: 24rpx; color: var(--wms-ink-3); }
.summary-value { font-size: 36rpx; font-weight: 700; color: var(--wms-ink); margin-top: 8rpx; font-family: monospace; }
.summary-value.value-green { color: var(--wms-success); }
.summary-value.value-red { color: var(--wms-danger); }

.lines-list { display: flex; flex-direction: column; gap: 16rpx; }
.line-item { background: var(--wms-bg); border-radius: 16rpx; padding: 24rpx; }
.line-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 12rpx; flex-wrap: wrap; }
/* 物品编码 chip：蓝浅底 + 主蓝 + 等宽字体 */
.line-code {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-family: monospace;
  letter-spacing: 1rpx;
}
.line-name { font-size: 28rpx; font-weight: 500; color: var(--wms-ink); flex: 1; min-width: 0; }
.line-meta { display: flex; gap: 12rpx; font-size: 22rpx; color: var(--wms-ink-2); margin-bottom: 12rpx; flex-wrap: wrap; }
.meta { background: var(--wms-card); border: 2rpx solid var(--wms-border); padding: 2rpx 12rpx; border-radius: 6rpx; }
.line-qty-row { display: flex; gap: 32rpx; font-size: 24rpx; flex-wrap: wrap; }
.qty-label { color: var(--wms-ink-3); }
.qty-label.value-green { color: var(--wms-success); }
.qty-label.value-red { color: var(--wms-danger); }
/* 行尾关键数字突出 */
.line-qty-row text:last-child { font-weight: 600; font-size: 26rpx; }
.qty-diff { font-weight: 600; }

.loading { text-align: center; padding: 80rpx; color: var(--wms-ink-3); }

/* 操作栏：按钮组并排均分 */
.action-buttons { display: flex; gap: 16rpx; flex-wrap: wrap; }
.action-buttons button { flex: 1; min-width: 200rpx; margin: 0; font-size: 28rpx; padding-left: 0; padding-right: 0; }
</style>