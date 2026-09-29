<template>
  <view class="doc-create-page">
    <!-- 单据类型 -->
    <view class="card">
      <text class="section-title">单据类型</text>
      <view class="type-grid">
        <view v-for="t in types" :key="t.value" :class="['type-chip', form.type === t.value && 'active']" @tap="onTypeChange(t.value)">
          {{ t.label }}
        </view>
      </view>
      <text class="type-hint">{{ typeHint }}</text>
    </view>

    <!-- 往来单位 -->
    <view class="card">
      <text class="section-title">往来单位（选填）</text>
      <picker mode="selector" :range="partnerNames" :value="partnerIndex" @change="onPartnerChange">
        <view class="picker-val">{{ partnerIndex > 0 ? partnerNames[partnerIndex] : '请选择' }} <text class="arrow">▾</text></view>
      </picker>
    </view>

    <!-- 备注 -->
    <view class="card">
      <text class="section-title">备注</text>
      <input class="input" v-model="form.remark" placeholder="选填" />
    </view>

    <!-- 明细 -->
    <view class="card">
      <view class="lines-header">
        <text class="section-title">明细（{{ form.lines.length }}）</text>
        <button class="btn-secondary btn-add-line" size="mini" @tap="openItemPicker">+ 添加物品</button>
      </view>
      <view v-if="form.lines.length === 0" class="empty-lines">
        <text>暂无明细，点击"添加物品"开始</text>
      </view>
      <view v-for="(line, idx) in form.lines" :key="idx" class="line-card">
        <view class="line-head">
          <text class="line-name">{{ line.itemName }}</text>
          <text class="line-unit">{{ line.unit }}</text>
          <text class="line-remove" @tap="removeLine(idx)">删除</text>
        </view>
        <view class="line-form">
          <view class="field">
            <text class="field-label">库位 *</text>
            <view class="field-inline">
              <input class="input input-sm" v-model="line.locationCode" placeholder="如 A-01-01" />
              <button class="btn-secondary btn-pick" size="mini" @tap="pickLocation(idx)">选择</button>
            </view>
          </view>
          <view class="field">
            <text class="field-label">数量 *</text>
            <input class="input input-sm" type="digit" v-model="line.quantity" placeholder="> 0" />
          </view>
          <view class="field">
            <text class="field-label">单价 *</text>
            <input class="input input-sm" type="digit" v-model="line.unitPrice" placeholder="≥ 0" />
          </view>
          <view class="field">
            <text class="field-label">批次</text>
            <input class="input input-sm" v-model="line.batchNo" placeholder="选填" />
          </view>
        </view>
      </view>
    </view>

    <!-- 提交 -->
    <view class="bottom-bar">
      <button class="btn-primary btn-submit" :disabled="submitting" @tap="submit">
        <text v-if="submitting">提交中...</text>
        <text v-else>创建单据</text>
      </button>
    </view>

    <!-- 物品选择弹层 -->
    <view v-if="itemPickerVisible" class="picker-mask" @tap="closeItemPicker">
      <view class="picker-panel" @tap.stop>
        <view class="picker-search">
          <input class="input" v-model="itemKeyword" placeholder="搜索物品编码/名称" @confirm="searchItems" />
          <button class="btn-secondary btn-search" size="mini" @tap="searchItems">搜索</button>
        </view>
        <scroll-view class="picker-list" scroll-y>
          <view v-for="it in itemOptions" :key="it.id" class="picker-item" @tap="addLine(it)">
            <text class="pi-name">{{ it.name }}</text>
            <text class="pi-code">{{ it.code }}</text>
          </view>
          <view v-if="itemOptions.length === 0" class="picker-empty"><text>无匹配物品</text></view>
        </scroll-view>
        <view class="picker-cancel" @tap="closeItemPicker"><text>取消</text></view>
      </view>
    </view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { useUserStore } from '@/store/user.js'
import { chooseIndex } from '@/utils/choose.js'

const TYPES = [
  { value: 'IN', label: '采购入库', partnerType: 'SUPPLIER', hint: '入库单需供应商或双方单位' },
  { value: 'OUT', label: '销售出库', partnerType: 'CUSTOMER', hint: '出库单需客户或双方单位' },
  { value: 'RETURN_IN', label: '退货入库', partnerType: 'CUSTOMER', hint: '客户退货入库，往来单位为客户' },
  { value: 'RETURN_OUT', label: '退回供应商', partnerType: 'SUPPLIER', hint: '退回供应商出库，往来单位为供应商' },
]

export default {
  data() {
    return {
      types: TYPES,
      form: {
        type: 'IN',
        partnerId: null,
        remark: '',
        lines: [],
      },
      partners: [],
      partnerIndex: 0,
      partnerNames: ['不指定'],
      submitting: false,
      // 物品选择弹层
      itemPickerVisible: false,
      itemKeyword: '',
      itemOptions: [],
      // 库位快速选择
      locations: [],
    }
  },
  computed: {
    userStore() { return useUserStore() },
    typeHint() {
      return (TYPES.find(t => t.value === this.form.type) || {}).hint || ''
    },
    currentType() {
      return TYPES.find(t => t.value === this.form.type) || TYPES[0]
    },
  },
  onLoad() {
    this.loadPartners()
  },
  methods: {
    onTypeChange(value) {
      if (this.form.type === value) return
      this.form.type = value
      // 往来单位类型联动：切换类型后重置已选单位（供应商/客户口径不同）
      this.form.partnerId = null
      this.partnerIndex = 0
      this.loadPartners()
    },
    async loadPartners() {
      try {
        const list = await api.partners(this.currentType.partnerType)
        this.partners = list || []
        this.partnerNames = ['不指定', ...this.partners.map(p => p.name)]
      } catch (e) {
        this.partners = []
        this.partnerNames = ['不指定']
      }
    },
    onPartnerChange(e) {
      this.partnerIndex = Number(e.detail.value)
      this.form.partnerId = this.partnerIndex > 0 ? this.partners[this.partnerIndex - 1].id : null
    },
    // ---- 物品选择 ----
    openItemPicker() {
      this.itemPickerVisible = true
      this.itemKeyword = ''
      this.itemOptions = []
      this.searchItems()
    },
    closeItemPicker() {
      this.itemPickerVisible = false
    },
    async searchItems() {
      try {
        const params = { page: 1, pageSize: 20 }
        if (this.itemKeyword) params.keyword = this.itemKeyword
        const res = await api.items(params)
        this.itemOptions = (res && res.records) || []
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '物品加载失败', icon: 'none' })
      }
    },
    addLine(item) {
      const dup = this.form.lines.find(l => l.itemCode === item.code)
      if (dup) {
        uni.showToast({ title: '该物品已在明细中', icon: 'none' })
        return
      }
      this.form.lines.push({
        itemCode: item.code,
        itemName: item.name,
        unit: item.unit || '',
        locationCode: '',
        quantity: '',
        unitPrice: '',
        batchNo: '',
      })
      this.closeItemPicker()
    },
    removeLine(idx) {
      this.form.lines.splice(idx, 1)
    },
    // ---- 库位快速选择（入库可手输新库位，执行时后端按需自动创建）----
    async pickLocation(idx) {
      if (!this.userStore.warehouseId) {
        uni.showToast({ title: '请先在首页选择仓库', icon: 'none' })
        return
      }
      try {
        if (this.locations.length === 0) {
          this.locations = (await api.locations(this.userStore.warehouseId)) || []
        }
        if (this.locations.length === 0) {
          uni.showToast({ title: '当前仓库暂无库位，可直接输入新库位', icon: 'none' })
          return
        }
        const pickIdx = await chooseIndex(this.locations.map(l => l.code))
        if (pickIdx < 0) return
        this.form.lines[idx].locationCode = this.locations[pickIdx].code
      } catch (e) {
        uni.showToast({ title: '库位加载失败', icon: 'none' })
      }
    },
    // ---- 校验与提交 ----
    validate() {
      if (!this.userStore.warehouseId) return '请先在首页选择仓库'
      if (this.form.lines.length === 0) return '至少需要一条明细'
      for (let i = 0; i < this.form.lines.length; i++) {
        const l = this.form.lines[i]
        const pos = `第 ${i + 1} 行（${l.itemName}）`
        if (!String(l.locationCode || '').trim()) return `${pos}：库位不能为空`
        if (!/^\d+(\.\d+)?$/.test(String(l.quantity).trim()) || !(Number(l.quantity) > 0)) return `${pos}：数量必须为正数`
        if (!/^\d+(\.\d+)?$/.test(String(l.unitPrice).trim()) || !(Number(l.unitPrice) >= 0)) return `${pos}：单价必须为非负数`
      }
      return ''
    },
    async submit() {
      if (this.submitting) return
      const err = this.validate()
      if (err) {
        uni.showToast({ title: err, icon: 'none' })
        return
      }
      this.submitting = true
      try {
        const payload = {
          type: this.form.type,
          partnerId: this.form.partnerId,
          warehouseId: this.userStore.warehouseId,
          businessDate: new Date().toISOString().slice(0, 10),
          remark: this.form.remark || null,
          lines: this.form.lines.map(l => ({
            itemCode: l.itemCode,
            locationCode: String(l.locationCode).trim(),
            quantity: parseFloat(l.quantity),
            unitPrice: parseFloat(l.unitPrice),
            batchNo: (l.batchNo || '').trim() || null,
          })),
        }
        const doc = await api.createDocument(payload)
        uni.showToast({ title: '单据已创建', icon: 'success' })
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/document-detail/document-detail?id=${doc.id}&type=${doc.type}` })
        }, 600)
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '创建失败', icon: 'none' })
      } finally {
        this.submitting = false
      }
    },
  },
}
</script>

<style scoped>
.doc-create-page { min-height: 100vh; padding-bottom: 160rpx; }

.type-grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.type-chip {
  padding: 14rpx 28rpx; border-radius: 12rpx; font-size: 26rpx;
  background: var(--wms-bg); color: var(--wms-ink-2); border: 2rpx solid var(--wms-border);
}
.type-chip.active { background: var(--wms-primary-bg); color: var(--wms-primary); border-color: var(--wms-primary); font-weight: 600; }
.type-hint { display: block; margin-top: 14rpx; font-size: 22rpx; color: var(--wms-ink-3); }

.picker-val { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 0; font-size: 28rpx; color: var(--wms-ink); }
.picker-val .arrow { color: var(--wms-ink-3); }

.lines-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.btn-add-line { margin: 0; }
.empty-lines { padding: 40rpx 0; text-align: center; color: var(--wms-ink-3); font-size: 26rpx; }
.line-card { background: var(--wms-bg); border-radius: 16rpx; padding: 20rpx; margin-bottom: 16rpx; }
.line-head { display: flex; align-items: center; gap: 12rpx; margin-bottom: 14rpx; }
.line-name { flex: 1; font-size: 28rpx; font-weight: 600; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.line-unit { font-size: 22rpx; color: var(--wms-ink-3); }
.line-remove { font-size: 24rpx; color: var(--wms-danger); padding: 4rpx 8rpx; }
.line-form { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.field { display: flex; flex-direction: column; gap: 8rpx; }
.field-label { font-size: 22rpx; color: var(--wms-ink-3); }
.field-inline { display: flex; gap: 10rpx; align-items: center; }
.field-inline .input-sm { flex: 1; min-width: 0; }
.input-sm { height: 64rpx; line-height: 64rpx; padding: 0 16rpx; font-size: 26rpx; }
.btn-pick { margin: 0; flex-shrink: 0; }

.bottom-bar {
  position: fixed; left: 0; right: 0; bottom: 0;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: var(--wms-card); border-top: 2rpx solid var(--wms-border);
}
.btn-submit { width: 100%; }

.picker-mask {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.5); z-index: 99;
  display: flex; align-items: flex-end;
}
.picker-panel {
  width: 100%; max-height: 75vh; background: var(--wms-card);
  border-radius: 24rpx 24rpx 0 0; padding: 24rpx; box-sizing: border-box;
  display: flex; flex-direction: column;
}
.picker-search { display: flex; gap: 16rpx; margin-bottom: 16rpx; }
.picker-search .input { flex: 1; height: 68rpx; line-height: 68rpx; }
.btn-search { margin: 0; }
.picker-list { flex: 1; max-height: 50vh; }
.picker-item {
  display: flex; align-items: center; gap: 16rpx; padding: 22rpx 12rpx;
  border-bottom: 2rpx solid var(--wms-border);
}
.pi-name { flex: 1; font-size: 28rpx; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pi-code { font-size: 22rpx; color: var(--wms-ink-3); font-family: monospace; }
.picker-empty { padding: 60rpx 0; text-align: center; color: var(--wms-ink-3); }
.picker-cancel { padding: 24rpx 0 8rpx; text-align: center; color: var(--wms-ink-2); font-size: 28rpx; }
</style>
