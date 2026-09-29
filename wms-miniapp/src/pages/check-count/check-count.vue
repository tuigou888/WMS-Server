<template>
  <view class="check-count-page">
    <view v-if="loading" class="loading">加载盘点单详情...</view>

    <view v-else>
      <!-- 盘点单头部信息 -->
      <view class="header-card">
        <view class="header-row">
          <text class="order-no">{{ order.stocktakeNo }}</text>
          <text :class="['badge', statusClass(order.status)]">{{ statusText(order.status) }}</text>
        </view>
        <view class="header-info">
          <text>仓库: {{ order.warehouseName }}</text>
          <text>创建: {{ formatDate(order.createdAt) }}</text>
        </view>
      </view>

      <!-- 扫码录入 -->
      <view class="scan-section">
        <button class="btn-primary btn-scan" @tap="scanCode" :disabled="scanning || order.status !== 'DRAFT'">
          <text v-if="scanning" class="loading-sm"></text>
          <text v-else>📷  扫码录入实盘</text>
        </button>
        <text class="scan-hint" v-if="order.status !== 'DRAFT'">仅草稿状态可录入实盘数量</text>
      </view>

      <!-- 明细列表 -->
      <view class="list-section">
        <text class="section-title">盘点明细 ({{ lines.length }})</text>
        <view class="list">
          <view v-for="line in lines" :key="line.id" class="line-item">
            <view class="line-main">
              <view class="line-header">
                <text class="line-code">{{ line.itemCode }}</text>
                <text class="line-name">{{ line.itemName }}</text>
              </view>
              <view class="line-meta">
                <text class="meta">库位: {{ line.locationCode || '-' }}</text>
                <text class="meta" v-if="line.batchNo">批次: {{ line.batchNo }}</text>
              </view>
              <view class="line-qty">
                <view class="qty-row">
                  <text class="qty-label">账面</text>
                  <text class="qty-value">{{ formatNum(line.bookQuantity) }}</text>
                </view>
                <view class="qty-row">
                  <text class="qty-label">实盘</text>
                  <input class="qty-input" type="digit" v-model="line.actualQuantity" placeholder="请输入" :disabled="order.status !== 'DRAFT'" @blur="saveLine(line)" />
                </view>
                <view class="qty-row diff" v-if="line.differenceQuantity !== undefined && line.differenceQuantity !== null">
                  <text class="qty-label">差异</text>
                  <text class="qty-value" :class="diffClass(line.differenceQuantity)">
                    {{ line.differenceQuantity > 0 ? '+' : '' }}{{ formatNum(line.differenceQuantity) }}
                  </text>
                </view>
              </view>
            </view>
            <view class="line-actions">
              <button class="btn-secondary btn-save-line" @tap="saveLine(line)" :disabled="savingLineId === line.id || order.status !== 'DRAFT'">
                <text v-if="savingLineId === line.id" class="loading-sm"></text>
                <text v-else>保存</text>
              </button>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部操作 -->
      <view v-if="order.status === 'DRAFT'" class="bottom-actions">
        <button class="btn-primary btn-submit-count" @tap="submitAll" :disabled="submittingAll">
          <text v-if="submittingAll" class="loading-sm"></text>
          <text v-else>全部提交实盘数量</text>
        </button>
      </view>
    </view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum, date as formatDate } from '@/utils/format.js'

export default {
  props: {
    id: { type: [String, Number], required: true },
  },
  data() {
    return {
      order: null,
      lines: [],
      loading: true,
      scanning: false,
      savingLineId: null,
      submittingAll: false,
    }
  },
  onLoad() {
    this.loadDetail()
  },
  methods: {
    async loadDetail() {
      this.loading = true
      try {
        const data = await api.get(`/stocktakes/${this.id}`)
        this.order = data
        this.lines = data.lines || []
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1500)
      } finally {
        this.loading = false
      }
    },
    async scanCode() {
      if (this.order.status !== 'DRAFT') return
      this.scanning = true
      try {
        const res = await uni.scanCode({ scanType: ['qrCode', 'barCode'] })
        if (res.result) {
          await this.processScan(res.result)
        }
      } catch (e) {
        const scanErr = (e && e.errMsg) || ''
        if (scanErr.indexOf('cancel') < 0) uni.showToast({ title: scanErr || '扫码失败', icon: 'none' })
      } finally {
        this.scanning = false
      }
    },
    async processScan(code) {
      try {
        // 查找明细行：同一物品可能有多库位/多批次多行，仅按 itemCode 取第一行会漏录
        const item = await api.itemByCode(code)
        const candidates = this.lines.filter(l => l.itemCode === item.code)
        if (candidates.length === 0) {
          uni.showToast({ title: '该物品不在盘点范围内', icon: 'none' })
          return
        }
        let line = candidates[0]
        if (candidates.length > 1) {
          if (candidates.length > 6) {
            uni.showToast({ title: '该物品有多行盘点行，请在列表中直接录入', icon: 'none' })
            return
          }
          const tapIndex = await new Promise((resolve) => {
            uni.showActionSheet({
              itemList: candidates.map(l => `${l.locationCode || '默认库位'}${l.batchNo ? ' / 批次 ' + l.batchNo : ''}`),
              success: (r) => resolve(r.tapIndex),
              fail: () => resolve(-1),
            })
          })
          if (tapIndex < 0) return
          line = candidates[tapIndex]
        }
        // 弹窗输入实盘数量
        const result = await uni.showModal({
          title: '录入实盘',
          content: `${item.name} (${line.locationCode || '默认库位'})\n账面数量: ${formatNum(line.bookQuantity)}`,
          editable: true,
          placeholderText: '请输入实盘数量',
        })
        if (result.confirm && result.content) {
          const raw = String(result.content).trim()
          if (!/^\d+(\.\d+)?$/.test(raw)) {
            uni.showToast({ title: '请输入非负数字', icon: 'none' })
            return
          }
          line.actualQuantity = raw
          await this.saveLine(line)
        }
      } catch (e) {
        uni.showToast({ title: e.message || '处理失败', icon: 'none' })
      }
    },
    async saveLine(line) {
      if (line.actualQuantity === '' || line.actualQuantity === undefined || line.actualQuantity === null) return
      if (this.savingLineId === line.id) return
      if (!/^\d+(\.\d+)?$/.test(String(line.actualQuantity).trim())) {
        uni.showToast({ title: '请输入非负数字', icon: 'none' })
        return
      }
      this.savingLineId = line.id
      try {
        const payload = {
          warehouseId: this.order.warehouseId,
          lines: [{
            itemCode: line.itemCode,
            locationCode: line.locationCode,
            batchNo: line.batchNo || null,
            actualQuantity: parseFloat(line.actualQuantity),
          }],
        }
        await api.countStocktake(this.id, payload)
        // 本地行已带最新实盘值，同步差异数供模板展示（不再整页 reload）
        line.differenceQuantity = parseFloat(line.actualQuantity) - parseFloat(line.bookQuantity || 0)
        uni.showToast({ title: '保存成功', icon: 'success' })
        // 本地行已带最新实盘值，不再整页 reload（会强制收起键盘、打断连续录入）
      } catch (e) {
        uni.showToast({ title: e.message || '保存失败', icon: 'none' })
      } finally {
        this.savingLineId = null
      }
    },
    async submitAll() {
      const filledLines = this.lines.filter(l => l.actualQuantity !== '' && l.actualQuantity !== undefined && l.actualQuantity !== null)
      if (filledLines.length === 0) {
        uni.showToast({ title: '请先录入至少一行实盘数量', icon: 'none' })
        return
      }
      const invalid = filledLines.find(l => !/^\d+(\.\d+)?$/.test(String(l.actualQuantity).trim()))
      if (invalid) {
        uni.showToast({ title: `${invalid.itemName} 的实盘数量不是有效数字`, icon: 'none' })
        return
      }
      if (this.submittingAll) return
      this.submittingAll = true
      try {
        const payload = {
          warehouseId: this.order.warehouseId,
          lines: filledLines.map(l => ({
            itemCode: l.itemCode,
            locationCode: l.locationCode,
            batchNo: l.batchNo || null,
            actualQuantity: parseFloat(l.actualQuantity),
          })),
        }
        await api.countStocktake(this.id, payload)
        uni.showToast({ title: '提交成功', icon: 'success' })
        this.loadDetail()
      } catch (e) {
        uni.showToast({ title: e.message || '提交失败', icon: 'none' })
      } finally {
        this.submittingAll = false
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
    diffClass(diff) {
      if (diff > 0) return 'value-green'
      if (diff < 0) return 'value-red'
      return ''
    },
    formatMoney,
    formatNum,
    formatDate,
  },
}
</script>

<style scoped>
.check-count-page { padding-bottom: 180rpx; }

.loading { text-align: center; padding: 80rpx; color: var(--wms-ink-3); font-size: 28rpx; }

.header-card {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  margin: 24rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 16rpx; margin-bottom: 16rpx; }
.order-no { font-size: 32rpx; font-weight: 600; color: var(--wms-ink); font-family: monospace; letter-spacing: 1rpx; }
.header-info { display: flex; gap: 32rpx; font-size: 26rpx; color: var(--wms-ink-2); }

.scan-section { padding: 0 24rpx; }
.btn-scan { width: 100%; padding: 28rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; }
.scan-hint { display: block; text-align: center; margin-top: 16rpx; font-size: 24rpx; color: var(--wms-ink-3); }

.list-section { padding: 0 24rpx; }
.list { display: flex; flex-direction: column; gap: 20rpx; }
.line-item {
  display: flex;
  align-items: center;
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.line-main { flex: 1; min-width: 0; }
.line-header { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; flex-wrap: wrap; }
.line-code {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}
.line-name { font-size: 28rpx; font-weight: 600; color: var(--wms-ink); }
.line-meta { display: flex; gap: 12rpx; font-size: 22rpx; color: var(--wms-ink-3); margin-bottom: 16rpx; flex-wrap: wrap; }
.meta { background: var(--wms-bg); padding: 4rpx 14rpx; border-radius: 6rpx; }

.line-qty { display: flex; flex-direction: column; gap: 12rpx; }
.qty-row { display: flex; align-items: center; gap: 16rpx; font-size: 26rpx; }
.qty-label { color: var(--wms-ink-3); width: 90rpx; flex-shrink: 0; }
.qty-value { font-weight: 600; font-size: 30rpx; color: var(--wms-ink); }
.qty-input {
  flex: 1;
  padding: 14rpx 20rpx;
  border: 2rpx solid var(--wms-border);
  border-radius: 8rpx;
  background: var(--wms-card);
  font-size: 28rpx;
  font-weight: 600;
  text-align: right;
  color: var(--wms-ink);
  box-sizing: border-box;
}
.qty-row.diff { padding-top: 12rpx; border-top: 2rpx dashed var(--wms-border); margin-top: 4rpx; }

.line-actions { display: flex; align-items: center; margin-left: 20rpx; flex-shrink: 0; }
.btn-save-line {
  padding: 12rpx 28rpx;
  font-size: 24rpx;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}
.btn-save-line:disabled { opacity: 0.5; }
.btn-save-line .loading-sm { border-color: var(--wms-primary-bg); border-top-color: var(--wms-primary); }

.loading-sm { width: 24rpx; height: 24rpx; border: 4rpx solid rgba(255, 255, 255, 0.35); border-top-color: #ffffff; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.bottom-actions {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: var(--wms-card);
  border-top: 2rpx solid var(--wms-border);
  box-shadow: 0 -4rpx 16rpx rgba(31, 35, 41, 0.06);
  z-index: 100;
}
.btn-submit-count { width: 100%; padding: 26rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; }
</style>
