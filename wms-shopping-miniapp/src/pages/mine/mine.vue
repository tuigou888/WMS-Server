<template>
  <view class="mine">
    <!-- 未登录 -->
    <view v-if="!isLoggedIn" class="guest">
      <image class="avatar" :src="avatar" mode="aspectFill" />
      <text class="guest-text">登录后可查看订单与购物车</text>
      <button class="btn-primary" @tap="goLogin">立即登录</button>
    </view>

    <!-- 已登录 -->
    <view v-else>
      <view class="profile">
        <image class="avatar" :src="avatar" mode="aspectFill" />
        <view class="profile-info">
          <text class="display-name">{{ displayName }}</text>
          <text class="username">@{{ username }}</text>
        </view>
      </view>

      <!-- 订单快捷 -->
      <view class="card section">
        <view class="section-title">我的订单</view>
        <view class="order-shortcuts">
          <view class="os-item" @tap="goOrders('PENDING')">
            <text class="os-num">{{ pendingCount }}</text>
            <text class="os-label">待付款</text>
          </view>
          <view class="os-item" @tap="goOrders('AUDITED')">
            <view class="os-num-wrap">
              <text class="os-num">{{ auditedCount }}</text>
              <text class="badge-dot" v-if="auditedCount>0"></text>
            </view>
            <text class="os-label">待发货</text>
          </view>
          <view class="os-item" @tap="goOrders('SHIPPED')">
            <text class="os-num">{{ shippedCount }}</text>
            <text class="os-label">已发货</text>
          </view>
          <view class="os-item" @tap="goOrders('COMPLETED')">
            <text class="os-num">{{ completedCount }}</text>
            <text class="os-label">已完成</text>
          </view>
        </view>
      </view>

      <!-- 功能菜单 -->
      <view class="card section">
        <view class="menu-item" @tap="go('/pages/address/address')"><text>收货地址</text><text class="arrow">></text></view>
        <view class="menu-item" @tap="go('/pages/favorites/favorites')"><text>我的收藏</text><text class="arrow">></text></view>
        <view class="menu-item" @tap="go('/pages/setting/setting')"><text>设置</text><text class="arrow">></text></view>
        <view class="menu-item" @tap="go('/pages/about/about')"><text>关于</text><text class="arrow">></text></view>
      </view>

      <button class="logout-btn" @tap="logout">退出登录</button>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user.js'
import { orders as orderApi, auth } from '@/api/market.js'
import { getBaseUrl } from '@/api/request.js'

export default {
  data() { return { pendingCount: 0, auditedCount: 0, shippedCount: 0, completedCount: 0 } },
  computed: {
    userStore() { return useUserStore() },
    isLoggedIn() { return this.userStore.isLoggedIn },
    username() { return this.userStore.user?.username || '' },
    displayName() { return this.userStore.user?.displayName || this.username },
    avatar() { return `${getBaseUrl().replace(/\/api\/v1$/, '')}/avatar.png` },
  },
  onShow() { if (this.isLoggedIn) this.loadCounts() },
  methods: {
    goLogin() { uni.navigateTo({ url: '/pages/login/login' }) },
    go(url) { uni.navigateTo({ url }) },
    goOrders(status) { uni.navigateTo({ url: `/pages/orders/orders?status=${status}` }) },
    async loadCounts() {
      try {
        const res = await orderApi.list({ pageSize: 100 })
        const rows = (res && res.records) || []
        this.pendingCount = rows.filter(o => o.orderStatus === 'PENDING').length
        this.auditedCount = rows.filter(o => o.orderStatus === 'AUDITED').length
        this.shippedCount = rows.filter(o => o.orderStatus === 'SHIPPED').length
        this.completedCount = rows.filter(o => o.orderStatus === 'COMPLETED').length
      } catch (e) {}
    },
    async logout() {
      try { await auth.logout() } catch (e) {}
      this.userStore.logout()
      uni.reLaunch({ url: '/pages/index/index' })
    },
  },
}
</script>

<style scoped>
.mine { padding: 20rpx; min-height: 100vh; }
.guest { text-align: center; padding: 120rpx 40rpx; }
.avatar { width: 140rpx; height: 140rpx; border-radius: 50%; background: #e6f7ff; margin-bottom: 20rpx; }
.profile { display: flex; align-items: center; padding: 40rpx 20rpx; }
.profile .avatar { margin-bottom: 0; margin-right: 24rpx; }
.profile-info { display: flex; flex-direction: column; }
.display-name { font-size: 36rpx; font-weight: 600; color: #333; }
.username { font-size: 24rpx; color: #999; margin-top: 6rpx; }
.card.section { padding: 32rpx 24rpx; }
.section-title { font-size: 28rpx; font-weight: 600; margin-bottom: 24rpx; color: #333; }
.order-shortcuts { display: flex; }
.os-item { flex: 1; text-align: center; display: flex; flex-direction: column; align-items: center; }
.os-num { font-size: 36rpx; font-weight: 700; color: #333; }
.os-label { font-size: 22rpx; color: #999; margin-top: 8rpx; }
.badge-dot { width: 12rpx; height: 12rpx; background: #ff4d4f; border-radius: 50%; margin-left: 6rpx; }
.menu-item { display: flex; justify-content: space-between; align-items: center; padding: 28rpx 0; border-bottom: 1rpx solid #f0f0f0; font-size: 28rpx; color: #333; }
.menu-item:last-child { border-bottom: none; }
.arrow { color: #ccc; }
.logout-btn { margin-top: 40rpx; background: #fff; color: #ff4d4f; border: 1rpx solid #ff4d4f; border-radius: 12rpx; padding: 24rpx 0; font-size: 30rpx; }
</style>
