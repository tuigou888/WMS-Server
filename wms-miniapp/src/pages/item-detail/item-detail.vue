<template>
  <view class="item-detail-page">
    <scroll-view class="content" scroll-y :style="{ height: contentHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading" class="loading">加载中...</view>

      <view v-else>
        <!-- 基本信息 -->
        <view class="card">
          <text class="section-title">基本信息</text>
          <view class="info-grid">
            <view class="info-row">
              <text class="info-label">物品编码</text>
              <text class="info-value">{{ item.code }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">物品名称</text>
              <text class="info-value">{{ item.name }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">规格型号</text>
              <text class="info-value">{{ item.specs || '-' }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">品牌</text>
              <text class="info-value">{{ item.brand || '-' }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">分类</text>
              <text class="info-value">{{ item.categoryName || '-' }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">单位</text>
              <text class="info-value">{{ item.unit }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">成本计价</text>
              <text class="info-value">{{ item.costMethod === 'average' ? '移动加权平均' : '先进先出' }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">安全库存</text>
              <text class="info-value">{{ formatNum(item.safetyStock) }} {{ item.unit }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">最大/最小库存</text>
              <text class="info-value">{{ formatNum(item.maxStock) }} / {{ formatNum(item.minStock) }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">状态</text>
              <text class="info-value" :class="item.status ? 'value-green' : 'value-red'">{{ item.status ? '启用' : '禁用' }}</text>
            </view>
            <view class="info-row">
              <text class="info-label">备注</text>
              <text class="info-value">{{ item.remark || '-' }}</text>
            </view>
          </view>
        </view>

        <!-- 库存汇总 -->
        <view class="card">
          <text class="section-title">库存汇总</text>
          <view class="summary-grid">
            <view class="summary-item">
              <text class="summary-label">总库存</text>
              <text class="summary-value">{{ formatNum(totalQty) }} {{ item.unit }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">总金额</text>
              <text class="summary-value value-green">¥{{ formatMoney(totalAmt) }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">平均成本</text>
              <text class="summary-value">¥{{ formatMoney(avgCost) }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">仓库数</text>
              <text class="summary-value">{{ warehousesCount }}</text>
            </view>
          </view>
        </view>

        <!-- 库位分布 -->
        <view class="card" v-if="distribution.length > 0">
          <view class="section-header">
            <text class="section-title">库位分布</text>
            <navigator :url="`/pages/inventory/inventory?keyword=${encodeURIComponent(item.code)}`" class="view-all">查看全部库存</navigator>
          </view>
          <view class="distribution-list">
            <view v-for="d in distribution" :key="d.id" class="dist-item">
              <view class="dist-main">
                <text class="dist-warehouse">{{ d.warehouseName }}</text>
                <text class="dist-location">{{ d.locationCode }}</text>
              </view>
              <view class="dist-stats">
                <text class="dist-qty">{{ formatNum(d.quantity) }} {{ item.unit }}</text>
                <text class="dist-amt">¥{{ formatMoney(d.totalAmount) }}</text>
                <text class="dist-cost">成本: ¥{{ formatMoney(d.avgCost) }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 二维码 -->
        <view class="card">
          <text class="section-title">物品二维码</text>
          <view class="qr-section" @tap="showQrcode">
            <image v-if="qrImage" class="qr-image" :src="qrImage" mode="aspectFit" />
            <view v-else class="qr-placeholder" @tap.stop="loadQrcode">
              <text>点击生成二维码</text>
            </view>
            <text class="qr-hint">长按识别或保存到相册</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum } from '@/utils/format.js'

export default {
  props: {
    id: { type: [String, Number], default: '' },
    code: { type: String, default: '' },
  },
  data() {
    return {
      item: null,
      distribution: [],
      loading: true,
      refreshing: false,
      contentHeight: 0,
      qrImage: '',
    }
  },
  computed: {
    totalQty() {
      return this.distribution.reduce((sum, d) => sum + (parseFloat(d.quantity) || 0), 0)
    },
    totalAmt() {
      return this.distribution.reduce((sum, d) => sum + (parseFloat(d.totalAmount) || 0), 0)
    },
    avgCost() {
      return this.totalQty > 0 ? this.totalAmt / this.totalQty : 0
    },
    warehousesCount() {
      const set = new Set(this.distribution.map(d => d.warehouseId))
      return set.size
    },
  },
  onLoad() {
    this.setContentHeight()
    this.loadDetail()
  },
  onShow() {
    this.loadDetail()
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.loadDetail()
  },
  methods: {
    setContentHeight() {
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      this.contentHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight
    },
    async loadDetail() {
      this.loading = true
      try {
        const identifier = this.id || this.code
        if (!identifier) throw new Error('缺少物品标识')

        const [item, dist] = await Promise.all([
          this.id ? api.item(this.id) : api.itemByCode(this.code),
          api.inventoryByItem(this.id || (await api.itemByCode(this.code)).id),
        ])
        this.item = item
        this.distribution = dist
        await this.loadQrcode()
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        this.loading = false
        this.refreshing = false
        uni.stopPullDownRefresh()
      }
    },
    async loadQrcode() {
      if (!this.item) return
      try {
        const res = await api.qrcode(this.item.code)
        this.qrImage = res.image
      } catch (e) {
        console.warn('加载二维码失败:', e)
      }
    },
    showQrcode() {
      if (this.qrImage) {
        uni.previewImage({ urls: [this.qrImage] })
      }
    },
    onRefresh() {
      this.refreshing = true
      this.loadDetail()
    },
    formatMoney,
    formatNum,
  },
}
</script>

<style scoped>
.item-detail-page { background: #f5f5f5; min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 20px; }

.card {
  background: #fff;
  border-radius: 10px;
  margin: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.section-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.view-all { font-size: 13px; color: #1677ff; }

.info-grid { display: flex; flex-direction: column; gap: 10px; }
.info-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.info-row:last-child { border-bottom: none; }
.info-label { color: #999; font-size: 14px; }
.info-value { color: #333; font-size: 14px; text-align: right; max-width: 60%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

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

.distribution-list { display: flex; flex-direction: column; gap: 8px; }
.dist-item {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.dist-main { display: flex; flex-direction: column; gap: 2px; }
.dist-warehouse { font-size: 14px; font-weight: 500; color: #333; }
.dist-location { font-size: 12px; color: #999; }
.dist-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px; }
.dist-qty { font-weight: 600; color: #333; }
.dist-amt { color: #52c41a; }
.dist-cost { color: #999; }

.qr-section { text-align: center; padding: 10px 0; }
.qr-image { width: 180px; height: 180px; border: 1px solid #f0f0f0; border-radius: 8px; }
.qr-placeholder {
  width: 180px; height: 180px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
}
.qr-hint { display: block; margin-top: 8px; font-size: 12px; color: #999; }

.loading { text-align: center; padding: 40px; color: #999; }
</style>