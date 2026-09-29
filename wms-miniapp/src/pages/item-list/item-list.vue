<template>
  <view class="item-list-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索物品编码/名称/规格" @confirm="search" />
      <button class="search-btn btn-primary" @tap="search">搜索</button>
    </view>

    <!-- 列表 -->
    <scroll-view class="list-container" scroll-y @scrolltolower="loadMore" :style="{ height: listHeight + 'px' }" @refresh="onRefresh" :refresher-enabled="true" :refresher-triggered="refreshing">
      <view v-if="loading && list.length === 0" class="loading">加载中...</view>

      <view v-else-if="list.length === 0" class="empty-state">
        <text>🏷️</text>
        <text>暂无物品数据</text>
      </view>

      <view v-else class="list">
        <navigator v-for="item in list" :key="item.id" :url="`/pages/item-detail/item-detail?id=${item.id}`" class="list-item" hover-class="list-item-hover">
          <view class="item-main">
            <view class="item-header">
              <text class="item-name">{{ item.name }}</text>
              <text class="item-code">{{ item.code }}</text>
            </view>
            <view class="item-meta">
              <text class="meta" v-if="item.categoryName">{{ item.categoryName }}</text>
              <text class="meta">{{ item.unit }}</text>
              <text class="meta" v-if="item.specs">{{ item.specs }}</text>
              <text class="meta" v-if="item.brand">{{ item.brand }}</text>
            </view>
            <view class="item-stock" v-if="item.stockInfo">
              <text class="stock-label">库存:</text>
              <text class="stock-value">{{ formatNum(item.stockInfo.quantity) }} {{ item.unit }}</text>
              <text class="stock-cost">成本: ¥{{ formatMoney(item.stockInfo.avgCost) }}</text>
            </view>
          </view>
          <view class="item-qr" @click.stop="showQrcode(item.code)">
            <text>📱</text>
          </view>
        </navigator>
      </view>

      <view v-if="loadingMore" class="loading-more">加载更多...</view>
      <view v-else-if="hasMore === false && list.length > 0" class="loading-more">已加载全部</view>
    </scroll-view>

    <!-- 二维码弹窗 -->
    <view v-if="showQrModal" class="qr-modal" @tap="showQrModal = false">
      <view class="qr-content" @tap.stop>
        <view class="qr-header">
          <text>{{ qrItemName }}</text>
          <text class="qr-close" @tap="showQrModal = false">✕</text>
        </view>
        <image class="qr-image" :src="qrImage" mode="aspectFit" />
        <text class="qr-code">{{ qrItemCode }}</text>
        <button class="btn-save-qr" @tap="saveQrcode">保存到相册</button>
      </view>
    </view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'
import { money as formatMoney, num as formatNum } from '@/utils/format.js'

export default {
  data() {
    return {
      keyword: '',
      list: [],
      page: 1,
      pageSize: 20,
      loading: false,
      loadingMore: false,
      refreshing: false,
      hasMore: true,
      reqSeq: 0,
      listHeight: 0,
      showQrModal: false,
      qrImage: '',
      qrItemCode: '',
      qrItemName: '',
    }
  },
  onLoad() {
    this.setListHeight()
  },
  onShow() {
    // 统一在 onShow 重置加载（原 onLoad 拉第 1 页后 onShow 再拉会自动 append 第 2 页）
    this.search(true)
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.search(true)
  },
  methods: {
    setListHeight() {
      // windowHeight 已扣除原生导航栏（本页无 tabBar），只需再减页内搜索栏高度
      const sysInfo = uni.getSystemInfoSync()
      const searchHeight = 60
      this.listHeight = sysInfo.windowHeight - searchHeight
    },
    async search(reset = false) {
      // reset 允许打断在途翻页，过期响应直接丢弃
      if (reset) {
        this.reqSeq++
        this.page = 1
        this.list = []
        this.hasMore = true
      } else if (this.loading) {
        return
      }
      const seq = this.reqSeq
      this.loading = true
      try {
        const params = { page: this.page, pageSize: this.pageSize }
        if (this.keyword) params.keyword = this.keyword
        const res = await api.items(params)
        if (seq !== this.reqSeq) return
        const data = res.records || res
        // 为每个物品并行获取库存摘要（原串行 await 一次列表 21 个请求）
        await Promise.all(data.map(async (item) => {
          try {
            const inv = await api.inventoryByItem(item.id)
            if (inv.length > 0) {
              const totalQty = inv.reduce((sum, i) => sum + (parseFloat(i.quantity) || 0), 0)
              const totalAmt = inv.reduce((sum, i) => sum + (parseFloat(i.totalAmount) || 0), 0)
              const avgCost = totalQty > 0 ? (totalAmt / totalQty).toFixed(4) : 0
              item.stockInfo = { quantity: totalQty, avgCost }
            }
          } catch (e) {
            item.stockInfo = null
          }
        }))
        if (seq !== this.reqSeq) return
        this.list.push(...data)
        this.hasMore = data.length >= this.pageSize
        this.page++
      } catch (e) {
        if (seq !== this.reqSeq) return
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        if (seq === this.reqSeq) {
          this.loading = false
          this.refreshing = false
          uni.stopPullDownRefresh()
        }
      }
    },
    loadMore() {
      if (!this.loadingMore && this.hasMore && !this.loading) {
        this.loadingMore = true
        this.search().finally(() => { this.loadingMore = false })
      }
    },
    async showQrcode(code) {
      try {
        uni.showLoading({ title: '生成中...', mask: true })
        const res = await api.qrcode(code)
        this.qrImage = res.image
        this.qrItemCode = res.itemCode
        this.qrItemName = res.itemName
        this.showQrModal = true
      } catch (e) {
        uni.showToast({ title: e.message || '生成失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    async saveQrcode() {
      uni.showLoading({ title: '保存中...', mask: true })
      const finish = () => uni.hideLoading()
      try {
        const res = await api.qrcodePng(this.qrItemCode)
        // #ifdef H5
        const blob = new Blob([res], { type: 'image/png' })
        const a = document.createElement('a')
        a.href = URL.createObjectURL(blob)
        a.download = `qrcode_${this.qrItemCode}.png`
        a.click()
        URL.revokeObjectURL(a.href)
        uni.showToast({ title: '已下载', icon: 'success' })
        // #endif
        // #ifndef H5
        const fs = uni.getFileSystemManager()
        const path = `${uni.env.USER_DATA_PATH}/qrcode_${this.qrItemCode}.png`
        fs.writeFile({
          filePath: path,
          data: res,
          success: () => {
            uni.saveImageToPhotosAlbum({
              filePath: path,
              success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
              fail: () => uni.showToast({ title: '保存失败，请授权相册权限', icon: 'none' }),
            })
          },
          fail: () => uni.showToast({ title: '保存失败', icon: 'none' }),
        })
        // #endif
      } catch (e) {
        uni.showToast({ title: e.message || '保存失败', icon: 'none' })
      } finally {
        finish()
      }
    },
    formatMoney,
    formatNum,
  },
}
</script>

<style scoped>
.item-list-page { min-height: 100vh; }

/* 搜索栏 */
.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: var(--wms-card);
  border-bottom: 2rpx solid var(--wms-border);
}
.search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: var(--wms-ink);
  background: var(--wms-bg);
}
.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  height: 72rpx;
  padding: 0 36rpx;
  font-size: 28rpx;
  border-radius: 12rpx;
  box-shadow: 0 2rpx 8rpx rgba(22, 119, 255, 0.25);
}

/* 列表 */
.list-container { width: 100%; box-sizing: border-box; }
.list { padding: 20rpx 24rpx 40rpx; display: flex; flex-direction: column; gap: 20rpx; }
.list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
  text-decoration: none;
}
.list-item-hover { background: var(--wms-bg); }
.item-main { flex: 1; min-width: 0; }
.item-header { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.item-name { flex: 1; min-width: 0; font-size: 30rpx; font-weight: 600; color: var(--wms-ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-code {
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  white-space: nowrap;
}
.item-meta { display: flex; gap: 20rpx; font-size: 22rpx; color: var(--wms-ink-3); flex-wrap: wrap; margin-bottom: 12rpx; }
.meta { line-height: 1.5; }
.item-stock {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  padding-top: 12rpx;
  border-top: 2rpx solid var(--wms-border);
}
.stock-label { font-size: 22rpx; color: var(--wms-ink-3); }
.stock-value { font-size: 30rpx; font-weight: 600; color: var(--wms-ink); }
.stock-cost { font-size: 24rpx; font-weight: 500; color: var(--wms-primary); }

.item-qr {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  margin-left: 20rpx;
  font-size: 36rpx;
  background: var(--wms-primary-bg);
  border-radius: 16rpx;
}

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 60rpx;
  color: var(--wms-ink-3);
  font-size: 26rpx;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-state text:first-child { font-size: 96rpx; opacity: 0.5; }

/* 二维码弹窗 */
.qr-modal {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(31, 35, 41, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.qr-content {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 24rpx;
  padding: 40rpx;
  width: 80%;
  max-width: 600rpx;
  text-align: center;
}
.qr-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.qr-header text:first-child { font-size: 32rpx; font-weight: 600; color: var(--wms-ink); }
.qr-close { font-size: 40rpx; color: var(--wms-ink-3); }
.qr-image { width: 400rpx; height: 400rpx; }
.qr-code { display: block; margin-top: 24rpx; font-size: 26rpx; font-weight: 600; letter-spacing: 1rpx; color: var(--wms-ink-2); font-family: monospace; }
.btn-save-qr { margin-top: 32rpx; background: var(--wms-success); color: #fff; border: none; border-radius: 12rpx; padding: 20rpx; font-weight: 500; }
</style>