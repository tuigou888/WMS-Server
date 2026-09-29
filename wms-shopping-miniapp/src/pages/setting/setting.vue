<template>
  <view class="page">
    <view class="card">
      <view class="menu-item"><text>服务器地址</text><text class="muted">{{ baseUrl }}</text></view>
      <view class="menu-item" @tap="editBase"><text>修改服务器地址</text><view class="arrow"></view></view>
      <view class="menu-item" @tap="clearRedis"><text>清除本地缓存</text><view class="arrow"></view></view>
    </view>
  </view>
</template>

<script>
import { getBaseUrl } from '@/api/request.js'
import { useUserStore } from '@/store/user.js'
export default {
  data() { return { baseUrl: '', userStore: null } },
  onLoad() { this.userStore = useUserStore() },
  onShow() { this.baseUrl = getBaseUrl() },
  methods: {
    editBase() {
      const stored = uni.getStorageSync('wms_api_base') || ''
      uni.showModal({
        title: '服务器地址',
        editable: true,
        placeholderText: getBaseUrl(),
        content: stored,
        success: (r) => {
          if (!r.confirm) return
          const v = (r.content || '').trim()
          if (v) uni.setStorageSync('wms_api_base', v)
          else uni.removeStorageSync('wms_api_base')
          this.baseUrl = getBaseUrl()
          uni.showToast({ title: '已保存，重进小程序后生效', icon: 'none' })
        },
      })
    },
    clearRedis() {
      // 保留服务器地址覆盖；同步清内存登录态/购物车，避免"界面仍显示已登录"
      const apiBase = uni.getStorageSync('wms_api_base')
      uni.clearStorageSync()
      if (apiBase) uni.setStorageSync('wms_api_base', apiBase)
      this.userStore && this.userStore.logout()
      uni.reLaunch({ url: '/pages/login/login' })
    },
  },
}
</script>
<style scoped>
.menu-item { display: flex; justify-content: space-between; padding: 28rpx 0; border-bottom: 1rpx solid #f0f0f0; font-size: 28rpx; }
.muted { color: #999; font-size: 24rpx; }
.arrow { width: 18rpx; height: 18rpx; border-top: 4rpx solid #ccc; border-right: 4rpx solid #ccc; transform: rotate(45deg); margin-left: 8rpx; }
</style>
