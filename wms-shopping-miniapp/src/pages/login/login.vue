<template>
  <view class="container">
    <view class="header">
      <image class="logo" src="/static/logo.png" mode="aspectFit" />
      <text class="app-name">WMS 机械商城</text>
      <text class="app-slogan">工业零配件 · 一站式采购</text>
    </view>

    <view class="card login-card">
      <view class="tabs">
        <view :class="['tab', mode==='password'&&'active']" @tap="mode='password'">账号登录</view>
        <view :class="['tab', mode==='wechat'&&'active']" @tap="mode='wechat'">微信登录</view>
      </view>

      <!-- 账号登录 -->
      <view v-if="mode==='password'" class="form">
        <input class="input" v-model="username" placeholder="用户名" />
        <input class="input" v-model="password" type="password" placeholder="密码" />
        <button class="btn-primary" :disabled="submitting" @tap="doLogin">登录</button>
        <view class="hint">演示账号：admin / admin123</view>
      </view>

      <!-- 微信登录 -->
      <view v-else class="form">
        <button class="btn-primary" :disabled="submitting" @tap="wxLogin">微信一键登录</button>
        <view class="hint">未绑定的账号将跳到绑定流程</view>
      </view>
    </view>
  </view>
</template>

<script>
import { auth } from '@/api/market.js'
import { useUserStore } from '@/store/user.js'

export default {
  data() {
    return { mode: 'password', username: '', password: '', submitting: false }
  },
  methods: {
    async doLogin() {
      if (!this.username || !this.password) { uni.showToast({ title: '请输入用户名和密码', icon: 'none' }); return }
      this.submitting = true
      try {
        const data = await auth.login({ username: this.username, password: this.password })
        useUserStore().login(data, data.token)
        uni.switchTab({ url: '/pages/index/index' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '登录失败', icon: 'none' })
      } finally { this.submitting = false }
    },
    async wxLogin() {
      this.submitting = true
      try {
        const res = await new Promise((resolve, reject) => {
          uni.login({ provider: 'weixin', success: r => resolve(r.code), fail: reject })
        })
        const data = await auth.wxLogin(res)
        if (data && data.needBind) {
          // 需要绑定：转入绑定（这里先提示与账号登录结合）
          uni.showModal({
            title: '微信未绑定',
            content: '请先使用账号登录，或在后台将微信 openid 绑定到账号',
            showCancel: false,
          })
        } else {
          useUserStore().login(data, data.token)
          uni.switchTab({ url: '/pages/index/index' })
        }
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '微信登录失败', icon: 'none' })
      } finally { this.submitting = false }
    },
  },
}
</script>

<style scoped>
.container { min-height: 100vh; padding: 120rpx 40rpx; box-sizing: border-box; }
.header { text-align: center; margin-bottom: 60rpx; }
.logo { width: 140rpx; height: 140rpx; border-radius: 24rpx; }
.app-name { display: block; font-size: 40rpx; font-weight: 700; margin-top: 20rpx; color: #333; }
.app-slogan { display: block; font-size: 26rpx; color: #999; margin-top: 8rpx; }
.login-card { padding: 40rpx 32rpx; }
.tabs { display: flex; margin-bottom: 40rpx; border-bottom: 1rpx solid #f0f0f0; }
.tab { flex: 1; text-align: center; padding: 16rpx; font-size: 30rpx; color: #666; }
.tab.active { color: #1677ff; font-weight: 600; border-bottom: 4rpx solid #1677ff; }
.form { display: flex; flex-direction: column; gap: 24rpx; }
.hint { text-align: center; color: #999; font-size: 24rpx; margin-top: 16rpx; }
</style>
