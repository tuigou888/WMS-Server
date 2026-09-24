<template>
  <view class="login-page">
    <view class="login-card">
      <view class="logo">
        <text class="logo-icon">📦</text>
        <text class="logo-text">WMS 仓库管理</text>
      </view>

      <view class="tabs">
        <view class="tab" :class="{ active: tab === 'wx' }" @tap="tab = 'wx'">微信一键登录</view>
        <view class="tab" :class="{ active: tab === 'pwd' }" @tap="tab = 'pwd'">账号密码登录</view>
      </view>

      <!-- 微信登录/绑定 -->
      <view v-if="tab === 'wx'" class="form-section">
        <view v-if="wxState === 'login'" class="wx-login">
          <button class="btn-wx" @tap="wxLogin" :disabled="loading">
            <text v-if="loading" class="loading"></text>
            <text v-else>微信授权登录</text>
          </button>
          <text class="wx-tip" v-if="isLocalDebug">本地调试请使用账号密码登录；微信一键登录需要真实 AppID</text>
          <text class="wx-tip" v-else>首次使用需绑定账号，已绑定可直接登录</text>
        </view>

        <view v-else-if="wxState === 'bind'" class="wx-bind">
          <view class="bind-info">微信未绑定账号，请输入账号密码完成绑定</view>
          <view class="input-group">
            <label class="label">用户名</label>
            <input class="input" v-model="bindForm.username" placeholder="请输入用户名" @confirm="bindForm.password ? doBind() : ''" />
          </view>
          <view class="input-group">
            <label class="label">密码</label>
            <input class="input" type="password" v-model="bindForm.password" placeholder="请输入密码" @confirm="doBind" />
          </view>
          <button class="btn-primary" @tap="doBind" :disabled="loading || !bindForm.username || !bindForm.password">
            <text v-if="loading" class="loading"></text>
            <text v-else>绑定并登录</text>
          </button>
        </view>
      </view>

      <!-- 账号密码登录 -->
      <view v-else class="form-section">
        <view class="input-group">
          <label class="label">用户名</label>
          <input class="input" v-model="pwdForm.username" placeholder="请输入用户名" @confirm="pwdForm.password ? doPwdLogin() : ''" />
        </view>
        <view class="input-group">
          <label class="label">密码</label>
          <input class="input" type="password" v-model="pwdForm.password" placeholder="请输入密码" @confirm="doPwdLogin" />
        </view>
        <button class="btn-primary" @tap="doPwdLogin" :disabled="loading || !pwdForm.username || !pwdForm.password">
          <text v-if="loading" class="loading"></text>
          <text v-else>登录</text>
        </button>
      </view>

      <view v-if="loginSucceeded" class="login-success">
        <text>登录成功，正在打开首页</text>
        <navigator class="btn-primary login-home-link" url="/pages/index/index" open-type="reLaunch">进入首页</navigator>
      </view>

      <view v-if="isLocalDebug" class="demo-accounts">
        <text class="demo-title">演示账号：</text>
        <view class="demo-row">
          <text class="demo-item" @tap="fillAccount('admin', 'admin123')">管理员 / admin123</text>
          <text class="demo-item" @tap="fillAccount('operator', 'operator123')">操作员 / operator123</text>
        </view>
      </view>
    </view>

    <view class="footer">版本 1.0.0 | 仓库进销存管理系统</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user.js'
import { api, IS_LOCAL_API } from '@/api/request.js'

const isLocalDebug = import.meta.env.DEV || IS_LOCAL_API

export default {
  data() {
    return {
      tab: isLocalDebug ? 'pwd' : 'wx',
      wxState: 'login', // login | bind
      loading: false,
      isLocalDebug,
      loginSucceeded: false,
      bindForm: { username: '', password: '' },
      pwdForm: { username: '', password: '' },
    }
  },
  methods: {
    async wxLogin() {
      this.loading = true
      try {
        const res = await uni.login()
        if (!res.code) throw new Error('获取微信 code 失败')
        const result = await api.wxLogin(res.code)
        if (result.needBind) {
          this.wxState = 'bind'
          this.bindForm = { username: '', password: '' }
          this.bindForm.bindTicket = result.bindTicket
        } else {
          this.handleLoginSuccess(result)
        }
      } catch (e) {
        uni.showToast({ title: e.message || '微信登录失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    async doBind() {
      this.loading = true
      try {
        const result = await api.wxBind({
          bindTicket: this.bindForm.bindTicket,
          username: this.bindForm.username,
          password: this.bindForm.password,
        })
        this.handleLoginSuccess(result)
      } catch (e) {
        uni.showToast({ title: e.message || '绑定失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    async doPwdLogin() {
      this.loading = true
      try {
        const result = await api.login(this.pwdForm)
        this.handleLoginSuccess(result)
      } catch (e) {
        uni.showToast({ title: e.message || '登录失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },

    handleLoginSuccess(result) {
      const userStore = useUserStore()
      userStore.login({
        username: result.username,
        displayName: result.displayName,
        role: result.role,
        permissions: result.permissions,
      }, result.token)
      this.loginSucceeded = true
      uni.showToast({ title: '登录成功', icon: 'success' })
      this.navigateAfterLogin()
    },

    navigateAfterLogin() {
      const url = '/pages/index/index'
      const navigationApi = typeof wx !== 'undefined' && typeof wx.reLaunch === 'function' ? wx : uni
      console.log('[WMS] 登录成功，准备打开首页')
      navigationApi.reLaunch({
        url,
        success: () => console.log('[WMS] 登录后已打开首页'),
        fail: (error) => {
          console.log('[WMS] 重启首页失败，改用切换导航', error)
          uni.switchTab({
            url,
            success: () => console.log('[WMS] 登录后已切换至首页'),
            fail: (fallbackError) => {
              console.log('[WMS] 登录后打开首页失败', fallbackError)
              uni.showToast({ title: '登录成功，但首页打开失败', icon: 'none' })
            },
          })
        },
      })
    },

    fillAccount(username, password) {
      if (this.tab === 'pwd') {
        this.pwdForm = { username, password }
      } else {
        this.bindForm = { username, password }
      }
    },
  },
}
</script>

<style scoped>
.login-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(180deg, #f5f5f5 0%, #eef2f7 100%);
  padding: 40rpx;
  box-sizing: border-box;
}

.login-card {
  flex: 1;
  background: #fff;
  border-radius: 24rpx;
  padding: 48rpx;
  box-shadow: 0 8rpx 40rpx rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 48rpx;
}
.logo-icon { font-size: 96rpx; }
.logo-text { font-size: 40rpx; font-weight: 600; color: #333; margin-top: 16rpx; }

.tabs {
  display: flex;
  margin-bottom: 40rpx;
  border-bottom: 2rpx solid #f0f0f0;
}
.tab {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: 30rpx;
  color: #999;
  position: relative;
}
.tab.active { color: #1677ff; font-weight: 600; }
.tab.active::after {
  content: '';
  position: absolute;
  bottom: -2rpx;
  left: 25%;
  right: 25%;
  height: 4rpx;
  background: #1677ff;
  border-radius: 2rpx;
}

.form-section { flex: 1; }

.input-group { margin-bottom: 32rpx; }
.login-card .input {
  height: 88rpx;
  min-height: 88rpx;
  padding: 0 24rpx;
  line-height: 88rpx;
  background: #fff;
}

.login-success {
  position: fixed;
  right: 40rpx;
  bottom: 48rpx;
  left: 40rpx;
  z-index: 10;
  padding: 20rpx 24rpx;
  color: #2e7d32;
  font-size: 26rpx;
  background: #f1f8f3;
  border: 2rpx solid #b7dfbf;
  border-radius: 12rpx;
}
.login-home-link {
  display: block;
  margin-top: 16rpx;
  text-align: center;
  text-decoration: none;
}

.wx-login { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40rpx 0; }
.wx-tip { font-size: 24rpx; color: #999; margin-top: 24rpx; }

.wx-bind .bind-info { font-size: 26rpx; color: #faad14; background: #fffbe6; padding: 20rpx; border-radius: 12rpx; margin-bottom: 32rpx; }

.btn-wx {
  width: 100%;
  background: #07c160;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 28rpx;
  font-size: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}
.btn-wx:disabled { opacity: 0.7; }

.loading {
  width: 36rpx;
  height: 36rpx;
  border: 4rpx solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.demo-accounts {
  margin-top: 40rpx;
  padding-top: 32rpx;
  border-top: 2rpx solid #f0f0f0;
}
.demo-title { font-size: 24rpx; color: #999; display: block; margin-bottom: 16rpx; }
.demo-row { display: flex; gap: 24rpx; }
.demo-item {
  flex: 1;
  padding: 16rpx;
  background: #fafafa;
  border: 2rpx dashed #d9d9d9;
  border-radius: 12rpx;
  text-align: center;
  font-size: 24rpx;
  color: #666;
}

.footer {
  text-align: center;
  padding: 32rpx;
  font-size: 24rpx;
  color: #999;
}
</style>
