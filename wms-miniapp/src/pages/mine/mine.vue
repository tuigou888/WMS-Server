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
          <text v-for="p in userStore.permissions.slice(0, 6)" :key="p" class="perm-tag">{{ p }}</text>
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
              <text class="log-action">{{ log.action }}</text>
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
            <text>后端 API</text>
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
        this.myLogs = data
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
  },
}
</script>

<style scoped>
.mine-page { background: #f5f5f5; min-height: 100vh; }
.content { width: 100%; box-sizing: border-box; padding-bottom: 30px; }

.user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 20px;
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
  color: #fff;
}
.user-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255,255,255,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 600;
  margin-bottom: 12px;
}
.user-name { font-size: 20px; font-weight: 600; margin-bottom: 6px; }
.user-role { font-size: 13px; padding: 2px 10px; border-radius: 12px; background: rgba(255,255,255,0.2); }
.role-admin { background: rgba(255,255,255,0.3); }
.user-perms {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
}
.perm-tag { font-size: 10px; background: rgba(255,255,255,0.15); padding: 2px 8px; border-radius: 10px; }

.card {
  background: #fff;
  border-radius: 10px;
  margin: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.section-title { font-size: 15px; font-weight: 600; color: #333; margin-bottom: 12px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.view-all { font-size: 13px; color: #1677ff; }

.warehouse-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.ws-main { display: flex; align-items: center; gap: 12px; }
.ws-icon { font-size: 24px; }
.ws-info { display: flex; flex-direction: column; }
.ws-name { font-size: 15px; font-weight: 500; color: #333; }
.ws-code { font-size: 11px; color: #999; }
.arrow { font-size: 12px; color: #999; }

.menu-list { display: flex; flex-direction: column; gap: 8px; }
.menu-item {
  display: flex;
  align-items: center;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  text-decoration: none;
}
.menu-item-hover { background: #e6f7ff; }
.menu-icon { font-size: 20px; margin-right: 12px; }
.menu-name { flex: 1; font-size: 15px; color: #333; }
.menu-arrow { font-size: 12px; color: #999; }

.log-list { display: flex; flex-direction: column; gap: 8px; }
.log-item {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
}
.log-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.log-action { font-size: 13px; font-weight: 500; color: #333; }
.log-target { font-size: 11px; color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 11px; }
.log-time { color: #999; }

.about-list { display: flex; flex-direction: column; gap: 10px; }
.about-row { display: flex; justify-content: space-between; font-size: 13px; color: #666; }
.about-row text:last-child { color: #333; }

.btn-logout {
  width: calc(100% - 32px);
  margin: 20px 16px;
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 14px;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.btn-logout:disabled { opacity: 0.5; }
</style>