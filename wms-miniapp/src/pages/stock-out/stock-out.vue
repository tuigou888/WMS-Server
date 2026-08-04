<template>
  <view class="stock-page">
    <view v-if="!userStore.isAdmin" class="role-hint">仅管理员可扫码直接出库，操作员请通过单据流程</view>
    <view v-if="!item" class="scan-prompt" @tap="scanCode">
      <text class="scan-icon">📷</text>
      <text class="scan-text">点击扫描物品二维码</text>
      <text class="scan-hint">或从扫码历史选择</text>
    </view>

    <view v-else class="stock-form">
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
            <text class="stat-value">{{ formatNum(item.quantity || 0) }} {{ item.unit }}</text>
          </view>
          <view class="stat">
            <text class="stat-label">平均成本 (系统自动)</text>
            <text class="stat-value value-green">¥{{ formatMoney(item.avgCost || 0) }}</text>
          </view>
        </view>
      </view>

      <!-- 输入表单 -->
      <view class="form-section">
        <view class="form-row">
          <view class="input-group">
            <label class="label">出库数量 <text class="required">*</text></label>
            <input class="input" type="digit" v-model="form.quantity" placeholder="请输入数量" @input="calcProfit" />
          </view>
          <view class="input-group">
            <label class="label">售出单价 <text class="required">*</text></label>
            <input class="input" type="digit" v-model="form.salePrice" placeholder="请输入售价" @input="calcProfit" />
          </view>
        </view>

        <!-- 自动计算显示 -->
        <view class="calc-display">
          <view class="calc-row">
            <text class="calc-label">成本单价</text>
            <text class="calc-value value-green">¥{{ formatMoney(calcData.unitCost) }}</text>
          </view>
          <view class="calc-row">
            <text class="calc-label">成本金额</text>
            <text class="calc-value">¥{{ formatMoney(calcData.totalCost) }}</text>
          </view>
          <view class="calc-row">
            <text class="calc-label">销售金额</text>
            <text class="calc-value value-green">¥{{ formatMoney(calcData.totalSale) }}</text>
          </view>
          <view class="calc-row highlight">
            <text class="calc-label">预估利润</text>
            <text class="calc-value value-red">{{ formatMoney(calcData.profit) }}</text>
            <text class="calc-rate" v-if="calcData.profitRate > 0">利润率 {{ calcData.profitRate }}%</text>
          </view>
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
          <label class="label">客户/备注</label>
          <input class="input" v-model="form.remark" placeholder="可选" />
        </view>
      </view>

      <!-- 确认按钮 -->
      <button class="btn-submit" @tap="submit" :disabled="submitting || !formValid">
        <text v-if="submitting" class="loading"></text>
        <text v-else>确认出库</text>
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
        salePrice: '',
        batchNo: '',
        remark: '',
      },
      submitting: false,
      scanHistory: [],
      locations: [],
      selectedLocation: '',
      calcData: {
        unitCost: 0,
        totalCost: 0,
        totalSale: 0,
        profit: 0,
        profitRate: 0,
      },
    }
  },
  computed: {
    userStore() { return useUserStore() },
    currentWarehouse() {
      return this.userStore.warehouses.find(w => w.id === this.userStore.warehouseId)
    },
    formValid() {
      return this.form.quantity && this.form.salePrice &&
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
        this.form = { quantity: '', salePrice: '', batchNo: '', remark: '' }
        this.selectedLocation = ''
        this.locations = []
        this.calcData = { unitCost: 0, totalCost: 0, totalSale: 0, profit: 0, profitRate: 0 }
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
        item.quantity = totalQty
        item.avgCost = totalQty > 0 ? (totalAmt / totalQty) : 0
      } catch (e) {
        item.quantity = 0
        item.avgCost = 0
      }
    },
    calcProfit() {
      const qty = parseFloat(this.form.quantity) || 0
      const salePrice = parseFloat(this.form.salePrice) || 0
      const unitCost = this.item?.avgCost || 0

      const totalCost = qty * unitCost
      const totalSale = qty * salePrice
      const profit = totalSale - totalCost
      const profitRate = totalSale > 0 ? ((profit / totalSale) * 100).toFixed(1) : 0

      this.calcData = { unitCost, totalCost, totalSale, profit, profitRate }
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
          salePrice: parseFloat(this.form.salePrice),
          warehouseId: this.userStore.warehouseId,
          locationCode: this.selectedLocation,
          batchNo: this.form.batchNo || null,
          remark: this.form.remark,
        }
        const result = await api.stockOut(data)
        uni.showToast({ title: '出库成功', icon: 'success' })
        uni.showModal({
          title: '出库成功',
          content: `单据号: ${result.orderNo}\n出库数量: ${result.quantity}\n成本单价: ¥${formatMoney(result.costUnit)}\n成本金额: ¥${formatMoney(result.totalAmount)}\n售出单价: ¥${formatMoney(result.salePrice)}\n销售金额: ¥${formatMoney(result.saleAmount)}\n利润: ¥${formatMoney(result.profit)}\n新库存: ${formatNum(result.newStockQuantity)}`,
          showCancel: false,
          confirmText: '继续出库',
          success: () => {
            this.item = null
            this.form = { quantity: '', salePrice: '', batchNo: '', remark: '' }
            this.selectedLocation = ''
            this.calcData = { unitCost: 0, totalCost: 0, totalSale: 0, profit: 0, profitRate: 0 }
          },
        })
      } catch (e) {
        uni.showToast({ title: e.message || '出库失败', icon: 'none' })
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
.stock-page { background: #f5f5f5; min-height: 100vh; padding: 16px; box-sizing: border-box; }

.role-hint {
  background: #fffbe6;
  border: 1px solid #ffe58f;
  color: #d48806;
  font-size: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  text-align: center;
}

.scan-prompt {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  background: #fff;
  border-radius: 12px;
  margin: 16px;
  border: 2px dashed #d9d9d9;
}
.scan-icon { font-size: 64px; margin-bottom: 16px; }
.scan-text { font-size: 18px; color: #333; font-weight: 500; }
.scan-hint { font-size: 13px; color: #999; margin-top: 8px; }

.stock-form { display: flex; flex-direction: column; gap: 12px; }

.item-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.item-header { display: flex; align-items: baseline; gap: 10px; margin-bottom: 4px; }
.item-code { font-size: 13px; color: #1677ff; background: #e6f7ff; padding: 2px 8px; border-radius: 4px; }
.item-name { font-size: 16px; font-weight: 600; color: #333; }
.item-specs { font-size: 12px; color: #999; margin-bottom: 12px; }
.item-stats { display: flex; gap: 16px; }
.stat { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 8px; background: #fafafa; border-radius: 6px; }
.stat-label { font-size: 11px; color: #999; }
.stat-value { font-size: 14px; font-weight: 600; margin-top: 2px; }

.form-section {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.form-row { display: flex; gap: 12px; }
.form-row .input-group { flex: 1; }
.input-group { margin-bottom: 12px; }
.input-group:last-child { margin-bottom: 0; }
.required { color: #ff4d4f; margin-left: 2px; }

.input {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 16px;
  box-sizing: border-box;
  background: #fff;
}

.select-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  font-size: 16px;
  color: #333;
}
.select-wrapper .select-value { color: #999; }
.select-wrapper .select-value:not(:empty) { color: #333; }
.arrow { font-size: 12px; color: #999; }

.calc-display {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
  margin: 12px 0;
}
.calc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
}
.calc-row.highlight { border-top: 1px dashed #d9d9d9; margin-top: 4px; padding-top: 10px; }
.calc-label { color: #666; }
.calc-value { font-weight: 600; }
.calc-rate { font-size: 12px; color: #faad14; margin-left: 8px; background: #fffbe6; padding: 2px 6px; border-radius: 4px; }

.btn-submit {
  width: 100%;
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 16px;
  font-size: 17px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
}
.btn-submit:disabled { opacity: 0.5; }

.history-section { background: #fff; border-radius: 10px; padding: 16px; margin-top: 12px; }
.history-list { margin-top: 8px; }
.history-item { padding: 12px; border-bottom: 1px solid #f0f0f0; font-size: 14px; color: #333; }
.history-item:last-child { border-bottom: none; }
</style>