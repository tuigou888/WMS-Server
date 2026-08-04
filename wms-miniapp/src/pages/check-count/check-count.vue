<template>
  <view class="check-count-page">
    <view v-if="loading" class="loading">加载盘点单详情...</view>

    <view v-else>
      <!-- 盘点单头部信息 -->
      <view class="header-card">
        <view class="header-row">
          <text class="order-no">{{ order.stocktakeNo }}</text>
          <text class="status-badge" :class="['badge', statusClass(order.status)]">{{ statusText(order.status) }}</text>
        </view>
        <view class="header-info">
          <text>仓库: {{ order.warehouseName }}</text>
          <text>创建: {{ formatDate(order.createdAt) }}</text>
        </view>
      </view>

      <!-- 扫码录入 -->
      <view class="scan-section">
        <button class="btn-scan" @tap="scanCode" :disabled="scanning || order.status !== 'DRAFT'">
          <text v-if="scanning" class="loading"></text>
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
                  <input class="qty-input" type="digit" v-model="line.actualQuantity" placeholder="请输入" @blur="saveLine(line)" />
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
              <button class="btn-save-line" @tap="saveLine(line)" :disabled="savingLineId === line.id">
                <text v-if="savingLineId === line.id" class="loading-sm"></text>
                <text v-else>保存</text>
              </button>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部操作 -->
      <view v-if="order.status === 'DRAFT'" class="bottom-actions">
        <button class="btn-submit-count" @tap="submitAll" :disabled="submittingAll">
          <text v-if="submittingAll" class="loading"></text>
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
  onShow() {
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
        uni.showToast({ title: e.errMsg || '扫码失败', icon: 'none' })
      } finally {
        this.scanning = false
      }
    },
    async processScan(code) {
      try {
        // 查找明细行
        const item = await api.itemByCode(code)
        const line = this.lines.find(l => l.itemCode === item.code)
        if (line) {
          // 弹窗输入实盘数量
          const result = await uni.showModal({
            title: '录入实盘',
            content: `${item.name} (${line.locationCode || '默认库位'})\n账面数量: ${formatNum(line.bookQuantity)}`,
            editable: true,
            placeholderText: '请输入实盘数量',
          })
          if (result.confirm && result.content) {
            const qty = parseFloat(result.content)
            if (!isNaN(qty) && qty >= 0) {
              line.actualQuantity = qty.toString()
              await this.saveLine(line)
            } else {
              uni.showToast({ title: '请输入有效数量', icon: 'none' })
            }
          }
        } else {
          uni.showToast({ title: '该物品不在盘点范围内', icon: 'none' })
        }
      } catch (e) {
        uni.showToast({ title: e.message || '处理失败', icon: 'none' })
      }
    },
    async saveLine(line) {
      if (line.actualQuantity === '' || line.actualQuantity === undefined) return
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
        uni.showToast({ title: '保存成功', icon: 'success' })
        this.loadDetail()
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
      const map = { DRAFT: '草稿', IN_PROGRESS: '盘点中', CONFIRMED: '已确认', CANCELLED: '已取消' }
      return map[status] || status
    },
    statusClass(status) {
      const map = { DRAFT: 'badge-default', IN_PROGRESS: 'badge-info', CONFIRMED: 'badge-success', CANCELLED: 'badge-error' }
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
.check-count-page { background: #f5f5f5; min-height: 100vh; padding-bottom: 80px; }

.loading { text-align: center; padding: 40px; color: #999; }

.header-card {
  background: #fff;
  border-radius: 10px;
  margin: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.order-no { font-size: 16px; font-weight: 600; color: #333; font-family: monospace; }
.header-info { display: flex; gap: 16px; font-size: 13px; color: #666; }

.scan-section {
  padding: 0 12px 12px;
}
.btn-scan {
  width: 100%;
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 16px;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.btn-scan:disabled { opacity: 0.5; background: #91d5ff; }
.scan-hint { display: block; text-align: center; margin-top: 8px; font-size: 12px; color: #999; }

.list-section { padding: 0 12px; }
.list { display: flex; flex-direction: column; gap: 8px; }
.line-item {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.line-main { flex: 1; }
.line-header { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.line-code { font-size: 12px; color: #1677ff; background: #e6f7ff; padding: 1px 6px; border-radius: 3px; }
.line-name { font-size: 14px; font-weight: 500; color: #333; }
.line-meta { display: flex; gap: 10px; font-size: 11px; color: #999; margin-bottom: 8px; }
.meta { background: #f0f0f0; padding: 1px 6px; border-radius: 3px; }

.line-qty { display: flex; flex-direction: column; gap: 6px; }
.qty-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.qty-label { color: #999; width: 45px; }
.qty-value { font-weight: 600; color: #333; }
.qty-input { flex: 1; padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 4px; font-size: 13px; text-align: right; }
.qty-unit { color: #999; font-size: 12px; }
.qty-row.diff { padding-top: 4px; border-top: 1px dashed #f0f0f0; margin-top: 4px; }
.qty-amt { font-size: 11px; color: #faad14; }

.line-actions { display: flex; align-items: center; margin-left: 12px; }
.btn-save-line {
  background: #52c41a;
  color: #fff;
  border: none;
  border-radius: 4px;
  padding: 6px 12px;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.btn-save-line:disabled { opacity: 0.5; }

.loading-sm { width: 12px; height: 12px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.bottom-actions {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.05);
  z-index: 100;
}
.btn-submit-count {
  width: 100%;
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 14px;
  font-size: 16px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.btn-submit-count:disabled { opacity: 0.5; }
</style>