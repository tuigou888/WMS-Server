<template>
  <view class="mine-page">
    <scroll-view class="content" scroll-y :style="{ height: contentHeight + 'px' }">
      <!-- 用户信息 -->
      <view class="user-card">
        <view class="user-avatar">{{ userAvatar }}</view>
        <text class="user-name">{{ userStore.user?.displayName || userStore.user?.username }}</text>
        <text class="user-role" :class="userStore.isAdmin ? 'role-admin' : 'role-operator'">
          {{ roleText }}
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
          <navigator v-for="item in menus" :key="item.key" :url="item.url" :open-type="item.tab ? 'switchTab' : 'navigate'" class="menu-item" hover-class="menu-item-hover">
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
      <button class="btn-logout btn-danger" @tap="logout" :disabled="loggingOut">
        <text v-if="loggingOut" class="loading"></text>
        <text v-else>退出登录</text>
      </button>
    </scroll-view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user.js'
import { api } from '@/api/request.js'
import { chooseIndex } from '@/utils/choose.js'
import { dateTime as formatDateTime } from '@/utils/format.js'
import { actionLabel, permissionLabel } from '@/utils/labels.js'

export default {
  data() {
    return {
      myLogs: [],
      contentHeight: 0,
      loggingOut: false,
      menus: [
        { key: 'inventory', name: '库存查询', icon: '📦', url: '/pages/inventory/inventory', tab: true },
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
  },
  onShow() {
    this.loadMyLogs()
  },
  methods: {
    setContentHeight() {
      // windowHeight 已扣除原生导航栏与 tabBar，再减会多扣导致底部空白
      this.contentHeight = uni.getSystemInfoSync().windowHeight
    },
    async loadMyLogs() {
      try {
        const username = this.userStore.user?.username
        if (!username) return
        // /logs 需 log:view 权限（仅 ADMIN/AUDITOR），无权限用户跳过，避免每次进入都 403 静默失败
        if (!this.userStore.hasPerm('log:view')) { this.myLogs = []; return }
        const data = await api.logs({ username, pageSize: 20 })
        this.myLogs = data.records
      } catch (e) {
        console.warn('加载操作日志失败:', e)
      }
    },
    async showWarehousePicker() {
      const items = this.userStore.warehouses.map(w => w.name)
      if (items.length === 0) {
        uni.showToast({ title: '暂无仓库数据', icon: 'none' })
        return
      }
      // 仓库可能超过 6 个（微信 actionSheet 上限），用分页选择器
      const idx = await chooseIndex(items)
      if (idx < 0) return
      this.userStore.setWarehouse(this.userStore.warehouses[idx].id)
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
.mine-page { min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 60rpx; }

/* 用户信息：主蓝渐变头部卡 */
.user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 20rpx 24rpx;
  padding: 48rpx 32rpx;
  background: linear-gradient(135deg, var(--wms-primary), var(--wms-primary-deep));
  border-radius: 20rpx;
  color: #fff;
  box-shadow: 0 4rpx 16rpx rgba(9, 88, 217, 0.25);
}
.user-avatar {
  width: 144rpx;
  height: 144rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  border: 2rpx solid rgba(255, 255, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.user-name { font-size: 38rpx; font-weight: 600; margin-bottom: 12rpx; }
.user-role { font-size: 24rpx; padding: 6rpx 24rpx; border-radius: 999rpx; background: rgba(255, 255, 255, 0.2); }
.role-admin { background: rgba(255, 255, 255, 0.32); }
.user-perms {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12rpx;
  margin-top: 28rpx;
}
.perm-tag { font-size: 20rpx; background: rgba(255, 255, 255, 0.15); padding: 4rpx 16rpx; border-radius: 999rpx; }

/* 卡片内区块标题贴顶（样式复用全局 .card / .section-title） */
.card .section-title { margin-top: 0; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.section-header .section-title { margin-bottom: 0; }
.view-all { font-size: 26rpx; color: var(--wms-primary); }

.warehouse-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: var(--wms-bg);
  border: 2rpx solid var(--wms-border);
  border-radius: 16rpx;
}
.ws-main { display: flex; align-items: center; gap: 24rpx; }
.ws-icon { font-size: 48rpx; }
.ws-info { display: flex; flex-direction: column; }
.ws-name { font-size: 30rpx; font-weight: 600; color: var(--wms-ink); }
.ws-code { font-size: 22rpx; color: var(--wms-ink-3); margin-top: 4rpx; }
.arrow { font-size: 24rpx; color: var(--wms-ink-3); }

/* 功能菜单：分隔线列表 */
.menu-list { display: flex; flex-direction: column; }
.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 8rpx;
  border-bottom: 2rpx solid var(--wms-border);
  text-decoration: none;
}
.menu-item:last-child { border-bottom: none; }
.menu-item-hover { background: var(--wms-primary-bg); }
.menu-icon { font-size: 40rpx; margin-right: 24rpx; }
.menu-name { flex: 1; font-size: 30rpx; color: var(--wms-ink); }
.menu-arrow { font-size: 24rpx; color: var(--wms-ink-3); }

/* 我的操作记录 */
.log-list { display: flex; flex-direction: column; gap: 16rpx; }
.log-item {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: var(--wms-bg);
  border-radius: 12rpx;
}
.log-main { display: flex; flex-direction: column; gap: 4rpx; min-width: 0; }
.log-action { font-size: 26rpx; font-weight: 500; color: var(--wms-ink); }
.log-target { font-size: 22rpx; color: var(--wms-ink-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; font-size: 22rpx; }
.log-time { font-size: 22rpx; color: var(--wms-ink-3); }

.about-list { display: flex; flex-direction: column; gap: 20rpx; }
.about-row { display: flex; justify-content: space-between; font-size: 26rpx; color: var(--wms-ink); }
.about-row text:first-child { color: var(--wms-ink-2); }
.about-row text:last-child { font-weight: 500; }

.btn-logout {
  width: calc(100% - 48rpx);
  margin: 40rpx 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}
.btn-logout:disabled { opacity: 0.5; }

.loading {
  width: 36rpx;
  height: 36rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
