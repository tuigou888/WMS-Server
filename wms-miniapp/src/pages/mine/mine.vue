<template>
  <view class="mine-page">
    <scroll-view class="content" scroll-y :style="{ height: contentHeight + 'px' }">
      <!-- 用户信息 -->
      <view class="user-card">
        <view class="user-avatar">{{ userAvatar }}</view>
        <text class="user-name">{{ userStore.user?.displayName || userStore.user?.username }}</text>
        <text class="user-role" :class="userStore.isAdmin ? 'role-admin' : 'role-operator'">
          {{ userStore.isAdmin ? '系统管理员' : '仓库操作员' }}
        </text>
        <view class="user-perms">
          <text v-for="p in userStore.permissions.slice(0, 6)" :key="p" class="perm-tag">{{ permissionLabel(p) }}</text>
          <text v-if="userStore.permissions.length > 6" class="perm-tag">...+{{ userStore.permissions.length - 6 }}</text>
        </view>
      </view>

      <!-- 当前仓库 -->
      <view class="card">
        <text class="section-title">当前仓库</text>
        <view class="warehouse-selector" @tap="showWarehousePicker">
          <view class="ws-main">
            <text class="ws-icon">🏭</text>
            <view class="ws-info">
              <text class="ws-name">{{ currentWarehouse?.name || '未选择仓库' }}</text>
              <text class="ws-code">{{ currentWarehouse?.code || '' }}</text>
            </view>
          </view>
          <text class="arrow">▶</text>
        </view>
      </view>

      <!-- 功能菜单 -->
      <view class="card">
        <text class="section-title">功能菜单</text>
        <view class="menu-list">
          <navigator v-for="item in menus" :key="item.key" :url="item.url" class="menu-item" hover-class="menu-item-hover">
            <text class="menu-icon">{{ item.icon }}</text>
            <text class="menu-name">{{ item.name }}</text>
            <text class="menu-arrow">▶</text>
          </navigator>
        </view>
      </view>

      <!-- 我的操作记录 -->
      <view class="card" v-if="myLogs.length > 0">
        <view class="section-header">
          <text class="section-title">我的操作记录</text>
          <navigator url="/pages/mine/mine" class="view-all">查看全部</navigator>
        </view>
        <view class="log-list">
          <view v-for="log in myLogs.slice(0, 5)" :key="log.id" class="log-item">
            <view class="log-main">
              <text class="log-action">{{ actionLabel(log.action) }}</text>
              <text class="log-target">{{ log.target }}</text>
            </view>
            <view class="log-meta">
              <text class="log-result" :class="log.result === 'SUCCESS' ? 'value-green' : 'value-red'">
                {{ log.result === 'SUCCESS' ? '成功' : '失败' }}
              </text>
              <text class="log-time">{{ formatDateTime(log.operationAt) }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 版本信息 -->
      <view class="card">
        <text class="section-title">关于</text>
        <view class="about-list">
          <view class="about-row">
            <text>版本</text>
            <text>1.0.0</text>
          </view>
          <view class="about-row">
            <text>后端接口</text>
            <text class="value-green">v1.0</text>
          </view>
          <view class="about-row">
            <text>技术栈</text>
            <text>uni-app + Vue 3</text>
          </view>
        </view>
      </view>

      <!-- 退出登录 -->
      <button class="btn-logout" @tap="logout" :disabled="loggingOut">
        <text v-if="loggingOut" class="loading"></text>
        <text v-else>退出登录</text>
      </button>
    </scroll-view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user.js'
import { api } from '@/api/request.js'
import { dateTime as formatDateTime } from '@/utils/format.js'
import { actionLabel, permissionLabel } from '@/utils/labels.js'

export default {
  data() {
    return {
      myLogs: [],
      contentHeight: 0,
      loggingOut: false,
      menus: [
        { key: 'inventory', name: '库存查询', icon: '📦', url: '/pages/inventory/inventory' },
        { key: 'items', name: '物品查询', icon: '🏷️', url: '/pages/item-list/item-list' },
        { key: 'check', name: '盘点任务', icon: '📋', url: '/pages/check/check' },
        { key: 'documents', name: '单据查看', icon: '📄', url: '/pages/document-list/document-list' },
        { key: 'transactions', name: '库存流水', icon: '📋', url: '/pages/transactions/transactions' },
        { key: 'reports', name: '报表中心', icon: '📊', url: '/pages/reports/reports' },
      ],
    }
  },
  computed: {
    userStore() { return useUserStore() },
    userAvatar() {
      const name = this.userStore.user?.displayName || this.userStore.user?.username || '用'
      return name.charAt(0).toUpperCase()
    },
    currentWarehouse() {
      return this.userStore.warehouses.find(w => w.id === this.userStore.warehouseId)
    },
  },
  onLoad() {
    this.setContentHeight()
    this.loadMyLogs()
  },
  onShow() {
    this.loadMyLogs()
  },
  methods: {
    setContentHeight() {
      const sysInfo = uni.getSystemInfoSync()
      const tabBarHeight = 50
      const navBarHeight = sysInfo.statusBarHeight + 44
      this.contentHeight = sysInfo.windowHeight - navBarHeight - tabBarHeight
    },
    async loadMyLogs() {
      try {
        const username = this.userStore.user?.username
        if (!username) return
        const data = await api.logs({ username, pageSize: 20 })
        this.myLogs = data.records
      } catch (e) {
        console.warn('加载操作日志失败:', e)
      }
    },
    showWarehousePicker() {
      const items = this.userStore.warehouses.map(w => w.name)
      if (items.length === 0) {
        uni.showToast({ title: '暂无仓库数据', icon: 'none' })
        return
      }
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          const selected = this.userStore.warehouses[res.tapIndex]
          this.userStore.setWarehouse(selected.id)
        },
      })
    },
    async logout() {
      this.loggingOut = true
      try {
        await api.logout()
      } catch (e) {
        console.warn('登出接口调用失败:', e)
      } finally {
        const userStore = useUserStore()
        userStore.logout()
        uni.showToast({ title: '已退出登录', icon: 'success' })
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/login/login' })
        }, 500)
      }
    },
    formatDateTime,
    actionLabel,
    permissionLabel,
  },
}
</script>

<style scoped>
.mine-page { background: #f5f5f5; min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 60rpx; }

.user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 40rpx;
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
  color: #fff;
}
.user-avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64rpx;
  font-weight: 600;
  margin-bottom: 24rpx;
}
.user-name { font-size: 40rpx; font-weight: 600; margin-bottom: 12rpx; }
.user-role { font-size: 26rpx; padding: 4rpx 20rpx; border-radius: 24rpx; background: rgba(255,255,255,0.2); }
.role-admin { background: rgba(255,255,255,0.3); }
.user-perms {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12rpx;
  margin-top: 32rpx;
}
.perm-tag { font-size: 20rpx; background: rgba(255,255,255,0.15); padding: 4rpx 16rpx; border-radius: 20rpx; }

.card {
  background: #fff;
  border-radius: 20rpx;
  margin: 24rpx;
  padding: 32rpx;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.04);
}
.section-title { font-size: 30rpx; font-weight: 600; color: #333; margin-bottom: 24rpx; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.view-all { font-size: 26rpx; color: #1677ff; }

.warehouse-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: #fafafa;
  border-radius: 16rpx;
}
.ws-main { display: flex; align-items: center; gap: 24rpx; }
.ws-icon { font-size: 48rpx; }
.ws-info { display: flex; flex-direction: column; }
.ws-name { font-size: 30rpx; font-weight: 500; color: #333; }
.ws-code { font-size: 22rpx; color: #999; }
.arrow { font-size: 24rpx; color: #999; }

.menu-list { display: flex; flex-direction: column; gap: 16rpx; }
.menu-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #fafafa;
  border-radius: 16rpx;
  text-decoration: none;
}
.menu-item-hover { background: #e6f7ff; }
.menu-icon { font-size: 40rpx; margin-right: 24rpx; }
.menu-name { flex: 1; font-size: 30rpx; color: #333; }
.menu-arrow { font-size: 24rpx; color: #999; }

.log-list { display: flex; flex-direction: column; gap: 16rpx; }
.log-item {
  display: flex;
  justify-content: space-between;
  padding: 20rpx;
  background: #fafafa;
  border-radius: 12rpx;
}
.log-main { display: flex; flex-direction: column; gap: 4rpx; min-width: 0; }
.log-action { font-size: 26rpx; font-weight: 500; color: #333; }
.log-target { font-size: 22rpx; color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; font-size: 22rpx; }
.log-time { color: #999; }

.about-list { display: flex; flex-direction: column; gap: 20rpx; }
.about-row { display: flex; justify-content: space-between; font-size: 26rpx; color: #666; }
.about-row text:last-child { color: #333; }

.btn-logout {
  width: calc(100% - 64rpx);
  margin: 40rpx 32rpx;
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 16rpx;
  padding: 28rpx;
  font-size: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}
.btn-logout:disabled { opacity: 0.5; }
</style>
