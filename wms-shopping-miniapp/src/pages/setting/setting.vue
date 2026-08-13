<template>
  <view class="page">
    <view class="card">
      <view class="menu-item"><text>服务器地址</text><text class="muted">{{ baseUrl }}</text></view>
      <view class="menu-item" @tap="editBase"><text>修改服务器地址</text><text class="arrow">></text></view>
      <view class="menu-item" @tap="clearRedis"><text>清除本地缓存</text><text class="arrow">></text></view>
    </view>
  </view>
</template>

<script>
import { getBaseUrl } from '@/api/request.js'
export default {
  data() { return { baseUrl: '' } },
  onShow() { this.baseUrl = getBaseUrl() },
  methods: {
    editBase() {
      uni.showModal({ title: '服务器地址', editable: true, placeholderText: getBaseUrl(), content: '', success: (r) => { if (r.content) uni.showToast({ title: '重启后生效', icon: 'none' }) } })
    },
    clearRedis() {
      uni.clearStorageSync()
      uni.showToast({ title: '已清除', icon: 'success' })
    },
  },
}
</script>
<style scoped>
.menu-item { display: flex; justify-content: space-between; padding: 28rpx 0; border-bottom: 1rpx solid #f0f0f0; font-size: 28rpx; }
.muted { color: #999; font-size: 24rpx; }
.arrow { color: #ccc; }
</style>
