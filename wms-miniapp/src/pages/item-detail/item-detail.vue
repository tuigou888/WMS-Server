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
        const identifier = this.id || this.code
        if (!identifier) throw new Error('缺少物品标识')

        let item, dist
        if (this.id) {
          ;[item, dist] = await Promise.all([api.item(this.id), api.inventoryByItem(this.id)])
        } else {
          // 仅带 code 进入：先解析物品拿 id，避免 itemByCode 重复请求两次
          item = await api.itemByCode(this.code)
          dist = await api.inventoryByItem(item.id)
        }
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
.item-detail-page { min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 40rpx; }

.section-title { margin: 0 0 20rpx; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.section-header .section-title { margin-bottom: 0; }
.view-all { font-size: 26rpx; font-weight: 500; color: var(--wms-primary); }

/* 基本信息 · 键值对 */
.info-grid { display: flex; flex-direction: column; }
.info-row { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 2rpx solid var(--wms-border); }
.info-row:last-child { border-bottom: none; padding-bottom: 4rpx; }
.info-label { color: var(--wms-ink-2); font-size: 26rpx; flex-shrink: 0; margin-right: 24rpx; }
.info-value { color: var(--wms-ink); font-size: 28rpx; font-weight: 500; text-align: right; max-width: 60%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info-value.value-green { color: var(--wms-success); font-weight: 600; }
.info-value.value-red { color: var(--wms-danger); font-weight: 600; }

/* 库存汇总 */
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
.summary-value { font-size: 36rpx; font-weight: 600; color: var(--wms-ink); margin-top: 8rpx; }
.summary-value.value-green { color: var(--wms-success); }

/* 库位分布 */
.distribution-list { display: flex; flex-direction: column; gap: 16rpx; }
.dist-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: var(--wms-bg);
  border-radius: 16rpx;
}
.dist-main { display: flex; flex-direction: column; gap: 6rpx; min-width: 0; }
.dist-warehouse { font-size: 28rpx; font-weight: 600; color: var(--wms-ink); }
.dist-location { font-size: 24rpx; color: var(--wms-ink-3); letter-spacing: 1rpx; }
.dist-stats { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; font-size: 24rpx; }
.dist-qty { font-size: 30rpx; font-weight: 600; color: var(--wms-ink); }
.dist-amt { color: var(--wms-success); font-weight: 500; }
.dist-cost { color: var(--wms-ink-3); }

/* 二维码 */
.qr-section { text-align: center; padding: 8rpx 0 0; }
.qr-image { width: 360rpx; height: 360rpx; border: 2rpx solid var(--wms-border); border-radius: 16rpx; }
.qr-placeholder {
  width: 360rpx; height: 360rpx;
  margin: 0 auto;
  border: 4rpx dashed var(--wms-border);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wms-ink-3);
  font-size: 28rpx;
  background: var(--wms-bg);
}
.qr-hint { display: block; margin-top: 16rpx; font-size: 24rpx; color: var(--wms-ink-3); }

.loading { text-align: center; padding: 80rpx; color: var(--wms-ink-3); }
</style>