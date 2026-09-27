<template>
  <view class="stock-page">
    <view v-if="!userStore.isAdmin" class="role-hint">仅管理员可扫码直接入库，操作员请通过单据流程</view>
    <view v-if="userStore.isAdmin && !item" class="scan-prompt" @tap="scanCode">
      <text class="scan-icon">📷</text>
      <text class="scan-text">点击扫描物品二维码</text>
      <text class="scan-hint">或从扫码历史选择</text>
    </view>

    <view v-else-if="userStore.isAdmin" class="stock-form">
      <!-- 物品信息卡片 -->
      <view class="item-card">
        <view class="item-header">
          <text class="item-code">{{ item.code }}</text>
          <text class="item-name">{{ item.name }}</text>
        </view>
        <view class="item-specs" v-if="item.specs">{{ item.specs }}</view>
        <view class="item-stats">
          <view class="stat">
            <text class="stat-label">当前库存</text>
            <view class="stat-line">
              <text class="stat-value">{{ formatNum(item.quantity || 0) }}</text>
              <text class="stat-unit">{{ item.unit }}</text>
            </view>
          </view>
          <view class="stat">
            <text class="stat-label">平均成本</text>
            <view class="stat-line">
              <text class="stat-value value-green">¥{{ formatMoney(item.avgCost || 0) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 输入表单 -->
      <view class="form-section">
        <view class="form-row">
          <view class="input-group">
            <label class="label">入库数量 <text class="required">*</text></label>
            <input class="input" type="digit" v-model="form.quantity" placeholder="请输入数量" @input="calcAmount" />
          </view>
          <view class="input-group">
            <label class="label">入库单价 <text class="required">*</text></label>
            <input class="input" type="digit" v-model="form.unitCost" placeholder="请输入单价" @input="calcAmount" />
          </view>
        </view>

        <view class="input-group">
          <label class="label">入库金额 (自动计算)</label>
          <view class="input readonly">{{ formatMoney(form.totalAmount) }}</view>
        </view>

        <view class="form-row">
          <view class="input-group">
            <label class="label">仓库 <text class="required">*</text></label>
            <view class="select-wrapper" @tap="showWarehousePicker">
              <text class="select-value">{{ currentWarehouse?.name || '请选择仓库' }}</text>
              <text class="arrow">▼</text>
            </view>
          </view>
          <view class="input-group">
            <label class="label">库位 <text class="required">*</text></label>
            <view class="select-wrapper" @tap="showLocationPicker">
              <text class="select-value">{{ selectedLocation || '请选择库位' }}</text>
              <text class="arrow">▼</text>
            </view>
          </view>
        </view>

        <view class="input-group">
          <label class="label">批次号</label>
          <input class="input" v-model="form.batchNo" placeholder="可选" />
        </view>

        <view class="input-group">
          <label class="label">备注</label>
          <input class="input" v-model="form.remark" placeholder="可选" />
        </view>
      </view>

      <!-- 确认按钮 -->
      <button class="btn-primary btn-submit" @tap="submit" :disabled="submitting || !formValid">
        <text v-if="submitting" class="loading"></text>
        <text v-else>确认入库</text>
      </button>

      <!-- 扫码历史 -->
      <view class="history-section" v-if="scanHistory.length > 0">
        <text class="section-title">扫码历史</text>
        <view class="history-list">
          <view v-for="h in scanHistory" :key="h" class="history-item" @tap="loadItem(h)">
            <text>{{ h }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user.js'
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum } from '@/utils/format.js'

export default {
  data() {
    return {
      item: null,
      form: {
        quantity: '',
        unitCost: '',
        totalAmount: 0,
        batchNo: '',
        remark: '',
      },
      submitting: false,
      scanHistory: [],
      locations: [],
      selectedLocation: '',
    }
  },
  computed: {
    userStore() { return useUserStore() },
    currentWarehouse() {
      return this.userStore.warehouses.find(w => w.id === this.userStore.warehouseId)
    },
    formValid() {
      return this.form.quantity && this.form.unitCost &&
             this.userStore.warehouseId && this.selectedLocation
    },
  },
  onLoad() {
    this.loadHistory()
    if (!this.userStore.warehouseId) {
      this.loadWarehouses()
    }
  },
  onShow() {
    if (!this.item && this.userStore.warehouseId) {
      this.loadWarehouses()
    }
  },
  methods: {
    loadHistory() {
      this.scanHistory = (uni.getStorageSync('scan_history') || []).slice(0, 10)
    },
    saveHistory(code) {
      let history = uni.getStorageSync('scan_history') || []
      history = [code, ...history.filter(c => c !== code)].slice(0, 20)
      uni.setStorageSync('scan_history', history)
      this.scanHistory = history.slice(0, 10)
    },
    async loadWarehouses() {
      try {
        const list = await api.warehouses(true)
        this.userStore.setWarehouses(list)
      } catch (e) {
        console.warn('加载仓库失败:', e)
      }
    },
    async scanCode() {
      try {
        const res = await uni.scanCode({ scanType: ['qrCode', 'barCode'] })
        if (res.result) await this.loadItem(res.result)
      } catch (e) {
        uni.showToast({ title: e.errMsg || '扫码失败', icon: 'none' })
      }
    },
    async loadItem(code) {
      try {
        uni.showLoading({ title: '加载中...', mask: true })
        const item = await api.itemByCode(code)
        this.item = item
        this.form = { quantity: '', unitCost: '', totalAmount: 0, batchNo: '', remark: '' }
        this.selectedLocation = ''
        this.locations = []
        this.saveHistory(code)
        await this.loadStockInfo(item)
        await this.loadLocations()
      } catch (e) {
        uni.showToast({ title: e.message || '物品不存在', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    async loadStockInfo(item) {
      try {
        const dist = await api.inventoryByItem(item.id)
        const totalQty = dist.reduce((sum, d) => sum + (parseFloat(d.quantity) || 0), 0)
        const totalAmt = dist.reduce((sum, d) => sum + (parseFloat(d.totalAmount) || 0), 0)
        this.item.quantity = totalQty
        this.item.avgCost = totalQty > 0 ? (totalAmt / totalQty) : 0
      } catch (e) {
        this.item.quantity = 0
        this.item.avgCost = 0
      }
    },
    calcAmount() {
      const qty = parseFloat(this.form.quantity) || 0
      const cost = parseFloat(this.form.unitCost) || 0
      this.form.totalAmount = qty * cost
    },
    async loadLocations() {
      if (!this.userStore.warehouseId) return
      try {
        const list = await api.get(`/locations?warehouseId=${this.userStore.warehouseId}`)
        this.locations = list
      } catch (e) {
        console.warn('加载库位失败:', e)
      }
    },
    showWarehousePicker() {
      const items = this.userStore.warehouses.map(w => w.name)
      if (items.length === 0) return
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          const selected = this.userStore.warehouses[res.tapIndex]
          this.userStore.setWarehouse(selected.id)
          this.selectedLocation = ''
          this.loadLocations()
        },
      })
    },
    showLocationPicker() {
      if (!this.userStore.warehouseId) {
        uni.showToast({ title: '请先选择仓库', icon: 'none' })
        return
      }
      if (this.locations.length === 0) {
        uni.showToast({ title: '该仓库暂无库位', icon: 'none' })
        return
      }
      const items = this.locations.map(l => l.code)
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          this.selectedLocation = this.locations[res.tapIndex].code
        },
      })
    },
    async submit() {
      if (!this.formValid) {
        uni.showToast({ title: '请填写完整信息', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        const data = {
          itemCode: this.item.code,
          quantity: parseFloat(this.form.quantity),
          unitCost: parseFloat(this.form.unitCost),
          warehouseId: this.userStore.warehouseId,
          locationCode: this.selectedLocation,
          batchNo: this.form.batchNo || null,
          remark: this.form.remark,
        }
        const result = await api.stockIn(data)
        uni.showToast({ title: '入库成功', icon: 'success' })
        uni.showModal({
          title: '入库成功',
          content: `单据号: ${result.orderNo}\n入库数量: ${result.quantity}\n入库金额: ¥${formatMoney(result.totalAmount)}\n新库存: ${formatNum(result.newStockQuantity)}\n新平均成本: ¥${formatMoney(result.newAvgCost)}`,
          showCancel: false,
          confirmText: '继续入库',
          success: () => {
            this.item = null
            this.form = { quantity: '', unitCost: '', totalAmount: 0, batchNo: '', remark: '' }
            this.selectedLocation = ''
          },
        })
      } catch (e) {
        uni.showToast({ title: e.message || '入库失败', icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
    formatMoney,
    formatNum,
  },
}
</script>

<style scoped>
.stock-page { min-height: 100vh; padding: 24rpx; box-sizing: border-box; }

.role-hint {
  background: var(--wms-warning-bg);
  border: 2rpx solid var(--wms-warning);
  color: var(--wms-accent);
  font-size: 24rpx;
  font-weight: 500;
  padding: 18rpx 24rpx;
  border-radius: 12rpx;
  margin-bottom: 20rpx;
  text-align: center;
}

.scan-prompt {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  background: var(--wms-card);
  border-radius: 20rpx;
  border: 4rpx dashed var(--wms-primary);
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.scan-icon { font-size: 112rpx; margin-bottom: 24rpx; }
.scan-text { font-size: 34rpx; color: var(--wms-ink); font-weight: 600; }
.scan-hint { font-size: 26rpx; color: var(--wms-ink-3); margin-top: 16rpx; }

.stock-form { display: flex; flex-direction: column; gap: 20rpx; }

.item-card {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.item-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 12rpx; flex-wrap: wrap; }
.item-code {
  font-size: 24rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
}
.item-name { font-size: 32rpx; font-weight: 600; color: var(--wms-ink); }
.item-specs { font-size: 24rpx; color: var(--wms-ink-3); margin-bottom: 24rpx; }
.item-stats { display: flex; gap: 20rpx; }
.stat { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 20rpx 16rpx; background: var(--wms-bg); border-radius: 12rpx; }
.stat-label { font-size: 22rpx; color: var(--wms-ink-3); }
.stat-line { display: flex; align-items: baseline; gap: 6rpx; margin-top: 8rpx; }
.stat-value { font-size: 34rpx; font-weight: 600; color: var(--wms-ink); }
.stat-unit { font-size: 22rpx; color: var(--wms-ink-3); }

.form-section {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.form-row { display: flex; gap: 20rpx; }
.form-row .input-group { flex: 1; }
.input-group { margin-bottom: 24rpx; }
.input-group:last-child { margin-bottom: 0; }
.required { color: var(--wms-danger); margin-left: 4rpx; }

.input.readonly { background: var(--wms-bg); color: var(--wms-primary); font-weight: 600; }

.select-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  border: 2rpx solid var(--wms-border);
  border-radius: 12rpx;
  background: var(--wms-card);
  font-size: 30rpx;
  color: var(--wms-ink);
}
.select-value { color: var(--wms-ink); }
.arrow { font-size: 22rpx; color: var(--wms-ink-3); }

.btn-submit { width: 100%; padding: 28rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; margin-top: 8rpx; }

.loading { width: 32rpx; height: 32rpx; border: 4rpx solid rgba(255, 255, 255, 0.35); border-top-color: #ffffff; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.history-section {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
  padding: 0 32rpx 8rpx;
}
.history-list { margin-top: 0; }
.history-item {
  padding: 26rpx 0;
  border-bottom: 2rpx solid var(--wms-border);
  font-size: 28rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--wms-ink);
  word-break: break-all;
}
.history-item:last-child { border-bottom: none; }
</style>
