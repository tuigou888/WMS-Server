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
          <text class="wx-tip">首次使用需绑定账号，已绑定可直接登录</text>
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

      <view class="demo-accounts">
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
import { api } from '@/api/request.js'

export default {
  data() {
    return {
      tab: 'wx',
      wxState: 'login', // login | bind
      loading: false,
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
          this.bindForm.openid = result.openid
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
          openid: this.bindForm.openid,
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
      uni.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/index/index' })
      }, 500)
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
  padding: 20px;
  box-sizing: border-box;
}

.login-card {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}
.logo-icon { font-size: 48px; }
.logo-text { font-size: 20px; font-weight: 600; color: #333; margin-top: 8px; }

.tabs {
  display: flex;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}
.tab {
  flex: 1;
  padding: 12px 0;
  text-align: center;
  font-size: 15px;
  color: #999;
  position: relative;
}
.tab.active { color: #1677ff; font-weight: 600; }
.tab.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 25%;
  right: 25%;
  height: 2px;
  background: #1677ff;
  border-radius: 1px;
}

.form-section { flex: 1; }

.input-group { margin-bottom: 16px; }

.wx-login { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px 0; }
.wx-tip { font-size: 12px; color: #999; margin-top: 12px; }

.wx-bind .bind-info { font-size: 13px; color: #faad14; background: #fffbe6; padding: 10px; border-radius: 6px; margin-bottom: 16px; }

.btn-wx {
  width: 100%;
  background: #07c160;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 14px;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.btn-wx:disabled { opacity: 0.7; }

.loading {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.demo-accounts {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
.demo-title { font-size: 12px; color: #999; display: block; margin-bottom: 8px; }
.demo-row { display: flex; gap: 12px; }
.demo-item {
  flex: 1;
  padding: 8px;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  text-align: center;
  font-size: 12px;
  color: #666;
}

.footer {
  text-align: center;
  padding: 16px;
  font-size: 12px;
  color: #999;
}
</style>