<template>
  <view class="item-list-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索物品编码/名称/规格" @confirm="search" />
      <button class="search-btn" @tap="search">搜索</button>
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
              <text class="item-code">{{ item.code }}</text>
              <text class="item-name">{{ item.name }}</text>
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
      listHeight: 0,
      showQrModal: false,
      qrImage: '',
      qrItemCode: '',
      qrItemName: '',
    }
  },
  onLoad() {
    this.setListHeight()
    this.search()
  },
  onShow() {
    this.search()
  },
  onPullDownRefresh() {
    this.refreshing = true
    this.search(true)
  },
  methods: {
    setListHeight() {
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      const searchHeight = 60
      this.listHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight - searchHeight
    },
    async search(reset = false) {
      if (reset) {
        this.page = 1
        this.list = []
        this.hasMore = true
      }
      this.loading = true
      try {
        const params = { page: this.page, pageSize: this.pageSize }
        if (this.keyword) params.keyword = this.keyword
        const res = await api.items(params)
        const data = res.records || res
        if (reset) this.list = []
        // 为每个物品获取库存摘要
        for (const item of data) {
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
        }
        this.list.push(...data)
        this.hasMore = data.length >= this.pageSize
        this.page++
      } catch (e) {
        uni.showToast({ title: e.message || '加载失败', icon: 'none' })
      } finally {
        this.loading = false
        this.refreshing = false
        uni.stopPullDownRefresh()
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
      try {
        uni.showLoading({ title: '保存中...', mask: true })
        const res = await api.qrcodePng(this.qrItemCode)
        const blob = new Blob([res], { type: 'image/png' })
        const url = URL.createObjectURL(blob)
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
      } catch (e) {
        uni.showToast({ title: e.message || '保存失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    formatMoney,
    formatNum,
  },
}
</script>

<style scoped>
.item-list-page { background: #f5f5f5; min-height: 100vh; }

.search-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.search-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 15px;
  background: #fafafa;
}
.search-btn {
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 0 20px;
  font-size: 15px;
}

.list-container { width: 100%; box-sizing: border-box; }
.list { padding: 8px 16px 20px; display: flex; flex-direction: column; gap: 8px; }
.list-item {
  display: flex;
  justify-content: space-between;
  background: #fff;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  text-decoration: none;
}
.list-item-hover { background: #fafafa; }
.item-main { flex: 1; min-width: 0; }
.item-header { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.item-code { font-size: 12px; color: #1677ff; background: #e6f7ff; padding: 1px 6px; border-radius: 3px; white-space: nowrap; }
.item-name { font-size: 15px; font-weight: 500; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { display: flex; gap: 10px; font-size: 11px; color: #999; flex-wrap: wrap; margin-bottom: 4px; }
.meta { background: #f0f0f0; padding: 1px 6px; border-radius: 3px; }
.item-stock { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.stock-label { color: #999; }
.stock-value { font-weight: 600; color: #333; }
.stock-cost { color: #1677ff; }

.item-qr { display: flex; align-items: center; justify-content: center; width: 44px; font-size: 20px; }

.loading, .loading-more, .empty-state {
  text-align: center;
  padding: 30px;
  color: #999;
  font-size: 14px;
}
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.empty-state text:first-child { font-size: 48px; opacity: 0.5; }

.qr-modal {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.qr-content {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  width: 80%;
  max-width: 300px;
  text-align: center;
}
.qr-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.qr-header text:first-child { font-size: 16px; font-weight: 600; }
.qr-close { font-size: 20px; color: #999; }
.qr-image { width: 200px; height: 200px; }
.qr-code { display: block; margin-top: 12px; font-size: 13px; color: #666; font-family: monospace; }
.btn-save-qr { margin-top: 16px; background: #52c41a; color: #fff; border: none; border-radius: 6px; padding: 10px; }
</style>