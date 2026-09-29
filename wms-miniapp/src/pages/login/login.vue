<template>
  <view class="login-page">
    <view class="brand-hero">
      <view class="logo-badge">
        <text class="logo-icon">📦</text>
      </view>
      <text class="logo-text">WMS 仓库管理</text>
      <text class="logo-sub">仓库进销存 · 工业效率作业台</text>
    </view>

    <view class="login-card">
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

      <view v-if="demoAccounts.length" class="demo-accounts">
        <text class="demo-title">演示账号：</text>
        <view class="demo-row">
          <text v-for="d in demoAccounts" :key="d.username" class="demo-item" @tap="fillAccount(d.username, d.password)">{{ d.label }} / {{ d.password }}</text>
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
// 演示口令只在 DEV 构建存在；生产构建时 import.meta.env.DEV 为 false，字面量会被死代码消除，不进产物
const DEMO_ACCOUNTS = import.meta.env.DEV
  ? [
      { label: '管理员', username: 'admin', password: 'admin123' },
      { label: '操作员', username: 'operator', password: 'operator123' },
    ]
  : []

export default {
  data() {
    return {
      tab: isLocalDebug ? 'pwd' : 'wx',
      wxState: 'login', // login | bind
      loading: false,
      isLocalDebug,
      demoAccounts: isLocalDebug ? DEMO_ACCOUNTS : [],
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
      // 买家（CUSTOMER）账号属于商城端，登入作业端只会到处 403——这里直接拦截
      if (!(result.permissions || []).includes('inventory:read')) {
        uni.showModal({
          title: '无仓库作业权限',
          content: '当前账号为商城买家账号，请使用商城小程序购物；仓库作业请使用管理员或仓管账号登录。',
          showCancel: false,
        })
        return
      }
      userStore.login({
        username: result.username,
        displayName: result.displayName,
        role: result.role,
        permissions: result.permissions,
      }, result.token)
      // 本地保存到期时间（expiresIn 秒，留 1 分钟余量），冷启动据此判断 token 是否已过期
      uni.setStorageSync('wms_token_expires_at', Date.now() + Math.max(60, result.expiresIn || 43200) * 1000)
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
  box-sizing: border-box;
}

/* 品牌头部：主蓝渐变 */
.brand-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 88rpx 48rpx 150rpx;
  background: linear-gradient(135deg, var(--wms-primary), var(--wms-primary-deep));
  border-radius: 0 0 40rpx 40rpx;
  color: #fff;
}
.logo-badge {
  width: 128rpx;
  height: 128rpx;
  border-radius: 36rpx;
  background: rgba(255, 255, 255, 0.18);
  border: 2rpx solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}
.logo-icon { font-size: 72rpx; line-height: 1; }
.logo-text { font-size: 44rpx; font-weight: 600; color: #fff; letter-spacing: 2rpx; }
.logo-sub { font-size: 24rpx; color: rgba(255, 255, 255, 0.75); letter-spacing: 4rpx; margin-top: 16rpx; }

/* 表单上浮卡片 */
.login-card {
  flex: 1;
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 24rpx;
  margin: -100rpx 24rpx 0;
  padding: 48rpx 32rpx;
  box-shadow: 0 8rpx 32rpx rgba(31, 35, 41, 0.08);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.tabs {
  display: flex;
  margin-bottom: 40rpx;
  border-bottom: 2rpx solid var(--wms-border);
}
.tab {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: 30rpx;
  color: var(--wms-ink-3);
  position: relative;
}
.tab.active { color: var(--wms-primary); font-weight: 600; }
.tab.active::after {
  content: '';
  position: absolute;
  bottom: -2rpx;
  left: 25%;
  right: 25%;
  height: 4rpx;
  background: var(--wms-primary);
  border-radius: 2rpx;
}

.form-section { flex: 1; }

.input-group { margin-bottom: 32rpx; }
.login-card .input {
  height: 88rpx;
  min-height: 88rpx;
  padding: 0 24rpx;
  line-height: 88rpx;
  background: var(--wms-card);
}

.login-card .btn-primary,
.login-card .btn-wx { width: 100%; }

.login-success {
  position: fixed;
  right: 40rpx;
  bottom: 48rpx;
  left: 40rpx;
  z-index: 10;
  padding: 24rpx;
  color: var(--wms-success);
  font-size: 26rpx;
  background: var(--wms-success-bg);
  border: 2rpx solid var(--wms-success);
  border-radius: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(31, 35, 41, 0.08);
}
.login-home-link {
  display: block;
  margin-top: 16rpx;
  text-align: center;
  text-decoration: none;
}

.wx-login { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40rpx 0; }
.wx-tip { font-size: 24rpx; color: var(--wms-ink-3); margin-top: 24rpx; text-align: center; }

.wx-bind .bind-info {
  font-size: 26rpx;
  color: var(--wms-warning);
  background: var(--wms-warning-bg);
  border-left: 6rpx solid var(--wms-warning);
  padding: 20rpx 24rpx;
  border-radius: 12rpx;
  margin-bottom: 32rpx;
}

.btn-wx {
  background: var(--wms-success);
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 24rpx;
  font-size: 32rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  box-shadow: 0 4rpx 12rpx rgba(82, 196, 26, 0.25);
}
.btn-wx:disabled { opacity: 0.7; box-shadow: none; }

.loading {
  width: 36rpx;
  height: 36rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.demo-accounts {
  margin-top: 40rpx;
  padding-top: 32rpx;
  border-top: 2rpx solid var(--wms-border);
}
.demo-title { font-size: 24rpx; color: var(--wms-ink-3); display: block; margin-bottom: 16rpx; }
.demo-row { display: flex; gap: 24rpx; }
.demo-item {
  flex: 1;
  padding: 16rpx;
  background: var(--wms-bg);
  border: 2rpx dashed var(--wms-border);
  border-radius: 12rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--wms-ink-2);
}

.footer {
  text-align: center;
  padding: 32rpx 24rpx;
  font-size: 24rpx;
  color: var(--wms-ink-3);
}
</style>
